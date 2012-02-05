package com.eleybourn.bookcatalogue;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import net.philipwarner.taskqueue.QueueManager;

import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.booklist.BooklistCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds;
import com.eleybourn.bookcatalogue.booklist.BooklistRowView;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.SendOneBookTask;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Handles all views in a multi-type ListView showing books, authors, series etc.
 *
 * @author Grunthos
 */
public class BooksMultitypeListHandler implements MultitypeListHandler {
	
	/** Queue for tasks getting extra row details as necessary */
	private static SimpleTaskQueue mInfoQueue = new SimpleTaskQueue("extra-info",1);

	/**
	 * Return the row type for the current cursor position.
	 */
	public int getItemViewType(Cursor c) {
		BooklistRowView rowView = ((BooklistCursor)c).getRowView();
		return rowView.getKind();
	}

	/**
	 * Return the numbver of different View types in this list.
	 */
	public int getViewTypeCount() {
		return ROW_KIND_MAX+1;
	}

	/**
	 * Get the text to display in the FastScroller for the row at the current cursor position.
	 */
	public String getSectionText(Cursor c) {
		BooklistRowView rowView = ((BooklistCursor)c).getRowView();
		return rowView.getLevel1Data();
	}

	/**
	 * Return a 'Holder' object for the row pointed to by rowView.
	 * 
	 * @param rowView	Points to the current row in the cursor.
	 *
	 * @return
	 */
	private BooklistHolder newHolder(BooklistRowView rowView) {
		final int k = rowView.getKind();

		switch(k) {
		case ROW_KIND_BOOK:
			return new BookHolder();
		case ROW_KIND_SERIES:
			return new GenericStringHolder(rowView, DOM_SERIES_NAME, R.string.no_series);
		case ROW_KIND_TITLE_LETTER:
			return new GenericStringHolder(rowView, DOM_TITLE_LETTER, R.string.no_title);
		case ROW_KIND_GENRE:
			return new GenericStringHolder(rowView, DOM_GENRE, R.string.no_genre);
		case ROW_KIND_AUTHOR:
			return new GenericStringHolder(rowView, DOM_AUTHOR_FORMATTED, R.string.no_author);
		case ROW_KIND_PUBLISHER:
			return new GenericStringHolder(rowView, DOM_PUBLISHER, R.string.no_publisher);
		case ROW_KIND_UNREAD:
			return new GenericStringHolder(rowView, DOM_READ_STATUS, R.string.empty_with_brackets);
		case ROW_KIND_LOANED:
			return new GenericStringHolder(rowView, DOM_LOANED_TO, R.string.empty_with_brackets);
		case RowKinds.ROW_KIND_YEAR_PUBLISHED:
			return new GenericStringHolder(rowView, DOM_PUBLICATION_YEAR, R.string.empty_with_brackets);
		case RowKinds.ROW_KIND_MONTH_PUBLISHED:
			return new MonthHolder();
		default:
			throw new RuntimeException("Invalid row kind " + k);
		}
	}

	/**
	 * Return the *absolute* position of the passed view in the list of books.
	 * 
	 * @param v
	 * @return
	 */
	public int getAbsolutePosition(View v) {
		BooklistHolder h = (BooklistHolder)v.getTag(R.id.TAG_HOLDER);
		return h.absolutePosition;		
	}

	/**
	 * Implementation of general code used by Booklist holders.
	 * 
	 * @author Grunthos
	 */
	public abstract static class BooklistHolder extends MultitypeHolder<BooklistRowView> {
		/** Pointer to the container of all info for this row. */
		public View rowInfo;
		/** Absolute position of this row */
		public int absolutePosition;

