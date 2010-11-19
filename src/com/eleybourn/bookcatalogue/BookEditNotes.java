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

import android.app.Activity;
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
	private DatePicker mReadStartView;
	private DatePicker mReadEndView;
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
			
			mReadStartView = (DatePicker) findViewById(R.id.read_start);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "read_start", true);
			if (field_visibility == false) {
				mReadStartView.setVisibility(GONE);
				TextView mReadStartLabelView = (TextView) findViewById(R.id.read_start_label);
				mReadStartLabelView.setVisibility(GONE);
			}
			
			mReadEndView = (DatePicker) findViewById(R.id.read_end);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "read_end", true);
			if (field_visibility == false) {
				mReadEndView.setVisibility(GONE);
				TextView mReadEndLabelView = (TextView) findViewById(R.id.read_end_label);
				mReadEndLabelView.setVisibility(GONE);
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
			try {
				String[] s_date = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START)).split("-");
				int s_yyyy = Integer.parseInt(s_date[0]);
				int s_mm = Integer.parseInt(s_date[1]);
				int s_dd = Integer.parseInt(s_date[2]);
				mReadStartView.updateDate(s_yyyy, s_mm, s_dd);
			} catch (Exception e) {
				//Keep the default date - which is now()
			}
			try {
				String[] e_date = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END)).split("-");
				int e_yyyy = Integer.parseInt(e_date[0]);
				int e_mm = Integer.parseInt(e_date[1]);
				int e_dd = Integer.parseInt(e_date[2]);
				mReadEndView.updateDate(e_yyyy, e_mm, e_dd);
			} catch (Exception e) {
				// Keep the default date - which is now()
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
		populateFields();
	}

	private void saveState() {
		float rating = mRatingText.getRating();
		boolean read = mReadText.isChecked();
		String notes = mNotesText.getText().toString();
		String location = mLocationView.getText().toString();
		int s_yyyy =  mReadStartView.getYear();
		int s_mm =  mReadStartView.getMonth();
		int s_dd =  mReadStartView.getDayOfMonth();
		String read_start = s_yyyy + "-" + s_mm + "-" + s_dd;
		int e_yyyy =  mReadEndView.getYear();
		int e_mm =  mReadEndView.getMonth();
		int e_dd =  mReadEndView.getDayOfMonth();
		String read_end = e_yyyy + "-" + e_mm + "-" + e_dd;
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