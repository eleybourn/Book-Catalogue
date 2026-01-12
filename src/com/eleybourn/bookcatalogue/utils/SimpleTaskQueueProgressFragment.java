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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

import java.util.ArrayList;

/**
 * Fragment Class to wrap a trivial progress dialog around (generally) a single task.
 * 
 * @author pjw
 */
public class SimpleTaskQueueProgressFragment extends BookCatalogueDialogFragment {
	/** The underlying task queue */
	private final SimpleTaskQueue mQueue;
	/** Handler so we can detect UI thread */
	private final Handler mHandler = new Handler();
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

	/** Unique ID for this task. Can be used like menu or activity IDs */
	private int mTaskId;

	/** Flag, defaults to true, that can be set by tasks and is passed to listeners */
	private boolean mSuccess = true;
	
	/** List of messages to be sent to the underlying activity, but not yet sent */
	private final ArrayList<TaskMessage> mTaskMessages = new ArrayList<>();

	/** Each message has a single method to deliver it and will only be called
	 * when the underlying Activity is actually present.
	 */
	private interface TaskMessage {
		void deliver(Activity a);
	}

	/** Listener for OnTaskFinished messages */
	public interface OnTaskFinishedListener {
		void onTaskFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled, FragmentTask task);
	}

	/** Listener for OnAllTasksFinished messages */
	public interface OnAllTasksFinishedListener {
		void onAllTasksFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled);
	}

	/**
	 * TaskFinished message.
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
			ArrayList<TaskMessage> toDeliver = new ArrayList<>();
			int count;
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
	 * @param fm		FragmentManager to use
	 * @param message	Message to display
	 * @param task		Task to run
	 */
	public static SimpleTaskQueueProgressFragment runTaskWithProgress(
			final FragmentManager fm, int message, FragmentTask task, boolean isIndeterminate, int taskId)
	{
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.newInstance(message, isIndeterminate, taskId);
		frag.enqueue(task);
		frag.show(fm, null);
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
		 * @throws Exception Called method can throw exceptions to terminate process. They will be passed to the onFinish()
		 */
		void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) throws Exception;
		/** Called in UI thread after task complete 
		 * @param exception TODO*/
		void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception);
	}
	
	/**
	 * Trivial implementation of FragmentTask that never calls onFinish(). The setState()/getState()
	 * calls can be used to store state info by a caller, eg. if they override requiresOnFinish() etc.
	 * 
	 * @author pjw
	 */
	public abstract static class FragmentTaskAbstract implements FragmentTask {
		private int mState = 0;

		@Override
		public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
			if (exception != null) {
				Logger.logError(exception);
				Toast.makeText(fragment.getActivity(), R.string.alert_unexpected_error, Toast.LENGTH_LONG).show();
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
		private final FragmentTask mInnerTask;

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
        // If there are no more tasks, close this dialog
        SimpleTaskQueue.OnTaskFinishListener mTaskFinishListener = (task, e) -> {
            // If there are no more tasks, close this dialog
            if (!mQueue.hasActiveTasks()) {
                queueAllTasksFinished();
            }
        };
        mQueue.setTaskFinishListener(mTaskFinishListener);
	}

	/**
	 * Utility routine to display a Toast message or queue it as appropriate.
	 * @param id	Show a toast message based on the string id
	 */
	public void showToast(final int id) {
		if (id != 0) {
			// We don't use getString() because we have no guarantee this 
			// object is associated with an activity when this is called, and
			// for whatever reason the implementation requires it.
			showToast(BookCatalogueApp.getResourceString(id));
		}
	}

	/**
	 * Utility routine to display a Toast message or queue it as appropriate.
	 * @param message String to display
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
						mMessages = new ArrayList<>();
					mMessages.add(message);
				}				
			}
		} else {
			// Post() it to main thread.
			mHandler.post(() -> showToast(message));
		}
	}

	/**
	 * Post a runnable to the UI thread
	 * 
	 * @param r	Runnable to execute in main thread at a later time
	 */
	public void post(Runnable r) {
		mHandler.post(r);
	}

	/**
	 * Enqueue a task for this fragment
	 * 
	 * @param task	The task to enqueue
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
	public void onAttach(@NonNull Context a) {
		super.onAttach(a);

		synchronized(this) {
			if (mMessages != null) {
				for(String message: mMessages) {
					if (message != null && !message.isEmpty())
						Toast.makeText(a, message, Toast.LENGTH_LONG).show();
				}
				mMessages.clear();
			}			
		}
    }

	@Override
	public void onStart() { // TODO: Decide if/how to use ViewModelProvider !!!
		super.onStart();
    }

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// VERY IMPORTANT. We do not want this destroyed!
		setRetainInstance(true);
        assert getArguments() != null;
        mTaskId = getArguments().getInt("taskId");
	}

    //public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    //    super.onViewCreated(view, savedInstanceState);
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Deliver any outstanding messages
		deliverMessages();
		
		// If no tasks left, exit
		if (!mQueue.hasActiveTasks()) {
			System.out.println("Simple Task Queue: Tasks finished while activity absent, closing");
			dismiss();
		}
	}

	/**
	 * Create the underlying dialog
	 */
	@NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		// This is ESSENTIAL; setting this on the dialog is insufficient.
		// Further, without this the 'back' key is used to dismiss the dialog (and fragment)
		// making the calling activity unavailable.
		//
		// We therefor catch keystrokes to look for the 'back' key, and then cancel
		// tasks as appropriate. When all tasks are finished, the dialog is dismissed.
		setCancelable(false);

		final Dialog dialog = new Dialog(requireActivity());
		dialog.setContentView(R.layout.progress_dialog);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(false);

        assert getArguments() != null;
        int msg = getArguments().getInt("title");
		if (msg != 0) {
			dialog.setTitle(msg);
        }

		final boolean isIndeterminate = getArguments().getBoolean("isIndeterminate");
        LinearLayout spinnerLayout = dialog.findViewById(R.id.spinner_layout);
        LinearLayout horizontalLayout = dialog.findViewById(R.id.horizontal_layout);

		if (isIndeterminate) {
            horizontalLayout.setVisibility(View.GONE);
            spinnerLayout.setVisibility(View.VISIBLE);
            TextView messageView = dialog.findViewById(R.id.spinner_message);
            if (mMessage != null) {
                messageView.setText(mMessage);
            } else if (msg != 0) {
                messageView.setText(requireActivity().getString(msg));
            }
		} else {
            spinnerLayout.setVisibility(View.GONE);
            horizontalLayout.setVisibility(View.VISIBLE);
            ProgressBar progressBar = dialog.findViewById(R.id.progress_horizontal);
			progressBar.setMax(mMax);
			progressBar.setProgress(mProgress);

            TextView messageView = dialog.findViewById(R.id.horizontal_message);
			if (mMessage != null) {
				messageView.setText(mMessage);
            } else if (msg != 0) {
                messageView.setText(requireActivity().getString(msg));
            }
		}

		dialog.setOnKeyListener((dialog1, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mWasCancelled = true;
                }
                return true;
            }
            return false;
        });

		return dialog;
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialog) {
		super.onCancel(dialog);
		cancel();
	}

	@Override
    public void onResume()
    {
        super.onResume();
        // If task finished, dismiss.
		if (!mQueue.hasActiveTasks())
			dismiss();
    }

    /** Accessor */
	public boolean isCancelled() {
		return mWasCancelled;
	}
	public void cancel() {
		mWasCancelled = true;
		//mQueue.finish();
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
	private final Runnable mRefresher = new Runnable() {
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
		System.out.println("Simple Task Queue: " + mMessage + " (" + mProgress + "/" + mMax + ")");
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
	 */
	public void step(String message) {
		step(message, 1);
	}
	
	/**
	 * Convenience method to step the progress by the passed delta
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
		Dialog d = getDialog();
		if (d != null) {
            ProgressBar progressBar = d.findViewById(R.id.progress_horizontal);
            TextView messageView = d.findViewById(R.id.horizontal_message);
            TextView spinnerMessageView = d.findViewById(R.id.spinner_message);

			synchronized(this) {
				if (mMaxChanged) {
                    progressBar.setMax(mMax);
					mMaxChanged = false;
				}
				if (mMessageChanged) {
                    messageView.setText(mMessage);
                    spinnerMessageView.setText(mMessage);
					mMessageChanged = false;
				}				

				if (mProgressChanged) {
                    progressBar.setProgress(mProgress);
					mProgressChanged = false;
				}
				
			}
		}		
	}



	/**
	 * Set the progress max value
	 */
	public void setMax(int max) {
		mMax = max;
		mMaxChanged = true;
		requestUpdateProgress();
	}
	
	/**
     * Work-around for bug in compatibility library:
     *     <a href="http://code.google.com/p/android/issues/detail?id=17423">...</a>
     */
	@Override
	 public void onDestroyView() {
	     if (getDialog() != null && getRetainInstance())
	         getDialog().setDismissMessage(null);
		 super.onDestroyView();
	 }
}
