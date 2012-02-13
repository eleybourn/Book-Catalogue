package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.SimpleTaskQueue.OnTaskFinishListener;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTaskContext;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
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
 * @author Grunthos
 */
public class StartupActivity extends Activity {
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
	
	/** Database connection */
	CatalogueDBAdapter mDb = null;

	/** Handler to post runnables to UI thread */
	private Handler mHandler = new Handler();
	/**UI thread */
	private Thread mUiThread;

	/** Set the flag to indicate an FTS rebuild is required */
	public static void scheduleFtsRebuild() {
		mFtsRebuildRequired = true;
	}
	
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

			SimpleTaskQueue q = getQueue();
			mDb = new CatalogueDBAdapter(this);
			mDb.open();
			if (mFtsRebuildRequired) {
				q.enqueue(new RebuildFtsTask());
				mFtsRebuildRequired = false;
			}
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
	 * Update the progress dialog, if it has not been dismissed.
	 * 
	 * @param message
	 */
	private void updateProgress(final String message) {
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
	
	private void stage3Startup() {
		// Just start MainMenu...it will start the users chosen startup page. Bizarre.
		Intent i;
		i = new Intent(this, MainMenu.class);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if (mWasReallyStartup)
			i.putExtra("startup", true);
		this.startActivity(i);

		// Die
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
		if (mDb != null)
			mDb.close();
	}
	
	/**
	 * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
	 * 
	 * @author Grunthos
	 *
	 */
	public class RebuildFtsTask implements SimpleTask {

		@Override
		public void run(SimpleTaskContext taskContext) {
			CatalogueDBAdapter db = taskContext.getDb();
			updateProgress(getString(R.string.rebuilding_search_index));
			db.rebuildFts();
		}

		@Override
		public void finished() {}

		@Override
		public boolean runFinished() { return false; }

	}

	public class AnalyzeDbTask implements SimpleTask {

		@Override
		public void run(SimpleTaskContext taskContext) {
			CatalogueDBAdapter db = taskContext.getDb();
			updateProgress(getString(R.string.optimizing_databases));
			// Analyze DB
			db.analyzeDb();
			// Analyze the covers DB
			Utils.analyzeCovers();
		}

		@Override
		public void finished() {}

		@Override
		public boolean runFinished() { return false; }
	}
	
}
