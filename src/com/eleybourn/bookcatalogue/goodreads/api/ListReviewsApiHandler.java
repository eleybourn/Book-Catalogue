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

import static com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.GOODREADS_API_ROOT;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.ADDED;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.AUTHORS;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_DESCRIPTION;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_FORMAT;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_ISBN;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_NOTES;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_PAGES;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_PUBLISHER;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_RATING;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_READ_END;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_READ_START;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.DB_TITLE;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.END;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.GR_BOOK_ID;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.GR_REVIEW_ID;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.ISBN13;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.LARGE_IMAGE;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.PUB_DAY;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.PUB_MONTH;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.PUB_YEAR;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.REVIEWS;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.SHELF;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.SHELVES;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.SMALL_IMAGE;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.START;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.TOTAL;
import static com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.UPDATED;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.goodreads.api.SimpleXmlFilter.BuilderContext;
import com.eleybourn.bookcatalogue.goodreads.api.SimpleXmlFilter.XmlListener;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to implement the reviews.list api call. It queries based on the passed parameters and returns
 * a single Bundle containing all results. The Bundle itself will contain other bundles: typically an 
 * array of 'Review' bundles, each of which will contains arrays of 'author' bundles.
 * 
 * Processing this data is up to the caller, but it is guaranteed to be type-safe if present, with the
 * exception of dates, which are collected as text strings.
 * 
 * @author Philip Warner
 */
public class ListReviewsApiHandler extends ApiHandler {

	/** Date format used for parsing 'last_update_date' */
	private static final SimpleDateFormat mUpdateDateFmt = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy");

	/**
	 * Field names we add to the bundle based on parsed XML data.
	 * 
	 * We duplicate the CatalogueDBAdapter names (and give them a DB_ prefix) so
	 * that (a) it is clear which fields are provided by this call, and (b) it is clear
	 * which fields directly relate to DB fields.
	 * 
	 * @author Philip Warner
	 */
	public static final class ListReviewsFieldNames {
		public static final String START = "__start";
		public static final String END = "__end";
		public static final String TOTAL = "__total";
		public static final String GR_BOOK_ID = "__gr_book_id";
		public static final String GR_REVIEW_ID = "__gr_review_id";
		public static final String ISBN13 = "__isbn13";
		public static final String SMALL_IMAGE = "__smallImage";
		public static final String LARGE_IMAGE = "__largeImage";
		public static final String PUB_DAY = "__pubDay";
		public static final String PUB_YEAR = "__pubYear";
		public static final String PUB_MONTH = "__pubMonth";
		public static final String ADDED = "__added";
		public static final String UPDATED = "__updated";
		public static final String REVIEWS = "__reviews";
		public static final String AUTHORS = "__authors";
		public static final String SHELF = "__shelf";
		public static final String SHELVES = "__shelves";
		public static final String DB_PAGES = CatalogueDBAdapter.KEY_PAGES;
		public static final String DB_ISBN = CatalogueDBAdapter.KEY_ISBN;
		public static final String DB_TITLE = CatalogueDBAdapter.KEY_TITLE;
		public static final String DB_NOTES = CatalogueDBAdapter.KEY_NOTES;
		public static final String DB_FORMAT = CatalogueDBAdapter.KEY_FORMAT;
		public static final String DB_PUBLISHER = CatalogueDBAdapter.KEY_PUBLISHER;
		public static final String DB_DESCRIPTION = CatalogueDBAdapter.KEY_DESCRIPTION;
		public static final String DB_AUTHOR_ID = CatalogueDBAdapter.KEY_AUTHOR_ID;
		public static final String DB_AUTHOR_NAME = CatalogueDBAdapter.KEY_AUTHOR_NAME;
		public static final String DB_RATING = CatalogueDBAdapter.KEY_RATING;
		public static final String DB_READ_START = CatalogueDBAdapter.KEY_READ_START;
		public static final String DB_READ_END = CatalogueDBAdapter.KEY_READ_END;
	}

	private SimpleXmlFilter mFilters;

