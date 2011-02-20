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
import java.io.StreamCorruptedException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.Toast;
import android.widget.ViewSwitcher.ViewFactory;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.LibraryThingManager.ImageSizes;

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

	public static final int ACTIVITY_EDIT_AUTHORS = 1000;
	public static final int ACTIVITY_EDIT_SERIES = 1001;
	
	// Target size of a thumbnail in edit dialog and zoom dialog (bbox dim)
	private static final int MAX_EDIT_THUMBNAIL_SIZE=256;
	private static final int MAX_ZOOM_THUMBNAIL_SIZE=1024;

	private static final int DELETE_ID = 1;
	private static final int REPLACE_THUMB_SUBMENU = 2;
	private static final int ADD_PHOTO = 21;
	private static final int ADD_GALLERY = 22;
	private static final int SHOW_ALT_COVERS = 23;
	private static final int ROTATE_THUMB_SUBMENU = 3;
	private static final int ROTATE_THUMB_CW = 31;
	private static final int ROTATE_THUMB_CCW = 32;
	private static final int ROTATE_THUMB_180 = 33;
	private static final int ZOOM_THUMB = 5;
	private static final int DATE_DIALOG_ID = 1;
	private static final int ZOOM_THUMB_DIALOG_ID = 2;
	
	public static final String BOOKSHELF_SEPERATOR = ", ";
	
	private ArrayList<Author> mAuthorList = null;
	private ArrayList<Series> mSeriesList = null;

	private android.util.DisplayMetrics mMetrics;

	// Global value for values from UI. Recreated periodically; at least 
	// in saveState(). Needs to be global so an Alert can be displayed 
	// during a save.
	private Bundle mStateValues = null;

	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
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
			mMetrics = new android.util.DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
			// Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension 
			mThumbEditSize = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels)/3);
			// Zoom size is minimum of MAX_ZOOM_THUMBNAIL_SIZE and largest screen dimension.
			mThumbZoomSize = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels));

			setContentView(R.layout.edit_book);

			// Generic validators; if field-specific defaults are needed, create a new one.
			FieldValidator integerValidator = new Fields.IntegerValidator("0");
			FieldValidator nonBlankValidator = new Fields.NonBlankValidator();
			FieldValidator blankOrIntegerValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.IntegerValidator("0"));
			FieldValidator blankOrFloatValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.FloatValidator("0.0"));
			FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());

			mFields = new Fields(this);

			mFields.add(R.id.author, "", CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, nonBlankValidator);
			{
				View v = mFields.getField(R.id.author).view;
				v.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i = new Intent(BookEditFields.this, EditAuthorList.class);
						i.putExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
						i.putExtra("title_label", CatalogueDBAdapter.KEY_TITLE);
						i.putExtra("title", mFields.getField(R.id.title).getValue().toString());
						startActivityForResult(i, ACTIVITY_EDIT_AUTHORS);						
					}
				});
			}

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

			mFields.add(R.id.series, CatalogueDBAdapter.KEY_SERIES_NAME, CatalogueDBAdapter.KEY_SERIES_NAME, null);
			{
				View v = mFields.getField(R.id.series).view;
				v.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i = new Intent(BookEditFields.this, EditSeriesList.class);
						i.putExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);
						i.putExtra("title_label", CatalogueDBAdapter.KEY_TITLE);
						i.putExtra("title", mFields.getField(R.id.title).getValue().toString());
						startActivityForResult(i, ACTIVITY_EDIT_SERIES);						
					}
				});
			}

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
					public String format(Field f, String s) {
						// Save the original value, if its an integer
						try {
							Integer i = Integer.parseInt(s);
							f.view.setTag(R.id.TAG_ORIGINAL_VALUE,i);
						} catch (Exception e) {
							f.view.setTag(R.id.TAG_ORIGINAL_VALUE,0);
						}
						// Just pass the string onwards to the accessor.
						return s;
					}
					public String extract(Field f, String s) {
						// Parse the string the CheckBox returns us (0 or 1)
						Integer i = Integer.parseInt(s);
						Integer orig = (Integer) f.view.getTag(R.id.TAG_ORIGINAL_VALUE);
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

			//ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthors());
			//mFields.setAdapter(R.id.author, author_adapter);

			ArrayAdapter<String> publisher_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getPublishers());
			mFields.setAdapter(R.id.publisher, publisher_adapter);

			mFields.setListener(R.id.date_published_button, new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(DATE_DIALOG_ID);
				}
			});

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

							Field fe = mFields.getField(R.id.bookshelf_text);
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
									Field fe = mFields.getField(R.id.bookshelf_text);
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
				ImageView iv = (ImageView) mFields.getField(R.id.row_img).view;
				CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);				
				// Author and series lists
				mAuthorList = savedInstanceState.getParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
				fixupAuthorList();	// Will update related display fields/button
				mSeriesList = savedInstanceState.getParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY);
				fixupSeriesList();	// Will update related display fields/button
				mFields.getField(R.id.date_published).setValue(savedInstanceState.getString(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
				mFields.getField(R.id.bookshelf_text).setValue(savedInstanceState.getString("bookshelf_text"));
			}

			// Setup the Save/Add/Anthology UI elements
			setupUi();

			// Set up the thumbnail context menu.
			mFields.getField(R.id.row_img).view.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
				@Override
				public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
					MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete_thumb);
					delete.setIcon(android.R.drawable.ic_menu_delete);

					// Submenu for rotate
					android.view.SubMenu replaceSubmenu = menu.addSubMenu(0, REPLACE_THUMB_SUBMENU, 2, R.string.menu_replace_thumb);
					replaceSubmenu.setIcon(android.R.drawable.ic_menu_gallery);


					MenuItem add_photo = replaceSubmenu.add(0, ADD_PHOTO, 1, R.string.menu_add_thumb_photo);
					add_photo.setIcon(android.R.drawable.ic_menu_camera);
					MenuItem add_gallery = replaceSubmenu.add(0, ADD_GALLERY, 2, R.string.menu_add_thumb_gallery);
					add_gallery.setIcon(android.R.drawable.ic_menu_gallery);
					MenuItem alt_covers = replaceSubmenu.add(0, SHOW_ALT_COVERS, 3, R.string.menu_thumb_alt_editions);
					alt_covers.setIcon(android.R.drawable.ic_menu_zoom);

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
				String dateString = (String) mFields.getField(R.id.date_published).getValue().toString();
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
				dialog.setTitle(getResources().getString(R.string.cover_not_set));
			} else {
				BitmapFactory.Options opt = new BitmapFactory.Options();
				opt.inJustDecodeBounds = true;
			    BitmapFactory.decodeFile( filename, opt );

			    // If no size info, assume file bad and return appropriate icon
			    if ( opt.outHeight <= 0 || opt.outWidth <= 0 ) {
			    	dialog.setTitle(getResources().getString(R.string.cover_corrupt));
				} else {
					dialog.setTitle(getResources().getString(R.string.cover_detail));
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
			mFields.getField(R.id.date_published).setValue(year + "-" + mm + "-" + dd);
		}
	};
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ImageView iv = (ImageView)mFields.getField(R.id.row_img).view;
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
			startActivityForResult(Intent.createChooser(gintent, getResources().getString(R.string.select_picture)), ADD_GALLERY);
			return true;
		case ZOOM_THUMB:
			showDialog(ZOOM_THUMB_DIALOG_ID);
			return true;
		case SHOW_ALT_COVERS:
			showEditionCovers();
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

			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + mFields.getField(R.id.title).getValue().toString());

			//Display the selected bookshelves
			Field bookshelfTextFe = mFields.getField(R.id.bookshelf_text);
			Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(mRowId);
			String bookshelves_text = "";
			while (bookshelves.moveToNext()) {
				bookshelves_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR;
			}
			bookshelves.close();
			bookshelfTextFe.setValue(bookshelves_text);

			Integer anthNo = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
			mFields.getField(R.id.anthology).setValue(anthNo.toString());

			ImageView iv = (ImageView) mFields.getField(R.id.row_img).view;
			CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);

			book.close();
			
			mAuthorList = mDbHelper.getBookAuthorList(mRowId);
			mSeriesList = mDbHelper.getBookSeriesList(mRowId);

		} else if (extras != null) {
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
			// From the ISBN Search (add)
			try {
				if (extras.containsKey("book")) {
					throw new RuntimeException("[book] array passed in Intent");
				} else {
					Bundle values = (Bundle)extras.getParcelable("bookData");
					Iterator<Fields.Field> i = mFields.iterator();
					while(i.hasNext()) {
						Fields.Field f = i.next();
						if (!f.column.equals("") && values.containsKey(f.column)) {
							try {
								f.setValue(values.getString(f.column));								
							} catch (Exception e) {
								android.util.Log.e("BookEditFields","Populate field " + f.column + " failed: " + e.getMessage());
							}
						}
					}
					//Display the selected bookshelves
					if (BookCatalogue.bookshelf.equals("All Books")) {
						Cursor bookshelves = mDbHelper.fetchBookshelf(1);
						bookshelves.moveToFirst();
						mFields.getField(R.id.bookshelf_text).setValue(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR);
						bookshelves.close();
					} else {
						mFields.getField(R.id.bookshelf_text).setValue(BookCatalogue.bookshelf + BOOKSHELF_SEPERATOR);
					}

					mAuthorList = values.getParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
					mSeriesList = values.getParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY);

				}
				
			} catch (NullPointerException e) {
				Log.e("BC","Failed to load data", e);
				// do nothing
			}

			setCoverImage();

		} else {
			// Manual Add
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));

			//Display the selected bookshelves
			if (BookCatalogue.bookshelf.equals("All Books")) {
				Cursor bookshelves = mDbHelper.fetchBookshelf(1);
				bookshelves.moveToFirst();
				mFields.getField(R.id.bookshelf_text).setValue(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BOOKSHELF_SEPERATOR);
				bookshelves.close();
			} else {
				mFields.getField(R.id.bookshelf_text).setValue(BookCatalogue.bookshelf + BOOKSHELF_SEPERATOR);
			}			
			mAuthorList = new ArrayList<Author>();
			mSeriesList = new ArrayList<Series>();
		}
		/**
		 * TODO: Add EditAuthor to edit family and given names
		 * TODO: Add EditBookSeries to change series and series num
		 * TODO: Add EditSeries to change series name
		 */

		fixupAuthorList();
		fixupSeriesList();

	}

	private void setCoverImage() {
		ImageView iv = (ImageView) mFields.getField(R.id.row_img).view;
		CatalogueDBAdapter.fetchThumbnailIntoImageView(mRowId, iv, mThumbEditSize, mThumbEditSize, true);		
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

	private void showAnthologyTab() {
		CheckBox cb = (CheckBox)mFields.getField(R.id.anthology).view;
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

	private interface PostSaveAction {
		public void success();
		public void failure();
	}

	private class DoAnthologyAction implements PostSaveAction {
		public void success() {
			showAnthologyTab();
		}
		public void failure() {
			// Do nothing
		}
	}

	private class DoConfirmAction implements PostSaveAction {
		boolean wasNew;

		DoConfirmAction(boolean wasNew) {
			this.wasNew = wasNew;
		}

		public void success() {
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
		public void failure() {
			// Do nothing
		}
	}

	private void setupUi() {

		if (mRowId != null && mRowId > 0) {
			mConfirmButton.setText(R.string.confirm_save);

			CheckBox cb = (CheckBox)mFields.getField(R.id.anthology).view;

			cb.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					saveState(new DoAnthologyAction());
				}
			});
		} else {
			mConfirmButton.setText(R.string.confirm_add);
		}

		mConfirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				boolean wasNew = (mRowId == null || mRowId == 0);
				saveState(new DoConfirmAction(wasNew));
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
		// DONT FORGET TO UPDATE onCreate to read these values back.
		// Need to save local data that is not stored in editable views
		outState.putParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
		outState.putParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);
		// ...including special text stored in TextViews and the like
		outState.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, mFields.getField(R.id.date_published).getValue().toString());
		outState.putString("bookshelf_text", mFields.getField(R.id.bookshelf_text).getValue().toString());
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	private class SaveAlert extends AlertDialog {

		protected SaveAlert() {
			super(BookEditFields.this);
		}
	}

	/**
	 * This will save a book into the database, by either updating or created a book.
	 * Minor modifications will be made to the strings:
	 * 	- Titles will be rewords so 'a', 'the', 'an' will be moved to the end of the 
	 *    string (this is only done for NEW books)
	 *  
	 * 	- Date published will be converted from a date to a string
	 * 
	 * Thumbnails will also be saved to the correct location 
	 * 
	 * It will check if the book already exists (isbn search) if you are creating a book;
	 * if so the user will be prompted to confirm.
	 * 
	 * In all cases, once the book is added/created, or not, the appropriate method
	 * of the passed nextStep parameter will be executed. Passing nextStep is necessary
	 * because this method may return after displaying a dialogue.
	 * 
	 * @param nextStep		The next step to be executed on success/failure.
	 * 
	 * @throws IOException 
	 */
	private void saveState(final PostSaveAction nextStep) {

		mStateValues = new Bundle();

		// Ignore validation failures; we still validate to get the current values.
		if (!validate(mStateValues)) {
			//nextStep.failure();
			//return;
		}

		mStateValues.putParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
		mStateValues.putParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);

		if (mRowId == null || mRowId == 0) {
			String isbn = mStateValues.getString(CatalogueDBAdapter.KEY_ISBN);
			/* Check if the book currently exists */
			if (!isbn.equals("")) {
				Cursor book = mDbHelper.fetchBookByISBN(isbn);
				int rows = book.getCount();
				book.close(); // close the cursor
				if (rows != 0) {
					/*
					 * If is exists, show a dialog and use it to perform the next action, according to the
					 * users choice.
					 */
					SaveAlert alert = new SaveAlert();
					alert.setMessage(getResources().getString(R.string.duplicate_book_message));
					alert.setTitle(R.string.duplicate_book_title);
					alert.setIcon(android.R.drawable.ic_menu_info_details);
					alert.setButton(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							updateOrCreate();
							nextStep.success();
							return;
						}
					}); 
					alert.setButton2(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							nextStep.failure();
							return;
						}
					}); 
					alert.show();
					return;
				}
			}
		}

		// No special actions required...just do it.
		updateOrCreate();
		nextStep.success();
		return;
	}

	private void updateOrCreate() {
		if (mRowId == null || mRowId == 0) {
			long id = mDbHelper.createBook(mStateValues);

			if (id > 0) {
				mRowId = id;
				File thumb = CatalogueDBAdapter.fetchThumbnail(0);
				File real = CatalogueDBAdapter.fetchThumbnail(id);
				thumb.renameTo(real);
			}
		} else {
			mDbHelper.updateBook(mRowId, mStateValues);
		}

		/* These are global variables that will be sent via intent back to the list view, if added/created */
		try {
			ArrayList<Author> authors = mStateValues.getParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
			added_author = authors.get(0).getSortName();
		} catch (Exception e) {};
		try {
			ArrayList<Author> series = mStateValues.getParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY);
			added_series = series.get(0).getSortName();
		} catch (Exception e) {};

		added_title = mStateValues.getString(CatalogueDBAdapter.KEY_TITLE);
		added_genre = mStateValues.getString(CatalogueDBAdapter.KEY_GENRE);
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
				setCoverImage();
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
				setCoverImage();
			}
			return;
		case ACTIVITY_EDIT_AUTHORS:
			if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)){
				mAuthorList = intent.getParcelableArrayListExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
			} else {
				// Even though the dialog was terminated, some authors MAY have been updated/added.
				for(Author a : mAuthorList) {
					mDbHelper.refreshAuthor(a);
				}
			}
			fixupAuthorList();
		case ACTIVITY_EDIT_SERIES:
			if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY)){
				mSeriesList = intent.getParcelableArrayListExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY);
				fixupSeriesList();
			}
		}
	}
	
	private void fixupAuthorList() {

		String newText;
		if (mAuthorList.size() == 0)
			newText = getResources().getString(R.string.set_authors);
		else {
			Utils.pruneList(mDbHelper, mAuthorList);
			newText = mAuthorList.get(0).getDisplayName();
			if (mAuthorList.size() > 1)
				newText += " " + getResources().getString(R.string.and_others);
		}
		mFields.getField(R.id.author).setValue(newText);		
	}

	private void fixupSeriesList() {

		String newText;
		if (mSeriesList.size() == 0)
			newText = getResources().getString(R.string.set_series);
		else {
			Utils.pruneList(mDbHelper, mSeriesList);
			newText = mSeriesList.get(0).getDisplayName();
			if (mSeriesList.size() > 1)
				newText += " " + getResources().getString(R.string.and_others);
		}
		mFields.getField(R.id.series).setValue(newText);		
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

	private void showEditionCovers() {
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.select_edition_cover);
		dialog.setTitle("Select cover");
		final FileManager fileManager = new FileManager();

		final ImageSwitcher switcher = (ImageSwitcher) dialog.findViewById(R.id.switcher);

		final ArrayList<String> editions = LibraryThingManager.searchEditions(mFields.getField(R.id.isbn).getValue().toString());
		final int previewSize = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels)/5);

		Gallery gallery = (Gallery) dialog.findViewById(R.id.gallery);
		ImageAdapter adapter = new ImageAdapter(this, editions, fileManager, previewSize);
		gallery.setAdapter(adapter);
		//gallery.setOnItemSelectedListener(this);
		gallery.setHorizontalScrollBarEnabled(true);
		gallery.setMinimumWidth(1000);
		gallery.setSpacing(previewSize/10);

		gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
	        public void onItemClick(AdapterView parent, View v, int position, long id) {
	            Toast.makeText(BookEditFields.this, "" + position, Toast.LENGTH_SHORT).show();
	            File file = fileManager.get(editions.get(position), ImageSizes.LARGE);
	            Drawable d = new BitmapDrawable(Utils.fetchFileIntoImageView(file, null, previewSize*4, previewSize*4, false));
	            switcher.setImageDrawable(d);
	            switcher.setTag(file.getAbsolutePath());
	        }
	    });

		switcher.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				File bookFile = CatalogueDBAdapter.fetchThumbnail(mRowId);
				Object newSpec = switcher.getTag();
				if (newSpec != null) {
					File newFile = new File((String)newSpec);					
					newFile.renameTo(bookFile);
					setCoverImage();
				}
				dialog.dismiss();
			}});
		
		
		dialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				fileManager.purge();
			}});

		switcher.setFactory(new ViewFactory() {
			@Override
			public View makeView() {
		        ImageView i = new ImageView(BookEditFields.this);
		        i.setBackgroundColor(0xFF000000);
		        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
		        i.setLayoutParams(new ImageSwitcher.LayoutParams(ImageSwitcher.LayoutParams.WRAP_CONTENT,
		        		ImageSwitcher.LayoutParams.WRAP_CONTENT));
		        return i;
			}});

		dialog.show();

	}

	private class FileManager {
		private Bundle mFiles = new Bundle();
		public File get(String isbn, ImageSizes size) {
		    String filespec;
		    String key = isbn + "_" + size;
		    if (!mFiles.containsKey(key)) {
		    	filespec = LibraryThingManager.getCoverImage(isbn, null, size);
			    mFiles.putString(key, filespec);
		    } else {
		    	filespec = mFiles.getString(key);
		    }
		    File file = new File(filespec);
			return file;
		}

		public void purge() {
			try {
				for(String k : mFiles.keySet()) {
					String filespec = mFiles.getString(k);
					File file = new File(filespec);
					if (file.exists())
						file.delete();
				}				
				mFiles.clear();
			} catch (Exception e) {
				Log.e("BC", " Error purging files", e);
			}
		}
	}

	public class ImageAdapter extends BaseAdapter {
	    int mGalleryItemBackground;
	    ArrayList<String> mEditions;
	    Bundle mFiles;
	    private int mPreviewSize;
	    private FileManager mFileManager = new FileManager();
	    
	    public ImageAdapter(Context c, ArrayList<String> editions, FileManager fileManager, int previewSize) {
	        mContext = c;
	        mEditions = editions;
	        mFileManager = fileManager;
	        mPreviewSize = previewSize;
	        // See res/values/attrs.xml for the <declare-styleable> that defines
	        // Gallery1.
	        //TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
	        //mGalleryItemBackground = a.getResourceId(
	        //				R.styleable.Gallery1_android_galleryItemBackground, 0);
	        //a.recycle();
	    }
	
		public int getCount() {
		    return mEditions.size();
		}
		
		public Object getItem(int position) {
		    return position;
		}
		
		public long getItemId(int position) {
		    return position;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
		    String isbn = mEditions.get(position);
		    ImageView i = new ImageView(mContext);

		    //URL url = new URL(LibraryThingManager.getCoverImageUrl(isbn, ImageSizes.SMALL));
		    //i.setImageURI(url.toURI());

		    File file = mFileManager.get(isbn, ImageSizes.SMALL);
		    file.deleteOnExit();
		    Utils.fetchFileIntoImageView(file, i, mPreviewSize, mPreviewSize, false);
		    i.setScaleType(ImageView.ScaleType.FIT_XY);
		    return i;
		}
		
	    private Context mContext;

	}
	
}
