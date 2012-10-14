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
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookEdit.OnRestoreTabInstanceStateListener;
import com.eleybourn.bookcatalogue.CoverBrowser.OnImageSelectedListener;
import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.dialogs.BigDatePicker;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditor;


public class BookEditFields extends Activity implements OnRestoreTabInstanceStateListener {

	/**
	 * Class to implement a clickable span of text and call a listener when text os clicked.
	 */
	static class InternalSpan extends ClickableSpan {  
	    OnClickListener mListener;  
	    
	    public InternalSpan(OnClickListener listener) {  
	        mListener = listener;  
	    }  
	  
	    @Override  
	    public void onClick(View widget) {  
	        mListener.onClick(widget);  
	    }  
	} 

	private Fields mFields = null;
	private boolean mIsDirty = false;

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
	public static String ADDED_HAS_INFO = "ADDED_HAS_INFO";
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
	private static final int CROP_THUMB = 6;
	private static final int DATE_DIALOG_ID = 1;
	private static final int ZOOM_THUMB_DIALOG_ID = 2;
	private static final int DESCRIPTION_DIALOG_ID = 3;
	private static final int CAMERA_RESULT = 41;
	private static final int CROP_RESULT = 42;
	
	public static final Character BOOKSHELF_SEPERATOR = ',';
	
