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
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;

import static com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.GOODREADS_API_ROOT;

/**
 * TODO: OwnedBookCreateHandler WORK IN PROGRESS
 * 
 * @author Philip Warner
 */
public class OwnedBookCreateHandler extends ApiHandler {

	public static class InvalidIsbnException extends RuntimeException {
		private static final long serialVersionUID = 2652418388349622089L;
	}

    //public enum ConditionCode {
	//	BRAND_NEW, LIKE_NEW, VERY_GOOD, GOOD, ACCEPTABLE, POOR
	//}

	public OwnedBookCreateHandler(GoodreadsManager manager) {
		super(manager);
	}

	/*
	 * <owned-book>
	 *  <available-for-swap type='boolean'>false</available-for-swap>
	 *  <book-id type='integer'>9376943</book-id>
	 *  <book-trades-count type='integer'>0</book-trades-count>
	 *  <comments-count type='integer'>0</comments-count>
	 *  <condition-code type='integer' nil='true'></condition-code>
	 *  <condition-description nil='true'></condition-description>
	 *  <created-at type='datetime'>2012-01-01T07:08:47-08:00</created-at>
	 *  <current-owner-id type='integer'>5129458</current-owner-id>
	 *  <current-owner-name nil='true'></current-owner-name>
	 *  <id type='integer'>5431803</id>
	 *  <last-comment-at type='datetime' nil='true'></last-comment-at>
	 *  <original-purchase-date type='datetime' nil='true'></original-purchase-date>
	 *  <original-purchase-location nil='true'></original-purchase-location>
	 *  <review-id type='integer' nil='true'></review-id>
	 *  <swappable-flag type='boolean'>false</swappable-flag>
	 *  <unique-code nil='true'></unique-code>
	 *  <updated-at type='datetime'>2012-01-01T07:08:47-08:00</updated-at>
	 *  <work-id type='integer'>14260549</work-id>
	 * </owned-book>
	 */
	private class OwnedBookCreateParser extends DefaultHandler {
		private static final String BOOK_ID = "book-id";
		private static final String OWNED_BOOK_ID = "id";
		private static final String WORK_ID = "work-id";

		StringBuilder m_builder = new StringBuilder();
		int m_bookId = 0;
		//int m_ownedBookId = 0;
		//int m_workId = 0;

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			super.characters(ch, start, length);
			m_builder.append(ch, start, length);
		}

		public int getBookId() {
			return m_bookId;
		}

		//public int getOwnedBookId() {
		//	return m_ownedBookId;
		//}
		//
		//public int getWorkId() {
		//	return m_workId;
		//}

		@Override
		public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
			super.startElement(uri, localName, name, attributes);

			// reset the string. See note in endElement() for a discussion.
			m_builder.setLength(0);

		}

		@Override
		public void endElement(String uri, String localName, String name) throws SAXException {
			super.endElement(uri, localName, name);

			if (localName.equalsIgnoreCase(BOOK_ID)) {
				m_bookId = Integer.parseInt( m_builder.toString() );
			} else if (localName.equalsIgnoreCase(OWNED_BOOK_ID)) {
				//m_ownedBookId = Integer.parseInt( m_builder.toString() );				
			} else if (localName.equalsIgnoreCase(WORK_ID)) {
				//m_workId = Integer.parseInt( m_builder.toString() );				
			}

			// Note:
			// Always reset the length. This is not entirely the right thing to do, but works
			// because we always want strings from the lowest level (leaf) XML elements.
			// To be completely correct, we should maintain a stack of builders that are pushed and
			// popped as each startElement/endElement is called. But lets not be pedantic for now.
			m_builder.setLength(0);
		}		
	}
	
	/* 
	 *  URL: http://www.goodreads.com/owned_books.xml
	 *	HTTP method: POST
	 *	Parameters:
	 *	    owned_book[condition_code]: one of 10 (brand new), 20 (like new), 30 (very good), 40 (good), 50 (acceptable), 60 (poor)
	 *	    owned_book[unique_code]: BookCrossing id (BCID)
	 *	    owned_book[original_purchase_location]: where this book was purchased
	 *	    owned_book[book_id]: id of the book (required)
	 *	    owned_book[original_purchase_date]: when book was purchased
	 *	    owned_book[condition_description]: description of book's condition
	 *	    owned_book[available_for_swap]: true or false, if book is available for swap
	 */
	public void create(String isbn, ArrayList<String> shelves) 
			throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, 
					NotAuthorizedException, NetworkException, BookNotFoundException 
	{
		IsbnToId isbnToId = new IsbnToId(mManager);
		long id;
		
		try {
			id = isbnToId.isbnToId(isbn);
		} catch (com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.BookNotFoundException e) {
			throw new InvalidIsbnException();
		}

		HttpPost post = new HttpPost(GOODREADS_API_ROOT + "/owned_books.xml");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("owned_book[book_id]", Long.toString(id)));
        
        post.setEntity(new UrlEncodedFormEntity(parameters));	        	

        OwnedBookCreateParser handler = new OwnedBookCreateParser();
        mManager.execute(post, handler, true);

        ShelfAddBookHandler shelfAdd = new ShelfAddBookHandler(mManager);
        for( String shelf : shelves) {
	        shelfAdd.add(shelf, handler.getBookId());	
        }
	}

	public void create(String isbn, String shelf) 
			throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, 
					NotAuthorizedException, NetworkException, BookNotFoundException {
		ArrayList<String> shelves = new ArrayList<String>();
		shelves.add(shelf);
		this.create(isbn, shelves);
	}
}
