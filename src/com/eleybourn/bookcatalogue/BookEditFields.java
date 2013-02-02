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

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment.OnBookshelfChangeListener;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;



public class BookEditFields extends BookDetailsAbstract implements OnPartialDatePickerListener, OnTextFieldEditorListener { // implements OnRestoreTabInstanceStateListener {

	/**
	 * Class to implement a clickable span of text and call a listener when text is clicked.
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

//	// TODO: Aggregate in Activity
//	private boolean mIsDirtyFlg = false;

//	private Button mConfirmButton;
//	private Button mCancelButton;
//	private String added_genre = "";
//	private String added_series = "";
//	private String added_title = "";
//	private String added_author = "";
//	public static String ADDED_HAS_INFO = "ADDED_HAS_INFO";
//	public static String ADDED_GENRE = "ADDED_GENRE";
//	public static String ADDED_SERIES = "ADDED_SERIES";
//	public static String ADDED_TITLE = "ADDED_TITLE";
//	public static String ADDED_AUTHOR = "ADDED_AUTHOR";

	public static final int ACTIVITY_EDIT_AUTHORS = 1000;
	public static final int ACTIVITY_EDIT_SERIES = 1001;
	
	private static final int DATE_DIALOG_ID = 1;
	private static final int DESCRIPTION_DIALOG_ID = 3;
	
	// Global value for values from UI. Recreated periodically; at least 
	// in saveState(). Needs to be global so an Alert can be displayed 
	// during a save.
	private Bundle mStateValues = null;


	/**
	 * Display the edit fields page
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.edit_book, container, false);
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		double t0 = System.currentTimeMillis();
		double t1 = 0;
		double t2 = 0;

		Tracker.enterOnCreateView(this);		
		try {
			t1 = System.currentTimeMillis();
			super.onActivityCreated(savedInstanceState);
			t2 = System.currentTimeMillis();
			
			if (savedInstanceState != null) {
				mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
			}

			//Set click listener on Author field
			View v = getView().findViewById(R.id.author); //Reusable view for setting listeners
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(getActivity(), EditAuthorList.class);
					i.putExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mEditManager.getBookData().getAuthorList());
					i.putExtra(CatalogueDBAdapter.KEY_ROWID, mEditManager.getBookData().getRowId());
					i.putExtra("title_label", CatalogueDBAdapter.KEY_TITLE);
					i.putExtra("title", mFields.getField(R.id.title).getValue().toString());
					startActivityForResult(i, ACTIVITY_EDIT_AUTHORS);
				}
			});
			
			//Set click listener on Series field
			v = getView().findViewById(R.id.series);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(getActivity(), EditSeriesList.class);
					i.putExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY, mEditManager.getBookData().getSeriesList());
					i.putExtra(CatalogueDBAdapter.KEY_ROWID, mEditManager.getBookData().getRowId());
					i.putExtra("title_label", CatalogueDBAdapter.KEY_TITLE);
					i.putExtra("title", mFields.getField(R.id.title).getValue().toString());
					startActivityForResult(i, ACTIVITY_EDIT_SERIES);
				}
			});
			
			ArrayAdapter<String> publisher_adapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getPublishers());
			mFields.setAdapter(R.id.publisher, publisher_adapter);
			ArrayAdapter<String> genre_adapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getGenres());
			mFields.setAdapter(R.id.genre, genre_adapter);
			
			mFields.setListener(R.id.date_published, new View.OnClickListener() {
				public void onClick(View view) {
					showDatePublishedDialog();
				}
			});
			
			final Field formatField = mFields.getField(R.id.format);
			// Get the formats to use in the AutoComplete stuff
			AutoCompleteTextView formatText = (AutoCompleteTextView) formatField.getView();
			formatText.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getFormats()));
			// Get the drop-down button for the formats list and setup dialog
			ImageView formatButton = (ImageView) getView().findViewById(R.id.format_button);
			formatButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					StandardDialogs.selectStringDialog(getActivity().getLayoutInflater(), getString(R.string.format),
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

			Field bookshelfButtonFe = mFields.getField(R.id.bookshelf);
			bookshelfButtonFe.getView().setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					Field f = mFields.getField(R.id.bookshelf);

					BookshelfDialogFragment frag = new BookshelfDialogFragment(
							mEditManager.getBookData().getRowId(), 
							mEditManager.getBookData().getBookshelfText(), 
							mEditManager.getBookData().getBookshelfList(), 
							new OnBookshelfChangeListener() {

								@Override
								public void onCheckChanged(boolean checked, String shelf, String textList, String encodedList) {
									Field fe = mFields.getField(R.id.bookshelf);
									fe.setValue(textList);
									mEditManager.getBookData().setBookshelfList(encodedList);
								}
							}
						);
					
					frag.show(getFragmentManager(),"bookshelves_dialog");
					
				}
			});
			
			// Build the label for the book description if this is first time, otherwise will be built later
			if (savedInstanceState == null)
				buildDescription();

			// Setup the Save/Add/Anthology UI elements
			setupUi();

			try {
				Utils.fixFocusSettings(getView());
				getView().findViewById(R.id.author).requestFocus();
			} catch (Exception e) {
				// Log, but ignore. This is a non-critical feature that prevents crashes when the
				// 'next' key is pressed and some views have been hidden.
				Logger.logError(e);
			}

			if (savedInstanceState != null) {
				mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));				
			} else {
				mEditManager.setDirty(false);
			}

		} catch (IndexOutOfBoundsException e) {
			Logger.logError(e);
		} catch (SQLException e) {
			Logger.logError(e);
		}
		Tracker.exitOnCreate(this);
		
		double tn = System.currentTimeMillis();
		System.out.println("BEF oAC(super): " + (t2 - t1));
		System.out.println("BEF oAC: " + (tn - t0));

	}
	
//	@Override
//	protected Dialog onCreateDialog(int id) {
//		SherlockDialogFragment dialog;
//
//		switch (id) {
//		case DATE_DIALOG_ID:
//			try {
//				dialog = Utils.buildDateDialog(R.string.date_published, mBigDateSetListener);
//			} catch (Exception e) {
//				Logger.logError(e);
//				// use the default date
//				dialog = null;
//			}
//			break;
//
//		case DESCRIPTION_DIALOG_ID:
//			dialog = new TextFieldEditor(this);
//			dialog.setTitle(R.string.description);
//			// The rest of the details will be set in onPrepareDialog
//			break;
//		default:
//			dialog = null;
//		}
//		return dialog;
//	}

	private void showDescriptionDialog() {
		Object o = mFields.getField(R.id.description).getValue();
		String description = (o == null? null : o.toString());
		TextFieldEditorFragment dlg = TextFieldEditorFragment.newInstance(R.id.description, R.string.description, description);
		dlg.show(getFragmentManager(), null);
	}

	private void showDatePublishedDialog() {
		PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance();
		Utils.prepareDateDialogFragment(frag, mFields.getField(R.id.date_published).getValue());
		frag.setTitle(R.string.date_published);
		frag.setDialogId(R.id.date_published); // Set to the destination field ID
		frag.show(getFragmentManager(), null);
	}

//	@Override
//	protected void onPrepareDialog(int id, Dialog dialog) {
//		System.out.println("Prep dialog " + id);
//
//		switch (id) {
//		case DATE_DIALOG_ID:
//			try {
//				Utils.prepareDateDialog((PartialDatePicker)dialog, mFields.getField(R.id.date_published).getValue(), mBigDateSetListener);
//
//			} catch (Exception e) {
//				Logger.logError(e);
//			}
//			break;
//
//		case DESCRIPTION_DIALOG_ID:
//			{
//				TextFieldEditor dlg = (TextFieldEditor)dialog;
//
//				Object o = mFields.getField(R.id.description).getValue();
//				String description = (o == null? null : o.toString());
//				dlg.setText(description);
//
//				dlg.setOnEditListener(mOnDescriptionEditListener);
//			}
//			break;
//		}
//	}

	/**
	 * This function will populate the forms elements in three different ways
	 * 1. If a valid rowId exists it will populate the fields from the database
	 * 2. If fields have been passed from another activity (e.g. ISBNSearch) it will populate the fields from the bundle
	 * 3. It will leave the fields blank for new books.
	 */
	private void populateFields() {
		double t0 = System.currentTimeMillis();
		Bundle extras = getActivity().getIntent().getExtras();
		final BookData book = mEditManager.getBookData();
		
		populateFieldsFromBook(book);
		if (book.getRowId() > 0) { //Populating from database
//			populateFieldsFromBook(book);
			//getActivity().setTitle(this.getResources().getString(R.string.menu));
		} else {
			if (extras != null) {
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
	
					}
				} catch (NullPointerException e) {
					Logger.logError(e);
				}
			}
			initDefaultShelf();				
			setCoverImage();
		}
		
		populateAuthorListField();
		populateSeriesListField();

		System.out.println("BEF popF: " + (System.currentTimeMillis() - t0));
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
		Field fe = mFields.getField(R.id.bookshelf);
		fe.setValue(currShelf);
		fe.setTag(encoded_shelf);		
	}

