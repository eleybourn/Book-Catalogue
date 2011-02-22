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
	
	// Current progress
	String mProgressMessage = "";
	int mProgressMax = 0;
	int mProgressCount = 0;

	// List of tasks
	ArrayList<TaskInfo> mTasks = new ArrayList<TaskInfo> ();

	private class TaskInfo {
		ManagedTask 	task;
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

	public interface OnTaskEndedListener {
		void taskEnded(TaskManager manager, ManagedTask task);
	}

	public void addOnTaskEndedListener(OnTaskEndedListener listener) {
		mOnTaskEndedListeners.add(listener);
	}

	public void removeOnTaskEndedListener(OnTaskEndedListener listener) {
		mOnTaskEndedListeners.remove(listener);
	}

	TaskManager(ActivityWithTasks ctx) {
		if (ctx == null)
			throw new IllegalArgumentException();
		mContext = ctx;		
		mUiThread = new WeakReference<Thread>(Thread.currentThread());
		mMessageHandler = new MessageHandler();
	}

	void addTask(ManagedTask t) {
		synchronized(this) {
			if (getTaskInfo(t) == null)
					mTasks.add(new TaskInfo(t));
		}
	}

	void removeTask(ManagedTask task) {
		synchronized(this) {
			for(TaskInfo i : mTasks) {
				if (i.task == task) {
					mTasks.remove(i);
					break;
				}
			}
			if (mTasks.size() == 0) {
				destroyProgress();
			}
		}
	}

	public void taskEnded(ManagedTask task) {
		for(OnTaskEndedListener l : mOnTaskEndedListeners)
			try {
				l.taskEnded(this, task);
			} catch (Exception e) {
				Log.e("BC","OnTaskEndedListener failed", e);
			}
	}

	public int count() {
		return mTasks.size();
	}

	void taskStarting(ManagedTask t) {
		initProgress();
	}

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

	public String getString(int id) {
		if (mContext != null) {
			return mContext.getResources().getString(id);
		} else {
			return "No Context";
		}
	}
	
	public Context getContext() {
		synchronized(this) {
			return mContext;
		}
	}

	public void reconnect(ActivityWithTasks context) {
		mContext = context;
		if (mTasks.size() > 0) {
			initProgress();
			for(TaskInfo t : mTasks) {
				TaskHandler h = context.getTaskHandler(t.task);
				t.task.reconnect(context, h);
			}			
		}
	}

	public void disconnect() {
		mContext = null;
		destroyProgress();
		for(TaskInfo t : mTasks) {
			t.task.disconnect();
		}
	}

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

	private OnCancelListener mCancelHandler = new OnCancelListener() {
		public void onCancel(DialogInterface i) {
			Log.i("BookCatalogue", "Cancelling Tasks...");
			for(TaskInfo t : mTasks) {
				t.task.cancelTask();
			}
		}
	};

	/**
	 * Update the current ProgressDialog.
	 * 
	 * NOTE: This is (or should be) ONLY called from the UI thread.
	 * 
	 * @param message	Message text
	 * @param count		Counter for progress
	 */
	public void doProgress(ManagedTask task, String message, int count) {
		setProgress(task, message, count);
	}

	private void updateProgressDialog() {
		if (Thread.currentThread() == mUiThread.get()) {
			if (mTasks.size() == 0)
				// Shoud NEVER happen...
				mProgressMessage = "No Tasks Running!";
			else {
				mProgressMessage = mTasks.get(0).progressMessage;
				if (mTasks.size() > 1) {
					mProgressMessage = "1: " + mProgressMessage;
					for(int i = 1; i < mTasks.size(); i++) {
						mProgressMessage += "\r\n" + (i+1) + ": " + mTasks.get(i).progressMessage;
					}
				}
			}
			mProgressMax = 0;
			mProgressCount = 0;
			for (TaskInfo t : mTasks) {
				mProgressMax += t.progressMax;
				mProgressCount += t.progressCurrent;
			}

			synchronized(this) {
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
		} else {
			/* Send message to the handler */
			Message msg = mMessageHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putString("__internal", "progress");
			msg.setData(b);
			mMessageHandler.sendMessage(msg);
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
			Message msg = mMessageHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putString("__internal", "toast");
			b.putString("message", message);
			msg.setData(b);
			mMessageHandler.sendMessage(msg);			
		}
	}

	private void initProgress() {

		if (Thread.currentThread() == mUiThread.get()) {
			synchronized(this) {
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

	private TaskInfo getTaskInfo(ManagedTask task) {
		for(TaskInfo t : mTasks) {
			if (t.task == task) {
				return t;
			}
		}		
		return null;
	}

	public void setMax(ManagedTask task, int max) {
		TaskInfo t = getTaskInfo(task);
		if (t != null) {
			t.progressMax = max;
			updateProgressDialog();
			return;
		}
	}

	public void setProgress(ManagedTask task, String message, int count) {
		TaskInfo t = getTaskInfo(task);
		if (t != null) {
			t.progressMessage = message;
			t.progressCurrent = count;
			updateProgressDialog();
			return;
		}
	}
}
