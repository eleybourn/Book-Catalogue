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

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.messaging.MessageSwitch;
import com.eleybourn.bookcatalogue.messaging.MessageSwitch.Message;
import com.eleybourn.bookcatalogue.utils.Logger;


/**
 * Class used to manager a collection of background threads for an ActivityWithTasks subclass.
 * Part of three components that make this easier:
 *  - TaskManager -- handles the management of multiple threads sharing a progressDialog
 *  - ActivityWithTasks -- uses a TaskManager (and communicates with it) to handle progress
 *    messages for threads. Deals with orientation changes in cooperation with TaskManager.
 *  - ManagedTask -- Background task that is managed by TaskManager and uses TaskManager to 
 *    do all display activities.
 *    
 * @author Philip Warner
 */
public class TaskManager {

	/**
	 * Allows other objects to know when a task completed. See SearchManager for an example.
	 * 
	 * @author Philip Warner
	 */
	public interface TaskManagerListener {
		void onTaskEnded(TaskManager manager, ManagedTask task);
		void onProgress(int count, int max, String message);
		void onToast(String message);
		void onFinished();
	}

	public interface TaskManagerController {
		void requestAbort();
		TaskManager getManager();
	}

	private final TaskManagerController mController = new TaskManagerController() {
		@Override
		public void requestAbort() {
			TaskManager.this.cancelAllTasks();
		}

		@Override
		public TaskManager getManager() {
			return TaskManager.this;
		}
	};
	
	public static class OnTaskEndedMessage implements Message<TaskManagerListener> {
		private final TaskManager mManager;
		private final ManagedTask mTask;

		public OnTaskEndedMessage(TaskManager manager, ManagedTask task) {
			mManager = manager;
			mTask = task;
		}

		@Override
		public boolean deliver(TaskManagerListener listener) {
			listener.onTaskEnded(mManager, mTask);
			return false;
		}
	}

    public static class OnProgressMessage implements Message<TaskManagerListener> {
		private final int mCount;
		private final int mMax;
		private final String mMessage;
		
		public OnProgressMessage(int count, int max, String message) {
			mCount = count;
			mMax = max;
			mMessage = message;
		}

		@Override
		public boolean deliver(TaskManagerListener listener) {
			listener.onProgress(mCount, mMax, mMessage);
			return false;
		}
	}

    public static class OnToastMessage implements Message<TaskManagerListener> {
		private final String mMessage;
		
		public OnToastMessage(String message) {
			mMessage = message;
		}

		@Override
		public boolean deliver(TaskManagerListener listener) {
			listener.onToast(mMessage);
			return false;
		}
	}

    public static class OnFinshedMessage implements Message<TaskManagerListener> {

		@Override
		public boolean deliver(TaskManagerListener listener) {
			listener.onFinished();
			return false;
		}
	}

    /* ====================================================================================================
	 *  OnTaskManagerListener handling
	 */

	/**
	 * 	STATIC Object for passing messages from background tasks to activities that may be recreated 
	 *  This object handles all underlying OnTaskEndedListener messages for every instance of this class.
	 */
	private static final MessageSwitch<TaskManagerListener, TaskManagerController> mMessageSwitch = new MessageSwitch<>();

	public static MessageSwitch<TaskManagerListener, TaskManagerController> getMessageSwitch() {
		return mMessageSwitch;
	}

	/** 
	 * Object for SENDING messages specific to this instance 
	 */
	private final long mMessageSenderId = mMessageSwitch.createSender(mController);

	public long getSenderId() {
		return mMessageSenderId;
	}

	/* ====================================================================================================
	 *  END OnTaskManagerListener handling
	 */

	
	
	// Current progress message to display, even if no tasks running. Setting to blank 
	// will remove the ProgressDialog
	String mBaseMessage = "";
	// Last task-related message displayed (used when rebuilding progress)
	String mProgressMessage = "";
	// Max value of progress. Set to 0 if no bar needed.
	int mProgressMax = 0;
	// Current value of progress.
	int mProgressCount = 0;

	/** Flag indicating tasks are being cancelled. This is reset when a new task is added */
	private boolean mCancelling = false;
	
	/** Flag indicating the TaskManager is terminating; will close after last task exits */
	private boolean mIsClosing = false;

	// List of tasks being managed by this object
    final ArrayList<TaskInfo> mTasks = new ArrayList<>();

