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
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsWork;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.XmlHandler;

import static com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.GOODREADS_API_ROOT;

/**
 * Class to query and response to search.books api call.
 * 
 * @author Philip Warner
 */
public class SearchBooksApiHandler extends ApiHandler {
	/** List of GoodreadsWork objects that result from a search */
	ArrayList<GoodreadsWork> m_works = null;
	/** Starting result # (for multi-page result sets). We dont use (yet). */
	private Long m_resultsStart;
	/** Ending result # (for multi-page result sets). We dont use (yet). */
	private Long m_resultsEnd;
	/** Total results available, as opposed to number returned on first page. */
	private Long m_totalResults;
	/** Transient global data for current work in search results. */
	private GoodreadsWork m_currWork;

	public SearchBooksApiHandler(GoodreadsManager manager) {
		super(manager);
		// Build the XML filters needed to get the data we're interested in.
		buildFilters();
	}

	/**
	 * Perform a search and handle the results.
	 * 
	 * @param query search query
	 * @return	ArrayList the array of GoodreadsWork objects.
	 */
	public ArrayList<GoodreadsWork> search(String query) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
		query = query.trim();

		// Setup API call
		HttpPost post = new HttpPost(GOODREADS_API_ROOT + "/search/index.xml");
        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("q", query));
        parameters.add(new BasicNameValuePair("key", mManager.getDeveloperKey()));
    
        post.setEntity(new UrlEncodedFormEntity(parameters));
        m_works = new ArrayList<>();

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        mManager.execute(post, handler, false);

        // Return parsed results.
        return m_works;
	}

	/**
	 * Accessor for resulting data
     */
	public long getResultsStart() {
		return m_resultsStart;
	}
	/**
	 * Accessor for resulting data
	 */
	public long getTotalResults() {
		return m_totalResults;
	}
	/**
	 * Accessor for resulting data
	 */
	public long getResultsEnd() {
		return m_resultsEnd;
	}

	/**
	 * Setup filters to process the XML parts we care about.
	 */
	protected void buildFilters() {
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results-start").setEndAction(mHandleResultsStart);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results-end").setEndAction(mHandleResultsEnd);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "total-results").setEndAction(mHandleTotalResults);			
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work")
			.setStartAction(mHandleWorkStart)
			.setEndAction(mHandleWorkEnd);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "id").setEndAction(mHandleWorkId);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "original_publication_day").setEndAction(mHandlePubDay);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "original_publication_month").setEndAction(mHandlePubMonth);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "original_publication_year").setEndAction(mHandlePubYear);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "best_book", "id").setEndAction(mHandleBookId);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "best_book", "title").setEndAction(mHandleBookTitle);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "best_book", "author", "id").setEndAction(mHandleAuthorId);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "best_book", "author", "name").setEndAction(mHandleAuthorName);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "best_book", "image_url").setEndAction(mHandleImageUrl);
		XmlFilter.buildFilter(mRootFilter, "GoodreadsResponse", "search", "results", "work", "best_book", "small_image_url").setEndAction(mHandleSmallImageUrl);		
	}

	private final XmlHandler mHandleResultsStart = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_resultsStart = Long.parseLong(context.body);
		}
	};
	private final XmlHandler mHandleResultsEnd = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_resultsEnd = Long.parseLong(context.body);
		}
	};
	private final XmlHandler mHandleTotalResults = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_totalResults = Long.parseLong(context.body);
		}
	};
	
	/**
	 * At the START of a "work" tag, we create a new work.
	 */
	private final XmlHandler mHandleWorkStart = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork = new GoodreadsWork();
		}
	};
	/**
	 * At the END of a "work" tag, we add it to list and reset the pointer.
	 */
	private final XmlHandler mHandleWorkEnd = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			//m_currWork.requestImage();
			m_works.add(m_currWork);
			m_currWork = null;
		}
	};
	private final XmlHandler mHandleWorkId = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork.workId = Long.parseLong(context.body);
		}
	};
	private final XmlHandler mHandlePubDay = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			try {
				m_currWork.pubDay = Long.parseLong(context.body);					
			} catch (Exception ignored) {}
        }
	};
	private final XmlHandler mHandlePubMonth = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			try {
				m_currWork.pubMonth = Long.parseLong(context.body);
			} catch (Exception ignored) {}
        }
	};
	private final XmlHandler mHandlePubYear = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			try {
				m_currWork.pubYear = Long.parseLong(context.body);
			} catch (Exception ignored) {}
        }
	};
	private final XmlHandler mHandleBookId = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork.bookId = Long.parseLong(context.body);
		}
	};
	private final XmlHandler mHandleBookTitle = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork.title = context.body;
		}
	};
	private final XmlHandler mHandleAuthorId = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork.authorId = Long.parseLong(context.body);
		}
	};
	private final XmlHandler mHandleAuthorName = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork.authorName = context.body;
		}
	};
	private final XmlHandler mHandleImageUrl = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork.imageUrl = context.body;
		}
	};
	private final XmlHandler mHandleSmallImageUrl = new XmlHandler(){
		@Override
		public void process(ElementContext context) {
			m_currWork.smallImageUrl = context.body;
		}
	};
		
}
