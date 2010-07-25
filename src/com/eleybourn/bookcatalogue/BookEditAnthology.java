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

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class BookEditAnthology extends ListActivity {
	
	private EditText mTitleText;
	private AutoCompleteTextView mAuthorText;
	private Button mAdd;
	//private DatePicker mDate_publishedText;
	//private Spinner mBookshelfText;
	//private ArrayAdapter<String> spinnerAdapter;
	//private AutoCompleteTextView mSeriesText;
	//private EditText mSeriesNumText;
	//private EditText mListPriceText;
	//private EditText mPagesText;
	//private Button mConfirmButton;
	//private Button mCancelButton;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;
	//private ImageView mImageView;
	//private Float rating = Float.parseFloat("0");
	//private boolean read = false;
	//private String notes = "";
	//private String added_series = "";
	//private String added_title = "";
	//private String added_author = "";
	//public static String ADDED_SERIES = "ADDED_SERIES";
	//public static String ADDED_TITLE = "ADDED_TITLE";
	//public static String ADDED_AUTHOR = "ADDED_AUTHOR";
	
	//private static final int DELETE_ID = 1;
	private static final int GONE = 8;
	private static final int DELETE_ID = Menu.FIRST;
	
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
	
	/**
	 * Display the edit fields page
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.list_anthology);
		super.onCreate(savedInstanceState);
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		
		mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		if (mRowId == null) {
			getRowId();
		}
		fillAnthology();
		
		ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthors());
		mAuthorText = (AutoCompleteTextView) findViewById(R.id.add_author);
		mAuthorText.setAdapter(author_adapter);
		//field_visibility = mPrefs.getBoolean(visibility_prefix + "author", true);
		//if (field_visibility == false) {
		//	mAuthorText.setVisibility(GONE);
		//}
		mTitleText = (EditText) findViewById(R.id.add_title);
		
		mAdd = (Button) findViewById(R.id.row_add);
		mAdd.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				String author = mAuthorText.getText().toString();
				String title = mTitleText.getText().toString();
				Log.e("BC", title);
				long result = mDbHelper.createAnthologyTitle(mRowId, author, title);
				Log.e("BC", " " + result);
				fillAnthology();
			}
		});
	}
	
	/**
	 * Populate the bookEditAnthology view
	 */
	public void fillAnthology() {
		int layout = R.layout.row_anthology;
		
		// Get all of the rows from the database and create the item list
		Cursor BooksCursor = mDbHelper.fetchAnthologyByBook(mRowId);
		startManagingCursor(BooksCursor);
		// Create an array to specify the fields we want to display in the list
		String[] from = new String[]{CatalogueDBAdapter.KEY_AUTHOR, CatalogueDBAdapter.KEY_TITLE};
		// and an array of the fields we want to bind those fields to (in this case just text1)
		int[] to = new int[]{R.id.row_author, R.id.row_title};
		// Now create a simple cursor adapter and set it to display
		SimpleCursorAdapter books = new SimpleCursorAdapter(this, layout, BooksCursor, from, to);
		setListAdapter(books);
		
		registerForContextMenu(getListView());
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, DELETE_ID, 0, R.string.menu_delete_anthology);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case DELETE_ID:
				AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
				mDbHelper.deleteAnthologyTitle(info.id);
				fillAnthology();
				return true;
		}
		return super.onContextItemSelected(item);
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
	
	/*
	@Override
	protected void onResume() {
		super.onResume();
		fillAnthology();
	}
	*/

}
