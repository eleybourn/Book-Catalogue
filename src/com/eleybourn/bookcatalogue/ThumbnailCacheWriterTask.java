package com.eleybourn.bookcatalogue;

import android.graphics.Bitmap;

import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;

/**
 * Background task to save a bitmap into the covers thumbnail database. Runs in background
 * because it involves compression and IO, and can be safely queued. Failures can be ignored
 * because it is just writing to a cache used solely for optimization.
 * 
 * This class also has its onw statis SimpleTaskQueue.
 * 
 * @author Grunthos
 */
public class ThumbnailCacheWriterTask implements SimpleTask {

	// ******** STATIC Data ******** //
	
	/** 
	 * Single-thread queue for writing data. There is no point in more than one thread since
	 * the database will force serialization of the updates.
	 */
	private static SimpleTaskQueue mQueue = new SimpleTaskQueue("cachewriter", 1);
	/** 'Covers' database helper */
	private static CoversDbHelper mDb = new CoversDbHelper();

	/**
	 * Queue the passed bitmap to be compresed and written to the database, will be recycled if
	 * flag is set.
	 * 
	 * @param cacheId		Cache ID to use
	 * @param source		Raw bitmap to store
	 * @param canRecycle	Indicates bitmap should be recycled after use
	 */
	public static void writeToCache(String cacheId, Bitmap source, boolean canRecycle) {
		ThumbnailCacheWriterTask t = new ThumbnailCacheWriterTask(cacheId, source, canRecycle);
		mQueue.enqueue(t);
	}

	/**
	 * Check if there is an active task in the queue.
	 * 
	 * @return
	 */
	public static boolean hasActiveTasks() {
		return mQueue.hasActiveTasks();
	}

	// ******** INSTANCE Data ******** //

	/** Cache ID of this object */
	private String mCacheId;
	/** Indicates if Bitmap can be recycled when no longer needed */
	private final boolean mCanRecycle;
	/** Bitmap to store */
	private Bitmap mBitmap;
	
	/**
	 * Create a task that will compress the passed bitmap and write it to the database, 
	 * it will also be recycled if flag is set.
	 * 
	 * @param cacheId		Cache ID to use
	 * @param source		Raw bitmap to store
	 * @param canRecycle	Indicates bitmap should be recycled after use
	 */
	public ThumbnailCacheWriterTask(String cacheId, Bitmap source, boolean canRecycle) {
		mCacheId = cacheId;
		mBitmap = source;
		mCanRecycle = canRecycle;
	}

	/**
	 * Do the main work in the background thread.
	 */
	@Override
	public void run() {
		mDb.saveFile(mCacheId, mBitmap);
		if (mCanRecycle) {
			mBitmap.recycle();
			mBitmap = null;
		}
		mCacheId = null;
	}

	@Override
	public void finished() {
	}

}