//	private void showAnthologyTab() {
//		CheckBox cb = (CheckBox)getView().findViewById(R.id.anthology);
//		try {
//			TabHost tabHost = ((TabActivity) getParent()).getTabHost();  // The activity TabHost
//			if (cb.isChecked()) {
//				Resources res = getResources();
//				TabHost.TabSpec spec;  // Reusable TabSpec for each tab
//				Intent intent = new Intent().setClass(BookEditFields.this, BookEditAnthology.class);
//				intent.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
//				spec = tabHost.newTabSpec("edit_book_anthology").setIndicator(res.getString(R.string.edit_book_anthology), res.getDrawable(R.drawable.ic_tab_anthology)).setContent(intent);
//				tabHost.addTab(spec);
//			} else {
//				// remove tab
//				tabHost.getTabWidget().removeViewAt(3);
//			}
//		} catch (Exception e) {
//			// if this doesn't work don't add the tab. The user will have to save and reenter
//		}
//	}

//	private class DoAnthologyAction implements BookEdit.PostSaveAction {
//		private boolean mWasChecked;
//		public DoAnthologyAction(boolean wasChecked) {
//			mWasChecked = wasChecked;
//		}
//		public void success() {
//			mEditManager.setShowAnthology(mWasChecked);
//			//showAnthologyTab();
//		}
//		public void failure() {
//			// Do nothing
//		}
//	}

	private void setupUi() {
		final CheckBox cb = (CheckBox)getView().findViewById(R.id.anthology);

		cb.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				mEditManager.setShowAnthology(cb.isChecked());
				//saveState(new DoAnthologyAction(cb.isChecked()));
			}
		});
	}

