/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

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
 * ENHANCE: Parse the result and store it against the bookshelf in the database. 
 * 		 	Currently, this is not a simple thing to do because bookshelf naming rules in 
 * 		 	goodreads are much more restrictive: no spaces, punctuation (at least).
 * 
 * 			Need to add the following to bookshelf table:
 * 			- gr_bookshelf_id
 * 			- (perhaps) gr_bookshelf_name
 * 
 * @author Philip Warner
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
        mManager.execute(post, null, true);
	}

}
