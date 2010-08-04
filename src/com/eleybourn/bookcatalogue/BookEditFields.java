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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class BookEditFields extends Activity {

	private AutoCompleteTextView mAuthorText;
	private EditText mTitleText;
	private EditText mIsbnText;
	private AutoCompleteTextView mPublisherText;
	private DatePicker mDate_publishedText;
	private Spinner mBookshelfText;
	private ArrayAdapter<String> spinnerAdapter;
	private AutoCompleteTextView mSeriesText;
	private EditText mSeriesNumText;
	private EditText mListPriceText;
	private EditText mPagesText;
	private CheckBox mAnthologyCheckBox;
	private Button mConfirmButton;
	private Button mCancelButton;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;
	private ImageView mImageView;
	private Float rating = Float.parseFloat("0");
	private boolean read = false;
	private int anthology_num = CatalogueDBAdapter.ANTHOLOGY_NO;
	private String notes = "";
	
	private String added_series = "";
	private String added_title = "";
	private String added_author = "";
	public static String ADDED_SERIES = "ADDED_SERIES";
	public static String ADDED_TITLE = "ADDED_TITLE";
	public static String ADDED_AUTHOR = "ADDED_AUTHOR";
	
	private static final int DELETE_ID = 1;
	private static final int ADD_PHOTO = 2;
	private static final int GONE = 8;
	
	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
	}
	
	protected ArrayList<String> getAuthors() {
		ArrayList<String> author_list = new ArrayList<String>();
		Cursor author_cur = mDbHelper.fetchAllAuthors("All Books");
		startManagingCursor(author_cur);
		while (author_cur.moveToNext()) {
			String family = author_cur.getString(author_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FAMILY_NAME));
			String given = author_cur.getString(author_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GIVEN_NAMES));
			author_list.add(family + ", " + given);
		}
		return author_list;
	}

	protected ArrayList<String> getSeries() {
		ArrayList<String> series_list = new ArrayList<String>();
		Cursor series_cur = mDbHelper.fetchAllSeries();
		startManagingCursor(series_cur);
		while (series_cur.moveToNext()) {
			String series = series_cur.getString(series_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES));
			series_list.add(series);
		}
		return series_list;
	}

	protected ArrayList<String> getPublishers() {
		ArrayList<String> publisher_list = new ArrayList<String>();
		Cursor publisher_cur = mDbHelper.fetchAllPublishers();
		startManagingCursor(publisher_cur);
		while (publisher_cur.moveToNext()) {
			String publisher = publisher_cur.getString(publisher_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER));
			publisher_list.add(publisher);
		}
		return publisher_list;
	}
	
	/**
	 * Display the edit fields page
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		SharedPreferences mPrefs = getSharedPreferences("bookCatalogue", MODE_PRIVATE);
		String visibility_prefix = FieldVisibility.prefix;
		boolean field_visibility = true;
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
			setContentView(R.layout.edit_book);
			
			ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthors());
			mAuthorText = (AutoCompleteTextView) findViewById(R.id.author);
			mAuthorText.setAdapter(author_adapter);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "author", true);
			if (field_visibility == false) {
				mAuthorText.setVisibility(GONE);
			}
			
			mTitleText = (EditText) findViewById(R.id.title);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "title", true);
			if (field_visibility == false) {
				mTitleText.setVisibility(GONE);
			}
			
			mIsbnText = (EditText) findViewById(R.id.isbn);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "isbn", true);
			if (field_visibility == false) {
				mIsbnText.setVisibility(GONE);
			}
			
			ArrayAdapter<String> publisher_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getPublishers());
			mPublisherText = (AutoCompleteTextView) findViewById(R.id.publisher);
			mPublisherText.setAdapter(publisher_adapter);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "publisher", true);
			if (field_visibility == false) {
				mPublisherText.setVisibility(GONE);
			}
			
			mDate_publishedText = (DatePicker) findViewById(R.id.date_published);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "date_published", true);
			if (field_visibility == false) {
				TextView mDate_publishedLabel = (TextView) findViewById(R.id.date_published_label);
				mDate_publishedLabel.setVisibility(GONE);
				mDate_publishedText.setVisibility(GONE);
			}
			
			ArrayAdapter<String> series_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getSeries());
			mSeriesText = (AutoCompleteTextView) findViewById(R.id.series);
			mSeriesText.setAdapter(series_adapter);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "series", true);
			if (field_visibility == false) {
				mSeriesText.setVisibility(GONE);
			}
			
			mSeriesNumText = (EditText) findViewById(R.id.series_num);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "series_num", true);
			if (field_visibility == false) {
				mSeriesNumText.setVisibility(GONE);
			}
			
			mListPriceText = (EditText) findViewById(R.id.list_price);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "list_price", true);
			if (field_visibility == false) {
				mListPriceText.setVisibility(GONE);
			}
			
			mPagesText = (EditText) findViewById(R.id.pages);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "pages", true);
			if (field_visibility == false) {
				mPagesText.setVisibility(GONE);
			}
			
			mAnthologyCheckBox = (CheckBox) findViewById(R.id.anthology);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "anthology", true);
			if (field_visibility == false) {
				TextView mAnthologyLabel = (TextView) findViewById(R.id.anthology_label);
				mAnthologyLabel.setVisibility(GONE);
				mAnthologyCheckBox.setVisibility(GONE);
			}
			mAnthologyCheckBox.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					saveState();
					try {
						TabHost tabHost = ((TabActivity) getParent()).getTabHost();  // The activity TabHost
						if (mAnthologyCheckBox.isChecked()) {
							Resources res = getResources();
							TabHost.TabSpec spec;  // Resusable TabSpec for each tab
							Intent intent = new Intent().setClass(BookEditFields.this, BookEditAnthology.class);
							spec = tabHost.newTabSpec("edit_book_anthology").setIndicator(res.getString(R.string.edit_book_anthology), res.getDrawable(R.drawable.ic_tab_anthology)).setContent(intent);
							tabHost.addTab(spec);
						} else {
							// remove tab
							tabHost.getTabWidget().removeViewAt(3);
						}
					} catch (Exception e) {
						// if this doesn't work don't add the tab. The user will have to save and reenter
					}
				}
			});
			
			mConfirmButton = (Button) findViewById(R.id.confirm);
			mCancelButton = (Button) findViewById(R.id.cancel);
			
			mImageView = (ImageView) findViewById(R.id.row_img);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "thumbnail", true);
			if (field_visibility == false) {
				mImageView.setVisibility(GONE);
			}
			
			/* Setup the Bookshelf Spinner */
			mBookshelfText = (Spinner) findViewById(R.id.bookshelf);
			spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
			spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			mBookshelfText.setAdapter(spinnerAdapter);
			Cursor bookshelves = mDbHelper.fetchAllBookshelves();
			if (bookshelves.moveToFirst()) { 
				do { 
					spinnerAdapter.add(bookshelves.getString(1)); 
				} 
				while (bookshelves.moveToNext()); 
			} 
			field_visibility = mPrefs.getBoolean(visibility_prefix + "bookshelf", true);
			if (field_visibility == false) {
				mBookshelfText.setVisibility(GONE);
			}
			
			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (mRowId == null) {
				getRowId();
			}
			populateFields();
			
			mImageView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
				@Override
				public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
					MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete_thumb);
					delete.setIcon(android.R.drawable.ic_menu_delete);
					MenuItem add_photo = menu.add(0, ADD_PHOTO, 0, R.string.menu_add_thumb_photo);
					add_photo.setIcon(android.R.drawable.ic_menu_camera);
				}
			});
			
			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					saveState();
					Intent i = new Intent();
					i.putExtra(ADDED_SERIES, added_series);
					i.putExtra(ADDED_TITLE, added_title);
					i.putExtra(ADDED_AUTHOR, added_author);
					if (getParent() == null) {
						setResult(RESULT_OK, i);
					} else {
						getParent().setResult(RESULT_OK, i);
					}
					getParent().finish();
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
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case DELETE_ID:
			deleteThumbnail(mRowId);
			populateFields();
			return true;
		case ADD_PHOTO:
			Intent intent = null;
			intent = new Intent("android.media.action.IMAGE_CAPTURE");
			startActivityForResult(intent, ADD_PHOTO);
			return true;
		}
		return super.onContextItemSelected(item);
	}
	
	/**
	 * Delete the provided thumbnail from the sdcard
	 * 
	 * @param id The id of the book (and thumbnail) to delete
	 */
	private void deleteThumbnail(long id) {
		try {
			String tmpThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/" + id + ".jpg";
			File thumb = new File(tmpThumbFilename);
			thumb.delete();
			
			tmpThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/tmp.jpg";
			thumb = new File(tmpThumbFilename);
			thumb.delete();
		} catch (Exception e) {
			// something has gone wrong. 
		}
	}
	
	/**
	 * This function will populate the forms elements in three different ways
	 * 1. If a valid rowId exists it will populate the fields from the database
	 * 2. If fields have been passed from another activity (e.g. ISBNSearch) it will populate the fields from the bundle
	 * 3. It will leave the fields blank for new books.
	 */
	private void populateFields() {
		Bundle extras = getIntent().getExtras();
		if (mRowId == null) {
			getRowId();
		}
		
		if (mRowId != null && mRowId > 0) {
			// From the database (edit)
			Cursor book = mDbHelper.fetchBook(mRowId);
			startManagingCursor(book);
			String title = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)); 
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + title);
			
			mAuthorText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR)));
			mTitleText.setText(title);
			mIsbnText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN)));
			mPublisherText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER)));
			String[] date = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)).split("-");
			int yyyy = Integer.parseInt(date[0]);
			int mm = Integer.parseInt(date[1]);
			int dd = Integer.parseInt(date[2]);
			mDate_publishedText.updateDate(yyyy, mm, dd);
			String bs = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF));
			mBookshelfText.setSelection(spinnerAdapter.getPosition(bs));
			mSeriesText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES)));
			mSeriesNumText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM)));
			mListPriceText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE)));
			mPagesText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES)));
			anthology_num = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
			if (anthology_num == 0) {
				mAnthologyCheckBox.setChecked(false);
			} else {
				mAnthologyCheckBox.setChecked(true);
			}
			mConfirmButton.setText(R.string.confirm_save);
			String thumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/" + mRowId + ".jpg";
			File thumb = new File(thumbFilename);
			if (thumb.exists()) {
				mImageView.setImageBitmap(BitmapFactory.decodeFile(thumbFilename));
			} else {
				mImageView.setImageResource(android.R.drawable.ic_menu_help);
			}
			rating = book.getFloat(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING));
			read = (book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_READ))==0 ? false:true);
			notes = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES));
		} else if (extras != null) {
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
			// From the ISBN Search (add)
			String[] book = extras.getStringArray("book");
			mAuthorText.setText(book[0]);
			mTitleText.setText(book[1]);
			mIsbnText.setText(book[2]);
			mPublisherText.setText(book[3]);
			try {
				String[] date = book[4].split("-");
				int yyyy = Integer.parseInt(date[0]);
				int mm = Integer.parseInt(date[1])-1;
				int dd = Integer.parseInt(date[2]);
				mDate_publishedText.updateDate(yyyy, mm, dd);
			} catch (ArrayIndexOutOfBoundsException e) {
				//do nothing
			} catch (NumberFormatException e) {
				//do nothing
			}
			
			// Bookshelves can't be set from the search results (very user specific)
			// so use the currently selected bookshelf
			try {
				mBookshelfText.setSelection(spinnerAdapter.getPosition(BookCatalogue.bookshelf));
			} catch (Exception e) {
				//do nothing. The default will not be set
				mBookshelfText.setSelection(spinnerAdapter.getPosition(book[6]));
			}
			mSeriesText.setText(book[8]);
			mSeriesNumText.setText(book[10]);
			try {
				//just in case - there was an exception earlier, but it should be fixed
				mListPriceText.setText(book[11]);
			} catch (Exception e) {
				//do nothing
			}
			mPagesText.setText(book[9]);
			
			String anthology = book[12];
			if (anthology.equals("0")) {
				mAnthologyCheckBox.setChecked(false);
			} else {
				mAnthologyCheckBox.setChecked(true);
			}
			mConfirmButton.setText(R.string.confirm_add);
			String thumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/tmp.jpg";
			File thumb = new File(thumbFilename);
			if (thumb.exists()) {
				mImageView.setImageBitmap(BitmapFactory.decodeFile(thumbFilename));
			} else {
				mImageView.setImageResource(android.R.drawable.ic_menu_help);
			}
		} else {
			// Manual Add
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
			try {
				mBookshelfText.setSelection(spinnerAdapter.getPosition(BookCatalogue.bookshelf));
			} catch (Exception e) {
				//do nothing. The default will not be set
			}
			mConfirmButton.setText(R.string.confirm_add);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mRowId != null) {
			outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		} else {
			//there is nothing todo
		}
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
	
	/**
	 * This will save a book into the database, by either updating or created a book.
	 * Minor modifications will be made to the strings:
	 * 	Titles will be rewords so a, the, an will be moved to the end of the string
	 * 	Date published will be converted from a date to a string
	 * 
	 * It will also ensure the book doesn't already exist (isbn search) if you are creating a book.
	 * Thumbnails will also be saved to the correct location 
	 */
	private void saveState() {
		String author = mAuthorText.getText().toString();
		
		/* Move "The, A, An" to the end of the string */
		String title = mTitleText.getText().toString();
		String[] title_words = title.split(" ");
		try {
			if (title_words[0].matches("a|A|an|An|the|The")) {
				title = "";
				for (int i = 1; i < title_words.length; i++) {
					if (i != 1) {
						title += " ";
					}
					title += title_words[i];
				}
				title += ", " + title_words[0];
			}
		} catch (Exception e) {
			//do nothing. Title stays the same
		}
		
		String isbn = mIsbnText.getText().toString();
		String publisher = mPublisherText.getText().toString();
		int yyyy =  mDate_publishedText.getYear();
		int mm =  mDate_publishedText.getMonth();
		int dd =  mDate_publishedText.getDayOfMonth();
		String date_published = yyyy + "-" + mm + "-" + dd;
		String bookshelf = mBookshelfText.getAdapter().getItem(mBookshelfText.getSelectedItemPosition()).toString(); 
		String series = mSeriesText.getText().toString();
		String series_num = mSeriesNumText.getText().toString();
		String list_price = mListPriceText.getText().toString();
		boolean anthology_checked = mAnthologyCheckBox.isChecked();
		int anthology = anthology_num; // Defaults to ANTHOLOGY_NO or what is in the database
		if (anthology_checked == true && anthology_num == CatalogueDBAdapter.ANTHOLOGY_NO) {
			anthology = CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS;
		} else if (anthology_checked == false) {
			anthology = CatalogueDBAdapter.ANTHOLOGY_NO;
		}
		int pages = 0;
		try {
			pages = Integer.parseInt(mPagesText.getText().toString());
		} catch (NumberFormatException e) {
			pages = 0;
		}
		
		if (mRowId == null || mRowId == 0) {
			/* Check if the book currently exists */
			if (!isbn.equals("")) {
				Cursor book = mDbHelper.fetchBookByISBN(isbn);
				int rows = book.getCount();
				if (rows != 0) {
					Toast.makeText(this, R.string.book_exists, Toast.LENGTH_LONG).show();
					return;
				}
			}
			
			long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology);
			if (id > 0) {
				mRowId = id;
				String tmpThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/tmp.jpg";
				String realThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/" + id + ".jpg";
				File thumb = new File(tmpThumbFilename);
				File real = new File(realThumbFilename);
				thumb.renameTo(real);
			}
		} else {
			mDbHelper.updateBook(mRowId, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology);
		}
		/* These are global variables that will be sent via intent back to the list view */
		added_author = author;
		added_title = title;
		added_series = series;
		return;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ADD_PHOTO:
			if (resultCode == Activity.RESULT_OK){
				
				String realThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/" + mRowId + ".jpg";
				Bitmap x = (Bitmap) intent.getExtras().get("data");
				Matrix m = new Matrix();
				m.postRotate(90);
				x = Bitmap.createBitmap(x, 0, 0, x.getWidth(), x.getHeight(), m, true);
				/* Create a file to copy the thumbnail into */
				FileOutputStream f = null;
				try {
					f = new FileOutputStream(realThumbFilename);
				} catch (FileNotFoundException e) {
					//Log.e("Book Catalogue", "Thumbnail cannot be written");
					return;
				}
				
				x.compress(Bitmap.CompressFormat.JPEG, 50, f);
			}
		}
	}
}
