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

package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Background task to load an image for a GoodreadsWork from a URL. Does not store it locally; 
 * it will call the related Work when done.
 * 
 * @author Philip Warner
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
	public void run(SimpleTaskContext taskContext) {
		mBytes = Utils.getBytesFromUrl(mUrl);
	}

	/**
	 * Tell the GoodreadsWork about it.
	 */
	@Override
	public void onFinish() {
		mWork.handleTaskFinished(mBytes);
	}

	/**
	 * Always want finished() to be called.
	 */
	@Override
	public boolean requiresOnFinish() {
		return true;
	}

}
