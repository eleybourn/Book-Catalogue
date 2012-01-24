package com.eleybourn.bookcatalogue.goodreads;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.Logger;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.*;
import com.eleybourn.bookcatalogue.goodreads.api.AuthUserApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.IsbnToId;
import com.eleybourn.bookcatalogue.goodreads.api.ShelfAddBookHandler;

/**
 * Class to wrap all GoodReads API calls and manage an API connection.
 * 
 * @author Grunthos
 */
public class GoodreadsManager {

	/** Enum to handle possible results of sending a book to goodreads */
	public static enum ExportDisposition { error, sent, noIsbn, notFound, networkError };
	

	/**
	 * Exceptions that may be thrown and used to wrap more varied inner exceptions
	 */
	public static class Exceptions {
		public static class GeneralException extends Exception {
			private static final long serialVersionUID = 5762518476144652354L;
			Throwable m_inner;
			public GeneralException(Throwable inner) { m_inner = inner; };
		}
		public static class NotAuthorizedException extends GeneralException {
			private static final long serialVersionUID = 5589234170614368111L;
			public NotAuthorizedException(Throwable inner) { super(inner); }
		};
		public static class BookNotFoundException extends GeneralException {
			private static final long serialVersionUID = 872113355903361212L;
			public BookNotFoundException(Throwable inner) { super(inner); }
		};
		public static class NetworkException extends GeneralException {
			private static final long serialVersionUID = -4233137984910957925L;
			public NetworkException(Throwable inner) { super(inner); }
		};		
	}

	// Set to true when the credentials have been successfully verified.
	protected static boolean m_hasValidCredentials = false;
	// Cached when credentials have been verified.
	protected static String m_accessToken = null;
	protected static String m_accessSecret = null;
	// Local copies of user data retrieved when the credentials were verified
	protected static String m_username = null;
	protected static long m_userid = 0;
	// Stores the last time an API request was made to avoid breaking API rules.
	private static Long m_LastRequestTime = 0L;

	private final static String DEV_KEY = GoodreadsApiKeys.GOODREADS_DEV_KEY;
	private final static String DEV_SECRET = GoodreadsApiKeys.GOODREADS_DEV_SECRET;

	// OAuth helpers
	CommonsHttpOAuthConsumer m_consumer;
	OAuthProvider m_provider;

	/**
	 * Standard constructor; call common code.
	 * 
	 * @author Grunthos
	 */
	public GoodreadsManager() {
		sharedInit();
	}

	/**
	 * Common constructor code.
	 * 
	 * @author Grunthos
	 */
	private void sharedInit() {

		m_consumer = new CommonsHttpOAuthConsumer(DEV_KEY, DEV_SECRET);
		m_provider = new CommonsHttpOAuthProvider(
				"http://www.goodreads.com/oauth/request_token",
				"http://www.goodreads.com/oauth/access_token",
				"http://www.goodreads.com/oauth/authorize");

	}
	
	/**
	 * Return the public developer key, used for GET queries.
	 * 
	 * @author Grunthos
	 */
	public String getDeveloperKey() {
		return DEV_KEY;
	}

	/**
	 * Check if the current credentials (either cached or in prefs) are valid. If they
	 * have been previously checked and were valid, just use that result.
	 * 
	 * @author Grunthos
	 */
	public boolean hasValidCredentials() {
		// If credentials have already been accepted, don't re-check.
		if (m_hasValidCredentials)
			return true;

		return validateCredentials();
	}

	/**
	 * Check if the current credentials (either cached or in prefs) are valid, and
	 * cache the result.
	 * 
	 * If cached credentials were used, call recursively after clearing the cached
	 * values.
	 * 
	 * @author Grunthos
	 */
	private boolean validateCredentials() {
		// Get the stored token values from prefs, and setup the consumer
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

		m_accessToken = prefs.getString("GoodReads.AccessToken.Token", "");
		m_accessSecret = prefs.getString("GoodReads.AccessToken.Secret", "");

		m_consumer.setTokenWithSecret(m_accessToken, m_accessSecret);
		
		try {
			AuthUserApiHandler authUserApi = new AuthUserApiHandler(this);
			
			if (authUserApi.getAuthUser() == 0)
				return false;

            // Save result...
            m_username = authUserApi.getUsername();
            m_userid = authUserApi.getUserid();

		} catch (Exception e) {
			// Something went wrong. Clear the access token, mark credentials as bad, and if we used
			// cached values, retry by getting them from prefs.
			m_hasValidCredentials = false;
			m_accessToken = null;
			return false;
		}

		// Cache the result to avoid web checks later
		m_hasValidCredentials = true;

		return true;
	}

