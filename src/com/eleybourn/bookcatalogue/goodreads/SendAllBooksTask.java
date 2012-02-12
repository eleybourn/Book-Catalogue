package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.Logger;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Utils;
import com.eleybourn.bookcatalogue.BookEvents.*;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.ExportDisposition;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.*;

import android.content.Context;
import android.database.Cursor;
import net.philipwarner.taskqueue.QueueManager;

/**
 * Background task class to send all books in the database to goodreads.
 * 
 * @author Grunthos
 */
public class SendAllBooksTask extends GenericTask {
	private static final long serialVersionUID = -1933000305276643875L;

	/** Last book ID processed */
	private long mLastId = 0;

	/** Number of books with no ISBN */
	private int mNoIsbn = 0;
	/** Number of books that had ISBN but could not be found */
	private int mNotFound = 0;
	/** Number of books successfully sent */
	private int mSent = 0;
	/** Total count of books processed */
	private int mCount = 0;
	/** Total count of books that are in cursor */
	private int mTotalBooks = 0;

	/**
	 * Constructor
	 */
	public SendAllBooksTask() {
		super(BookCatalogueApp.getResourceString(R.string.send_all_to_goodreads));
	}

	/**
	 * Run the task, log exceptions.
	 */
	@Override
	public boolean run(QueueManager manager, Context c) {
		boolean result = false;
		try {
			result = sendAllBooks(manager, c);			
		} catch (Exception e) {
			Logger.logError(e, "Error sending books to GoodReads");
		}
		return result;
	}

	/**
	 * Do the mean of the task. Deal with restarts by using mLastId as starting point.
	 * 
	 * @param qmanager
	 * @param context
	 * @return
	 * 
	 * @throws NotAuthorizedException
	 */
	public boolean sendAllBooks(QueueManager qmanager, Context context) throws NotAuthorizedException {
		int lastSave = mCount;
		boolean needsRetryReset = true;

		// ENHANCE: Work out a way of checking if GR site is up
		//if (!Utils.hostIsAvailable(context, "www.goodreads.com"))
		//	return false;

		if (!Utils.isNetworkAvailable(context)) {
			// Only wait 5 mins max on network errors.
			if (getRetryDelay() > 300)
				setRetryDelay(300);
			return false;
		}

		// Get the app context; the underlying activity may go away. And get DB.
		GoodreadsManager grManager = new GoodreadsManager();
		Context ctx = context.getApplicationContext();
		
		CatalogueDBAdapter  dbHelper = new CatalogueDBAdapter(ctx.getApplicationContext());

		// Ensure we are allowed
		if (!grManager.hasValidCredentials()) {
			throw new NotAuthorizedException(null);
		}

		dbHelper.open();
		BooksCursor books = null;
		Cursor shelves = null;

		try {
			books = dbHelper.getAllBooksForGoodreadsCursor(mLastId);
			final BooksRowView book = books.getRowView();
			mTotalBooks = books.getCount() + mCount;

			while (books.moveToNext()) {

				// Try to export one book
				ExportDisposition disposition;
				Exception exportException = null;
				try {
					disposition = grManager.sendOneBook(dbHelper, book);
				} catch (Exception e) {
					disposition = ExportDisposition.error;
					exportException = e;
				}

				// Handle the result
				switch(disposition) {
				case error:
					this.setException(exportException);
					qmanager.saveTask(this);
					return false;
				case sent:
					// Nothing to do
					mSent++;
					break;
				case noIsbn:
					storeEvent( new GrNoIsbnEvent(book.getId()) );
					mNoIsbn++;
					break;
				case notFound:
					storeEvent( new GrNoMatchEvent(book.getId()) );
					mNotFound++;
					break;
				case networkError:
					// Only wait 5 mins on network errors.
					if (getRetryDelay() > 300)
						setRetryDelay(300);						
					qmanager.saveTask(this);
					return false;
				}

				// Update internal status
				mCount++;
				mLastId = books.getId();
				// If we have done one successfully, reset the counter so a subsequent network error does not result in a long delay
				if (needsRetryReset) {
					needsRetryReset = false;
					resetRetryCounter();
				}

				// Save every few rows in case phone dies (and to allow task queries to see data)
				if (mCount - lastSave >= 5) {
					qmanager.saveTask(this);
					lastSave = mCount;
				}
			}

		} finally {
			if (books != null)
				try {
					books.close();
				} catch (Exception e)
				{
					// Ignore failures, but log them
					Logger.logError(e, "Failed to close GoodReads books cursor");
				}
			if (shelves != null)
				try {
					shelves.close();
				} catch (Exception e)
				{
					// Ignore failures, but log them
					Logger.logError(e, "Failed to close GoodReads book bookshelves cursor");
				}
			try {
				dbHelper.close();				
			} catch(Exception e)
			{}
		}

		// Notify the user: '15 books processed: 3 sent successfully, 5 with no ISBN and 7 with ISBN but not found in goodreads'
		String s = context.getString(R.string.send_all_to_goodreads_result, mCount, mSent, mNoIsbn, mNotFound);
		qmanager.showNotification(R.id.NOTIFICATION, 
							context.getString(R.string.send_all_to_goodreads), s, 
							BookCatalogueApp.getAppToForegroundIntent(context));

		return true;
	}

	/**
	 * Make a more informative description
	 */
	@Override 
	public String getDescription() {
		String base = super.getDescription();
		return base + " (" + BookCatalogueApp.getResourceString(R.string.x_of_y, mCount, mTotalBooks);
	}
}
