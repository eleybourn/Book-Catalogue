package com.eleybourn.bookcatalogue;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

abstract public class TaskWithProgress<T> extends Thread {
	private Context mContext;
	private Handler mHandler;
	private ProgressDialog mProgress = null;
	private boolean mFinished = false;
	private boolean mCancelFlg = false;

	private TaskHandler<T> mTaskHandler;

	protected int mProgressCount = 0;
	protected int mProgressMax = 1;
	private String mLastProgressMessage = "Updating...";

	abstract protected void onFinish();
	abstract protected void onRun();
	abstract protected void onMessage(Message msg);

	/**
	 * Interface allowing the caller to be informed of events in this thread.
	 * 
	 * @author Grunthos
	 */
	public interface TaskHandler<T> {
		/**
		 * Called when task completes.
		 */
		void onFinish(T arg);
		/**
		 * Called for each book when it is found
		 *
		 * @param id		ID of book
		 * @param bookData	Data from internet
		 */
		void onProgress(T arg);
	}

	/**
	 * Accessor for the task handler.
	 * 
	 * @return
	 */
	TaskHandler<T> getTaskHandler() {
		return mTaskHandler;
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
	public TaskWithProgress(Context ctx, TaskHandler<T> taskHandler) {
		mContext = ctx;
		mHandler = new ImportHandler();
		mTaskHandler = taskHandler;
		if (mProgress == null) {
			if (ctx != null) {
				initProgress();
			}
		}
	}

	@Override
	public void run() {

		onRun();

		mHandler.post(new Runnable() {
			public void run() { doFinish(); };
		});
	}

	private void doFinish() {
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
	 * Send a message to the handler object (defined below)
	 * 
	 * @param num
	 * @param title
	 */
	public void doProgress(String message, int count) {
		/* Send message to the handler */
		Message msg = obtainMessage();
		Bundle b = new Bundle();
		b.putBoolean("__internal", true);
		b.putInt("count", count);
		b.putString("message", message);
		msg.setData(b);
		sendMessage(msg);
		return;
	}

	/**
	 * Update the current ProgressDialog.
	 * 
	 * NOTE: This is (or should be) ONLY called from the UI thread.
	 * 
	 * @param message	Message text
	 * @param count		Counter for progress
	 */
	public void updateProgress(String message, int count) {
		synchronized(this) {
			Context ctx = getContext();
			if (mProgress == null) {
				if (ctx != null) {
					initProgress();
				}
			}
			if (mProgress != null && mContext != null) {
				mProgress.setMessage(message);
				mProgress.setProgress(count);
			}	
			mProgressCount = count;
			mLastProgressMessage = message;
		}
	}

	public void makeToast(String message) {
		synchronized(this) {
			Context ctx = getContext();
			if (ctx != null)
				android.widget.Toast.makeText(ctx, mProgressCount + " Books Searched", android.widget.Toast.LENGTH_LONG).show();			
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
				mProgress.setIndeterminate(false);
				mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				mProgress.setMax(mProgressMax);
				mProgress.setMessage(mLastProgressMessage);
				mProgress.setCancelable(true);
				mProgress.setOnCancelListener(mCancelHandler);
				mProgress.show();
				mProgress.setProgress(mProgressCount);
			}
		}		
	}

	public void reconnect(Context ctx, TaskHandler<T> taskHandler) {
		synchronized(this) {
			if (!mFinished) {
				mContext = ctx;
				mTaskHandler = taskHandler;
				initProgress();
			} else {
				onFinish();
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
				int count = b.getInt("count");
				String message = b.getString("message");
				updateProgress(message, count);
			} else {
				onMessage(msg);
			}
		}		
	}
}
