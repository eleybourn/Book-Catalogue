package com.eleybourn.bookcatalogue;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

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
		
		if (mRowId != null && mRowId > 0) {
			populateFieldsFromDb(mRowId);
			// Populate author and series fields
			populateAuthorListField();
			populateSeriesListField();
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
			
			String title = mFields.getField(R.id.title).getValue().toString();
			setActivityTitle(title);
		} catch (Exception e) {
			e.printStackTrace();
		} 
		finally {
			if (book != null)
				book.close();
		}
		
		populateBookshelvesField(mFields, rowId);
		
		// Get author and series lists
		mAuthorList = mDbHelper.getBookAuthorList(rowId);
		mSeriesList = mDbHelper.getBookSeriesList(rowId);
	}
	
	@Override
	/*
	 * Override populating author field. Hide the field if author not set or 
	 * shows author (or authors through ',') with 'by' at the beginning. 
	 */
	protected void populateAuthorListField() {
		int authorsCount = mAuthorList.size();
		if (authorsCount == 0){
			// Hide author field if it is not set
			findViewById(R.id.author).setVisibility(View.GONE);
		} else {
			StringBuilder builder = new StringBuilder();
			builder.append(getResources().getString(R.string.book_details_readonly_by));
			builder.append(" ");
			for(int i =  0; i < authorsCount; i++){
				builder.append(mAuthorList.get(i).getDisplayName());
				if(i != authorsCount - 1){
					builder.append(", ");
				}
			}
			mFields.getField(R.id.author).setValue(builder.toString());
		}
	}
	
	@Override
	protected void populateSeriesListField() {
		String newText = null;
		int size = 0;
		try {
			size = mSeriesList.size();
		} catch (NullPointerException e) {
			size = 0;
		}
		if (size == 0)
			// Override this line to show "Not set" text when there is no Series
			return;
		else {
			Utils.pruneSeriesList(mSeriesList);
			Utils.pruneList(mDbHelper, mSeriesList);
			int seriesCount = mSeriesList.size();
			if (seriesCount > 0) {
				StringBuilder builder = new StringBuilder();
				for(int i =  0; i < seriesCount; i++){
					builder.append(mSeriesList.get(i).getDisplayName());
					if(i != seriesCount - 1){
						builder.append(", ");
					}
				}
				newText = builder.toString();
			}
		}
		mFields.getField(R.id.series).setValue(newText);
	}
	
	/**
	 * Add other fields of book to details fields. We need this method to automatically 
	 * populate some fields during populating. See {@link #populateFieldsFromDb(Long)}.
	 * Note that it should be performed before populating.
	 */
	private void addFields(){
		//From 'My comments' tab
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
	private void showLoanedInfo(Long rowId){
		String personLoanedTo = mDbHelper.fetchLoanByBook(rowId);
		if (personLoanedTo != null) {
			TextView textView = (TextView) findViewById(R.id.lbl_loaned_to);
			textView.setVisibility(View.VISIBLE); //'Loaned to' label is visible now
			
			textView = (TextView) findViewById(R.id.who);
			textView.setVisibility(View.VISIBLE);
			textView.setText(personLoanedTo);
		}
	}
	
	/**
	 * Sets read status of the book if needed. Shows green tick if book is read.
	 * @param book Cursor containing information of the book from database
	 */
	private void showReadStatus(Cursor book){
		Integer isRead = book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_READ));
		boolean isBookRead = isRead == 1;
		if(isBookRead){
			((ImageView) findViewById(R.id.read)).setImageResource(R.drawable.btn_check_buttonless_on);
		}
	}
	
	/**
	 * Show signed status of the book. Set text 'yes' if signed. Otherwise it is 'No'.
	 * @param book Cursor containing information of the book from database
	 */
	private void showSignedStatus(Cursor book){
		Integer isSigned = book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_SIGNED));
		boolean isBookSigned = isSigned == 1;
		if(isBookSigned){
			((TextView) findViewById(R.id.signed)).setText(getResources().getString(R.string.yes));
		}
	}
}