//	@Override
//	/**
//	 * Method called when the containing TabActivity is running OnRestoreInstanceState; otherwise
//	 * locally made changes in our own OnRestoreInstanceState may get overwritten.
//	 * 
//	 * Also, make sure we are marked as 'dirty' based on saved state after a restore.
//	 */
//	public void restoreTabInstanceState(Bundle savedInstanceState) {
//		buildDescription();
//		mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
//	}

	/**
	 * Setup the 'description' header field to have a clickable link.
	 */
	private void buildDescription() {
		double t0 = System.currentTimeMillis();
		// get the view
		final TextView tv = (TextView)getView().findViewById(R.id.descriptionLabel);
		// Build the prefic text ('Description ')
		String baseText = getString(R.string.description) + " ";
		// Create the span ('Description (edit...)').
		SpannableString f = new SpannableString(baseText + "(" + getString(R.string.edit_lc_ellipsis) + ")");
		f.setSpan(new InternalSpan(new OnClickListener() {  
		        public void onClick(View v) {  
		        	BookEditFields.this.showDescriptionDialog();
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
		System.out.println("BEF bDesc: " + (System.currentTimeMillis() - t0));
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
	public void onSaveInstanceState(Bundle outState) {
		Tracker.enterOnSaveInstanceState(this);

		super.onSaveInstanceState(outState);

		// This is now done in onPause() since the view may have been deleted when this is called
		//mFields.getAll(mEditManager.getBookData());

//		final Long rowId = mEditManager.getRowId();
//		if (rowId != null) {
//			outState.putLong(CatalogueDBAdapter.KEY_ROWID, rowId);
//		} else {
//			//there is nothing todo
//		}
		// DONT FORGET TO UPDATE onCreate to read these values back.
		// Need to save local data that is not stored in editable views
//		outState.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
//		outState.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);
		// ...including special text stored in TextViews and the like
//		{
//			if (mFields == null) 
//				throw new RuntimeException("Fields is NULL in onSaveInstanceState");
//			Object o = mFields.getField(R.id.date_published).getValue();
//			if (o != null) {
//				mEditManager.getBookData().putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, o.toString());
//				//outState.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, o.toString());
//			}
//		}

//		Field fe = mFields.getField(R.id.bookshelf_text);
//		outState.putString("bookshelf_list", fe.getTag().toString());
//		outState.putString("bookshelf_text", fe.getValue().toString());
//
//		fe = mFields.getField(R.id.description);
//
//		// Save the current description
//		{
//			Object o = fe.getValue();
//			if (o != null)
//				outState.putString(CatalogueDBAdapter.KEY_DESCRIPTION, o.toString());
//		}

		Tracker.exitOnSaveInstanceState(this);
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ")", Tracker.States.Enter);			
		try {
			super.onActivityResult(requestCode, resultCode, intent);
			switch(requestCode) {
			case ACTIVITY_EDIT_AUTHORS:
				if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)){
					mEditManager.getBookData().setAuthorList(Utils.getAuthorsFromBundle(intent.getExtras()));
					mEditManager.setDirty(true);
				} else {
					// Even though the dialog was terminated, some authors MAY have been updated/added.
					mEditManager.getBookData().refreshAuthorList(mDbHelper);
				}
				// We do the fixup here because the user may have edited or merged authors; this will
				// have already been applied to the database so no update is necessary, but we do need 
				// to update the data we display.
				boolean oldDirty = mEditManager.isDirty();
				populateAuthorListField();
				mEditManager.setDirty(oldDirty);
			case ACTIVITY_EDIT_SERIES:
				if (resultCode == Activity.RESULT_OK && intent.hasExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY)){
					mEditManager.getBookData().setSeriesList(Utils.getSeriesFromBundle(intent.getExtras()));
					populateSeriesListField();
					mEditManager.setDirty(true);
				}
			}
		} finally {
			Tracker.handleEvent(this, "onActivityResult", Tracker.States.Exit);			
		}
	}
	
	@Override
	protected void populateAuthorListField() {
		ArrayList<Author> list = mEditManager.getBookData().getAuthorList();
		if (list.size() != 0 && Utils.pruneList(mDbHelper, list) ) {
			mEditManager.setDirty(true);
			mEditManager.getBookData().setAuthorList(list);
		}
		super.populateAuthorListField();
	}

	/**
	 * Show the context menu for the cover thumbnail
	 */
	public void showCoverContextMenu() {
		View v = getView().findViewById(R.id.row_img);
		v.showContextMenu();
	}

	@Override
	protected void onLoadBookDetails(BookData book, boolean setAllDone) {
		if (!setAllDone)
			mFields.setAll(book);
		populateFields();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		boolean thumbVisible = BookCatalogueApp.getAppPreferences().getBoolean(FieldVisibility.prefix + "thumbnail", true);
		if (thumbVisible) {
			MenuItem thumbOptions = menu.add(0, BookEditFragmentAbstract.THUMBNAIL_OPTIONS_ID, 0, R.string.cover_options_cc_ellipsis);
			thumbOptions.setIcon(android.R.drawable.ic_menu_camera);			
			thumbOptions.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		}
		super.onCreateOptionsMenu(menu, inflater);
	}

	/**
	 *  The callback received when the user "sets" the date in the dialog.
	 *  
	 *  Build a full or partial date in SQL format
	 */
	@Override
	public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
		String value = Utils.buildPartialDate(year, month, day);
		mFields.getField(dialogId).setValue(value);
		dialog.dismiss();
	}

	/**
	 *  The callback received when the user "cancels" the date in the dialog.
	 *  
	 *  Dismiss it.
	 */
	@Override
	public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
		dialog.dismiss();
	}

	/**
	 *  The callback received when the user "sets" the text editor in the text editor dialog.
	 *  
	 *  Set the appropriate field
	 */
	@Override
	public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText) {
		mFields.getField(dialogId).setValue(newText);
		dialog.dismiss();
	}

	/**
	 *  The callback received when the user "cancels" the text editor dialog.
	 *  
	 *  Dismiss it.
	 */
	@Override
	public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog) {
		dialog.dismiss();
	}

}