		/**
		 * Used to get the 'default' layout to use for differing row levels.
		 * 
		 * @param level	Level of layout
		 * 
		 * @return	Layout ID
		 */
		public static final int getDefaultLayoutId(final int level) {
			int id;
			switch(level) {
			case 1:
				id = R.layout.booksonbookshelf_row_level_1;
				break;
			case 2:
				id = R.layout.booksonbookshelf_row_level_2;
				break;
			default:
				id = R.layout.booksonbookshelf_row_level_3;
				break;
			}	
			return id;
		}
		/**
		 * For a simple row, just set the text (or hide it)
		 * 
		 * @param view				View to set
		 * @param s					String to display
		 * @param emptyStringId		String to display if first is empty and can not hide row
		 * @param level				Level of this item (we never hide level 1 items).
		 */
		public void setText(TextView view, String s, int emptyStringId, int level) {
			if (s == null || s.equals("")) {
				if (level > 1 && rowInfo != null) {
						rowInfo.setVisibility(View.GONE);
						return;
				}
				view.setText(BookCatalogueApp.getResourceString(emptyStringId));					
			} else {
				if (rowInfo != null)
					rowInfo.setVisibility(View.VISIBLE);
				view.setText(s);
			}			
		}
	};

	/**
	 * Holder for a BOOK row.
	 * 
	 * @author Grunthos
	 */
	public static class BookHolder extends BooklistHolder {
		/** Pointer to the view that stores the related book field */
		TextView title;
		/** Pointer to the view that stores the related book field */
		TextView shelves;
		/** Pointer to the view that stores the related book field */
		TextView location;
		/** Pointer to the view that stores the related book field */
		TextView publisher;
		/** Pointer to the view that stores the related book field */
		ImageView cover;
		/** Pointer to the view that stores the series number when it is a small piece of text */
		TextView seriesNum;
		/** Pointer to the view that stores the series number when it is a long piece of text */
		TextView seriesNumLong;
		/** Pointer to the view that stores the series number in PORTRAIT mode */
		ImageView read;
		/** The current task to get book extra info for this view. Can be null if none. */
		GetBookExtrasTask extrasTask;

		@Override
		public void map(BooklistRowView rowView, View v) {
			// Find the various views we use.
			title = (TextView) v.findViewById(R.id.title);
			cover = (ImageView) v.findViewById(R.id.cover);
			seriesNum = (TextView) v.findViewById(R.id.series_num);
			seriesNumLong = (TextView) v.findViewById(R.id.series_num_long);				
			read = (ImageView) v.findViewById(R.id.read);
			if (!rowView.hasSeries()) {
				seriesNum.setVisibility(View.GONE);
				seriesNumLong.setVisibility(View.GONE);
			}
			if (rowView.getShowThumbnails())  {
				cover.setVisibility(View.VISIBLE);
				// vvv REMOVED vvv: This speeds up viewing lists, but at the cost of taking up too much real-estate for normal sized book covers.
				//LayoutParams lp = new LayoutParams(rowView.getMaxThumbnailWidth(), rowView.getMaxThumbnailHeight()); 
				//cover.setLayoutParams(lp);
				//cover.setScaleType(ScaleType.CENTER);
				//cover.setMaxHeight(rowView.getMaxThumbnailHeight());
				//cover.setMaxWidth(rowView.getMaxThumbnailWidth());
			} else
				cover.setVisibility(View.GONE);

			shelves = (TextView) v.findViewById(R.id.shelves);
			if (rowView.getShowBookshelves()) {
				shelves.setVisibility(View.VISIBLE);
			} else {
				shelves.setVisibility(View.GONE);
			}

			location = (TextView) v.findViewById(R.id.location);
			if (rowView.getShowLocation()) {
				location.setVisibility(View.VISIBLE);
			} else {
				location.setVisibility(View.GONE);
			}

			publisher = (TextView) v.findViewById(R.id.publisher);
			if (rowView.getShowPublisher()) {
				publisher.setVisibility(View.VISIBLE);
			} else {
				publisher.setVisibility(View.GONE);
			}

			// The default is to indent all views based on the level, but with book covers on
			// the far left, it looks better if we 'outdent' one step.
			int level = rowView.getLevel();
			if (level > 0)
				--level;
			v.setPadding( level*5, 0,0,0);
		}

