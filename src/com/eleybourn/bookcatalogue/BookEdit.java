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
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.os.Bundle;
import android.widget.TabHost;

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

}
