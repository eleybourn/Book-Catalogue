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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Calendar;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class BookEditFields extends Activity {

	private AutoCompleteTextView mAuthorText;
	private EditText mTitleText;
	private EditText mIsbnText;
	private AutoCompleteTextView mPublisherText;
	private TextView mDate_publishedText;
	private Button mBookshelfButton;
	private TextView mBookshelfText;
	private AutoCompleteTextView mSeriesText;
	private EditText mSeriesNumText;
	private EditText mListPriceText;
	private EditText mPagesText;
	private CheckBox mAnthologyCheckBox;
	private Spinner mFormatText;
	private EditText mDescriptionText;
	private EditText mGenreText;
	private ArrayList<String> formats = new ArrayList<String>();

	private ArrayAdapter<String> spinnerAdapter;
	private Button mConfirmButton;
	private Button mCancelButton;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;
	private ImageView mImageView;
	private Float rating = Float.parseFloat("0");
	private boolean read = false;
	private int anthology_num = CatalogueDBAdapter.ANTHOLOGY_NO;
	private String notes = "";
	private String location = "";
	private String read_start = "";
	private String read_end = "";
	private boolean signed = false;
	
	private String added_series = "";
	private String added_title = "";
	private String added_author = "";
	public static String ADDED_SERIES = "ADDED_SERIES";
	public static String ADDED_TITLE = "ADDED_TITLE";
	public static String ADDED_AUTHOR = "ADDED_AUTHOR";
	
	private static final int DELETE_ID = 1;
	private static final int ADD_PHOTO = 2;
	private static final int ROTATE_THUMBNAIL = 3;
	private static final int ADD_GALLERY = 4;
	private static final int GONE = 8;
	private static final int DATE_DIALOG_ID = 1;
	
	public static final String BOOKSHELF_SEPERATOR = ", ";
	
	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
	}
	
	protected ArrayList<String> getAuthors() {
		ArrayList<String> author_list = new ArrayList<String>();
		Cursor author_cur = mDbHelper.fetchAllAuthorsIgnoreBooks();
		startManagingCursor(author_cur);
		while (author_cur.moveToNext()) {
			String name = author_cur.getString(author_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
			author_list.add(name);
		}
		author_cur.close();
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
		series_cur.close();
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
		publisher_cur.close();
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
			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (mRowId == null) {
				getRowId();
			}
			
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
			setContentView(R.layout.edit_book);
			
			ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthors());
			mAuthorText = (AutoCompleteTextView) findViewById(R.id.author);
			mAuthorText.setAdapter(author_adapter);
			field_visibility = mPrefs.getBoolean(visibility_prefix + CatalogueDBAdapter.KEY_AUTHOR, true);
			if (field_visibility == false) {
				mAuthorText.setVisibility(GONE);
			}
			
			mTitleText = (EditText) findViewById(R.id.title);
			field_visibility = mPrefs.getBoolean(visibility_prefix + CatalogueDBAdapter.KEY_TITLE, true);
			if (field_visibility == false) {
				mTitleText.setVisibility(GONE);
			}
			
			mIsbnText = (EditText) findViewById(R.id.isbn);
			field_visibility = mPrefs.getBoolean(visibility_prefix + CatalogueDBAdapter.KEY_ISBN, true);
			if (field_visibility == false) {
				mIsbnText.setVisibility(GONE);
			}
			
			ArrayAdapter<String> publisher_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getPublishers());
			mPublisherText = (AutoCompleteTextView) findViewById(R.id.publisher);
			mPublisherText.setAdapter(publisher_adapter);
			field_visibility = mPrefs.getBoolean(visibility_prefix + CatalogueDBAdapter.KEY_PUBLISHER, true);
			if (field_visibility == false) {
				mPublisherText.setVisibility(GONE);
			}
			
			Button mDate_publishedButton = (Button) findViewById(R.id.date_published_button);
			mDate_publishedButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(DATE_DIALOG_ID);
				}
			});
			mDate_publishedText = (TextView) findViewById(R.id.date_published);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "date_published", true);
			if (field_visibility == false) {
				mDate_publishedButton.setVisibility(GONE);
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
				mAnthologyCheckBox.setVisibility(GONE);
			}
			
			mDescriptionText = (EditText) findViewById(R.id.description);
			field_visibility = mPrefs.getBoolean(visibility_prefix + CatalogueDBAdapter.KEY_DESCRIPTION, true);
			if (field_visibility == false) {
				mDescriptionText.setVisibility(GONE);
			}
			
			mGenreText = (EditText) findViewById(R.id.genre);
			field_visibility = mPrefs.getBoolean(visibility_prefix + CatalogueDBAdapter.KEY_GENRE, true);
			if (field_visibility == false) {
				mGenreText.setVisibility(GONE);
			}
			
			mFormatText = (Spinner) findViewById(R.id.format);
			spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
			spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			mFormatText.setAdapter(spinnerAdapter);
			formats.add(getString(R.string.paperback));
			formats.add(getString(R.string.hardcover)); 
			formats.add(getString(R.string.ebook));
			formats.add(getString(R.string.audiobook));
			formats.add(getString(R.string.guide));
			for (int i=0; i<formats.size(); i++) {
				spinnerAdapter.add(formats.get(i)); 
			}
			field_visibility = mPrefs.getBoolean(visibility_prefix + "format", true);
			if (field_visibility == false) {
				mFormatText.setVisibility(GONE);
			}
			
			mConfirmButton = (Button) findViewById(R.id.confirm);
			mCancelButton = (Button) findViewById(R.id.cancel);
			
			mImageView = (ImageView) findViewById(R.id.row_img);
			field_visibility = mPrefs.getBoolean(visibility_prefix + "thumbnail", true);
			if (field_visibility == false) {
				mImageView.setVisibility(GONE);
			}
			
			/* Setup the Bookshelf Multiselect*/
			mBookshelfButton = (Button) findViewById(R.id.bookshelf);
			mBookshelfText = (TextView) findViewById(R.id.bookshelf_text);
			
			mBookshelfButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					Cursor bookshelves_for_book = null;
					if (mRowId == null) {
						bookshelves_for_book = mDbHelper.fetchAllBookshelves();
					} else {
						bookshelves_for_book = mDbHelper.fetchAllBookshelves(mRowId);
					}
					final Dialog dialog = new Dialog(BookEditFields.this);
					dialog.setContentView(R.layout.bookshelf_dialog);
					dialog.setTitle(R.string.bookshelf_title);
					Button button = (Button) dialog.findViewById(R.id.bookshelf_dialog_button);
					button.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							dialog.dismiss();
						}
					});
					
					LinearLayout root = (LinearLayout) dialog.findViewById(R.id.bookshelf_dialog_root);
					
					if (bookshelves_for_book.moveToFirst()) { 
						do { 
							final CheckBox cb = new CheckBox(BookEditFields.this);
							boolean checked = false;
							String db_bookshelf = bookshelves_for_book.getString(bookshelves_for_book.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF));
							if (mBookshelfText.getText().toString().indexOf(db_bookshelf + BOOKSHELF_SEPERATOR) > -1) {
								checked = true;
							}
							cb.setChecked(checked);
							cb.setHintTextColor(Color.WHITE);
							cb.setHint(db_bookshelf);
							cb.setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View v) {
									String name = cb.getHint() + BOOKSHELF_SEPERATOR;
									if (cb.isChecked()) {
										mBookshelfText.setText(mBookshelfText.getText().toString() + name);
									} else {
										String text = mBookshelfText.getText().toString();
										int index = text.indexOf(name);
										if (index > -1) {
											text = text.substring(0, index) + text.substring(index + name.length());
										}
										mBookshelfText.setText(text);
									}
									return;
								}
							});
							root.addView(cb, root.getChildCount()-1);
						} 
						while (bookshelves_for_book.moveToNext()); 
					} 
					bookshelves_for_book.close();
					
					dialog.show();
					
					//while (bookshelves_for_book2.moveToNext()) {
						//new ArrayAdapter(this, android.R.layout.simple_list_item_1, new String[] { "Apple", "Peach","Banane" });

						//root.addView(child);
					//}
					
					/*
					AlertDialog.Builder builder = new AlertDialog.Builder(BookEditFields.this);
					builder.setTitle(R.string.bookshelf_label);
					builder.setMultiChoiceItems(bookshelves_for_book2, CatalogueDBAdapter.KEY_BOOK, CatalogueDBAdapter.KEY_BOOKSHELF, new OnMultiChoiceClickListener() {
						public void onClick(DialogInterface dialog, int item, boolean isChecked) {
							((AlertDialog) dialog).getListView().setItemChecked(item, isChecked);
							bookshelves_for_book2.moveToPosition(item);
							String bookshelf = bookshelves_for_book2.getString(bookshelves_for_book2.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF));
							//String bookshelf = "foo";
							Toast.makeText(BookEditFields.this, item + " " + bookshelf, Toast.LENGTH_SHORT).show();
							mBookshelfText.setText(mBookshelfText.getText().toString() + bookshelf + "\n");
						}
					});
					AlertDialog alertDialog = builder.create();
					alertDialog.setButton(BookEditFields.this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							return;
						}
					}); 
					alertDialog.show();
					*/
				}
			});
			
			
			
			
			//spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
			//spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			//mBookshelfText.setAdapter(spinnerAdapter);
			
			//if (bookshelves.moveToFirst()) { 
			//	do { 
			//		spinnerAdapter.add(bookshelves.getString(1)); 
			//	} 
			//	while (bookshelves.moveToNext()); 
			//} 
			//bookshelves.close(); // close the cursor
			field_visibility = mPrefs.getBoolean(visibility_prefix + "bookshelf", true);
			if (field_visibility == false) {
				mBookshelfText.setVisibility(GONE);
			}
			
			populateFields();
			
			mImageView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
				@Override
				public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
					MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete_thumb);
					delete.setIcon(android.R.drawable.ic_menu_delete);
					MenuItem add_photo = menu.add(0, ADD_PHOTO, 0, R.string.menu_add_thumb_photo);
					add_photo.setIcon(android.R.drawable.ic_menu_camera);
					MenuItem add_gallery = menu.add(0, ADD_GALLERY, 0, R.string.menu_add_thumb_gallery);
					add_gallery.setIcon(android.R.drawable.ic_menu_gallery);
					MenuItem rotate_photo = menu.add(0, ROTATE_THUMBNAIL, 0, R.string.menu_rotate_thumbnail);
					rotate_photo.setIcon(android.R.drawable.ic_menu_rotate);
				}
			});
			
			// mConfirmButton.setOnClickListener - This is set in populate fields. The behaviour changes depending on if it is adding or saving
			mCancelButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					setResult(RESULT_OK);
					finish();
				}
			});
		} catch (SQLException e) {
			//Log.e("Book Catalogue", "Unknown error " + e.toString());
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DATE_DIALOG_ID:
			try {
				String dateString = (String) mDate_publishedText.getText();
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
				return new DatePickerDialog(this, mDateSetListener, yyyy, mm, dd);
			} catch (Exception e) {
				// use the default date
			}
		}
		return null;
	}	
	// the callback received when the user "sets" the date in the dialog
	private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {
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
			mDate_publishedText.setText(year + "-" + mm + "-" + dd);
		}
	};
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case DELETE_ID:
			deleteThumbnail(mRowId);
			populateFields();
			return true;
		case ROTATE_THUMBNAIL:
			rotateThumbnail(mRowId);
			populateFields();
			return true;
		case ADD_PHOTO:
			Intent pintent = null;
			pintent = new Intent("android.media.action.IMAGE_CAPTURE");
			startActivityForResult(pintent, ADD_PHOTO);
			return true;
		case ADD_GALLERY:
			Intent gintent = new Intent();
			gintent.setType("image/*");
			gintent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(gintent, "Select Picture"), ADD_GALLERY);
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
			File thumb = CatalogueDBAdapter.fetchThumbnail(id);
			thumb.delete();
			
			thumb = CatalogueDBAdapter.fetchThumbnail(id);
			thumb.delete();
		} catch (Exception e) {
			// something has gone wrong. 
		}
	}
	
	/**
	 * Rotate the thumbnail 90 degrees clockwise
	 * 
	 * @param id
	 */
	private void rotateThumbnail(long id) {
		String filename = CatalogueDBAdapter.fetchThumbnailFilename(mRowId, false);
		if (filename != null) {
			Bitmap x = BitmapFactory.decodeFile(filename);
			Matrix m = new Matrix();
			m.postRotate(90);
			x = Bitmap.createBitmap(x, 0, 0, x.getWidth(), x.getHeight(), m, true);
			/* Create a file to copy the thumbnail into */
			FileOutputStream f = null;
			try {
				f = new FileOutputStream(filename);
			} catch (FileNotFoundException e) {
				//Log.e("Book Catalogue", "Thumbnail cannot be written");
				return;
			}
			x.compress(Bitmap.CompressFormat.PNG, 100, f);
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
			Cursor book = mDbHelper.fetchBookById(mRowId);
			startManagingCursor(book);
			if (book != null) {
				book.moveToFirst();
			}
			String title = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)); 
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + title);
			
			mAuthorText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED)));
			mTitleText.setText(title);
			mIsbnText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN)));
			mPublisherText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER)));
			String[] date = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)).split("-");
			try {
				String yyyy = date[0];
				int month = Integer.parseInt(date[1]);
				month = month + 1;
				String mm = month + "";
				if (mm.length() == 1) {
					mm = "0" + mm;
				}
				String dd = date[2];
				if (dd.length() == 1) {
					dd = "0" + dd;
				}
				mDate_publishedText.setText(yyyy + "-" + mm + "-" + dd);
			} catch (Exception e) {
				
			}
			
			//Display the selected bookshelves
			Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(mRowId);
			String bookshelves_text = "";
			while (bookshelves.moveToNext()) {
				bookshelves_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR;
			}
			mBookshelfText.setText(bookshelves_text);
			bookshelves.close();
			
			mSeriesText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES)));
			mSeriesNumText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM)));
			mListPriceText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE)));
			mPagesText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES)));
			String format = book.getString(book.getColumnIndex(CatalogueDBAdapter.KEY_FORMAT));
			for (int i=0; i<formats.size(); i++) {
				if (formats.get(i).equals(format)) {
					mFormatText.setSelection(i);
				}
			}
			mDescriptionText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DESCRIPTION)));
			mGenreText.setText(book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GENRE)));
			
			anthology_num = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
			if (anthology_num == 0) {
				mAnthologyCheckBox.setChecked(false);
			} else {
				mAnthologyCheckBox.setChecked(true);
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
							intent.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
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
			
			// On save it should close this view
			mConfirmButton.setText(R.string.confirm_save);
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
			
			String filename = CatalogueDBAdapter.fetchThumbnailFilename(mRowId, false);
			if (filename == null) {
				mImageView.setImageResource(android.R.drawable.ic_menu_help);
			} else {
				mImageView.setImageBitmap(BitmapFactory.decodeFile(filename));
			}
			rating = book.getFloat(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING));
			read = (book.getInt(book.getColumnIndex(CatalogueDBAdapter.KEY_READ))==0 ? false:true);
			notes = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES));
			location = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION));
			read_start = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START));
			read_end = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END));
			signed = (book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))==0 ? false:true);
			book.close();
		} else if (extras != null) {
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
			// From the ISBN Search (add)
			try {
				//String[] book = {0=author, 1=title, 2=isbn, 3=publisher, 4=date_published, 5=rating,  6=bookshelf, 
				//	7=read, 8=series, 9=pages, 10=series_num, 11=list_price, 12=anthology, 13=location, 14=read_start, 
				//	15=read_end, 16=audiobook, 17=signed, 18=description, 19=genre};
				String[] book = extras.getStringArray("book");
				mAuthorText.setText(book[0]);
				mTitleText.setText(book[1]);
				mIsbnText.setText(book[2]);
				mPublisherText.setText(book[3]);
				try {
					mDate_publishedText.setText(book[4]);
				} catch (ArrayIndexOutOfBoundsException e) {
					//do nothing
				} catch (NumberFormatException e) {
					//do nothing
				}
				
				//Display the selected bookshelves
				Log.e("BC", BookCatalogue.bookshelf);
				if (BookCatalogue.bookshelf.equals("All Books")) {
					Cursor bookshelves = mDbHelper.fetchBookshelf(1);
					bookshelves.moveToFirst();
					mBookshelfText.setText(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR);
					bookshelves.close();
				} else {
					mBookshelfText.setText(BookCatalogue.bookshelf + BOOKSHELF_SEPERATOR);
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
				String formatValue = book[16];
				for (int i=0; i<formats.size(); i++) {
					if (formats.get(i) == formatValue) {
						mFormatText.setSelection(i);
					}
				}
				mDescriptionText.setText(book[18]);
				mGenreText.setText(book[19]);
			} catch (NullPointerException e) {
				// do nothing
			}
				
			// On add it should reload this view
			mConfirmButton.setText(R.string.confirm_add);
			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					saveState();
					Intent edit = new Intent(BookEditFields.this, BookEdit.class);
					edit.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
					edit.putExtra(BookEdit.TAB, BookEdit.TAB_EDIT_NOTES);
					startActivity(edit);
					
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
			
			String filename = CatalogueDBAdapter.fetchThumbnailFilename(0, false);
			if (filename == null) {
				mImageView.setImageResource(android.R.drawable.ic_menu_help);
			} else {
				mImageView.setImageBitmap(BitmapFactory.decodeFile(filename));
			}
		} else {
			// Manual Add
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
			
			//Display the selected bookshelves
			Log.e("BC", BookCatalogue.bookshelf);
			if (BookCatalogue.bookshelf.equals("All Books")) {
				Cursor bookshelves = mDbHelper.fetchBookshelf(1);
				bookshelves.moveToFirst();
				mBookshelfText.setText(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR);
				bookshelves.close();
			} else {
				mBookshelfText.setText(BookCatalogue.bookshelf + BOOKSHELF_SEPERATOR);
			}
			
			mConfirmButton.setText(R.string.confirm_add);
			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					saveState();
					Intent edit = new Intent(BookEditFields.this, BookEdit.class);
					edit.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
					edit.putExtra(BookEdit.TAB, BookEdit.TAB_EDIT_NOTES);
					startActivity(edit);
					
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
		String date_published = "";
		try {
			date_published = (String) mDate_publishedText.getText();
			String[] date = date_published.split("-");
			int yyyy = Integer.parseInt(date[0]);
			int mm = Integer.parseInt(date[1])-1;
			int dd = Integer.parseInt(date[2]);
			date_published = yyyy + "-" + mm + "-" + dd;
		} catch (Exception e) {
			//do nothing
		}
		String bookshelf = mBookshelfText.getText().toString(); 
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
		String format = "";
		try {
			format = spinnerAdapter.getItem(mFormatText.getSelectedItemPosition());
		} catch (IndexOutOfBoundsException e) {
			//do nothing - format is hidden
		}
		String description = mDescriptionText.getText().toString();
		String genre = mGenreText.getText().toString();
		
		if (mRowId == null || mRowId == 0) {
			/* Check if the book currently exists */
			if (!isbn.equals("")) {
				Cursor book = mDbHelper.fetchBookByISBN(isbn);
				int rows = book.getCount();
				if (rows != 0) {
					Toast.makeText(this, R.string.book_exists, Toast.LENGTH_LONG).show();
					return;
				}
				book.close(); // close the cursor
			}

			long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, format, signed, description, genre);
			if (id > 0) {
				mRowId = id;
				File thumb = CatalogueDBAdapter.fetchThumbnail(0);
				File real = CatalogueDBAdapter.fetchThumbnail(id);
				thumb.renameTo(real);
			}
		} else {
			mDbHelper.updateBook(mRowId, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, format, signed, description, genre);
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
				String filename = CatalogueDBAdapter.fetchThumbnailFilename(mRowId, true);
				Bitmap x = (Bitmap) intent.getExtras().get("data");
				Matrix m = new Matrix();
				m.postRotate(90);
				x = Bitmap.createBitmap(x, 0, 0, x.getWidth(), x.getHeight(), m, true);
				/* Create a file to copy the thumbnail into */
				FileOutputStream f = null;
				try {
					f = new FileOutputStream(filename);
				} catch (FileNotFoundException e) {
					//Log.e("Book Catalogue", "Thumbnail cannot be written");
					return;
				}
				
				x.compress(Bitmap.CompressFormat.PNG, 100, f);
			}
			return;
		case ADD_GALLERY:
			if (resultCode == Activity.RESULT_OK){
				Uri selectedImageUri = intent.getData();
				
				String[] projection = { MediaStore.Images.Media.DATA };
				Cursor cursor = managedQuery(selectedImageUri, projection, null, null, null);
				int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
				cursor.moveToFirst();
				String selectedImagePath = cursor.getString(column_index);
				
				File thumb = new File(selectedImagePath);
				File real = CatalogueDBAdapter.fetchThumbnail(mRowId);
				try {
					copyFile(thumb, real);
				} catch (IOException e) {
					//do nothing - error to be handled later
				}
			}
			return;
		}
	}
	
	private void copyFile(File src, File dst) throws IOException {
		FileChannel inChannel = new FileInputStream(src).getChannel();
		FileChannel outChannel = new FileOutputStream(dst).getChannel();
		try {
			inChannel.transferTo(0, inChannel.size(), outChannel);
		} finally {
			if (inChannel != null)
				inChannel.close();
			if (outChannel != null)
				outChannel.close();
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}
}
