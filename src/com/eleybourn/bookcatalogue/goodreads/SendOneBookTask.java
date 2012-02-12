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
 * Task to send a single books details to goodreads.
 * 
 * @author Grunthos
 */
public class SendOneBookTask extends GenericTask {
	private static final long serialVersionUID = 8585857100291691934L;

	/** ID of book to send */
	private long m_bookId = 0;

	/**
	 * Constructor. Save book ID.
	 * 
	 * @param bookId		Book to send
	 */
	public SendOneBookTask(long bookId) {
		super(BookCatalogueApp.getResourceString(R.string.send_book_to_goodreads, bookId));
		m_bookId = bookId;
	}

	/**
	 * Run the task, log exceptions.
	 */
	@Override
	public boolean run(QueueManager manager, Context c) {
		boolean result = false;
		try {
			result = sendBook(manager, c);			
		} catch (Exception e) {
			Logger.logError(e, "Error sending books to GoodReads");
		}
		return result;
	}

	/**
	 * Perform the main task
	 * 
	 * @param qmanager
	 * @param context
	 * @return
	 * @throws NotAuthorizedException
	 */
	public boolean sendBook(QueueManager qmanager, Context context) throws NotAuthorizedException {
		
		// ENHANCE: Work out a way of checking if GR site is up
		//if (!Utils.hostIsAvailable(context, "www.goodreads.com"))
		//	return false;

		if (!Utils.isNetworkAvailable(context)) {
			// Only wait 5 mins on network errors.
			if (getRetryDelay() > 300)
				setRetryDelay(300);
			return false;
		}

		// Get the goodreads manager and app context; the underlying activity may go away. Also get DB
		GoodreadsManager grManager = new GoodreadsManager();

		if (!grManager.hasValidCredentials()) {
			throw new NotAuthorizedException(null);
		}

		Context ctx = context.getApplicationContext();
		CatalogueDBAdapter  dbHelper = new CatalogueDBAdapter(ctx);
		dbHelper.open();

		// Open the cursor for the book
		final BooksCursor books = dbHelper.getBookForGoodreadsCursor(m_bookId);
		final BooksRowView book = books.getRowView();
		Cursor shelves = null;

		try {
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
					break;
				case noIsbn:
					storeEvent(new GrNoIsbnEvent(books.getId()));
					break;
				case notFound:
					storeEvent( new GrNoMatchEvent(books.getId()) );
					break;
				case networkError:
					// Only wait 5 mins on network errors.
					if (getRetryDelay() > 300)
						setRetryDelay(300);						
					qmanager.saveTask(this);
					return false;
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
		return true;
	}

}
