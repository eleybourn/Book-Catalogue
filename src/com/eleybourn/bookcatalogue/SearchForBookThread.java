package com.eleybourn.bookcatalogue;

import java.util.LinkedList;

import com.eleybourn.bookcatalogue.TaskWithProgress.TaskHandler;
import com.eleybourn.bookcatalogue.UpdateThumbnailsThread.BookInfo;

import android.content.ContentValues;
import android.content.Context;
import android.os.Message;

public class SearchForBookThread extends TaskWithProgress {
	private String mAuthor;
	private String mTitle;
	private String mIsbn;

	private ContentValues mBookData = new ContentValues();

	public interface SearchHandler extends TaskWithProgress.TaskHandler {
		void onFinish(ContentValues bookData);
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
		mBookData.put(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, mAuthor);
		mBookData.put(CatalogueDBAdapter.KEY_TITLE, mTitle);
		mBookData.put(CatalogueDBAdapter.KEY_ISBN, mIsbn);
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

			// Copy the series for later
			if (mBookData.containsKey(CatalogueDBAdapter.KEY_TITLE)) {
				mBookData.put(CatalogueDBAdapter.KEY_SERIES_NAME, findSeries(mBookData.getAsString(CatalogueDBAdapter.KEY_TITLE)));
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
			String tmpSeries = findSeries(mBookData.getAsString(CatalogueDBAdapter.KEY_TITLE));

			if (tmpSeries != null && tmpSeries.length() > 0) {
				if (!mBookData.containsKey(CatalogueDBAdapter.KEY_SERIES_NAME) 
						|| mBookData.getAsString(CatalogueDBAdapter.KEY_SERIES_NAME).length() < tmpSeries.length() ) {
					mBookData.put(CatalogueDBAdapter.KEY_SERIES_NAME, tmpSeries);
				}
			}

			//
			//	LibraryThing
			//
			//	We always contact LibraryThing because it is a good source of Series data and thumbnails. But it 
			//	does require an ISBN AND a developer key.
			//
			if (mBookData.containsKey(CatalogueDBAdapter.KEY_ISBN)) {
				String isbn = mBookData.getAsString(CatalogueDBAdapter.KEY_ISBN);
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
	    	String author = "";
	    	String title = "";
	    	try {
	    		author = mBookData.getAsString(CatalogueDBAdapter.KEY_AUTHOR_DETAILS);
	    	} catch (Exception e) {}
	    	try {
	    		title = mBookData.getAsString(CatalogueDBAdapter.KEY_TITLE);
	    	} catch (Exception e) {}
			if (author.length() == 0 || title.length() == 0) {

				doToast(getString(R.string.book_not_found));
				mBookData = null;

			} else {
				doProperCase(mBookData, CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
				doProperCase(mBookData, CatalogueDBAdapter.KEY_TITLE);
				doProperCase(mBookData, CatalogueDBAdapter.KEY_PUBLISHER);
				doProperCase(mBookData, CatalogueDBAdapter.KEY_DATE_PUBLISHED);
				doProperCase(mBookData, CatalogueDBAdapter.KEY_SERIES_NAME);
			}
		}

	}

	private void doProperCase(ContentValues values, String key) {
		if (!values.containsKey(key))
			return;
		values.put(key, Utils.properCase(values.getAsString(key)));
	}

	private void showException(int id, Exception e) {
		String s;
		try {s = e.getMessage(); } catch (Exception e2) {s = "Unknown Exception";};
		String msg = String.format(getString(R.string.search_exception), getString(id), s);
		doToast(msg);		
	}

}
