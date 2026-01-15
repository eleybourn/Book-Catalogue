/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License V3
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
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

import android.os.Handler;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;

/**
 * Class to perform time consuming but light-weight tasks in a worker thread. Users of this
 * class should implement their tasks as self-contained objects that implement SimpleTask.
 * <p>
 * The tasks run from (currently) a LIFO queue in a single thread; the run() method is called
 * in the alternate thread and the finished() method is called in the UI thread.
 * </p><p>
 * The execution queue is (currently) a stack so that the most recent queued is loaded. This is
 * good for loading (eg) gallery images to make sure that the most recently viewed is loaded.
 * </p><p>
 * The results queue is executed in FIFO order.
 * </p><p>
 * In the future, both queues could be done independently and this object could be broken into
 * 3 classes: SimpleTaskQueueBase, SimpleTaskQueueFIFO and SimpleTaskQueueLIFO. For now, this is
 * not needed.
 * </p><p>
 * TODO: Consider adding an 'AbortListener' interface so tasks can be told when queue is aborted
 * TODO: Consider adding an 'aborted' flag to onFinish() and always calling onFinish() when queue is killed
 * </p><p>
 * NOTE: Tasks can call context.isTerminating() if necessary
 * </p>
 * @author Philip Warner
 */
@SuppressWarnings("unused")
public class SimpleTaskQueue {
	public static class QueueTerminatedException extends RuntimeException { }

	// Execution queue
	private final BlockingStack<SimpleTaskWrapper> mQueue = new BlockingStack<>();
	// Results queue
	private final LinkedBlockingQueue<SimpleTaskWrapper> mResultQueue = new LinkedBlockingQueue<>();
	// Flag indicating this object should terminate.
	private boolean mTerminate = false;
	// Handler for sending tasks to the UI thread.
	private final Handler mHandler = new Handler();
	// Name for this queue
	private final String mName;
	// Threads associate with this queue
	private final ArrayList<SimpleTaskQueueThread> mThreads = new ArrayList<>();
	/** Max number of threads to create */
	private final int mMaxTasks;
	/** Number of currently queued, executing (or starting/finishing) tasks */
	private int mManagedTaskCount = 0;

	private OnTaskStartListener mTaskStartListener = null;
	private OnTaskFinishListener mTaskFinishListener = null;
	
	/**
	 * SimpleTask interface.
	 * <p>
	 * run() is called in worker thread
	 * finished() is called in UI thread.
	 * </p>
	 * @author Philip Warner
	 *
	 */
	public interface SimpleTask {
		/**
		 * Method called in queue thread to perform the background task.
		 */
		void run(SimpleTaskContext taskContext) throws Exception;
		/**
		 * Method called in UI thread after the background task has finished.
		 * @param e TODO
		 */
		void onFinish(Exception e);
	}

	/**
	 * Interface for an object to listen for when tasks start.
	 * 
	 * @author Philip Warner
	 */
	public interface OnTaskStartListener {
		void onTaskStart(SimpleTask task);
	}

	/**
	 * Interface for an object to listen for when tasks finish.
	 * 
	 * @author Philip Warner
	 */
	public interface OnTaskFinishListener {
		void onTaskFinish(SimpleTask task, Exception e);
	}

	/**
	 * Accessor.
	 * 
	 * @param listener		Listener for task starting events
	 */
	public void setTaskStartListener(OnTaskStartListener listener) {
		mTaskStartListener = listener;
	}
	/**
	 * Accessor.
	 */
	public OnTaskStartListener getTaskStartListener() {
		return mTaskStartListener;
	}

	/**
	 * Accessor.
	 * 
	 * @param listener	Listener for Task Finish events
	 */
	public void setTaskFinishListener(OnTaskFinishListener listener) {
		mTaskFinishListener = listener;
	}
	/**
	 * Accessor.
	 */
	public OnTaskFinishListener getTaskFinishListener() {
		return mTaskFinishListener;
	}

	/**
	 * Accessor
	 * 
	 * @return	Flag indicating queue is terminating (finish() was called)
	 */
	public boolean isTerminating() {
		return mTerminate;
	}

