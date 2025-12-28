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

package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.goodreads.api.AuthUserApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.BookshelfListApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.BookshelfListApiHandler.BookshelfListFieldNames;
import com.eleybourn.bookcatalogue.goodreads.api.ReviewUpdateHandler;
import com.eleybourn.bookcatalogue.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShelfAddBookHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookApiHandler.ShowBookFieldNames;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookByIdApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookByIsbnApiHandler;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Objects;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 * Class to wrap all GoodReads API calls and manage an API connection.
 * ENHANCE: Add 'send to goodreads'/'update from internet' option in book edit menu
 * ENHANCE: Change 'update from internet' to allow source selection and single-book execution
 * ENHANCE: Link an Event to a book, and display in book list with exclamation triangle overwriting cover.
 * ENHANCE: MAYBE Replace Events with something similar in local DB?
 *
 * @author Philip Warner
 */
public class GoodreadsManager {

    /**
     * Root URL for API calls
     */
    public static final String GOODREADS_API_ROOT = "https://www.goodreads.com";
    private static final String LAST_SYNC_DATE = "GoodreadsManager.LastSyncDate";
    private final static String DEV_KEY = GoodreadsApiKeys.GOODREADS_DEV_KEY;
    private final static String DEV_SECRET = GoodreadsApiKeys.GOODREADS_DEV_SECRET;
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
    // OAuth helpers
    CommonsHttpOAuthConsumer m_consumer;
    OAuthProvider m_provider;
    /**
     * Local API object
     */
    ShelfAddBookHandler m_addBookHandler = null;
    /**
     * Local API object
     */
    private GoodreadsBookshelves mBookshelfList = null;
    private ReviewUpdateHandler mReviewUpdater = null;

    /**
     * Standard constructor; call common code.
     *
     * @author Philip Warner
     */
    public GoodreadsManager() {
        sharedInit();
    }

    /**
     * Utility method to check if the access tokens are available (not if they are valid).
     */
    public static boolean hasCredentials() {
        if (m_accessToken != null && m_accessSecret != null &&
                !m_accessToken.isEmpty() && !m_accessSecret.isEmpty())
            return true;

        // Get the stored token values from prefs, and setup the consumer if present
        BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

        m_accessToken = prefs.getString("GoodReads.AccessToken.Token", "");
        m_accessSecret = prefs.getString("GoodReads.AccessToken.Secret", "");

        return m_accessToken != null && m_accessSecret != null &&
                !m_accessToken.isEmpty() && !m_accessSecret.isEmpty();
    }

