package com.eleybourn.bookcatalogue.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

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
	private ArrayList<Integer> mMessages = null;
	/** Flag indicating dialog was cancelled */
	private boolean mWasCancelled = false;
	
	/**
	 * Convenience routine to show a dialog fragment and start the task
	 * 
	 * @param context	Activity of caller
	 * @param message	Message to display
	 * @param task		Task to run
	 */
	public static void runTaskWithProgress(final FragmentActivity context, int message, FragmentTask task) {
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.newInstance(message);
		frag.enqueue(task);
		frag.show(context.getSupportFragmentManager(), (String)null);		
	}

	/**
	 * Interface for 'FragmentTask' objects. Closely based on SimpleTask, but takes the fragment as a parameter
	 * to all calls.
	 * 
	 * @author pjw
	 */
	public interface FragmentTask {
		/** Run the task in it's own thread */
		public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext);
		/** Called in UI thread after task complete */
		public void onFinish(SimpleTaskQueueProgressFragment fragment);
		/** Check is the task wants onFinish() to be called. Will be called in non-UI thread, after run() completes */
		public boolean requiresOnFinish(SimpleTaskQueueProgressFragment fragment);
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
		public void onFinish(SimpleTaskQueueProgressFragment fragment) {
		}

		@Override
		public boolean requiresOnFinish(SimpleTaskQueueProgressFragment fragment) {
			return false;
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
		public void run(SimpleTaskContext taskContext) {
			mInnerTask.run(SimpleTaskQueueProgressFragment.this, taskContext);
		}

		@Override
		public void onFinish() {
			mInnerTask.onFinish(SimpleTaskQueueProgressFragment.this);
		}

		@Override
		public boolean requiresOnFinish() {
			return mInnerTask.requiresOnFinish(SimpleTaskQueueProgressFragment.this);
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
		// Can only display in main thread.
		if (Looper.getMainLooper().getThread() == Thread.currentThread() ) {
			synchronized(this) {
				if (this.getActivity() != null) {
					Toast.makeText(this.getActivity(), id, Toast.LENGTH_LONG).show();
				} else {
					// Assume the toast message was sent before the fragment was displayed; this
					// list will be read in onAttach
					if (mMessages == null)
						mMessages = new ArrayList<Integer>();
					mMessages.add(id);
				}				
			}
		} else {
			// Post() it to main thread.
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					showToast(id);
				}
			});
		}
	}

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

	public static SimpleTaskQueueProgressFragment newInstance(int title) {
		SimpleTaskQueueProgressFragment frag = new SimpleTaskQueueProgressFragment();
        Bundle args = new Bundle();
        args.putInt("title", title);
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
				for(Integer message: mMessages) {
					if ((int)message > 0)
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
	}

	/**
	 * Create the underlying dialog
	 */
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

		ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setCancelable(true);
		dialog.setMessage(getActivity().getString(getArguments().getInt("title")));
		dialog.setIndeterminate(true);
		return dialog;
	}

	@Override 
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		mWasCancelled = true;
		mQueue.finish();		
	}
	//@Override
	//public void onDismiss(DialogInterface dialog) {
	//	//((OnSyncTaskCompleteListener)getActivity()).onSyncTaskComplete(mTaskId, true, mWasCancelled);
	//}

	@Override
    public void onResume()
    {
        super.onResume();
        // If task finished, dismiss.
		if (!mQueue.hasActiveTasks())
			dismiss();
    }

	private SimpleTaskQueue.OnTaskFinishListener mTaskFinishListener = new SimpleTaskQueue.OnTaskFinishListener() {

		@Override
		public void onTaskFinish(SimpleTask task, Exception e) {
			// If there are no more tasks, close this dialog
			if (!mQueue.hasActiveTasks())
				dismiss();
		}
	};

	/** Accessor */
	public boolean isCancelled() {
		return mWasCancelled;
	}
}
