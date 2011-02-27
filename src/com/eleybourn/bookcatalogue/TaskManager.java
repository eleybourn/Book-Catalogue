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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;
import com.eleybourn.bookcatalogue.UpdateThumbnailsThread.BookInfo;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Class used to manager a collection of backgroud threads for an AcitivityWithTasks subclass.
 * 
 * Part of three components that make this easier:
 *  - TaskManager -- handles the management of multiple threads sharing a progressDialog
 *  - ActivityWithTasks -- uses a TaskManager (and communicates with it) to handle progress
 *    messages for threads. Deals with orientation changes in cooperation with TaskManager.
 *  - ManagedTask -- Background task that is managed by TaskManager and uses TaskManager to 
 *    do all display activities.
 *    
 * @author Grunthos
 */
public class TaskManager {
	// Calling activity
	private ActivityWithTasks mContext = null;
	// ProgressDialog
	private ProgressDialog mProgress = null;
	// Ref to UI thread; assumed to be thread that created this object
	private WeakReference<Thread> mUiThread;
	// Handler for UI thread messages
	private Handler mMessageHandler;

	private ArrayList<OnTaskEndedListener> mOnTaskEndedListeners = new ArrayList<OnTaskEndedListener>();
	
	// Current progress message to display, even if no tasks running. Setting to blank 
	// will remove the ProgressDialog
	String mBaseMessage = "";
	// Last task-related message displayed (used when rebuilding progress)
	String mProgressMessage = "";
	// Max value of progress. Set to 0 if no bar needed.
	int mProgressMax = 0;
	// Current value of progress.
	int mProgressCount = 0;

	// List of tasks being managed by this object
	ArrayList<TaskInfo> mTasks = new ArrayList<TaskInfo> ();

