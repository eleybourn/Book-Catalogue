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
package com.eleybourn.bookcatalogue.messaging;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.utils.Logger;
/**
 * Switchboard class for disconnecting listener instances from task instances. Maintains
 * separate lists and each 'sender' queue maintains a last-message for re-transmission
 * when a listener instance (re)connects.
 * Usage:
 * A sender (typically a background task, thread or thread manager) registers itself and is assigned
 * a unique ID. The creator of the sender uses the ID as the key to later retrieval.
 * The listener must have access to the unique ID and use that to register themselves.
 * The listener should call addListener, removeListener, reply or getController as necessary.
 * ENHANCE: Allow fixed sender IDs to ensure uniqueness and/or allow multiple senders for specific IDs
 * 
 * @author pjw
 *
 * @param <T>	The Class of message that this switchboard sends
 * @param <U>	The Class of controller object made available to listeners. The controller gives access to the sender.
 */
public class MessageSwitch<T,U> {
	/** ID counter for unique sender IDs; set > 0 to allow for possible future static senders **/
	private static Long mSenderIdCounter = 1024L;
	/** List of message sources */
	private final Hashtable<Long,MessageSender<U>> mSenders = new Hashtable<>();
	/** List of all messages in the message queue, both messages and replies */
	private final LinkedBlockingQueue<RoutingSlip> mMessageQueue = new LinkedBlockingQueue<>();
	/** List of message listener queues */
	private final Hashtable<Long, MessageListeners> mListeners = new Hashtable<>();

	/** Handler object for posting to main thread and for testing if running on UI thread */
	private static final Handler mHandler = new Handler(Looper.getMainLooper());

	/** Interface that must be implemented by any message that will be sent via send() */
	public interface Message<T> {
		/**
		 * Method to deliver a message.
		 * 
		 * @param listener		Listener to who message must be delivered
		 * 
		 * @return		true if message should not be delivered to any other listeners or stored for delivery as 'last message'
		 * 				should only return true if the message has been handled and would break the app if delivered more than once.
		 */
        boolean deliver(T listener);
	}

	/** Register a new sender and it's controller object; return the unique ID for this sender */
	public Long createSender(U controller) {
		MessageSenderImpl s = new MessageSenderImpl(controller);
		mSenders.put(s.getId(), s);
		return s.getId();
	}

	/**
	 * Add a listener for the specified sender ID
	 * 
	 * @param senderId		ID of sender to which the listener listens
	 * @param listener		Listener object
	 * @param deliverLast	If true, send the last message (if any) to this listener
	 */
	public void addListener(Long senderId, final T listener, boolean deliverLast) {
		// Add the listener to the queue, creating queue if necessary
		MessageListeners queue;
		synchronized(mListeners) {
			queue = mListeners.get(senderId);
			if (queue == null) {
				queue = new MessageListeners();
				mListeners.put(senderId,  queue);
			}
			queue.add(listener);
		}
		// Try to deliver last message if requested
		if (deliverLast) {
			final MessageRoutingSlip r = queue.getLastMessage();
			// If there was a message then send to the passed listener
			if (r != null) {
				// Do it on the UI thread.
				if (mHandler.getLooper().getThread() == Thread.currentThread()) {
					r.message.deliver(listener);
				} else {
					mHandler.post(() -> r.message.deliver(listener));
				}
			}
		}
	}

	/**
	 * Remove the specified listener from the specified queue
	 */
	public void removeListener(Long senderId, T l) {
		synchronized(mListeners) {
			MessageListeners queue = mListeners.get(senderId);
			if (queue != null)
				queue.remove(l);
		}
	}

	/**
	 * Send a message to a queue
	 * 
	 * @param senderId		Queue ID
	 * @param message		Message to send
	 */
	public void send(long senderId, Message<T> message) {
		// Create a routing slip
		RoutingSlip m = new MessageRoutingSlip(senderId, message);
		// Add to queue
		synchronized(mMessageQueue) {
			mMessageQueue.add(m);
		}
		// Process queue
		startProcessingMessages();
	}

	/**
	 * Get the controller object associated with a sender ID
	 * 
	 * @param senderId	ID of sender
	 * 
	 * @return	Controller object of type 'U'
	 */
	public U getController(long senderId) {
		MessageSender<U> sender = mSenders.get(senderId);
		if (sender != null) {
			return sender.getController();
		} else {
			return null;
		}
	}

	/**
	 * Interface for all messages sent to listeners
	 * 
	 * @author pjw
	 *
	 * @param <U>	Arbitrary class that will be responsible for the message
	 */
	private interface MessageSender<U> {
		long getId();
		void close();
		U getController();
	}

	/**
	 * Class used to hold a list of listener objects
	 * 
	 * @author pjw
	 */
	private class MessageListeners implements Iterable<T> {
		/** Last message sent */
		private MessageRoutingSlip mLastMessage = null;
		/** Weak refs to all listeners */
		private final ArrayList<WeakReference<T>> mList = new ArrayList<>();

