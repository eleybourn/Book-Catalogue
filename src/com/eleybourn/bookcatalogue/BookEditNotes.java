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
import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookEditNotes extends Activity {

	private RatingBar mRatingText;
	private CheckBox mReadText;
	private EditText mNotesText;
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
	private int pages;

	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();

			setContentView(R.layout.edit_book_notes);

			mRatingText = (RatingBar) findViewById(R.id.rating);
			mReadText = (CheckBox) findViewById(R.id.read);
			mNotesText = (EditText) findViewById(R.id.notes);
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
			Cursor book = mDbHelper.fetchBook(mRowId);
			startManagingCursor(book);

			mRatingText.setRating(book.getFloat(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING)));
			mReadText.setChecked((book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_READ))==0? false:true) );
			mNotesText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES)));
			mConfirmButton.setText(R.string.confirm_save);

			author = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR));
			title = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
			isbn = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN));
			publisher = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER));
			date_published = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
			bookshelf = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF));
			series = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES));
			series_num = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM));
			pages = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES));
		} else {
			// Manual Add
			//TODO: This should never happen
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
		Boolean read = mReadText.isChecked();
		String notes = mNotesText.getText().toString();

		if (mRowId == null || mRowId == 0) {
			//TODO: This should never happen
			//long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		} else {
			mDbHelper.updateBook(mRowId, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes);
		}
		return;
	}

}