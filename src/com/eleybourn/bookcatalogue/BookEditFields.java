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
import java.util.Iterator;

import android.content.ContentValues;

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
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.Toast;
import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;

public class BookEditFields extends Activity {

	private Fields mFields = null;

	private Button mConfirmButton;
	private Button mCancelButton;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;
	private Integer mThumbEditSize;
	private Integer mThumbZoomSize;
	
	private String added_genre = "";
	private String added_series = "";
	private String added_title = "";
	private String added_author = "";
	public static String ADDED_GENRE = "ADDED_GENRE";
	public static String ADDED_SERIES = "ADDED_SERIES";
	public static String ADDED_TITLE = "ADDED_TITLE";
	public static String ADDED_AUTHOR = "ADDED_AUTHOR";
	
	// Target size of a thumbnail in edit dialog and zoom dialog (bbox dim)
	private static final int MAX_EDIT_THUMBNAIL_SIZE=256;
	private static final int MAX_ZOOM_THUMBNAIL_SIZE=1024;

	private static final int DELETE_ID = 1;
	private static final int ADD_PHOTO = 2;
	private static final int ROTATE_THUMB_SUBMENU = 3;
	private static final int ROTATE_THUMB_CW = 31;
	private static final int ROTATE_THUMB_CCW = 32;
	private static final int ROTATE_THUMB_180 = 33;
	private static final int ADD_GALLERY = 4;
	private static final int ZOOM_THUMB = 5;
	private static final int DATE_DIALOG_ID = 1;
	private static final int ZOOM_THUMB_DIALOG_ID = 2;
	
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
		try {
			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (mRowId == null) {
				getRowId();
			}
			
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();

			// See how big the display is and use that to set bitmap sizes
			android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);
			// Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension 
			mThumbEditSize = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(metrics.widthPixels, metrics.heightPixels)/3);
			// Zoom size is minimum of MAX_ZOOM_THUMBNAIL_SIZE and largest screen dimension.
			mThumbZoomSize = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(metrics.widthPixels, metrics.heightPixels));

			setContentView(R.layout.edit_book);

			// Generic validators; if field-specific defaults are needed, create a new one.
			FieldValidator booleanValidator = new Fields.BooleanValidator("0");
			FieldValidator integerValidator = new Fields.IntegerValidator("0");
			FieldValidator nonBlankValidator = new Fields.NonBlankValidator();
			FieldValidator blankOrIntegerValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.IntegerValidator("0"));
			FieldValidator blankOrFloatValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.FloatValidator("0.0"));
			FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());

			mFields = new Fields(this);

			mFields.add(R.id.author, CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, nonBlankValidator);

			// Title has some post-processing on the text, to move leading 'A', 'The' etc to the end.
			// While we could do it in a formatter, it it not really a display-oriented function and
			// is handled in preprocessing in the database layer since it also needs to be applied
			// to imported record etc.
			mFields.add(R.id.title, CatalogueDBAdapter.KEY_TITLE, nonBlankValidator);

			mFields.add(R.id.isbn, CatalogueDBAdapter.KEY_ISBN, null);
			mFields.add(R.id.publisher, CatalogueDBAdapter.KEY_PUBLISHER, null);
			mFields.add(R.id.date_published_button, "", CatalogueDBAdapter.KEY_DATE_PUBLISHED, null);
			mFields.add(R.id.date_published, CatalogueDBAdapter.KEY_DATE_PUBLISHED, CatalogueDBAdapter.KEY_DATE_PUBLISHED, 
							blankOrDateValidator, new Fields.DateFieldFormatter());

			mFields.add(R.id.series, CatalogueDBAdapter.KEY_SERIES, CatalogueDBAdapter.KEY_SERIES, null);
			mFields.add(R.id.series_num, "series_num", CatalogueDBAdapter.KEY_SERIES, blankOrIntegerValidator);

			// Ensure that series number is blank if series is blank 
			mFields.addCrossValidator(new Fields.FieldCrossValidator() {
				public void validate(Fields fields, android.content.ContentValues values) {
					if (!values.getAsString(CatalogueDBAdapter.KEY_SERIES).equals(""))
						return;
					// For blank series, series-number must also be blank
					String num = values.getAsString(CatalogueDBAdapter.KEY_SERIES_NUM);
					if (!num.equals("")) {
						throw new Fields.ValidatorException(R.string.vldt_series_num_must_be_blank,new Object[]{num});							
					}
				}
			});

			mFields.add(R.id.list_price, "list_price", blankOrFloatValidator);
			mFields.add(R.id.pages, "pages", blankOrIntegerValidator);
			
			// Anthology needs special handling, and we use a formatter to do this. If the original value was 0 or 1, then 
			// setting/clearing it here should just set the new value to 0 or 1.
			// However...if if the original value was 2, then we want setting/clearing to alternate between 2 and 0, not 1
			// and 0.
			//
			// So, despite if being a checkbox, we use an integerValidator and use a special formatter.
			// We also store it in the tag field so that it is automatically serialized with the activity.
			mFields.add(R.id.anthology, CatalogueDBAdapter.KEY_ANTHOLOGY, integerValidator, new Fields.FieldFormatter() {
					private static final int TAG_ORIG_VAL = 1;
					public String format(Field f, String s) {
						// Save the original value, if its an integer
						try {
							Integer i = Integer.parseInt(s);
							f.view.setTag(TAG_ORIG_VAL,i);
						} catch (Exception e) {
							f.view.setTag(TAG_ORIG_VAL,0);
						}
						// Just pass the string onwards to the accessor.
						return s;
					}
					public String extract(Field f, String s) {
						// Parse the string the CheckBox returns us (0 or 1)
						Integer i = Integer.parseInt(s);
						Integer orig = (Integer) f.view.getTag(TAG_ORIG_VAL);
						if (i != 0 && orig > 0) {
							// If non-zero, and original was non-zero, re-use original
							return orig.toString();
						} else {
							// Just return what we got.
							return s;
						}
					}
			});

			mFields.add(R.id.description, CatalogueDBAdapter.KEY_DESCRIPTION, null);

			mFields.add(R.id.genre, CatalogueDBAdapter.KEY_GENRE, null);
			mFields.add(R.id.row_img, "", "thumbnail", null);
			Field formatField = mFields.add(R.id.format, CatalogueDBAdapter.KEY_FORMAT, null);

			mFields.add(R.id.bookshelf_text, "bookshelf_text", null).doNoFetch = true; // Output-only field
			Field bookshelfButtonFe = mFields.add(R.id.bookshelf, "", null);

			ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthors());
			mFields.setAdapter(R.id.author, author_adapter);

			ArrayAdapter<String> publisher_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getPublishers());
			mFields.setAdapter(R.id.publisher, publisher_adapter);

			mFields.setListener(R.id.date_published_button, new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(DATE_DIALOG_ID);
				}
			});

			ArrayAdapter<String> series_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getSeries());
			mFields.setAdapter(R.id.series, series_adapter);

			Spinner formatSpinner = (Spinner) formatField.view;
			ArrayAdapter<String> formatArr = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
			formatArr.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			formatSpinner.setAdapter(formatArr);
			formatArr.add(getString(R.string.format1));
			formatArr.add(getString(R.string.format2));
			formatArr.add(getString(R.string.format3));
			formatArr.add(getString(R.string.format4));
			formatArr.add(getString(R.string.format5));

			mConfirmButton = (Button) findViewById(R.id.confirm);
			mCancelButton = (Button) findViewById(R.id.cancel);

			bookshelfButtonFe.view.setOnClickListener(new View.OnClickListener() {
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

							Field fe = mFields.get(R.id.bookshelf_text);
							if (fe.getValue().toString().indexOf(db_bookshelf + BOOKSHELF_SEPERATOR) > -1) {
								checked = true;
							}
							cb.setChecked(checked);
							cb.setHintTextColor(Color.WHITE);
							cb.setHint(db_bookshelf);
							cb.setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View v) {
									String name = cb.getHint() + BOOKSHELF_SEPERATOR;
									Field fe = mFields.get(R.id.bookshelf_text);
									if (cb.isChecked()) {
										fe.setValue(fe.getValue().toString()+name);
									} else {
										String text = fe.getValue().toString();
										int index = text.indexOf(name);
										if (index > -1) {
											text = text.substring(0, index) + text.substring(index + name.length());
										}
										fe.setValue(text);
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

			// Don't populate the fields if we are just restoring from a save
			if (savedInstanceState == null) {
				populateFields();
			} else {
				// The thumbnail image is not automatically preserved, so reload it.
				ImageView iv = (ImageView) mFields.get(R.id.row_img).view;
				CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);				
			}

			// Setup the Save/Add/Anthology UI elements
			setupUi();

			// Set up the thumbnail context menu.
			mFields.get(R.id.row_img).view.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
				@Override
				public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
					MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete_thumb);
					delete.setIcon(android.R.drawable.ic_menu_delete);
					MenuItem add_photo = menu.add(0, ADD_PHOTO, 1, R.string.menu_add_thumb_photo);
					add_photo.setIcon(android.R.drawable.ic_menu_camera);
					MenuItem add_gallery = menu.add(0, ADD_GALLERY, 2, R.string.menu_add_thumb_gallery);
					add_gallery.setIcon(android.R.drawable.ic_menu_gallery);

					// Submenu for rotate
					android.view.SubMenu submenu = menu.addSubMenu(0, ROTATE_THUMB_SUBMENU, 3, R.string.menu_rotate_thumb);
					add_gallery.setIcon(android.R.drawable.ic_menu_rotate);

					MenuItem rotate_photo_cw = submenu.add(0, ROTATE_THUMB_CW, 1, R.string.menu_rotate_thumb_cw);
					rotate_photo_cw.setIcon(android.R.drawable.ic_menu_rotate);
					MenuItem rotate_photo_ccw = submenu.add(0, ROTATE_THUMB_CCW, 2, R.string.menu_rotate_thumb_ccw);
					rotate_photo_ccw.setIcon(android.R.drawable.ic_menu_rotate);
					MenuItem rotate_photo_180 = submenu.add(0, ROTATE_THUMB_180, 3, R.string.menu_rotate_thumb_180);
					rotate_photo_180.setIcon(android.R.drawable.ic_menu_rotate);

					MenuItem zoom_thumb = menu.add(0, ZOOM_THUMB, 4, R.string.menu_zoom_thumb);
					zoom_thumb.setIcon(android.R.drawable.ic_menu_zoom);
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
		Dialog dialog;

		switch (id) {
		case DATE_DIALOG_ID:
			try {
				String dateString = (String) mFields.get(R.id.date_published).getValue().toString();
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
				dialog = new DatePickerDialog(this, mDateSetListener, yyyy, mm, dd);
			} catch (Exception e) {
				// use the default date
				dialog = null;
			}
			break;
		case ZOOM_THUMB_DIALOG_ID:
			// Create dialog and set layout
			dialog = new Dialog(BookEditFields.this);
			dialog.setContentView(R.layout.zoom_thumb_dialog);

			// Check if we have a file and/or it is valid
			String filename = CatalogueDBAdapter.fetchThumbnailFilename(mRowId, false);

			if (filename == null) {
				dialog.setTitle("Cover is not set");
			} else {
				BitmapFactory.Options opt = new BitmapFactory.Options();
				opt.inJustDecodeBounds = true;
			    BitmapFactory.decodeFile( filename, opt );

			    // If no size info, assume file bad and return appropriate icon
			    if ( opt.outHeight <= 0 || opt.outWidth <= 0 ) {
			    	dialog.setTitle("Cover corrupt");
				} else {
					dialog.setTitle("Cover Detail");
					ImageView cover = new ImageView(this);
					CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, cover, mThumbZoomSize, mThumbZoomSize, true);
					cover.setAdjustViewBounds(true);
				    LayoutParams lp = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
				    dialog.addContentView(cover, lp);
				}
			}
			break;
		default:
			dialog = null;
		}
		return dialog;
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
			mFields.get(R.id.date_published).setValue(year + "-" + mm + "-" + dd);
		}
	};
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ImageView iv = (ImageView)mFields.get(R.id.row_img).view;
		switch(item.getItemId()) {
		case DELETE_ID:
			deleteThumbnail(mRowId);
			CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);
			return true;
		case ROTATE_THUMB_SUBMENU:
			// Just a submenu; skip
			return true;
		case ROTATE_THUMB_CW:
			rotateThumbnail(mRowId, 90);
			CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);
			return true;
		case ROTATE_THUMB_CCW:
			rotateThumbnail(mRowId, -90);
			CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);
			return true;
		case ROTATE_THUMB_180:
			rotateThumbnail(mRowId, 180);
			CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);
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
		case ZOOM_THUMB:
			showDialog(ZOOM_THUMB_DIALOG_ID);
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
	 * Rotate the thumbnail a specified amount
	 * 
	 * @param id
	 */
	private void rotateThumbnail(long id, long angle) {

		Bitmap bm = CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, null, mThumbZoomSize*2, mThumbZoomSize*2, true);
		if (bm == null)
			return;

		Matrix m = new Matrix();
		m.postRotate(angle);
		bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
		/* Create a file to copy the thumbnail into */
		FileOutputStream f = null;
		try {
			String filename = CatalogueDBAdapter.fetchThumbnailFilename(mRowId, false);
			f = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			//Log.e("Book Catalogue", "Thumbnail cannot be written");
			return;
		}
		bm.compress(Bitmap.CompressFormat.PNG, 100, f);				
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

			// Set any field that has a 'column' non blank.
			mFields.setFromCursor(book);

			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + mFields.get(R.id.title).getValue().toString());

			//Display the selected bookshelves
			Field bookshelfTextFe = mFields.get(R.id.bookshelf_text);
			Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(mRowId);
			String bookshelves_text = "";
			while (bookshelves.moveToNext()) {
				bookshelves_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR;
			}
			bookshelves.close();
			bookshelfTextFe.setValue(bookshelves_text);

			Integer anthNo = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
			mFields.get(R.id.anthology).setValue(anthNo.toString());

			ImageView iv = (ImageView) mFields.get(R.id.row_img).view;
			CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);

			book.close();
		} else if (extras != null) {
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
			// From the ISBN Search (add)
			try {
				//String[] book = {0=author, 1=title, 2=isbn, 3=publisher, 4=date_published, 5=rating,  6=bookshelf, 
				//	7=read, 8=series, 9=pages, 10=series_num, 11=list_price, 12=anthology, 13=location, 14=read_start, 
				//	15=read_end, 16=audiobook, 17=signed, 18=description, 19=genre};
				if (extras.containsKey("book")) {
					String[] book = extras.getStringArray("book");
					mFields.get(R.id.author).setValue(book[0]);
					mFields.get(R.id.title).setValue(book[1]);
					mFields.get(R.id.isbn).setValue(book[2]);
					mFields.get(R.id.publisher).setValue(book[3]);
					try {
						mFields.get(R.id.date_published).setValue(book[4]);
					} catch (ArrayIndexOutOfBoundsException e) {
						//do nothing
					} catch (NumberFormatException e) {
						//do nothing
					}

					//Display the selected bookshelves
					if (BookCatalogue.bookshelf.equals("All Books")) {
						Cursor bookshelves = mDbHelper.fetchBookshelf(1);
						bookshelves.moveToFirst();
						mFields.get(R.id.bookshelf_text).setValue(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR);
						bookshelves.close();
					} else {
						mFields.get(R.id.bookshelf_text).setValue(BookCatalogue.bookshelf + BOOKSHELF_SEPERATOR);
					}
					mFields.get(R.id.series).setValue(book[8]);
					mFields.get(R.id.series_num).setValue(book[10]);
					try {
						//just in case - there was an exception earlier, but it should be fixed
						mFields.get(R.id.list_price).setValue(book[11]);
					} catch (Exception e) {
						//do nothing
					}
					mFields.get(R.id.pages).setValue(book[9]);

					mFields.get(R.id.anthology).setValue(book[12]);
					mFields.get(R.id.format).setValue(book[16]);
					mFields.get(R.id.description).setValue(book[18]);
					mFields.get(R.id.genre).setValue(book[19]);
				} else {
					ContentValues values = (ContentValues)extras.getParcelable("bookData");
					Iterator<Fields.Field> i = mFields.iterator();
					while(i.hasNext()) {
						Fields.Field f = i.next();
						if (!f.column.equals("") && values.containsKey(f.column)) {
							try {
								f.setValue(values.getAsString(f.column));								
							} catch (Exception e) {
								android.util.Log.e("BookEditFields","Populate field " + f.column + " failed: " + e.getMessage());
							}
						}
					}
					//Display the selected bookshelves
					if (BookCatalogue.bookshelf.equals("All Books")) {
						Cursor bookshelves = mDbHelper.fetchBookshelf(1);
						bookshelves.moveToFirst();
						mFields.get(R.id.bookshelf_text).setValue(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR);
						bookshelves.close();
					} else {
						mFields.get(R.id.bookshelf_text).setValue(BookCatalogue.bookshelf + BOOKSHELF_SEPERATOR);
					}
				}
				

			} catch (NullPointerException e) {
				// do nothing
			}

			ImageView iv = (ImageView) mFields.get(R.id.row_img).view;
			CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);

		} else {
			// Manual Add
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));

			//Display the selected bookshelves
			if (BookCatalogue.bookshelf.equals("All Books")) {
				Cursor bookshelves = mDbHelper.fetchBookshelf(1);
				bookshelves.moveToFirst();
				mFields.get(R.id.bookshelf_text).setValue(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR);
				bookshelves.close();
			} else {
				mFields.get(R.id.bookshelf_text).setValue(BookCatalogue.bookshelf + BOOKSHELF_SEPERATOR);
			}			
		}
	}

	/**
	 * Validate the current data in all fields that have validators. Display any errors.
	 * 
	 * @param values The values to use
	 * 
	 * @return Boolean success or failure.
	 */
	private boolean validate(ContentValues values) {
		if (!mFields.validate(values)) {
			Toast.makeText(getParent(), mFields.getValidationExceptionMessage(getResources()), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	private void setupUi() {

		if (mRowId != null && mRowId > 0) {
			mConfirmButton.setText(R.string.confirm_save);

			CheckBox cb = (CheckBox)mFields.get(R.id.anthology).view;

			cb.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					ContentValues values = new ContentValues();
					if (!validate(values))
						return;
					saveState(values);

					CheckBox cb = (CheckBox) view;
					try {
						TabHost tabHost = ((TabActivity) getParent()).getTabHost();  // The activity TabHost
						if (cb.isChecked()) {
							Resources res = getResources();
							TabHost.TabSpec spec;  // Reusable TabSpec for each tab
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
		} else {
			mConfirmButton.setText(R.string.confirm_add);
		}

		mConfirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				ContentValues values = new ContentValues();
				if (!validate(values))
					return;
				boolean wasNew = (mRowId == null || mRowId == 0);

				saveState(values);
				if (wasNew) {
					Intent edit = new Intent(BookEditFields.this, BookEdit.class);
					edit.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
					edit.putExtra(BookEdit.TAB, BookEdit.TAB_EDIT_NOTES);
					startActivity(edit);
				}
				Intent i = new Intent();
				i.putExtra(ADDED_GENRE, added_genre);
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
	private void saveState(android.content.ContentValues values) {

		
		if (mRowId == null || mRowId == 0) {
			String isbn = values.getAsString(CatalogueDBAdapter.KEY_ISBN);
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

			long id = mDbHelper.createBook(values);

			if (id > 0) {
				mRowId = id;
				File thumb = CatalogueDBAdapter.fetchThumbnail(0);
				File real = CatalogueDBAdapter.fetchThumbnail(id);
				thumb.renameTo(real);
			}
		} else {
			mDbHelper.updateBook(mRowId, values);
		}
		/* These are global variables that will be sent via intent back to the list view */
		added_author = values.getAsString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
		added_title = values.getAsString(CatalogueDBAdapter.KEY_TITLE);
		added_series = values.getAsString(CatalogueDBAdapter.KEY_SERIES);
		added_genre = values.getAsString(CatalogueDBAdapter.KEY_GENRE);
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

				// Update the ImageView with the new image
				ImageView iv = (ImageView)mFields.get(R.id.row_img).view;
				CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);				
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
				// Update the ImageView with the new image
				ImageView iv = (ImageView)mFields.get(R.id.row_img).view;
				CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);				
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
