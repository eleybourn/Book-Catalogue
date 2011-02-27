package com.eleybourn.bookcatalogue;

public class SearchAmazonThread extends SearchThread {

	public SearchAmazonThread(TaskManager manager, TaskHandler taskHandler,
			String author, String title, String isbn) {
		super(manager, taskHandler, author, title, isbn);
	}

	@Override
	protected void onRun() {
		//
		//	Amazon
		//
		this.doProgress(getString(R.string.searching_amazon_books), 0);

		try {
			AmazonManager.searchAmazon(mIsbn, mAuthor, mTitle, mBookData);
			// Look for series name and clear KEY_TITLE
			checkForSeriesName();
		} catch (Exception e) {
			BookCatalogue.logError(e);
			showException(R.string.searching_amazon_books, e);
		}
	}

}
