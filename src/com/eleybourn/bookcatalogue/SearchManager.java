package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.SearchThread.SearchHandler;
import com.eleybourn.bookcatalogue.TaskManager.OnTaskEndedListener;

import android.os.Bundle;
import android.util.Log;

public class SearchManager implements OnTaskEndedListener {
	TaskManager mTaskManager;
	private Bundle mBookData = new Bundle();
	private boolean mLtWaitingForIsbn = false;

	private String mAuthor;
	private String mTitle;
	private String mIsbn;

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

	public void search(String author, String title, String isbn) {
		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;

		if (isbn != null && isbn.length() > 0)
		{
			SearchLibraryThingThread lt = new SearchLibraryThingThread(mTaskManager, mLibraryThingHandler, author, title, isbn);
			mTasks.add(lt);
			lt.start();			
		} else {
			mLtWaitingForIsbn = true;
		}
		{
			SearchGoogleThread gt = new SearchGoogleThread(mTaskManager, mGoogleHandler, author, title, isbn);
			mTasks.add(gt);
			gt.start();			
		}
		{
			SearchAmazonThread at = new SearchAmazonThread(mTaskManager, mAmazonHandler, author, title, isbn);
			mTasks.add(at);
			at.start();
		}
	}

	private void appendData(String key, Bundle source, Bundle dest) {
		String res = dest.getString(key) + "|" + source.getString(key);
		dest.putString(key, res);
	}

	private void accumulateData(Bundle bookData) {
		Log.i("BC", "Appending data");
		for (String k : bookData.keySet()) {
			if (!mBookData.containsKey(k) || mBookData.getString(k) == null || mBookData.getString(k).trim().length() == 0) 
				mBookData.putString(k, bookData.getString(k));
			else {
				if (k.equals(CatalogueDBAdapter.KEY_AUTHOR_DETAILS)) {
					appendData(k, bookData, mBookData);
				} else if (k.equals(CatalogueDBAdapter.KEY_SERIES_DETAILS)) {
					appendData(k, bookData, mBookData);					
				} else if (k.equals("__thumbnail")) {
					appendData(k, bookData, mBookData);					
				}
			}
		}
	}

	private void finish() {
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

	private void doLibraryThingIfNecessary(Bundle bookData) {
		if (mLtWaitingForIsbn && bookData.containsKey(CatalogueDBAdapter.KEY_ISBN) && bookData.getString(CatalogueDBAdapter.KEY_ISBN).length() > 0) {
			String isbn = bookData.getString(CatalogueDBAdapter.KEY_ISBN);
			SearchLibraryThingThread lt = new SearchLibraryThingThread(mTaskManager, mLibraryThingHandler, mAuthor, mTitle, isbn);
			mTasks.add(lt);
			lt.start();	
			mLtWaitingForIsbn = false;
		}		
	}

	private SearchHandler mGoogleHandler = new SearchHandler() {
		@Override
		public void onFinish(SearchThread t, Bundle bookData) {
			doLibraryThingIfNecessary(bookData);
			accumulateData(bookData);
		}
	};
	private SearchHandler mAmazonHandler = new SearchHandler() {
		@Override
		public void onFinish(SearchThread t, Bundle bookData) {
			doLibraryThingIfNecessary(bookData);
			accumulateData(bookData);
		}
	};
	private SearchHandler mLibraryThingHandler = new SearchHandler() {
		@Override
		public void onFinish(SearchThread t, Bundle bookData) {
			accumulateData(bookData);
		}
	};

}
