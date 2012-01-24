package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsSearchCriteria;
import com.eleybourn.bookcatalogue.goodreads.SendOneBookTask;

import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.Event;
import net.philipwarner.taskqueue.EventsCursor;
import net.philipwarner.taskqueue.QueueManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDoneException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * Class to define all book-related events that may be stored in the QueueManager.
 * 
 * *************!!!!!!!!!!!!*************!!!!!!!!!!!!*************!!!!!!!!!!!!*************
 * NOTE: If internal data or data types need to change in this class after the application
 * has been deployed, CREATE A NEW CLASS OR EVENT. Otherwise, deployed serialized objects
 * may fail to reload. This is not a big issue (it's what LegacyTask and LegacyEvent are
 * for), but it is better to present data to the users cleanly.
 * *************!!!!!!!!!!!!*************!!!!!!!!!!!!*************!!!!!!!!!!!!*************
 * 
 * @author Grunthos
 */
public class BookEvents {
	/**
	 * Base class for all book-related events. Has a book ID and is capable of 
	 * displaying basic book data in a View.
	 * 
	 * @author Grunthos
	 */
	public static class BookEvent extends Event {
		private static final long serialVersionUID = 74746691665235897L;

		protected long m_bookId;

		/**
		 * Constructor
		 * 
		 * @param bookId		ID of related book.
		 * @param description	Description of this event.
		 */
		public BookEvent(long bookId, String description) {
			super(description);
			m_bookId = bookId;
		}

		/**
		 * Constructor
		 * 
		 * @param bookId		ID of related book.
		 * @param description	Description of this event.
		 * @param e				Exception related to this event.
		 */
		public BookEvent(long bookId, String description, Exception e) {
			super(description, e);
			m_bookId = bookId;
		}

		/**
		 * Get the related Book ID.
		 *
		 * @return	ID of related book.
		 */
		public long getBookId() {
			return m_bookId;			
		}

		/**
		 * Class to implement the 'holder' model for view we create.
		 *
		 * @author Grunthos
		 */
		protected class BookEventHolder {
			long rowId;
			BookEvent event;
			TextView title;
			TextView author;
			TextView error;
			TextView date;
			Button retry;
			CheckBox checkbox;
		}

		/**
		 * Return a view capable of displaying basic book event details, ideally usable by all BookEvent subclasses.
		 * This method also prepares the BookEventHolder object for the View.
		 */
		@Override
		public View newListItemView(LayoutInflater inflater, Context context, BindableItemSQLiteCursor cursor, ViewGroup parent) {
			View view = inflater.inflate(R.layout.book_event_info, parent, false);
			view.setTag(R.id.TAG_EVENT, this);
			BookEventHolder holder = new BookEventHolder();
			holder.event = this;
			holder.rowId = cursor.getId();

			holder.author = (TextView)view.findViewById(com.eleybourn.bookcatalogue.R.id.author);
			holder.checkbox = (CheckBox)view.findViewById(com.eleybourn.bookcatalogue.R.id.checked);
			holder.date = (TextView)view.findViewById(com.eleybourn.bookcatalogue.R.id.date);
			holder.error = (TextView)view.findViewById(com.eleybourn.bookcatalogue.R.id.error);
			holder.retry = (Button)view.findViewById(com.eleybourn.bookcatalogue.R.id.retry);
			holder.title = ((TextView)view.findViewById(com.eleybourn.bookcatalogue.R.id.title));

			view.setTag(R.id.TAG_BOOK_EVENT_HOLDER, holder);
			holder.checkbox.setTag(R.id.TAG_BOOK_EVENT_HOLDER, holder);
			holder.retry.setTag(R.id.TAG_BOOK_EVENT_HOLDER, holder);

			return view;
		}

