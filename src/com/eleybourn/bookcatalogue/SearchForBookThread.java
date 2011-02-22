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

/**
 * Class to handle all book searches in a separate thread.
 * TODO: Consider putting each search in its own thread to improve speed.
 *
 * @author Grunthos
 *
 */
public class SearchForBookThread extends TaskWithProgress {
	private String mAuthor;
	private String mTitle;
	private String mIsbn;

	private String mSavedTitle = null;

	// Accumulated book info.
	private Bundle mBookData = new Bundle();

	/**
	 * Task handler for thread management; caller MUST implement this to get
	 * search results.
	 * 
	 * @author Grunthos
	 */
	public interface SearchHandler extends TaskWithProgress.TaskHandler {
		void onFinish(SearchForBookThread t, Bundle bookData);
	}

	/**
	 * Constructor. Will search according to passed parameters. If an ISBN
	 * is provided that will be used to the exclusion of all others.
	 * 
	 * @param ctx			Context
	 * @param taskHandler	TaskHandler implementation
	 * @param author		Author to search for
	 * @param title			Title to search for
	 * @param isbn			ISBN to search for.
	 */
	SearchForBookThread(TaskManager manager, SearchHandler taskHandler, String author, String title, String isbn) {
		super(manager, taskHandler);
		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;
	}

	@Override
	protected boolean onFinish() {
		if (getTaskHandler() != null) {
			((SearchHandler)getTaskHandler()).onFinish(this, mBookData);
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onMessage(Message msg) {
	}

	/**
	 * Try to extract a series from a book title.
	 * TODO: Consider removing findSeries if LibraryThing proves reliable.
	 * 
	 * @param 	title	Book title to parse
	 * @return
	 */
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
			doProgress(getString(R.string.searching_google_books), 0);

			try {
				GoogleBooksManager.searchGoogle(mIsbn, mAuthor, mTitle, mBookData);					
			} catch (Exception e) {
				showException(R.string.searching_google_books, e);
			}

			// Look for series name and clear KEY_TITLE
			checkForSeriesName();

			//
			//	Amazon
			//
			this.doProgress(getString(R.string.searching_amazon_books), 0);

			try {
				AmazonManager.searchAmazon(mIsbn, mAuthor, mTitle, mBookData);
			} catch (Exception e) {
				showException(R.string.searching_amazon_books, e);
			}

			// Look for series name and clear KEY_TITLE
			checkForSeriesName();

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
						// Look for series name and clear KEY_TITLE
						checkForSeriesName();
					} catch (Exception e) {
						showException(R.string.searching_library_thing, e);
					}
				}
			}
			
			if (mSavedTitle != null)
				mBookData.putString(CatalogueDBAdapter.KEY_TITLE, mSavedTitle);

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
			}
		}

	}

	/**
	 * Look in the data for a title, if present try to get a series name from it.
	 * In any case, clear the title (and save if none saved already) so that the 
	 * next lookup will overwrite with a possibly new title.
	 */
	private void checkForSeriesName() {
		try {
			if (mBookData.containsKey(CatalogueDBAdapter.KEY_TITLE)) {
				String thisTitle = mBookData.getString(CatalogueDBAdapter.KEY_TITLE);
				if (mSavedTitle == null)
					mSavedTitle = thisTitle;
				String tmpSeries = findSeries(thisTitle);
				if (tmpSeries != null && tmpSeries.length() > 0)
					Utils.appendOrAdd(mBookData, CatalogueDBAdapter.KEY_SERIES_DETAILS, tmpSeries);				
				// Delete the title so that Amazon will get it too
				mBookData.remove(CatalogueDBAdapter.KEY_TITLE);
			}							
		} catch (Exception e) {};		
	}
	
	/**
	 * Convert text at specified key to proper case.
	 * 
	 * @param values
	 * @param key
	 */
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
