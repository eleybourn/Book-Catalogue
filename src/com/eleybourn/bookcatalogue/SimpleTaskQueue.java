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
import java.util.concurrent.LinkedBlockingQueue;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

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
public class SimpleTaskQueue extends Thread {
	// Execution queue
	private BlockingStack<SimpleTask> mQueue = new BlockingStack<SimpleTask>();
	// Results queue
	private LinkedBlockingQueue<SimpleTask> mResultQueue = new LinkedBlockingQueue<SimpleTask>();
	// Flag indicating this object should terminate.
	private boolean mTerminate = false;
	// Handler for sending tasks to the UI thread.
	private Handler mHandler = new Handler();

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

	/**
	 * Constructor. Nothing to see here, move along. Just start the thread.
	 * 
	 * @author Grunthos
	 *
	 */
	SimpleTaskQueue() {
		start();
	}

	/**
	 * Terminate processing.
	 */
	public void finish() {
		mTerminate = true;
		this.interrupt();
	}

	/**
	 * Queue a request to run in the worker thread.
	 * 
	 * @param task		Task to run.
	 */
	public void request(SimpleTask task) {
		//Log.i("BC","Queueing request");
		try {
			mQueue.push(task);				
		} catch (InterruptedException e) {
			// Ignore. This happens if the object is being terminated.
			//Log.e("BC", "Thread interrupted while queueing request", e);
		}
		//Log.i("BC","Queued request");
	}

	/**
	 * Main worker thread logic
	 */
	public void run() {
		//Log.i("BC","Thread running");
		try {
			while (!mTerminate) {
				//Log.i("BC","Thread waiting for request");
				SimpleTask req = mQueue.pop();
				//Log.i("BC","Thread handling request");
				handleRequest(req);
				//Log.i("BC","Thread handled request");
			}
		} catch (InterruptedException e) {
			// Ignore; these will happen when object is destroyed
		} catch (Exception e) {
			Log.e("BC", "Exception in thread", e);
		}
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
			Log.e("BC", "Error running task", e);
		}
		Log.i("BC","Thread queueing result");
		try {
			mResultQueue.put(task);	
		} catch (InterruptedException e) {
			//Log.e("BC", "Thread interrupted while queueing request", e);
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
					Log.e("BC", "Error processing request result", e);
				}
			}
		} catch (Exception e) {
			Log.e("BC", "Exception in processResults in UI thread", e);
		}
	}
}
