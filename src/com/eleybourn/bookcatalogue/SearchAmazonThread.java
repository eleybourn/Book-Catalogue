package com.eleybourn.bookcatalogue;

public class SearchAmazonThread extends SearchThread {

	public SearchAmazonThread(TaskManager manager, TaskHandler taskHandler,
			String author, String title, String isbn, boolean fetchThumbnail) {
		super(manager, taskHandler, author, title, isbn, fetchThumbnail);
	}

	@Override
	protected void onRun() {
		//
		//	Amazon
		//
		this.doProgress(getString(R.string.searching_amazon_books), 0);

		try {
			AmazonManager.searchAmazon(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
			if (mBookData.size() == 0)
				throw new RuntimeException("No data found for " + mIsbn + "/" + mAuthor + "/" + mTitle);
			// Look for series name and clear KEY_TITLE
			checkForSeriesName();
		} catch (Exception e) {
			Logger.logError(e);
			showException(R.string.searching_amazon_books, e);
		}
	}

	/**
	 * Return the global ID for this searcher
	 */
	@Override
	public int getSearchId() {
		return SearchManager.SEARCH_AMAZON;
	}

}
