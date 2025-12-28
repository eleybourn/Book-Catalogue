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

import java.nio.channels.ClosedByInterruptException;

import com.eleybourn.bookcatalogue.messaging.MessageSwitch;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Base class for handling tasks in background while displaying a ProgressDialog.
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
abstract public class ManagedTask extends Thread {
	// The manager who we will use for progress etc, and who we will inform about our state.
	protected TaskManager mManager;
	// Flag indicating the main onRun method has completed. Set in call do doFinish() in the UI thread.
	private boolean mFinished = false;
	// Indicates the user has requested a cancel. Up to subclass to decice what to do. Set by TaskManager.
	private boolean mCancelFlg = false;

	//
	// Called when the task has finished, but *only* if the TaskManager has a context (ie. is
	// attached to an Activity). If the task manager is *not* attached to an activity, then onFinis()
	// will be called in the reconnect() call.
	//
	// The subclass must return 'true' if it was able to execute all required code in any required
	// TaskHandler. It does not matter if that code failed or succeeded, only that the Taskhandler 
	// was executed (if necessary). TaskHandler objects will be cleared by the disconnect() call
	// and reset by the reconnect() call.
	//
	abstract protected void onThreadFinish();
	// Called to do the main thread work. Can use doProgress() and doToast() to display messages.
	abstract protected void onRun() throws InterruptedException, ClosedByInterruptException;

	/**
	 * Utility routine to ask the Taskmanager to get a String from a resource ID.
	 * 
	 * @param id	Resource ID
	 * 
	 * @return		Result
	 */
	String getString(int id) {
		return BookCatalogueApp.getResourceString(id);
	}

	/**
	 * Constructor.
	 * 
	 * @param manager			Associated task manager
	 * @param taskHandler		Object to inform of life0cycle events
	 * 
	 */
	public ManagedTask(TaskManager manager) {
		// Must be non-null
		if (manager == null)
			throw new IllegalArgumentException();

		// Save the stuff for mater
		mManager = manager;
		// Add to my manager
		mManager.addTask(this);
	}

	/**
	 * Utility to ask the TaskManager to update the ProgressDialog
	 * 
	 * @param message	Message to display
	 * @param count		Counter. 0 if Max not set.
	 */
	public void doProgress(String message, int count) {
		mManager.doProgress(this, message, count);
	}

	/**
	 * Utility to ask TaskManager to display a toast message
	 * 
	 * @param message	Message to display
	 */
	public void doToast(String message) {
		mManager.doToast(message);
	}

	/**
	 * Executed in main task thread.
	 */
	@Override
	public void run() {

		try {
			onRun();			
		} catch (InterruptedException e) {
			mCancelFlg = true;
		} catch (ClosedByInterruptException e) {
			mCancelFlg = true;
		} catch (Exception e) {
			Logger.logError(e);
		}

		mFinished = true;
		// Let the implementation know it is finished
		onThreadFinish();

		// Queue the 'onTaskFinished' message; this should also inform the TaskManager
		mMessageSwitch.send(mMessageSenderId, new MessageSwitch.Message<TaskListener>() {
			@Override
			public boolean deliver(TaskListener listener) {
				listener.onTaskFinished(ManagedTask.this);
				return false;
			}}
		);
	}

	/**
	 * Mark this thread as 'cancelled'
	 */
	public void cancelTask() {
		mCancelFlg = true;
		this.interrupt();
	}

	/**
	 * Accessor to check if task cancelled.
	 * @return true/false depending on state
	 */
	public boolean isCancelled() {
		return mCancelFlg;
	}

	/**
	 * Accessor to check if task finished.
	 * @return true/false depending on state
	 */
	public boolean isFinished() {
		return mFinished;
	}


	/* ===================================================================== 
	 * Message Switchboard implementation
	 * =====================================================================
	 */
	/**
	 * Allows other objects to know when a task completed.
	 * 
	 * @author Philip Warner
	 */
	public interface TaskListener {
		void onTaskFinished(ManagedTask t);
	}

	/**
	 * Controller interface for this object
	 */
	public interface TaskController {
		void requestAbort();
		ManagedTask getTask();
	}

	/**
	 * Controller instance for this specific task
	 */
	private final TaskController mController = new TaskController() {
		@Override
		public void requestAbort() {
			ManagedTask.this.cancelTask();
		}
		@Override
		public ManagedTask getTask() {
			return ManagedTask.this;
		}
	};

	/**
	 * 	STATIC Object for passing messages from background tasks to activities that may be recreated 
	 *
	 *  This object handles all underlying OnTaskEndedListener messages for every instance of this class.
	 */
	protected static class TaskSwitch extends MessageSwitch<TaskListener, TaskController> {}

    private static final TaskSwitch mMessageSwitch = new TaskSwitch();
	protected static final TaskSwitch getMessageSwitch() { return mMessageSwitch; }

	private final long mMessageSenderId = mMessageSwitch.createSender(mController);
	public long getSenderId() { return mMessageSenderId; }
}
