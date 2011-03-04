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

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;
import com.eleybourn.bookcatalogue.SearchThread.SearchTaskHandler;
import com.eleybourn.bookcatalogue.TaskManager.OnTaskEndedListener;

import android.os.Bundle;

/**
 * Class to co-ordinate multiple SearchThread objects using an existing TaskManager.
 * 
 * It uses the task manager it is passed and listens to OnTaskEndedListener messages;
 * it maintain its own internal list of tasks and as ones it knows about end, it
 * processes the data. Once all tasks are complete, it sends a message to its
 * creator via its SearchHandler.
 * 
 * @author Grunthos
 */
public class SearchManager implements OnTaskEndedListener {
	// TaskManager for threads; may have other threads tham the ones this object creates.
	TaskManager mTaskManager;
	// Accumulated book data
	private Bundle mBookData = null;
	// Flag indicating searches will be non-concurrent until an ISBN is found
	private boolean mWaitingForIsbn = false;
	// Flag indicating a task was cancelled.
	private boolean mCancelledFlg = false;
	// Flag to indicate search finished
	private boolean mFinished = false;
	// Original author for search
	private String mAuthor;
	// Original title for search
	private String mTitle;
	// Original ISBN for search
	private String mIsbn;
	// Whether of not to fetch thumbnails
	private boolean mFetchThumbnail;

	// Output from google search
	private Bundle mGoogleData;
	// Output from Amazon search
	private Bundle mAmazonData;
	// Output from LibraryThing search
	private Bundle mLibraryThingData;

	// Handler for search results
	private SearchResultHandler mSearchHandler = null;

	// List of threads created by *this* object.
	private ArrayList<ManagedTask> mTasks = new ArrayList<ManagedTask>();

	/**
	 * Task handler for thread management; caller MUST implement this to get
	 * search results.
	 * 
	 * @author Grunthos
	 */
	public interface SearchResultHandler extends ManagedTask.TaskHandler {
		void onSearchFinished(Bundle bookData, boolean cancelled);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param taskManager	TaskManager to use
	 * @param taskHandler	SearchHandler to send results
	 */
	SearchManager(TaskManager taskManager, SearchResultHandler taskHandler) {
		mTaskManager = taskManager;
		mSearchHandler = taskHandler;
	}

	/**
	 * Disconnect from handler so that underlying activity can rebuild.
	 */
	public void disconnect() {
		mSearchHandler = null;
	}

	/**
	 * Reconnect and (possibly)send the completion message.
	 * @param handler
	 */
	public void reconnect(SearchResultHandler handler) {
		mSearchHandler = handler;
		if (mFinished)
			mSearchHandler.onSearchFinished(mBookData, mCancelledFlg);
	}
	
	public TaskHandler getTaskHandler(ManagedTask t) {
		if (t instanceof SearchAmazonThread) {
			return mAmazonHandler;
		} else if (t instanceof SearchGoogleThread) {
			return mGoogleHandler;
		} else if (t instanceof SearchLibraryThingThread){
			return mLibraryThingHandler;
		} else {
			return null;
		}
	}

	/**
	 * When a task has ended, see if we are finished (no more tasks running).
	 * If so, finish.
	 */
	@Override
	public void onTaskEnded(TaskManager manager, ManagedTask task) {
		mTasks.remove(task);
		if (mTasks.size() == 0) {
			finish();
			mTaskManager.removeOnTaskEndedListener(this);
		}
	}

	/**
	 * Utility routine to start a task
	 * 
	 * @param thread	Task to start
	 */
	private void startOne(SearchThread thread) {
		mTasks.add(thread);
		thread.start();
	}

	/**
	 * Start an Amazon search
	 */
	private void startAmazon() {
		if (!mCancelledFlg)
			startOne( new SearchAmazonThread(mTaskManager, mAmazonHandler, mAuthor, mTitle, mIsbn, mFetchThumbnail) );		
	}
	/**
	 * Start a Google search
	 */
	private void startGoogle() {
		if (!mCancelledFlg)
			startOne( new SearchGoogleThread(mTaskManager, mGoogleHandler, mAuthor, mTitle, mIsbn, mFetchThumbnail) );		
	}
	/**
	 * Start an Amazon search
	 */
	private void startLibraryThing(){
		if (!mCancelledFlg)
			if (mIsbn != null && mIsbn.trim().length() > 0)
				startOne( new SearchLibraryThingThread(mTaskManager, mLibraryThingHandler, mAuthor, mTitle, mIsbn, mFetchThumbnail));		
	}

