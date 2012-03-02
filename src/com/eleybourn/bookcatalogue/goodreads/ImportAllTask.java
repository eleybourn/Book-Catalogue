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

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.client.ClientProtocolException;

import net.philipwarner.taskqueue.QueueManager;
import android.content.Context;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.BcQueueManager;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.Utils;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.FieldNames;
import com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;
import static com.eleybourn.bookcatalogue.goodreads.api.ShowBookApiHandler.FieldNames.ORIG_TITLE;

/**
 * Import all a users 'reviews' from goodreads; a users 'reviews' consistes of all the books that 
 * they have placed on bookshelves, irrespective of whether they have rated or reviewd the book.
 * 
 * @author Philip Warner
 */
public class ImportAllTask extends GenericTask {
	private static final long serialVersionUID = -3535324410982827612L;

	/** Current position in entire list of reviews */
	private int mPosition;
	/** Total number of reviews user has */
	private int mTotalBooks;
	/** Flag indicating this is the first time *this* object instance has been called */
	private transient boolean mFirstCall = true;

	/** Number of books to retrieve in one batch; we are encouarged to make fewer API calls, so
	 * setting this number high is good. 50 seems to take several seconds to retrieve, so it 
	 * was chosen.
	 */
	private static final int BOOKS_PER_PAGE = 50;

	/**
	 * Constructor
	 */
	public ImportAllTask() {
		super(BookCatalogueApp.getResourceString(R.string.import_all_from_goodreads));
		mPosition = 0;
	}

	/**
	 * Do the actual work.
	 */
	@Override
	public boolean run(QueueManager qMgr, Context context) {
		CatalogueDBAdapter db = new CatalogueDBAdapter(context);
		db.open();
		
		try {
			return processReviews(qMgr, db);
		} finally {
			if (db != null)
				db.close();
		}
	}

	/**
	 * Repeatedly request review pages until we are done.
	 * 
	 * @param qMgr
	 * @param db
	 *
	 * @return
	 */
	private boolean processReviews(QueueManager qMgr, CatalogueDBAdapter db) {
		GoodreadsManager gr = new GoodreadsManager();
		ListReviewsApiHandler api = new ListReviewsApiHandler(gr);

		int currPage = (mPosition / BOOKS_PER_PAGE);
		while(true) {
			// page numbers are 1-based; start at 0 and increment at start of each loop
			currPage++;

			// In case of a restart, reset position to first in page
			mPosition = BOOKS_PER_PAGE * (currPage - 1);

			Bundle books;

			// Call the API, return false if failed.
			try {
				books = api.run(currPage, BOOKS_PER_PAGE);
			} catch (Exception e) {
				this.setException(e);
				return false;
			}
			
			// Get the total, and if first call, save the object again so the UI can update.
			mTotalBooks = (int)books.getLong(FieldNames.TOTAL);
			if (mFirstCall) {
				// So the details get updated
				qMgr.saveTask(this);
				mFirstCall = false;
			}

			// Get the reviews array and process it
			ArrayList<Bundle> reviews = books.getParcelableArrayList(FieldNames.REVIEWS);

			if (reviews.size() == 0)
				break;

			for(Bundle review: reviews) {
				// Always check for an abort request
				if (this.isAborting())
					return false;

				// Processing may involve a SLOW thumbnail download...don't run in TX!
				processReview(db, review);
				//SyncLock tx = db.startTransaction(true);
				//try {
				//	processReview(db, review);
				//	db.setTransactionSuccessful();
				//} finally {
				//	db.endTransaction(tx);
				//}

				// Update after each book. Mainly for a nice UI.
				qMgr.saveTask(this);
				mPosition++;
			}
		}	
		return true;
	}

	/**
	 * Process one review (book).
	 * 
	 * @param db
	 * @param review
	 */
	private void processReview(CatalogueDBAdapter db, Bundle review) {
		long grId = review.getLong(FieldNames.GR_BOOK_ID);

		// Find the books in our database - NOTE: may be more than one!
		// First look by goodreads book ID
		BooksCursor c = db.fetchBooksByGoodreadsBookId(grId);
		try {
			boolean found = c.moveToFirst();
			if (!found) {
				// Not found by GR id, look via ISBNs
				c.close();
				c = null;

				ArrayList<String> isbns = extractIsbns(review);
				if (isbns != null && isbns.size() > 0) {
					c = db.fetchBooksByIsbns(isbns);
					found = c.moveToFirst();
				}
			}

			if (found) {
				// If found, update ALL related books
				BooksRowView rv = c.getRowView();
				do {
					// Check for abort
					if (this.isAborting())
						break;
					updateBook(db, rv, review);
				} while (c.moveToNext());
			} else {
				// Create the book
				createBook(db, review);
			}
		} finally {
			if (c != null)
				c.close();
		}
	}

