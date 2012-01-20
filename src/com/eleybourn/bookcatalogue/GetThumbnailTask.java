package com.eleybourn.bookcatalogue;

import java.lang.ref.WeakReference;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;

/**
 * Task to get a thumbnail from the sdcard or cover database. It will resize it as required and 
 * apply the resulting Bitmap to the related view.
 * 
 * @author Grunthos
 */
public class GetThumbnailTask implements SimpleTask {
	/** Reference to the view we are using */
	WeakReference<ImageView> mView = null;
	/** ID of book whose cover we are getting */
	long mBookId = 0;
	/** Resulting bitmap object */
	Bitmap mBitmap = null;
	/** Desire output width */
	int mWidth;
	/** Desire output height */
	int mHeight;

	/**
	 * Utility routine to remove any record of a prior thumbnail task from a View object.
	 * 
	 * Used internally and from Utils.fetchFileIntoImageView to ensure that nothing 
	 * overwrites the view.
	 * 
	 * @param queue
	 * @param v
	 */
	public static void clearTaskFromView(SimpleTaskQueue queue, ImageView v) {
		GetThumbnailTask oldTask = (GetThumbnailTask)v.getTag(R.id.TAG_TASK);
		if (oldTask != null) {
			queue.remove(oldTask);
			v.setTag(R.id.TAG_TASK, null);
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
	public GetThumbnailTask(SimpleTaskQueue queue, long bookId, ImageView v, int width, int height) {
		synchronized(v) {
			clearTaskFromView(queue, v);
			v.setImageBitmap(null);
			mView = new WeakReference<ImageView>(v);
			mBookId = bookId;
			mWidth = width;
			mHeight = height;
			// Associate the view with this task
			v.setTag(R.id.TAG_TASK, this);
		}
	}
	/**
	 * Do the image manipulation. We wait at start to prevent a flood of images from hitting the UI thread.
	 */
	@Override
	public void run() {
		try {
			Thread.sleep(50); // Let the UI have a chance to do something if we are racking up images!
		} catch (InterruptedException e) {
		}
		// Get the view we are targetting and make sure it is valid
		ImageView v = mView.get();
		if (v == null)
			return;
		synchronized(v) {
			// Make sure the view is still associated with this task. Just to avoid unnecessary work.
			if (!v.getTag(R.id.TAG_TASK).equals(this))
				return;
			// Create the bitmap.
			mBitmap = CatalogueDBAdapter.fetchThumbnailIntoImageView(mBookId, null, mWidth, mHeight, true, null);			
		}

	}

	/**
	 * Handle the results of the task.
	 */
	@Override
	public void finished() {
		// Get the view we are targetting and make sure it is valid
		ImageView v = mView.get();
		if (v == null)
			return;
		synchronized(v) {
			// Make sure the view is still associated with this task. We dont want to overwrite the wrong image
			// in a recycled view.
			if (!v.getTag(R.id.TAG_TASK).equals(this))
				return;
			v.setImageBitmap(mBitmap);
			v.setTag(R.id.TAG_TASK, null);
		}
	}

}
