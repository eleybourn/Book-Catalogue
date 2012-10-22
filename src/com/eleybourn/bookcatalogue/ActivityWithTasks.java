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

import java.util.Hashtable;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.TaskManager.TaskManagerListener;

/**
 * Class to used as a base class for any Activity that wants to run one or more threads that
 * use a ProgressDialog. Even a good choice for Activities that run a ProgressDialog because
 * PDs don't play well with orientation changes.
 * 
 * NOTE: If an activity needs to use onRetainNonConfigurationInstance() it should use the
 * overridable method onRetainNonConfigurationInstance() to save object references then use
 * getLastNonConfigurationInstance(String key) to retrieve the values.
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
	protected Long mTaskManagerId = null;
	protected TaskManager mTaskManager = null;
	protected ProgressDialog mProgressDialog = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (savedInstanceState != null) {
			mTaskManagerId = savedInstanceState.getLong("TaskManagerId");
			mTaskManager = TaskManager.getMessageSwitch().getController(mTaskManagerId).getManager();
		} else {
			TaskManager tm = new TaskManager();
			mTaskManagerId = tm.getSenderId();
			mTaskManager = tm;
		}
		if (mTaskManager != null)
			TaskManager.getMessageSwitch().addListener(mTaskManagerId, mTaskListener, true);
	}

	private TaskManagerListener mTaskListener = new TaskManagerListener() {

		@Override
		public void onTaskEnded(TaskManager manager, ManagedTask task) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onProgress(int count, int max, String message) {
			mProgressCount = count;
			mProgressMax = max;
			mProgressMessage = message;
			
			if (mProgressMessage.trim().length() == 0 && mProgressMax == mProgressCount) {
				if (mProgressDialog != null)
					ActivityWithTasks.this.dismissDialog(UniqueId.DIALOG_PROGRESS);
			} else {
				if (mProgressDialog == null) {
					ActivityWithTasks.this.showDialog(UniqueId.DIALOG_PROGRESS);
				} else {
					if (mProgressMax > 0 && mProgressDialog.isIndeterminate()) {
						ActivityWithTasks.this.dismissDialog(UniqueId.DIALOG_PROGRESS);
						ActivityWithTasks.this.showDialog(UniqueId.DIALOG_PROGRESS);
					} else {
						prepareProgressDialog(mProgressDialog);						
					}
				}				
			}

		}

		@Override
		public void onToast(String message) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onFinished() {
			// TODO Auto-generated method stub
			
		}};

		@Override
		protected Dialog onCreateDialog(int id) {
			Dialog dialog;

			switch(id) {
			case UniqueId.DIALOG_PROGRESS:
				dialog = createProgressDialog();
				break;
			default:
				dialog = super.onCreateDialog(id);
			}
			return dialog;
		}
		
		@Override
		protected void onPrepareDialog(int id, Dialog dialog) {
			switch(id) {
			case UniqueId.DIALOG_PROGRESS:
				prepareProgressDialog(dialog);
				break;
			default:
				super.onPrepareDialog(id, dialog);
			}
		}

	private int mProgressMax = 0;
	private int mProgressCount = 0;
	private String mProgressMessage = "";

	/**
	 * Setup the progress dialog.
	 * 
	 * If not called in the UI thread it will just queue initProgress().
	 * 
	 */
	private Dialog createProgressDialog() {
		ProgressDialog p;
		p = new ProgressDialog(this);
		return p;
	}
	
	private void prepareProgressDialog(Dialog d) {
		ProgressDialog p = (ProgressDialog)d;
		if (mProgressMax > 0) {
			p.setIndeterminate(false);
			p.setMax(mProgressMax);
			p.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		} else {
			p.setIndeterminate(true);					
			p.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		}
		p.setMessage(mProgressMessage);
		p.setCancelable(true);
		p.setOnKeyListener(mDialogKeyListener);
		p.setOnCancelListener(mCancelHandler);
		p.show();
		p.setProgress(mProgressCount);
		mProgressDialog = p;
	}

	/**
	 * Handler for the user cancelling the progress dialog.
	 */
	private OnCancelListener mCancelHandler = new OnCancelListener() {
		public void onCancel(DialogInterface i) {
			if (mTaskManager != null)
				mTaskManager.close();
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
						mTaskManager.close();
					return true;
				}
			}
			return false;
		}
	};
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putLong("TaskManagerId", mTaskManagerId);
	}
	
//	private class NonConfigInstance {
//		TaskManager taskManager = mTaskManager;
//		Hashtable<String,Object> extra = new Hashtable<String,Object>();
//	}
//
//	/**
//	 * Ensure the TaskManager is restored.
//	 */
//	@Override
//	protected void onRestoreInstanceState(Bundle inState) {
//		NonConfigInstance info = (NonConfigInstance) getLastNonConfigurationInstance();
//		if (info != null && info.taskManager != null) {
//			mTaskManager = info.taskManager;
//			mTaskManager.reconnect(this);		
//		}
//		super.onRestoreInstanceState(inState);
//	}
//
//	/**
//	 * Ensure the TaskManager is saved and call a onRetainNonConfigurationInstance(store)
//	 * to allow subclasses to save data as well.
//	 * 
//	 * Marked as final to ensure this does not get overwritten.
//	 */
//	@Override
//	final public Object onRetainNonConfigurationInstance() {
//		NonConfigInstance info = new NonConfigInstance();
//		if (mTaskManager != null) {
//			mTaskManager.disconnect();
//			mTaskManager = null;
//		}
//		onRetainNonConfigurationInstance(info.extra);
//		return info;
//	}
//
//	/**
//	 * Method to get a value from the store that was saved in onRetainNonConfigurationInstance().
//	 * 
//	 * @param key	Key to lookup
//	 * 
//	 * @return		Object from key in hashtable
//	 */
//	Object getLastNonConfigurationInstance(String key) {
//		NonConfigInstance info = (NonConfigInstance) getLastNonConfigurationInstance();
//		if (info != null && info.extra.containsKey(key)) {
//			return info.extra.get(key);
//		} else {
//			return null;
//		}
//	}
//
//	/**
//	 * Passed a task find the appropriate handler to use. Called on reconnect().
//	 * 
//	 * @param t		Task reference to lookup
//	 * 
//	 * @return		Appropriate handler for the task
//	 */
//	abstract TaskHandler getTaskHandler(ManagedTask t);
//	
//	/**
//	 * Method to save names instance state objects.
//	 * 
//	 * @param store
//	 */
//	void onRetainNonConfigurationInstance(Hashtable<String,Object> store) {};
}