	/**
	 * Extract a list of ISBNs from the bundle
	 *
	 * @param review
	 * @return
	 */
	private ArrayList<String> extractIsbns(Bundle review) {
		ArrayList<String> isbns = new ArrayList<String>();
		String isbn;

		isbn = review.getString(FieldNames.ISBN13).trim();
		if (isbn != null && !isbn.equals(""))
			isbns.add(isbn);

		isbn = review.getString(CatalogueDBAdapter.KEY_ISBN).trim();
		if (isbn != null && !isbn.equals(""))
			isbns.add(isbn);

		return isbns;
	}

	/**
	 * Update the book using the GR data
	 * 
	 * @param db
	 * @param rv
	 * @param review
	 */
	private void updateBook(CatalogueDBAdapter db, BooksRowView rv, Bundle review) {
		// We build a new book bundle each time since it will build on the existing
		// data for the given book, not just replace it.
		Bundle book = buildBundle(db, rv, review);
		db.updateBook(rv.getId(), book, false);			
		//db.setGoodreadsSyncDate(rv.getId());
	}

	/**
	 * Create a new book
	 * 
	 * @param db
	 * @param review
	 */
	private void createBook(CatalogueDBAdapter db, Bundle review) {
		Bundle book = buildBundle(db, null, review);
		long id = db.createBook(book);
		if (book.getBoolean(CatalogueDBAdapter.KEY_THUMBNAIL)) {
			File thumb = CatalogueDBAdapter.fetchThumbnail(0);
			File real = CatalogueDBAdapter.fetchThumbnail(id);
			thumb.renameTo(real);			
		}
		//db.setGoodreadsSyncDate(id);
	}

	/**
	 * Build a book bundle based on the goodreads 'review' data. Some data is just copied
	 * while other data is processed (eg. dates) and other are combined (authors & series).
	 * 
	 * @param db
	 * @param rv
	 * @param review
	 * @return
	 */
	private Bundle buildBundle(CatalogueDBAdapter db, BooksRowView rv, Bundle review) {
		Bundle book = new Bundle();

		addStringIfNonBlank(review, FieldNames.DB_TITLE, book, FieldNames.DB_TITLE);
		addStringIfNonBlank(review, FieldNames.DB_DESCRIPTION, book, FieldNames.DB_DESCRIPTION);
		addStringIfNonBlank(review, FieldNames.DB_FORMAT, book, FieldNames.DB_FORMAT);
		addStringIfNonBlank(review, FieldNames.DB_NOTES, book, FieldNames.DB_NOTES);
		addLongIfPresent(review, FieldNames.DB_PAGES, book, FieldNames.DB_PAGES);
		addStringIfNonBlank(review, FieldNames.DB_PUBLISHER, book, FieldNames.DB_PUBLISHER);
		addDoubleIfPresent(review, FieldNames.DB_RATING, book, FieldNames.DB_RATING);
		addStringIfNonBlank(review, FieldNames.DB_READ_END, book, FieldNames.DB_READ_END);
		addStringIfNonBlank(review, FieldNames.DB_READ_START, book, FieldNames.DB_READ_START);
		addStringIfNonBlank(review, FieldNames.DB_TITLE, book, FieldNames.DB_TITLE);
		addLongIfPresent(review, FieldNames.GR_BOOK_ID, book, DOM_GOODREADS_BOOK_ID.name);

		// Find the best (longest) isbn.
		ArrayList<String> isbns = extractIsbns(review);
		if (isbns.size() > 0) {
			String best = isbns.get(0);
			int bestLen = best.length();
			for(int i = 1; i < isbns.size(); i++) {
				String curr = isbns.get(i);
				if (curr.length() > bestLen) {
					best = curr;
					bestLen = best.length();
				}
			}
			if (bestLen > 0) {
				book.putString(CatalogueDBAdapter.KEY_ISBN, best);
			}
		}

        /** Build the pub date based on the components */
        String pubDate = GoodreadsManager.buildDate(review, FieldNames.PUB_YEAR, FieldNames.PUB_MONTH, FieldNames.PUB_DAY, null);
        if (pubDate != null && !pubDate.equals(""))
        	book.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, pubDate);
        
        ArrayList<Bundle> grAuthors = review.getParcelableArrayList(FieldNames.AUTHORS);
        ArrayList<Author> authors;

