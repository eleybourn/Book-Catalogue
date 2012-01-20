package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.Utils;

/**
 * Background task to load an image for a GoodreadsWork from a URL. Does not store it locally; 
 * it will call the related Work when done.
 * 
 * @author Grunthos
 */
public class GetImageTask implements SimpleTask {
	//private static Integer mIdCounter = 0;
	//private int mId = 0;

	/** URL of image to fetch */
	private final String mUrl;
	/** Byte data of image. NOT a Bitmap because we fetch several and store them in the related
	 * GoodreadsWork object and Bitmap objects are much larger than JPG objects.
	 */
	private byte[] mBytes = null;
	/** Related work */
	private GoodreadsWork mWork;

	/**
	 * Constructor. Save the stuff we need.
	 * 
	 * @param url		URL to retrieve.
	 */
	public GetImageTask(String url, GoodreadsWork work) {
		mUrl = url;
		mWork = work;
	}

	/**
	 * Return the resulting image byte[]
	 * @return
	 */
	public byte[] getBytes() {
		return mBytes;
	}

	/**
	 * Just get the URL
	 */
	@Override
	public void run() {
		mBytes = Utils.getBytesFromUrl(mUrl);
	}

	/**
	 * Tell the GoodreadsWork about it.
	 */
	@Override
	public void finished() {
		mWork.handleTaskFinished(mBytes);
	}

}
