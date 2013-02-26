/*
 * @copyright 2012 Philip Warner
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
package com.eleybourn.bookcatalogue.utils;

import java.util.ArrayList;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

/**
 * Fragment Class to wrap a trivial progress dialog arounf (generally) a single task.
 * 
 * @author pjw
 */
public class SimpleTaskQueueProgressFragment extends BookCatalogueDialogFragment {
	/** The underlying task queue */
	private final SimpleTaskQueue mQueue;
	/** Handler so we can detect UI thread */
	private Handler mHandler = new Handler();
	/** List of messages queued; only used if activity not present when showToast() is called */
	private ArrayList<String> mMessages = null;
	/** Flag indicating dialog was cancelled */
	private boolean mWasCancelled = false;
	
	/** Max value of progress (for determinate progress) */
	private String mMessage = null;
	/** Max value of progress (for determinate progress) */
	private int mMax;
	/** Current value of progress (for determinate progress) */
	private int mProgress = 0;

	/** Flag indicating underlying field has changed so that progress dialog will be updated */
	private boolean mMessageChanged = false;
	/** Flag indicating underlying field has changed so that progress dialog will be updated */
	private boolean mProgressChanged = false;
	/** Flag indicating underlying field has changed so that progress dialog will be updated */
	private boolean mMaxChanged = false;
	/** Flag indicating underlying field has changed so that progress dialog will be updated */
	private boolean mNumberFormatChanged = false;

	/** Format of number part of dialog */
	private String mNumberFormat = null;

	/** Unique ID for this task. Can be used like menu or activity IDs */
	private int mTaskId;

	/** Flag, defaults to true, that can be set by tasks and is passed to listeners */
	private boolean mSuccess = true;
	
	/** List of messages to be sent to the underlying activity, but not yet sent */
	private ArrayList<TaskMessage> mTaskMessages = new ArrayList<TaskMessage>();

	/** Each message has a single method to deliver it and will only be called
	 * when the underlying Activity is actually present.
	 */
	private static interface TaskMessage {
		public void deliver(Activity a);
	}