	/**
	 * Class to wrap a simpleTask with more info needed by the queue.
	 * 
	 * @author Philip Warner
	 */
	private static class SimpleTaskWrapper implements SimpleTaskContext {
		private static final Object mCounterSync = new Object();
		private static long mCounter = 0L;
		private final SimpleTaskQueue mOwner;
		public final SimpleTask task;
		public Exception exception;
		public boolean finishRequested = true;
		public final long id;
		public SimpleTaskQueueThread activeThread = null;
		SimpleTaskWrapper(SimpleTaskQueue owner, SimpleTask task) {
			mOwner = owner;
			this.task = task;
			synchronized(mCounterSync) {
				this.id = ++mCounter;
			}
		}
		/**
		 * Accessor when behaving as a context
		 */
		@Override
		public CatalogueDBAdapter getDb() {
			if (activeThread == null)
				throw new RuntimeException("SimpleTaskWrapper can only be used a context during the run() stage");
			return activeThread.getDb();
		}
		@Override
		public CoversDbHelper getCoversDb() {
			if (activeThread == null)
				throw new RuntimeException("SimpleTaskWrapper can only be used a context during the run() stage");
			return activeThread.getCoversDb();
		}
		@Override
		public Utils getUtils() {
			if (activeThread == null)
				throw new RuntimeException("SimpleTaskWrapper can only be used a context during the run() stage");
			return activeThread.getUtils();
		}
		@Override
		public void setRequiresFinish(boolean requiresFinish) {
			this.finishRequested = requiresFinish;
		}
		@Override
		public boolean getRequiresFinish() {
			return finishRequested;
		}
		@Override
		public boolean isTerminating() {
			return mOwner.isTerminating();
		}
	}

	/**
	 * Constructor. Nothing to see here, move along. Just start the thread.
	 * 
	 * @author Philip Warner
	 *
	 */
	public SimpleTaskQueue(String name) {
		mName = name;
		mMaxTasks = 5;
	}

	/**
	 * Constructor. Nothing to see here, move along. Just start the thread.
	 * 
	 * @author Philip Warner
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
		int cnt;
		synchronized(this) {
			mTerminate = true;

			// Get all un-executed tasks
			Stack<SimpleTaskWrapper> allTasks = mQueue.getElements();
			cnt = allTasks.size();

			// Erase the queue
			mQueue.clear();

			// Set the exception
			for(SimpleTaskWrapper w: allTasks) {
				w.exception = new QueueTerminatedException();
			}

			// TODO: There is a problem with ordering here. If the queued tasks are added before the running tasks, then the app may become confused.
			// Add them all to the completed queue
			mResultQueue.addAll(allTasks);

			// Kill running threads.
			for(Thread t : mThreads) {
				try { t.interrupt(); } catch (Exception ignored) {}
			}
		}
		// If we moved anything, then post the task to process them
		if (cnt > 0) {
			synchronized(mDoProcessResults) {
				if (!mDoProcessResultsIsQueued) {
					mDoProcessResultsIsQueued = true;
					mHandler.post(mDoProcessResults);
				}
			}
		}
	}

	/**
	 * Check to see if any tasks are active -- either queued, or with ending results.
	 * 
	 * @return 	True if there are still tasks to be processed
	 */
	public boolean hasActiveTasks() {
		synchronized(this) {
			return ( mManagedTaskCount > 0 );
		}
	}