	// Task info for each ManagedTask object
	private class TaskInfo {
		final ManagedTask 		task;
		String				progressMessage;
		int					progressMax;
		int					progressCurrent;
		TaskInfo(ManagedTask t) {
			this(t, 0, 0, "");
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
	 */
	TaskManager() {
	}

	/**
	 * Add a task to this object. Ignores duplicates if already present.
	 * 
	 * @param t		Task to add
	 */
	void addTask(ManagedTask t) {
		if (mIsClosing)
			throw new RuntimeException("Can not add a task when closing down");

		mCancelling = false;

		synchronized(mTasks) {
			if (getTaskInfo(t) == null) {
				mTasks.add(new TaskInfo(t));
				ManagedTask.getMessageSwitch().addListener(t.getSenderId(), mTaskListener, true);
			}
		}
	}

	/**
	 * Listen for task messages, specifically, task termination
	 */
	private final ManagedTask.TaskListener mTaskListener = TaskManager.this::onTaskFinished;

	/**
	 * Accessor
	 */
	public boolean isCancelling() {
		return mCancelling;
	}

	/**
	 * Called when the onTaskFinished message is received by the listener object.
	 */
	private void onTaskFinished(ManagedTask task) {
		boolean doClose;
		
		// Remove from the list of tasks. From now on, it should
		// not send any progress requests.
		synchronized(mTasks) {
			for(TaskInfo i : mTasks) {
				if (i.task == task) {
					mTasks.remove(i);
					break;
				}
			}
			doClose = (mIsClosing && mTasks.isEmpty());
		}

		// Tell all listeners that it has ended.
		mMessageSwitch.send(mMessageSenderId, new OnTaskEndedMessage(TaskManager.this, task));

		// Update the progress dialog
		updateProgressDialog();

		// Call close() if necessary
		if (doClose)
			close();
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
	 * Utility routine to cancel all tasks.
	 */
	public void cancelAllTasks() {
		synchronized(mTasks) {
			mCancelling = true;
			for(TaskInfo t : mTasks) {
				t.task.cancelTask();
			}
		}		
	}

	/**
	 * Update the base progress message. Used (generally) by the ActivityWithTasks to
	 * display some text above the task info. Set to blank to ensure ProgressDialog will
	 * be removed.
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
        }
	}

	/**
	 * If in the UI thread, update the progress dialog, otherwise resubmit to UI thread.
	 */
	private void updateProgressDialog() {
		try {
			// Start with the base message if present
			if (mBaseMessage != null && !mBaseMessage.isEmpty())
				mProgressMessage = mBaseMessage;
			else
				mProgressMessage = "";

			synchronized(mTasks) {
				// Append each task message
				if (!mTasks.isEmpty()) {
					if (!mProgressMessage.isEmpty())
						mProgressMessage += "\n";
					if (mTasks.size() == 1) {
						String oneMsg = mTasks.get(0).progressMessage;
						if (oneMsg != null && !oneMsg.trim().isEmpty())
							mProgressMessage += oneMsg;						
					} else {
						StringBuilder taskMessages = new StringBuilder();
						boolean got = false;
						// Don't append blank messages; allows tasks to hide.
						for(int i = 0; i < mTasks.size(); i++) {
							String oneMsg = mTasks.get(i).progressMessage;
							if (oneMsg != null && !oneMsg.trim().isEmpty()) {
								if (got)
									taskMessages.append("\n");
								else
									got = true;
								taskMessages.append(" - ").append(oneMsg);
							}
						}
						if (taskMessages.length() > 0)
							mProgressMessage += taskMessages;
					}
				}				
			}

			// Sum the current & max values for each active task. This will be our new values.
			mProgressMax = 0;
			mProgressCount = 0;
			synchronized(mTasks) {
				for (TaskInfo t : mTasks) {
					mProgressMax += t.progressMax;
					mProgressCount += t.progressCurrent;
				}				
			}

			// Now, display it if we have a context; if it is empty and complete, delete the progress.
			mMessageSwitch.send(mMessageSenderId, new OnProgressMessage(mProgressCount, mProgressMax, mProgressMessage));
		} catch (Exception e) {
			Logger.logError(e, "Error updating progress");
		}
	}

	/**
	 * Make a toast message for the caller. Queue in UI thread if necessary.
	 * 
	 * @param message	Message to send
	 */
	public void doToast(String message) {
		mMessageSwitch.send(mMessageSenderId, new OnToastMessage(message));
	}

	
	/**
	 * Lookup the TaskInfo for the passed task.
	 * 
	 * @param task		Task to lookup
	 *
	 * @return			TaskInfo associated with task.
	 */
	private TaskInfo getTaskInfo(ManagedTask task) {
		synchronized(mTasks) {
			for(TaskInfo t : mTasks) {
				if (t.task == task) {
					return t;
				}
			}			
		}
		return null;
	}

	/**
	 * Set the maximum value for progress for the passed task.
	 */
	public void setMax(ManagedTask task, int max) {
		TaskInfo t = getTaskInfo(task);
		if (t != null) {
			t.progressMax = max;
			updateProgressDialog();
        }
	}

	/**
	 * Set the count value for progress for the passed task.
	 */
	public void setCount(ManagedTask task, int count) {
		TaskInfo t = getTaskInfo(task);
		if (t != null) {
			t.progressCurrent = count;
			updateProgressDialog();
        }
	}

	/**
	 * Cancel all tasks and close dialogs then cleanup; if no tasks running, just close dialogs and cleanup
	 */
	protected void close() {
		System.out.println("DBG: Task Manager close requested");
		mIsClosing = true;
		synchronized(mTasks) {
			for(TaskInfo t : mTasks) {
				t.task.cancelTask();
			}
		}
	}
}
