package com.eleybourn.bookcatalogue;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

abstract public class TaskWithProgress<T> extends Thread {
	private Context mContext;
	private Handler mHandler;
	private ProgressDialog mProgress = null;
	private boolean mFinished = false;
	private boolean mCancelFlg = false;

	private TaskHandler<T> mLookupHandler;

	protected int mProgressCount = 0;
	protected int mProgressMax = 1;
	private String mLastProgressMessage = "Updating...";

	abstract protected void onFinish();
	abstract protected void onFound();
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
	 * Constructor.
	 * 
	 * @param ctx				Context to use for constructingprogressdialog
	 * @param overwrite			Whether to overwrite details
	 * @param books				Cursor to scan
	 * @param lookupHandler		Interface object to handle events in this thread.
	 * 
	 */
	public TaskWithProgress(Context ctx, TaskHandler<T> lookupHandler) {
		mContext = ctx;
		mHandler = new ImportHandler();
		mLookupHandler = lookupHandler;
		if (mProgress == null) {
			if (ctx != null) {
				initProgress();
			}
		}
	}

	public void updateProgress(String message, int count) {
		synchronized(this) {
			if (mProgress != null && mContext != null) {
				mProgress.setMessage(message);
				mProgress.setProgress(count);
			}			
		}
	}

	/**
	 * Accessor to check if task finished.
	 * @return true/false depending on state
	 */
	public boolean isFinished() {
		return mFinished;
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

	public void reconnect(Context ctx, TaskHandler<T> lookupHandler) {
		synchronized(this) {
			if (!mFinished) {
				mContext = ctx;
				mLookupHandler = lookupHandler;
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
			onMessage(msg);
		}		
	}
}
