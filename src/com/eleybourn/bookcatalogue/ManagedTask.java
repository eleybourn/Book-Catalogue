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

import android.os.Handler;
import android.os.Message;

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
	// Each task has a handler object that can be used to communicate with the main thread.
	private TaskHandler mTaskHandler;
	// Handler for UI thread messages. Used to manage thread-based comms.
	protected Handler mMessageHandler;

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
	abstract protected boolean onFinish();
	// Called to do the main thread work. Can use doProgress() and doToast() to display messages.
	abstract protected void onRun() throws InterruptedException, ClosedByInterruptException;
	// Called to handle any messages posted via the sendMessage() method. Messages can be constructed
	// by calling obtainMessage().
	abstract protected void onMessage(Message msg);

	/**
	 * Interface allowing the caller to be informed of events in this thread. Stug that can be extended
	 * if necessary by a subclass.
	 * 
	 * @author Philip Warner
	 */
	public interface TaskHandler {
	}

	/**
	 * Utility routine to ask the Taskmanager to get a String from a resource ID.
	 * 
	 * @param id	Resource ID
	 * 
	 * @return		Result
	 */
	String getString(int id) {
		return mManager.getString(id);
	}

	/**
	 * Accessor for the task handler.
	 * 
	 * @return
	 */
	TaskHandler getTaskHandler() {
		return mTaskHandler;
	}

	/**
	 * Constructor.
	 * 
	 * @param manager			Associated task manager
	 * @param taskHandler		Object to inform of life0cycle events
	 * 
	 */
	public ManagedTask(TaskManager manager, TaskHandler taskHandler) {
		// Must be non-null
		if (manager == null)
			throw new IllegalArgumentException();

		// Save the stuff for mater
		mManager = manager;
		mTaskHandler = taskHandler;
		// Add to my manager
		mManager.addTask(this);
		// Create a new Handler.
		mMessageHandler = new TaskMessageHandler();
		// Let the subclass create DB if they need it for now.
		// mDbHelper = new CatalogueDBAdapter(mContext);
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
		mMessageHandler.post(new Runnable() {
			public void run() { doFinish(); };
		});
	}

	/**
	 * Called in UI thread to call the onFinish() method and, if successful,
	 * tell the task manager it has ended.
	 */
	private void doFinish() {
		mFinished = true;	
		if (mManager.isConnected()) {
			if (onFinish()) {
				mManager.taskEnded(this);
			}
		}
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

	/**
	 * Utility for subclass to get a Message object.
	 * @return
	 */
	public Message obtainMessage() {
		return mMessageHandler.obtainMessage();
	}

	/**
	 * Utility for subclass to send a Message to the UI thread.
	 * @param msg
	 */
	public void sendMessage(Message msg) {
		mMessageHandler.sendMessage(msg);
	}

	/**
	 * Called when an activity reconnects with the associated TaskManager to 
	 * allow the task to get a new taskHandler.
	 * 
	 * @param taskHandler
	 */
	public void reconnect(TaskHandler taskHandler) {
		mTaskHandler = taskHandler;
		synchronized(this) {
			if (mFinished) {
				doFinish();
			}
		}
	}

	/**
	 * Called when an activity disconnects; must remove taskHandler.
	 */
	public void disconnect() {
		mTaskHandler = null;
	}

	/**
	 * Dispatcher for messages to the UI thread.
	 * 
	 * @author Philip Warner
	 *
	 */
	private class TaskMessageHandler extends Handler {
		public void handleMessage(Message msg) {
			onMessage(msg);
		}		
	}
}
