package com.eleybourn.bookcatalogue.utils;

import java.io.Serializable;
import java.util.Comparator;
import java.util.PriorityQueue;

import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

/**
 * Class to execute Runnable objects in a separate thread after a predetermined delay.
 * 
 * @author pjw
 */
public class Terminator {
	
	/**
	 * Dummy method to make sure static initialization is done. Need to be 
	 * called from main thread (usually at app startup).
	 */
	public static void init() {
	}

	/** Task queue to get book lists in background */
	private static final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("Terminator", 1);

	/** Flag indicating the main thread process is still running and waiting for 
	 *  a timer to elapse.
	 */
	private static boolean mIsRunning = false;
	/** Object used in synchronization */
	private static final Object mWaitObject = new Object();

	/** Details of the runnable to run */
	private static class Event {
		public final long time;
		public final Runnable runnable;
		public Event(Runnable r, long time) {
			runnable = r;
			this.time = time;
		}
	}

	/** Comparator to ensure Event objects are returned in the correct order */
	private static class EventComparator implements Comparator<Event>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(Event lhs, Event rhs) {
			if (lhs.time < rhs.time) {
				return -1;
			} else if (lhs.time > rhs.time) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	/** Queue of Event objects currently awaiting execution */
	private static final PriorityQueue<Event> mEvents = new PriorityQueue<Event>(10, new EventComparator());

	/**
	 * Enqueue the passed runnable to be run after the specified delay.
	 * 
	 * @param r			Runnable to execute
	 * @param delay		Delay before execution
	 */
	public static void enqueue(Runnable r, long delay) {
		// Compute actual time
		long time = System.currentTimeMillis() + delay;
		// Create Event and add to queue.
		Event e = new Event(r, time);
		synchronized(mTaskQueue) {
			mEvents.add(e);
			// Make sure task is actually running
			if (!mIsRunning) {
				mTaskQueue.enqueue(new TerminatorTask());
				mIsRunning = true;
			} else {
				// Wake up task in case this object has a shorter timer
				synchronized(mWaitObject){
					mWaitObject.notify();					
				}
			}
		}
	}

	/**
	 * Background task to process the queue and schedule appropriate delays
	 * 
	 * @author pjw
	 */
	private static class TerminatorTask implements SimpleTask {

		@Override
		public void run(SimpleTaskContext taskContext) throws Exception {
			System.out.println("Terminator starting");
			do {
				Event e;
				long delay;
				// Check when next task due
				synchronized(mTaskQueue) {
					// Lok for a task; if exception or none found, abort.
					try {
						e = mEvents.peek();						
					} catch(Exception ex) {
						e = null;
					}
					if (e == null) {
						mIsRunning = false;
						return;
					}
					// Check how long until it should run
					delay = e.time - System.currentTimeMillis();
					// If it's due now, then remove it from the queue.
					if (delay <= 0)
						mEvents.remove(e);
				}

				if (delay > 0) {
					// If we have nothing to run, wait for first
					synchronized(mWaitObject) {
						try {	
							mWaitObject.wait(delay);
						} catch(Exception ex) {
							Logger.logError(ex);
						}
					}
				} else {
					// Run the available event
					// TODO: if 'run' blocks, then our Terminator stops terminating! Should probably be another thread. But...not for now.
					try {
						e.runnable.run();
					} catch(Exception ex) {
						Logger.logError(ex);
					}
				}
			} while (true);
		}

		@Override
		public void onFinish(Exception e) {
			System.out.println("Terminator terminating. I'll be back.");
		}
		
	}
}
