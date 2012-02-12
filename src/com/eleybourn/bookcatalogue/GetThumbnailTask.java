package com.eleybourn.bookcatalogue;

import java.io.File;
import java.lang.ref.WeakReference;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTaskContext;

/**
 * Task to get a thumbnail from the sdcard or cover database. It will resize it as required and 
 * apply the resulting Bitmap to the related view.
 * 
 * This object also has it's own statically defined SimpleTaskQueue for getting thumbnails in
 * background.
 * 
 * @author Grunthos
 */
public class GetThumbnailTask implements SimpleTask {

	// Queue for background thumbnail retrieval; allow 2 threads. More is nice, but with 
	// many books to process it introduces what looks like lag when scrolling: 5 tasks
	// building now-invisible views is pointless. 
	private static final SimpleTaskQueue mQueue = new SimpleTaskQueue("thumbnails", 2);

	/**
	 * Create a task to convert, set and store the thumbnail for the passed book.
	 * If cacheWasChecked = false, then the cache will be checked before any work is
	 * done, and if found in the cache it will be used. This option is included to
	 * reduce contention between background and foreground tasks: the forground (UI)
	 * thread checks the chache only if there are no background cache-related tasks
	 * currently running.
	 */
	public static void getThumbnail(long bookId, ImageView view, int maxWidth, int maxHeight, boolean cacheWasChecked) {
		GetThumbnailTask t = new GetThumbnailTask( bookId, view, maxWidth, maxHeight, cacheWasChecked);
		mQueue.enqueue(t);
	}
	
	/**
	 * Allow other tasks (or subclasses tasks) to be queued.
	 * 
	 * @param t		Task to a[put in queue
	 */
	public static void enqueue(SimpleTask t) {
		mQueue.enqueue(t);
	}

	/** Reference to the view we are using */
	WeakReference<ImageView> mView = null;
	/** ID of book whose cover we are getting */
	private final long mBookId;
	/** Resulting bitmap object */
	Bitmap mBitmap = null;
	/** Flag indicating original caller had checked cache */
	private final boolean mCacheWasChecked;
	/** Flag indicating image was found in the cache */
	private boolean mWasInCache = false;
	/** The width of the thumbnail retrieved (based on preferences) */
	private int mWidth;
	/** The height of the thumbnail retrieved (based on preferences) */
	private int mHeight;
	/** Indicated we want the queue manager to call the finished() method. */
	private boolean  mWantFinished = true;
	
	public static boolean hasActiveTasks() {
		return mQueue.hasActiveTasks();
	}
	/**
	 * Utility routine to remove any record of a prior thumbnail task from a View object.
	 * 
	 * Used internally and from Utils.fetchFileIntoImageView to ensure that nothing 
	 * overwrites the view.
	 * 
	 * @param queue
	 * @param v
	 */
	public static void clearOldTaskFromView(final ImageView v) {
		final GetThumbnailTask oldTask = (GetThumbnailTask)v.getTag(R.id.TAG_GET_THUMBNAIL_TASK);
		if (oldTask != null) {
			v.setTag(R.id.TAG_GET_THUMBNAIL_TASK, null);
			mQueue.remove(oldTask);
		}		
	}

	/**
	 * Constructor. Clean the view and save the details of what we want.
	 * 
	 * @param queue
	 * @param bookId
	 * @param v
	 * @param width
	 * @param height
	 */
	public GetThumbnailTask( final long bookId, final ImageView v, int maxWidth, int maxHeight, boolean cacheWasChecked ) {
		clearOldTaskFromView(v);

		mView = new WeakReference<ImageView>(v);
		mBookId = bookId;
		mCacheWasChecked = cacheWasChecked;
		mWidth = maxWidth;
		mHeight = maxHeight;

		// Clear current image
		v.setImageBitmap(null);

		// Associate the view with this task
		v.setTag(R.id.TAG_GET_THUMBNAIL_TASK, this);
	}

	/**
	 * Do the image manipulation. We wait at start to prevent a flood of images from hitting the UI thread.
	 */
	@Override
	public void run(SimpleTaskContext taskContext) {
		try {
			/*
			try {
				Thread.sleep(10); // Let the UI have a chance to do something if we are racking up images!
			} catch (InterruptedException e) {
			}
			*/
			//
			// fetchBookCoverIntoImageView is an expensive operation. Makre wure its still needed.
			//

			// Get the view we are targeting and make sure it is valid
			ImageView v = mView.get();
			if (v == null) {
				mWantFinished = false;
				return;
			}

			// Make sure the view is still associated with this task. We don't want to overwrite the wrong image
			// in a recycled view.
			if (!this.equals(v.getTag(R.id.TAG_GET_THUMBNAIL_TASK))) {
				mWantFinished = false;
				return;
			}

			File originalFile = CatalogueDBAdapter.fetchThumbnail(mBookId);

			if (!mCacheWasChecked) {
				final String cacheId = Utils.getCoverCacheId(mBookId, mWidth, mHeight);
				mBitmap = Utils.fetchCachedImageIntoImageView(originalFile, null, cacheId);
				mWasInCache = (mBitmap != null);
			}

			if (mBitmap == null)
				mBitmap = Utils.fetchBookCoverIntoImageView(null, mWidth, mHeight, true, mBookId, false, false);
			//}			
		} finally {
		}
	}

	/**
	 * Handle the results of the task.
	 */
	@Override
	public void finished() {
		// Get the view we are targetting and make sure it is valid
		ImageView v = mView.get();
		// Make sure the view is still associated with this task. We dont want to overwrite the wrong image
		// in a recycled view.
		final boolean viewIsValid = (v != null && this.equals(v.getTag(R.id.TAG_GET_THUMBNAIL_TASK)));

		// Clear the view tag
		if (viewIsValid)
			v.setTag(R.id.TAG_GET_THUMBNAIL_TASK, null);

		if (mBitmap != null) {
			if (!mWasInCache)  {
				// Queue the image to be written to the cache. Do it in a separate queue to avoid delays in displaying image
				// and to avoid contention -- the cache queue only has one thread. Tell the cache write it can be recycled
				// if we don't have a valid view.
				ThumbnailCacheWriterTask.writeToCache(Utils.getCoverCacheId(mBookId, mWidth, mHeight), mBitmap, !viewIsValid);
			}
			if (viewIsValid) {
				//LayoutParams lp = new LayoutParams(mBitmap.getWidth(), mBitmap.getHeight()); 
				//v.setLayoutParams(lp);
				v.setImageBitmap(mBitmap);
			} else {
				mBitmap.recycle();
				mBitmap = null;
			}
		} else {
			v.setImageResource(android.R.drawable.ic_dialog_alert);
		}

		//System.out.println("Set image for ID " + mBookId);
	}

	@Override
	public boolean runFinished() {
		return mWantFinished;
	}

}
