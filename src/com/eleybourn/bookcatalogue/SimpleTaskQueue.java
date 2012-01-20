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
	private LinkedBlockingQueue<SimpleTask> mResultQueue = new LinkedBlockingQueue<SimpleTask>();
	// Flag indicating this object should terminate.
	private boolean mTerminate = false;
	// Handler for sending tasks to the UI thread.
	private Handler mHandler = new Handler();
	// Name for this queue
	private final String mName;
	// Threads associate with this queue
	ArrayList<SimpleTaskQueueThread> mThreads = new ArrayList<SimpleTaskQueueThread>();

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
		void run();
		void finished();
	}

	private static class SimpleTaskWrapper {
		private static Long mCounter = 0L;
		public SimpleTask task;
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
	 * Queue a request to run in the worker thread.
	 * 
	 * @param task		Task to run.
	 */
	public long enqueue(SimpleTask task) {
		SimpleTaskWrapper wrapper = new SimpleTaskWrapper(task);
		try {
			mQueue.push(wrapper);
		} catch (InterruptedException e) {
			// Ignore. This happens if the object is being terminated.
		}
		//System.out.println("SimpleTaskQueue(added): " + mQueue.size());
		synchronized(this) {
			int qSize = mQueue.size();
			int nThreads = mThreads.size();
			if (nThreads < qSize && nThreads < 5) {
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
	private void handleRequest(final SimpleTask task) {
		try {
			task.run();
		} catch (Exception e) {
			Logger.logError(e, "Error running task");
		}
		try {
			mResultQueue.put(task);	
		} catch (InterruptedException e) {
		}
		// Wake up the UI thread.
		mHandler.post(mDoProcessResults);
	}

	/**
	 * Run in the UI thread, process the results queue.
	 */
	private void processResults() {
		try {
			while (!mTerminate) {
				// Get next; if none, exit.
				SimpleTask req = mResultQueue.poll();
				if (req == null)
					break;
				// Call the task handler; log and ignore errors.
				try {
					req.finished();
				} catch (Exception e) {
					Logger.logError(e, "Error processing request result");
				}
			}
		} catch (Exception e) {
			Logger.logError(e, "Exception in processResults in UI thread");
		}
	}

	/**
	 * Class to actually run the tasks. Can start more than one. They wait until there is nothing left in
	 * the queue before terminating.
	 * 
	 * @author Grunthos
	 */
	private class SimpleTaskQueueThread extends Thread {
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
							}
						}
						return;
					}

					//System.out.println("SimpleTaskQueue(run): " + mQueue.size());						
					handleRequest(req.task);
				}
			} catch (InterruptedException e) {
				// Ignore; these will happen when object is destroyed
			} catch (Exception e) {
				Logger.logError(e);
			}
		}
	}
}
