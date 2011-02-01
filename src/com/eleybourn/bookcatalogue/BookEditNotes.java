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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookEditNotes extends Activity {

	private RatingBar mRatingText;
	private CheckBox mReadText;
	private EditText mNotesText;
	private AutoCompleteTextView mLocationView;
	private TextView mReadStartView;
	private TextView mReadEndView;
	private CheckBox mSignedView;
	private Button mConfirmButton;
	private Button mCancelButton;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;
	
	private String author;
	private String title;
	private String isbn;
	private String publisher;
	private String date_published;
	private String bookshelf;
	private String series;
	private String series_num;
	private String list_price;
	private int anthology;
	private int pages;
	private String format;
	private String description;
	private String genre;
	
	private static final int GONE = 8;
	private static final int READ_START_DIALOG_ID = 1;
	private static final int READ_END_DIALOG_ID = 2;
	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
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
		SharedPreferences mPrefs = getSharedPreferences("bookCatalogue", MODE_PRIVATE);
		String visibility_prefix = FieldVisibility.prefix;
		boolean field_visibility = true;
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
			setContentView(R.layout.edit_book_notes);
			
			mRatingText = (RatingBar) findViewById(R.id.rating);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "rating", true);
			if (field_visibility == false) {
				TextView mRatingLabel = (TextView) findViewById(R.id.rating_label);
				mRatingLabel.setVisibility(GONE);
				mRatingText.setVisibility(GONE);
			}
			
			mReadText = (CheckBox) findViewById(R.id.read);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "read", true);
			if (field_visibility == false) {
				mReadText.setVisibility(GONE);
			}
			
			mNotesText = (EditText) findViewById(R.id.notes);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "notes", true);
			if (field_visibility == false) {
				mNotesText.setVisibility(GONE);
			}
			
			ArrayAdapter<String> location_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getLocations());
			mLocationView = (AutoCompleteTextView) findViewById(R.id.location);
			mLocationView.setAdapter(location_adapter);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "location", true);
			if (field_visibility == false) {
				mLocationView.setVisibility(GONE);
			}
			
			Button mReadStartButton = (Button) findViewById(R.id.read_start_button);
			mReadStartButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(READ_START_DIALOG_ID);
				}
			});
			mReadStartView = (TextView) findViewById(R.id.read_start);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "read_start", true);
			if (field_visibility == false) {
				mReadStartView.setVisibility(GONE);
				mReadStartButton.setVisibility(GONE);
			}
			
			Button mReadEndButton = (Button) findViewById(R.id.read_end_button);
			mReadEndButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(READ_END_DIALOG_ID);
				}
			});
			mReadEndView = (TextView) findViewById(R.id.read_end);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "read_end", true);
			if (field_visibility == false) {
				mReadEndView.setVisibility(GONE);
				mReadEndButton.setVisibility(GONE);
			}
			
			mSignedView = (CheckBox) findViewById(R.id.signed);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "signed", true);
			if (field_visibility == false) {
				mSignedView.setVisibility(GONE);
			}
			
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
					saveState();
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
			//Log.e("Book Catalogue", "Unknown error " + e.toString());
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case READ_START_DIALOG_ID:
			try {
				String dateString = (String) mReadStartView.getText();
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
				String dateString = (String) mReadEndView.getText();
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
			mReadStartView.setText(year + "-" + mm + "-" + dd);
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
			mReadEndView.setText(year + "-" + mm + "-" + dd);
		}
	};
	
	private void populateFields() {
		if (mRowId == null) {
			getRowId();
		}
		
		if (mRowId != null && mRowId > 0) {
			// From the database (edit)
			Cursor book = mDbHelper.fetchBookById(mRowId);
			if (book != null) {
				book.moveToFirst();
			}
			startManagingCursor(book);
			title = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)); 
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + title);
			
			mRatingText.setRating(book.getFloat(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING)));
			mReadText.setChecked((book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_READ))==0? false:true) );
			mNotesText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES)));
			mLocationView.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION)));
			String[] start_date = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START)).split("-");
			try {
				String yyyy = start_date[0];
				int month = Integer.parseInt(start_date[1]);
				month = month + 1;
				String mm = month + "";
				if (mm.length() == 1) {
					mm = "0" + mm;
				}
				String dd = start_date[2];
				if (dd.length() == 1) {
					dd = "0" + dd;
				}
				mReadStartView.setText(yyyy + "-" + mm + "-" + dd);
			} catch (Exception e) {
				
			}
			String[] end_date = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END)).split("-");
			try {
				String yyyy = end_date[0];
				int month = Integer.parseInt(end_date[1]);
				month = month + 1;
				String mm = month + "";
				if (mm.length() == 1) {
					mm = "0" + mm;
				}
				String dd = end_date[2];
				if (dd.length() == 1) {
					dd = "0" + dd;
				}
				mReadEndView.setText(yyyy + "-" + mm + "-" + dd);
			} catch (Exception e) {
				
			}
			mSignedView.setChecked((book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_SIGNED))==0? false:true) );
			mConfirmButton.setText(R.string.confirm_save);
			
			author = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
			isbn = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN));
			publisher = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER));
			date_published = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
			bookshelf = null;
			series = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES));
			series_num = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM));
			list_price = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE));
			anthology = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
			pages = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES));
			format = book.getString(book.getColumnIndex(CatalogueDBAdapter.KEY_FORMAT));
			description = book.getString(book.getColumnIndex(CatalogueDBAdapter.KEY_DESCRIPTION));
			genre = book.getString(book.getColumnIndex(CatalogueDBAdapter.KEY_GENRE));

			// Tidy up
			book.close();
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

	private void saveState() {
		float rating = mRatingText.getRating();
		boolean read = mReadText.isChecked();
		String notes = mNotesText.getText().toString();
		String location = mLocationView.getText().toString();
		String read_start = "";
		try {
			read_start = (String) mReadStartView.getText();
			String[] date = read_start.split("-");
			int yyyy = Integer.parseInt(date[0]);
			int mm = Integer.parseInt(date[1])-1;
			int dd = Integer.parseInt(date[2]);
			read_start = yyyy + "-" + mm + "-" + dd;
		} catch (Exception e) {
			//do nothing
		}
		String read_end = "";
		try {
			read_end = (String) mReadEndView.getText();
			String[] date = read_end.split("-");
			int yyyy = Integer.parseInt(date[0]);
			int mm = Integer.parseInt(date[1])-1;
			int dd = Integer.parseInt(date[2]);
			read_end = yyyy + "-" + mm + "-" + dd;
		} catch (Exception e) {
			//do nothing
		}
		boolean signed = mSignedView.isChecked();
		
		if (mRowId == null || mRowId == 0) {
			//This should never happen
			//long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		} else {
			mDbHelper.updateBook(mRowId, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, format, signed, description, genre);
		}
		return;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}

}