	public ListReviewsApiHandler(GoodreadsManager manager) {
		super(manager);
		if (!manager.hasValidCredentials())
			throw new RuntimeException("Goodreads credentials not valid");
		// Build the XML filters needed to get the data we're interested in.
		buildFilters();
	}

	/**
	 * 
	 * @param page
	 * @return
	 * @throws ClientProtocolException
	 * @throws OAuthMessageSignerException
	 * @throws OAuthExpectationFailedException
	 * @throws OAuthCommunicationException
	 * @throws NotAuthorizedException
	 * @throws BookNotFoundException
	 * @throws IOException
	 * @throws NetworkException 
	 */
	public Bundle run(int page, int perPage) 
			throws ClientProtocolException, OAuthMessageSignerException, OAuthExpectationFailedException, 
					OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException 
	{
		long t0 = System.currentTimeMillis();

		// Sort by update_dte (descending) so sync is faster. Specify 'shelf=all' because it seems goodreads returns 
		// the shelf that is selected in 'My Books' on the web interface by default.
		final String urlBase = GOODREADS_API_ROOT + "/review/list/%4$s.xml?key=%1$s&v=2&page=%2$s&per_page=%3$s&sort=date_updated&order=d&shelf=all";
		final String url = String.format(urlBase, mManager.getDeveloperKey(), page, perPage, mManager.getUserid());
		HttpGet get = new HttpGet(url);

		// Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        // Even thought it's only a GET, it needs a signature.
        mManager.execute(get, handler, true);

        // When we get here, the data has been collected but needs to be processed into standard form.
        Bundle results = mFilters.getData();

        // Return parsed results.
		long t1 = System.currentTimeMillis();
		System.out.println("Found " + results.getLong(TOTAL) + " books in " + (t1 - t0) + "ms");
        return results;
	}