	/** Listener for OnTaskFinished messages */
	public interface OnTaskFinishedListener {
		public void onTaskFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled, FragmentTask task);
	}

	/** Listener for OnAllTasksFinished messages */
	public interface OnAllTasksFinishedListener {
		public void onAllTasksFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled);
	}

	/**
	 * TaskFinished message.
	 * 
	 * We only deliver onFinish() to the FragmentTask when the activity is present.
	 */
	private class TaskFinishedMessage implements TaskMessage {
		FragmentTask mTask;
		Exception mException;

		public TaskFinishedMessage(FragmentTask task, Exception e) {
			mTask = task;
			mException = e;
		}
		
		@Override
		public void deliver(Activity a) {
			try {
				mTask.onFinish(SimpleTaskQueueProgressFragment.this, mException);				
			} catch (Exception e) {
				Logger.logError(e);
			}
			try {
				if (a instanceof OnTaskFinishedListener) {
					((OnTaskFinishedListener)a).onTaskFinished(SimpleTaskQueueProgressFragment.this, mTaskId, mSuccess, mWasCancelled, mTask);
				}
			} catch (Exception e) {
				Logger.logError(e);
			}

		}
		
	}
	
	/**
	 * AllTasksFinished message.
	 */
	private class AllTasksFinishedMessage implements TaskMessage {
		
		public AllTasksFinishedMessage() {
		}
		
		@Override
		public void deliver(Activity a) {
			if (a instanceof OnAllTasksFinishedListener) {
				((OnAllTasksFinishedListener)a).onAllTasksFinished(SimpleTaskQueueProgressFragment.this, mTaskId, mSuccess, mWasCancelled);
			}
			dismiss();
		}
		
	}

	/**
	 * Queue a TaskMessage and then try to process the queue.
	 */
	private void queueMessage(TaskMessage m) {

		synchronized(mTaskMessages) {
			mTaskMessages.add(m);
		}
		deliverMessages();
	}

	/**
	 * Queue a TaskFinished message
	 */
	private void queueTaskFinished(FragmentTask t, Exception e) {
		queueMessage(new TaskFinishedMessage(t, e));
	}
	
	/**
	 * Queue an AllTasksFinished message
	 */
	private void queueAllTasksFinished() {
		queueMessage(new AllTasksFinishedMessage());
	}

	/**
	 * If we have an Activity, deliver the current queue.
	 */
	private void deliverMessages() {
		Activity a = getActivity();
		if (a != null) {
			ArrayList<TaskMessage> toDeliver = new ArrayList<TaskMessage>();
			int count = 0;
			do {
				synchronized(mTaskMessages) {
					toDeliver.addAll(mTaskMessages);
					mTaskMessages.clear();
				}
				count = toDeliver.size();
				for(TaskMessage m: toDeliver) {
					try {
						m.deliver(a);						
					} catch (Exception e) {
						Logger.logError(e);
					}
				}				
				toDeliver.clear();
			} while (count > 0);			
		}
	}

	/**
	 * Convenience routine to show a dialog fragment and start the task
	 * 
	 * @param context	Activity of caller
	 * @param message	Message to display
	 * @param task		Task to run
	 */
	public static SimpleTaskQueueProgressFragment runTaskWithProgress(final FragmentActivity context, int message,
			FragmentTask task, boolean isIndeterminate, int taskId) {
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.newInstance(message, isIndeterminate, taskId);
		frag.enqueue(task);
		frag.show(context.getSupportFragmentManager(), (String) null);
		return frag;
	}

	/**
	 * Interface for 'FragmentTask' objects. Closely based on SimpleTask, but takes the fragment as a parameter
	 * to all calls.
	 * 
	 * @author pjw
	 */
	public interface FragmentTask {
		/** Run the task in it's own thread 
		 * @throws Exception */
		public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) throws Exception;
		/** Called in UI thread after task complete 
		 * @param exception TODO*/
		public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception);
	}
	
	/**
	 * Trivial implementation of FragmentTask that never calls onFinish(). The setState()/getState()
	 * calles can be used to store state info by a caller, eg. if they override requiresOnFinish() etc.
	 * 
	 * @author pjw
	 */
	public abstract static class FragmentTaskAbstract implements FragmentTask {
		private int mState = 0;

		@Override
		public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
			if (exception != null) {
				Logger.logError(exception);
				Toast.makeText(fragment.getActivity(), R.string.unexpected_error, Toast.LENGTH_LONG).show();
			}
		}

		public void setState(int state) {
			mState = state;
		}

		public int getState() {
			return mState;
		}
	}

	/**
	 * A SimpleTask wrapper for a FragmentTask.
	 * 
	 * @author pjw
	 */
	private class FragmentTaskWrapper implements SimpleTask {
		private FragmentTask mInnerTask;

		public FragmentTaskWrapper(FragmentTask task) {
			mInnerTask = task;
		}

		@Override
		public void run(SimpleTaskContext taskContext) throws Exception {
			try {
				mInnerTask.run(SimpleTaskQueueProgressFragment.this, taskContext);				
			} catch (Exception e) {
				mSuccess = false;
				throw e;
			}
		}

		@Override
		public void onFinish(Exception e) {
			SimpleTaskQueueProgressFragment.this.queueTaskFinished(mInnerTask, e);
		}

	}

	/**
	 * Constructor
	 */
	public SimpleTaskQueueProgressFragment() {
		mQueue = new SimpleTaskQueue("FragmentQueue");
		mQueue.setTaskFinishListener(mTaskFinishListener);
	}

	/**
	 * Utility routine to display a Toast message or queue it as appropriate.
	 * @param id
	 */
	public void showToast(final int id) {
		if (id != 0)
			showToast(getString(id));
	}

	/**
	 * Utility routine to display a Toast message or queue it as appropriate.
	 * @param id
	 */
	public void showToast(final String message) {
		// Can only display in main thread.
		if (Looper.getMainLooper().getThread() == Thread.currentThread() ) {
			synchronized(this) {
				if (this.getActivity() != null) {
					Toast.makeText(this.getActivity(), message, Toast.LENGTH_LONG).show();
				} else {
					// Assume the toast message was sent before the fragment was displayed; this
					// list will be read in onAttach
					if (mMessages == null)
						mMessages = new ArrayList<String>();
					mMessages.add(message);
				}				
			}
		} else {
			// Post() it to main thread.
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					showToast(message);
				}
			});
		}
	}

	/**
	 * Post a runnable to the UI thread
	 * 
	 * @param r
	 */
	public void post(Runnable r) {
		mHandler.post(r);
	}

	/**
	 * Enqueue a task for this fragment
	 * 
	 * @param task
	 */
	public void enqueue(FragmentTask task) {
		mQueue.enqueue(new FragmentTaskWrapper(task));
	}

	public static SimpleTaskQueueProgressFragment newInstance(int title, boolean isIndeterminate, int taskId) {
		SimpleTaskQueueProgressFragment frag = new SimpleTaskQueueProgressFragment();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("taskId", taskId);
        args.putBoolean("isIndeterminate", isIndeterminate);
        frag.setArguments(args);
        return frag;
	}

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		synchronized(this) {
			if (mMessages != null) {
				for(String message: mMessages) {
					if (message != null && !message.equals(""))
						Toast.makeText(a, message, Toast.LENGTH_LONG).show();
				}
				mMessages.clear();
			}			
		}
		//if (! (a instanceof OnSyncTaskCompleteListener))
		//	throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnSyncTaskCompleteListener");
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// VERY IMPORTANT. We do not want this destroyed!
		setRetainInstance(true);
		mTaskId = getArguments().getInt("taskId");
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Deliver any outstanding messages
		deliverMessages();
		
		// If no tasks left, exit
		if (!mQueue.hasActiveTasks()) {
			System.out.println("STQPF: Tasks finished while activity absent, closing");
			dismiss();
		}
	}

	/**
	 * Create the underlying dialog
	 */
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

		ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(false);

		int msg = getArguments().getInt("title");
		if (msg != 0)
			dialog.setMessage(getActivity().getString(msg));
		final boolean isIndet = getArguments().getBoolean("isIndeterminate");
		dialog.setIndeterminate(isIndet);
		if (isIndet) {
			dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		} else {
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);			
		}

		// We can't use "this.requestUpdateProgress()" because getDialog() will still return null
		if (!isIndet) {
			dialog.setMax(mMax);
			dialog.setProgress(mProgress);
			if (mMessage != null)
				dialog.setMessage(mMessage);
			setDialogNumberFormat(dialog);
		}
		
		return dialog;
	}

	@Override 
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		mWasCancelled = true;
		mQueue.finish();		
	}

	@Override
    public void onResume()
    {
        super.onResume();
        // If task finished, dismiss.
		if (!mQueue.hasActiveTasks())
			dismiss();
    }

	/**
	 * Dismiss dialog if all tasks finished
	 */
	private SimpleTaskQueue.OnTaskFinishListener mTaskFinishListener = new SimpleTaskQueue.OnTaskFinishListener() {

		@Override
		public void onTaskFinish(SimpleTask task, Exception e) {
			// If there are no more tasks, close this dialog
			if (!mQueue.hasActiveTasks()) {
				queueAllTasksFinished();
			}
		}
	};

	/** Accessor */
	public boolean isCancelled() {
		return mWasCancelled;
	}

	/** Accessor */
	public boolean getSuccess() {
		return mSuccess;
	}
	/** Accessor */
	public void setSuccess(boolean success) {
		mSuccess = success;
	}

	/** Flag indicating a Refresher has been posted but not run yet */
	private boolean mRefresherQueued = false;
	/**
	 * Runnable object to refresh the dialog
	 */
	private Runnable mRefresher = new Runnable() {
		@Override
		public void run() {
			synchronized(mRefresher) {
				mRefresherQueued = false;
				updateProgress();				
			}
		}
	};

	/**
	 * Refresh the dialog, or post a refresh to the UI thread
	 */
	private void requestUpdateProgress() {
		System.out.println("STQPF: " + mMessage + " (" + mProgress + "/" + mMax + ")");
		if (Thread.currentThread() == mHandler.getLooper().getThread()) {
			updateProgress();
		} else {
			synchronized(mRefresher) {
				if (!mRefresherQueued) {
					mHandler.post(mRefresher);
					mRefresherQueued = true;					
				}
			}
		}		
	}

	/**
	 * Convenience method to step the progress by 1.
	 * 
	 * @param message
	 */
	public void step(String message) {
		step(message, 1);
	}
	
	/**
	 * Convenience method to step the progress by the passed delta
	 * 
	 * @param message
	 */
	public void step(String message, int delta) {
		synchronized(this) {
			if (message != null) {
				mMessage = message;
				mMessageChanged = true;
			}
			mProgress += delta;			
			mProgressChanged = true;
		}	
		requestUpdateProgress();
	}

	/**
	 * Direct update of message and progress value
	 * 
	 * @param message
	 * @param progress
	 */
	public void onProgress(String message, int progress) {

		synchronized(this) {
			if (message != null) {
				mMessage = message;
				mMessageChanged = true;
			}
			mProgress = progress;			
			mProgressChanged = true;
		}

		requestUpdateProgress();
	}

	/**
	 * Method, run in the UI thread, that updates the various dialog fields.
	 */
	private void updateProgress() {
		ProgressDialog d = (ProgressDialog)getDialog();
		if (d != null) {
			synchronized(this) {
				if (mMaxChanged) {
					d.setMax(mMax);
					mMaxChanged = false;
				}
				if (mNumberFormatChanged) {
					if (Build.VERSION.SDK_INT >= 11) {
						// Called in a separate function so we can set API attributes
						setDialogNumberFormat(d);
					}
					mNumberFormatChanged = false;
				}
				if (mMessageChanged) {
					d.setMessage(mMessage);
					mMessageChanged = false;
				}				

				if (mProgressChanged) {
					d.setProgress(mProgress);
					mProgressChanged = false;
				}
				
			}
		}		
	}

	/**
	 * Set the number format on API >= 11
	 * @param d
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setDialogNumberFormat(ProgressDialog d) {
		if (Build.VERSION.SDK_INT >= 11) {
			try {
				d.setProgressNumberFormat(mNumberFormat);				
			} catch (Exception e) {
				// Ignore and log; Android 3.2 seems not to like NULL format despite docs, 
				// and this is a non-critical feature
				Logger.logError(e);
			}
		}		
	}

	/**
	 * Set the progress max value
	 * 
	 * @param max
	 */
	public void setMax(int max) {
		mMax = max;
		mMaxChanged = true;
		requestUpdateProgress();
	}

	/**
	 * Set the progress number format, if the API will support it
	 * 
	 * @param max
	 */
	public void setNumberFormat(String format) {
		if (Build.VERSION.SDK_INT >= 11) {
			synchronized(this) {
				mNumberFormat = format;
				mNumberFormatChanged = true;			
			}
			requestUpdateProgress();
		}
	}
	
	/**
	 * Work-around for bug in compatibility library:
	 * 
	 *     http://code.google.com/p/android/issues/detail?id=17423
	 */
	@Override
	 public void onDestroyView() {
	     if (getDialog() != null && getRetainInstance())
	         getDialog().setDismissMessage(null);
	         super.onDestroyView();
	 }
}