		/** Accessor */
		public void setLastMessage(MessageRoutingSlip m) {
			mLastMessage = m;
		}

		/** Accessor */
		public MessageRoutingSlip getLastMessage() {
			return mLastMessage;
		}

		/** Add a listener to this queue */
		public void add(T listener) {
			synchronized(mList) {
				mList.add(new WeakReference<>(listener));
			}
		}

		/**
		 * Remove a listener from this queue; also removes dead references
		 *  
		 * @param listener	Listener to be removed
		 */
		public void remove(T listener) {
			synchronized(mList) {
				// List of refs to be removed
				ArrayList<WeakReference<T>> toRemove = new ArrayList<>();
				// Scan the list for matches or dead refs
				for(WeakReference<T> w: mList) {
					T l = w.get();
					if (l == null || l == listener) {
						toRemove.add(w);
					}
				}
				// Remove all listeners we found
				for(WeakReference<T> w: toRemove)
					mList.remove(w);				
			}
		}

		/**
		 * Return an iterator to a *copy* of the valid underlying elements. This means that
		 * callers can make changes to the underlying list with impunity, and more importantly
		 * they can iterate over type T, rather than a bunch of weak references to T.
		 * Side-effect: removes invalid listeners
		 */
		@NonNull
        @Override
		public Iterator<T> iterator() {
			ArrayList<T> list = new ArrayList<>();
			ArrayList<WeakReference<T>> toRemove = null;
			synchronized(mList) {
				for(WeakReference<T> w: mList) {
					T l = w.get();
					if (l != null) {
						list.add(l);
					} else {
						if (toRemove == null)
							toRemove = new ArrayList<>();
						toRemove.add(w);
					}
				}
				if (toRemove != null)
					for(WeakReference<T> w: toRemove)
						mList.remove(w);
			}
			return list.iterator();
		}

	}

	/**
	 * Remove a sender and it's queue
	 */
	private void removeSender(MessageSender<U> s) {
		synchronized(mSenders) {
			mSenders.remove(s.getId());
		}
	}

	/**
	 * Interface implemented by all routing slips objects
	 * 
	 * @author pjw
	 *
	 */
	private interface RoutingSlip {
		void deliver();
	}

	/**
	 * RoutingSlip to deliver a Message object to all associated listeners
	 * 
	 * @author pjw
	 */
	private class MessageRoutingSlip implements RoutingSlip {
		/** Destination queue (sender ID) */
		long destination;
		/** Message to deliver */
		Message<T> message;

		/** Constructor */
		public MessageRoutingSlip(long destination, Message<T> message) {
			this.destination = destination;
			this.message = message;
		}

		/**
		 * Deliver message to all members of queue of sender
		 */
		@Override
		public void deliver() {
			// Iterator for iterating queue
			Iterator<T> i = null;

			MessageListeners queue;
			// Get the queue and find the iterator
			synchronized(mListeners) {
				// Queue for given ID
				queue = mListeners.get(destination);
				if (queue != null) {
					queue.setLastMessage(this);
					i = queue.iterator();
				}
			}
			// If we have an iterator, send the message to each listener
			if (i != null) {
				boolean handled = false;
				while(i.hasNext()) {
					T l = i.next();
					try {
						if (message.deliver(l)) {
							handled = true;
							break;
						}
							
					} catch (Exception e) {
						Logger.logError(e, "Error delivering message to listener");					
					}
				}
				if (handled) {
					queue.setLastMessage(null);
				}
			}
		}
	}

	/**
	 * Implementation of Message sender object
	 * 
	 * @author pjw
	 */
	private class MessageSenderImpl implements MessageSender<U> {
		private final long mId = ++mSenderIdCounter;
		private final U mController;

		/** Constructor */
		public MessageSenderImpl(U controller) {
			mController = controller;
		}

		/** accessor */
		@Override
		public long getId() {
			return mId;
		}

		/** Accessor */
		@Override
		public U getController() {
			return mController;
		}

		/** Close and delete this sender */
		@Override
		public void close() {
			synchronized(mSenders) {
				MessageSwitch.this.removeSender(this);				
			}
		}
	}

	/**
	 * If in UI thread, then process the queue, otherwise post a new runnable 
	 * to process the queued messages
	 */
	private void startProcessingMessages() {
		if (mHandler.getLooper().getThread() == Thread.currentThread()) {
			processMessages();
		} else {
			mHandler.post(this::processMessages
            );
		}
	}

	/**
	 * Process the queued messages
	 */
	private void processMessages() {
		RoutingSlip m;
		do {
			synchronized(mMessageQueue) {
				m = mMessageQueue.poll();
			}
			if (m == null)
				break;

			m.deliver();
		} while (true);
	}

	/**
	 * Accessor. Sometimes senders (or receivers) need to check which thread they are on and possibly post runnable.
	 * 
	 * @return the Handler object
	 */
	public static Handler getHandler() {
		return mHandler;
	}
}
