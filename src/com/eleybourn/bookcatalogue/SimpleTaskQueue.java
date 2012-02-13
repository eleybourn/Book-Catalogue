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

import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;
import android.os.Handler;

/**
 * Class to perform time consuming but light-weight tasks in a worker thread. Users of this
 * class should implement their tasks as self-contained objects that implement SimpleTask.
 * 
 * The tasks run from (currently) a LIFO queue in a single thread; the run() method is called
 * in the alternate thread and the finished() method is called in the UI thread.
 * 
 * The execution queue is (currently) a stack so that the most recent queued is loaded. This is
 * good for loading (eg) gallery images to make sure that the most recently viewed is loaded.
 * 
 * The results queue is executed in FIFO order.
 * 
 * In the future, both queues could be done independently and this object could be broken into
 * 3 classes: SimpleTaskQueueBase, SimpleTaskQueueFIFO and SimpleTaskQueueLIFO. For now, this is
 * not needed.
 * 
 * @author Grunthos
 */
public class SimpleTaskQueue {
	// Execution queue
	private BlockingStack<SimpleTaskWrapper> mQueue = new BlockingStack<SimpleTaskWrapper>();
	// Results queue
	private LinkedBlockingQueue<SimpleTaskWrapper> mResultQueue = new LinkedBlockingQueue<SimpleTaskWrapper>();
	// Flag indicating this object should terminate.
	private boolean mTerminate = false;
	// Handler for sending tasks to the UI thread.
	private Handler mHandler = new Handler();
	// Name for this queue
	private final String mName;
	// Threads associate with this queue
	private ArrayList<SimpleTaskQueueThread> mThreads = new ArrayList<SimpleTaskQueueThread>();
	/** Max number of threads to create */
	private int mMaxTasks;
	/** Number of currently queued, executing (or starting/finishing) tasks */
	private int mManagedTaskCount = 0;

	private OnTaskStartListener mTaskStartListener = null;
	private OnTaskFinishListener mTaskFinishListener = null;
	
	/**
	 * SimpleTask interface.
	 * 
	 * run() is called in worker thread
	 * finished() is called in UI thread.
	 * 
	 * @author Grunthos
	 *
	 */
	public interface SimpleTask {
		/**
		 * Method called in queue thread to perform the background task.
		 */
		void run(SimpleTaskContext taskContext);
		/**
		 * Method called in UI thread after the background task has finished.
		 */
		void finished();
		/**
		 * Method called by queue manager to determine if 'finished()' needs to be called.
		 * This is an optimization to avoid queueing unnecessary Runnables.
		 * 
		 * @return	boolean indicating 'finished()' should be called in UI thread.
		 */
		boolean runFinished();
	}

	/**
	 * Interface for an object to listen for when tasks start.
	 * 
	 * @author Grunthos
	 */
	public interface OnTaskStartListener {
		void onTaskStart(SimpleTask task);
	}

	/**
	 * Interface for an object to listen for when tasks finish.
	 * 
	 * @author Grunthos
	 */
	public interface OnTaskFinishListener {
		void onTaskFinish(SimpleTask task, Exception e);
	}

	/**
	 * Accessor.
	 * 
	 * @param listener
	 */
	public void setTaskStartListener(OnTaskStartListener listener) {
		mTaskStartListener = listener;
	}
	/**
	 * Accessor.
	 * 
	 * @param listener
	 */
	public OnTaskStartListener getTaskStartListener() {
		return mTaskStartListener;
	}

	/**
	 * Accessor.
	 * 
	 * @param listener
	 */
	public void setTaskFinishListener(OnTaskFinishListener listener) {
		mTaskFinishListener = listener;
	}
	/**
	 * Accessor.
	 * 
	 * @param listener
	 */
	public OnTaskFinishListener getTaskFinishListener() {
		return mTaskFinishListener;
	}

	/**
	 * Class to wrap a simpleTask with more info needed by the queue.
	 * 
	 * @author Grunthos
	 */
	private static class SimpleTaskWrapper {
		private static Long mCounter = 0L;
		public SimpleTask task;
		public Exception exception;
		public boolean finishRequested = false;
		public long id;
		SimpleTaskWrapper(SimpleTask task) {
			this.task = task;
			synchronized(mCounter) {
				this.id = ++mCounter;
			}
		}
	}

	/**
	 * Constructor. Nothing to see here, move along. Just start the thread.
	 * 
	 * @author Grunthos
	 *
	 */
	public SimpleTaskQueue(String name) {
		mName = name;
		mMaxTasks = 5;
	}

	/**
	 * Constructor. Nothing to see here, move along. Just start the thread.
	 * 
	 * @author Grunthos
	 *
	 */
	public SimpleTaskQueue(String name, int maxTasks) {
		mName = name;
		mMaxTasks = maxTasks;
		if (maxTasks < 1 || maxTasks > 10)
			throw new RuntimeException("Illegal value for maxTasks");
	}

	/**
	 * Terminate processing.
	 */
	public void finish() {
		synchronized(this) {
			mTerminate = true;
			for(Thread t : mThreads) {
				try { t.interrupt(); } catch (Exception e) {};
			}
		}
	}

	/**
	 * Check to see if any tasks are active -- either queued, or with ending results.
	 * 
	 * @return
	 */
	public boolean hasActiveTasks() {
		synchronized(this) {
			return ( mManagedTaskCount > 0 );
		}
	}

