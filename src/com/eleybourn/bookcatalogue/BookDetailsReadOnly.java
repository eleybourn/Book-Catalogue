package com.eleybourn.bookcatalogue;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

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
		
		/* Disable Anthology field. User will see Anthology tab if it set,
		 * so we need show that in read-only details. */
		findViewById(R.id.anthology).setVisibility(View.GONE);

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
			//Set read status if needed
			Long isRead = book.getLong(book.getColumnIndex(CatalogueDBAdapter.KEY_READ));
			boolean isBookRead = isRead == 1;
			if(isBookRead){
				((ImageView) findViewById(R.id.read)).setImageResource(R.drawable.btn_check_buttonless_on);
			}
			
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
	// Override only one line from superclass method. See description below
	protected void populateSeriesListField() {
		String newText;
		int size = 0;
		try {
			size = mSeriesList.size();
		} catch (NullPointerException e) {
			size = 0;
		}
		if (size == 0)
			// Override only this line to show "Not set" text when there is no Series
			return;
		else {
			Utils.pruneSeriesList(mSeriesList);
			Utils.pruneList(mDbHelper, mSeriesList);
			newText = mSeriesList.get(0).getDisplayName();
			if (mSeriesList.size() > 1)
				newText += " " + getResources().getString(R.string.and_others);
		}
		mFields.getField(R.id.series).setValue(newText);
	}
	
	/**
	 * Add other fields of book to details fields. We need this method to automatically 
	 * populate some fields during populating. See {@link #populateFieldsFromDb(Long)}.
	 * Note that it should be performed before populating.
	 */
	private void addFields(){
		mFields.add(R.id.rating, CatalogueDBAdapter.KEY_RATING, new Fields.FloatValidator());
	}
}
