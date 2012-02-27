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
import static com.eleybourn.bookcatalogue.goodreads.api.ShowBookApiHandler.FieldNames.*;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.XmlHandler;

/**
 * Class to query and response to search.books api call. This is an abstract class
 * designed to be used by other classes that implement specific search methods. It does
 * the heavy lifting of parsing the results etc.
 * 
 * @author Philip Warner
 */
public abstract class ShowBookApiHandler extends ApiHandler {

	/**
	 * Field names we add to the bundle based on parsed XML data
	 * 
	 * @author Philip Warner
	 */
	public static final class FieldNames {
		public static final String BOOK_ID = "__book_id";
		public static final String ISBN13 = "__isbn13";
		public static final String IMAGE = "__image";
		public static final String SMALL_IMAGE = "__smallImage";
		public static final String PUBLICATION_YEAR = "__pub_year";
		public static final String PUBLICATION_MONTH = "__pub_month";
		public static final String PUBLICATION_DAY = "__pub_day";
		public static final String IS_EBOOK = "__is_ebook";
		public static final String WORK_ID = "__work_id";
		public static final String ORIG_PUBLICATION_YEAR = "__orig_pub_year";
		public static final String ORIG_PUBLICATION_MONTH = "__orig_pub_month";
		public static final String ORIG_PUBLICATION_DAY = "__orig_pub_day";
		public static final String ORIG_TITLE = "__orig_title";
		public static final String RATING = "__rating";
		public static final String BOOK_URL = "__url";		
	}

	/** Transient global data for current work in search results. */
	private Bundle mBook;
	/** Local storage for series book appears in */
	private ArrayList<Series> mSeries = null;
	/** Local storage for series book appears in */
	private ArrayList<Author> mAuthors = null;
	
	/** Current author being processed */
	private String mCurrAuthorName = null;
	/** Current author being processed */
	private long mCurrAuthorId = 0;

	/** Current series being processed */
	private String mCurrSeriesName = null;
	/** Current series being processed */
	private Integer mCurrSeriesPosition = null;
	/** Current series being processed */
	private int mCurrSeriesId = 0;
	
	public ShowBookApiHandler(GoodreadsManager manager) {
		super(manager);
		// Build the XML filters needed to get the data we're interested in.
		buildFilters();
	}

	/**
	 * Perform a search and handle the results.
	 * 
	 * @param request			HttpGet request to use
	 * @param fetchThumbnail 	Indicates if thumbnail file should be retrieved
	 * 
	 * @return	the Bundl of data.
	 * 
	 * @throws IOException 
	 * @throws BookNotFoundException 
	 * @throws NotAuthorizedException 
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthMessageSignerException 
	 * @throws ClientProtocolException 
	 */
	public Bundle sendRequest(HttpGet request, boolean fetchThumbnail) throws ClientProtocolException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException {
    
		mBook = new Bundle();

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        mManager.execute(request, handler, true);

        // When we get here, the data has been collected but needs to be processed into standard form.
        
        // Use ISBN13 by preference
        if (mBook.containsKey(ISBN13)) {
        	String s = mBook.getString(ISBN13);
        	if (s.length() == 13)
        		mBook.putString(CatalogueDBAdapter.KEY_ISBN, s);
        }

    	// RELEASE Store BOOK_ID = "__book_id" into GR_BOOK_ID;
        if (fetchThumbnail) {
            String bestImage = null;
            if (mBook.containsKey(IMAGE)) {
            	bestImage = mBook.getString(IMAGE);
            	if (bestImage.contains("nocover") && mBook.containsKey(SMALL_IMAGE)) {
            		bestImage = mBook.getString(SMALL_IMAGE);
            		if (bestImage.contains("nocover"))
            			bestImage = null;
            	}
            }
            if (bestImage != null) {
    			String filename = Utils.saveThumbnailFromUrl(bestImage, "_GR");
    			if (filename.length() > 0)
    				Utils.appendOrAdd(mBook, "__thumbnail", filename);
            }
        }

        /** Build the pub date based on the components */
        buildDate(PUBLICATION_YEAR, PUBLICATION_MONTH, PUBLICATION_DAY, CatalogueDBAdapter.KEY_DATE_PUBLISHED);

        if (mBook.containsKey(IS_EBOOK) && mBook.getBoolean(IS_EBOOK))
        	mBook.putString(CatalogueDBAdapter.KEY_FORMAT, "Ebook");

        /**
         * Cleanup the title by removing series name, if present
         */
        if (mBook.containsKey(CatalogueDBAdapter.KEY_TITLE)) {
			String thisTitle = mBook.getString(CatalogueDBAdapter.KEY_TITLE);
			Series.SeriesDetails details = Series.findSeries(thisTitle);
			if (details != null && details.name.length() > 0) {
				if (mSeries == null)
					mSeries = new ArrayList<Series>();
				mSeries.add(new Series(details.name, details.position));
				if (mBook.containsKey(ORIG_TITLE)) {
		        	mBook.putString(CatalogueDBAdapter.KEY_TITLE, mBook.getString(ORIG_TITLE));
		        } else {
					mBook.putString(CatalogueDBAdapter.KEY_TITLE, thisTitle.substring(0, details.startChar-1));		        	
		        }
			}
        } else if (mBook.containsKey(ORIG_TITLE)) {
        	mBook.putString(CatalogueDBAdapter.KEY_TITLE, mBook.getString(ORIG_TITLE));
        }

    	// RELEASE Store WORK_ID = "__work_id" into GR_WORK_ID;
        // RELEASE: Store ORIGINAL_PUBLICATION_DATE in database
        
        // If no published date, try original date
        if (!mBook.containsKey(CatalogueDBAdapter.KEY_DATE_PUBLISHED)) {
            String origDate = buildDate(ORIG_PUBLICATION_YEAR, ORIG_PUBLICATION_MONTH, ORIG_PUBLICATION_DAY, null);
        	if (origDate != null && origDate.length() > 0)
        		mBook.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, origDate);
        }

