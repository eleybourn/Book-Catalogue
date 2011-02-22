package com.eleybourn.bookcatalogue;

import android.util.Log;

public class SearchGoogleThread extends SearchThread {

	public SearchGoogleThread(TaskManager manager, TaskHandler taskHandler,
			String author, String title, String isbn) {
		super(manager, taskHandler, author, title, isbn);
	}

	@Override
	protected void onRun() {
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

		} catch (Exception e) {
			showException(R.string.search_fail, e);
		}			
		Log.i("BC", "Google done");
	}

}