	/**
	 * Queue a request to run in the worker thread.
	 * 
	 * @param task		Task to run.
	 */
	public long enqueue(SimpleTask task) {
		SimpleTaskWrapper wrapper = new SimpleTaskWrapper(task);

		synchronized(this) {
			mManagedTaskCount++;
		}
		try {
			mQueue.push(wrapper);
		} catch (InterruptedException e) {
			// Ignore. This happens if the queue object is being terminated.
		}
		//System.out.println("SimpleTaskQueue(added): " + mQueue.size());
		synchronized(this) {
			int qSize = mQueue.size();
			int nThreads = mThreads.size();
			if (nThreads < qSize && nThreads < mMaxTasks) {
				SimpleTaskQueueThread t = new SimpleTaskQueueThread();
				mThreads.add(t);
				t.start();
			}
		}
		return wrapper.id;
	}

	/**
	 * Remove a previously requested task based on ID, if present
	 */
	public boolean remove(long id) {
		Stack<SimpleTaskWrapper> currTasks = mQueue.getElements();
		for (SimpleTaskWrapper w : currTasks) {
			if (w.id == id) {
				mQueue.remove(w);
				//System.out.println("SimpleTaskQueue(removeok): " + mQueue.size());			
				return true;
			}
		}			
		//System.out.println("SimpleTaskQueue(removefail): " + mQueue.size());			
		return false;
	}
	
	/**
	 * Remove a previously requested task, if present
	 */
	public boolean remove(SimpleTask t) {
		Stack<SimpleTaskWrapper> currTasks = mQueue.getElements();
		for (SimpleTaskWrapper w : currTasks) {
			if (w.task.equals(t)) {
				mQueue.remove(w);
				//System.out.println("SimpleTaskQueue(removeok): " + mQueue.size());			
				return true;
			}
		}
		//System.out.println("SimpleTaskQueue(removefail): " + mQueue.size());			
		return false;			
	}
	
	/**
	 * Method to ensure results queue is processed.
	 */
	private Runnable mDoProcessResults = new Runnable() {
		@Override
		public void run() {
			processResults();
		}
	};

	/**
	 * Run the task then queue the results.
	 * 
	 * @param task
	 */
	private void handleRequest(final SimpleTaskQueueThread thread, final SimpleTaskWrapper taskWrapper) {
		final SimpleTask task = taskWrapper.task;

		if (mTaskStartListener != null) {
			try {
				mTaskStartListener.onTaskStart(task);
			} catch (Exception e) {
				// Ignore
			}
		}

		try {
			task.run(thread);
		} catch (Exception e) {
			taskWrapper.exception = e;
			Logger.logError(e, "Error running task");
		}
		// See if we need to call finished(). Default to true.
		try {
			taskWrapper.finishRequested = task.runFinished();
		} catch (Exception e) {
			taskWrapper.finishRequested = true;
		}
		synchronized(this) {

			// Queue the call to finished() if necessary.
			if (taskWrapper.finishRequested || mTaskFinishListener != null) {
				try {
					mResultQueue.put(taskWrapper);	
				} catch (InterruptedException e) {
				}
				// Queue Runnable in the UI thread.
				mHandler.post(mDoProcessResults);			
			} else {
				// If no other methods are going to be called, then decrement
				// managed task count. We do not care about this task any more.
				mManagedTaskCount--;
			}
		}
	}

	/**
	 * Run in the UI thread, process the results queue.
	 */
	private void processResults() {
		try {
			while (!mTerminate) {
				// Get next; if none, exit.
				SimpleTaskWrapper req = mResultQueue.poll();
				if (req == null)
					break;

				final SimpleTask task = req.task;
				
				// Decrement the managed task count BEFORE we call any methods.
				// This allows them to call hasActiveTasks() and get a useful result
				// when they are the last task.
				synchronized(this) {
					mManagedTaskCount--;
				}

				// Call the task handler; log and ignore errors.
				if (req.finishRequested) {
					try {
						task.finished();
					} catch (Exception e) {
						Logger.logError(e, "Error processing request result");
					}
				}

				// Call the task listener; log and ignore errors.
				if (mTaskFinishListener != null)
					try {
						mTaskFinishListener.onTaskFinish(task, req.exception);
					} catch (Exception e) {
						Logger.logError(e, "Error from listener while processing request result");
					}
			}
		} catch (Exception e) {
			Logger.logError(e, "Exception in processResults in UI thread");
		}
	}

	public static interface SimpleTaskContext {
		public CatalogueDBAdapter getDb();
	}

	/**
	 * Class to actually run the tasks. Can start more than one. They wait until there is nothing left in
	 * the queue before terminating.
	 * 
	 * @author Grunthos
	 */
	private class SimpleTaskQueueThread extends Thread implements SimpleTaskContext {
		/** DB Connection, if task requests one. Survives while thread is alive */
		CatalogueDBAdapter mDb = null;

		/**
		 * Main worker thread logic
		 */
		public void run() {
			try {
				this.setName(mName);
				while (!mTerminate) {
					SimpleTaskWrapper req = mQueue.pop(15000);

					// If timeout occurred, get a lock on the queue and see if anything was queued
					// in the intervening milliseconds. If not, delete this tread and exit.
					if (req == null) {
						synchronized(SimpleTaskQueue.this) {
							req = mQueue.poll();
							if (req == null) {
								mThreads.remove(this);
								return;
							}
						}
					}

					//System.out.println("SimpleTaskQueue(run): " + mQueue.size());						
					handleRequest(this, req);
				}
			} catch (InterruptedException e) {
				// Ignore; these will happen when object is destroyed
			} catch (Exception e) {
				Logger.logError(e);
			} finally {
				if (mDb != null)
					mDb.close();
			}
		}

		@Override
		public CatalogueDBAdapter getDb() {
			if (mDb == null) {
				mDb = new CatalogueDBAdapter(BookCatalogueApp.context);
				mDb.open();
			}
			return mDb;
		}
	}
}