		/**
		 * Display the related book details in the passed View object.
		 */
		@Override
		public boolean bindView(View view, Context context, BindableItemSQLiteCursor bindableCursor, Object appInfo) {
			final EventsCursor cursor = (EventsCursor)bindableCursor;
			BookEventHolder holder = (BookEventHolder)view.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
			CatalogueDBAdapter db = (CatalogueDBAdapter)appInfo;

			// Update event info binding; the Views in the holder are unchanged, but when it is reused
			// the Event and ID will change.
			holder.event = this;
			holder.rowId = cursor.getId();

			ArrayList<Author> authors = db.getBookAuthorList(m_bookId);
			String author;
			if (authors.size() > 0) {
				author = authors.get(0).getDisplayName();
				if (authors.size() > 1)
					author = author + " et. al.";
			} else {
				author = context.getString(R.string.unknown_uc);
			}

			String title;
			try {
				title = db.getBookTitle(m_bookId);
			} catch (SQLiteDoneException e) {
				title = context.getString(R.string.this_book_deleted_uc);
			}
			holder.title.setText(title);

			
			holder.author.setText(Utils.format(context, R.string.by, author));

			Exception e = this.getException();
			if (e == null) {
				holder.error.setText(this.getDescription());
			} else {
				holder.error.setText(e.getMessage());
			}

			String date = "(" + Utils.format(context, R.string.occurred_at, cursor.getEventDate().toLocaleString()) + ")";
			holder.date.setText(date);

			holder.retry.setVisibility(View.GONE);

			holder.checkbox.setChecked(cursor.getIsSelected());
			holder.checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					BookEventHolder holder = (BookEventHolder)buttonView.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
					cursor.setIsSelected(holder.rowId,isChecked);
				}});

			return true;
		}

		/**
		 * Add ContextDialogItems relevant for the specific book the selected View is associated with.
		 * Subclass can override this and add items at end/start or just replace these completely.
		 */
		@Override
		public void addContextMenuItems(final Context ctx, AdapterView<?> parent, final View v, final int position, final long id, ArrayList<ContextDialogItem> items, final Object appInfo) {

			// EDIT BOOK
			items.add(new ContextDialogItem(ctx.getString(R.string.edit_book), new Runnable() {
				@Override
				public void run() {
					try {
						GrSendBookEvent event = (GrSendBookEvent) v.getTag(R.id.TAG_EVENT);
						editBook(ctx, event.getBookId());
					} catch (Exception e) {
						// not a book event?
					}
				}}));
			// SEARCH GOODREADS
			items.add(new ContextDialogItem(ctx.getString(R.string.visit_goodreads), new Runnable() {
				@Override
				public void run() {
					BookEventHolder holder = (BookEventHolder)v.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
					Intent i = new Intent(ctx, GoodreadsSearchCriteria.class);
					i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					i.putExtra(GoodreadsSearchCriteria.EXTRA_BOOK_ID, holder.event.getBookId());
					ctx.startActivity(i);
				}}));
			// DELETE EVENT
			items.add(new ContextDialogItem(ctx.getString(R.string.delete_entry), new Runnable() {
				@Override
				public void run() {
					BookCatalogueApp.getQueueManager().deleteEvent(id);
				}}));
		}

	};

	/**
	 * Subclass of BookEvent that is the base class for all Event objects resulting from 
	 * sending books to goodreads.
	 * 
	 * @author Grunthos
	 */
	public static class GrSendBookEvent extends BookEvent {
		private static final long serialVersionUID = 1L;

		public GrSendBookEvent(long bookId, String message) {
			super(bookId, message);
		}

		public GrSendBookEvent(long bookId, String message, Exception e) {
			super(bookId, message, e);
		}

		/**
		 * Resubmit this book and delete this event.
		 */
		public void retry() {
			QueueManager qm = BookCatalogueApp.getQueueManager();
			SendOneBookTask task = new SendOneBookTask(m_bookId);
			// TODO: MAKE IT USE THE SAME QUEUE
			qm.enqueueTask(task, BcQueueManager.QUEUE_SMALL_JOBS, 0);
			qm.deleteEvent(this.getId());
		}

		/**
		 * Override to allow modification of view.
		 */
		@Override
		public boolean bindView(View view, Context context, final BindableItemSQLiteCursor cursor, Object appInfo) {
			// Get the 'standard' view.
			super.bindView(view, context, cursor, appInfo);

			// get book details
			final BookEventHolder holder = (BookEventHolder)view.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
			final CatalogueDBAdapter db = (CatalogueDBAdapter)appInfo;
			final BooksCursor booksCursor = db.getBookForGoodreadsCursor(m_bookId);
			final BooksRowView book = booksCursor.getRowView();
			try {
				// Hide parts of view based on current book details.
				if (booksCursor.moveToFirst()) {
					if (book.getIsbn().equals("")) {
						holder.retry.setVisibility(View.GONE);
					} else {
						holder.retry.setVisibility(View.VISIBLE);
						holder.retry.setTag(this);
						holder.retry.setOnClickListener(m_retryButtonListener);
					}
				} else {
					holder.retry.setVisibility(View.GONE);
				}				
			} finally {
				// Always close
				if (booksCursor != null)
					booksCursor.close();				
			}

			return true;
		}

		/**
		 * Override to allow a new context menu item.
		 */
		@Override
		public void addContextMenuItems(final Context ctx, AdapterView<?> parent, final View v, final int position, final long id, ArrayList<ContextDialogItem> items, Object appInfo) {
			super.addContextMenuItems(ctx, parent, v, position, id, items, appInfo);

			final CatalogueDBAdapter db = (CatalogueDBAdapter)appInfo;
			final BooksCursor booksCursor = db.getBookForGoodreadsCursor(m_bookId);
			try {
				final BooksRowView book = booksCursor.getRowView();
				if (booksCursor.moveToFirst()) {
					if (! (book.getIsbn().equals(""))) {
						items.add(new ContextDialogItem(ctx.getString(R.string.retry_task), new Runnable() {
							@Override
							public void run() {
								try {
									GrSendBookEvent event = (GrSendBookEvent) v.getTag(R.id.TAG_EVENT);
									event.retry();
									QueueManager.getQueueManager().deleteEvent(id);
								} catch (Exception e) {
									// not a book event?
								}
							}}));				
					}
				}				
			} finally {				
				booksCursor.close();
			}
		}
	};

	/**
	 * Method to retry sending a book to goodreads.
	 */
	private static OnClickListener m_retryButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			BookEvent.BookEventHolder holder = (BookEvent.BookEventHolder)v.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
			((GrSendBookEvent)holder.event).retry();
		}
	};

	/**
	 * Method to edit a book details.
	 */
	private static void editBook(Context ctx, long bookId) {
		Intent i = new Intent(ctx, BookEdit.class);
		i.putExtra(CatalogueDBAdapter.KEY_ROWID, bookId);
		i.putExtra(BookEdit.TAB, BookEdit.TAB_EDIT);
		ctx.startActivity(i);	
	}

	/*****************************************************************************************************
	 * 
	 * 'General' purpose exception class
	 * 
	 * @author Grunthos
	 */
	public static class GrGeneralBookEvent extends GrSendBookEvent {
		private static final long serialVersionUID = -7684121345325648066L;

		public GrGeneralBookEvent(long bookId, Exception e, String message) {
			super(bookId, message, e);
		}
	};

	/**
	 * Exception indicating the book's ISBN could not be found at GoodReads
	 * 
	 * @author Grunthos
	 *
	 */
	public static class GrNoMatchEvent extends GrSendBookEvent {
		private static final long serialVersionUID = -7684121345325648066L;

		public GrNoMatchEvent(long bookId) {
			super(bookId, BookCatalogueApp.getResourceString(R.string.no_matching_book_found));
		}
	};

	/**
	 * Exception indicating the book's ISBN was blank
	 * 
	 * @author Grunthos
	 *
	 */
	public static class GrNoIsbnEvent extends GrSendBookEvent {
		private static final long serialVersionUID = 7260496259505914311L;

		public GrNoIsbnEvent(long bookId) {
			super(bookId, BookCatalogueApp.getResourceString(R.string.no_isbn_stored_for_book));
		}		

	}
}