	/*
	 * Typical result:

			<GoodreadsResponse>
				<Request>
					...
				</Request>
				<reviews start="3" end="4" total="933">
					<review>
						<id>276860380</id>
						<book>
							<id type="integer">951750</id>
							<isbn>0583120911</isbn>
							<isbn13>9780583120913</isbn13>
							<text_reviews_count type="integer">2</text_reviews_count>
			
							<title>
								<![CDATA[The Dying Earth]]>
							</title>
							<image_url>http://photo.goodreads.com/books/1294108593m/951750.jpg</image_url>
							<small_image_url>http://photo.goodreads.com/books/1294108593s/951750.jpg</small_image_url>
							<link>http://www.goodreads.com/book/show/951750.The_Dying_Earth</link>
							<num_pages>159</num_pages>
			
							<format></format>
							<edition_information></edition_information>
							<publisher></publisher>
							<publication_day>20</publication_day>
							<publication_year>1972</publication_year>
							<publication_month>4</publication_month>
							<average_rating>3.99</average_rating>
			
							<ratings_count>713</ratings_count>
							<description>
								<![CDATA[]]>
							</description>
			
							<authors>
								<author>
									<id>5376</id>
									<name><![CDATA[Jack Vance]]></name>
									<image_url><![CDATA[http://photo.goodreads.com/authors/1207604643p5/5376.jpg]]></image_url>
									<small_image_url><![CDATA[http://photo.goodreads.com/authors/1207604643p2/5376.jpg]]></small_image_url>
									<link><![CDATA[http://www.goodreads.com/author/show/5376.Jack_Vance]]></link>
									<average_rating>3.94</average_rating>
									<ratings_count>12598</ratings_count>
									<text_reviews_count>844</text_reviews_count>
								</author>
			
							</authors>
							<published>1972</published>
						</book>
			
						<rating>0</rating>
						<votes>0</votes>
						<spoiler_flag>false</spoiler_flag>
						<spoilers_state>none</spoilers_state>
			
						<shelves>
							<shelf name="sci-fi-fantasy" />
							<shelf name="to-read" />
						</shelves>
						<recommended_for><![CDATA[]]></recommended_for>
						<recommended_by><![CDATA[]]></recommended_by>
						<started_at></started_at>
						<read_at></read_at>
						<date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>
						<date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>
						<read_count></read_count>
						<body>
							<![CDATA[]]>
						</body>
						<comments_count>0</comments_count>
			
						<url><![CDATA[http://www.goodreads.com/review/show/276860380]]></url>
						<link><![CDATA[http://www.goodreads.com/review/show/276860380]]></link>
			
						<owned>0</owned>
					</review>
			
					<review>
						<id>273090417</id>
						<book>
							<id type="integer">2042540</id>
							<isbn>0722129203</isbn>
			
							<isbn13>9780722129203</isbn13>
							<text_reviews_count type="integer">0</text_reviews_count>
							<title>
								<![CDATA[The Fallible Fiend]]>
							</title>
							<image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
							<small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>
			
							<link>http://www.goodreads.com/book/show/2042540.The_Fallible_Fiend</link>
							<num_pages></num_pages>
							<format></format>
							<edition_information></edition_information>
							<publisher></publisher>
							<publication_day></publication_day>
							<publication_year></publication_year>
							<publication_month></publication_month>
			
							<average_rating>3.55</average_rating>
							<ratings_count>71</ratings_count>
							<description>
								<![CDATA[]]>
							</description>
			
							<authors>
								<author>
									<id>3305</id>
									<name><![CDATA[L. Sprague de Camp]]></name>
									<image_url><![CDATA[http://photo.goodreads.com/authors/1218217726p5/3305.jpg]]></image_url>
									<small_image_url><![CDATA[http://photo.goodreads.com/authors/1218217726p2/3305.jpg]]></small_image_url>
									<link><![CDATA[http://www.goodreads.com/author/show/3305.L_Sprague_de_Camp]]></link>
									<average_rating>3.78</average_rating>
									<ratings_count>9424</ratings_count>
									<text_reviews_count>441</text_reviews_count>
								</author>
							</authors> 
							<published></published>
						</book>
			
						<rating>0</rating>
						<votes>0</votes>
						<spoiler_flag>false</spoiler_flag>
			
						<spoilers_state>none</spoilers_state>
						<shelves>
							<shelf name="read" />
							<shelf name="sci-fi-fantasy" />
						</shelves>
						<recommended_for><![CDATA[]]></recommended_for>
						<recommended_by><![CDATA[]]></recommended_by>
						<started_at></started_at>
			
						<read_at></read_at>
						<date_added>Mon Feb 06 03:40:52 -0800 2012</date_added>
						<date_updated>Mon Feb 06 03:40:52 -0800 2012</date_updated>
						<read_count></read_count>
						<body>
							<![CDATA[]]>
						</body>
						<comments_count>0</comments_count>
			
			
						<url><![CDATA[http://www.goodreads.com/review/show/273090417]]></url>
						<link><![CDATA[http://www.goodreads.com/review/show/273090417]]></link>
						<owned>0</owned>
					</review>
			
				</reviews>
			
			</GoodreadsResponse>

	 */

