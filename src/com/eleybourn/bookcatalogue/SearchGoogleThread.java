package com.eleybourn.bookcatalogue;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.utils.Logger;

public class SearchGoogleThread extends SearchThread {

	public SearchGoogleThread(TaskManager manager,
			String author, String title, String isbn, boolean fetchThumbnail) {
		super(manager, author, title, isbn, fetchThumbnail);
	}

	@Override
	protected void onRun() {
		try {
			//
			//	Google
			//
			doProgress(getString(R.string.searching_google_books), 0);

			BookSearchResults result = new BookSearchResults(DataSource.Google, new Bundle());
			mResults.add(result);
			try {
				GoogleBooksManager.searchGoogle(mIsbn, mAuthor, mTitle, result.data, mFetchThumbnail);
			} catch (Exception e) {
				Logger.logError(e);
				showException(R.string.searching_google_books, e);
			}

			// Look for series name and clear KEY_TITLE
			checkForSeriesName();
		} catch (Exception e) {
			Logger.logError(e);
			showException(R.string.search_fail, e);
		}
	}

	/**
	 * Return the global ID for this searcher
	 */
	@Override
	public DataSource getSearchId() {
		return DataSource.Google;
	}

}
