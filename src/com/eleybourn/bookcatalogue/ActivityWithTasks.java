/*
 * @copyright 2011 Philip Warner
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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.TaskManager.TaskManagerController;
import com.eleybourn.bookcatalogue.TaskManager.TaskManagerListener;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.debug.Tracker.States;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Class to used as a base class for any Activity that wants to run one or more threads that
 * use a ProgressDialog.
 * 
 * Part of three components that make this easier:
 *  - TaskManager -- handles the management of multiple threads sharing a progressDialog
 *  - ActivityWithTasks -- uses a TaskManager (and communicates with it) to handle progress
 *    messages for threads. Deals with orientation changes in cooperation with TaskManager.
 *  - ManagedTask -- Background task that is managed by TaskManager and uses TaskManager to 
 *    do all display activities.
 *    
 * @author Philip Warner
 */
abstract public class ActivityWithTasks extends Activity {
	/** ID of associated TaskManager */
	protected long mTaskManagerId = 0;
	/** Associated TaskManager */
	private TaskManager mTaskManager = null;
	/** ProgressDialog for this activity */
	protected ProgressBase mProgressDialog = null;
	/** Max value for ProgressDialog */
	private int mProgressMax = 0;
	/** Current value for ProgressDialog */
	private int mProgressCount = 0;
	/** Message for ProgressDialog */
	private String mProgressMessage = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Restore mTaskManagerId if present 
		if (savedInstanceState != null) {
			mTaskManagerId = savedInstanceState.getLong("TaskManagerId");
		};
	}

	/**
	 * Trivial internal class to implement our base progress object
	 * 
	 * @author pjw
	 */
	private class ProgressBase extends ProgressDialog {
		public ProgressBase(Context context) {
			super(context);
			this.setCancelable(false);
			this.setCanceledOnTouchOutside(false);
		}
	}

	/**
	 * ProgressDialog for Indeterminate states.
	 * 
	 * @author pjw
	 */
	private class ProgressIndet extends ProgressBase {

		public ProgressIndet(Context context) {
			super(context);
			this.setIndeterminate(true);
			this.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		}
	};

	/**
	 * ProgressDialog for Determinate states.
	 * 
	 * @author pjw
	 */
	private class ProgressDet extends ProgressBase {

		public ProgressDet(Context context) {
			super(context);
			this.setIndeterminate(false);
			this.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		}
	};

	/**
	 * Utility routine to get the task manager for his activity
	 * @return
	 */
	protected TaskManager getTaskManager() {
		if (mTaskManager == null) {
			if (mTaskManagerId != 0) {
				TaskManagerController c = TaskManager.getMessageSwitch().getController(mTaskManagerId);
				if (c != null) {
					mTaskManager = c.getManager();
				} else {
					Logger.logError(new RuntimeException("Have ID, but can not find controller getting TaskManager"));
				}
			} else {
				//Logger.logError(new RuntimeException("Task manager requested, but no ID available"));				
			}

			// Create if necessary
			if (mTaskManager == null) {
				TaskManager tm = new TaskManager();
				mTaskManagerId = tm.getSenderId();
				mTaskManager = tm;
			}
		}
		return mTaskManager;
	}

	@Override
	protected void onPause() {
		super.onPause();
		// Stop listening
		if (mTaskManagerId != 0) {
			TaskManager.getMessageSwitch().removeListener(mTaskManagerId, mTaskListener);
			// If it's finishing, the remove all tasks and cleanup
			if (isFinishing())
				getTaskManager().close();
		}
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Restore mTaskManager if present 
		getTaskManager();

		// Listen
		TaskManager.getMessageSwitch().addListener(mTaskManagerId, mTaskListener, true);
	}

	/**
	 * Method to allow subclasses easy access to terminating tasks
	 * 
	 * @param task
	 */
	public void onTaskEnded(ManagedTask task) {	}

	/**
	 * Object to handle all TaskManager events
	 */
	private TaskManagerListener mTaskListener = new TaskManagerListener() {

		@Override
		public void onTaskEnded(TaskManager manager, ManagedTask task) {
			// Just pass this one on
			ActivityWithTasks.this.onTaskEnded(task);
		}

		@Override
		public void onProgress(int count, int max, String message) {
			// RELEASE: Remove these lines!
			String dbgMsg =  count + "/" + max + ", '" + message.replace("\n", "\\n") + "'";
			Tracker.handleEvent(ActivityWithTasks.this, "SearchProgress " + dbgMsg, States.Running);
			System.out.println("PRG: " + dbgMsg);

			// Save the details
			mProgressCount = count;
			mProgressMax = max;
			mProgressMessage = message;

			// If empty, close any dialog
			if ((mProgressMessage == null || mProgressMessage.trim().length() == 0) && mProgressMax == mProgressCount) {
				if (mProgressDialog != null) {
					mProgressDialog.dismiss();
					mProgressDialog = null;
				}
			} else {
				updateProgress();
			}

		}

		/**
		 * Display a Toast message
		 */
		@Override
		public void onToast(String message) {
			Toast.makeText(ActivityWithTasks.this, message, Toast.LENGTH_LONG).show();
		}

		/**
		 * TaskManager is finishing...cleanup.
		 */
		@Override
		public void onFinished() {
			mTaskManager.close();
			mTaskManager = null;
			mTaskManagerId = 0;
		}
	};

	/**
	 * Utility routine to standardize checking for desired dialog type.
	 * 
	 * @return	true if dialog should be determinate
	 */
	private boolean wantDeterminateProgress() {
		return (mProgressMax > 0);
	}

	/**
	 * Setup the ProgressDialog according to our needs
	 * 
	 * @param d		Dialog
	 * @param show	Flag indicating if show() should be called.
	 * 
	 */
	private void updateProgress() {
		boolean wantDet = wantDeterminateProgress();
		
		if (mProgressDialog != null) {
			if ((wantDet && mProgressDialog instanceof ProgressIndet) || (!wantDet && mProgressDialog instanceof ProgressDet)) {
				mProgressDialog.dismiss();
				mProgressDialog = null;
			}			
		}

		// Create dialog if necessary
		if (mProgressDialog == null) {
			if (wantDet) {
				mProgressDialog = new ProgressDet(ActivityWithTasks.this);
			} else {
				mProgressDialog = new ProgressIndet(ActivityWithTasks.this);
			}
		}

		// Set style
		if (mProgressMax > 0) {
			mProgressDialog.setMax(mProgressMax);
		}

		// Set message; if we are cancelling we override the message
		if (mTaskManager.isCancelling()) {
			mProgressDialog.setMessage(getString(R.string.cancelling));
		} else {
			mProgressDialog.setMessage(mProgressMessage);
		}
		
		// Set other attrs
		mProgressDialog.setOnKeyListener(mDialogKeyListener);
		mProgressDialog.setOnCancelListener(mCancelHandler);
		// Show it if necessary
		mProgressDialog.show();
		mProgressDialog.setProgress(mProgressCount);
	}

	/**
	 * Handler for the user cancelling the progress dialog.
	 */
	private OnCancelListener mCancelHandler = new OnCancelListener() {
		public void onCancel(DialogInterface i) {
			cancelAndUpdateProgress();
		}
	};

	/**
	 * Wait for the 'Back' key and cancel all tasks on keyUp.
	 */
	private OnKeyListener mDialogKeyListener = new OnKeyListener() {
		@Override
		public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					// Toasting a message here makes the app look less responsive, because
					// the final 'Cancelled...' message is delayed too much.
					//Toast.makeText(ActivityWithTasks.this, R.string.cancelling, Toast.LENGTH_LONG).show();
					cancelAndUpdateProgress();
					return true;
				}
			}
			return false;
		}
	};

	/**
	 * Cancel all tasks, and if the progress is showing, update it (it will check task manager status)
	 */
	private void cancelAndUpdateProgress() {
		if (mTaskManager != null)
			mTaskManager.cancelAllTasks();
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			updateProgress();
		}
	}

	@Override
	/**
	 * Save the TaskManager ID for later retrieval
	 */
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mTaskManagerId != 0)
			outState.putLong("TaskManagerId", mTaskManagerId);
	}
}