	// Task info for each ManagedTask object
	private class TaskInfo {
		ManagedTask 		task;
		String				progressMessage;
		int					progressMax;
		int					progressCurrent;
		TaskInfo(ManagedTask t) {
			this(t, 0, 0, "Starting");
		}
		TaskInfo(ManagedTask t, int max, int curr, String message) {
			task = t;
			progressMax = max;
			progressCurrent = curr;
			progressMessage = message;
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param ctx	An ActivityWithTasks that owns this TaskManager.
	 */
	TaskManager(ActivityWithTasks activity) {
		// Must not be null
		if (activity == null)
			throw new IllegalArgumentException();
		mContext = activity;	
		// Assumes the current thread is the UI thread.
		mUiThread = new WeakReference<Thread>(Thread.currentThread());
		// New handler to send messages to the UI thread.
		mMessageHandler = new MessageHandler();
	}

	/**
	 * Allows other objects to know when a task completed. See SearchManager for an example.
	 * 
	 * @author Grunthos
	 */
	public interface OnTaskEndedListener {
		void onTaskEnded(TaskManager manager, ManagedTask task);
	}

	/**
	 * Add a listener.
	 * 
	 * @param listener	Object to add
	 */
	public void addOnTaskEndedListener(OnTaskEndedListener listener) {
		mOnTaskEndedListeners.add(listener);
	}

	/**
	 * Remove a listener
	 *
	 * @param listener	object to remove
	 */
	public void removeOnTaskEndedListener(OnTaskEndedListener listener) {
		mOnTaskEndedListeners.remove(listener);
	}

	/**
	 * Add a task to this object. Ignores duplicates if already present.
	 * 
	 * @param t		Task to add
	 */
	void addTask(ManagedTask t) {
		synchronized(this) {
			if (getTaskInfo(t) == null)
					mTasks.add(new TaskInfo(t));
		}
	}

	/**
	 * Called by a task when it ends.
	 * 
	 * @param task
	 */
	public void taskEnded(ManagedTask task) {
		// Remove from the list of tasks. From now on, it should
		// not send any progress requests.
		synchronized(this) {
			for(TaskInfo i : mTasks) {
				if (i.task == task) {
					mTasks.remove(i);
					break;
				}
			}
		}

		// Tell all listeners that it has ended.
		for(OnTaskEndedListener l : mOnTaskEndedListeners)
			try {
				l.onTaskEnded(this, task);
			} catch (Exception e) {
				Log.e("BC","OnTaskEndedListener failed", e);
			}

		// Update the progress dialog
		updateProgressDialog();
	}

	/**
	 * Get the number of tasks currently managed.
	 * 
	 * @return	Number of tasks
	 */
	public int count() {
		return mTasks.size();
	}

	/**
	 * Called to completely remove the ProgressDialog as a result of disconnect() or
	 * having no messages to display.
	 */
	private void destroyProgress() {
		synchronized(this) {
			if (mProgress != null) {
				Log.i("BC", "Deleting progress");
				try { 
					mProgress.dismiss(); 
				} catch (Exception e) {
					Log.e("BC", "Failed to delete progress", e);
				};
				mProgress = null;
			}
		}
	}

	/**
	 * Utility for ManagedTask objects to get a string from an ID.
	 * 
	 * @param id		String resource ID
	 * 
	 * @return			The associated string
	 */
	public String getString(int id) {
		if (mContext != null) {
			return mContext.getResources().getString(id);
		} else {
			return "No Context";
		}
	}

	/**
	 * Return the associated context object.
	 * 
	 * @return	The context
	 */
	public Context getContext() {
		synchronized(this) {
			return mContext;
		}
	}

	/**
	 * Called by ActivityWithTasks to reconnect to this TaskManager (eg. this
	 * is called after an orientation change). It asks the ActivityWithTasks
	 * for the TaskHandler for each task then calls the tasks reconnect() method.
	 * 
	 * @param context	ActivityWithTasks to connect
	 */
	public void reconnect(ActivityWithTasks context) {
		mContext = context;
		if (mTasks.size() > 0) {
			initProgress();
			for(TaskInfo t : mTasks) {
				TaskHandler h = context.getTaskHandler(t.task);
				t.task.reconnect(h);
			}			
		}
	}

	/**
	 * Disconnect from the associated ActivityWithTasks. Let each task know.
	 */
	public void disconnect() {
		mContext = null;
		destroyProgress();
		for(TaskInfo t : mTasks) {
			t.task.disconnect();
		}
	}

	/**
	 * Handler for internal UI thread messages.
	 * 
	 * @author Grunthos
	 */
	private class MessageHandler extends Handler {
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			if (b.containsKey("__internal")) {
				String kind = b.getString("__internal");
				if (kind.equals("toast")) {
					doToast(b.getString("message"));
				} else if (kind.equals("initProgress")) {
					initProgress();
				} else {
					updateProgressDialog();
				}
			} else {
				throw new RuntimeException("Unknown message");
			}
		}		
	}

	/**
	 * Handler for the user cancelling the progress dialog.
	 */
	private OnCancelListener mCancelHandler = new OnCancelListener() {
		public void onCancel(DialogInterface i) {
			Log.i("BookCatalogue", "Cancelling Tasks...");
			for(TaskInfo t : mTasks) {
				t.task.cancelTask();
			}
		}
	};

	/**
	 * Update the base progress message. Used (gereally) by the ActivityWuthTasks to 
	 * display some text above the task info. Set to blank to ensure ProgressDialog will
	 * be removed.
	 * 
	 * @param message
	 */
	public void doProgress(String message) {
		mBaseMessage = message;
		updateProgressDialog();
	}
	
	/**
	 * Update the current ProgressDialog based on information about a task.
	 * 
	 * @param task		The task associated with this message
	 * @param message	Message text
	 * @param count		Counter for progress
	 */
	public void doProgress(ManagedTask task, String message, int count) {
		TaskInfo t = getTaskInfo(task);
		if (t != null) {
			t.progressMessage = message;
			t.progressCurrent = count;
			updateProgressDialog();
			return;
		}
	}

