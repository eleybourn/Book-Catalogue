package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.FutureTask;;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

public class ImageFetcher<T> extends Thread {
	//private ArrayList<ImageRequest> mQueue;
	private LinkedBlockingQueue<ImageRequest> mQueue = new LinkedBlockingQueue<ImageRequest>();
	private LinkedBlockingQueue<ImageRequest> mResultQueue = new LinkedBlockingQueue<ImageRequest>();
	private boolean mTerminate = false;
	private Handler mHandler = new Handler();

	public interface RequestHandler<T> {
		void get(T context);
		void done(T context);
	}

	ImageFetcher() {
		start();
	}

	public void finish() {
		mTerminate = true;
		this.interrupt();
	}

	private class ImageRequest {
		RequestHandler<T>	handler;
		T		 			context;
		ImageRequest(T context, RequestHandler<T> handler) {
			this.context = context;
			this.handler = handler;
		}
	}

	public void request(T context, RequestHandler<T> request) {
		ImageRequest req = new ImageRequest(context, request);
		Log.i("BC","Queueing request");
		try {
			mQueue.put(req);				
		} catch (InterruptedException e) {
			//Log.e("BC", "Thread interrupted while queueing request", e);
		}
		Log.i("BC","Queued request");
	}

	public void run() {
		Log.i("BC","Thread running");
		try {
			while (!mTerminate) {
				ImageRequest req = mQueue.take();
				Log.i("BC","Thread handling request");
				handleRequest(req);
				Log.i("BC","Thread handled request");
			}
		} catch (Exception e) {
			
		}
	}

	private Runnable mDoProcessResults = new Runnable() {
		@Override
		public void run() {
			processResults();
		}
	};

	private void handleRequest(final ImageRequest req) {
		try {
			req.handler.get(req.context);
		} catch (Exception e) {
			Log.e("BC", "Error fetching image", e);
		}
		Log.i("BC","Thread queuening result");
		try {
			mResultQueue.put(req);	
		} catch (InterruptedException e) {
			//Log.e("BC", "Thread interrupted while queueing request", e);
		}
		mHandler.post(mDoProcessResults);
	}

	private void processResults() {
		try {
			while (!mTerminate) {
				ImageRequest req = mResultQueue.poll();
				if (req == null)
					break;
				try {
					req.handler.done(req.context);
				} catch (Exception e) {
					Log.e("BC", "Error processing request result", e);
				}
			}
		} catch (Exception e) {
			
		}
	}
}
