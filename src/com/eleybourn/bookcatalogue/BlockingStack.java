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

import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
	 * Get the size of the stack
	 */
	public int size() {
		return mStack.size();
	}

	/**
	 * Remove the passed element, if present.
	 */
	public boolean remove(T o) {
		synchronized(mStack) {
			return mStack.remove(o);
		}
	}
	
	/**
	 * Return a copy of all elements for safe examination. Obviously this
	 * collection will not reflect reality for very long, but is safe to
	 * iterate etc.
	 * 
	 * @return
	 */
	public Stack<T> getElements() {
		Stack<T> copy = new Stack<T>();
		synchronized(mStack) {
			for(T o : mStack) {
				copy.add(o);
			}
		}
		return copy;
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
		try {
			// Add the object and get the size of the current stack
			// we 'synchronize' because it is not at all clear that 
			// push and pop can be done concurrently (unlike the
			// linked list versions of queues).
			synchronized(mStack) {
				origSize = mStack.size();
				mStack.push(object);
			}
		} finally {
			pushLock.unlock();
		}
		if (origSize == 0) {
			// It was an empty stack; signal that it has some objects now.
			// But we need to take the popLock because the pop code also
			// messes with this.
			final ReentrantLock popLock = mPopLock;
			popLock.lock();
			try {
				mNotEmpty.signal();
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
	T pop(long waitMilliseconds) throws InterruptedException {
		final ReentrantLock popLock = mPopLock;

		T o;
		boolean noTimeLimit = (waitMilliseconds <= 0);
		// Make sure we are the only popper.
		popLock.lockInterruptibly();
		try {
			o = poll();
			// If none left, wait for another thread to signal.
			while (o == null) {
				// Wait for the notEmpty condition, or until timeout if one was specified
				if (noTimeLimit)
					mNotEmpty.await();
				else {
					waitMilliseconds = mNotEmpty.awaitNanos(TimeUnit.MILLISECONDS.toNanos(waitMilliseconds));
					if (waitMilliseconds <= 0) // Ran out of time
						break;
				}
				// Try getting an object
				o = poll();
			};

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
		try {
			int count;
			// Get the current size
			synchronized(mStack) {
				count = mStack.size();
				// If any present, we know no-one will delete (we are the popper) so get it.
				if (count > 0) {
					// Pop an item
					o = mStack.pop();
				}
			}
			// If, after popping, there would be more left, resignal.
			if (count > 1)
				mNotEmpty.signal();					

		} finally {
			popLock.unlock();
		}
		return o;
	}
}

