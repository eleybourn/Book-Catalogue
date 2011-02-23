/*
 * @copyright 2011 Philip Warner
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

 package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.Date;

import com.eleybourn.bookcatalogue.SearchThread.SearchHandler;
import com.eleybourn.bookcatalogue.TaskManager.OnTaskEndedListener;

import android.os.Bundle;
import android.util.Log;

public class SearchManager implements OnTaskEndedListener {
	TaskManager mTaskManager;
	private Bundle mBookData = new Bundle();
	private boolean mWaitingForIsbn = false;

	private String mAuthor;
	private String mTitle;
	private String mIsbn;

	private Bundle mGoogleData;
	private Bundle mAmazonData;
	private Bundle mLibraryThingData;

	private SearchHandler mSearchHandler = null;

	private ArrayList<ManagedTask> mTasks = new ArrayList<ManagedTask>();

	SearchManager(TaskManager taskManager, SearchHandler taskHandler) {
		mTaskManager = taskManager;
		mSearchHandler = taskHandler;
		mTaskManager.addOnTaskEndedListener(this);
	}

	@Override
	public void taskEnded(TaskManager manager, ManagedTask task) {
		mTasks.remove(task);
		if (mTasks.size() == 0) {
			finish();
			mTaskManager.removeOnTaskEndedListener(this);
		}
	}

	private void startOne(SearchThread thread) {
		mTasks.add(thread);
		thread.start();
	}

	private void startAmazon() {
		startOne( new SearchAmazonThread(mTaskManager, mAmazonHandler, mAuthor, mTitle, mIsbn) );		
	}
	private void startGoogle() {
		startOne( new SearchGoogleThread(mTaskManager, mGoogleHandler, mAuthor, mTitle, mIsbn) );		
	}
	private void startLibraryThing(){
		if (mIsbn != null && mIsbn.trim().length() > 0)
			startOne( new SearchLibraryThingThread(mTaskManager, mLibraryThingHandler, mAuthor, mTitle, mIsbn));		
	}

	public void search(String author, String title, String isbn) {
		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;

		// We really want to ensure we get the same book from each, so if isbn is not present, do
		// these in series.
		if (isbn != null && isbn.length() > 0) {
			mWaitingForIsbn = false;
			startLibraryThing();
			startGoogle();
			startAmazon();
		} else {
			// Run one at a time, startNext() defined the order.
			mWaitingForIsbn = true;
			startNext();
		}
	}

	private void appendData(String key, Bundle source, Bundle dest) {
		String res = dest.getString(key) + "|" + source.getString(key);
		dest.putString(key, res);
	}

	private void accumulateData(Bundle bookData) {
		Log.i("BC", "Appending data");
		if (bookData == null)
			return;
		for (String k : bookData.keySet()) {
			if (!mBookData.containsKey(k) || mBookData.getString(k) == null || mBookData.getString(k).trim().length() == 0) 
				mBookData.putString(k, bookData.getString(k));
			else {
				if (k.equals(CatalogueDBAdapter.KEY_AUTHOR_DETAILS)) {
					appendData(k, bookData, mBookData);
				} else if (k.equals(CatalogueDBAdapter.KEY_SERIES_DETAILS)) {
					appendData(k, bookData, mBookData);					
				} else if (k.equals(CatalogueDBAdapter.KEY_DATE_PUBLISHED)) {
					// Grab a different date if we can parse it.
					Date newDate = Utils.parseDate(bookData.getString(k));
					if (newDate != null) {
						String curr = mBookData.getString(k);
						if (Utils.parseDate(curr) == null) {
							mBookData.putString(k, Utils.toSqlDate(newDate));
						}
					}
				} else if (k.equals("__thumbnail")) {
					appendData(k, bookData, mBookData);					
				}
			}
		}
	}

	private void finish() {
		// Merge the data we have. We do this in a fixed order rather than as the threads finish.
		accumulateData(mGoogleData);
		accumulateData(mAmazonData);
		accumulateData(mLibraryThingData);
		
    	// If there are thumbnails present, pick the biggest, delete others and rename.
    	Utils.cleanupThumbnails(mBookData);

    	// If book is not found, just return to dialog.
    	String authors = null;
    	String title = null;
    	try {
    		authors = mBookData.getString(CatalogueDBAdapter.KEY_AUTHOR_DETAILS);
    	} catch (Exception e) {}
    	try {
    		title = mBookData.getString(CatalogueDBAdapter.KEY_TITLE);
    	} catch (Exception e) {}
		if (authors == null || authors.length() == 0 || title == null || title.length() == 0) {

			mTaskManager.doToast(mTaskManager.getString(R.string.book_not_found));
			mSearchHandler.onFinish(null, null);

		} else {
			Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_TITLE);
			Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_PUBLISHER);
			Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_DATE_PUBLISHED);
			Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_SERIES_NAME);
			
			// Decode the collected author names and convert to an ArrayList
			ArrayList<Author> aa = Utils.getAuthorUtils().decodeList(authors, '|', false);
			mBookData.putParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, aa);

			// Decode the collected series names and convert to an ArrayList
			try {
	    		String series = mBookData.getString(CatalogueDBAdapter.KEY_SERIES_DETAILS);
				ArrayList<Series> sa = Utils.getSeriesUtils().decodeList(series, '|', false);
				mBookData.putParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY, sa);
	    	} catch (Exception e) {
	    		Log.e("BC","Failed to add series", e);
	    	}
			mSearchHandler.onFinish(null, mBookData);
		}
	}

	private void startNext() {
		// Google is reputedly most likely to succeed. Amazon is fastest, and LT REQUIRES and ISBN.
		if (mGoogleData == null) {
			startGoogle();
		} else if (mAmazonData == null) {
			startAmazon();
		} else if (mLibraryThingData == null) {
			startLibraryThing();
		}
	}

	private SearchHandler mGoogleHandler = new SearchHandler() {
		@Override
		public void onFinish(SearchThread t, Bundle bookData) {
			mGoogleData = bookData;
			if (mWaitingForIsbn) {
				if (Utils.isNonBlankString(bookData, CatalogueDBAdapter.KEY_ISBN)) {
					mWaitingForIsbn = false;
					// Start the other two...even if they have run before
					mIsbn = bookData.getString(CatalogueDBAdapter.KEY_ISBN);
					startAmazon();
					startLibraryThing();
				} else {
					// Start next one that has not run. 
					startNext();
				}
			}
		}
	};

	private SearchHandler mAmazonHandler = new SearchHandler() {
		@Override
		public void onFinish(SearchThread t, Bundle bookData) {
			mAmazonData = bookData;
			if (mWaitingForIsbn) {
				if (Utils.isNonBlankString(bookData, CatalogueDBAdapter.KEY_ISBN)) {
					mWaitingForIsbn = false;
					// Start the other two...even if they have run before
					mIsbn = bookData.getString(CatalogueDBAdapter.KEY_ISBN);
					startGoogle();
					startLibraryThing();
				} else {
					// Start next one that has not run. 
					startNext();
				}
			}
		}
	};
	private SearchHandler mLibraryThingHandler = new SearchHandler() {
		@Override
		public void onFinish(SearchThread t, Bundle bookData) {
			mLibraryThingData = bookData;
			if (mWaitingForIsbn) {
				if (Utils.isNonBlankString(bookData, CatalogueDBAdapter.KEY_ISBN)) {
					mWaitingForIsbn = false;
					// Start the other two...even if they have run before
					mIsbn = bookData.getString(CatalogueDBAdapter.KEY_ISBN);
					startGoogle();
					startAmazon();
				} else {
					// Start next one that has not run. 
					startNext();
				}
			}
		}
	};

}
