package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class for representing read-only book details.
 * @author n.silin
 */
public class BookDetailsReadOnly extends BookDetailsAbstract {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		setContentView(R.layout.book_details);
		setRowIdByExtras();
		/* In superclass onCreate method we initialize fields, background,
		 * display metrics and other. So see superclass onCreate method. */
		super.onCreate(savedInstanceState);
		// Set additional (non book deatils) fields before thier populating
		addFields();
		
		/* 
		 * We should override this value to initialize book thumb with right size.
		 * You have to see in book_details.xml to get dividing coefficient
		 */
		mThumbEditSize = mMetrics.widthPixels / 3;
		
		if (mRowId != null && mRowId > 0) {
			updateFields();
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
		Cursor book = mDbHelper.fetchBookById(rowId);
		try {
			if (book != null) {
				book.moveToFirst();
			}

			populateBookDetailsFields(rowId, book);

			// Additional fields for read-only mode which are not initialized automatically
			showReadStatus(book);
			showLoanedInfo(rowId);
			showSignedStatus(book);

			hideEmptyFields();

			String title = mFields.getField(R.id.title).getValue().toString();
			setActivityTitle(title);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (book != null)
				book.close();
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
		if (size == 0) {
			// Hide 'Series' label and data
			findViewById(R.id.lbl_series).setVisibility(View.GONE);
			findViewById(R.id.series).setVisibility(View.GONE);
			return;
		} else {
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
	private void showReadStatus(Cursor book) {
		Integer isRead = book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_READ));
		boolean isBookRead = isRead == 1;
		if (isBookRead) {
			ImageView image = (ImageView) findViewById(R.id.read);
			image.setVisibility(View.VISIBLE);
			image.setImageResource(R.drawable.btn_check_buttonless_on);
		}
	}

	/**
	 * Show signed status of the book. Set text 'yes' if signed. Otherwise it is 'No'.
	 * @param book Cursor containing information of the book from database
	 */
	private void showSignedStatus(Cursor book) {
		Integer isSigned = book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_SIGNED));
		boolean isBookSigned = isSigned == 1;
		if (isBookSigned) {
			((TextView) findViewById(R.id.signed)).setText(getResources().getString(R.string.yes));
		}
	}
	
	/**
	 * Hides unused fields if they have not any useful data. Checks all text fields
	 * except of author, series and loaned. 
	 */
	private void hideEmptyFields() {
		// CHeck publishing information
		if (hideFieldIfEmpty(R.id.publisher) && hideFieldIfEmpty(R.id.date_published)) {
			findViewById(R.id.lbl_publishing).setVisibility(View.GONE);
		}
		// Check format information
		boolean hasPages = !hideFieldIfEmpty(R.id.pages);
		if (hasPages) { // Add 'pages' word to numbers
			Field pagesField = mFields.getField(R.id.pages);
			String numPages = (String) pagesField.getValue();
			pagesField.setValue(getString(R.string.book_details_readonly_pages, numPages));
		}
		if (hideFieldIfEmpty(R.id.format) && !hasPages) {
			findViewById(R.id.lbl_format).setVisibility(View.GONE);
		}
		// Check genre
		if (hideFieldIfEmpty(R.id.genre)) {
			findViewById(R.id.lbl_genre).setVisibility(View.GONE);
		}
		// Check ISBN
		if (hideFieldIfEmpty(R.id.isbn)) {
			findViewById(R.id.row_isbn).setVisibility(View.GONE);
		}
		// Check list price
		if (hideFieldIfEmpty(R.id.list_price)) {
			findViewById(R.id.row_list_price).setVisibility(View.GONE);
		}
		// Check description
		if (hideFieldIfEmpty(R.id.description)) {
			findViewById(R.id.descriptionLabel).setVisibility(View.GONE);
			findViewById(R.id.description_divider).setVisibility(View.GONE);
		}

		// **** MY COMMENTS SECTION ****
		// Check notes
		if (hideFieldIfEmpty(R.id.notes)) {
			findViewById(R.id.lbl_notes).setVisibility(View.GONE);
		}
		// Check date start reading
		if (hideFieldIfEmpty(R.id.read_start)) {
			findViewById(R.id.row_read_start).setVisibility(View.GONE);
		}
		// Check date end reading
		if (hideFieldIfEmpty(R.id.read_end)) {
			findViewById(R.id.row_read_end).setVisibility(View.GONE);
		}
		// Check location
		if (hideFieldIfEmpty(R.id.location)) {
			findViewById(R.id.row_location).setVisibility(View.GONE);
		}
	}
	
	/**
	 * Hide text field if it has not any useful data.
	 * @param resId layout resource id of the field
	 * @return true if field was hidden, false otherwise
	 */
	private boolean hideFieldIfEmpty(int resId) {
		String value = (String) mFields.getField(resId).getValue();
		boolean isExist = value != null && !value.equals("");
		if (!isExist) {
			findViewById(resId).setVisibility(View.GONE);
		}
		return !isExist;
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

}