		@Override
		public void set(BooklistRowView rowView, View v, final int level) {

			// Title
			title.setText(rowView.getTitle());

			// Series details
			if (rowView.hasSeries()) {
				final String seriesNumber = rowView.getSeriesNumber();
				final String seriesName = rowView.getSeriesName();
				if (seriesName == null || seriesName.equals("")) {
					// Hide it.
					seriesNum.setVisibility(View.GONE);
					seriesNumLong.setVisibility(View.GONE);
				} else {
					// Display it in one of the views, based on the size of the text.
					if (seriesNumber.length() > 4) {
						seriesNum.setVisibility(View.GONE);
						seriesNumLong.setVisibility(View.VISIBLE);
						seriesNumLong.setText(seriesNumber);
					} else {
						seriesNum.setVisibility(View.VISIBLE);
						seriesNum.setText(seriesNumber);
						seriesNumLong.setVisibility(View.GONE);
					}
				}
			}

			// READ
			if (rowView.getRead()) {
				read.setVisibility(View.VISIBLE);
				read.setImageResource(R.drawable.btn_check_buttonless_on);
			} else {
				read.setVisibility(View.GONE);
			}

			// Thumbnail
			if (rowView.getShowThumbnails())
				Utils.fetchBookCoverIntoImageView(cover, rowView.getMaxThumbnailWidth(), rowView.getMaxThumbnailHeight(), true, rowView.getBookId(), true, true);

			// Extras
			
			// We are displaying a new row, so delete any existing background task. It is now irrelevant.
			if (extrasTask != null) {
				mInfoQueue.remove(extrasTask);
				extrasTask = null;
			}

			// If there are extras to get, run the background task.
			if (rowView.getShowBookshelves() || rowView.getShowLocation() || rowView.getShowPublisher()) {
				// Fill in the extras field as blank initially.
				shelves.setText("");
				location.setText("");
				publisher.setText("");
				// Queue the task.
				GetBookExtrasTask t = new GetBookExtrasTask(rowView.getBookId(), this);
				mInfoQueue.enqueue(t);
			}
		}