	/**
     * Queue a request to run in the worker thread.
     *
     * @param task Task to run.
     */
	public void enqueue(SimpleTask task) {
		SimpleTaskWrapper wrapper = new SimpleTaskWrapper(this, task);

		try {
			synchronized(this) {
				mQueue.push(wrapper);
				mManagedTaskCount++;
			}
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
    }

	/**
	 * Remove a previously requested task based on ID, if present
	 */
	public boolean remove(long id) {
		Stack<SimpleTaskWrapper> currTasks = mQueue.getElements();
		for (SimpleTaskWrapper w : currTasks) {
			if (w.id == id) {
				synchronized(this) {
					if (mQueue.remove(w))
						mManagedTaskCount--;					
				}
				return true;
			}
		}			
		return false;
	}
	
	/**
     * Remove a previously requested task, if present
     */
	public void remove(SimpleTask t) {
		Stack<SimpleTaskWrapper> currTasks = mQueue.getElements();
		for (SimpleTaskWrapper w : currTasks) {
			if (w.task.equals(t)) {
				synchronized(this) {
					if (mQueue.remove(w))
						mManagedTaskCount--;					
				}
				return;
			}
		}
    }
	
	/**
	 * Flag indicating runnable is queued but not run; avoids multiple unnecessary runnable tasks
	 */
	private boolean mDoProcessResultsIsQueued = false;
	/**
	 * Method to ensure results queue is processed.
	 */
	private final Runnable mDoProcessResults = new Runnable() {
		@Override
		public void run() {
			synchronized(mDoProcessResults) {
				mDoProcessResultsIsQueued = false;
			}
			processResults();				
		}
	};

	/**
	 * Run the task then queue the results.
	 * 
	 * @param thread		Thread on which task will run
	 * @param taskWrapper	Wrapper of task to run
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

		// Use the thread object to get some context stuff (mainly DBs)
		taskWrapper.activeThread = thread;
		try {
			task.run(taskWrapper);
		} catch (Exception e) {
			taskWrapper.exception = e;
			Logger.logError(e, "Error running task");
		} finally {
			// Dereference
			taskWrapper.activeThread = null;			
		}

		synchronized(this) {

			// Queue the call to finished() if necessary.
			if (taskWrapper.finishRequested || mTaskFinishListener != null) {
				while(true) {
					try {
						mResultQueue.put(taskWrapper);
						break;
					} catch (InterruptedException e) {
						try { //noinspection BusyWait
							Thread.sleep(50); } catch (InterruptedException ignored) {}
					}
				}
				// Queue Runnable in the UI thread.
				synchronized(mDoProcessResults) {
					if (!mDoProcessResultsIsQueued) {
						mDoProcessResultsIsQueued = true;
						mHandler.post(mDoProcessResults);						
					}
				}
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
			while (true) { //!mTerminate) {
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
						task.onFinish(req.exception);
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

	public interface SimpleTaskContext {
		CatalogueDBAdapter getDb();
		/** 'Covers' database helper */
		CoversDbHelper getCoversDb();
		/** Utils object */
		Utils getUtils();
		/** Accessor */
		void setRequiresFinish(boolean requiresFinish);
		/** Accessor */
		boolean getRequiresFinish();
		/** Accessor */
		boolean isTerminating();
	}

	/**
	 * Class to actually run the tasks. Can start more than one. They wait until there is nothing left in
	 * the queue before terminating.
	 * 
	 * @author Philip Warner
	 */
	private class SimpleTaskQueueThread extends Thread {
		/** DB Connection, if task requests one. Survives while thread is alive */
		CatalogueDBAdapter mDb = null;
		/** Covers DB Connection, if task requests one. Survives while thread is alive */
		CoversDbHelper mCoversDb = null;
		/** Utils object, if needed. Survives while thread is alive */
		Utils mUtils = null;

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
				try {
					if (mDb != null)
						mDb.close();					
				} catch (Exception ignored) {}
				try {
					if (mCoversDb != null)
						mCoversDb.close();					
				} catch (Exception ignored) {}
				try {
					if (mUtils != null)
						mUtils.close();					
				} catch (Exception ignored) {}
			}
		}

		public CatalogueDBAdapter getDb() {
			if (mDb == null) {
				mDb = new CatalogueDBAdapter(BookCatalogueApp.context);
				mDb.open();
			}
			return mDb;
		}

		public Utils getUtils() {
			if (mUtils == null)
				mUtils = new Utils();
			return mUtils;
		}

		public CoversDbHelper getCoversDb() {
			if (mCoversDb == null)
				mCoversDb = new CoversDbHelper();
			return mCoversDb;
		}
	}
}
