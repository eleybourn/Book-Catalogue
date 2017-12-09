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
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;

import static com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.GOODREADS_API_ROOT;

/**
 * TODO: ReviewUpdateHandler WORK IN PROGRESS
 * 
 * @author Philip Warner
 */
public class ReviewUpdateHandler extends ApiHandler {

	public ReviewUpdateHandler(GoodreadsManager manager) {
		super(manager);
	}

	public void update(long reviewId, boolean isRead, String readAt, String review, int rating) 
			throws ClientProtocolException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, 
					NotAuthorizedException, BookNotFoundException, NetworkException
	{
		HttpPost post = new HttpPost(GOODREADS_API_ROOT + "/review/" + reviewId + ".xml");

		//StringBuilder shelvesString = null;
		//if (shelves != null && shelves.size() > 0) {
		//	shelvesString = new StringBuilder();
		//    if (shelves.size() > 0) {
		//        shelvesString.append(shelves.get(0));
		//    }
		//    for (int i = 1; i < shelves.size(); i++) {
		//        shelvesString.append("," + shelves.get(i));
		//    }
		//}

		// Set the 'read' or 'to-read' shelf based on status.
		// Note a lot of point...it does not update goodreads!
        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        if (isRead)
        	parameters.add(new BasicNameValuePair("shelf", "read"));
        else
	    	parameters.add(new BasicNameValuePair("shelf", "to-read"));
        //if (shelvesString != null)
	    //    parameters.add(new BasicNameValuePair("shelf", shelvesString.toString()));
        
        if (review != null)
	        parameters.add(new BasicNameValuePair("review[review]", review));

        if (readAt != null && !readAt.equals(""))
	        parameters.add(new BasicNameValuePair("review[read_at]", readAt));
        
        if (rating >= 0)
	        parameters.add(new BasicNameValuePair("review[rating]", Integer.toString(rating)));

        post.setEntity(new UrlEncodedFormEntity(parameters, "UTF8"));	        	

        //ReviewUpdateParser handler = new ReviewUpdateParser();
        mManager.execute(post, null, true);
        //String s = handler.getHtml();
        //System.out.print(s);
        /* Typical response can be ignored, but is:
           <review>
			  <book-id type='integer'>375802</book-id>
			  <comments-count type='integer'>0</comments-count>
			  <created-at type='datetime'>2011-03-15T01:51:42-07:00</created-at>
			  <hidden-flag type='boolean'>false</hidden-flag>
			  <id type='integer'>154477749</id>
			  <language-code type='integer' nil='true'></language-code>
			  <last-comment-at type='datetime' nil='true'></last-comment-at>
			  <last-revision-at type='datetime'>2012-01-01T05:43:30-08:00</last-revision-at>
			  <non-friends-rating-count type='integer'>0</non-friends-rating-count>
			  <notes></notes>
			  <rating type='integer'>4</rating>
			  <ratings-count type='integer'>0</ratings-count>
			  <ratings-sum type='integer'>0</ratings-sum>
			  <read-at type='datetime'>1991-05-01T00:00:00-07:00</read-at>
			  <read-count></read-count>
			  <read-status>read</read-status>
			  <recommendation></recommendation>
			  <recommender-user-id1 type='integer'>0</recommender-user-id1>
			  <recommender-user-name1></recommender-user-name1>
			  <review></review>
			  <sell-flag type='boolean'>true</sell-flag>
			  <spoiler-flag type='boolean'>false</spoiler-flag>
			  <started-at type='datetime' nil='true'></started-at>
			  <updated-at type='datetime'>2012-01-01T05:43:30-08:00</updated-at>
			  <user-id type='integer'>5129458</user-id>
			  <weight type='integer'>0</weight>
			  <work-id type='integer'>2422333</work-id>
			</review>
         */
	}

}