	private ArrayList<Author> mAuthorList = null;
	private ArrayList<Series> mSeriesList = null;
	CoverBrowser mCoverBrowser = null;

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
		try {
			while (publisher_cur.moveToNext()) {
				String publisher = publisher_cur.getString(publisher_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER));
				publisher_list.add(publisher);
			}
		} finally {
			publisher_cur.close();			
		}
		return publisher_list;
	}
	
	protected ArrayList<String> getGenres() {
		ArrayList<String> genre_list = new ArrayList<String>();
		Cursor genre_cur = mDbHelper.fetchAllGenres("");
		try {
			while (genre_cur.moveToNext()) {
				String genre = genre_cur.getString(genre_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
				genre_list.add(genre);
			}
		} finally {
			genre_cur.close();			
		}
		return genre_list;
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

			if (savedInstanceState != null) {
				mIsDirty = savedInstanceState.getBoolean("Dirty");
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
			FieldValidator blankOrFloatValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.FloatValidator("0.00"));
			FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());

			mFields = new Fields(this);

			mFields.add(R.id.author, "", CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, nonBlankValidator);
			{
				View v = findViewById(R.id.author);
				v.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i = new Intent(BookEditFields.this, EditAuthorList.class);
						i.putExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
						i.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
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
				View v = findViewById(R.id.series);
				v.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent i = new Intent(BookEditFields.this, EditSeriesList.class);
						i.putExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);
						i.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
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
							ViewTagger.setTag(f.getView(), R.id.TAG_ORIGINAL_VALUE,i);
						} catch (Exception e) {
							ViewTagger.setTag(f.getView(), R.id.TAG_ORIGINAL_VALUE,0);
						}
						// Just pass the string onwards to the accessor.
						return s;
					}
					public String extract(Field f, String s) {
						// Parse the string the CheckBox returns us (0 or 1)
						Integer i = Integer.parseInt(s);
						Integer orig = (Integer) ViewTagger.getTag(f.getView(), R.id.TAG_ORIGINAL_VALUE);
						try {
							if (i != 0 && orig > 0) {
								// If non-zero, and original was non-zero, re-use original
								return orig.toString();
							} else {
								// Just return what we got.
								return s;
							}
						} catch (NullPointerException e) {
							return s;
						}
					}
			});

			mFields.add(R.id.description, CatalogueDBAdapter.KEY_DESCRIPTION, null);
			
			mFields.add(R.id.genre, CatalogueDBAdapter.KEY_GENRE, null);
			mFields.add(R.id.row_img, "", "thumbnail", null);
			final Field formatField = mFields.add(R.id.format, CatalogueDBAdapter.KEY_FORMAT, null);
			mFields.add(R.id.format_button, "", CatalogueDBAdapter.KEY_FORMAT, null);
			
			mFields.add(R.id.bookshelf_text, "bookshelf_text", null).doNoFetch = true; // Output-only field
			Field bookshelfButtonFe = mFields.add(R.id.bookshelf, "", null);
			ArrayAdapter<String> publisher_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getPublishers());
			mFields.setAdapter(R.id.publisher, publisher_adapter);
			ArrayAdapter<String> genre_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getGenres());
			mFields.setAdapter(R.id.genre, genre_adapter);
			
			mFields.setListener(R.id.date_published_button, new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(DATE_DIALOG_ID);
				}
			});

			// Get the formats to use in the AutoComplete stuff
			ArrayAdapter<String> formatAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mDbHelper.getFormats());
			AutoCompleteTextView formatText = (AutoCompleteTextView) formatField.getView();
			formatText.setAdapter(formatAdapter);
			// Get the drop-down button for the formats list and setup dialog
			ImageView formatButton = (ImageView)findViewById(R.id.format_button);
			formatButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					StandardDialogs.selectStringDialog(getLayoutInflater(), getString(R.string.format), mDbHelper.getFormats(), formatField.getValue().toString(), new SimpleDialogOnClickListener() {
						@Override
						public void onClick(SimpleDialogItem item) {
							formatField.setValue(item.toString());
						}});
				}});

			//Spinner formatSpinner = (Spinner) formatField.getView();
			//ArrayAdapter<String> formatArr = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
			//formatArr.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			//formatSpinner.setAdapter(formatArr);
			//formatArr.add(getString(R.string.format1));
			//formatArr.add(getString(R.string.format2));
			//formatArr.add(getString(R.string.format3));
			//formatArr.add(getString(R.string.format4));
			//formatArr.add(getString(R.string.format5));

			mConfirmButton = (Button) findViewById(R.id.confirm);
			mCancelButton = (Button) findViewById(R.id.cancel);

			bookshelfButtonFe.getView().setOnClickListener(new View.OnClickListener() {
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
						final String shelves = BOOKSHELF_SEPERATOR + mFields.getField(R.id.bookshelf_text).getTag().toString() + BOOKSHELF_SEPERATOR;
						do { 
							final CheckBox cb = new CheckBox(BookEditFields.this);
							boolean checked = false;
							String db_bookshelf = bookshelves_for_book.getString(bookshelves_for_book.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)).trim();
							String db_encoded_bookshelf = Utils.encodeListItem(db_bookshelf, BOOKSHELF_SEPERATOR);
							if (shelves.indexOf(BOOKSHELF_SEPERATOR + db_encoded_bookshelf + BOOKSHELF_SEPERATOR) > -1) {
								checked = true;
							}
							cb.setChecked(checked);
							cb.setHintTextColor(Color.WHITE);
							cb.setHint(db_bookshelf);
							cb.setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View v) {
									String hint = cb.getHint() + "";
									String name = hint.trim();
									String encoded_name = Utils.encodeListItem(name, BOOKSHELF_SEPERATOR);
									Field fe = mFields.getField(R.id.bookshelf_text);
									// If box is checked, then we just append to list
									if (cb.isChecked()) {
										String curr = fe.getValue().toString();
										String list = fe.getTag().toString();
										if (curr == null || curr.equals("")) {
											curr = name;
											list = encoded_name;
										} else {
											curr += ", " + name;
											list += BOOKSHELF_SEPERATOR + encoded_name;
										}
										fe.setValue(curr);
										fe.setTag(list);
									} else {
										// Get the underlying list
										ArrayList<String> shelves = Utils.decodeList(fe.getTag().toString(), BOOKSHELF_SEPERATOR);
										// Start a new list
										String newList = "";
										String newText = "";
										for(String s : shelves) {
											// If item in underlying list is non-blank...
											if (s != null && !s.equals("")) {
												// If item in underlying list does not match...
												if (!s.equalsIgnoreCase(name)) {
													// Encode item
													String item = Utils.encodeListItem(s, BOOKSHELF_SEPERATOR);
													// Append to list (or set to only element if list empty)
													if (newList.equals("")) {
														newList = Utils.encodeListItem(s, BOOKSHELF_SEPERATOR);
														newText = s;
													} else {
														newList += BOOKSHELF_SEPERATOR + item;
														newText += ", " + s;
													}
												}
											}
										}
										fe.setValue(newText);
										fe.setTag(newList);
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
				setCoverImage();
				// Author and series lists
				mAuthorList = (ArrayList<Author>) savedInstanceState.getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
				fixupAuthorList();	// Will update related display fields/button
				mSeriesList = (ArrayList<Series>) savedInstanceState.getSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY);
				fixupSeriesList();	// Will update related display fields/button
				mFields.getField(R.id.date_published).setValue(savedInstanceState.getString(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
				// Restore bookshelves
				Field fe = mFields.getField(R.id.bookshelf_text);
				fe.setValue(savedInstanceState.getString("bookshelf_text"));
				fe.setTag(savedInstanceState.getString("bookshelf_list"));
				// Restore description (it's a read-only text and not managed by Android)
				fe = mFields.getField(R.id.description);
				fe.setValue(savedInstanceState.getString(CatalogueDBAdapter.KEY_DESCRIPTION));
			}

			// Build the label for the book description if this is first time, otherwise will be built later
			if (savedInstanceState == null)
				buildDescription();

			// Setup the Save/Add/Anthology UI elements
			setupUi();

			// Set up the thumbnail context menu.
			mFields.getField(R.id.row_img).getView().setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
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

					MenuItem crop_thumb = menu.add(0, CROP_THUMB, 4, R.string.menu_crop_thumb);
					crop_thumb.setIcon(android.R.drawable.ic_menu_crop);

				}
			});

			// mConfirmButton.setOnClickListener - This is set in populate fields. The behaviour changes depending on if it is adding or saving
			mCancelButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					// Cleanup because we may have made global changes
					mDbHelper.purgeAuthors();
					mDbHelper.purgeSeries();
					// We're done.
					setResult(RESULT_OK);

					if (mIsDirty) {
						StandardDialogs.showConfirmUnsavedEditsDialog(BookEditFields.this);
					} else {
						finish();
					}
				}
			});
			
			try {
				Utils.fixFocusSettings(findViewById(android.R.id.content));				
			} catch (Exception e) {
				// Log, but ignore. This is a non-critical feature that prevents crashes when the
				// 'next' key is pressed and some views have been hidden.
				Logger.logError(e);
			}

			Utils.initBackground(R.drawable.bc_background_gradient_dim, this);

			mFields.setAfterFieldChangeListener(new AfterFieldChangeListener(){
				@Override
				public void afterFieldChange(Field field, String newValue) {
					mIsDirty = true;
				}});

		} catch (IndexOutOfBoundsException e) {
			Logger.logError(e);
		} catch (SQLException e) {
			Logger.logError(e);
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;

		switch (id) {
		case DATE_DIALOG_ID:
			try {
				Object o = mFields.getField(R.id.date_published).getValue();
				String dateString = o == null ? "" : o.toString();
				// get the current date
				final Calendar c = Calendar.getInstance();
				Integer yyyy = null; //c.get(Calendar.YEAR);
				Integer mm = null; //c.get(Calendar.MONTH);
				Integer dd = null; //c.get(Calendar.DAY_OF_MONTH);
				try {
					String[] date = dateString.split("-");
					yyyy = Integer.parseInt(date[0]);
					mm = Integer.parseInt(date[1]);
					dd = Integer.parseInt(date[2]);
				} catch (Exception e) {
					//do nothing
				}

				dialog = new BigDatePicker(this, mBigDateSetListener, yyyy, mm, dd);
				dialog.setTitle(R.string.date_published);
			} catch (Exception e) {
				Logger.logError(e);
				// use the default date
				dialog = null;
			}
			break;

		case DESCRIPTION_DIALOG_ID:
			dialog = new TextFieldEditor(this);
			dialog.setTitle(R.string.description);
			// The rest of the details will be set in onPrepareDialog
			break;

		case ZOOM_THUMB_DIALOG_ID:
			// Create dialog and set layout
			dialog = new Dialog(BookEditFields.this);
			dialog.setContentView(R.layout.zoom_thumb_dialog);

			// Check if we have a file and/or it is valid
			File thumbFile = getCoverFile();

			if (thumbFile == null || !thumbFile.exists()) {
				dialog.setTitle(getResources().getString(R.string.cover_not_set));
			} else {
				
				BitmapFactory.Options opt = new BitmapFactory.Options();
				opt.inJustDecodeBounds = true;
			    BitmapFactory.decodeFile( thumbFile.getAbsolutePath(), opt );

			    // If no size info, assume file bad and return appropriate icon
			    if ( opt.outHeight <= 0 || opt.outWidth <= 0 ) {
			    	dialog.setTitle(getResources().getString(R.string.cover_corrupt));
				} else {
					dialog.setTitle(getResources().getString(R.string.cover_detail));
					ImageView cover = new ImageView(this);
					Utils.fetchFileIntoImageView(thumbFile, cover, mThumbZoomSize, mThumbZoomSize, true);
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

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		System.out.println("Prep dialog " + id);

		switch (id) {
		case DATE_DIALOG_ID:
			try {
				Object o = mFields.getField(R.id.date_published).getValue();
				String dateString = o == null ? "" : o.toString();
				// get the current date
				final Calendar c = Calendar.getInstance();
				Integer yyyy = null; //c.get(Calendar.YEAR);
				Integer mm = null; //c.get(Calendar.MONTH);
				Integer dd = null; //c.get(Calendar.DAY_OF_MONTH);
				try {
					String[] date = dateString.split("-");
					yyyy = Integer.parseInt(date[0]);
					mm = Integer.parseInt(date[1]);
					dd = Integer.parseInt(date[2]);
				} catch (Exception e) {
					//do nothing
				}

				((BigDatePicker)dialog).setDate(yyyy, mm, dd);

			} catch (Exception e) {
				Logger.logError(e);
				// use the default date
				dialog = null;
			}
			break;

		case DESCRIPTION_DIALOG_ID:
			{
				TextFieldEditor dlg = (TextFieldEditor)dialog;

				Object o = mFields.getField(R.id.description).getValue();
				String description = (o == null? null : o.toString());
				dlg.setText(description);

				dlg.setOnEditListener(mOnDescriptionEditListener);
			}
			break;
		}
	}

	/**
	 * Object to handle changes to a description field.
	 */
	private TextFieldEditor.OnEditListener mOnDescriptionEditListener = new TextFieldEditor.OnEditListener(){
		@Override
		public void onSaved(TextFieldEditor dialog, String newText) {
			mFields.getField(R.id.description).setValue(newText);
			removeDialog(DESCRIPTION_DIALOG_ID);
		}
		@Override
		public void onCancel(TextFieldEditor dialog) {
			removeDialog(DESCRIPTION_DIALOG_ID);
		}};

	/**
	 * Get the File object for the cover of the book we are editing. If the boo
	 * is new, return the standard temp file.
	 */
	private File getCoverFile() {
		if (mRowId == null || mRowId == 0) 
			return CatalogueDBAdapter.getTempThumbnail();
		else
			return CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(mRowId));			
	}

	/**
	 *  The callback received when the user "sets" the date in the dialog.
	 *  
	 *  Build a full or partial date in SQL format
	 */
	private BigDatePicker.OnDateSetListener mBigDateSetListener = new BigDatePicker.OnDateSetListener() {
		public void onDateSet(BigDatePicker dialog, Integer year, Integer month, Integer day) {
			String value;
			if (year == null) {
				value = "";
			} else {
				value = String.format("%04d", year);
				if (month != null && month > 0) {
					String mm = month.toString();
					if (mm.length() == 1) {
						mm = "0" + mm;
					}

					value += "-" + mm;

					if (day != null && day > 0) {
						String dd = day.toString();
						if (dd.length() == 1) {
							dd = "0" + dd;
						}
						value += "-" + dd;
					}
				}
			}
				
			mFields.getField(R.id.date_published).setValue(value);
			dismissDialog(DATE_DIALOG_ID);
		}

		@Override
		public void onCancel(BigDatePicker dialog) {
			dismissDialog(DATE_DIALOG_ID);
		}
	};

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ImageView iv = (ImageView)findViewById(R.id.row_img);
		File thumbFile = getCoverFile();

		switch(item.getItemId()) {
		case DELETE_ID:
			deleteThumbnail(mRowId);
			Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
			return true;
		case ROTATE_THUMB_SUBMENU:
			// Just a submenu; skip
			return true;
		case ROTATE_THUMB_CW:
			rotateThumbnail(90);
			Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
			return true;
		case ROTATE_THUMB_CCW:
			rotateThumbnail(-90);
			Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
			return true;
		case ROTATE_THUMB_180:
			rotateThumbnail(180);
			Utils.fetchFileIntoImageView(thumbFile, iv, mThumbEditSize, mThumbEditSize, true);
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
		case CROP_THUMB:
			cropCoverImage(thumbFile);
			
//			Intent crop_intent = new Intent(this, CropCropImage.class);
//			// here you have to pass absolute path to your file
//			crop_intent.putExtra("image-path", thumbFile.getAbsolutePath());
//			crop_intent.putExtra("scale", true);
//			startActivityForResult(crop_intent, CAMERA_RESULT);
			return true;
		case SHOW_ALT_COVERS:
			String isbn = mFields.getField(R.id.isbn).getValue().toString();
			if (isbn == null || isbn.trim().length() == 0) {
				Toast.makeText(this, getResources().getString(R.string.editions_require_isbn), Toast.LENGTH_LONG).show();
			} else {
				mCoverBrowser = new CoverBrowser(this, mMetrics, isbn, mOnImageSelectedListener);
				mCoverBrowser.showEditionCovers();				
			}
			return true;
		}
		return super.onContextItemSelected(item);
	}
	
	private void cropCoverImage(File thumbFile) {
		Intent intent = new Intent("com.android.camera.action.CROP");
		// this will open any image file
		intent.setDataAndType(Uri.fromFile(new File(thumbFile.getAbsolutePath())), "image/*");
		intent.putExtra("crop", "true");
		// this defines the aspect ratio
		//intent.putExtra("aspectX", 1);
		//intent.putExtra("aspectY", 1);
		// this defines the output bitmap size
		//intent.putExtra("outputX", 3264);
		//intent.putExtra("outputY", 2448);
		//intent.putExtra("outputX", mThumbZoomSize*2);
		//intent.putExtra("outputY", mThumbZoomSize*2);
		intent.putExtra("scale", false);
		intent.putExtra("noFaceDetection", true);
		// true to return a Bitmap, false to directly save the cropped iamge
		intent.putExtra("return-data", false);
		//save output image in uri
		intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(thumbFile.getAbsolutePath() + ".cropped.jpg")));

		List<ResolveInfo> list = getPackageManager().queryIntentActivities( intent, 0 );
	    int size = list.size();
	    if (size == 0) {
	        Toast.makeText(this, "Can not find image crop app", Toast.LENGTH_SHORT).show();
	    } else {
			startActivityForResult(intent, CROP_RESULT);		    	
	    }
		
	}
	/**
	 * Delete the provided thumbnail from the sdcard
	 * 
	 * @param id The id of the book (and thumbnail) to delete
	 */
	private void deleteThumbnail(long id) {
		try {
			File thumbFile = getCoverFile();
			if (thumbFile != null && thumbFile.exists())
				thumbFile.delete();
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * Rotate the thumbnail a specified amount
	 * 
	 * @param id
	 */
	private void rotateThumbnail(long angle) {
		boolean retry = true;
		while (retry) {
			try {
				File thumbFile = getCoverFile();

				Bitmap origBm = Utils.fetchFileIntoImageView(thumbFile, null, mThumbZoomSize*2, mThumbZoomSize*2, true );
				if (origBm == null)
					return;

				Matrix m = new Matrix();
				m.postRotate(angle);
				Bitmap rotBm = Bitmap.createBitmap(origBm, 0, 0, origBm.getWidth(), origBm.getHeight(), m, true);
				if (rotBm != origBm) {
					origBm.recycle();
				}

				/* Create a file to copy the thumbnail into */
				FileOutputStream f = null;
				try {
					f = new FileOutputStream(thumbFile.getAbsoluteFile());
				} catch (FileNotFoundException e) {
					Logger.logError(e);
					return;
				}
				rotBm.compress(Bitmap.CompressFormat.PNG, 100, f);
				rotBm.recycle();
			} catch (java.lang.OutOfMemoryError e) {
				if (retry) {
					System.gc();
				} else {
					throw new RuntimeException(e);
				}
			}
			retry = false;
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
			Cursor bookshelves = null;
			try {
				if (book != null) {
					book.moveToFirst();
				}
				
				// Set any field that has a 'column' non blank.
				mFields.setFromCursor(book);
				
				getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + mFields.getField(R.id.title).getValue().toString());
				
				//Display the selected bookshelves
				Field bookshelfTextFe = mFields.getField(R.id.bookshelf_text);
				bookshelves = mDbHelper.fetchAllBookshelvesByBook(mRowId);
				String bookshelves_text = "";
				String bookshelves_list = "";
				while (bookshelves.moveToNext()) {
					String name = bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF));
					String encoded_name = Utils.encodeListItem(name, BOOKSHELF_SEPERATOR);
					if (bookshelves_text.equals("")) {
						bookshelves_text = name;
						bookshelves_list = encoded_name;
					} else {
						bookshelves_text += ", " + name;
						bookshelves_list += BOOKSHELF_SEPERATOR + encoded_name;
					}
				}
				bookshelfTextFe.setValue(bookshelves_text);
				bookshelfTextFe.setTag(bookshelves_list);

				Integer anthNo = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
				mFields.getField(R.id.anthology).setValue(anthNo.toString());
				
				ImageView iv = (ImageView) findViewById(R.id.row_img);
				Utils.fetchFileIntoImageView(getCoverFile(), iv, mThumbEditSize, mThumbEditSize, true );				
			} finally {	
				if (book != null)
					book.close();
				if (bookshelves != null)
					bookshelves.close();
			}
			
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
								String msg = "Populate field " + f.column + " failed: " + e.getMessage();
								Logger.logError(e, msg);
							}
						}
					}

					initDefaultShelf();

					// Author/Series
					mAuthorList = (ArrayList<Author>) values.getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
					mSeriesList = (ArrayList<Series>) values.getSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY);
				}
				
			} catch (NullPointerException e) {
				Logger.logError(e);
			}
			setCoverImage();
		} else {
			// Manual Add
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
			
			initDefaultShelf();

			// Author/Series
			mAuthorList = new ArrayList<Author>();
			mSeriesList = new ArrayList<Series>();
		}
		
		fixupAuthorList();
		fixupSeriesList();

	}
	
	/**
 	 * Use the currently selected bookshelf as default
	 */
	private void initDefaultShelf() {
		String currShelf = BookCatalogueApp.getAppPreferences().getString(BooksOnBookshelf.PREF_BOOKSHELF, "");
		if (currShelf.equals("")) {
			currShelf = mDbHelper.getBookshelfName(1);
		}
		String encoded_shelf = Utils.encodeListItem(currShelf, BOOKSHELF_SEPERATOR);
		Field fe = mFields.getField(R.id.bookshelf_text);
		fe.setValue(currShelf);
		fe.setTag(encoded_shelf);		
	}

	private void setCoverImage() {
		ImageView iv = (ImageView) findViewById(R.id.row_img);
		Utils.fetchFileIntoImageView(getCoverFile(), iv, mThumbEditSize, mThumbEditSize, true );		
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
		CheckBox cb = (CheckBox)findViewById(R.id.anthology);
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
				edit.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
				startActivity(edit);
			}
			Intent i = new Intent();
			i.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
			i.putExtra(ADDED_HAS_INFO, true);
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

			CheckBox cb = (CheckBox)findViewById(R.id.anthology);

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
	/**
	 * Method called when the containing TabActivity is running OnRestoreInstanceState; otherwise
	 * locally made changes in our own OnRestoreInstanceState may get overwritten
	 */
	public void restoreTabInstanceState(Bundle savedInstanceState) {
		buildDescription();
	}

	/**
	 * Setup the 'description' header field to have a clickable link.
	 */
	private void buildDescription() {
		// get the view
		final TextView tv = (TextView)findViewById(R.id.descriptionLabel);
		// Build the prefic text ('Description ')
		String baseText = getString(R.string.description) + " ";
		// Create the span ('Description (edit...)').
		SpannableString f = new SpannableString(baseText + "(" + getString(R.string.edit_lc_ellipsis) + ")");
		f.setSpan(new InternalSpan(new OnClickListener() {  
		        public void onClick(View v) {  
		        	showDialog(DESCRIPTION_DIALOG_ID);
		        }
		    }), baseText.length(), f.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

		// Quirks in Android mean old spans may be preserved; delete them
		clearOldInternalSpans(tv);
		// Set the text
		tv.setText(f);

		// Set the MovementMethod to allow clicks
		tv.setMovementMethod(LinkMovementMethod.getInstance());
		// Prevent focus...not precisely sure why, but sample code does this
		tv.setFocusable(false);
		// Set the colout to prevent flicker on click
		tv.setTextColor(this.getResources().getColor(android.R.color.primary_text_dark_nodisable));
	}

	private void clearOldInternalSpans(TextView tv) {
		CharSequence cs = tv.getText();
		if (cs instanceof Spannable) {
			final Spannable s = (Spannable)cs;
			InternalSpan[] spans = s.getSpans(0, tv.getText().length(), InternalSpan.class);
			for (int i = 0; i < spans.length; i++) {
			    s.removeSpan(spans[i]);
			}
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
		// DONT FORGET TO UPDATE onCreate to read these values back.
		// Need to save local data that is not stored in editable views
		outState.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
		outState.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);
		// ...including special text stored in TextViews and the like
		{
			Object o = mFields.getField(R.id.date_published).getValue();
			if (o != null)
				outState.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, o.toString());
		}

		Field fe = mFields.getField(R.id.bookshelf_text);
		outState.putString("bookshelf_list", fe.getTag().toString());
		outState.putString("bookshelf_text", fe.getValue().toString());

		fe = mFields.getField(R.id.description);

		// Save the current description
		{
			Object o = fe.getValue();
			if (o != null)
				outState.putString(CatalogueDBAdapter.KEY_DESCRIPTION, o.toString());
		}

		// Save flag indicating 'dirty'
		outState.putBoolean("Dirty", mIsDirty);
	}

	/**
	 * If 'back' is pressed, and the user has made changes, ask them if they really want to lose the changes.
	 * 
	 * We don't use onBackPressed because it does not work with API level 4.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && mIsDirty) {
			StandardDialogs.showConfirmUnsavedEditsDialog(this);
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		// Close down the cover browser.
		if (mCoverBrowser != null) {
			mCoverBrowser.dismiss();
			mCoverBrowser = null;
		}
	}

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this);		
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

		// However, there is some data that we really do require...
		if (mAuthorList.size() == 0) {
			Toast.makeText(this, getResources().getText(R.string.author_required), Toast.LENGTH_LONG).show();
			return;
		}
		if (!mStateValues.containsKey(CatalogueDBAdapter.KEY_TITLE) || mStateValues.getString(CatalogueDBAdapter.KEY_TITLE).trim().length() == 0) {
			Toast.makeText(this, getResources().getText(R.string.title_required), Toast.LENGTH_LONG).show();
			return;			
		}

		Field fe = mFields.getField(R.id.bookshelf_text);
		mStateValues.putString("bookshelf_list", fe.getTag().toString());

		mStateValues.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
		mStateValues.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);

		if (mRowId == null || mRowId == 0) {
			String isbn = mStateValues.getString(CatalogueDBAdapter.KEY_ISBN);
			/* Check if the book currently exists */
			if (!isbn.equals("")) {
				if (mDbHelper.checkIsbnExists(isbn)) {
					/*
					 * If it exists, show a dialog and use it to perform the next action, according to the
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
				File thumb = CatalogueDBAdapter.getTempThumbnail();
				File real = CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(id));
				thumb.renameTo(real);
			}
		} else {
			mDbHelper.updateBook(mRowId, mStateValues, true);
		}

		/* These are global variables that will be sent via intent back to the list view, if added/created */
		try {
			ArrayList<Author> authors = (ArrayList<Author>) mStateValues.getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
			if (authors.size() > 0) {
				added_author = authors.get(0).getSortName();
			} else { 
				added_author = "";
			}
		} catch (Exception e) {
			Logger.logError(e);
		};
		try {
			ArrayList<Series> series = (ArrayList<Series>) mStateValues.getSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY);
			if (series.size() > 0)
				added_series = series.get(0).name;
			else 
				added_series = "";
		} catch (Exception e) {
			Logger.logError(e);
		};

		added_title = mStateValues.getString(CatalogueDBAdapter.KEY_TITLE);
		added_genre = mStateValues.getString(CatalogueDBAdapter.KEY_GENRE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ADD_PHOTO:
			if (resultCode == Activity.RESULT_OK && intent != null && intent.getExtras() != null){
				File thumbFile = getCoverFile();
				Bitmap x = (Bitmap) intent.getExtras().get("data");
				if (x != null && x.getWidth() > 0 && x.getHeight() > 0) {
					Matrix m = new Matrix();
					m.postRotate(90);
					x = Bitmap.createBitmap(x, 0, 0, x.getWidth(), x.getHeight(), m, true);
					/* Create a file to copy the thumbnail into */
					FileOutputStream f = null;
					try {
						f = new FileOutputStream(thumbFile.getAbsoluteFile());
					} catch (FileNotFoundException e) {
						Logger.logError(e);
						return;
					}
					
					x.compress(Bitmap.CompressFormat.PNG, 100, f);
					
					cropCoverImage(thumbFile);
					//Intent crop_intent = new Intent(this, CropCropImage.class);
					//// here you have to pass absolute path to your file
					//crop_intent.putExtra("image-path", thumbFile.getAbsolutePath());
					//crop_intent.putExtra("scale", true);
					//startActivityForResult(crop_intent, CAMERA_RESULT);					
				}
			}
			return;
		case CROP_RESULT:
			if (resultCode == Activity.RESULT_OK){
				File thumbFile = getCoverFile();
				File cropped = new File(thumbFile.getAbsoluteFile() + ".cropped.jpg");
				if (cropped.exists()) {
					cropped.renameTo(thumbFile);
					// Update the ImageView with the new image
					setCoverImage();
				}
			}
			return;
		case CAMERA_RESULT:
			if (resultCode == Activity.RESULT_OK){
				// Update the ImageView with the new image
				setCoverImage();
			}
			return;
		case ADD_GALLERY:
			if (resultCode == Activity.RESULT_OK){
				Uri selectedImageUri = intent.getData();

				if (selectedImageUri != null) {
					String[] projection = { MediaStore.Images.Media.DATA };
					Cursor cursor = managedQuery(selectedImageUri, projection, null, null, null);
					int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
					if (column_index < 0 || !cursor.moveToFirst()) {
						Logger.logError(new RuntimeException("Add from gallery failed (col = " + column_index +"), name = " + MediaStore.Images.Media.DATA));
						// This should not happen, but tell the user and log something
						String s = getResources().getString(R.string.no_image_found) + ". " + getResources().getString(R.string.if_the_problem_persists);
						Toast.makeText(this, s, Toast.LENGTH_LONG).show();
					} else {
						String selectedImagePath = cursor.getString(column_index);
						
						File thumb = new File(selectedImagePath);
						File real = getCoverFile();
						try {
							copyFile(thumb, real);
						} catch (IOException e) {
							Logger.logError(e, "copyImage failed in add from gallery");
							String s = getResources().getString(R.string.could_not_copy_image) + ". " + getResources().getString(R.string.if_the_problem_persists);
							Toast.makeText(this, s, Toast.LENGTH_LONG).show();
						}
						// Update the ImageView with the new image
						setCoverImage();					
					}
				} else {
					// Deal with the case where the chooser returns a null intent. This seems to happen when the filename
					// is not properly understood by the choose (eg. an apostrophe in the file name confuses ES File Explorer
					// in the current version as of 23-Sep-2012.
					Toast.makeText(this, R.string.could_not_copy_image, Toast.LENGTH_LONG).show();
				}
			}
			return;
		case ACTIVITY_EDIT_AUTHORS:
			if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)){
				mAuthorList = (ArrayList<Author>) intent.getSerializableExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
				mIsDirty = true;
			} else {
				// Even though the dialog was terminated, some authors MAY have been updated/added.
				if (mAuthorList != null)
					for(Author a : mAuthorList) {
						mDbHelper.refreshAuthor(a);
					}
			}
			// We do the fixup here because the user may have edited or merged authors; this will
			// have already been applied to the database so no update is necessary, but we do need 
			// to update the data we display.
			boolean oldDirty = mIsDirty;
			fixupAuthorList();
			mIsDirty = oldDirty;
		case ACTIVITY_EDIT_SERIES:
			if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY)){
				mSeriesList = (ArrayList<Series>) intent.getSerializableExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY);
				fixupSeriesList();
				mIsDirty = true;
			}
		}
	}
	
	private void fixupAuthorList() {

		String newText;
		if (mAuthorList.size() == 0)
			newText = getResources().getString(R.string.set_authors);
		else {
			mIsDirty = mIsDirty || Utils.pruneList(mDbHelper, mAuthorList);
			newText = mAuthorList.get(0).getDisplayName();
			if (mAuthorList.size() > 1)
				newText += " " + getResources().getString(R.string.and_others);
		}
		mFields.getField(R.id.author).setValue(newText);	
	}

	private void fixupSeriesList() {

		String newText;
		int size = 0;
		try {
			size = mSeriesList.size();
		} catch (NullPointerException e) {
			size = 0;
		}
		if (size == 0)
			newText = getResources().getString(R.string.set_series);
		else {
			Utils.pruneSeriesList(mSeriesList);
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

	/**
	 * Handler to process a cover selected from the CoverBrowser.
	 */
	private OnImageSelectedListener mOnImageSelectedListener = new OnImageSelectedListener() {
		@Override
		public void onImageSelected(String fileSpec) {
			if (mCoverBrowser != null && fileSpec != null) {
				// Get the current file
				File bookFile = getCoverFile();
				// Get the new file
				File newFile = new File(fileSpec);					
				// Overwrite with new file
				newFile.renameTo(bookFile);
				// update current activity
				setCoverImage();
			}
			if (mCoverBrowser != null)
				mCoverBrowser.dismiss();
			mCoverBrowser = null;
		}};
		
	/**
	 * Show the context menu for the cover thumbnail
	 */
	public void showCoverContextMenu() {
		View v = findViewById(R.id.row_img);
		v.showContextMenu();
	}
}