    	//public static final String RATING = "__rating";
    	//public static final String BOOK_URL = "__url";

        if (mAuthors != null && mAuthors.size() > 0)
			mBook.putString(CatalogueDBAdapter.KEY_AUTHOR_DETAILS, Utils.getAuthorUtils().encodeList(mAuthors, '|'));

        if (mSeries != null && mSeries.size() > 0)
			mBook.putString(CatalogueDBAdapter.KEY_SERIES_DETAILS, Utils.getSeriesUtils().encodeList(mSeries, '|'));

        // Return parsed results.
        return mBook;
	}

	/**
	 * Construct a full or partial date string based on the y/m/d fields.
	 * 
	 * @param yearField
	 * @param monthField
	 * @param dayField
	 * @param resultField
	 * @return
	 */
	private String buildDate(String yearField, String monthField, String dayField, String resultField) {
		String date = null;
	    if (mBook.containsKey(yearField)) {
	    	date = String.format("%04d", mBook.getLong(yearField));
	        if (mBook.containsKey(monthField)) {
	        	date += "-" + String.format("%02d", mBook.getLong(monthField));
	            if (mBook.containsKey(dayField)) {
	            	date += "-" + String.format("%02d", mBook.getLong(dayField));
	            }
	        }
	        if (resultField != null && date != null && date.length() > 0)
	        	mBook.putString(resultField, date);
	    }
	    return date;
	}
	/*
	 * Typical result:

			<GoodreadsResponse>
				<Request>
					<authentication>true</authentication>
					<key>GJ59HZyvOM5KGm6Wn8GDzg</key>
					<method>book_show</method>
				</Request>
				<book>
					<id>50</id>
					<title>Hatchet (Hatchet, #1)</title>
					<isbn>0689840926</isbn>
					<isbn13>9780689840920</isbn13>
					<asin></asin>
					<image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
					<small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>
					<publication_year>2000</publication_year>
					<publication_month>4</publication_month>
					<publication_day>1</publication_day>
					<publisher/><language_code/>
					<is_ebook>false</is_ebook>
					<description><p>Since it was first published in 1987, the story of thirteen-year-old Brian Robeson's survival following a plane crash has become a modern classic. Stranded in the desolate wilderness, Brian uses his instincts and his hatchet to stay alive for fifty-four harrowing days. <p> This twentieth-anniversary edition of <em>Hatchet</em> contains a new introduction and sidebar commentary by Gary Paulsen, written especially for this volume. Drew Willis's detailed pen-and-ink illustrations complement the descriptions in the text and add a new dimension to the book. This handsome edition of the Newbery Honor book will be treasured by <em>Hatchet</em> fans as well as by readers encountering Brian's unforgettable story for the first time.</p></p></description>
					<work>
						<best_book_id type="integer">50</best_book_id>
						<books_count type="integer">47</books_count>
						<id type="integer">1158125</id>
						<media_type>book</media_type>
						<original_language_id type="integer" nil="true"/>
						<original_publication_day type="integer">1</original_publication_day>
						<original_publication_month type="integer">1</original_publication_month>
						<original_publication_year type="integer">1987</original_publication_year>
						<original_title>Hatchet</original_title>
						<rating_dist>5:12626|4:17440|3:15621|2:6008|1:2882|total:54577</rating_dist>
						<ratings_count type="integer">54545</ratings_count>
						<ratings_sum type="integer">194541</ratings_sum>
						<reviews_count type="integer">64752</reviews_count>
						<text_reviews_count type="integer">3705</text_reviews_count>
					</work>
					<average_rating>3.57</average_rating>
					<num_pages>208</num_pages>
					<format>Hardcover</format>
					<edition_information></edition_information>
					<ratings_count>51605</ratings_count>
					<text_reviews_count>3299</text_reviews_count>
					<url>http://www.goodreads.com/book/show/50.Hatchet</url>
					<link>http://www.goodreads.com/book/show/50.Hatchet</link>
					<authors>
						<author>
							<id>18</id>
							<name>Gary Paulsen</name>
							<image_url>http://photo.goodreads.com/authors/1309159225p5/18.jpg</image_url>
							<small_image_url>http://photo.goodreads.com/authors/1309159225p2/18.jpg</small_image_url>
							<link>http://www.goodreads.com/author/show/18.Gary_Paulsen</link>
							<average_rating>3.64</average_rating>
							<ratings_count>92755</ratings_count>
							<text_reviews_count>9049</text_reviews_count>
						</author>
					</authors>
					<friend_reviews>
					</friend_reviews>
					<reviews_widget>....</reviews_widget>
					<popular_shelves>
						<shelf name="to-read" count="3496"/>
						<shelf name="young-adult" count="810"/>
						<shelf name="fiction" count="537"/>
						<shelf name="currently-reading" count="284"/>
						<shelf name="adventure" count="247"/>
						<shelf name="childrens" count="233"/>
						<shelf name="ya" count="179"/>
						<shelf name="survival" count="170"/>
						<shelf name="favorites" count="164"/>
						<shelf name="classics" count="155"/>
					</popular_shelves>
					<book_links>
						<book_link>
							<id>3</id>
							<name>Barnes & Noble</name>
							<link>http://www.goodreads.com/book_link/follow/3?book_id=50</link>
						</book_link>
						<book_link>
							<id>8</id>
							<name>WorldCat</name>
							<link>http://www.goodreads.com/book_link/follow/8?book_id=50</link>
						</book_link>
						<book_link>
							<id>1027</id>
							<name>Kobo</name>
							<link>http://www.goodreads.com/book_link/follow/1027?book_id=50</link>
						</book_link>
						<book_link>
							<id>9</id>
							<name>Indigo</name>
							<link>http://www.goodreads.com/book_link/follow/9?book_id=50</link>
						</book_link>
						<book_link><id>4</id><name>Abebooks</name><link>http://www.goodreads.com/book_link/follow/4?book_id=50</link></book_link>
						<book_link><id>2</id><name>Half.com</name><link>http://www.goodreads.com/book_link/follow/2?book_id=50</link></book_link>
						<book_link><id>10</id><name>Audible</name><link>http://www.goodreads.com/book_link/follow/10?book_id=50</link></book_link>
						<book_link><id>5</id><name>Alibris</name><link>http://www.goodreads.com/book_link/follow/5?book_id=50</link></book_link>
						<book_link><id>2102</id><name>iBookstore</name><link>http://www.goodreads.com/book_link/follow/2102?book_id=50</link></book_link>
						<book_link><id>1602</id><name>Google eBooks</name><link>http://www.goodreads.com/book_link/follow/1602?book_id=50</link></book_link>
						<book_link><id>107</id><name>Better World Books</name><link>http://www.goodreads.com/book_link/follow/107?book_id=50</link></book_link>
						<book_link><id>7</id><name>IndieBound</name><link>http://www.goodreads.com/book_link/follow/7?book_id=50</link></book_link>
						<book_link><id>1</id><name>Amazon</name><link>http://www.goodreads.com/book_link/follow/1?book_id=50</link></book_link>
					</book_links>
					<series_works>
						<series_work>
							<id>268218</id>
							<user_position>1</user_position>
							<series>
								<id>62223</id>
								<title>Brian's Saga</title>
								<description></description>
								<note></note>
								<series_works_count>7</series_works_count>
								<primary_work_count>5</primary_work_count>
								<numbered>true</numbered>
							</series>
						</series_work>
					</series_works>
				</book>
			</GoodreadsResponse>

	 */

	/**
	 * Setup filters to process the XML parts we care about.
	 */
	protected void buildFilters() {
		/*
		   Stuff we care about

			<GoodreadsResponse>
				...
				<book>
					<id>50</id>
					<title>Hatchet (Hatchet, #1)</title>
					<isbn>0689840926</isbn>
					<isbn13>9780689840920</isbn13>
					...
					<image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
					<small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>

					<publication_year>2000</publication_year>
					<publication_month>4</publication_month>
					<publication_day>1</publication_day>

					<publisher/><language_code/>
					<is_ebook>false</is_ebook>
					<description><p>Since it was first published in 1987, the story of thirteen-year-old Brian Robeson's survival following a plane crash has become a modern classic. Stranded in the desolate wilderness, Brian uses his instincts and his hatchet to stay alive for fifty-four harrowing days. <p> This twentieth-anniversary edition of <em>Hatchet</em> contains a new introduction and sidebar commentary by Gary Paulsen, written especially for this volume. Drew Willis's detailed pen-and-ink illustrations complement the descriptions in the text and add a new dimension to the book. This handsome edition of the Newbery Honor book will be treasured by <em>Hatchet</em> fans as well as by readers encountering Brian's unforgettable story for the first time.</p></p></description>
					<work>
						...
						<id type="integer">1158125</id>
						...
						<original_publication_day type="integer">1</original_publication_day>
						<original_publication_month type="integer">1</original_publication_month>
						<original_publication_year type="integer">1987</original_publication_year>
						<original_title>Hatchet</original_title>
						...
					</work>
					<average_rating>3.57</average_rating>
					<num_pages>208</num_pages>
					<format>Hardcover</format>
					...
					<url>http://www.goodreads.com/book/show/50.Hatchet</url>
					<link>http://www.goodreads.com/book/show/50.Hatchet</link>

					<authors>
						<author>
							<id>18</id>
							<name>Gary Paulsen</name>
							...
						</author>
					</authors>
					...
					<series_works>
						<series_work>
							<id>268218</id>
							<user_position>1</user_position>
							<series>
								<id>62223</id>
								<title>Brian's Saga</title>
								...
							</series>
						</series_work>
					</series_works>
				</book>
			</GoodreadsResponse>
		 */
		
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "id").setEndAction(mHandleLong, BOOK_ID);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "title").setEndAction(mHandleText, CatalogueDBAdapter.KEY_TITLE);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "isbn").setEndAction(mHandleText, CatalogueDBAdapter.KEY_ISBN);			
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "isbn13").setEndAction(mHandleText, ISBN13);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "image_url").setEndAction(mHandleText, IMAGE);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "small_image_url").setEndAction(mHandleText, SMALL_IMAGE);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "publication_year").setEndAction(mHandleLong, PUBLICATION_YEAR);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "publication_month").setEndAction(mHandleLong,PUBLICATION_MONTH);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "publication_day").setEndAction(mHandleLong, PUBLICATION_DAY);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "publisher").setEndAction(mHandleText, CatalogueDBAdapter.KEY_PUBLISHER);

		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "is_ebook").setEndAction(mHandleBoolean, IS_EBOOK);

		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "description").setEndAction(mHandleText, CatalogueDBAdapter.KEY_DESCRIPTION);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "work", "id").setEndAction(mHandleLong, WORK_ID);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "work", "original_publication_day").setEndAction(mHandleLong, ORIG_PUBLICATION_DAY);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "work", "original_publication_month").setEndAction(mHandleLong, ORIG_PUBLICATION_MONTH);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "work", "original_publication_year").setEndAction(mHandleLong, ORIG_PUBLICATION_YEAR);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "work", "original_title").setEndAction(mHandleText, ORIG_TITLE);

		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "average_rating").setEndAction(mHandleFloat, RATING);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "num_pages").setEndAction(mHandleLong, CatalogueDBAdapter.KEY_PAGES);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "format").setEndAction(mHandleText, CatalogueDBAdapter.KEY_FORMAT);

		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "url").setEndAction(mHandleText, BOOK_URL);

		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "authors", "author")
			.setStartAction(mHandleAuthorStart)
			.setEndAction(mHandleAuthorEnd);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "authors", "author", "id").setEndAction(mHandleAuthorId);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "authors", "author", "name").setEndAction(mHandleAuthorName);
	
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "series_works", "series_work")
			.setStartAction(mHandleSeriesStart)
			.setEndAction(mHandleSeriesEnd);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "series_works", "series_work", "user_position").setEndAction(mHandleSeriesPosition);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "series_works", "series_work", "series", "id").setEndAction(mHandleSeriesId);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "book", "series_works", "series_work", "series", "title").setEndAction(mHandleSeriesName);
	
	
	}

	private XmlHandler mHandleSeriesStart = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			//mCurrSeries = new Series();
		}
	};
	private XmlHandler mHandleSeriesEnd = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			if (mCurrSeriesName != null && mCurrSeriesName.length() > 0) {
				if (mSeries == null)
					mSeries = new ArrayList<Series>();
				if (mCurrSeriesPosition == null) {
					mSeries.add(new Series(mCurrSeriesName, ""));
				} else {
					mSeries.add(new Series(mCurrSeriesName, mCurrSeriesPosition + ""));
				}
				mCurrSeriesName = null;	
				mCurrSeriesPosition = null;
			}
		}
	};

	private XmlHandler mHandleSeriesPosition = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			try {
				mCurrSeriesPosition = Integer.parseInt(context.body.trim());
			} catch (Exception e) {
				// Ignore
			}
		}
	};
	private XmlHandler mHandleSeriesName = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			mCurrSeriesName = context.body.trim();
		}
	};
	private XmlHandler mHandleSeriesId = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			try {
				mCurrSeriesId = Integer.parseInt(context.body.trim());
			} catch (Exception e) {
				// Ignore
			}
		}
	};

	
	private XmlHandler mHandleAuthorStart = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			//mCurrAuthor = new Author();
		}
	};
	private XmlHandler mHandleAuthorEnd = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			if (mCurrAuthorName != null && mCurrAuthorName.length() > 0) {
				if (mAuthors == null)
					mAuthors = new ArrayList<Author>();
				mAuthors.add(new Author(mCurrAuthorName));
				mCurrAuthorName = null;		
			}
		}
	};
	private XmlHandler mHandleAuthorId = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			try {
				mCurrAuthorId = Long.parseLong(context.body.trim());
			} catch (Exception e) {
				// Ignore
			}
		}
	};
	private XmlHandler mHandleAuthorName = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			mCurrAuthorName = context.body.trim();
		}
	};
	
	private XmlHandler mHandleText = new XmlHandler() {

		@Override
		public void process(ElementContext context) {
			final String name = (String)context.userArg;
			mBook.putString(name, context.body.trim());
		}
	};

	private XmlHandler mHandleLong = new XmlHandler() {

		@Override
		public void process(ElementContext context) {
			final String name = (String)context.userArg;
			try {
				long l = Long.parseLong(context.body.trim());
				mBook.putLong(name, l);
			} catch (Exception e) {
				// Ignore but dont add
			}
		}
	};
	
	private XmlHandler mHandleFloat = new XmlHandler() {

		@Override
		public void process(ElementContext context) {
			final String name = (String)context.userArg;
			try {
				double d = Double.parseDouble(context.body.trim());
				mBook.putDouble(name, d);
			} catch (Exception e) {
				// Ignore but dont add
			}
		}
	};

	private XmlHandler mHandleBoolean = new XmlHandler() {

		@Override
		public void process(ElementContext context) {
			final String name = (String)context.userArg;
			try {
				String s = context.body.trim();
				boolean b;
				if (s.length() == 0) {
					b = false;
				} else if (s.equalsIgnoreCase("false")) {
					b = false;
				} else if (s.equalsIgnoreCase("true")) {
					b = true;
				} else if (s.equalsIgnoreCase("f")) {
					b = false;
				} else if (s.equalsIgnoreCase("t")) {
					b = true;
				} else {
					long l = Long.parseLong(s);
					b = (l != 0);
				}
				mBook.putBoolean(name, b);
			} catch (Exception e) {
				// Ignore but dont add
			}
		}
	};

}