	/**
	 * If in the UI thread, update the progress dialog, otherwise resubmit to UI thread.
	 */
	private void updateProgressDialog() {
		if (Thread.currentThread() == mUiThread.get()) {
			// Start with the base message if present
			if (mBaseMessage != null && mBaseMessage.length() > 0)
				mProgressMessage = mBaseMessage;
			else
				mProgressMessage = "";

			// Append each task message
			if (mTasks.size() > 0) {
				if (mProgressMessage.length() > 0)
					mProgressMessage += "\n";
				if (mTasks.size() == 1) {
					mProgressMessage += mTasks.get(0).progressMessage;						
				} else {
					mProgressMessage += "1: " + mTasks.get(0).progressMessage;
					for(int i = 1; i < mTasks.size(); i++) {
						mProgressMessage += "\n" + (i+1) + ": " + mTasks.get(i).progressMessage;
					}
				}
			}

			// Sum the current & max values for each active task. This will be our new values.
			mProgressMax = 0;
			mProgressCount = 0;
			for (TaskInfo t : mTasks) {
				mProgressMax += t.progressMax;
				mProgressCount += t.progressCurrent;
			}

			// Now, display it if we have a context; if it is empty and complete, delete the progress.
			synchronized(this) {
				if (mProgressMessage.trim().length() == 0 && mProgressMax == mProgressCount) {
					destroyProgress();
				} else {
					Context ctx = getContext();
					if (mProgress == null) {
						if (ctx != null) {
							initProgress();
						}
					}
					if (mProgress != null && ctx != null) {
						mProgress.setMessage(mProgressMessage);
						if (mProgressMax > 0) {
							mProgress.setMax(mProgressMax);
							mProgress.setProgress(mProgressCount);						
						}
					}	
				}
			}
		} else {
			/* Send message to the handler */
			Message msg = mMessageHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putString("__internal", "progress");
			msg.setData(b);
			mMessageHandler.sendMessage(msg);
		}
	}

	/**
	 * Make a toast message for the caller. Queue in UI thread if necessary.
	 * 
	 * @param message	Message to send
	 */
	public void doToast(String message) {
		if (Thread.currentThread() == mUiThread.get()) {
			synchronized(this) {
				Context ctx = getContext();
				if (ctx != null)
					android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_LONG).show();			
			}
		} else {
			/* Send message to the handler */
			Message msg = mMessageHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putString("__internal", "toast");
			b.putString("message", message);
			msg.setData(b);
			mMessageHandler.sendMessage(msg);			
		}
	}

	/**
	 * Setup the progress dialog.
	 * 
	 * If not called in the UI thread it will just queue initProgress().
	 * 
	 */
	private void initProgress() {

		if (Thread.currentThread() == mUiThread.get()) {
			synchronized(this) {
				// Get the context; if null or we already have a PD, just skip
				Context ctx = getContext();
				if (ctx != null && mProgress == null) {
					Log.i("BC", "Creating progress");
					mProgress = new ProgressDialog(ctx);
					if (mProgressMax > 0) {
						mProgress.setIndeterminate(false);
						mProgress.setMax(mProgressMax);
						mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
					} else {
						mProgress.setIndeterminate(true);					
						mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
					}
					mProgress.setMessage(mProgressMessage);
					mProgress.setCancelable(true);
					mProgress.setOnCancelListener(mCancelHandler);
					mProgress.show();
					mProgress.setProgress(mProgressCount);
				}
			}		
		} else {
			/* Send message to the handler */
			Message msg = mMessageHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putString("__internal", "initProgress");
			msg.setData(b);
			mMessageHandler.sendMessage(msg);						
		}
	}

	/**
	 * Lookup the TaskInfo for the passed task.
	 * 
	 * @param task		Task to lookup
	 *
	 * @return			TaskInfo associated with task.
	 */
	private TaskInfo getTaskInfo(ManagedTask task) {
		for(TaskInfo t : mTasks) {
			if (t.task == task) {
				return t;
			}
		}		
		return null;
	}

	/**
	 * Set the maximum value for progress for the passed task.
	 * 
	 * @param task
	 * @param max
	 */
	public void setMax(ManagedTask task, int max) {
		TaskInfo t = getTaskInfo(task);
		if (t != null) {
			t.progressMax = max;
			updateProgressDialog();
			return;
		}
	}

}
