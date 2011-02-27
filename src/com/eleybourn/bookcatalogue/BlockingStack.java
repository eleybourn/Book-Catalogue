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

import java.util.Collection;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.util.Log;

/**
 * Based loosely on LinkedBlockingQueue. Ideally we would use BlockingDeque but that is only
 * available in Android 2.3.
 * 
 * A much-simplified blocking stack that satisfies the need of this application. Should be 
 * replaceable with BlockingDeque when we set the min. version requirement to 2.3!
 * 
 * @author Grunthos
 * @param <T>
 */
public class BlockingStack<T> {
	// Underlying stack object
	private Stack<T> mStack;
	// Lock help by pop and by push when an item was added to an empty stack.
	private final ReentrantLock mPopLock = new ReentrantLock();
	// Signal for available items
	private final Condition mNotEmpty = mPopLock.newCondition();

	// Lock held by push(). Probably not needed since we sync on mStack...
	private final ReentrantLock mPushLock = new ReentrantLock();

	BlockingStack() {
		mStack = new Stack<T>();
	}

	/**
	 * Add an object to the stack and signal
	 * 
	 * @param object		Object to add
	 * 
	 * @throws InterruptedException
	 */
	void push(T object) throws InterruptedException {
		final ReentrantLock pushLock = this.mPushLock;

		// This will hold the original stack size, before push.
		int origSize;

		// Make sure we are the only 'pusher' here.
		pushLock.lockInterruptibly();
		//Log.i("BC",mName + " push - Lock");
		try {
			// Add the object and get the size of the current stack
			// we 'synchronize' because it is not at all clear that 
			// push and pop can be done concurrently (unlike the
			// linked list versions of queues).
			synchronized(mStack) {
				origSize = mStack.size();
				mStack.push(object);
			}
			//Log.i("BC",mName + " push - pushed - orig size = " + origSize);
		} finally {
			pushLock.unlock();
			//Log.i("BC",mName + " push - Unlock");
		}
		if (origSize == 0) {
			// It was an empty stack; signal that it has some objects now.
			// But we need to take the popLock because the pop code also
			// messes with this.
			final ReentrantLock popLock = mPopLock;
			popLock.lock();
			try {
				mNotEmpty.signal();
				//Log.i("BC",mName + " push - signalled not empty");
			} finally {
				popLock.unlock();
			}			
		}			
	}

	/**
	 * Remove an object from the stack, wait if none.
	 * 
	 * @return
	 * @throws InterruptedException
	 */
	T pop() throws InterruptedException {
		final ReentrantLock popLock = mPopLock;
		int count;

		T o = null;
		// Make sure we are the only popper.
		popLock.lockInterruptibly();
		//Log.i("BC",mName + " pop - Lock");
		try {
			// Get the size
			synchronized(mStack) {
				count = mStack.size();
			}
			// If none left, wait for another thread to signal.
			while (count ==0) {
				// Wait for the notEmpty condition and get the current size
				//Log.i("BC",mName + " pop - Waiting, count = " + count);
				mNotEmpty.await();
				//Log.i("BC",mName + " pop - Waited");
				synchronized(mStack) {
					count = mStack.size();
				}
				//Log.i("BC",mName + " pop - Waited, count = " + count);					
			};
			// Pop an item
			synchronized(mStack) {
				o = mStack.pop();
			}
			//Log.i("BC",mName + " pop - popped");				
			// If, after popping, there would be more left, resignal for any other threads.
			// Note that the regignal DOES NOT apply to this thread.
			if (count > 1) {
				mNotEmpty.signal();
				//Log.i("BC",mName + " pop - resignalled");				
			}
		} finally {
			popLock.unlock();
		}
		return o;
	}

	/**
	 * Return an object if available, otherwise null.
	 * 
	 * @return	Object
	 * 
	 * @throws InterruptedException
	 */
	T poll() throws InterruptedException {
		final ReentrantLock popLock = mPopLock;

		T o = null;
		// Make sure we are the only popper.
		popLock.lockInterruptibly();
		//Log.i("BC",mName + " poll - Lock");
		try {
			int count;
			// Get the current size
			synchronized(mStack) {
				count = mStack.size();
			}
			//Log.i("BC",mName + " poll - count = " + count);
			// If any present, we know no-one will delete (we are the popper) so get it.
			if (count > 0) {
				// Pop an item
				synchronized(mStack) {
					o = mStack.pop();
				}
				//Log.i("BC",mName + " poll - popped");
				// If, after popping, there would be more left, resignal.
				if (count > 1) {
					//Log.i("BC",mName + " poll - signal");
					mNotEmpty.signal();					
				}
			}
		} finally {
			popLock.unlock();
		}
		return o;
	}
}

