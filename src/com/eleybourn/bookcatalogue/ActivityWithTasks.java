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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.TaskManager.TaskManagerListener;

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
	protected TaskManager mTaskManager = null;
	/** ProgressDialog for this activity */
	protected ProgressDialog mProgressDialog = null;
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
	
	@Override
	protected void onPause() {
		super.onPause();
		// Stop listening
		if (mTaskManagerId != 0) {
			TaskManager.getMessageSwitch().removeListener(mTaskManagerId, mTaskListener);
			// If it's finishing, the remove all tasks and cleanup
			if (isFinishing())
				mTaskManager.close();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		// Restore mTaskManager if present 
		if (mTaskManagerId != 0) {
			mTaskManager = TaskManager.getMessageSwitch().getController(mTaskManagerId).getManager();
		};

		// Create if necessary
		if (mTaskManager == null) {
			TaskManager tm = new TaskManager();
			mTaskManagerId = tm.getSenderId();
			mTaskManager = tm;
		}

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
			// Save the details
			mProgressCount = count;
			mProgressMax = max;
			mProgressMessage = message;

			// If empty, close any dialog
			if ((mProgressMessage == null || mProgressMessage.trim().length() == 0) && mProgressMax == mProgressCount) {
				if (mProgressDialog != null)
					ActivityWithTasks.this.dismissDialog(UniqueId.DIALOG_PROGRESS);
			} else {
				if (mProgressDialog == null) {
					// Create dialog if necessary
					ActivityWithTasks.this.showDialog(UniqueId.DIALOG_PROGRESS);
				} else {
					if (mProgressMax > 0 && mProgressDialog.isIndeterminate()) {
						// Destroy and recreate if dialog is unsuitable
						ActivityWithTasks.this.removeDialog(UniqueId.DIALOG_PROGRESS);
						ActivityWithTasks.this.showDialog(UniqueId.DIALOG_PROGRESS);
					} else {
						// Set message and display
						prepareProgressDialog(mProgressDialog, true);						
					}
				}				
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
	 * Handle ProgressDialog creation
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;

		switch(id) {
			case UniqueId.DIALOG_PROGRESS:
				dialog = createProgressDialog();
				break;
			default:
				dialog = super.onCreateDialog(id);
				break;
		}
		return dialog;
	}
	
	/**
	 * Handle ProgressDialog setup
	 */
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {

		switch(id) {
		case UniqueId.DIALOG_PROGRESS:
			prepareProgressDialog(dialog, true);
			break;
		default:
			super.onPrepareDialog(id, dialog);
			break;
		}
	}

	/**
	 * Setup the progress dialog.
	 * 
	 * If not called in the UI thread it will just queue initProgress().
	 * 
	 */
	private Dialog createProgressDialog() {
		ProgressDialog p;
		p = new ProgressDialog(this);
		prepareProgressDialog(p, false);
		return p;
	}

	/**
	 * Setup the ProgressDialog according to our needs
	 * 
	 * @param d		Dialog
	 * @param show	Flag indicating if show() should be called.
	 * 
	 */
	private void prepareProgressDialog(Dialog d, boolean show) {

		ProgressDialog p = (ProgressDialog)d;

		// Set style
		if (mProgressMax > 0) {
			p.setIndeterminate(false);
			p.setMax(mProgressMax);
			p.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		} else {
			p.setIndeterminate(true);					
			p.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		}

		// Set message and other attrs
		p.setMessage(mProgressMessage);
		p.setCancelable(true);
		p.setOnKeyListener(mDialogKeyListener);
		p.setOnCancelListener(mCancelHandler);
		// Show it if necessary
		if (show)
			p.show();
		p.setProgress(mProgressCount);
		// Save a reference
		mProgressDialog = p;
	}

	/**
	 * Handler for the user cancelling the progress dialog.
	 */
	private OnCancelListener mCancelHandler = new OnCancelListener() {
		public void onCancel(DialogInterface i) {
			if (mTaskManager != null)
				mTaskManager.cancelAllTasks();
			ActivityWithTasks.this.dismissDialog(UniqueId.DIALOG_PROGRESS);
			mProgressDialog = null;
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
					Toast.makeText(ActivityWithTasks.this, R.string.cancelling, Toast.LENGTH_LONG).show();
					if (mTaskManager != null)
						mTaskManager.cancelAllTasks();
					return true;
				}
			}
			return false;
		}
	};
	
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
