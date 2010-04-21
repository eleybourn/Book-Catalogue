/*
 * @copyright 2010 Evan Leybourn
 */

package com.eleybourn.bookcatalogue;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemSelectedListener;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookCatalogueTitle extends ListActivity {
    private static final int ACTIVITY_CREATE=0;
    private static final int ACTIVITY_EDIT=1;
    private static final int ACTIVITY_SORT=2;

    private CatalogueDBAdapter mDbHelper;
    private static final int SORT_BY_AUTHOR = Menu.FIRST; 
    private static final int INSERT_ID = Menu.FIRST + 1;
    private static final int DELETE_ID = Menu.FIRST + 2;
    private static final int INSERT_ISBN_ID = Menu.FIRST + 3;
    private static final int INSERT_BARCODE_ID = Menu.FIRST + 4;
    private static final int BOOKSHELVES = Menu.FIRST + 5;

    public String bookshelf = "";
    private ArrayAdapter<String> spinnerAdapter;
    public int sort = 0;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_books);
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		bookshelf();
		//fillBooks();
		registerForContextMenu(getListView());
    }
    
    private void bookshelf() {
        // Setup the Bookshelf Spinner 
    	Spinner mBookshelfText = (Spinner) findViewById(R.id.bookshelf_name);
    	spinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_frontpage);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfText.setAdapter(spinnerAdapter);

        /* Add the default All Books bookshelf */
        spinnerAdapter.add(getString(R.string.all_books)); 
        
        Cursor bookshelves = mDbHelper.fetchAllBookshelves();
        if (bookshelves.moveToFirst()) { 
            do { 
            	spinnerAdapter.add(bookshelves.getString(1)); 
            } 
            while (bookshelves.moveToNext()); 
        } 
    	
        mBookshelfText.setOnItemSelectedListener(new OnItemSelectedListener() {
 
			public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
				bookshelf = spinnerAdapter.getItem(position);
				fillBooks();
			}

			public void onNothingSelected(AdapterView<?> parentView) {
				// TODO Auto-generated method stub
				
			}
        });
    }
    
    private void fillBooks() {
    	// base the layout and the query on the sort order
       	int layout = R.layout.row_books;
       	String order = CatalogueDBAdapter.KEY_TITLE + ", " + CatalogueDBAdapter.KEY_FAMILY_NAME;
    	
    	// Get all of the rows from the database and create the item list
    	Cursor BooksCursor = mDbHelper.fetchAllBooks(order, bookshelf);
        startManagingCursor(BooksCursor);
     
        // Create an array to specify the fields we want to display in the list
        String[] from = new String[]{CatalogueDBAdapter.KEY_AUTHOR, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_PUBLISHER};
        
        // and an array of the fields we want to bind those fields to (in this case just text1)
        int[] to = new int[]{R.id.row_author, R.id.row_title, R.id.row_publisher};
        
        // Now create a simple cursor adapter and set it to display
        SimpleCursorAdapter books = new SimpleCursorAdapter(this, layout, BooksCursor, from, to);
        setListAdapter(books);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem author = menu.add(0, SORT_BY_AUTHOR, 0, R.string.menu_sort_by_author);
        author.setIcon(android.R.drawable.ic_menu_sort_alphabetically);

        MenuItem insert = menu.add(1, INSERT_ID, 2, R.string.menu_insert);
        insert.setIcon(android.R.drawable.ic_menu_add);

        MenuItem insertBC = menu.add(1, INSERT_BARCODE_ID, 3, R.string.menu_insert_barcode);
        insertBC.setIcon(R.drawable.ic_menu_insert_barcode);

        MenuItem insertISBN = menu.add(1, INSERT_ISBN_ID, 4, R.string.menu_insert_isbn);
        insertISBN.setIcon(android.R.drawable.ic_menu_zoom);

        MenuItem bookshelf = menu.add(2, BOOKSHELVES, 4, R.string.menu_bookshelf);
        bookshelf.setIcon(R.drawable.ic_menu_bookshelves);
        
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()) {
        case SORT_BY_AUTHOR:
        	sort = SORT_BY_AUTHOR;
        	sortByAuthor();
            return true;
        case INSERT_ID:
            createBook();
            return true;
        case INSERT_ISBN_ID:
        case INSERT_BARCODE_ID:
            createBookISBN();
            return true;
        case BOOKSHELVES:
            manageBookselves();
            return true;
        }

        return super.onMenuItemSelected(featureId, item);
    }
	
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void createBookISBN() {
        Intent i = new Intent(this, BookISBNSearch.class);
        startActivityForResult(i, ACTIVITY_CREATE);
    }
	
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void manageBookselves() {
        Intent i = new Intent(this, Bookshelf.class);
        startActivityForResult(i, ACTIVITY_CREATE);
    }
	
    /*
     * Load the BookCatalogueTitle Activity
     * 
     * return void
     */
    private void sortByAuthor() {
        //Intent i = new Intent(this, BookCatalogue.class);
        //startActivityForResult(i, ACTIVITY_SORT);
    	finish();
    }
	
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete);
	}

    @Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
    	case DELETE_ID:
    		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	        mDbHelper.deleteBook(info.id);
	        fillBooks();
	        return true;
		}
		return super.onContextItemSelected(item);
	}
	
    private void createBook() {
        Intent i = new Intent(this, BookEdit.class);
        startActivityForResult(i, ACTIVITY_CREATE);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i = new Intent(this, BookEdit.class);
        i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
        startActivityForResult(i, ACTIVITY_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        fillBooks();
    }


}