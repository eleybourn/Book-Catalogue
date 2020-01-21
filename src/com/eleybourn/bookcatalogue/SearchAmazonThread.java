package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.bcservices.BcSearchManager;
import com.eleybourn.bookcatalogue.utils.Logger;

public class SearchAmazonThread extends SearchThread {

	public SearchAmazonThread(TaskManager manager,
			String author, String title, String isbn, boolean fetchThumbnail) {
		super(manager, author, title, isbn, fetchThumbnail);
	}

	@Override
	protected void onRun() {
		//
		//	Amazon
		//
		this.doProgress(getString(R.string.searching_book_catalogue), 0);

		try {
			BcSearchManager.searchBcService(mIsbn, mAuthor, mTitle, mResults, mFetchThumbnail);
			//AmazonManager.searchAmazon(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
			if (mResults.size() == 0)
				throw new RuntimeException("No data found for " + mIsbn + "/" + mAuthor + "/" + mTitle);
			// Look for series name and clear KEY_TITLE
			checkForSeriesName();
		} catch (Exception e) {
			Logger.logError(e);
			showException(R.string.searching_book_catalogue, e);
		}
	}

	/**
	 * Return the global ID for this searcher
	 */
	@Override
	public int getSearchId() {
		return SearchManager.SEARCH_BC;
	}

}