	/**
	 * Start a search
	 * 
	 * @param author	Author to search for
	 * @param title		Title to search for
	 * @param isbn		ISBN to search for
	 */
	public void search(String author, String title, String isbn, boolean fetchThumbnail) {
		// Save the input and initialize
		mBookData = new Bundle();
		mGoogleData = null;
		mAmazonData = null;
		mLibraryThingData = null;
		mWaitingForIsbn = false;
		mCancelledFlg = false;
		mFinished = false;

		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;
		mFetchThumbnail = fetchThumbnail;

		if (mTaskManager.runningInUiThread()) {
			doSearch();
		} else {
			mTaskManager.postToUiThread(new Runnable() {
				@Override
				public void run() {
					doSearch();
				}});
		}
	}
	private void doSearch() {
		// List for task ends
		mTaskManager.addOnTaskEndedListener(this);

		// We really want to ensure we get the same book from each, so if isbn is not present, do
		// these in series.
		if (mIsbn != null && mIsbn.length() > 0) {
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

	/**
	 * Utility routine to append text data from one Bundle to another
	 * 
	 * @param key		Key of data
	 * @param source	Source Bundle
	 * @param dest		Destination Bundle
	 */
	private void appendData(String key, Bundle source, Bundle dest) {
		String res = dest.getString(key) + "|" + source.getString(key);
		dest.putString(key, res);
	}

	/**
	 * Copy data from passed Bundle to current accumulated data. Does some careful
	 * processing of the data.
	 * 
	 * @param bookData	Source
	 */
	private void accumulateData(Bundle bookData) {
		if (bookData == null)
			return;
		for (String k : bookData.keySet()) {
			// If its not there, copy it.
			if (!mBookData.containsKey(k) || mBookData.getString(k) == null || mBookData.getString(k).trim().length() == 0) 
				mBookData.putString(k, bookData.getString(k));
			else {
				// Copy, append or update data as appropriate.
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

	/**
	 * Combine all the data and create a book or display an error.
	 */
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
			mBookData = null;
			if (mSearchHandler != null)
				mSearchHandler.onSearchFinished(mBookData, mCancelledFlg);

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
				Logger.logError(e);
			}
			if (mSearchHandler != null)
				mSearchHandler.onSearchFinished(mBookData, mCancelledFlg);
		}
		mFinished = true;
	}

	/**
	 * When running in single-stream mode, start the next thread that has no data.
	 * Google is reputedly most likely to succeed. Amazon is fastest, and LT REQUIRES and ISBN.
	 */
	private void startNext() {
		if (mGoogleData == null) {
			startGoogle();
		} else if (mAmazonData == null) {
			startAmazon();
		} else if (mLibraryThingData == null) {
			startLibraryThing();
		}
	}

	/**
	 * Handle google completion
	 */
	private SearchTaskHandler mGoogleHandler = new SearchTaskHandler() {
		@Override
		public void onSearchThreadFinish(SearchThread t, Bundle bookData, boolean cancelled) {
			mCancelledFlg = cancelled;
			mGoogleData = bookData;
			if (cancelled) {
				mWaitingForIsbn = false;
			} else {
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
		}
	};

	/**
	 * Handle Amazon completion
	 */
	private SearchTaskHandler mAmazonHandler = new SearchTaskHandler() {
		@Override
		public void onSearchThreadFinish(SearchThread t, Bundle bookData, boolean cancelled) {
			mCancelledFlg = cancelled;
			mAmazonData = bookData;
			if (cancelled) {
				mWaitingForIsbn = false;
			} else {
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
		}
	};

	/**
	 * Handle LibraryThing completion
	 */
	private SearchTaskHandler mLibraryThingHandler = new SearchTaskHandler() {
		@Override
		public void onSearchThreadFinish(SearchThread t, Bundle bookData, boolean cancelled) {
			mCancelledFlg = cancelled;
			mLibraryThingData = bookData;
			if (cancelled) {
				mWaitingForIsbn = false;
			} else {
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
		}
	};

}
