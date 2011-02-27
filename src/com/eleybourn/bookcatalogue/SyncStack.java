package com.eleybourn.bookcatalogue;

import java.util.Stack;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Based closely on LinkedBlockingQueue. Ideally we would use BlockingDeque but that is only
 * available in Android 2.3.
 * 
 * @author Grunthos
 * @param <T>
 */
public class SyncStack<T> {
	private Stack<T> mStack;
	// Lock help by pop
	private final ReentrantLock mPopLock = new ReentrantLock();
	// Signal for available items
	private final Condition mNotEmpty = mPopLock.newCondition();

	// Lock held by push()
	private final ReentrantLock mPushLock = new ReentrantLock();

	SyncStack() {
		mStack = new Stack<T>();
	}

	void push(T o) throws InterruptedException {
		final ReentrantLock pushLock = this.mPushLock;

		pushLock.lockInterruptibly();
		try {
			mStack.push(o);
		} finally {
			pushLock.unlock();
		}
	
	}
 
	T pop() throws InterruptedException {
		final ReentrantLock popLock = mPopLock;

		popLock.lockInterruptibly();
		try {
			
		} finall {
			popLock.unlock();
		}
		T o = null;
		synchronized(mStack) {
			o = mStack.pop();
		}
	}

	/**
	 * Signals a waiting pop. Called only from push.
	 */
	private void signalNotEmpty() {
		final ReentrantLock popLock = mPopLock;
		popLock.lock();
		try {
			mNotEmpty.signal();
		} finally {
			popLock.unlock();
		}
	}

}

