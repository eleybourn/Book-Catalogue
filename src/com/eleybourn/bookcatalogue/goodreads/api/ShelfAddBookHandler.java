package com.eleybourn.bookcatalogue.goodreads.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.*;

/**
 * Class to add a book to a shelf. In this case, we do not care about the data returned.
 * 
 * TODO: Parse the result and store it against the bookshelf in the database. Currently, this
 * is not a simple thing to do because bookshelf naming rules in goodreads are much more 
 * restrictive: no spaces, punctuation (at least).
 * 
 * @author Grunthos
 *
 */
public class ShelfAddBookHandler extends ApiHandler {

	public ShelfAddBookHandler(GoodreadsManager manager) {
		super(manager);
	}

    /*
     * <shelf>
     *  <created-at type='datetime' nil='true'></created-at>
     *  <position type='integer' nil='true'></position>
     *  <review-id type='integer'>254171613</review-id>
     *  <updated-at type='datetime' nil='true'></updated-at>
     *  <user-shelf-id type='integer'>16480894</user-shelf-id>
     *  <name>read</name>
     * </shelf>
     */

	public void add(String shelfName, long grBookId) 
			throws ClientProtocolException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, 
			NotAuthorizedException, BookNotFoundException
	{
		HttpPost post = new HttpPost("http://www.goodreads.com/shelf/add_to_shelf.xml");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("book_id", Long.toString(grBookId)));
        parameters.add(new BasicNameValuePair("name", shelfName));
        
        post.setEntity(new UrlEncodedFormEntity(parameters));	        	

        // Send call. Errors will result in an exception.
        mManager.execute(post, null);
	}

}