	/**
	 * Request authorization from the current user by going to the OAuth web page.
	 * 
	 * @author Grunthos
	 * @throws NetworkException 
	 */
	public void requestAuthorization(Activity ctx) throws NetworkException {
        String authUrl;

        // Dont do this; this is just part of OAuth and not the API
		//waitUntilRequestAllowed();

        // Get the URL
        try {
        	authUrl = m_provider.retrieveRequestToken(m_consumer,  "com.eleybourn.bookcatalogue://goodreadsauth"); 
        	//authUrl = m_provider.retrieveRequestToken(m_consumer,  "intent:#Intent;action=android.intent.action.VIEW;category=android.intent.category.DEFAULT;component=com.eleybourn.bookcatalogue/.goodreads.GoodReadsAuthorizationActivity;end"); 
        } catch (OAuthCommunicationException e) {
        	throw new NetworkException(e);
        } catch (Exception e) {
        	throw new RuntimeException(e);
        }

		// Make a valid URL for the parser (some come back without a schema)
		if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://"))
			authUrl = "http://" + authUrl;

		// Save the token; this object may well be destroyed before the web page has returned.
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		SharedPreferences.Editor ed = prefs.edit();
		ed.putString("GoodReads.RequestToken.Token", m_consumer.getToken());
		ed.putString("GoodReads.RequestToken.Secret", m_consumer.getTokenSecret());
		ed.commit();

		// Open the web page
		android.content.Intent browserIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl));
		ctx.startActivity(browserIntent);
	}

	/**
	 * Called by the callback activity, GoodReadsAuthorizationActivity, when a request has been 
	 * authorized by the user.
	 *
	 * @author Grunthos
	 * @throws NotAuthorizedException 
	 */
	public void handleAuthentication() throws NotAuthorizedException {
		// Get the saved request tokens.
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		String tokenString = prefs.getString("GoodReads.RequestToken.Token", "");
		String secretString = prefs.getString("GoodReads.RequestToken.Secret", "");

		if (tokenString.equals("") || secretString.equals(""))
			throw new RuntimeException("Expected a request token to be stored in preferences; none found");

		// Update the consumer.
		m_consumer.setTokenWithSecret(tokenString, secretString);

		// Get the access token
		waitUntilRequestAllowed();
		try {
	    	m_provider.retrieveAccessToken(m_consumer, null ); //m_consumer.getToken());			
        } catch (oauth.signpost.exception.OAuthNotAuthorizedException e) {
        	throw new NotAuthorizedException(e);
	    } catch (Exception e) {
	    	throw new RuntimeException(e);
	    }

		// Cache and save the token
		m_accessToken = m_consumer.getToken();
		m_accessSecret = m_consumer.getTokenSecret();

		SharedPreferences.Editor ed = prefs.edit();
		ed.putString("GoodReads.AccessToken.Token", m_accessToken);
		ed.putString("GoodReads.AccessToken.Secret", m_accessSecret);
		ed.commit();
	}

	/**
	 * Utility routine called to sign a request and submit it then pass it off to a parser.
	 *
	 * @author Grunthos
	 * @throws NotAuthorizedException 
	 * @throws BookNotFoundException 
	 */
	public HttpResponse execute(HttpUriRequest request, DefaultHandler requestHandler) throws ClientProtocolException, IOException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException {

		// Get a new client
		HttpClient httpClient = new DefaultHttpClient();

		// Sign the request and wait until we can submit it legally.
		m_consumer.setTokenWithSecret(m_accessToken, m_accessSecret);
		m_consumer.sign(request);
    	waitUntilRequestAllowed();

    	// Submit the request and process result.
    	HttpResponse response = httpClient.execute(request);

    	int code = response.getStatusLine().getStatusCode();
    	if (code == 200 || code == 201)
    		parseResponse(response, requestHandler);
    	else if (code == 401) {
    		m_hasValidCredentials = false;
    		throw new NotAuthorizedException(null);
    	} else if (code == 404) {
    		throw new BookNotFoundException(null);
    	} else
    		throw new RuntimeException("Unexpected status code from API: " + response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());

    	return response;
	}

	/**
	 * Utility routine called to sign a request and submit it then return the raw text output.
	 *
	 * @author Grunthos
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthMessageSignerException 
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 * @throws NotAuthorizedException 
	 * @throws BookNotFoundException 
	 * @throws NetworkException 
	 */
	public String executeRaw(HttpUriRequest request) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, ClientProtocolException, IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

		// Get a new client
		HttpClient httpClient = new DefaultHttpClient();

		// Sign the request and wait until we can submit it legally.
		m_consumer.setTokenWithSecret(m_accessToken, m_accessSecret);
		m_consumer.sign(request);
    	waitUntilRequestAllowed();

    	// Submit the request then process result.
    	HttpResponse response = null;
    	try {
    		response = httpClient.execute(request);
    	} catch (Exception e) {
    		throw new NetworkException(e);
    	}

    	int code = response.getStatusLine().getStatusCode();
        StringBuilder html = new StringBuilder();
        HttpEntity e = response.getEntity();
        if (e != null) {
            InputStream in = e.getContent();
            if (in != null) {
                while (true) {
                	int i = in.read();
                	if (i == -1) break;
                	html.append((char)(i));
                }        	            	
            }
        }

        if (code == 200 || code == 201) {
            return html.toString();
    	} else if (code == 401) {
    		m_hasValidCredentials = false;
    		throw new NotAuthorizedException(null);
    	} else if (code == 404) {
    		throw new BookNotFoundException(null);
    	} else {
    		throw new RuntimeException("Unexpected status code from API: " + response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());
    	}
	}

	/**
	 * Utility routine called to pass a response off to a parser.
	 *
	 * @author Grunthos
	 */
	private boolean parseResponse(HttpResponse response, DefaultHandler requestHandler) throws IllegalStateException, IOException {
		boolean parseOk = false;

		// Setup the parser
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;

		InputStream in = response.getEntity().getContent();

		// Dont bother catching general exceptions, they will be caught by the caller.
		try {
			parser = factory.newSAXParser();
			// Make sure we follow LibraryThing ToS (no more than 1 request/second).
			parser.parse(in, requestHandler);
			parseOk = true;
		} catch (MalformedURLException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (ParserConfigurationException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (SAXException e) {
			String s = e.getMessage(); // "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (java.io.IOException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		}		
		return parseOk;
	}

	/**
	 * Use mLastRequestTime to determine how long until the next request is allowed; and
	 * update mLastRequestTime this needs to be synchroized across threads.
	 *
 	 * Note that as a result of this approach mLastRequestTime may in fact be
	 * in the future; callers to this routine effectively allocate time slots.
	 * 
	 * This method will sleep() until it can make a request; if ten threads call this 
	 * simultaneously, one will return immediately, one will return 1 second later, another
	 * two seconds etc.
	 * 
	 */
	private static void waitUntilRequestAllowed() {
		long now = System.currentTimeMillis();
		long wait;
		synchronized(m_LastRequestTime) {
			wait = 1000 - (now - m_LastRequestTime);
			//
			// mLastRequestTime must be updated while synchronized. As soon as this
			// block is left, another block may perform another update.
			//
			if (wait < 0)
				wait = 0;
			m_LastRequestTime = now + wait;
		}
		if (wait > 0) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
			}
		}
	}

	public String getUsername() {
		if (!m_hasValidCredentials)
			throw new RuntimeException("GoodReads credentials need to be validated before accessing user data");

		return m_username;
	}

	public long getUserid() {
		if (!m_hasValidCredentials)
			throw new RuntimeException("GoodReads credentials need to be validated before accessing user data");

		return m_userid;
	}

	/** Local API object */
	private IsbnToId m_isbnToId = null;
	/**
	 * Wrapper to call ISBN->ID API
	 */
	public long isbnToId(String isbn) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
		if (m_isbnToId == null)
			m_isbnToId = new IsbnToId(this);
		return m_isbnToId.isbnToId(isbn);
	}

	/** Local API object */
	ShelfAddBookHandler m_addBookHandler = null;
	/**
	 * Wrapper to call API to add book to shelf
	 */
	public void addBookToShelf(String shelfName,long grBookId) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
		if (m_addBookHandler == null)
			m_addBookHandler = new ShelfAddBookHandler(this);
		m_addBookHandler.add(shelfName, grBookId);
	}

	/**
	 * Wrapper to send an entire book, including shelves, to Goodreads.
	 * 
	 * @param dbHelper	DB connection
	 * @param books		Cursor poiting to single book to send
	 *
	 * @return			Disposition of book
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws NotAuthorizedException 
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthMessageSignerException 
	 */
	public ExportDisposition sendOneBook(CatalogueDBAdapter dbHelper, BooksRowView books) throws InterruptedException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, IOException {
		long bookId = books.getId();
		String isbn = books.getIsbn();

		if (!isbn.equals("")) {
			long grId;
			try {
				grId = this.isbnToId(isbn);
				if (grId == 0) {
					Thread.sleep(1000);
					grId = this.isbnToId(isbn);							
				}
			} catch (BookNotFoundException e) {
				return ExportDisposition.notFound;
			} catch (NetworkException e) {
				return ExportDisposition.networkError;
			}

			Cursor shelves = dbHelper.getAllBookBookshelvesForGoodreadsCursor(bookId);
			try {
				int	shelfCol = shelves.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF);

				while (shelves.moveToNext()) {
					String shelfName = shelves.getString(shelfCol);
					try {
						this.addBookToShelf(shelfName, grId);								
					} catch (Exception e) {
						return ExportDisposition.error;
					}
				}				
			} finally {
				shelves.close();
			}
			return ExportDisposition.sent;
		} else {
			return ExportDisposition.noIsbn;
		}
		
	}

}



