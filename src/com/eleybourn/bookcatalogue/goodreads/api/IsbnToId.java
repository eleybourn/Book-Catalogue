package com.eleybourn.bookcatalogue.goodreads.api;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.*;

/**
 * API call to get a goodreads ID from an ISBN.
 * 
 * NOTE: THIS API DOES NOT RETURN XML. The text output is the ID.
 * 
 * @author Grunthos
 */
public class IsbnToId extends ApiHandler {

	public IsbnToId(GoodreadsManager manager) {
		super(manager);
	}

	/*
	 * Get the Goodreads book ID given an ISBN. Response contains the ID without any markup.
	 *	URL: http://www.goodreads.com/book/isbn_to_id    (sample url)
	 *	HTTP method: GET
	 *	Parameters:
	 *	    isbn: The ISBN of the book to lookup.
	 *	    key: Developer key (required).
	 */
	public long isbnToId(String isbn) 
			throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException,
					NotAuthorizedException, BookNotFoundException, NetworkException 
	{
		HttpGet get = new HttpGet("http://www.goodreads.com/book/isbn_to_id/" + isbn + "?key=" + mManager.getDeveloperKey());
		String s = mManager.executeRaw(get);        
        return Long.parseLong(s);
	}
	
}