		@Override
		public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
			// All book rows have the same type of view.
			return inflater.inflate(R.layout.booksonbookshelf_row_book, parent, false);
		}
	}

	/**
	 * Background task to get 'extra' details for a book row. Doing this in a background task keeps the booklist cursor
	 * simple and small.
	 * 
	 * @author Grunthos
	 */
	private static class GetBookExtrasTask implements SimpleTask  {
		/** The filled-in view holder for the book view. */
		final BookHolder mHolder;
		/** The book ID to fetch */
		final long mBookId;
		/** Resulting location data */
		String mLocation;
		/** Resulting publisher data */
		String mPublisher;
		/** Resulting shelves data */
		String mShelves;
		/** Flag indicating we want finished() to be called */
		private boolean mWantFinished = true;

		/**
		 * Constructor.
		 * 
		 * @param bookId	Book to fetch
		 * @param holder	View holder of view for the book
		 */
		public GetBookExtrasTask(long bookId, BookHolder holder) {
			mHolder = holder;
			mBookId = bookId;
			synchronized(mHolder) {
				mHolder.extrasTask = this;
			}
		}

		@Override
		public void run() {
			try {
				// Make sure we are the right task.
				synchronized(mHolder) {
					if (mHolder.extrasTask != this) {
						mWantFinished = false;
						return;
					}
				}
				// Get a DB connection and find the book.
				CatalogueDBAdapter dba = new CatalogueDBAdapter(BookCatalogueApp.context);
				dba.open();
				BooksCursor c = dba.fetchBookById(mBookId);
				try {
					// If we have a book, use it. Otherwise we are done.
					if (c.moveToFirst()) {
						mLocation = BookCatalogueApp.getResourceString(R.string.location) + 
										": " + c.getString(c.getColumnIndex(CatalogueDBAdapter.KEY_LOCATION));
						mPublisher = BookCatalogueApp.getResourceString(R.string.publisher) + 
										": " + c.getString(c.getColumnIndex(CatalogueDBAdapter.KEY_PUBLISHER));
						
						// Now build a list of all bookshelves the book is on.
						Cursor sc = dba.getAllBookBookshelvesForGoodreadsCursor(mBookId);
						mShelves = "";
						try {
							if (sc.moveToFirst()) {
								do {
									if (mShelves != null && !mShelves.equals(""))
										mShelves += ", ";
									mShelves += sc.getString(0);
								} while (sc.moveToNext());						
							}
						} finally {
							sc.close();
						}
						mShelves = BookCatalogueApp.getResourceString(R.string.shelves) + ": " + mShelves;
					} else {
						// No data, no need for UI thread call.
						mWantFinished = false;
					}
				} finally {
					c.close();
					dba.close();
				}
			} finally {
				// Not much to see
			}
		}
		/**
		 * Handle the results of the task.
		 */
		@Override
		public void finished() {
			try {
				synchronized(mHolder) {
					if (mHolder.extrasTask != this) {
						return;
					}

					mHolder.shelves.setText(mShelves);
					mHolder.location.setText(mLocation);
					mHolder.publisher.setText(mPublisher);
				}
			} finally {
			}
		}

		@Override
		public boolean runFinished() {
			return mWantFinished;
		}
	}

	/**
	 * Holder to handle any field that can be displayed as a simple string.
	 * Assumes there is a 'name' TextView and an optional enclosing ViewGroup 
	 * called row_info.
	 * 
	 * @author Grunthos
	 */
	public class GenericStringHolder extends BooklistHolder {
		/** Field to use */
		TextView text;
		/** String ID to use when data is blank */
		private int mNoDataId;
		/** Index of related data column */
		private int mColIndex = -1;

		/**
		 * Constructor
		 * 
		 * @param rowView	Row view that represents a typical row of this kind.
		 * 
		 * @param domain		Domain name to use
		 * @param noDataId		String ID to use when data is blank
		 */
		private GenericStringHolder(BooklistRowView rowView, DomainDefinition domain, int noDataId) {
			mColIndex = rowView.getColumnIndex(domain.name);
			mNoDataId = noDataId;
		}

		@Override
		public void map(BooklistRowView rowView, View v) {
			rowInfo = v.findViewById(R.id.row_info);
			text = (TextView) v.findViewById(R.id.name);
		}
		@Override
		public void set(BooklistRowView rowView, View v, final int level) {
			String s = rowView.getString(mColIndex);
			setText(text, s, mNoDataId, level);
		}
		@Override
		public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
			return inflater.inflate(getDefaultLayoutId(level), parent, false);
		}
	}
	
	/**
	 * Holder for a row that displays a 'month'. This code turns a month number into a 
	 * locale-based month name.
	 * 
	 * @author Grunthos
	 */
	public static class MonthHolder extends BooklistHolder {
		/** TextView for month name */
		TextView text;
		/** Calendar to construct dates from month numbers */
		private static Calendar mCalendar = null;
		/** Formatter for month names given dates */
		private static SimpleDateFormat mFormatter = null;

		@Override
		public void map(BooklistRowView rowView, View v) {
			rowInfo = v.findViewById(R.id.row_info);
			text = (TextView) v.findViewById(R.id.name);
		}
		@Override
		public void set(BooklistRowView rowView, View v, final int level) {
			// Get the month and try to format it.
			String s = rowView.getPublicationMonth();
			try {
				int i = Integer.parseInt(s);
				// If valid, get the name
				if (i > 0 && i <= 12) {
					// Create static formatter if necessary
					if (mFormatter == null)
						mFormatter = new SimpleDateFormat("MMMM");
					// Create static calendar if necessary
					if (mCalendar == null)
						mCalendar = Calendar.getInstance();
					// Assumes months are integers and in sequence...which everyone seems to assume
					mCalendar.set(Calendar.MONTH, i - 1 + java.util.Calendar.JANUARY);
					s = mFormatter.format(mCalendar.getTime());
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			// Display whatever text we have
			setText(text, s, R.string.unknown_uc, level);
		}
		@Override
		public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
			return inflater.inflate(getDefaultLayoutId(level), parent, false);
		}
	}

	@Override
	public View getView(Cursor c, LayoutInflater inflater, View convertView, ViewGroup parent) {
		BooklistRowView rowView = ((BooklistCursor)c).getRowView();
		BooklistHolder holder;
		final int level = rowView.getLevel();
		if (convertView == null) {
			holder = newHolder(rowView);
			convertView = holder.newView(rowView, inflater, parent, level);
			convertView.setPadding( (level-1)*5, 0,0,0);
			holder.map(rowView, convertView);
			convertView.setTag(R.id.TAG_HOLDER, holder);
			// Indent based on level; we assume rows of a given type only occur at the same level
		} else 
			holder = (BooklistHolder)convertView.getTag(R.id.TAG_HOLDER);

		holder.absolutePosition = rowView.getAbsolutePosition();
		holder.set(rowView, convertView, level);

		return convertView;
	}

	/**
	 * Utility routine to add an item to a ContextMenu object.
	 * 
	 * @param menu		Parent menu
	 * @param id		unique item ID
	 * @param stringId	string ID of string to display
	 * @param iconId	icon of menu item
	 */
	private void addMenuItem(ContextMenu menu, int id, int stringId, int iconId) {
		// Add the menu
		MenuItem item = menu.add(0, id, 0, stringId);
		// Set the icon
		item.setIcon(iconId);
	}

	/**
	 * Utility routine to add 'standard' menu options based on row type.
	 * 
	 * @param rowView		Row view pointing to current row for this context menu
	 * @param menu			Base menu item
	 * @param v				View that was clicked
	 * @param menuInfo		menuInfo object from Adapter (not really needed since we have holders and cursor)
	 */
	public void onCreateContextMenu(BooklistRowView rowView, ContextMenu menu, View v, AdapterView.AdapterContextMenuInfo menuInfo) {
		try {
			switch(rowView.getKind()) {
			case ROW_KIND_BOOK:
			{
				addMenuItem(menu, R.id.MENU_DELETE_BOOK, R.string.menu_delete, android.R.drawable.ic_menu_delete);
				addMenuItem(menu, R.id.MENU_EDIT_BOOK, R.string.edit_book, android.R.drawable.ic_menu_edit);
				addMenuItem(menu, R.id.MENU_EDIT_BOOK_NOTES, R.string.edit_book_notes, R.drawable.ic_menu_compose);
				addMenuItem(menu, R.id.MENU_EDIT_BOOK_FRIENDS, R.string.edit_book_friends, R.drawable.ic_menu_cc);
				addMenuItem(menu, R.id.MENU_SEND_BOOK_TO_GR, R.string.edit_book_send_to_gr, R.drawable.ic_menu_cc);
				break;
			}
			case ROW_KIND_AUTHOR:
			{
				addMenuItem(menu, R.id.MENU_EDIT_AUTHOR, R.string.menu_edit_author, android.R.drawable.ic_menu_edit);
				break;
			}
			case ROW_KIND_SERIES:
			{
				addMenuItem(menu, R.id.MENU_DELETE_SERIES, R.string.menu_delete_series, android.R.drawable.ic_menu_delete);
				addMenuItem(menu, R.id.MENU_EDIT_SERIES, R.string.menu_edit_series, android.R.drawable.ic_menu_edit);
				break;
			}
			}
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	public interface BooklistChangeListener {
		public static final int FLAG_AUTHOR = 1;
		public static final int FLAG_SERIES = 2;
		void onBooklistChange(int flags);
	}

	/**
	 * Handle the 'standard' menu items. If the passed activity implements BooklistChangeListener then
	 * inform it when changes have been made.
	 * 
	 * TODO: Consider using LocalBroadcastManager instead.
	 * 
	 * @param rowView	Row view for affected cursor row
	 * @param context	Calling Activity
	 * @param dba		Database helper
	 * @param item		Related MenuItem
	 * 
	 * @return			True, if handled.
	 */
	public boolean onContextItemSelected(BooklistRowView rowView, final Activity context, final CatalogueDBAdapter dba, final MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		switch(item.getItemId()) {

		case R.id.MENU_DELETE_BOOK:
			// Show the standard dialog
			int res = StandardDialogs.deleteBookAlert(context, dba, rowView.getBookId(), new Runnable() {
				@Override
				public void run() {
					dba.purgeAuthors();
					dba.purgeSeries();
					// Let the activity know
					if (context instanceof BooklistChangeListener) {
						final BooklistChangeListener l = (BooklistChangeListener) context;
						l.onBooklistChange(BooklistChangeListener.FLAG_AUTHOR | BooklistChangeListener.FLAG_SERIES);
					}
				}});
			// Display an error, if any
			if (res != 0) 
				Toast.makeText(context, res, Toast.LENGTH_LONG).show();
			return true;

		case R.id.MENU_EDIT_BOOK:
			// Start the activity in the correct tab
			BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT);
			return true;

		case R.id.MENU_EDIT_BOOK_NOTES:
			// Start the activity in the correct tab
			BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT_NOTES);
			return true;

		case R.id.MENU_EDIT_BOOK_FRIENDS:
			// Start the activity in the correct tab
			BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT_FRIENDS);
			return true;

		case R.id.MENU_SEND_BOOK_TO_GR:
			// Get a GoodreadsManager and make sure we are authorized.
			GoodreadsManager grMgr = new GoodreadsManager();
			if (!grMgr.hasValidCredentials()) {
				try {
					grMgr.requestAuthorization(context);
				} catch (NetworkException e) {
					Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				}
			}
			// get a QueueManager and queue the task.
			QueueManager qm = BookCatalogueApp.getQueueManager();
			SendOneBookTask task = new SendOneBookTask(info.id);
			qm.enqueueTask(task, BcQueueManager.QUEUE_MAIN, 0);
			return true;

		case R.id.MENU_EDIT_SERIES:
			{
				long id = rowView.getSeriesId();
				if (id==-1) {
					Toast.makeText(context, R.string.cannot_edit_system, Toast.LENGTH_LONG).show();
				} else {
					Series s = dba.getSeriesById(id);
					EditSeriesDialog d = new EditSeriesDialog(context, dba, new Runnable() {
						@Override
						public void run() {
							dba.purgeSeries();
							// Let the Activity know
							if (context instanceof BooklistChangeListener) {
								final BooklistChangeListener l = (BooklistChangeListener) context;
								l.onBooklistChange(BooklistChangeListener.FLAG_SERIES);
							}
						}});
					d.editSeries(s);
				} 
				break;
			}
		case R.id.MENU_DELETE_SERIES:
			{
				long id = rowView.getSeriesId();
				StandardDialogs.deleteSeriesAlert(context, dba, dba.getSeriesById(id), new Runnable() {
					@Override
					public void run() {
						// Let the Activity know
						if (context instanceof BooklistChangeListener) {
							final BooklistChangeListener l = (BooklistChangeListener) context;
							l.onBooklistChange(BooklistChangeListener.FLAG_SERIES);
						}
					}});
				break;			
			}
		case R.id.MENU_EDIT_AUTHOR:
			{
				long id = rowView.getAuthorId();
				EditAuthorDialog d = new EditAuthorDialog(context, dba, new Runnable() {
					@Override
					public void run() {
						dba.purgeAuthors();
						// Let the Activity know
						if (context instanceof BooklistChangeListener) {
							final BooklistChangeListener l = (BooklistChangeListener) context;
							l.onBooklistChange(BooklistChangeListener.FLAG_AUTHOR);
						}
					}});
				d.editAuthor(dba.getAuthorById(id));
				break;
			}
		}
		return false;
	}

}
