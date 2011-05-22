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
import java.util.ArrayList;
import java.util.Iterator;

import android.app.AlertDialog;
import android.app.ExpandableListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnGroupCollapseListener;
import android.widget.ExpandableListView.OnGroupExpandListener;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookCatalogue extends ExpandableListActivity {
	/*
	public static final String APP_NAME = "DVD Catalogue";
	public static final String LOCATION = "dvdCatalogue";
	public static final String DATABASE_NAME = "dvd_catalogue";
	public static final String APP_NAME = "CD Catalogue";
	public static final String LOCATION = "cdCatalogue";
	public static final String DATABASE_NAME = "cd_catalogue";
	*/

	// Target size of a thumbnail in a list (bbox dim)
	private static final int LIST_THUMBNAIL_SIZE=60;

	private static final int ACTIVITY_CREATE=0;
	private static final int ACTIVITY_EDIT=1;
	private static final int ACTIVITY_SORT=2;
	private static final int ACTIVITY_ISBN=3;
	private static final int ACTIVITY_SCAN=4;
	private static final int ACTIVITY_ADMIN=5;
	private static final int ACTIVITY_ADMIN_FINISH=6;
	
	private CatalogueDBAdapter mDbHelper;
	private static final int SORT_BY_AUTHOR_EXPANDED = Menu.FIRST + 1; 
	private static final int SORT_BY_AUTHOR_COLLAPSED = Menu.FIRST + 2;
	private static final int SORT_BY = Menu.FIRST + 3; 
	private static final int INSERT_ID = Menu.FIRST + 4;
	private static final int INSERT_ISBN_ID = Menu.FIRST + 5;
	private static final int INSERT_BARCODE_ID = Menu.FIRST + 6;
	private static final int DELETE_ID = Menu.FIRST + 7;
	private static final int ADMIN = Menu.FIRST + 9;
	private static final int EDIT_BOOK = Menu.FIRST + 10;
	private static final int EDIT_BOOK_NOTES = Menu.FIRST + 11;
	private static final int EDIT_BOOK_FRIENDS = Menu.FIRST + 12;
	private static final int SEARCH = Menu.FIRST + 13;
	private static final int INSERT_NAME_ID = Menu.FIRST + 14;

	private static final int DELETE_SERIES_ID = Menu.FIRST + 15;
	private static final int EDIT_AUTHOR_ID = Menu.FIRST + 16;
	private static final int EDIT_SERIES_ID = Menu.FIRST + 17;

	public static String bookshelf = "All Books";
	private ArrayAdapter<String> spinnerAdapter;
	private Spinner mBookshelfText;

	private SharedPreferences mPrefs;
	public int sort = 0;
	private static final int SORT_AUTHOR = 0; 
	private static final int SORT_TITLE = 1; 
	private static final int SORT_SERIES = 2; 
	private static final int SORT_LOAN = 3; 
	private static final int SORT_UNREAD = 4;
	private static final int SORT_GENRE = 5;
	private static final int SORT_AUTHOR_GIVEN = 6;
	private ArrayList<Integer> currentGroup = new ArrayList<Integer>();
	private boolean collapsed = false;

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
	
	private static boolean shown = false;
	private String justAdded = ""; 
	private String search_query = "";
	// These are the states that get saved onPause
	private static final String STATE_SORT = "state_sort"; 
	private static final String STATE_BOOKSHELF = "state_bookshelf"; 
	private static final String STATE_CURRENT_GROUP_COUNT = "state_current_group_count"; 
	private static final String STATE_CURRENT_GROUP = "state_current_group"; 
	private static final String STATE_OPENED = "state_opened";
	private static final int GONE = 8;
	private static final int VISIBLE = 0;
	private static final int BACKUP_PROMPT_WAIT = 5;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Reset the error log if new instance
		if (savedInstanceState == null) {
			Logger.clearLog();
		}

		//check which strings.xml file is currently active
		if (!getString(R.string.app_name).equals(Utils.APP_NAME)) {
			throw new NullPointerException();
		}

		bookshelf = getString(R.string.all_books);
		try {
			super.onCreate(savedInstanceState);
			// Extract the sort type from the bundle. getInt will return 0 if there is no attribute 
			// sort (which is exactly what we want)
			try {
				mPrefs = getSharedPreferences("bookCatalogue", MODE_PRIVATE);
				sort = mPrefs.getInt(STATE_SORT, sort);
				bookshelf = mPrefs.getString(STATE_BOOKSHELF, bookshelf);
				loadCurrentGroup();
			} catch (Exception e) {
				Logger.logError(e);
			}
			// This sets the search capability to local (application) search
			setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
			setContentView(R.layout.list_authors);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			if (savedInstanceState == null) {
				// Analyze DB  if new instance
				mDbHelper.analyzeDb();
			}
			
			// Did the user search
			Intent intent = getIntent();
			if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
				// Return the search results instead of all books (for the bookshelf)
				search_query = intent.getStringExtra(SearchManager.QUERY).trim();
			} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				// Handle a suggestions click (because the suggestions all use ACTION_VIEW)
				search_query = intent.getDataString();
			}
			if (search_query == null || search_query.equals(".")) {
				search_query = "";
			}
			
			bookshelf();
			//fillData();
			if (!CatalogueDBAdapter.message.equals("")) {
				upgradePopup(CatalogueDBAdapter.message);
			}
			if (CatalogueDBAdapter.do_action.equals(CatalogueDBAdapter.DO_UPDATE_FIELDS)) {
				AlertDialog alertDialog = new AlertDialog.Builder(BookCatalogue.this).setMessage(R.string.auto_update).create();
				alertDialog.setTitle(R.string.import_data);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(BookCatalogue.this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						adminPage("update_fields", ACTIVITY_ADMIN);
						return;
					}
				}); 
				alertDialog.setButton2(BookCatalogue.this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
						return;
					}
				}); 
				alertDialog.show();
				return;
			}
			registerForContextMenu(getExpandableListView());
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * This will display a popup with a provided message to the user. This will be
	 * mostly used for upgrade notifications
	 * 
	 * @param message The message to display in the popup
	 */
	public void upgradePopup(String message) {
		if (shown) {
			return;
		}
		AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(message).create();
		alertDialog.setTitle(R.string.upgrade_title);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				return;
			}
		}); 
		alertDialog.show();
		shown = true;
		return;
	}
	
	/**
	 * Setup the bookshelf spinner. This function will also call fillData when 
	 * complete having loaded the appropriate bookshelf. 
	 */
	private void bookshelf() {
		// Setup the Bookshelf Spinner 
		mBookshelfText = (Spinner) findViewById(R.id.bookshelf_name);
		spinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_frontpage);
		spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mBookshelfText.setAdapter(spinnerAdapter);
		
		// Add the default All Books bookshelf
		int pos = 0;
		int bspos = pos;
		spinnerAdapter.add(getString(R.string.all_books)); 
		pos++;
		
		Cursor bookshelves = mDbHelper.fetchAllBookshelves();
		if (bookshelves.moveToFirst()) { 
			do {
				String this_bookshelf = bookshelves.getString(1);
				if (this_bookshelf.equals(bookshelf)) {
					bspos = pos;
				}
				pos++;
				spinnerAdapter.add(this_bookshelf); 
			} 
			while (bookshelves.moveToNext()); 
		} 
		bookshelves.close(); // close the cursor
		// Set the current bookshelf. We use this to force the correct bookshelf after
		// the state has been restored. 
		mBookshelfText.setSelection(bspos);
		
		/**
		 * This is fired whenever a bookshelf is selected. It is also fired when the 
		 * page is loaded with the default (or current) bookshelf.
		 */
		mBookshelfText.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
				String new_bookshelf = spinnerAdapter.getItem(position);
				if (!new_bookshelf.equals(bookshelf)) {
					currentGroup = new ArrayList<Integer>();
				}
				bookshelf = new_bookshelf;
				// save the current bookshelf into the preferences
				SharedPreferences.Editor ed = mPrefs.edit();
				ed.putString(STATE_BOOKSHELF, bookshelf);
				ed.commit();
				fillData();
			}
			
			public void onNothingSelected(AdapterView<?> parentView) {
				// Do Nothing
				
			}
		});
		
		ImageView mBookshelfDown = (ImageView) findViewById(R.id.bookshelf_down);
		mBookshelfDown.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBookshelfText.performClick();
				return;
			}
		});
		
		TextView mBookshelfNum = (TextView) findViewById(R.id.bookshelf_num);
		mBookshelfNum.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBookshelfText.performClick();
				return;
			}
		});
		
	}
	
	/*
	 * Class that handles list/view specific initializations etc.
	 * The member variable mViewManager is set early in object
	 * initialization of the containing class.
	 */
	private abstract class ViewManager {
		protected int mGroupIdColumnIndex;
		protected int mLayout = -1;			// Top level resource I
		protected int mChildLayout = -1;	// Child resource ID
		protected Cursor mCursor = null;	// Top level cursor
		protected String[] mFrom = null;	// Source fields for top level resource
		protected String[] mChildFrom = null;	// Source fields for child
		protected int[] mTo = null;			// Dest field resource IDs for top level
		protected int[] mChildTo = null;	// Dest field resourrce IDs for child
		// Methods to 'get' list/view related items
		public int getLayout() { return mLayout; };
		public int getLayoutChild() { return mChildLayout; };
		public Cursor getCursor() {
			if (mCursor == null) {
				newCursor();
				BookCatalogue.this.startManagingCursor(mCursor);
			}
			return mCursor;
		};
		abstract public Cursor newCursor();
		public String[] getFrom() { return mFrom; };
		public int[] getTo() { return mTo; };
		public String[] getChildFrom() { return mChildFrom; };
		public int[] getChildTo() { return mChildTo; };

		// Get a cursor to retrieve list of children.
		public abstract Cursor getChildrenCursor(Cursor groupCursor);

		public BasicBookListAdapter newAdapter(Context context) {
			return new BasicBookListAdapter(context);
		}

		/**
		 * Adapter for the the expandable list of books. Uses ViewManager to manage
		 * cursor.
		 * 
		 * Note: Could expand ViewManager to hlpwith setViewText, but at the moment
		 * it seems OK to make it completely generic.
		 * 
		 * @author pjw
		 *
		 */
		public class BasicBookListAdapter extends SimpleCursorTreeAdapter {
			/**
			 * 
			 * Pass the parameters directly to the overridden function
			 * 
			 * Note: It would be great to pass a ViewManager to the constructor
			 * as an instance variable, but the 'super' initializer calls 
			 * getChildrenCursor which needs the ViewManager...whih can not be set
			 * before the call to 'super'...so we use an instance variable in the
			 * containing class.
			 * 
			 * @param context
			 */
			public BasicBookListAdapter(Context context) {
				super(context, ViewManager.this.getCursor(), ViewManager.this.getLayout(), 
						ViewManager.this.getFrom(), ViewManager.this.getTo(), 
						ViewManager.this.getLayoutChild(), ViewManager.this.getChildFrom(), 
						ViewManager.this.getChildTo());
			}

			/**
			 * Override the getChildrenCursor. This runs the SQL to extract the titles per author
			 */
			@Override
			protected Cursor getChildrenCursor(Cursor groupCursor) {
				if (mDbHelper == null) // We are terminating
					return null;

				Cursor children = ViewManager.this.getChildrenCursor(groupCursor);
				// TODO THIS CAUSES CRASH IN HONEYCOMB when viewing book details then clicking 'back', so we have 
				// overridden startManagingCursor to only close cursors in onDestroy().
				// FIND A BETTER SOLUTION!
				BookCatalogue.this.startManagingCursor(children);
				return children;
			}

			/**
			 * Make datasetchanged available
			 */
//			@Override
//			public void notifyDataSetChanged() {
//				super.notifyDataSetChanged();
//			}

			/**
			 * Override the setTextView function. This helps us set the appropriate opening and
			 * closing brackets for series numbers and standardize the view.
			 */
			//TODO: @Override
			public void setViewText(TextView v, String text) {
				if (v.getId() == R.id.row_img) {
					boolean field_visibility = mPrefs.getBoolean(FieldVisibility.prefix + "thumbnail", true);
					ImageView newv = (ImageView) ((ViewGroup) v.getParent()).findViewById(R.id.row_image_view);
					if (field_visibility == false) {
						newv.setVisibility(GONE);
					} else {
						CatalogueDBAdapter.fetchThumbnailIntoImageView(Long.parseLong(text),newv, LIST_THUMBNAIL_SIZE,LIST_THUMBNAIL_SIZE, true);
						newv.setVisibility(VISIBLE);
					}
					text = "";
					return;
				} else if (v.getId() == R.id.row_read) {
					boolean field_visibility = mPrefs.getBoolean(FieldVisibility.prefix + "read", true);
					ImageView newv = (ImageView) ((ViewGroup) v.getParent()).findViewById(R.id.row_read_image_view);
					if (field_visibility == false) {
						newv.setVisibility(GONE);
					} else {
						if (text.equals("1")) {
							newv.setImageResource(R.drawable.btn_check_buttonless_on);
						} else {
							newv.setImageResource(R.drawable.btn_check_buttonless_off);
						}
						newv.setVisibility(VISIBLE);
					}
					text = "";
					return;
				} else if (v.getId() == R.id.row_series) {
					// Hide series if necessary.
					if (setFieldVisiblity(v,CatalogueDBAdapter.KEY_SERIES_NAME)) {
						if (sort == SORT_SERIES || text.length() == 0) {
							v.setText(text);						
						} else {
							v.setText("[" + text + "]");
						}
					}
					return;
				} else if (v.getId() == R.id.row_publisher) {
					if (setFieldVisiblity(v,CatalogueDBAdapter.KEY_PUBLISHER))
						v.setText(text);
					return;
				}
				v.setText(text);
			}

			/*
			 * Set layout field visibility and return flag indicating is visible.
			 * 
			 * @param v View to set
			 * @param key The data key to use
			 * 
			 * @returns flag indicating of visible
			 */
			private boolean setFieldVisiblity(View v, String key) {
				// Hide field if necessary.
				boolean field_visibility = mPrefs.getBoolean(FieldVisibility.prefix + key, true);
				if (field_visibility == false) {
					v.setVisibility(GONE);
					return false;
				} else {
					v.setVisibility(VISIBLE);
					return true;
				}
			}

			/**
			 * Utility routine to regenerate the groups cursor using the enclosing ViewManager.
			 */
			private void regenGroups() {
				setGroupCursor(newCursor());
				notifyDataSetChanged();		
			}

		}
	}

	/*
	 * ViewManager for sorting by Title
	 */
	private class TitleViewManager extends ViewManager {
		TitleViewManager() {
			mLayout = R.layout.row_authors;
			mChildLayout = R.layout.row_books; 
			mFrom = new String[]{CatalogueDBAdapter.KEY_ROWID};
			mTo = new int[]{R.id.row_family};			
			mChildFrom = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_PUBLISHER, CatalogueDBAdapter.KEY_SERIES_FORMATTED, CatalogueDBAdapter.KEY_READ};
			mChildTo = new int[]{R.id.row_img, R.id.row_author, R.id.row_title, R.id.row_publisher, R.id.row_series, R.id.row_read};
		}
		public Cursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.equals("")) {
				return mDbHelper.fetchAllBooksByChar(groupCursor.getString(mGroupIdColumnIndex), bookshelf, "");
			} else {
				return mDbHelper.searchBooksByChar(search_query, groupCursor.getString(mGroupIdColumnIndex), bookshelf);
			}
		}
		@Override
		public Cursor newCursor() {
			if (search_query.equals("")) {
				// Return all books (for the bookshelf)
				mCursor = mDbHelper.fetchAllBookChars(bookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDbHelper.searchBooksChars(search_query, bookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
	}
	
	/*
	 * ViewManager for sorting by Author
	 */
	private class AuthorViewManager extends ViewManager {
		AuthorViewManager() {
			mLayout = R.layout.row_authors;
			mChildLayout = R.layout.row_authors_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_AUTHOR_FORMATTED};
			mTo = new int[]{R.id.row_family};	
			mChildFrom = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_SERIES_FORMATTED, CatalogueDBAdapter.KEY_READ};
			mChildTo = new int[]{R.id.row_img, R.id.row_title, R.id.row_series, R.id.row_read};
		}
		public Cursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), bookshelf, search_query);
		}
		@Override
		public Cursor newCursor() {
			if (search_query.equals("")) {
				// Return all books for the given bookshelf
				mCursor = mDbHelper.fetchAllAuthors(bookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDbHelper.searchAuthors(search_query, bookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
	}
	
	/*
	 * ViewManager for sorting by Author
	 */
	private class AuthorFirstViewManager extends ViewManager {
		AuthorFirstViewManager() {
			mLayout = R.layout.row_authors;
			mChildLayout = R.layout.row_authors_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_AUTHOR_FORMATTED_GIVEN_FIRST};
			mTo = new int[]{R.id.row_family};	
			mChildFrom = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_SERIES_FORMATTED, CatalogueDBAdapter.KEY_READ};
			mChildTo = new int[]{R.id.row_img, R.id.row_title, R.id.row_series, R.id.row_read};
		}
		public Cursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), bookshelf, search_query);
		}
		@Override
		public Cursor newCursor() {
			if (search_query.equals("")) {
				// Return all books for the given bookshelf
				mCursor = mDbHelper.fetchAllAuthors(bookshelf, false);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDbHelper.searchAuthors(search_query, bookshelf, false);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
	}

	/*
	 * ViewManager for sorting by Series
	 */
	private class SeriesViewManager extends ViewManager {
		SeriesViewManager() {
			mLayout = R.layout.row_authors;
			mChildLayout = R.layout.row_series_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_SERIES_NAME};
			mTo = new int[]{R.id.row_family};	
			mChildFrom = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_SERIES_NUM, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, CatalogueDBAdapter.KEY_READ};		
			mChildTo = new int[]{R.id.row_img, R.id.row_series_num, R.id.row_title, R.id.row_author, R.id.row_read};
		}
		public Cursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksBySeries(groupCursor.getString(groupCursor.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_NAME)), bookshelf, search_query);
		}
		@Override
		public Cursor newCursor() {
			if (search_query.equals("")) {
				mCursor = mDbHelper.fetchAllSeries(bookshelf, true);
			} else {
				mCursor = mDbHelper.searchSeries(search_query, bookshelf);
			}
			BookCatalogue.this.startManagingCursor(mCursor);
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
	}

	/*
	 * ViewManager for sorting by Loan status
	 */
	private class LoanViewManager extends ViewManager {
		LoanViewManager() {
			mLayout = R.layout.row_authors; 
			mChildLayout = R.layout.row_series_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_ROWID};
			mTo = new int[]{R.id.row_family};	
			mChildFrom = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_AUTHOR_FORMATTED};
			mChildTo = new int[]{R.id.row_img, R.id.row_title, R.id.row_author};
		}
		public Cursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByLoan(groupCursor.getString(mGroupIdColumnIndex), search_query);
		}
		@Override
		public Cursor newCursor() {
			mCursor = mDbHelper.fetchAllLoans();
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
	}

	/*
	 * ViewManager for sorting by Unread 
	 */
	private class UnreadViewManager extends ViewManager {
		UnreadViewManager() {
			mLayout = R.layout.row_authors; 
			mChildLayout = R.layout.row_series_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_ROWID};
			mTo = new int[]{R.id.row_family};	
			mChildFrom = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_AUTHOR_FORMATTED};
			mChildTo = new int[]{R.id.row_img, R.id.row_title, R.id.row_author};
		}
		public Cursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByRead(groupCursor.getString(mGroupIdColumnIndex), bookshelf, search_query);
		}
		@Override
		public Cursor newCursor() {
			mCursor = mDbHelper.fetchAllUnreadPsuedo();
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
	}
	
	/*
	 * ViewManager for sorting by Genre
	 */
	private class GenreViewManager extends ViewManager {
		GenreViewManager() {
			mLayout = R.layout.row_authors;
			mChildLayout = R.layout.row_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_ROWID};
			mTo = new int[]{R.id.row_family};	
			mChildFrom = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, CatalogueDBAdapter.KEY_TITLE, CatalogueDBAdapter.KEY_PUBLISHER, CatalogueDBAdapter.KEY_SERIES_FORMATTED, CatalogueDBAdapter.KEY_READ};
			mChildTo = new int[]{R.id.row_img, R.id.row_author, R.id.row_title, R.id.row_publisher, R.id.row_series, R.id.row_read};
		}
		public Cursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.equals("")) {
				return mDbHelper.fetchAllBooksByGenre(groupCursor.getString(mGroupIdColumnIndex), bookshelf, "");
			} else {
				return mDbHelper.searchBooksByGenre(search_query, groupCursor.getString(mGroupIdColumnIndex), bookshelf);
			}
		}
		@Override
		public Cursor newCursor() {
			if (search_query.equals("")) {
				// Return all books (for the bookshelf)
				mCursor = mDbHelper.fetchAllGenres(bookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDbHelper.searchGenres(search_query, bookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
	}
	
	/**
	 * Build the tree view
	 */
	private void fillData() {
		ViewManager vm;

		/**
		 * Select between the different ViewManager objects based on the sort parameter
		 */
		switch(sort) {
		case SORT_TITLE:
			vm  = new TitleViewManager();
			break;
		case SORT_AUTHOR:
			vm = new AuthorViewManager();
			break;
		case SORT_AUTHOR_GIVEN:
			vm = new AuthorFirstViewManager();
			break;
		case SORT_SERIES:
			vm = new SeriesViewManager();
			break;
		case SORT_LOAN:
			vm = new LoanViewManager();
			break;
		case SORT_UNREAD:
			vm = new UnreadViewManager();
			break;
		case SORT_GENRE:
			vm = new GenreViewManager();
			break;
		default:
			throw new IllegalArgumentException();
		}

		// Manage it
		startManagingCursor(vm.getCursor());

		// Set view title
		if (search_query.equals("")) {
			this.setTitle(R.string.app_name);
		} else {
			int numResults = vm.getCursor().getCount();
			Toast.makeText(this, numResults + " " + this.getResources().getString(R.string.results_found), Toast.LENGTH_LONG).show();
			this.setTitle(getResources().getString(R.string.search_title) + " - " + search_query);
		}
		
		// Instantiate the List Adapter
		ViewManager.BasicBookListAdapter adapter = vm.newAdapter(this); 

		// Handle the click event. Do not open, but goto the book edit page
		ExpandableListView expandableList = getExpandableListView();
		// Extend the onGroupClick (Open) - Every click should add to the currentGroup array
		expandableList.setOnGroupExpandListener(new OnGroupExpandListener() {
			@Override
			public void onGroupExpand(int groupPosition) {
				adjustCurrentGroup(groupPosition, 1, false);
			}
		});
		// Extend the onGroupClick (Close) - Every click should remove from the currentGroup array
		expandableList.setOnGroupCollapseListener(new OnGroupCollapseListener() {
			@Override
			public void onGroupCollapse(int groupPosition) {
				adjustCurrentGroup(groupPosition, -1, false);				
			}
		});
		
		/* Hide the default expandable icon, and use a different icon (actually the same icon)
		 * The override is for when changing back from the title view and it has hidden the icon. */
		Drawable indicator = this.getResources().getDrawable(R.drawable.expander_group); 
		expandableList.setGroupIndicator(indicator);

		setListAdapter(adapter);		
	
		gotoCurrentGroup();
		/* Add number to bookshelf */
		TextView mBookshelfNumView = (TextView) findViewById(R.id.bookshelf_num);
		int numBooks = mDbHelper.countBooks(bookshelf);
		mBookshelfNumView.setText("(" + numBooks + ")");
	}

	/**
	 * Setup the sort options. This function will also call fillData when 
	 * complete having loaded the appropriate view. 
	 */
	private void sortOptions() {
		RadioGroup group = new RadioGroup(this);
		
		RadioButton radio_author = new RadioButton(this);
		radio_author.setText(R.string.sortby_author);
		group.addView(radio_author);
		if (sort == SORT_AUTHOR) {
			radio_author.setChecked(true);
		} else {
			radio_author.setChecked(false);
		}
		
		RadioButton radio_author_given = new RadioButton(this);
		radio_author_given.setText(R.string.sortby_author_given);
		group.addView(radio_author_given);
		if (sort == SORT_AUTHOR_GIVEN) {
			radio_author_given.setChecked(true);
		} else {
			radio_author_given.setChecked(false);
		}
		
		RadioButton radio_title = new RadioButton(this);
		radio_title.setText(R.string.sortby_title);
		group.addView(radio_title);
		if (sort == SORT_TITLE) {
			radio_title.setChecked(true);
		} else {
			radio_title.setChecked(false);
		}
		
		RadioButton radio_series = new RadioButton(this);
		radio_series.setText(R.string.sortby_series);
		group.addView(radio_series);
		if (sort == SORT_SERIES) {
			radio_series.setChecked(true);
		} else {
			radio_series.setChecked(false);
		}
		
		RadioButton radio_genre = new RadioButton(this);
		radio_genre.setText(R.string.sortby_genre);
		group.addView(radio_genre);
		if (sort == SORT_GENRE) {
			radio_genre.setChecked(true);
		} else {
			radio_genre.setChecked(false);
		}
		
		RadioButton radio_loan = new RadioButton(this);
		radio_loan.setText(R.string.sortby_loan);
		group.addView(radio_loan);
		if (sort == SORT_LOAN) {
			radio_loan.setChecked(true);
		} else {
			radio_loan.setChecked(false);
		}
		
		RadioButton radio_unread = new RadioButton(this);
		radio_unread.setText(R.string.sortby_unread);
		group.addView(radio_unread);
		if (sort == SORT_UNREAD) {
			radio_unread.setChecked(true);
		} else {
			radio_unread.setChecked(false);
		}
		
		final AlertDialog sortDialog = new AlertDialog.Builder(this).setView(group).create();
		sortDialog.setTitle(R.string.menu_sort_by);
		sortDialog.setIcon(android.R.drawable.ic_menu_info_details);
		sortDialog.show();
		
		radio_author.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sortByAuthor();
				sortDialog.dismiss();
				return;
			}
		});
		
		radio_author_given.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sortByAuthorGiven();
				sortDialog.dismiss();
				return;
			}
		});
		
		radio_title.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sortByTitle();
				sortDialog.dismiss();
				return;
			}
		});
		
		radio_series.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sortBySeries();
				sortDialog.dismiss();
				return;
			}
		});
		
		radio_genre.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sortByGenre();
				sortDialog.dismiss();
				return;
			}
		});
		
		radio_loan.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sortByLoan();
				sortDialog.dismiss();
				return;
			}
		});
		
		radio_unread.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sortByUnread();
				sortDialog.dismiss();
				return;
			}
		});
	}
	
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		
		MenuItem insert = menu.add(0, INSERT_ID, 0, R.string.menu_insert);
		insert.setIcon(android.R.drawable.ic_menu_add);
		
		MenuItem insertBC = menu.add(0, INSERT_BARCODE_ID, 1, R.string.menu_insert_barcode);
		insertBC.setIcon(R.drawable.ic_menu_insert_barcode);
		
		MenuItem insertISBN = menu.add(0, INSERT_ISBN_ID, 2, R.string.menu_insert_isbn);
		insertISBN.setIcon(android.R.drawable.ic_menu_zoom);
		
		MenuItem insertName = menu.add(0, INSERT_NAME_ID, 2, R.string.menu_insert_name);
		insertName.setIcon(android.R.drawable.ic_menu_zoom);
		
		if (collapsed == true || currentGroup.size() == 0) {
			MenuItem expand = menu.add(0, SORT_BY_AUTHOR_COLLAPSED, 3, R.string.menu_sort_by_author_expanded);
			expand.setIcon(R.drawable.ic_menu_expand);
		} else {
			MenuItem collapse = menu.add(0, SORT_BY_AUTHOR_EXPANDED, 3, R.string.menu_sort_by_author_collapsed);
			collapse.setIcon(R.drawable.ic_menu_collapse);
		}
		
		MenuItem sortby = menu.add(0, SORT_BY, 4, R.string.menu_sort_by);
		sortby.setIcon(android.R.drawable.ic_menu_sort_alphabetically);
		
		String adminTitle = getResources().getString(R.string.help) + " & " + getResources().getString(R.string.menu_administration);
		MenuItem admin = menu.add(0, ADMIN, 5, adminTitle);
		admin.setIcon(android.R.drawable.ic_menu_manage);
		
		MenuItem search = menu.add(0, SEARCH, 4, R.string.menu_search);
		search.setIcon(android.R.drawable.ic_menu_search);
		
		return super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case SORT_BY_AUTHOR_COLLAPSED:
			expandAll();
			return true;
		case SORT_BY_AUTHOR_EXPANDED:
			collapseAll();
			return true;
		case SORT_BY:
			sortOptions();
			return true;
		case INSERT_ID:
			createBook();
			return true;
		case INSERT_ISBN_ID:
			createBookISBN("isbn");
			return true;
		case INSERT_BARCODE_ID:
			createBookScan();
			return true;
		case ADMIN:
			adminPage();
			return true;
		case SEARCH:
			onSearchRequested();
			return true;
		case INSERT_NAME_ID:
			createBookISBN("name");
			return true;
		}
		
		return super.onMenuItemSelected(featureId, item);
	}

	/*
	 * Save Current group to preferences
	 */
	public void saveCurrentGroup() {
		try {
			SharedPreferences.Editor ed = mPrefs.edit();
			ed.putInt(STATE_CURRENT_GROUP_COUNT, currentGroup.size());

			int i = 0;
			Iterator<Integer> arrayIterator = currentGroup.iterator();
			while(arrayIterator.hasNext()) {
				ed.putInt(STATE_CURRENT_GROUP + " " + i, arrayIterator.next());
				i++;
			}
			
			ed.commit();

		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}

	/*
	 * Load Current group from preferences
	 */
	public void loadCurrentGroup() {
		try {
			if (currentGroup != null)
				currentGroup.clear();
			else
				currentGroup = new ArrayList<Integer>();

			int count = mPrefs.getInt(STATE_CURRENT_GROUP_COUNT, -1);

			int i = 0;
			while(i < count) {
				int pos = mPrefs.getInt(STATE_CURRENT_GROUP + " " + i, -1);
				if (pos >= 0)
					adjustCurrentGroup(pos, 1, false);
				i++;
			}

			if (count == 0)
				collapsed = true;

		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}

	/**
	 * Expand and scroll to the current group
	 */
	public void gotoCurrentGroup() {
		try {
			ExpandableListView view = this.getExpandableListView();
			Iterator<Integer> arrayIterator = currentGroup.iterator();
			while(arrayIterator.hasNext()) {
				view.expandGroup(arrayIterator.next());
			}
			
			int pos = currentGroup.size()-1;
			if (pos >= 0)
				view.setSelectedGroup(currentGroup.get(pos));
		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}
		
	/**
	 * add / remove items from the current group arrayList
	 * 
	 * @param pos	The position to add or remove
	 * @param adj	Adjustment to make (+1/-1 = open/close)
	 * @param force	If force is true, then it will be always be added (if adj=1), even if it already exists - but moved to the end
	 */
	public void adjustCurrentGroup(int pos, int adj, boolean force) {
		int index = currentGroup.indexOf(pos);
		if (index == -1) {
			//it does not exist (so is not open), so if adj=1, add to the list
			if (adj > 0) {
				currentGroup.add(pos);
				/* Add the latest position to the preferences */
				saveCurrentGroup();
			}
		} else {
			//it does exist (so is open), so remove from the list if adj=-1
			if (adj < 0) {
				currentGroup.remove(index);	
			} else {
				if (force == true) {
					currentGroup.remove(index);	
					currentGroup.add(pos);
					/* Add the latest position to the preferences */
					saveCurrentGroup();
				}				
			}
		}
		collapsed = (currentGroup.size() == 0);
	}
	
	/**
	 * Expand all Groups
	 */
	public void expandAll() {
		ExpandableListView view = this.getExpandableListView();
		ExpandableListAdapter ad = view.getExpandableListAdapter();
		int numAuthors = ad.getGroupCount();
		currentGroup = new ArrayList<Integer>();
		int i = 0;
		while (i < numAuthors) {
			adjustCurrentGroup(i, 1, false);
			view.expandGroup(i);
			i++;
		}
		collapsed = false;
	}
	
	/**
	 * Collapse all Author Groups. Passes directly to collapseAll(boolean clearCurrent)
	 * 
	 * @see collapseAll(boolean clearCurrent) 
	 */
	public void collapseAll() {
		collapseAll(true);
	}
	
	/**
	 * Collapse all Author Groups
	 * 
	 * @param clearCurrent - Also clear the currentGroup ArrayList
	 */
	public void collapseAll(boolean clearCurrent) {
		// there is no current group anymore
		ExpandableListView view = this.getExpandableListView();
		ExpandableListAdapter ad = view.getExpandableListAdapter();
		int numAuthors = ad.getGroupCount();
		int i = 0;
		while (i < numAuthors) {
			view.collapseGroup(i);
			//if (!expand) {
			//	break;
			//}
			i++;
		}
		if (clearCurrent) {
			currentGroup = new ArrayList<Integer>();
		}
		collapsed = true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
		try {
			// Only delete titles, not authors
			if (ExpandableListView.getPackedPositionType(info.packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
				MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete);
				delete.setIcon(android.R.drawable.ic_menu_delete);
				MenuItem edit_book = menu.add(0, EDIT_BOOK, 0, R.string.edit_book);
				edit_book.setIcon(android.R.drawable.ic_menu_edit);
				MenuItem edit_book_notes = menu.add(0, EDIT_BOOK_NOTES, 0, R.string.edit_book_notes);
				edit_book_notes.setIcon(R.drawable.ic_menu_compose);
				MenuItem edit_book_friends = menu.add(0, EDIT_BOOK_FRIENDS, 0, R.string.edit_book_friends);
				edit_book_friends.setIcon(R.drawable.ic_menu_cc);
			} else if (ExpandableListView.getPackedPositionType(info.packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
				switch(sort) {
				case SORT_AUTHOR:
				case SORT_AUTHOR_GIVEN:
					{
						MenuItem edit_book = menu.add(0, EDIT_AUTHOR_ID, 0, R.string.menu_edit_author);
						edit_book.setIcon(android.R.drawable.ic_menu_edit);
						break;
					}
				case SORT_SERIES:
					{
						MenuItem delete = menu.add(0, DELETE_SERIES_ID, 0, R.string.menu_delete_series);
						delete.setIcon(android.R.drawable.ic_menu_delete);
						MenuItem edit_book = menu.add(0, EDIT_SERIES_ID, 0, R.string.menu_edit_series);
						edit_book.setIcon(android.R.drawable.ic_menu_edit);
						break;
					}
				}
			}
		} catch (NullPointerException e) {
			Logger.logError(e);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
		switch(item.getItemId()) {

		case DELETE_ID:
			int res = StandardDialogs.deleteBookAlert(this, mDbHelper, info.id, new Runnable() {
				@Override
				public void run() {
					mDbHelper.purgeAuthors();
					mDbHelper.purgeSeries();
					fillData();
				}});
			if (res != 0) 
				Toast.makeText(this, res, Toast.LENGTH_LONG).show();
			return true;

		case EDIT_BOOK:
			editBook(info.id, BookEdit.TAB_EDIT);
			return true;

		case EDIT_BOOK_NOTES:
			editBook(info.id, BookEdit.TAB_EDIT_NOTES);
			return true;

		case EDIT_BOOK_FRIENDS:
			editBook(info.id, BookEdit.TAB_EDIT_FRIENDS);
			return true;

		case EDIT_SERIES_ID:
			{
				Series s = mDbHelper.getSeriesById(info.id);
				EditSeriesDialog d = new EditSeriesDialog(this, mDbHelper, new Runnable() {
					@Override
					public void run() {
						mDbHelper.purgeSeries();
						regenGroups();
					}});
				d.editSeries(s);
				break;
			}
		case DELETE_SERIES_ID:
			{
				StandardDialogs.deleteSeriesAlert(this, mDbHelper, mDbHelper.getSeriesById(info.id), new Runnable() {
					@Override
					public void run() {
						regenGroups();
					}});
				break;			
			}
		case EDIT_AUTHOR_ID:
			{
				EditAuthorDialog d = new EditAuthorDialog(this, mDbHelper, new Runnable() {
					@Override
					public void run() {
						mDbHelper.purgeAuthors();
						regenGroups();
					}});
				d.editAuthor(mDbHelper.getAuthorById(info.id));
				break;
			}
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * Utility routine to regenerate the groups cursor.
	 */
	private void regenGroups() {
		ViewManager.BasicBookListAdapter adapter = (ViewManager.BasicBookListAdapter) getExpandableListAdapter();
		adapter.regenGroups();
	}

	/**
	 * Change the sort order of the view and refresh the page
	 */
	private void sortByTitle() {
		sort = SORT_TITLE;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.commit();
	}
	
	/**
	 * Change the sort order of the view and refresh the page
	 */
	private void sortByAuthor() {
		sort = SORT_AUTHOR;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.commit();
	}
	
	/**
	 * Change the sort order of the view and refresh the page
	 */
	private void sortByAuthorGiven() {
		sort = SORT_AUTHOR_GIVEN;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.commit();
	}
	
	/**
	 * Change the sort order of the view and refresh the page
	 */
	private void sortBySeries() {
		sort = SORT_SERIES;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.commit();
	}
	
	/**
	 * Change the sort order of the view and refresh the page
	 */
	private void sortByGenre() {
		sort = SORT_GENRE;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.commit();
	}
	
	/**
	 * Change the sort order of the view and refresh the page
	 */
	private void sortByLoan() {
		sort = SORT_LOAN;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.commit();
	}
	
	/**
	 * Change the sort order of the view and refresh the page
	 */
	private void sortByUnread() {
		sort = SORT_UNREAD;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.commit();
	}
	
	/**
	 * Load the Administration Activity
	 */
	private void adminPage() {
		adminPage("", ACTIVITY_ADMIN);
	}
	
	/**
	 * Load the Administration Activity
	 */
	private void adminPage(String auto, int activityAdmin) {
		Intent i = new Intent(this, Administration.class);
		if (!auto.equals("")) {
			i.putExtra(AdministrationFunctions.DOAUTO, auto);
		}
		startActivityForResult(i, activityAdmin);
	}
	
	/**
	 * Load the BookEdit Activity
	 */
	private void createBook() {
		Intent i = new Intent(this, BookEdit.class);
		startActivityForResult(i, ACTIVITY_CREATE);
	}
	
	/**
	 * Load the Search by ISBN Activity
	 */
	private void createBookISBN(String by) {
		Intent i = new Intent(this, BookISBNSearch.class);
		i.putExtra(BookISBNSearch.BY, by);
		startActivityForResult(i, ACTIVITY_ISBN);
	}
	
	/**
	 * Load the EditBook activity based on the provided id. Also open to the provided tab
	 * 
	 * @param id The id of the book to edit
	 * @param tab Which tab to open first
	 */
	private void editBook(long id, int tab) {
		Intent i = new Intent(this, BookEdit.class);
		i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
		i.putExtra(BookEdit.TAB, tab);
		startActivityForResult(i, ACTIVITY_EDIT);
		return;
	}

	/**
	 * Load the Search by ISBN Activity to begin scanning.
	 */
	private void createBookScan() {
		Intent i = new Intent(this, BookISBNSearch.class);
		i.putExtra(BookISBNSearch.BY, "scan");
		startActivityForResult(i, ACTIVITY_ISBN);
	}

	@Override
	public boolean onChildClick(ExpandableListView l, View v, int position, int childPosition, long id) {
		boolean result = super.onChildClick(l, v, position, childPosition, id);
		adjustCurrentGroup(position, 1, true);
		editBook(id, BookEdit.TAB_EDIT);
		return result;
	}
	
	/**
	 * Called when an activity launched exits, giving you the requestCode you started it with, 
	 * the resultCode it returned, and any additional data from it. 
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ACTIVITY_SCAN:
			try {
				String contents = intent.getStringExtra("SCAN_RESULT");
				Toast.makeText(this, R.string.isbn_found, Toast.LENGTH_LONG).show();
				Intent i = new Intent(this, BookISBNSearch.class);
				i.putExtra("isbn", contents);
				startActivityForResult(i, ACTIVITY_ISBN);
			} catch (NullPointerException e) {
				// This is not a scan result, but a normal return
				fillData();
			}
			break;
		case ACTIVITY_CREATE:
		case ACTIVITY_EDIT:
		case ACTIVITY_SORT:
		case ACTIVITY_ISBN:
		case ACTIVITY_ADMIN:
			try {
				// Use the ADDED_* fields if present.
				if (intent != null && intent.hasExtra(BookEditFields.ADDED_HAS_INFO)) {
					if (sort == SORT_TITLE) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_TITLE);
						int position = mDbHelper.fetchBookPositionByTitle(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true);
					} else if (sort == SORT_AUTHOR) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_AUTHOR);
						int position = mDbHelper.fetchAuthorPositionByName(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true);
					} else if (sort == SORT_AUTHOR_GIVEN) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_AUTHOR);
						int position = mDbHelper.fetchAuthorPositionByGivenName(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true);
					} else if (sort == SORT_SERIES) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_SERIES);
						int position = mDbHelper.fetchSeriesPositionBySeries(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true);
					} else if (sort == SORT_GENRE) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_GENRE);
						int position = mDbHelper.fetchGenrePositionByGenre(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true);
					}					
				}
			} catch (Exception e) {
				Logger.logError(e);
			}
			// We call bookshelf not fillData in case the bookshelves have been updated.
			bookshelf();
			break;
		case ACTIVITY_ADMIN_FINISH:
			finish();
			break;
		}
	}
	
	/**
	 * Restore UI state when loaded.
	 */
	@Override
	public void onResume() {
		try {
			mPrefs = getSharedPreferences("bookCatalogue", MODE_PRIVATE);
			sort = mPrefs.getInt(STATE_SORT, sort);
			bookshelf = mPrefs.getString(STATE_BOOKSHELF, bookshelf);
			loadCurrentGroup();
		} catch (Exception e) {
			Logger.logError(e);
		}
		super.onResume();
	}
	
	/**
	 * Save UI state changes.
	 */
	@Override
	public void onPause() {
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.putString(STATE_BOOKSHELF, bookshelf);
		ed.commit();
		saveCurrentGroup();
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		try {
			destroyManagedCursors();
			if (mDbHelper != null) {
				mDbHelper.close();
				mDbHelper = null;
			}
		} catch (RuntimeException e) {
			// could not be closed (app crash maybe). Don't worry about it
		}
		super.onDestroy();
	} 
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (search_query.equals("")) {
				int opened = mPrefs.getInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
				SharedPreferences.Editor ed = mPrefs.edit();
				if (opened == 0){
					ed.putInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
					ed.commit();
					backupPopup();
					return true;
				} else {
					ed.putInt(STATE_OPENED, opened - 1);
					ed.commit();
				}
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	
	/**
	 * This will display a popup asking if the user would like to backup.
	 */
	public void backupPopup() {
		AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(R.string.backup_request).create();
		alertDialog.setTitle(R.string.backup_title);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				adminPage("export", ACTIVITY_ADMIN_FINISH);
				return;
			}
		}); 
		alertDialog.setButton2("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				finish();
				return;
			}
		}); 
		alertDialog.show();
		return;
	}
	
	/**
	 * Accessor used by Robotium test harness.
	 * 
	 * @param s		New search string.
	 */
	public void setSearchQuery(String s) {
		search_query = s;
		regenGroups();
	}
}