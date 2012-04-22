/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.OnTaskFinishListener;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTaskContext;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;

/**
 * Single Activity to be the 'Main' activity for the app. I does app-startup stuff which is initially
 * to start the 'real' main activity.
 * 
 * Note that calling the desired main activity first resulted in MainMenu's 'singleInstance' property
 * NOT being honoured. So we call MainMenu anyway, but set a flag in the Intent to indicate this is
 * a startup. This approach mostly works, but results in an apparent misordering of the activity 
 * stack, which we can live with for now.
 * 
 * @author Philip Warner
 */
public class StartupActivity extends Activity {
	private static final String STATE_OPENED = "state_opened";
	private static final int BACKUP_PROMPT_WAIT = 5;

	/** Indicates the upgrade message has been shown */
	private static boolean mUpgradeMessageShown = false;
	/** Flag to indicate FTS rebuild is required at startup */
	private static boolean mFtsRebuildRequired = false;
	/** Flag set to true on first call */
	private static boolean mIsReallyStartup = true;

	/** Queue for executing startup tasks, if any */
	private SimpleTaskQueue mTaskQueue = null;
	/** Progress Dialog for startup tasks */
	private ProgressDialog mProgress = null;
	/** Flag indicating THIS instance was really the startup instance */
	private boolean mWasReallyStartup = false;
	
	/** Flag indicating an export is required after startup */
	private boolean mExportRequired = false;

	/** Database connection */
	//CatalogueDBAdapter mDb = null;

	/** Handler to post runnables to UI thread */
	private Handler mHandler = new Handler();
	/**UI thread */
	private Thread mUiThread;

	/** Set the flag to indicate an FTS rebuild is required */
	public static void scheduleFtsRebuild() {
		mFtsRebuildRequired = true;
	}
	
	public static WeakReference<StartupActivity> mStartupActivity = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mUiThread = Thread.currentThread();