	/**
	 * Setup filters to process the XML parts we care about.
	 */
	protected void buildFilters() {
		/*
		 * Process the stuff we care about
		 */
		mFilters = new SimpleXmlFilter(mRootFilter);

		mFilters
		//<GoodreadsResponse>
		  .s("GoodreadsResponse")
		//	<Request>
		//		...
		//	</Request>
		//	<reviews start="3" end="4" total="933">
			.s("reviews").isArray(REVIEWS)
				.longAttr("start", START)
				.longAttr("end", END)
				.longAttr("total", TOTAL)
		//		<review>
				.s("review").isArrayItem()
		//			<id>276860380</id>
					.longBody("id", GR_REVIEW_ID)
		//			<book>
					.s("book")
		//				<id type="integer">951750</id>
						.longBody("id", GR_BOOK_ID)
		//				<isbn>0583120911</isbn>
						.stringBody("isbn", DB_ISBN)
		//				<isbn13>9780583120913</isbn13>
						.stringBody("isbn13", ISBN13)
		//				...			
		//				<title><![CDATA[The Dying Earth]]></title>
						.stringBody("title", DB_TITLE)
		//				<image_url>http://photo.goodreads.com/books/1294108593m/951750.jpg</image_url>
						.stringBody("image_url", LARGE_IMAGE)
		//				<small_image_url>http://photo.goodreads.com/books/1294108593s/951750.jpg</small_image_url>
						.stringBody("small_image_url", SMALL_IMAGE)
		//				...
		//				<num_pages>159</num_pages>			
						.longBody("num_pages", DB_PAGES)
		//				<format></format>
						.stringBody("format", DB_FORMAT)
		//				<publisher></publisher>
						.stringBody("publisher", DB_PUBLISHER)
		//				<publication_day>20</publication_day>
						.longBody("publication_day", PUB_DAY)
		//				<publication_year>1972</publication_year>
						.longBody("publication_year", PUB_YEAR)
		//				<publication_month>4</publication_month>
						.longBody("publication_month", PUB_MONTH)
		//				<description><![CDATA[]]></description>
						.stringBody("description", DB_DESCRIPTION)
		//				...
		//
		//				<authors>
						.s("authors")
						.isArray(AUTHORS)
		//					<author>
							.s("author")
							.isArrayItem()
		//						<id>5376</id>
								.longBody("id", DB_AUTHOR_ID)
		//						<name><![CDATA[Jack Vance]]></name>
								.stringBody("name", DB_AUTHOR_NAME)
		//						...
		//					</author>
		//				</authors>
		//				...
		//			</book>
					.popTo("review")
		//
		//			<rating>0</rating>
					.doubleBody("rating", DB_RATING)
		//			...
		//			<shelves>
					.s("shelves")
					.isArray(SHELVES)
		//				<shelf name="sci-fi-fantasy" />
						.s("shelf")
							.isArrayItem()
							.stringAttr("name", SHELF)
					.popTo("review")
		//				<shelf name="to-read" />
		//			</shelves>
		//			...
		//			<started_at></started_at>
					.stringBody("started_at", DB_READ_START)
		//			<read_at></read_at>
					.stringBody("read_at", DB_READ_END)
		//			<date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>
					//.stringBody("date_added", ADDED)
					.s("date_added").stringBody(ADDED).setListener(mAddedListener).pop()
		//			<date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>
					.s("date_updated").stringBody(UPDATED).setListener(mUpdatedListener).pop()
		//			...
		//			<body><![CDATA[]]></body>
					.stringBody("body", DB_NOTES).pop()
		//			...			
		//			<owned>0</owned>
		//		</review>
		//	</reviews>
		//
		//</GoodreadsResponse>
		.done();
	}

	void date2Sql(Bundle b, String key) {
        if (b.containsKey(key)) {
        	String date = b.getString(key);
        	try {
        		Date d = mUpdateDateFmt.parse(date);
        		date = Utils.toSqlDateTime(d);
        		b.putString(key, date);
        	} catch (Exception e) {
        		b.remove(key);
        	}
        }		
	}
	/**
	 * Listener to handle the contents of the date_updated field. We only
	 * keep it if it is a valid date, and we store it in SQL format using 
	 * UTC TZ so comparisons work.
	 */
	XmlListener mUpdatedListener = new XmlListener() {
		@Override
		public void onStart(BuilderContext bc, ElementContext c) {
		}

		@Override
		public void onFinish(BuilderContext bc, ElementContext c) {
			date2Sql(bc.getData(), UPDATED);
		}
	};

	/**
	 * Listener to handle the contents of the date_added field. We only
	 * keep it if it is a valid date, and we store it in SQL format using 
	 * UTC TZ so comparisons work.
	 */
	XmlListener mAddedListener = new XmlListener() {
		@Override
		public void onStart(BuilderContext bc, ElementContext c) {
		}

		@Override
		public void onFinish(BuilderContext bc, ElementContext c) {
			date2Sql(bc.getData(), ADDED);
		}
	};
}
