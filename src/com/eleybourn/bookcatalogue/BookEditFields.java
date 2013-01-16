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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
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
import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePicker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditor;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;



public class BookEditFields extends BookDetailsAbstract implements OnRestoreTabInstanceStateListener {

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

	private boolean mIsDirtyFlg = false;

	private Button mConfirmButton;
	private Button mCancelButton;
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
	
	private static final int DATE_DIALOG_ID = 1;
	private static final int DESCRIPTION_DIALOG_ID = 3;
	
	// Global value for values from UI. Recreated periodically; at least 
	// in saveState(). Needs to be global so an Alert can be displayed 
	// during a save.
	private Bundle mStateValues = null;

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
			Tracker.enterOnCreate(this);
			setContentView(R.layout.edit_book);
			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (mRowId == null) {
				setRowIdByExtras();
			}

			if (savedInstanceState != null) {
				setDirty(savedInstanceState.getBoolean("Dirty"));
			}

			super.onCreate(savedInstanceState);
			
			//Set click listener on Author field
			View v = findViewById(R.id.author); //Reusable view for setting listeners
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
			
			//Set click listener on Series field
			v = findViewById(R.id.series);
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
			
			ArrayAdapter<String> publisher_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getPublishers());
			mFields.setAdapter(R.id.publisher, publisher_adapter);
			ArrayAdapter<String> genre_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getGenres());
			mFields.setAdapter(R.id.genre, genre_adapter);
			
			mFields.setListener(R.id.date_published_button, new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(DATE_DIALOG_ID);
				}
			});
			
			final Field formatField = mFields.getField(R.id.format);
			// Get the formats to use in the AutoComplete stuff
			AutoCompleteTextView formatText = (AutoCompleteTextView) formatField.getView();
			formatText.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mDbHelper.getFormats()));
			// Get the drop-down button for the formats list and setup dialog
			ImageView formatButton = (ImageView) findViewById(R.id.format_button);
			formatButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					StandardDialogs.selectStringDialog(getLayoutInflater(), getString(R.string.format),
							mDbHelper.getFormats(), formatField.getValue().toString(),
							new SimpleDialogOnClickListener() {
								@Override
								public void onClick(SimpleDialogItem item) {
									formatField.setValue(item.toString());
								}
							});
				}
			});

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
			
			Field bookshelfButtonFe = mFields.getField(R.id.bookshelf);
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
				mAuthorList = Utils.getAuthorsFromBundle(savedInstanceState);
				populateAuthorListField();	// Will update related display fields/button
				mSeriesList = Utils.getSeriesFromBundle(savedInstanceState);
				populateSeriesListField();	// Will update related display fields/button
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

			// mConfirmButton.setOnClickListener - This is set in populate fields. The behaviour changes depending on if it is adding or saving
			mCancelButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					// Cleanup because we may have made global changes
					mDbHelper.purgeAuthors();
					mDbHelper.purgeSeries();
					// We're done.
					setResult(RESULT_OK);

					if (isDirty()) {
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

//			Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);

			mFields.setAfterFieldChangeListener(new AfterFieldChangeListener(){
				@Override
				public void afterFieldChange(Field field, String newValue) {
					setDirty(true);
				}});

		} catch (IndexOutOfBoundsException e) {
			Logger.logError(e);
		} catch (SQLException e) {
			Logger.logError(e);
		}
		Tracker.exitOnCreate(this);
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;

		switch (id) {
		case DATE_DIALOG_ID:
			try {
				dialog = Utils.buildDateDialog(this, R.string.date_published, mBigDateSetListener);
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
				Utils.prepareDateDialog((PartialDatePicker)dialog, mFields.getField(R.id.date_published).getValue(), mBigDateSetListener);

			} catch (Exception e) {
				Logger.logError(e);
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
	 *  The callback received when the user "sets" the date in the dialog.
	 *  
	 *  Build a full or partial date in SQL format
	 */
	private PartialDatePicker.OnDateSetListener mBigDateSetListener = new PartialDatePicker.OnDateSetListener() {
		public void onDateSet(PartialDatePicker dialog, Integer year, Integer month, Integer day) {
			String value = Utils.buildPartialDate(year, month, day);
			mFields.getField(R.id.date_published).setValue(value);
			dismissDialog(DATE_DIALOG_ID);
		}

		@Override
		public void onCancel(PartialDatePicker dialog) {
			dismissDialog(DATE_DIALOG_ID);
		}
	};

	/**
	 * This function will populate the forms elements in three different ways
	 * 1. If a valid rowId exists it will populate the fields from the database
	 * 2. If fields have been passed from another activity (e.g. ISBNSearch) it will populate the fields from the bundle
	 * 3. It will leave the fields blank for new books.
	 */
	private void populateFields() {
		Bundle extras = getIntent().getExtras();
		if (mRowId == null) {
			setRowIdByExtras();
		}
		
		if (mRowId != null && mRowId > 0) { //Populating from database
			populateFieldsFromDb(mRowId);
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
					mAuthorList = Utils.getAuthorsFromBundle(values);
					mSeriesList = Utils.getSeriesFromBundle(values);
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
		
		populateAuthorListField();
		populateSeriesListField();

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
	 * locally made changes in our own OnRestoreInstanceState may get overwritten.
	 * 
	 * Also, make sure we are marked as 'dirty' based on saved state after a restore.
	 */
	public void restoreTabInstanceState(Bundle savedInstanceState) {
		buildDescription();
		setDirty(savedInstanceState.getBoolean("Dirty"));
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
		Tracker.enterOnSaveInstanceState(this);

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
		outState.putBoolean("Dirty", isDirty());
		Tracker.exitOnSaveInstanceState(this);
	}

	@Override
	/**
	 * Prevent state restoration from falsely marking this activity as dirty
	 */
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Tracker.enterOnRestoreInstanceState(this);
		super.onRestoreInstanceState(savedInstanceState);
		setDirty(savedInstanceState.getBoolean("Dirty"));
		Tracker.exitOnRestoreInstanceState(this);
	}

	/**
	 * If 'back' is pressed, and the user has made changes, ask them if they really want to lose the changes.
	 * 
	 * We don't use onBackPressed because it does not work with API level 4.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && isDirty()) {
			StandardDialogs.showConfirmUnsavedEditsDialog(this);
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
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
					alert.setButton2(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							updateOrCreate();
							nextStep.success();
							return;
						}
					}); 
					alert.setButton(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
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
			ArrayList<Author> authors = Utils.getAuthorsFromBundle(mStateValues);
			if (authors.size() > 0) {
				added_author = authors.get(0).getSortName();
			} else { 
				added_author = "";
			}
		} catch (Exception e) {
			Logger.logError(e);
		};
		try {
			ArrayList<Series> series = Utils.getSeriesFromBundle(mStateValues);
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
		Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ")", Tracker.States.Enter);			
		try {
			super.onActivityResult(requestCode, resultCode, intent);
			switch(requestCode) {
			case ACTIVITY_EDIT_AUTHORS:
				if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)){
					mAuthorList = Utils.getAuthorsFromBundle(intent.getExtras());
					setDirty(true);
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
				boolean oldDirty = isDirty();
				populateAuthorListField();
				setDirty(oldDirty);
			case ACTIVITY_EDIT_SERIES:
				if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY)){
					mSeriesList = Utils.getSeriesFromBundle(intent.getExtras());
					populateSeriesListField();
					setDirty(true);
				}
			}
		} finally {
			Tracker.handleEvent(this, "onActivityResult", Tracker.States.Exit);			
		}
	}
	
	@Override
	protected void populateAuthorListField() {
		if (mAuthorList.size() != 0 && Utils.pruneList(mDbHelper, mAuthorList) )
			setDirty(true);
		super.populateAuthorListField();
	}

	/**
	 * Mark the data as dirty (or not)
	 */
	public void setDirty(boolean dirty) {
		mIsDirtyFlg = dirty;
	}

	/**
	 * Get the current status of the data in this activity
	 */
	public boolean isDirty() {
		return mIsDirtyFlg;
	}

	/**
	 * Show the context menu for the cover thumbnail
	 */
	public void showCoverContextMenu() {
		View v = findViewById(R.id.row_img);
		v.showContextMenu();
	}
}
