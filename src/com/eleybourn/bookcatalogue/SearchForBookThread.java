package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.LinkedList;

import com.eleybourn.bookcatalogue.TaskWithProgress.TaskHandler;
import com.eleybourn.bookcatalogue.UpdateThumbnailsThread.BookInfo;
import com.eleybourn.bookcatalogue.Utils.ArrayUtils;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

public class SearchForBookThread extends TaskWithProgress {
	private String mAuthor;
	private String mTitle;
	private String mIsbn;

	private Bundle mBookData = new Bundle();

	public interface SearchHandler extends TaskWithProgress.TaskHandler {
		void onFinish(Bundle bookData);
	}

	SearchForBookThread(Context ctx, SearchHandler taskHandler, String author, String title, String isbn) {
		super(ctx, taskHandler);
		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;
	}

	@Override
	protected void onFinish() {
		if (getTaskHandler() != null) {
			((SearchHandler)getTaskHandler()).onFinish(mBookData);
		}
	}

	@Override
	protected void onMessage(Message msg) {
	}

	public String findSeries(String title) {
		String series = "";
		int last = title.lastIndexOf("(");
		int close = title.lastIndexOf(")");
		if (last > -1 && close > -1 && last < close) {
			series = title.substring((last+1), close);
		}
		return series;
	}

	@Override
	protected void onRun() {
		mBookData.putString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, mAuthor);
		mBookData.putString(CatalogueDBAdapter.KEY_TITLE, mTitle);
		mBookData.putString(CatalogueDBAdapter.KEY_ISBN, mIsbn);
		try {

			//
			//	Google
			//
			this.doProgress(getString(R.string.searching_google_books), 0);

			try {
				GoogleBooksManager.searchGoogle(mIsbn, mAuthor, mTitle, mBookData);					
			} catch (Exception e) {
				showException(R.string.searching_google_books, e);
			}

			// Save the series from title, if found
			if (mBookData.containsKey(CatalogueDBAdapter.KEY_TITLE)) {
				String tmpSeries = findSeries(mBookData.getString(CatalogueDBAdapter.KEY_TITLE));
				if (tmpSeries != null && tmpSeries.length() > 0)
					Utils.appendOrAdd(mBookData, CatalogueDBAdapter.KEY_SERIES_DETAILS, tmpSeries);
			}

			//
			//	Amazon
			//
			this.doProgress(getString(R.string.searching_amazon_books), 0);

			try {
				AmazonManager.searchAmazon(mIsbn, mAuthor, mTitle, mBookData);
			} catch (Exception e) {
				showException(R.string.searching_amazon_books, e);
			}

			//Look for series in Title. e.g. Red Phoenix (Dark Heavens Trilogy)
			String tmpSeries = findSeries(mBookData.getString(CatalogueDBAdapter.KEY_TITLE));
			if (tmpSeries != null && tmpSeries.length() > 0)
				Utils.appendOrAdd(mBookData, CatalogueDBAdapter.KEY_SERIES_DETAILS, tmpSeries);

			//
			//	LibraryThing
			//
			//	We always contact LibraryThing because it is a good source of Series data and thumbnails. But it 
			//	does require an ISBN AND a developer key.
			//
			if (mBookData.containsKey(CatalogueDBAdapter.KEY_ISBN)) {
				String isbn = mBookData.getString(CatalogueDBAdapter.KEY_ISBN);
				if (isbn.length() > 0) {
					this.doProgress(getString(R.string.searching_library_thing), 0);
					LibraryThingManager ltm = new LibraryThingManager(mBookData);
					try {
						ltm.searchByIsbn(isbn);											
					} catch (Exception e) {
						showException(R.string.searching_library_thing, e);
					}
				}
			}
			return;

		} catch (Exception e) {
			showException(R.string.search_fail, e);
			return;
		} finally {
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

				doToast(getString(R.string.book_not_found));
				mBookData = null;

			} else {
				doProperCase(mBookData, CatalogueDBAdapter.KEY_TITLE);
				doProperCase(mBookData, CatalogueDBAdapter.KEY_PUBLISHER);
				doProperCase(mBookData, CatalogueDBAdapter.KEY_DATE_PUBLISHED);
				doProperCase(mBookData, CatalogueDBAdapter.KEY_SERIES_NAME);
				ArrayList<Author> aa = Utils.getAuthorUtils().decodeList(authors, '|');
				mBookData.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, aa);
				try {
		    		String series = mBookData.getString(CatalogueDBAdapter.KEY_SERIES_DETAILS);
					ArrayList<Series> sa = Utils.getSeriesUtils().decodeList(series, '|');
					mBookData.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, sa);
		    	} catch (Exception e) {
		    		Log.e("BC","Failed to add series", e);
		    	}
			}
		}

	}

	private void doProperCase(Bundle values, String key) {
		if (!values.containsKey(key))
			return;
		values.putString(key, Utils.properCase(values.getString(key)));
	}

	private void showException(int id, Exception e) {
		String s;
		try {s = e.getMessage(); } catch (Exception e2) {s = "Unknown Exception";};
		String msg = String.format(getString(R.string.search_exception), getString(id), s);
		doToast(msg);		
	}

}
