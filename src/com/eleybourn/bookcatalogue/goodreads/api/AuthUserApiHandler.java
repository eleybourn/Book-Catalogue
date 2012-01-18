package com.eleybourn.bookcatalogue.goodreads.api;

import org.apache.http.client.methods.HttpPost;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.XmlHandler;

/**
 * API handler for the authUser call. Just gets the current user details.
 * 
 * @author Grunthos
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
		HttpPost post = new HttpPost("http://www.goodreads.com/api/auth_user");

		mUserId = 0;
		try {
	        // Get a handler and run query.
	        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
	        mManager.execute(post, handler);			
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
