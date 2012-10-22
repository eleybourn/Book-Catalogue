package com.eleybourn.bookcatalogue.messaging;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.LinkedBlockingQueue;
import com.eleybourn.bookcatalogue.Logger;
import android.os.Handler;

public class MessageSwitch<T,U> {
	private static Long mSenderIdCounter = 0L;
	private Hashtable<Long,MessageSender<U>> mSenders = new Hashtable<Long,MessageSender<U>>();
	private LinkedBlockingQueue<RoutingSlip> mMessageQueue = new LinkedBlockingQueue<RoutingSlip>();
	private Hashtable<Long, MessageListeners> mListeners = new Hashtable<Long, MessageListeners>();

	Handler mHandler = new Handler();

	public interface Message<T> {
		public void deliver(T listener);
	}

	public Long createSender(U replyHandler) {
		MessageSenderImpl s = new MessageSenderImpl(replyHandler);
		mSenders.put(s.getId(), s);
		return s.getId();
	}

	public void addListener(Long queueId, final T listener, boolean deliverLast) {
		synchronized(mListeners) {
			MessageListeners queue = mListeners.get(queueId);
			if (queue == null) {
				queue = new MessageListeners();
				mListeners.put(queueId,  queue);
			}
			queue.add(listener);
			if (deliverLast) {
				final RoutingSlip m = queue.getLastMessage();
				if (m != null) {
					if (mHandler.getLooper().getThread() == Thread.currentThread()) {
						m.deliver();
					} else {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								m.deliver();
							}
						});
					}					
				}
			}
		}
	}

	public void removeListener(Long senderId, T l) {
		synchronized(mListeners) {
			ArrayList<T> queue = mListeners.get(senderId);
			if (queue != null) {
				queue.remove(l);			
			}
		}
	}

	public void send(long senderId, Message<T> message) {
		RoutingSlip m = new MessageRoutingSlip(senderId, message);
		synchronized(mMessageQueue) {
			mMessageQueue.add(m);
		}
		startProcessingMessages();
	}

	public void reply(long senderId, Message<U> reply) {
		RoutingSlip m = new ReplyRoutingSlip(senderId, reply);
		synchronized(mMessageQueue) {
			mMessageQueue.add(m);
		}
		startProcessingMessages();
	}

	public U getController(long senderId) {
		MessageSender<U> sender = mSenders.get(senderId);
		if (sender != null) {
			return sender.getReplyHandler();
		} else {
			return null;
		}
	}

	private interface MessageSender<U> {
		public Long getId();
		public void close();
		public U getReplyHandler();
	}

	private class MessageListeners extends ArrayList<T> {
		private static final long serialVersionUID = -1504405475324961196L;
		private RoutingSlip mLastMessage = null;

		public void setLastMessage(RoutingSlip m) {
			mLastMessage = m;
		}
		public RoutingSlip getLastMessage() {
			return mLastMessage;
		}
		//private final ReentrantLock mPopLock = new ReentrantLock();
		//ReentrantLock getLock() {
		//	return mPopLock;
		//}
	}

	private void removeSender(MessageSender<U> s) {
		synchronized(mSenders) {
			mSenders.remove(s.getId());
		}
	}

	private interface RoutingSlip {
		public void deliver();
	}

	private class MessageRoutingSlip implements RoutingSlip {
		Long destination;
		Message<T> message;
		public MessageRoutingSlip(Long destination, Message<T> message) {
			this.destination = destination;
			this.message = message;
		}
		@Override
		public void deliver() {
			// Make a copy of the list in case it gets modified by a listener or in another thread.
			ArrayList<T> tmpList = null;
			synchronized(mListeners) {
				MessageListeners queue = mListeners.get(destination);
				if (queue != null) {
					queue.setLastMessage(this);
					tmpList = new ArrayList<T>(queue);
				}
			}
			for(T l: tmpList) {
				try {
					message.deliver(l);
				} catch (Exception e) {
					Logger.logError(e, "Error delivering message to listener");					
				}
			}

		}
	}

	private class ReplyRoutingSlip implements RoutingSlip {
		Long destination;
		Message<U> message;
		public ReplyRoutingSlip(Long destination, Message<U> message) {
			this.destination = destination;
			this.message = message;
		}
		@Override
		public void deliver() {
			synchronized(mSenders) {
				MessageSender<U> sender = mSenders.get(this.destination);
				if (sender != null) {
					message.deliver(sender.getReplyHandler());
				}
			}
		}
	}

//	private class Reply {
//		Long destination;
//		Message<U> messenger;
//		public Reply(Long destination, Message<U> messenger) {
//			this.destination = destination;
//			this.messenger = messenger;
//		}
//	}

	private class MessageSenderImpl implements MessageSender<U> {
		private final Long mId = ++mSenderIdCounter;
		private final U mReplyHandler;

		public MessageSenderImpl(U replyHandler) {
			mReplyHandler = replyHandler;
		}

		@Override
		public Long getId() {
			return mId;
		}
		@Override
		public void close() {
			synchronized(mSenders) {
				MessageSwitch.this.removeSender(this);				
			}
		}

		@Override
		public U getReplyHandler() {
			return mReplyHandler;
		}
	}

	private void startProcessingMessages() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				processMessages();
			}}
		);		
	}

	private void processMessages() {
		RoutingSlip m = null;
		do {
			synchronized(mMessageQueue) {
				m = mMessageQueue.poll();
			}
			if (m == null)
				break;

			m.deliver();
		} while (true);
	}

}