        if (rv == null) {
        	// It's a new book. Start a clean list.
        	authors = new ArrayList<Author>();
        } else {
        	// it's an update. Get current authors.
        	authors = db.getBookAuthorList(rv.getId());
        }

        for (Bundle grAuthor: grAuthors) {
        	String name = grAuthor.getString(FieldNames.DB_AUTHOR_NAME);
        	if (name != null && !name.trim().equals("")) {
        		authors.add(new Author(name));
        	}
        }
        book.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, authors);

        if (rv == null) {
        	// Use the GR added date for new books
        	addStringIfNonBlank(review, FieldNames.ADDED, book, DOM_ADDED_DATE.name);
        	// Also fetch thumbnail if add
        	String thumbnail;
        	if (review.containsKey(FieldNames.LARGE_IMAGE) && !review.getString(FieldNames.LARGE_IMAGE).toLowerCase().contains("nocover")) {
        		thumbnail = review.getString(FieldNames.LARGE_IMAGE);
        	} else if (review.containsKey(FieldNames.SMALL_IMAGE) && !review.getString(FieldNames.SMALL_IMAGE).toLowerCase().contains("nocover")) {
        		thumbnail = review.getString(FieldNames.SMALL_IMAGE);        		
        	} else {
        		thumbnail = null;
        	}
        	if (thumbnail != null) {
    			String filename = Utils.saveThumbnailFromUrl(thumbnail, "_GR");
    			if (filename.length() > 0)
    				Utils.appendOrAdd(book, "__thumbnail", filename);
    			Utils.cleanupThumbnails(book);        		
        	}
        }

        /**
         * Cleanup the title by removing series name, if present
         */
        if (book.containsKey(CatalogueDBAdapter.KEY_TITLE)) {
			String thisTitle = book.getString(CatalogueDBAdapter.KEY_TITLE);
			Series.SeriesDetails details = Series.findSeries(thisTitle);
			if (details != null && details.name.length() > 0) {
				ArrayList<Series> allSeries;
				if (rv == null)
					allSeries = new ArrayList<Series>();
				else
					allSeries = db.getBookSeriesList(rv.getId());

				allSeries.add(new Series(details.name, details.position));
				book.putString(CatalogueDBAdapter.KEY_TITLE, thisTitle.substring(0, details.startChar-1));

				Utils.pruneSeriesList(allSeries);
		        book.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, allSeries);
			}
        }

        // We need to set BOTH of these fields, otherwise the add/update method will set the
        // last_update_date for us, and that will most likely be set ahead of the GR update date
        Date now = new Date();
        book.putString(DOM_LAST_GOODREADS_SYNC_DATE.name, Utils.toSqlDate(now));
        book.putString(DOM_LAST_UPDATE_DATE.name, Utils.toSqlDateTime(now));

        return book;
	}

	/**
	 * Utility to copy a non-blank string to the book bundle.
	 * 
	 * @param source
	 * @param sourceField
	 * @param dest
	 * @param destField
	 */
	private void addStringIfNonBlank(Bundle source, String sourceField, Bundle dest, String destField) {
		if (source.containsKey(sourceField)) {
			String val = source.getString(sourceField);
			if (val != null && !val.equals(""))
				dest.putString(destField, val);
		}
	}
	/**
	 * Utility to copy a Long value to the book bundle.
	 * 
	 * @param source
	 * @param sourceField
	 * @param dest
	 * @param destField
	 */
	private void addLongIfPresent(Bundle source, String sourceField, Bundle dest, String destField) {
		if (source.containsKey(sourceField)) {
			long val = source.getLong(sourceField);
			dest.putLong(destField, val);
		}
	}
	/**
	 * Utility to copy a Double value to the book bundle.
	 * 
	 * @param source
	 * @param sourceField
	 * @param dest
	 * @param destField
	 */
	private void addDoubleIfPresent(Bundle source, String sourceField, Bundle dest, String destField) {
		if (source.containsKey(sourceField)) {
			double val = source.getDouble(sourceField);
			dest.putDouble(destField, val);
		}
	}

	/**
	 * Make a more informative description
	 */
	@Override 
	public String getDescription() {
		String base = super.getDescription();
		return base + " (" + BookCatalogueApp.getResourceString(R.string.x_of_y, mPosition, mTotalBooks) + ")";
	}

	@Override
	public long getCategory() {
		return BcQueueManager.CAT_GOODREADS_IMPORT_ALL;
	}

	/**
	 * Custom serialization support.
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
	}

	/**
	 * Pseudo-constructor for custom serialization support.
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		mFirstCall = true;
	}
}