    /**
     * Use mLastRequestTime to determine how long until the next request is allowed; and
     * update mLastRequestTime this needs to be synchronized across threads.
     * <p>
     * Note that as a result of this approach mLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     * <p>
     * This method will sleep() until it can make a request; if ten threads call this
     * simultaneously, one will return immediately, one will return 1 second later, another
     * two seconds etc.
     *
     */
    private static void waitUntilRequestAllowed() {
        long now = System.currentTimeMillis();
        long wait;
        synchronized (m_LastRequestTime) {
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
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Create canonical representation based on the best guess as to the goodreads rules.
     */
    public static String canonicalizeBookshelfName(String name) {
        StringBuilder canonical = new StringBuilder();
        name = name.toLowerCase();
        for (int i = 0; i < name.length(); i++) {
            Character c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                canonical.append(c);
            } else {
                canonical.append('-');
            }
        }
        return canonical.toString();
    }

    /**
     * Construct a full or partial date string based on the y/m/d fields.
     */
    public static String buildDate(Bundle data, String yearField, String monthField, String dayField, String resultField) {
        String date = null;
        if (data.containsKey(yearField)) {
            date = String.format("%04d", data.getLong(yearField));
            if (data.containsKey(monthField)) {
                date += "-" + String.format("%02d", data.getLong(monthField));
                if (data.containsKey(dayField)) {
                    date += "-" + String.format("%02d", data.getLong(dayField));
                }
            }
            if (resultField != null && !date.isEmpty())
                data.putString(resultField, date);
        }
        return date;
    }

    /**
     * Get the date at which the last goodreads synchronization was run
     *
     * @return    Last date
     */
    public static Date getLastSyncDate() {
        String last = BookCatalogueApp.getAppPreferences().getString(LAST_SYNC_DATE, null);
        if (last == null || last.isEmpty()) {
            return null;
        } else {
            try {
                return Utils.parseDate(last);
            } catch (Exception e) {
                Logger.logError(e);
                return null;
            }
        }
    }

    /**
     * Set the date at which the last goodreads synchronization was run
     *
     * @param d Last date
     */
    public static void setLastSyncDate(Date d) {
        if (d == null) {
            BookCatalogueApp.getAppPreferences().setString(LAST_SYNC_DATE, null);
        } else {
            BookCatalogueApp.getAppPreferences().setString(LAST_SYNC_DATE, Utils.toSqlDateTime(d));
        }
    }

    /**
     * Common constructor code.
     *
     * @author Philip Warner
     */
    private void sharedInit() {

        m_consumer = new CommonsHttpOAuthConsumer(DEV_KEY, DEV_SECRET);
        m_provider = new CommonsHttpOAuthProvider(
                GOODREADS_API_ROOT + "/oauth/request_token",
                GOODREADS_API_ROOT + "/oauth/access_token",
                GOODREADS_API_ROOT + "/oauth/authorize");

        if (hasCredentials())
            m_consumer.setTokenWithSecret(m_accessToken, m_accessSecret);
    }

    /**
     * Return the public developer key, used for GET queries.
     *
     * @author Philip Warner
     */
    public String getDeveloperKey() {
        return DEV_KEY;
    }

    /**
     * Check if the current credentials (either cached or in prefs) are valid. If they
     * have been previously checked and were valid, just use that result.
     *
     * @author Philip Warner
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
     * If cached credentials were used, call recursively after clearing the cached
     * values.
     *
     * @author Philip Warner
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
     * @author Philip Warner
     */
    public void requestAuthorization(Context ctx) throws NetworkException {
        String authUrl;

        // Dont do this; this is just part of OAuth and not the API
        //waitUntilRequestAllowed();

        // Get the URL
        try {
            authUrl = m_provider.retrieveRequestToken(m_consumer, "com.eleybourn.bookcatalogue://goodreadsauth");
            //authUrl = m_provider.retrieveRequestToken(m_consumer,  "intent:#Intent;action=android.intent.action.VIEW;category=android.intent.category.DEFAULT;component=com.eleybourn.bookcatalogue/.goodreads.GoodReadsAuthorizationActivity;end");
        } catch (OAuthCommunicationException e) {
            throw new NetworkException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Make a valid URL for the parser (some come back without a schema)
        if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://"))
            authUrl = "https://" + authUrl;

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
     * @author Philip Warner
     */
    public void handleAuthentication() throws NotAuthorizedException {
        // Get the saved request tokens.
        BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
        String tokenString = prefs.getString("GoodReads.RequestToken.Token", "");
        String secretString = prefs.getString("GoodReads.RequestToken.Secret", "");

        if (tokenString.isEmpty() || secretString.isEmpty())
            throw new RuntimeException("Expected a request token to be stored in preferences; none found");

        // Update the consumer.
        m_consumer.setTokenWithSecret(tokenString, secretString);

        // Get the access token
        waitUntilRequestAllowed();
        try {
            m_provider.retrieveAccessToken(m_consumer, null); //m_consumer.getToken());
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
     * Create an HttpClient with specifically set buffer sizes to deal with
     * potentially exorbitant settings on some HTC handsets.
     */
    private HttpClient newHttpClient() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 30000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpConnectionParams.setLinger(params, 0);
        HttpConnectionParams.setTcpNoDelay(params, false);
        return new DefaultHttpClient(params);
    }

    /**
     * Utility routine called to sign a request and submit it then pass it off to a parser.
     *
     * @author Philip Warner
     */
    public HttpResponse execute(HttpUriRequest request, DefaultHandler requestHandler, boolean requiresSignature) throws IOException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, NetworkException {

        // Get a new client
        HttpClient httpClient = newHttpClient();

        // Sign the request and wait until we can submit it legally.
        if (requiresSignature) {
            m_consumer.setTokenWithSecret(m_accessToken, m_accessSecret);
            m_consumer.sign(request);
        }
        waitUntilRequestAllowed();

        // Submit the request and process result.
        HttpResponse response;
        try {
            response = httpClient.execute(request);
        } catch (Exception e) {
            throw new NetworkException(e);
        }

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
     * @author Philip Warner
     */
    public String executeRaw(HttpUriRequest request) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

        // Get a new client
        HttpClient httpClient = newHttpClient();

        // Sign the request and wait until we can submit it legally.
        m_consumer.setTokenWithSecret(m_accessToken, m_accessSecret);
        m_consumer.sign(request);
        waitUntilRequestAllowed();

        // Submit the request then process result.
        HttpResponse response;
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
                    html.append((char) (i));
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
     * @author Philip Warner
     */
    private void parseResponse(HttpResponse response, DefaultHandler requestHandler) throws IllegalStateException, IOException {

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser;

        InputStream in = response.getEntity().getContent();

        // Dont bother catching general exceptions, they will be caught by the caller.
        try {
            parser = factory.newSAXParser();
            // Make sure we follow LibraryThing ToS (no more than 1 request/second).
            parser.parse(in, requestHandler);
        } catch (MalformedURLException | ParserConfigurationException e) {
            String s = "unknown";
            try {
                s = e.getMessage();
            } catch (Exception ignored) {
            }
            Logger.logError(e, s);
        } catch (SAXException e) {
            String s = e.getMessage(); // "unknown";
            try {
                s = e.getMessage();
            } catch (Exception ignored) {
            }
            Logger.logError(e, s);
        } catch (java.io.IOException e) {
            String s = "unknown";
            try {
                s = e.getMessage();
            } catch (Exception ignored) {
            }
            Logger.logError(e, s);
        }
    }

    public long getUserid() {
        if (!m_hasValidCredentials)
            throw new RuntimeException("GoodReads credentials need to be validated before accessing user data");

        return m_userid;
    }

    private GoodreadsBookshelves getShelves() throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
        if (mBookshelfList == null) {
            Hashtable<String, GoodreadsBookshelf> list = new Hashtable<>();
            BookshelfListApiHandler h = new BookshelfListApiHandler(this);
            int page = 1;
            while (true) {
                Bundle result = h.run(page);
                ArrayList<Bundle> shelves = result.getParcelableArrayList(BookshelfListFieldNames.SHELVES);
                assert shelves != null;
                if (shelves.isEmpty())
                    break;

                for (Bundle b : shelves) {
                    GoodreadsBookshelf shelf = new GoodreadsBookshelf(b);
                    list.put(shelf.getName(), shelf);
                }

                if (result.getLong(BookshelfListFieldNames.END) >= result.getLong(BookshelfListFieldNames.TOTAL))
                    break;

                page++;
            }
            mBookshelfList = new GoodreadsBookshelves(list);
        }
        return mBookshelfList;
    }

    /**
     * Wrapper to call API to add book to shelf
     */
    public long addBookToShelf(String shelfName, long grBookId) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
        if (m_addBookHandler == null)
            m_addBookHandler = new ShelfAddBookHandler(this);
        return m_addBookHandler.add(shelfName, grBookId);
    }

    /**
     * Wrapper to call API to remove a book from a shelf
     */
    public void removeBookFromShelf(String shelfName, long grBookId) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
        if (m_addBookHandler == null)
            m_addBookHandler = new ShelfAddBookHandler(this);
        m_addBookHandler.remove(shelfName, grBookId);
    }

    public void updateReview(long reviewId, boolean isRead, String readAt, String review, int rating) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        if (mReviewUpdater == null) {
            mReviewUpdater = new ReviewUpdateHandler(this);
        }
        mReviewUpdater.update(reviewId, isRead, readAt, review, rating);
    }

    /**
     * Wrapper to send an entire book, including shelves, to Goodreads.
     *
     * @param dbHelper DB connection
     * @param books    Cursor pointing to single book to send
     * @return            Disposition of book
     */
    public ExportDisposition sendOneBook(CatalogueDBAdapter dbHelper, BooksRowView books) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, IOException, NetworkException, BookNotFoundException {
        long bookId = books.getId();
        long grId;
        long reviewId = 0;
        Bundle grBookInfo = null;
        boolean isNew;

        // Get the list of shelves from goodreads. This is cached per instance of GoodreadsManager.
        GoodreadsBookshelves grShelfList = getShelves();

        // Get the book ISBN
        String isbn = books.getIsbn();

        // See if the book has a goodreads ID and if it is valid.
        try {
            grId = books.getGoodreadsBookId();
            if (grId != 0) {
                // Get the book details to make sure we have a valid book ID
                grBookInfo = this.getBookById(grId);
                if (grBookInfo == null)
                    grId = 0;
            }
        } catch (Exception e) {
            grId = 0;
        }

        isNew = (grId == 0);

        if (grId == 0 && !isbn.isEmpty()) {

            if (!IsbnUtils.isValid(isbn))
                return ExportDisposition.notFound;

            try {
                // Get the book details using ISBN
                grBookInfo = this.getBookByIsbn(isbn);
                if (grBookInfo != null && grBookInfo.containsKey(ShowBookFieldNames.BOOK_ID))
                    grId = grBookInfo.getLong(ShowBookFieldNames.BOOK_ID);

                // If we got an ID, save it against the book
                if (grId != 0) {
                    dbHelper.setGoodreadsBookId(bookId, grId);
                }
            } catch (BookNotFoundException e) {
                return ExportDisposition.notFound;
            } catch (NetworkException e) {
                return ExportDisposition.networkError;
            }
        }

        // If we found a goodreads book, update it
        if (grId != 0) {
            // Get the review ID if we have the book details. For new books, it will not be present.
            if (!isNew && grBookInfo.containsKey(ShowBookFieldNames.REVIEW_ID)) {
                reviewId = grBookInfo.getLong(ShowBookFieldNames.REVIEW_ID);
            }

            // Lists of shelf names and our best guess at the goodreads canonical name
            ArrayList<String> shelves = new ArrayList<>();
            ArrayList<String> canonicalShelves = new ArrayList<>();

            // Build the list of shelves that we have in the local database for the book
            int exclusiveCount = 0;
            try (Cursor shelfCsr = dbHelper.getAllBookBookshelvesForGoodreadsCursor(bookId)) {
                int shelfCol = shelfCsr.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF);
                // Collect all shelf names for this book
                while (shelfCsr.moveToNext()) {
                    final String shelfName = shelfCsr.getString(shelfCol);
                    final String canonicalShelfName = canonicalizeBookshelfName(shelfName);
                    shelves.add(shelfName);
                    canonicalShelves.add(canonicalShelfName);
                    // Count how many of these shelves are exclusive in goodreads.
                    if (grShelfList.isExclusive(canonicalShelfName)) {
                        exclusiveCount++;
                    }
                }
            }

            // If no exclusive shelves are specified, then add pseudo-shelf to match goodreads because
            // review.update does not seem to update them properly
            if (exclusiveCount == 0) {
                String pseudoShelf;
                if (books.getRead() == 0) {
                    pseudoShelf = "To Read";
                } else {
                    pseudoShelf = "Read";
                }
                if (!shelves.contains(pseudoShelf)) {
                    shelves.add(pseudoShelf);
                    canonicalShelves.add(canonicalizeBookshelfName(pseudoShelf));
                }
            }

            // Get the names of the shelves the book is currently on AT GOODREADS
            ArrayList<String> grShelves;
            if (!isNew && grBookInfo.containsKey(ShowBookFieldNames.SHELVES)) {
                grShelves = grBookInfo.getStringArrayList(ShowBookFieldNames.SHELVES);
            } else {
                grShelves = new ArrayList<>();
            }

            // Remove from any shelves from goodreads that are not in our local list
            assert grShelves != null;
            for (String grShelf : grShelves) {
                if (!canonicalShelves.contains(grShelf)) {
                    try {
                        // Goodreads does not seem to like removing books from the special shelves.
                        if (!(grShelfList.isExclusive(grShelf)))
                            this.removeBookFromShelf(grShelf, grId);
                    } catch (BookNotFoundException e) {
                        // Ignore for now; probably means book not on shelf anyway
                    } catch (Exception e) {
                        return ExportDisposition.error;
                    }
                }
            }

            // Add shelves to goodreads if they are not currently there
            for (String shelf : shelves) {
                // Get the name the shelf will have at goodreads
                final String canonicalShelfName = canonicalizeBookshelfName(shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                boolean okToSend = (exclusiveCount < 2 || !grShelfList.isExclusive(canonicalShelfName));
                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    try {
                        reviewId = this.addBookToShelf(shelf, grId);
                    } catch (Exception e) {
                        return ExportDisposition.error;
                    }
                }
            }

            /* We should be safe always updating here because:
             * - all books that are already added have a review ID, which we would have got from the bundle
             * - all new books will be added to at least one shelf, which will have returned a review ID.
             * But, just in case, we check the review ID, and if 0, we add the book to the 'Default' shelf.
             */
            if (reviewId == 0) {
                try {
                    reviewId = this.addBookToShelf("Default", grId);
                } catch (Exception e) {
                    return ExportDisposition.error;
                }
            }
            // Now update the remaining review details.
            try {
                // Do not sync Notes<->Review. We will add a 'Review' field later.
                //this.updateReview(reviewId, books.getRead() != 0, books.getReadEnd(), books.getNotes(), ((int)books.getRating()) );
                this.updateReview(reviewId, books.getRead() != 0, books.getReadEnd(), null, ((int) books.getRating()));
            } catch (BookNotFoundException e) {
                return ExportDisposition.error;
            }

            return ExportDisposition.sent;
        } else {
            return ExportDisposition.noIsbn;
        }

    }