		// Create a progress dialog; we may not use it...but we need it to be created in the UI thread.
		mProgress = ProgressDialog.show(this, getString(R.string.book_catalogue_startup), getString(R.string.starting_up), true, true, new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				// Cancelling the list cancels the activity.
				StartupActivity.this.finish();
			}});			

		mWasReallyStartup = mIsReallyStartup;

		// If it's a real application startup...cleanup old stuff
		if (mWasReallyStartup) {
			mStartupActivity = new WeakReference<StartupActivity>(this);

			updateProgress("Starting");

			SimpleTaskQueue q = getQueue();

			// Always enqueue it; it will get a DB and check if required...
			q.enqueue(new RebuildFtsTask());
			q.enqueue(new AnalyzeDbTask());				

			// Remove old logs
			Logger.clearLog();
			// Clear the flag
			mIsReallyStartup = false;

			// ENHANCE: add checks for new Events/crashes
		}

		// If no tasks were queued, then move on to stage 2. Otherwise, the completed
		// tasks will cause stage 2 to start.
		if (mTaskQueue == null)
			stage2Startup();
	}

	/**
	 * Kludge to get a reference to the currently running StartupActivity, if defined.
	 * 
	 * @return		Reference or null.
	 */
	public static StartupActivity getActiveActivity() {
		if (mStartupActivity != null)
			return mStartupActivity.get();
		else
			return null;
	}

	/**
	 * Update the progress dialog, if it has not been dismissed.
	 * 
	 * @param message
	 */
	public void updateProgress(final int stringId) {
		updateProgress(getString(stringId));
	}
	/**
	 * Update the progress dialog, if it has not been dismissed.
	 * 
	 * @param message
	 */
	public void updateProgress(final String message) {
		// If mProgress is null, it has been dismissed. Don't update.
		if (mProgress == null) {
			return;
		}

		// If we are in the UI thread, update the progress.
		if (Thread.currentThread().equals(mUiThread)) {
			mProgress.setMessage(message);
			if (!mProgress.isShowing())
				mProgress.show();
		} else {
			// If we are NOT in the UI thread, queue it to the UI thread.
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					updateProgress(message);
				}});
		}
	}

	/**
	 * Called in the UI thread when any startup task completes. If no more tasks, start stage 2.
	 * Because it is in the UI thread, it is not possible for this code to be called until after 
	 * onCreate() completes, so a race condition is not possible. Equally well, tasks should only
	 * be queued in onCreate().
	 */
	private void taskCompleted(SimpleTask task) {
		System.out.println("Task Completed: " + task.getClass().getSimpleName());
		if (!mTaskQueue.hasActiveTasks()) {
			System.out.println("Task Completed - no more");
			stage2Startup();
		}
	}

	/**
	 * Get (or create) the task queue.
	 * 
	 * @return
	 */
	private SimpleTaskQueue getQueue() {
		if (mTaskQueue == null) {
			mTaskQueue = new SimpleTaskQueue("startup-tasks", 1);	
			// Listen for task completions
			mTaskQueue.setTaskFinishListener(new OnTaskFinishListener() {
				@Override
				public void onTaskFinish(SimpleTask task, Exception e) {
					taskCompleted(task);
				}});
		}
		return mTaskQueue;
	}

	/**
	 * Called in UI thread after last startup task completes, or if there are no tasks to queue.
	 */
	private void stage2Startup() {
		// Remove the weak reference. Only used by db onUpgrade.
		mStartupActivity.clear();
		// Get rid of the progress dialog
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}

		// Display upgrade message if necessary, otherwise go on to stage 3
		if (mUpgradeMessageShown || CatalogueDBAdapter.message.equals("")) {
			stage3Startup();
		} else {
			upgradePopup(CatalogueDBAdapter.message);
		}
	}
	
	/**
	 * Start the main book list
	 */
	private void doMyBooks() {
		Intent i = new Intent(this, BooksOnBookshelf.class);
		if (mWasReallyStartup)
			i.putExtra("startup", true);
		startActivity(i);
	}
	
	/**
	 * Start the main menu
	 */
	private void doMainMenu() {
		Intent i = new Intent(this, MainMenu.class);
		if (mWasReallyStartup)
			i.putExtra("startup", true);
		startActivity(i);
	}

	public void stage3Startup() {
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		int opened = prefs.getInt(STATE_OPENED, BACKUP_PROMPT_WAIT);

		Editor ed = prefs.edit();
		if (opened == 0) {
			ed.putInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
		} else {
			ed.putInt(STATE_OPENED, opened - 1);
		}
		ed.commit();			

		
		mExportRequired = false;

		if (opened == 0) {

			AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(R.string.backup_request).create();
			alertDialog.setTitle(R.string.backup_title);
			alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
			alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mExportRequired = true;
					dialog.dismiss();
				}
			}); 
			alertDialog.setButton2("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			}); 
			alertDialog.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					stage4Startup();
				}});
			alertDialog.show();
		} else {
			stage4Startup();
		}
	}

	/**
	 * Start whatever activity the user expects
	 */
	private void stage4Startup() {
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		Bundle extras = this.getIntent().getExtras();

		// Handle startup specially.
		// Check if we really want to start this activity.
		if (prefs.getStartInMyBook()) {
			doMyBooks();
		} else {			
			doMainMenu();
		}

		if (mExportRequired)
			Administration.adminPage(StartupActivity.this, "export", R.id.ACTIVITY_ADMIN);

		// We are done
		finish();		
	}

	/**
	 * This will display a popup with a provided message to the user. This will be
	 * mostly used for upgrade notifications
	 * 
	 * @param message The message to display in the popup
	 */
	public void upgradePopup(String message) {
		AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(message).create();
		alertDialog.setTitle(R.string.upgrade_title);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				stage3Startup();
			}
		});
		alertDialog.show();
		mUpgradeMessageShown = true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mTaskQueue != null)
			mTaskQueue.finish();
	}
	
	/**
	 * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
	 * 
	 * @author Philip Warner
	 *
	 */
	public class RebuildFtsTask implements SimpleTask {

		@Override
		public void run(SimpleTaskContext taskContext) {
			// Get a DB to make sure the FTS rebuild flag is set appropriately
			CatalogueDBAdapter db = taskContext.getDb();

			if (mFtsRebuildRequired) {
				updateProgress(getString(R.string.rebuilding_search_index));
				db.rebuildFts();
				mFtsRebuildRequired = false;				
			}
		}

		@Override
		public void onFinish() {}

		@Override
		public boolean requiresOnFinish() { return false; }

	}

	public class AnalyzeDbTask implements SimpleTask {

		@Override
		public void run(SimpleTaskContext taskContext) {
			CatalogueDBAdapter db = taskContext.getDb();
			updateProgress(getString(R.string.optimizing_databases));
			// Analyze DB
			db.analyzeDb();
			// Analyze the covers DB
			Utils utils = taskContext.getUtils();
			utils.analyzeCovers();				
		}

		@Override
		public void onFinish() {}

		@Override
		public boolean requiresOnFinish() { return false; }
	}
	
}
