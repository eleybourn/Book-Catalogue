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

import org.apache.http.client.methods.HttpPost;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.XmlHandler;

import static com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.GOODREADS_API_ROOT;

/**
 * API handler for the authUser call. Just gets the current user details.
 * 
 * @author Philip Warner
 */
public class AuthUserApiHandler extends ApiHandler {

	/**
	 * Constructor. Setup the filters.
	 * 
	 * @param manager
	 */
	public AuthUserApiHandler(GoodreadsManager manager) {
		super(manager);
		buildFilters();
	}

	/**
	 * Call the API.
	 * 
	 * @return		Resulting User ID, 0 if error/none.
	 */
	public long getAuthUser() {
		// Setup API call
		HttpPost post = new HttpPost(GOODREADS_API_ROOT + "/api/auth_user");

		mUserId = 0;
		try {
	        // Get a handler and run query.
	        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
	        mManager.execute(post, handler, true);			
	        // Return user found.
	        return mUserId;
		} catch (Exception e) {
			return 0;
		}
	}
	/*
	 * Typical response:
	 * 
	 *	<GoodreadsResponse>
	 *	  <Request>
	 *	    <authentication>true</authentication>
	 *	      <key><![CDATA[KEY]]></key>
	 *	    <method><![CDATA[api_auth_user]]></method>
	 *	  </Request>
	 *	  <user id="5129458">
	 *		<name><![CDATA[Grunthos]]></name>
	 *		<link><![CDATA[http://www.goodreads.com/user/show/5129458-grunthos?utm_medium=api]]></link>
	 *	  </user>
	 *	</GoodreadsResponse>
	 */
	private void buildFilters() {
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "user").setStartAction(mHandleUserStart);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "user", "name").setEndAction(mHandleUsernameEnd);		
	}

	public String getUsername() {
		return mUsername;
	}

	public long getUserid() {
		return mUserId;
	}

	private long mUserId = 0;
	private String mUsername = null;

	private XmlHandler mHandleUserStart = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			mUserId = Long.parseLong(context.attributes.getValue("", "id"));
		}
	};
	private XmlHandler mHandleUsernameEnd = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			mUsername = context.body;
		}
	};
	
}
