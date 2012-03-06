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
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.Toast;

/**
 * A tab host activity which holds the three edit book tabs
 * 1. Edit Details
 * 2. Edit Comments
 * 3. Loan Book
 * 
 * @author Evan Leybourn
 */
public class BookEdit extends TabActivity {
	public static final String TAB = "tab";
	public static final int TAB_EDIT = 0;
	public static final int TAB_EDIT_NOTES = 1;
	public static final int TAB_EDIT_FRIENDS = 2;

	private static final int DELETE_ID = 1;
	private static final int DUPLICATE_ID = 3; //2 is taken by populate in anthology
	private static final int TWEET_ID = 4; //2 is taken by populate in anthology
	private static final int THUMBNAIL_OPTIONS_ID = 5;

	public int currentTab = 0;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper = new CatalogueDBAdapter(this);

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabhost);
		
		Resources res = getResources(); // Resource object to get Drawables
		TabHost tabHost = getTabHost();  // The activity TabHost
		TabHost.TabSpec spec;  // Resusable TabSpec for each tab
		Intent intent;  // Reusable Intent for each tab
		mDbHelper.open();
		
		//get the passed parameters
		Bundle extras = getIntent().getExtras();
		currentTab = extras != null ? extras.getInt(BookEdit.TAB) : 0;
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
		
		// Create an Intent to launch an Activity for the tab (to be reused)
		intent = new Intent().setClass(this, BookEditFields.class);
		if (extras != null) {
			intent.putExtras(extras);
		}
		//Change the name depending on whether it is a new or existing book
		String name = "";
		if (mRowId == null || mRowId == 0) {
			name = res.getString(R.string.menu_insert);
		} else {
			name = res.getString(R.string.edit_book);
		}
		// Initialise a TabSpec for each tab and add it to the TabHost
		spec = tabHost.newTabSpec("edit_book").setIndicator(name, res.getDrawable(R.drawable.ic_tab_edit)).setContent(intent);
		tabHost.addTab(spec);
		
		// Only show the other tabs if it is an edited book, otherwise only show the first tab
		if (mRowId != null && mRowId > 0) {
			// Do the same for the other tabs
			intent = new Intent().setClass(this, BookEditNotes.class);
			if (extras != null) {
				intent.putExtras(extras);
			}
			spec = tabHost.newTabSpec("edit_book_notes").setIndicator(res.getString(R.string.edit_book_notes), res.getDrawable(R.drawable.ic_tab_notes)).setContent(intent);
			tabHost.addTab(spec);
			
			intent = new Intent().setClass(this, BookEditLoaned.class);
			if (extras != null) {
				intent.putExtras(extras);
			}
			spec = tabHost.newTabSpec("edit_book_friends").setIndicator(res.getString(R.string.edit_book_friends), res.getDrawable(R.drawable.ic_tab_friends)).setContent(intent);
			tabHost.addTab(spec);
			
			// Only show the anthology tab if the book is marked as an anthology
			if (anthology_num != 0) {
				intent = new Intent().setClass(this, BookEditAnthology.class);
				if (extras != null) {
					intent.putExtras(extras);
				}
				spec = tabHost.newTabSpec("edit_book_anthology").setIndicator(res.getString(R.string.edit_book_anthology), res.getDrawable(R.drawable.ic_tab_anthology)).setContent(intent);
				tabHost.addTab(spec);
			}
		}
		
		tabHost.setCurrentTab(currentTab);
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
		super.onDestroy();
		mDbHelper.close();
	} 
	
	@Override 
	protected void onSaveInstanceState(Bundle outState) { 
		super.onSaveInstanceState(outState);
		try {
			outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		} catch (Exception e) {
			//do nothing
		}
	} 
	
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.clear();
		MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete);
		delete.setIcon(android.R.drawable.ic_menu_delete);
		MenuItem duplicate = menu.add(0, DUPLICATE_ID, 0, R.string.menu_duplicate);
		duplicate.setIcon(android.R.drawable.ic_menu_add);
		MenuItem tweet = menu.add(0, TWEET_ID, 0, R.string.menu_share_this);
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
			case TWEET_ID:
				thisBook = mDbHelper.fetchBookById(mRowId);
				thisBook.moveToFirst();
				String title = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_TITLE));
				double rating = thisBook.getDouble(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_RATING));
				String ratingString = "";
				String author = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED_GIVEN_FIRST));
				String series = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_FORMATTED));
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
				
				String url = "https://twitter.com/intent/tweet?related=eleybourn&text=%23reading " + title + " by " + author + series + " " + ratingString;
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(loadweb); 
				return true;
			case DELETE_ID:
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
		a.startActivityForResult(i, R.id.ACTIVITY_EDIT_BOOK);
		return;
	}

}