    /**
     * Wrapper to search for a book.
     *
     * @param query String to search for
     * @return    ArrayList of GoodreadsWork objects
     */
    public ArrayList<GoodreadsWork> search(String query) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {

        if (!query.isEmpty()) {
            SearchBooksApiHandler searcher = new SearchBooksApiHandler(this);
            // Run the search
            return searcher.search(query);
        } else {
            throw new RuntimeException("No search criteria specified");
        }

    }

    /**
     * Wrapper to search for a book.
     *
     * @param bookId book id to search for
     * @return    Array of GoodreadsWork objects
     */
    public Bundle getBookById(long bookId) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        if (bookId != 0) {
            ShowBookByIdApiHandler api = new ShowBookByIdApiHandler(this);
            // Run the search
            return api.get(bookId, true);
        } else {
            throw new RuntimeException("No work ID specified");
        }

    }

    /**
     * Wrapper to search for a book.
     *
     * @param isbn String to search for
     * @return    Array of GoodreadsWork objects
     */
    public Bundle getBookByIsbn(String isbn) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        if (isbn != null && !isbn.isEmpty()) {
            ShowBookByIsbnApiHandler api = new ShowBookByIsbnApiHandler(this);
            // Run the search
            return api.get(isbn, true);
        } else {
            throw new RuntimeException("No work ID specified");
        }

    }

    /**
     * Enum to handle possible results of sending a book to goodreads
     */
    public enum ExportDisposition {error, sent, noIsbn, notFound, networkError}

    /**
     * Exceptions that may be thrown and used to wrap more varied inner exceptions
     */
    public static class Exceptions {
        public static class GeneralException extends Exception {
            private static final long serialVersionUID = 5762518476144652354L;
            Throwable m_inner;

            public GeneralException(Throwable inner) {
                m_inner = inner;
            }
        }

        public static class NotAuthorizedException extends GeneralException {
            private static final long serialVersionUID = 5589234170614368111L;

            public NotAuthorizedException(Throwable inner) {
                super(inner);
            }
        }

        public static class BookNotFoundException extends GeneralException {
            private static final long serialVersionUID = 872113355903361212L;

            public BookNotFoundException(Throwable inner) {
                super(inner);
            }
        }

        public static class NetworkException extends GeneralException {
            private static final long serialVersionUID = -4233137984910957925L;

            public NetworkException(Throwable inner) {
                super(inner);
            }
        }
    }

    private static class GoodreadsBookshelf {
        private final Bundle mBundle;

        public GoodreadsBookshelf(Bundle b) {
            mBundle = b;
        }

        public String getName() {
            return mBundle.getString(BookshelfListFieldNames.NAME);
        }

        public boolean isExclusive() {
            return mBundle.getBoolean(BookshelfListFieldNames.EXCLUSIVE);
        }
    }

    private static class GoodreadsBookshelves {
        private final Hashtable<String, GoodreadsBookshelf> mBookshelfList;

        public GoodreadsBookshelves(Hashtable<String, GoodreadsBookshelf> list) {
            mBookshelfList = list;
        }

        public boolean isExclusive(String name) {
            if (mBookshelfList.containsKey(name)) {
                return Objects.requireNonNull(mBookshelfList.get(name)).isExclusive();
            } else {
                return false;
            }
        }
    }
}

