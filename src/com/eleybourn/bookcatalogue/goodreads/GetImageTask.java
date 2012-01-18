package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Utils;

import net.philipwarner.taskqueue.QueueManager;
import android.content.Context;

/**
 * Background task to load an image from a URL. Does not store it locally; it should
 * be a fast task and the caller can listen for a result.
 * 
 * @author Grunthos
 */
public class GetImageTask extends GenericTask {
	private static final long serialVersionUID = 5508460375399613510L;
	//private static Integer mIdCounter = 0;
	//private int mId = 0;

	private String mUrl = "";
	private byte[] mBytes = null;

	/**
	 * Constructor.
	 * 
	 * @param url		URL to retrieve.
	 */
	public GetImageTask(String url) {
		super(BookCatalogueApp.getResourceString(R.string.get_image_from, url));
		mUrl = url;
		//mId = ++mIdCounter;
	}

	/**
	 * Return the resulting image byte[]
	 * @return
	 */
	public byte[] getBytes() {
		return mBytes;
	}

	/**
	 * Run the task.
	 */
	@Override
	public boolean run(QueueManager manager, Context c) {
		mBytes = Utils.getBytesFromUrl(mUrl);
		//System.out.println("GetImage(" + mId + "): " + mUrl);
		return true;
	}

}
