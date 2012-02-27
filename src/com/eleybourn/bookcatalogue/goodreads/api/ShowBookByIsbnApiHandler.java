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

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.Utils;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.*;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.XmlHandler;

/**
 * Class to call the search.books api (using an ISBN).
 * 
 * @author Philip Warner
 */
public class ShowBookByIsbnApiHandler extends ShowBookApiHandler {

	
	public ShowBookByIsbnApiHandler(GoodreadsManager manager) {
		super(manager);
	}

	/**
	 * Perform a search and handle the results.
	 * 
	 * @param query
	 * @return	the array of GoodreadsWork objects.
	 * @throws IOException 
	 * @throws BookNotFoundException 
	 * @throws NotAuthorizedException 
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthMessageSignerException 
	 * @throws ClientProtocolException 
	 */
	public Bundle get(String isbn, boolean fetchThumbnail) throws ClientProtocolException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException {
		// Setup API call //
		final String urlBase = "http://www.goodreads.com/book/isbn?isbn=%1$s&key=%2$s";
		final String url = String.format(urlBase, isbn, mManager.getDeveloperKey());
		HttpGet get = new HttpGet(url);

		return sendRequest(get, fetchThumbnail);
	}

}
