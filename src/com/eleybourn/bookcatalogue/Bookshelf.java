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

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.eleybourn.bookcatalogue.compat.BookCatalogueListActivity;
import com.eleybourn.bookcatalogue.utils.Utils;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class Bookshelf extends BookCatalogueListActivity {
	private static final int ACTIVITY_CREATE=0;
	private static final int ACTIVITY_EDIT=1;
	private CatalogueDBAdapter mDbHelper;
	private static final int INSERT_ID = Menu.FIRST + 0;
	private static final int DELETE_ID = Menu.FIRST + 1;

	/* Side-step a bug in HONEYCOMB. It seems that startManagingCursor() in honeycomb causes
	 * child-list cursors for ExpanadableList objects to be closed prematurely. So we seem to have
	 * to roll our own...see http://osdir.com/ml/Android-Developers/2011-03/msg02605.html.
	 */
	private ArrayList<Cursor> mManagedCursors = new ArrayList<Cursor>();
	@Override    
	public void startManagingCursor(Cursor c)
	{     
		synchronized(mManagedCursors) {
			if (!mManagedCursors.contains(c))
				mManagedCursors.add(c);     
		}    
	}

	@Override    
	public void stopManagingCursor(Cursor c)
	{
		synchronized(mManagedCursors) {
			try {
				mManagedCursors.remove(c);				
			} catch (Exception e) {
				// Don;t really care if it's called more than once.
			}
		}
	}

	private void destroyManagedCursors() 
	{
		synchronized(mManagedCursors) {
			for (Cursor c : mManagedCursors) {
				try {
					c.close();
				} catch (Exception e) {
					// Don;t really care if it's called more than once or fails.
				}
			}     
			mManagedCursors.clear();
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_bookshelves);
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		fillBookshelves();
		registerForContextMenu(getListView());
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
	}
	
	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);		
	}

	private void fillBookshelves() {
		// base the layout and the query on the sort order
		int layout = R.layout.row_bookshelf;
		
		// Get all of the rows from the database and create the item list
		Cursor BookshelfCursor = mDbHelper.fetchAllBookshelves();
		startManagingCursor(BookshelfCursor);
		
		// Create an array to specify the fields we want to display in the list
		String[] from = new String[]{CatalogueDBAdapter.KEY_BOOKSHELF, CatalogueDBAdapter.KEY_ROWID};
		
		// and an array of the fields we want to bind those fields to (in this case just text1)
		int[] to = new int[]{R.id.row_bookshelf};
		
		// Now create a simple cursor adapter and set it to display
		SimpleCursorAdapter books = new SimpleCursorAdapter(this, layout, BookshelfCursor, from, to);
		setListAdapter(books);
	}
	
	@Override
	public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, INSERT_ID, 0, R.string.menu_insert_bs)
			.setIcon(R.drawable.ic_action_bookshelf_add)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case INSERT_ID:
			createBookshelf();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete_bs);
	}

    @Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		switch(item.getItemId()) {
    	case DELETE_ID:
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    		if (info.id == 1) {
    			Toast.makeText(this, R.string.delete_1st_bs, Toast.LENGTH_LONG).show();
    		} else {
    			mDbHelper.deleteBookshelf(info.id);
    			fillBookshelves();
    		}
   			return true;
		}
		return super.onContextItemSelected(item);
	}
	
    private void createBookshelf() {
        Intent i = new Intent(this, BookshelfEdit.class);
        startActivityForResult(i, ACTIVITY_CREATE);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i = new Intent(this, BookshelfEdit.class);
        i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
        startActivityForResult(i, ACTIVITY_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        fillBookshelves();
    }
	
	@Override
	protected void onDestroy() {
		destroyManagedCursors();
		super.onDestroy();
		mDbHelper.close();
	}

}
