package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class for representing read-only book details.
 * @author n.silin
 */
public class BookDetailsReadOnly extends BookDetailsAbstract {
	private FlattenedBooklist mList = null;
	private GestureDetector mGestureDetector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		setContentView(R.layout.book_details);
		setRowIdByExtras();
		/* In superclass onCreate method we initialize fields, background,
		 * display metrics and other. So see superclass onCreate method. */
		super.onCreate(savedInstanceState);
		// Set additional (non book details) fields before their populating
		addFields();
		
		/* 
		 * We have to override this value to initialize book thumb with right size.
		 * You have to see in book_details.xml to get dividing coefficient
		 */
		mThumbEditSize = Math.min(mMetrics.widthPixels, mMetrics.heightPixels) / 3;
		
		// See if the name of a booklist table was passed to us
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String list = extras.getString("FlattenedBooklist");
			if (list != null && !list.equals("")) {
				mList = new FlattenedBooklist(mDbHelper.getDb(), list);
				// Check to see it really exists. The underlying table disappeared once in testing
				// which is hard to explain; it theoretically should only happen if the app closes
				// the database or if the activity pauses with 'isFinishing()' returning true.
				if (mList.exists()) {
					if (extras.containsKey("FlattenedBooklistPosition")) {
						mList.moveTo(extras.getInt("FlattenedBooklistPosition"));					
					}
					// Add a gesture lister for 'swipe' gestures
					mGestureDetector = new GestureDetector(this, mGestureListener);
				} else {
					mList.close();
					mList = null;
				}
			}
		}

		if (mRowId != null && mRowId > 0) {
			updateFields();
		}

		if (savedInstanceState == null)
			HintManager.displayHint(this, R.string.hint_view_only_help, null);
	}
	
	@Override
	/**
	 * Close the list object (frees statments) and if we are finishing, delete the temp table
	 */
	protected void onPause() {
		if (mList != null) {
			mList.close();
			if (this.isFinishing()) {
				mList.deleteData();
			}
		}
		super.onPause();
	}

	/**
	 * If 'back' is pressed, and the user has made changes, ask them if they really want to lose the changes.
	 * 
	 * We don't use onBackPressed because it does not work with API level 4.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Intent i = new Intent();
			i.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
			if (getParent() == null) {
				setResult(RESULT_OK, i);
			} else {
				getParent().setResult(RESULT_OK, i);
			}
			finish();
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	/**
	 * This is a straight passthrough
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch (requestCode) {
			case UniqueId.ACTIVITY_EDIT_BOOK:
				// Update fields of read-only book after editing
				if (resultCode == Activity.RESULT_OK) {
					updateFields();
				}
				break;
		}
	}
	
	@Override
	/* The only difference from super class method is initializing of additional
	 * fields needed for read-only mode (user notes, loaned, etc.) */
	protected void populateFieldsFromDb(Long rowId) {
		// From the database (edit)
		BooksCursor books = mDbHelper.fetchBookById(rowId);
		try {
			if (books != null) {
				books.moveToFirst();
			}
			BooksRowView book = books.getRowView();

			populateBookDetailsFields(rowId, books);
			// Set maximum aspect ratio width : height = 1 : 2
			setBookThumbnail(rowId, mThumbEditSize, mThumbEditSize * 2);
			
			// Additional fields for read-only mode which are not initialized automatically
			showReadStatus(book);
			showLoanedInfo(rowId);
			showSignedStatus(book);
			formatFormatSection();
			formatPublishingSection();
			
			// Restore defaut visibilty and hide unused/unwanted fields
			showHideFields();

			String title = mFields.getField(R.id.title).getValue().toString();
			// If we are in a list, then append the position
			if (mList != null) {
				// RELEASE: Stringify!!!!
				String info = mList.getAbsolutePosition() + " of " + mList.getCount();
				title += " (" + info + ")";
			}
			setActivityTitle(title);
		} catch (Exception e) {
			Logger.logError(e);
		} finally {
			if (books != null)
				books.close();
		}

		// Populate bookshelves and hide the field if bookshelves are not set.
		if (!populateBookshelvesField(mFields, rowId)) {
			findViewById(R.id.lbl_bookshelves).setVisibility(View.GONE);
			findViewById(R.id.bookshelf_text).setVisibility(View.GONE);
		}

		// Get author and series lists
		mAuthorList = mDbHelper.getBookAuthorList(rowId);
		mSeriesList = mDbHelper.getBookSeriesList(rowId);
	}

	@Override
	/* Override populating author field. Hide the field if author not set or
	 * shows author (or authors through ',') with 'by' at the beginning. */
	protected void populateAuthorListField() {
		int authorsCount = mAuthorList.size();
		if (authorsCount == 0) {
			// Hide author field if it is not set
			findViewById(R.id.author).setVisibility(View.GONE);
		} else {
			StringBuilder builder = new StringBuilder();
			builder.append(getResources().getString(R.string.book_details_readonly_by));
			builder.append(" ");
			for (int i = 0; i < authorsCount; i++) {
				builder.append(mAuthorList.get(i).getDisplayName());
				if (i != authorsCount - 1) {
					builder.append(", ");
				}
			}
			mFields.getField(R.id.author).setValue(builder.toString());
		}
	}

	@Override
	protected void populateSeriesListField() {
		int size = 0;
		try {
			size = mSeriesList.size();
		} catch (NullPointerException e) {
			size = 0;
		}
		if (size == 0 || !mFields.getField(R.id.series).visible) {
			// Hide 'Series' label and data
			findViewById(R.id.lbl_series).setVisibility(View.GONE);
			findViewById(R.id.series).setVisibility(View.GONE);
			return;
		} else {
			// Show 'Series' label and data
			findViewById(R.id.lbl_series).setVisibility(View.VISIBLE);
			findViewById(R.id.series).setVisibility(View.VISIBLE);

			String newText = null;
			Utils.pruneSeriesList(mSeriesList);
			Utils.pruneList(mDbHelper, mSeriesList);
			int seriesCount = mSeriesList.size();
			if (seriesCount > 0) {
				StringBuilder builder = new StringBuilder();
				for (int i = 0; i < seriesCount; i++) {
					builder.append("    " + mSeriesList.get(i).getDisplayName());
					if (i != seriesCount - 1) {
						builder.append("<br/>");
					}
				}
				newText = builder.toString();
			}
			mFields.getField(R.id.series).setValue(newText);
		}
	}

	/**
	 * Add other fields of book to details fields. We need this method to automatically
	 * populate some fields during populating. See {@link #populateFieldsFromDb(Long)}.
	 * Note that it should be performed before populating.
	 */
	private void addFields() {
		// From 'My comments' tab
		mFields.add(R.id.rating, CatalogueDBAdapter.KEY_RATING, new Fields.FloatValidator());
		mFields.add(R.id.notes, CatalogueDBAdapter.KEY_NOTES, null);
		mFields.add(R.id.read_start, CatalogueDBAdapter.KEY_READ_START, null, new Fields.DateFieldFormatter());
		mFields.add(R.id.read_end, CatalogueDBAdapter.KEY_READ_END, null, new Fields.DateFieldFormatter());
		mFields.add(R.id.location, CatalogueDBAdapter.KEY_LOCATION, null);
		// Make sure the label is hidden when the ISBN is 
		mFields.add(R.id.isbn_label, "", CatalogueDBAdapter.KEY_ISBN, null);
	}
	
	/**
	 * Formats 'format' section of the book depending on values
	 * of 'pages' and 'format' fields.
	 */
	private void formatFormatSection(){
		Field field = mFields.getField(R.id.pages);
		String value = (String) field.getValue();
		boolean isExist = value != null && !value.equals("");
		if (isExist) { //If 'pages' field is set format it
			field.setValue(getString(R.string.book_details_readonly_pages, value));
		}
		// Format 'format' field
		field = mFields.getField(R.id.format);
		value = (String) field.getValue();
		if(isExist && value != null && !value.equals("")){
			/* Surround 'format' field with braces if 'pages' field is set 
			 * and 'format' field is not empty */
			field.setValue(getString(R.string.brackets, value));
		}
	}
	
	/**
	 * Formats 'Publishing' section of the book depending on values
	 * of 'publisher' and 'date published' fields.
	 */
	private void formatPublishingSection(){
		Field field = mFields.getField(R.id.date_published);
		String value = (String) field.getValue();
		boolean isDateExist = value != null && !value.equals("");

		// Format 'publisher' field
		field = mFields.getField(R.id.publisher);
		value = (String) field.getValue();
		if(isDateExist && value != null && !value.equals("")){
			// Add comma to the end
			field.setValue(value + ", ");
		}
	}
	
	/**
	 * Inflates 'Loaned' field showing a person the book loaned to.
	 * If book is not loaned field is invisible.
	 * @param rowId Database row _id of the loaned book
	 */
	private void showLoanedInfo(Long rowId) {
		String personLoanedTo = mDbHelper.fetchLoanByBook(rowId);
		if (personLoanedTo != null) {
			TextView textView = (TextView) findViewById(R.id.who);
			textView.setVisibility(View.VISIBLE);
			String resultText = getString(R.string.book_details_readonly_loaned_to, personLoanedTo);
			textView.setText(resultText);
		}
	}

	/**
	 * Sets read status of the book if needed. Shows green tick if book is read.
	 * @param book Cursor containing information of the book from database
	 */
	private void showReadStatus(BooksRowView book) {
		if (FieldVisibility.isVisible(CatalogueDBAdapter.KEY_READ)) {
			ImageView image = (ImageView) findViewById(R.id.read);
			if (book.isRead()) {
				image.setVisibility(View.VISIBLE);
				image.setImageResource(R.drawable.btn_check_buttonless_on);
			} else {
				image.setVisibility(View.GONE);				
			}
		} else {
			ImageView image = (ImageView) findViewById(R.id.read);
			image.setVisibility(View.GONE);			
		}
	}

	/**
	 * Show signed status of the book. Set text 'yes' if signed. Otherwise it is 'No'.
	 * @param book Cursor containing information of the book from database
	 */
	private void showSignedStatus(BooksRowView book) {
		if (book.isSigned()) {
			((TextView) findViewById(R.id.signed)).setText(getResources().getString(R.string.yes));
		}
	}
	
	/**
	 * Hides unused fields if they have not any useful data. Checks all text fields
	 * except of author, series and loaned. 
	 */
	private void showHideFields() {
		// Restore the default based on user preferences.
		mFields.resetVisibility();
	
		// Check publishing information
		if (showHideFieldIfEmpty(R.id.publisher) == View.GONE && showHideFieldIfEmpty(R.id.date_published) == View.GONE) {
			findViewById(R.id.lbl_publishing).setVisibility(View.GONE);
		}

		boolean hasImage = findViewById(R.id.row_img).getVisibility() != View.GONE;
		if (!hasImage) {
			findViewById(R.id.image_wrapper).setVisibility(View.GONE);						
		}

		// Check format information
		boolean hasPages = (showHideFieldIfEmpty(R.id.pages) == View.VISIBLE);
		if (!hasPages) {
			findViewById(R.id.pages).setVisibility(View.GONE);			
		}
		showHideFieldIfEmpty(R.id.format);

		// Check genre
		showHideFieldIfEmpty(R.id.genre, R.id.lbl_genre);

		// Check ISBN
		showHideFieldIfEmpty(R.id.isbn, R.id.row_isbn);

		// Check list price
		showHideFieldIfEmpty(R.id.list_price, R.id.row_list_price);

		// Check description
		showHideFieldIfEmpty(R.id.description, R.id.descriptionLabel, R.id.description_divider);

		// **** MY COMMENTS SECTION ****
		// Check notes
		showHideFieldIfEmpty(R.id.notes, R.id.lbl_notes);

		// Check date start reading
		showHideFieldIfEmpty(R.id.read_start, R.id.row_read_start);

		// Check date end reading
		showHideFieldIfEmpty(R.id.read_end, R.id.row_read_end);

		// Check location
		showHideFieldIfEmpty(R.id.location, R.id.row_location);

		// Hide the fields that we never use...
		findViewById(R.id.anthology).setVisibility(View.GONE);
	}
	
	/**
	 * Show or Hide text field if it has not any useful data.
	 * Don't show a field if it is already hidden (assumed by user preference)
	 *
	 * @param resId layout resource id of the field
	 * @param relatedFields list of fields whose visibility will also be set based on the first field
	 *
	 * @return The resulting visibility setting value (VISIBLE or GONE)
	 */
	private int showHideFieldIfEmpty(int resId, int...relatedFields) {
		// Get the base view
		final View v = findViewById(resId);
		if (v.getVisibility() != View.GONE) {
			// Determine if we should hide it
			String value = (String) mFields.getField(resId).getValue();
			boolean isExist = value != null && !value.equals("");
			int visibility = isExist ? View.VISIBLE : View.GONE;
			v.setVisibility(visibility);
			// Set the related views
			for(int i: relatedFields) {
				View rv = findViewById(i);
				if (rv != null)
					rv.setVisibility(visibility);
			}
			return visibility;
		} else {
			return View.GONE;
		}
	}
	
	/**
	 * Updates all fields of book from database.
	 */
	private void updateFields(){
		populateFieldsFromDb(mRowId);
		// Populate author and series fields
		populateAuthorListField();
		populateSeriesListField();
	}

	@Override
	/**
	 * We override the dispatcher because the ScrollView will consume
 	 * all events otherwise.
	 */
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (mGestureDetector != null && mGestureDetector.onTouchEvent(event))
			return true;

		super.dispatchTouchEvent(event);
		return true;
	}

	/**
	 * Listener to handle 'fling' events; we could handle others but need to be careful about possible clicks and scrolling.
	 */
	GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			if (mList == null)
				return false;

			// Make sure we have considerably more X-velocity than Y-velocity; otherwise it might be a scroll.
			if (Math.abs(velocityX / velocityY) > 2) {
				boolean moved;
				// Work out which way to move, and do it.
				if (velocityX > 0) {
					moved = mList.movePrev();
				} else {
					moved = mList.moveNext();
				}
				if (moved) {
					mRowId = mList.getBookId();
					updateFields();
				}
				return true;
			} else {
				return false;
			}
		}
	};
	
	/**
	 * Accessor; used by parent Activity to get the real current row
	 */
	public Long getRowId() {
		return mRowId;
	}
}
