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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Base class for handling tasks in background while displaying a ProgressDialog.
 * Copes with deconstruction of the underlying activity BUT that activity MUST:
 * 
 *  - call reconnect(...) in onRestoreInstanceState.
 *  - save the task in onRetainNonConfigurationInstance
 *  - call disconnect() in onRetainNonConfigurationInstance, possibly even in onPause()
 * 
 * @author Grunthos
 */
abstract public class ManagedTask extends Thread {
	protected TaskManager mManager;
	private boolean mFinished = false;
	private boolean mCancelFlg = false;
	private TaskHandler mTaskHandler;
	// Handler for UI thread messages
	private Handler mMessageHandler;

	abstract protected boolean onFinish();
	abstract protected void onRun();
	abstract protected void onMessage(Message msg);

	/**
	 * Interface allowing the caller to be informed of events in this thread.
	 * 
	 * @author Grunthos
	 */
	public interface TaskHandler {
	}

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

	@Override
	public void start() {
		super.start();
	}

	/**
	 * Constructor.
	 * 
	 * @param ctx				Context to use for constructing progressdialog
	 * @param overwrite			Whether to overwrite details
	 * @param books				Cursor to scan
	 * @param lookupHandler		Interface object to handle events in this thread.
	 * 
	 */
	public ManagedTask(TaskManager manager, TaskHandler taskHandler) {
		if (manager == null)
			throw new IllegalArgumentException();

		mManager = manager;
		mTaskHandler = taskHandler;
		mMessageHandler = new TaskMessageHandler();
		mManager.addTask(this);
		//mDbHelper = new CatalogueDBAdapter(mContext);
	}

	public void doProgress(String message, int count) {
		mManager.doProgress(this, message, count);
	}

	public void doToast(String message) {
		mManager.doToast(message);
	}

	@Override
	public void run() {

		onRun();

		mMessageHandler.post(new Runnable() {
			public void run() { doFinish(); };
		});
	}

	public void removeFromManager() {
		mManager.removeTask(this);
	}

	private void doFinish() {
		mFinished = true;	
		if (mManager.getContext() != null) {
			if (onFinish()) {
				this.removeFromManager();
				mManager.taskEnded(this);
			}
		}
	}

	public void cancelTask() {
		mCancelFlg = true;
	}

	/**
	 * Accessor to check if task finished.
	 * @return true/false depending on state
	 */
	public boolean isFinished() {
		return mFinished;
	}

	/**
	 * Accessor to check if task cancelled.
	 * @return true/false depending on state
	 */
	public boolean isCancelled() {
		return mCancelFlg;
	}

	public Message obtainMessage() {
		return mMessageHandler.obtainMessage();
	}

	void sendMessage(Message msg) {
		mMessageHandler.sendMessage(msg);
	}

	public void reconnect(ActivityWithTasks ctx, TaskHandler taskHandler) {
		synchronized(this) {
			if (mFinished) {
				doFinish();
			}
		}
	}

	public void disconnect() {
	}

	private class TaskMessageHandler extends Handler {
		public void handleMessage(Message msg) {
			onMessage(msg);
		}		
	}
}
