/*
 * @copyright 2010 Evan Leybourn
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

package com.eleybourn.bookcatalogue;

//import android.R;
import java.util.ArrayList;
import java.util.Calendar;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookEditNotes extends Activity {

	private Fields mFields;
	private Button mConfirmButton;
	private Button mCancelButton;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;

	private static final int READ_START_DIALOG_ID = 1;
	private static final int READ_END_DIALOG_ID = 2;
	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
	}
	
	/**
	 * Validate the current data in all fields that have validators. Display any errors.
	 * 
	 * @param values The values to use
	 * 
	 * @return Boolean success or failure.
	 */
	private boolean validate(Bundle values) {
		if (!mFields.validate(values)) {
			Toast.makeText(getParent(), mFields.getValidationExceptionMessage(getResources()), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	/**
	 * Returns a unique list of all locations in the database
	 *  
	 * @return The list
	 */
	protected ArrayList<String> getLocations() {
		ArrayList<String> location_list = new ArrayList<String>();
		Cursor location_cur = mDbHelper.fetchAllLocations();
		startManagingCursor(location_cur);
		while (location_cur.moveToNext()) {
			String publisher = location_cur.getString(location_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION));
			location_list.add(publisher);
		}
		return location_list;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
			setContentView(R.layout.edit_book_notes);

			mFields = new Fields(this);
			Field f;

			// Generic validators; if field-specific defaults are needed, create a new one.
			FieldValidator booleanValidator = new Fields.BooleanValidator();
			FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());
			FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

			mFields.add(R.id.rating, CatalogueDBAdapter.KEY_RATING, new Fields.FloatValidator());
			mFields.add(R.id.rating_label, "",  CatalogueDBAdapter.KEY_RATING, null);
			mFields.add(R.id.read, CatalogueDBAdapter.KEY_READ, booleanValidator);
			mFields.add(R.id.notes, CatalogueDBAdapter.KEY_NOTES, null);

			ArrayAdapter<String> location_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getLocations());
			f = mFields.add(R.id.location, CatalogueDBAdapter.KEY_LOCATION, null);
			((AutoCompleteTextView)f.view).setAdapter(location_adapter);

			f = mFields.add(R.id.read_start_button, "",  CatalogueDBAdapter.KEY_READ_START, null);
			f.view.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(READ_START_DIALOG_ID);
				}
			});
			mFields.add(R.id.read_start, CatalogueDBAdapter.KEY_READ_START, blankOrDateValidator, dateFormatter);

			f = mFields.add(R.id.read_end_button, "",  CatalogueDBAdapter.KEY_READ_END, null);
			f.view.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(READ_END_DIALOG_ID);
				}
			});
			f = mFields.add(R.id.read_end, CatalogueDBAdapter.KEY_READ_END, blankOrDateValidator, dateFormatter);

			mFields.add(R.id.signed, CatalogueDBAdapter.KEY_SIGNED,  booleanValidator);

			mFields.addCrossValidator(new Fields.FieldCrossValidator() {
				public void validate(Fields fields, Bundle values) {
					String start = values.getString(CatalogueDBAdapter.KEY_READ_START);
					if (start.equals(""))
						return;
					String end = values.getString(CatalogueDBAdapter.KEY_READ_END);
					if (end.equals(""))
						return;
					if (start.compareToIgnoreCase(end) > 0)
						throw new Fields.ValidatorException(R.string.vldt_read_start_after_end,new Object[]{});							
				}
			});

			mConfirmButton = (Button) findViewById(R.id.confirm);
			mCancelButton = (Button) findViewById(R.id.cancel);
			
			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (mRowId == null) {
				getRowId();
			}

			if (savedInstanceState == null)
				populateFields();

			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					Bundle values = new Bundle();
					if (!validate(values)) {
						//return;
						// Ignore...nothing here is that important...but warn the users.
					}

					saveState(values);
					setResult(RESULT_OK);
					finish();
				}
			});
			
			mCancelButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					setResult(RESULT_OK);
					finish();
				}
			});
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case READ_START_DIALOG_ID:
			try {
				String dateString = mFields.getField(R.id.read_start).getValue().toString();
				// get the current date
				final Calendar c = Calendar.getInstance();
				int yyyy = c.get(Calendar.YEAR);
				int mm = c.get(Calendar.MONTH);
				int dd = c.get(Calendar.DAY_OF_MONTH);
				try {
					String[] date = dateString.split("-");
					yyyy = Integer.parseInt(date[0]);
					mm = Integer.parseInt(date[1])-1;
					dd = Integer.parseInt(date[2]);
				} catch (Exception e) {
					//do nothing
				}
				return new DatePickerDialog(this, mReadStartSetListener, yyyy, mm, dd);
			} catch (Exception e) {
				// use the default date
			}
			break;
		case READ_END_DIALOG_ID:
			try {
				String dateString = mFields.getField(R.id.read_end).getValue().toString();
				// get the current date
				final Calendar c = Calendar.getInstance();
				int yyyy = c.get(Calendar.YEAR);
				int mm = c.get(Calendar.MONTH);
				int dd = c.get(Calendar.DAY_OF_MONTH);
				try {
					String[] date = dateString.split("-");
					yyyy = Integer.parseInt(date[0]);
					mm = Integer.parseInt(date[1])-1;
					dd = Integer.parseInt(date[2]);
				} catch (Exception e) {
					//do nothing
				}
				return new DatePickerDialog(this, mReadEndSetListener, yyyy, mm, dd);
			} catch (Exception e) {
				// use the default date
			}
			break;
		}
		return null;
	}	

	// the callback received when the user "sets" the date in the dialog
	private DatePickerDialog.OnDateSetListener mReadStartSetListener = new DatePickerDialog.OnDateSetListener() {
		public void onDateSet(DatePicker view, int year, int month, int day) {
			month = month + 1;
			String mm = month + "";
			if (mm.length() == 1) {
				mm = "0" + mm;
			}
			String dd = day + "";
			if (dd.length() == 1) {
				dd = "0" + dd;
			}
			mFields.getField(R.id.read_start).setValue(year + "-" + mm + "-" + dd);
		}
	};
	// the callback received when the user "sets" the date in the dialog
	private DatePickerDialog.OnDateSetListener mReadEndSetListener = new DatePickerDialog.OnDateSetListener() {
		public void onDateSet(DatePicker view, int year, int month, int day) {
			month = month + 1;
			String mm = month + "";
			if (mm.length() == 1) {
				mm = "0" + mm;
			}
			String dd = day + "";
			if (dd.length() == 1) {
				dd = "0" + dd;
			}
			mFields.getField(R.id.read_end).setValue(year + "-" + mm + "-" + dd);
		}
	};
	
	private void populateFields() {
		if (mRowId == null) {
			getRowId();
		}
		
		if (mRowId != null && mRowId > 0) {
			// From the database (edit)
			Cursor book = mDbHelper.fetchBookById(mRowId);
			try {
				if (book != null) {
					book.moveToFirst();
				}
				getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + 
							book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)));

				mFields.setFromCursor(book);

				mConfirmButton.setText(R.string.confirm_save);
				
			} finally {
				// Tidy up
				if (book != null)
					book.close();				
			}
		} else {
			// Manual Add
			//This should never happen
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	private void saveState(Bundle values) {

		if (mRowId == null || mRowId == 0) {
			//This should never happen
			//long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		} else {
			mDbHelper.updateBook(mRowId, values, true);
		}
		return;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}

}