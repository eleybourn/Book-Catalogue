package com.eleybourn.bookcatalogue.goodreads.api;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;

/**
 * Base class for all goodreads API handler classes.
 * 
 * The job of an API handler is to implement a method to run the API (eg. 'search' in 
 * SearchBooksApiHandler) and to process the output.
 * 
 * @author Grunthos
 *
 */
public abstract class ApiHandler {
	GoodreadsManager mManager;

	/** XmlFilter root object. Used in extracting data file XML results. */
	protected XmlFilter mRootFilter = new XmlFilter("");

	public ApiHandler(GoodreadsManager manager) {
		mManager = manager;
	}

}
