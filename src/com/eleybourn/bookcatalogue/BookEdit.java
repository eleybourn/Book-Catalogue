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
import java.util.Hashtable;
import java.util.Map.Entry;

import android.app.Activity;
import android.app.LocalActivityManager;
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * A tab host activity which holds the three edit book tabs
 * 1. Edit Details / Book Details
 * 2. Edit Comments
 * 3. Loan Book
 * 
 * @author Evan Leybourn
 */
public class BookEdit extends TabActivity {
	
	/** Interface to be notified when OnRestoreInstanceState is called on this activity
	 * This is important because OnRestoreInstanceState in the tab host activity gets called
	 * *AFTER* OnRestoreInstanceState in a contained activity, and will restore THE SAME fields.
	 * 
	 * This results in some fields (eg. description) being overwritten after the containing
	 * activity has restored them. It also results in the child activity being marked as 'dirty'
	 * because the fields are updated after it's own 'restore' is done.
	 */
	public interface OnRestoreTabInstanceStateListener {
		boolean isDirty();
		void setDirty(boolean dirty);
		void restoreTabInstanceState(Bundle savedInstanceState);
	}

	public static final String TAB = "tab";
	public static final int TAB_EDIT = 0;
	public static final int TAB_EDIT_NOTES = 1;
	public static final int TAB_EDIT_FRIENDS = 2;

	/** 
	 * Standardize the names of tabs 
	 * ANY NEW NAMES NEED TO BE ADDED TO mTabNames, below 
	 */
	private static String TAB_NAME_EDIT_BOOK = "edit_book";
	private static String TAB_NAME_EDIT_NOTES = "edit_book_notes";
	private static String TAB_NAME_EDIT_FRIENDS = "edit_book_friends";
	private static String TAB_NAME_EDIT_ANTHOLOGY = "edit_book_anthology";

	/** Create a collection of tab names for easy iteration */
	private static String[] mTabNames = { TAB_NAME_EDIT_BOOK, TAB_NAME_EDIT_NOTES, TAB_NAME_EDIT_FRIENDS, TAB_NAME_EDIT_ANTHOLOGY };
	
	private static final int DELETE_ID = 1;
	private static final int DUPLICATE_ID = 3; //2 is taken by populate in anthology
	private static final int SHARE_ID = 4;
	private static final int THUMBNAIL_OPTIONS_ID = 5;

	public int mCurrentTab = 0;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper = new CatalogueDBAdapter(this);

