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
 * Base class for handling tasks in background while displating a ProgressDialog.
 * Copes with deconstruction of the underlying activity BUT that activity MUST:
 * 
 *  - call reconnect(...) in onRestoreInstanceState.
 *  - save the task in onRetainNonConfigurationInstance
 *  - call disconnect() in onRetainNonConfigurationInstance, possibly even in onPause()
 * 
 * @author Grunthos
 */
abstract public class TaskWithProgress extends Thread {
	private Context mContext;
	private Handler mHandler;
	private ProgressDialog mProgress = null;
	private boolean mFinished = false;
	private boolean mCancelFlg = false;
	private WeakReference<Thread> mUiThread;
	private TaskHandler mTaskHandler;

	protected int mProgressCount = 0;
	protected int mProgressMax = 0;
	//protected CatalogueDBAdapter mDbHelper;

	private String mLastProgressMessage = "Updating...";

	abstract protected void onFinish();
	abstract protected void onRun();
	abstract protected void onMessage(Message msg);

	/**
	 * Interface allowing the caller to be informed of events in this thread.
	 * 
	 * @author Grunthos
	 */
	public interface TaskHandler {
		/**
		 * Lookup a resource string
		 * @param id
		 * @return
		 */
		String getString(int id);
	}

	String getString(int id) {
		return mTaskHandler.getString(id);
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
		if (mProgress == null) {
			if (getContext() != null) {
				initProgress();
			}
		}
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
	public TaskWithProgress(Context ctx, TaskHandler taskHandler) {
		if (ctx == null)
			throw new IllegalArgumentException();

		mUiThread = new WeakReference<Thread>(Thread.currentThread());
		mContext = ctx;
		mHandler = new ImportHandler();
		mTaskHandler = taskHandler;
		//mDbHelper = new CatalogueDBAdapter(mContext);
	}

	@Override
	public void run() {

		onRun();

		mHandler.post(new Runnable() {
			public void run() { doFinish(); };
		});
	}

	private void doFinish() {
		if (mContext == null) {
			// We are disconnected...wait for a reconnect.
			mFinished = true;	
			return;			
		}

		onFinish();
		synchronized(this) {
			if (mProgress != null) {
				try { mProgress.dismiss(); } catch (Exception e) {};
			}
			mProgress = null;
			mContext = null;
			mFinished = true;	
		}
	}

	/**
	 * Update the current ProgressDialog.
	 * 
	 * NOTE: This is (or should be) ONLY called from the UI thread.
	 * 
	 * @param message	Message text
	 * @param count		Counter for progress
	 */
	public void doProgress(String message, int count) {
		if (Thread.currentThread() == mUiThread.get()) {
			synchronized(this) {
				Context ctx = getContext();
				if (mProgress == null) {
					if (ctx != null) {
						initProgress();
					}
				}
				if (mProgress != null && mContext != null) {
					mProgress.setMessage(message);
					if (mProgressMax > 0)
						mProgress.setProgress(count);
				}	
				mProgressCount = count;
				mLastProgressMessage = message;
			}
		} else {
			/* Send message to the handler */
			Message msg = obtainMessage();
			Bundle b = new Bundle();
			b.putString("__internal", "progress");
			b.putInt("count", count);
			b.putString("message", message);
			msg.setData(b);
			sendMessage(msg);
		}
	}

	public void doToast(String message) {
		if (Thread.currentThread() == mUiThread.get()) {
			synchronized(this) {
				Context ctx = getContext();
				if (ctx != null)
					android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_LONG).show();			
			}
		} else {
			/* Send message to the handler */
			Message msg = obtainMessage();
			Bundle b = new Bundle();
			b.putString("__internal", "toast");
			b.putString("message", message);
			msg.setData(b);
			sendMessage(msg);			
		}
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

	Message obtainMessage() {
		return mHandler.obtainMessage();
	}

	void sendMessage(Message msg) {
		mHandler.sendMessage(msg);
	}

	private OnCancelListener mCancelHandler = new OnCancelListener() {
		public void onCancel(DialogInterface i) {
			mCancelFlg = true;
			Log.i("BookCatalogue", "Cancelling TaskWithProgress...");
		}
	};

	public void setMax(int max) {
		mProgressMax = max;		
		if (mProgress != null) {
			mProgress.setMax(mProgressMax);
		}
	}
	private void initProgress() {

		synchronized(this) {
			Context ctx = getContext();
			if (ctx != null) {
				mProgress = new ProgressDialog(ctx);
				if (mProgressMax > 0) {
					mProgress.setIndeterminate(false);
					mProgress.setMax(mProgressMax);
					mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				} else {
					mProgress.setIndeterminate(true);					
					mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				}
				mProgress.setMessage(mLastProgressMessage);
				mProgress.setCancelable(true);
				mProgress.setOnCancelListener(mCancelHandler);
				mProgress.show();
				mProgress.setProgress(mProgressCount);
			}
		}		
	}

	public void reconnect(Context ctx, TaskHandler taskHandler) {
		synchronized(this) {
			mContext = ctx;
			mTaskHandler = taskHandler;
			initProgress();
			if (mFinished) {
				doFinish();
			}
		}
	}

	public Context getContext() {
		synchronized(this) {
			return mContext;
		}
	}

	public void disconnect() {
		synchronized(this) {
			mContext = null;
			if (mProgress != null) {
				mProgress.dismiss();
				mProgress = null;
			}			
		}
	}

	private class ImportHandler extends Handler {
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			if (b.containsKey("__internal")) {
				String kind = b.getString("__internal");
				if (kind.equals("toast")) {
					doToast(b.getString("message"));
				} else {
					int count = b.getInt("count");
					String message = b.getString("message");
					doProgress(message, count);
				}
			} else {
				onMessage(msg);
			}
		}		
	}

}