	public void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabhost);
		
		TabHost tabHost = getTabHost();  // The activity TabHost
		mDbHelper.open();
		
		//get the passed parameters
		Bundle extras = getIntent().getExtras();
		mCurrentTab = extras != null ? extras.getInt(BookEdit.TAB) : 0;
		mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		if (mRowId == null) {
			mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		}
		int anthology_num = 0;

		// Avoid unnecessary exception logging; check the rowId
		if (mRowId != null && mRowId > 0) {
			try {
				Cursor book = mDbHelper.fetchBookById(mRowId);
				book.moveToFirst();
				anthology_num = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
				book.close(); // close the cursor
			} catch (CursorIndexOutOfBoundsException e) {
				//do nothing - new book
			} catch (NullPointerException e) {
				Logger.logError(e);
			}
		}
		
		//Change the name depending on whether it is a new or existing book
		boolean isReadOnly = BookCatalogueApp.getAppPreferences()
				.getBoolean(BookCataloguePreferences.PREF_OPEN_BOOK_READ_ONLY, false);
		int firstTabTitleResId;
		// Class needed for the first tab: BookEditFields except when book is exist and read-only mode enabled
		Class<?> neededClass = BookEditFields.class;  
		if (mRowId == null || mRowId == 0) {
			firstTabTitleResId = R.string.menu_insert;
		} else {
			firstTabTitleResId = isReadOnly ? R.string.book : R.string.edit_book; //Just use R.string.book for read-only title now
			if (isReadOnly) {
				neededClass = BookDetails.class;
			}
		}
		
		initTab(tabHost, neededClass, TAB_NAME_EDIT_BOOK, firstTabTitleResId, R.drawable.ic_tab_edit, extras);
		
		// Only show the other tabs if it is not new book, otherwise only show the first tab
		if (mRowId != null && mRowId > 0) {
			initTab(tabHost, BookEditNotes.class, TAB_NAME_EDIT_NOTES, R.string.edit_book_notes, R.drawable.ic_tab_notes, extras);
			initTab(tabHost, BookEditLoaned.class, TAB_NAME_EDIT_FRIENDS, R.string.edit_book_friends, R.drawable.ic_tab_friends, extras);
			
			// Only show the anthology tab if the book is marked as an anthology
			if (anthology_num != 0) {
				initTab(tabHost, BookEditAnthology.class, TAB_NAME_EDIT_ANTHOLOGY, R.string.edit_book_anthology, R.drawable.ic_tab_anthology, extras);
			}
		}
		
		tabHost.setCurrentTab(mCurrentTab);
		Tracker.exitOnCreate(this);
	}
	
	/**
	 * This is a straight passthrough
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		setResult(resultCode, intent);
		switch(requestCode) {
		case DUPLICATE_ID:
			finish();
		}
		finish();
	}
	
	@Override
	protected void onDestroy() {
		Tracker.enterOnDestroy(this);
		super.onDestroy();
		mDbHelper.close();
		Tracker.exitOnDestroy(this);
	} 
	
	@Override 
	protected void onSaveInstanceState(Bundle outState) { 
		Tracker.enterOnSaveInstanceState(this);
		super.onSaveInstanceState(outState);
		try {
			outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		} catch (Exception e) {
			//do nothing
		}
		Tracker.exitOnSaveInstanceState(this);
	}
	
	@Override
	/**
	 * Inform the hosted tabsl that they may have been overwritten, if they implements the
	 * relevant interface.
	 * 
	 * This only seems to be relevant for TextView objects that have Spannable text.
	 */
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Tracker.enterOnRestoreInstanceState(this);
		LocalActivityManager mgr = this.getLocalActivityManager();

		Hashtable<OnRestoreTabInstanceStateListener, Boolean> tabs = new Hashtable<OnRestoreTabInstanceStateListener, Boolean>();
		for(String name: mTabNames) {
			Activity a = mgr.getActivity(name);
			if (a instanceof OnRestoreTabInstanceStateListener) {
				OnRestoreTabInstanceStateListener l = (OnRestoreTabInstanceStateListener)a;
				tabs.put(l, l.isDirty());
			}			
		}
		super.onRestoreInstanceState(savedInstanceState);
		for(Entry<OnRestoreTabInstanceStateListener, Boolean> e: tabs.entrySet()) {
			e.getKey().restoreTabInstanceState(savedInstanceState);
			e.getKey().setDirty(e.getValue());
		}
		Tracker.exitOnRestoreInstanceState(this);
	}
	
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.clear();
		if (mRowId != null && mRowId != 0) {
			MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete);
			delete.setIcon(android.R.drawable.ic_menu_delete);

			MenuItem duplicate = menu.add(0, DUPLICATE_ID, 0, R.string.menu_duplicate);
			duplicate.setIcon(android.R.drawable.ic_menu_add);
		}

		// TODO: Consider allowing Tweets (or other sharig methods) to work on un-added books.
		MenuItem tweet = menu.add(0, SHARE_ID, 0, R.string.menu_share_this);
		tweet.setIcon(R.drawable.ic_menu_twitter);

		boolean thumbVisible = BookCatalogueApp.getAppPreferences().getBoolean(FieldVisibility.prefix + "thumbnail", true);
		if (thumbVisible && getCurrentActivity() instanceof BookEditFields) {
			MenuItem thumbOptions = menu.add(0, THUMBNAIL_OPTIONS_ID, 0, R.string.cover_options_cc_ellipsis);
			thumbOptions.setIcon(android.R.drawable.ic_menu_camera);			
		}
		
		return super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Cursor thisBook = null;
		try {
			switch(item.getItemId()) {
			case THUMBNAIL_OPTIONS_ID:
				Activity a = this.getCurrentActivity();
				if (a instanceof BookEditFields) {
					((BookEditFields)a).showCoverContextMenu();
				}
				break;
			case SHARE_ID:
				if (mRowId == null || mRowId == 0) {
					Toast.makeText(this, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
					return true;
				}
				
				thisBook = mDbHelper.fetchBookById(mRowId);
				thisBook.moveToFirst();
				String title = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_TITLE));
				double rating = thisBook.getDouble(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_RATING));
				String ratingString = "";
				String author = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED_GIVEN_FIRST));
				String series = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_FORMATTED));
				File image = CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(mRowId));

				if (series.length() > 0) {
					series = " (" + series.replace("#", "%23") + ")";
				}
				//remove trailing 0's
				if (rating > 0) {
					int ratingTmp = (int)rating;
					double decimal = rating - ratingTmp;
					if (decimal > 0) {
						ratingString = rating + "/5";
					} else {
						ratingString = ratingTmp + "/5";
					}
				}
				
				if (ratingString.length() > 0){
					ratingString = "(" + ratingString + ")";
				}

				/*
				 * There's a problem with the facebook app in android, so 
				 * despite it being shown on the list
				 * it will not post any text unless the user types it.
				*/
				Intent share = new Intent(Intent.ACTION_SEND); 
				share.putExtra(Intent.EXTRA_TEXT, "I'm reading " + title + " by " + author + series + " " + ratingString);
				share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + image.getPath()));
                share.setType("text/plain");
                
                startActivity(Intent.createChooser(share, "Share"));
                
				return true;
			case DELETE_ID:
				if (mRowId == null || mRowId == 0) {
					Toast.makeText(this, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
					return true;
				}
				int res = StandardDialogs.deleteBookAlert(this, mDbHelper, mRowId, new Runnable() {
					@Override
					public void run() {
						mDbHelper.purgeAuthors();
						mDbHelper.purgeSeries();
						finish();
					}});
				if (res != 0) {
					Toast.makeText(this, res, Toast.LENGTH_LONG).show();
					finish();
				}
				return true;
			case DUPLICATE_ID:
				if (mRowId == null || mRowId == 0) {
					Toast.makeText(this, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
					return true;
				}
				Intent i = new Intent(this, BookEdit.class);
				Bundle book = new Bundle();
				thisBook = mDbHelper.fetchBookById(mRowId);
				try {
					thisBook.moveToFirst();
					book.putString(CatalogueDBAdapter.KEY_TITLE, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_TITLE)));
					book.putString(CatalogueDBAdapter.KEY_ISBN, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_ISBN)));
					book.putString(CatalogueDBAdapter.KEY_PUBLISHER, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_PUBLISHER)));
					book.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_DATE_PUBLISHED)));
					book.putString(CatalogueDBAdapter.KEY_RATING, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_RATING)));
					book.putString(CatalogueDBAdapter.KEY_READ, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_READ)));
					book.putString(CatalogueDBAdapter.KEY_PAGES, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_PAGES)));
					book.putString(CatalogueDBAdapter.KEY_NOTES, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_NOTES)));
					book.putString(CatalogueDBAdapter.KEY_LIST_PRICE, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_LIST_PRICE)));
					book.putString(CatalogueDBAdapter.KEY_ANTHOLOGY, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_ANTHOLOGY)));
					book.putString(CatalogueDBAdapter.KEY_LOCATION, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_LOCATION)));
					book.putString(CatalogueDBAdapter.KEY_READ_START, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_READ_START)));
					book.putString(CatalogueDBAdapter.KEY_READ_END, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_READ_END)));
					book.putString(CatalogueDBAdapter.KEY_FORMAT, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_FORMAT)));
					book.putString(CatalogueDBAdapter.KEY_SIGNED, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_SIGNED)));
					book.putString(CatalogueDBAdapter.KEY_DESCRIPTION, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_DESCRIPTION)));
					book.putString(CatalogueDBAdapter.KEY_GENRE, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_GENRE)));
					
					book.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mDbHelper.getBookAuthorList(mRowId));
					book.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, mDbHelper.getBookSeriesList(mRowId));
					
					i.putExtra("bookData", book);
					startActivityForResult(i, DUPLICATE_ID);
				} catch (CursorIndexOutOfBoundsException e) {
					Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
					Logger.logError(e);
				}
				return true;
			}
		} catch (NullPointerException e) {
			Logger.logError(e);
		}
		return true;
	}
	
	/********************************************************
	 * Standard STATIC Methods
	 * ******************************************************
	 */

	/**
	 * Load the EditBook activity based on the provided id. Also open to the provided tab.
	 * 
	 * @param id The id of the book to edit
	 * @param tab Which tab to open first
	 */
	public static void editBook(Activity a, long id, int tab) {
		Intent i = new Intent(a, BookEdit.class);
		i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
		i.putExtra(BookEdit.TAB, tab);
		a.startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
		return;
	}
	
	/**
	 * Initialize a TabSpec according to defined parameters and add it to the TabHost. 
	 * @param tabHost parent TabHost
	 * @param intentClass class for specifying intent. It`s the Activity class contained in this tab.
	 * @param tabTag  required tag of tab
	 * @param titleResId resource id of a title of the tab
	 * @param iconResId resource id of an icon (drawable) of the tab
	 * @param extras extras for putting in the intent. If extras is null they will not be added.
	 */
	private void initTab(TabHost tabHost, Class<?> intentClass, String tabTag, int titleResId, int iconResId, Bundle extras){
		Resources resources = getResources();
		String tabTitle = resources.getString(titleResId);
		Drawable tabIcon = resources.getDrawable(iconResId);
		TabHost.TabSpec spec = tabHost.newTabSpec(tabTag).setIndicator(tabTitle, tabIcon);
		
		Intent intent = new Intent(this, intentClass);
		if (extras != null) {
			intent.putExtras(extras);
		}
		spec.setContent(intent);
		tabHost.addTab(spec);
	}
}
