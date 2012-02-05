package com.eleybourn.bookcatalogue;

import java.util.Iterator;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.BooksMultitypeListHandler.BooklistChangeListener;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferences;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 * 
 * @author Grunthos
 */
public class BooksOnBookshelf extends ListActivity implements BooklistChangeListener {

	/** Prefix used in preferences for this activity */
	private final static String TAG = "BooksOnBookshelf";

	/** Preference name */
	private final static String PREF_BOOKSHELF = TAG + ".BOOKSHELF";
	/** Preference name */
	private final static String PREF_TOP_ROW = TAG + ".TOP_ROW";
	/** Preference name */
	private final static String PREF_TOP_ROW_TOP = TAG + ".TOP_ROW_TOP";

	/** List of styles used in selecting the desired list style */
	private BooklistStyles mBooklistStyles;
	
	/** Currently selected bookshelf */
	private String mCurrentBookshelf = ""; //getString(R.string.all_books);
	/** Currently selected list style */
	BooklistStyle mCurrentStyle = null;

	/** Flag indicating activity has been destroyed. Used for background tasks */
	private boolean mIsDead = false;
	/** Used by onScroll to detect when the top row has actuallt changed. */
	private int mLastTop = -1;
	/** ProgressDialog used to display "Getting books...". Needed here so we can dismiss it on close. */
	private ProgressDialog mListDialog = null;
	/** A book ID used for keeping/updating current list position, eg. when a book is edited. */
	private long mMarkBookId = 0;
	/** Text to use in search query */
	private String mSearchText = "";
	/** Saved position of last top row */
	private int mTopRow = 0;
	/** Saved position of last top row offset from view top */
	private int mTopRowTop = 0;

	/** Database connection */
	private CatalogueDBAdapter mDb;
	/** Handler used to post runnable events to allow UI update before code executes */
	private Handler mHandler = new Handler();

	/** Handler to manage all Views on the list */
	private BooksMultitypeListHandler mListHandler;
	/** Current displayed list cursor */
	private BooklistCursor mList;
	/** Multi-type adapter to manage list connection to cursor */
	private MultitypeListAdapter mAdapter;
	/** Task queue to get book lists in background */
	private SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("BoB-List", 1);

	@Override
	public void onCreate(Bundle savedInstanceState) {

		try {
			super.onCreate(savedInstanceState);

			mDb = new CatalogueDBAdapter(this);
			mDb.open();

			// Extract the sort type from the bundle. getInt will return 0 if there is no attribute 
			// sort (which is exactly what we want)
			try {
				BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
				
				mCurrentBookshelf = prefs.getString(PREF_BOOKSHELF, mCurrentBookshelf);
				mTopRow = prefs.getInt(PREF_TOP_ROW, 0);
				mTopRowTop = prefs.getInt(PREF_TOP_ROW_TOP, 0);

			} catch (Exception e) {
				Logger.logError(e);
			}

			// This sets the search capability to local (application) search
			setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
			setContentView(R.layout.booksonbookshelf);

			// Did the user search
			Intent intent = getIntent();
			if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
				// Return the search results instead of all books (for the bookshelf)
				mSearchText = intent.getStringExtra(SearchManager.QUERY).trim();
			} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				// Handle a suggestions click (because the suggestions all use ACTION_VIEW)
				mSearchText = intent.getDataString();
			}
			if (mSearchText == null || mSearchText.equals(".")) {
				mSearchText = "";
			}

			// We want context menus to be available
			registerForContextMenu(getListView());

			// use the custom fast scroller (the ListView in the XML is our custome version).
			getListView().setFastScrollEnabled(true);

			// Handle item click events
			getListView().setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View view, int position, long rowId) {
					handleItemClick(arg0, view, position, rowId);
				}});
			
			// Debug; makes list structures vary across calls to ensure code is correct...
			mMarkBookId = -1;

			// This will cause the list to be generated.
			initBookshelfSpinner();
			setupList(true);
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * Handle a list item being clicked.
	 * 
	 * @param arg0		Parent adapter
	 * @param view		Row View that was clicked
	 * @param position	Position of view in listView
	 * @param rowId		_id field from cursor
	 */
	private void handleItemClick(AdapterView<?> arg0, View view, int position, long rowId) {
		// Move the cursor to the position
		mList.moveToPosition(position);
		// If it's a book, edit it.
		if (mList.getRowView().getKind() == RowKinds.ROW_KIND_BOOK) {
			BookEdit.editBook(this, mList.getRowView().getBookId(), BookEdit.TAB_EDIT);
		} else {
			// If it's leve1, expand/collapse. Technically, we could expand/collapse any level
			// but storing and recovering the view becomes unmanageable.
			if (mList.getRowView().getLevel() == 1) {
				mList.getBuilder().toggleExpandNode(mList.getRowView().getAbsolutePosition());
				mList.requery();
				mAdapter.notifyDataSetChanged();
			}
		}
	}

	/**
	 * Build the context menu.
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		try {
			// Just move the cursor and call the handler to do the work.
			mList.moveToPosition(info.position);
			mListHandler.onCreateContextMenu(mList.getRowView(), menu, v, info);
		} catch (NullPointerException e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * Handle selections from context menu
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		mList.moveToPosition(info.position);
		if (mListHandler.onContextItemSelected(mList.getRowView(), this, mDb, item))
			return true;
		else
			return super.onContextItemSelected(item);
	}

	/**
	 * Handle the style that a user has selected.
	 * 
	 * @param name		Name of the selected style
	 */
	private void handleSelectedStyle(String name) {
		// Find the style, if no match warn user and exit
		BooklistStyle style = mBooklistStyles.find(name);
		if (style == null) {
			Toast.makeText(this, "Could not find appropriate list", Toast.LENGTH_LONG).show();
			return;
		}

		// Determine kind of rebuild needed.
		boolean fullRebuild;
		if (!style.getName().equals(mCurrentStyle.getName())) {
			fullRebuild = true;
		} else {
			fullRebuild = false;
		}

		// Do a rebuild
		mCurrentStyle = style;
		setupList(fullRebuild);
	}

	/**
	 * Background task to build and retrieve the list of books based on current settings.
	 *
	 * @author Grunthos
	 */
	private class GetListTask implements SimpleTask {
		/** Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data */
		private final boolean mIsFullRebuild;
		/** Resulting Cursor */
		BooklistCursor mTempList = null;
		/** used to determine new cursor position */
		int mTargetPos = -1;

		/**
		 * Constructor.
		 * 
		 * @param isFullRebuild		Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
		 */
		public GetListTask(boolean isFullRebuild) {
			mIsFullRebuild = isFullRebuild;
		}

		@Override
		public void run() {
			long t0 = System.currentTimeMillis();
			// Build the underlying data
			BooklistBuilder b = buildBooklist(mIsFullRebuild);
			long t1 = System.currentTimeMillis();
			// Try to sync the previously selected book ID
			if (mMarkBookId != 0) {
				// get all positions of the book
				int[] bookRows = b.getBookAbsolutePositions(mMarkBookId);

				if (bookRows != null && bookRows.length > 0) {
					// Find the nearest row to the recorded 'top' row.
					int targetRow = bookRows[0];
					int minDist = Math.abs(mTopRow - b.getPosition(targetRow));
					for(int i=1; i < bookRows.length; i++) {
						int pos = b.getPosition(bookRows[i]);
						int dist = Math.abs(mTopRow - pos);
						if (dist < minDist)
							targetRow = bookRows[i];
					}
					// Make sure the target row is visible/expanded.
					b.ensureAbsolutePositionVisible(targetRow);
					// Now find the position it will occupy in the view
					mTargetPos = b.getPosition(targetRow);
				}
			} else
				mTargetPos = -1;
			long t2 = System.currentTimeMillis();

			// Now we have expanded groups as needed, get the list cursor
			mTempList = b.getList();

			// Clear it so it wont be reused.
			mMarkBookId = 0;
			
			// get a count() from the cursor in background task because the setAdapter() call
			// will do a count() and potentially block the UI thread while it pages through the
			// entire cursor. If we do it here, subsequent calls will be fast.
			long t3 = System.currentTimeMillis();
			int count = mTempList.getCount();
			long t4 = System.currentTimeMillis();

			System.out.println("Build: " + (t1-t0));
			System.out.println("Position: " + (t2-t1));
			System.out.println("Select: " + (t3-t2));
			System.out.println("Count(" + count + "): " + (t4-t3));
			System.out.println("====== " );
			System.out.println("Total: " + (t4-t0));
		}

		@Override
		public void finished() {
			// Make sure activity is not dead.
			if (mIsDead)
				return;
			// Dismiss the progress dialog, if present
			if (mListDialog != null)
				mListDialog.dismiss();
			// Update the data
			if (mTempList != null) {
				displayList(mTempList, mTargetPos);
			}
		}

		@Override
		public boolean runFinished() {
			return true;
		}
		
	}
	
	/**
	 * Queue a rebuild of the underlying cursor and data.
	 * 
	 * @param isFullRebuild		Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
	 */
	private void setupList(boolean isFullRebuild) {
		mTaskQueue.enqueue(new GetListTask(isFullRebuild));
		mListDialog = ProgressDialog.show(this, "", "Getting books...", true, true, new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				// Cancelling the list cancels the activity.
				BooksOnBookshelf.this.finish();
			}});
	}

	/**
	 * Display the passed cursor in the ListView, and change the position to targetRow.
	 * 
	 * @param newList		New cursor to use
	 * @param targetPos		
	 */
	private void displayList(BooklistCursor newList, final int targetPos) {	
		if (newList == null) {
			throw new RuntimeException("Unexpected empty list");
		}

		long t0 = System.currentTimeMillis();
		// Save the old list so we can close it later, and set the new list locally
		BooklistCursor oldList = mList;
		mList = newList;

		// Get new handler and adapter since list may be radically different structure
		mListHandler = new BooksMultitypeListHandler();
		mAdapter = new MultitypeListAdapter(this, mList, mListHandler);

		// Get the ListView and set it up
		final FastScrollListView lv = (FastScrollListView)getListView();
		final ListViewHolder holder = new ListViewHolder();

		lv.setAdapter(mAdapter);
		mAdapter.notifyDataSetChanged();

		// Force a rebuild of FastScroller
		lv.setFastScrollEnabled(false);
		lv.setFastScrollEnabled(true);

		// Restore saved position
		final int count = mList.getCount();
		try {
			if (mTopRow >= count) {
				mTopRow = count-1;
				lv.setSelection(mTopRow);
			} else {
				lv.setSelectionFromTop(mTopRow, mTopRowTop);
			}			
		} catch (Exception e) {}; // Don't really care

		// If a target position is set, then queue a runnable to set the position
		// once we know how many items appear in a typical view and once we 
		// can tell if it is already in the view.
		if (targetPos >= 0) {
			// post a runnable to fix the position once the control is drawn
			if (targetPos != mTopRow) {
				mTopRow = targetPos;
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						int first = lv.getFirstVisiblePosition();
						int last = lv.getLastVisiblePosition();
						// Try to 'centre' it
						if (first > targetPos || last < targetPos)
							lv.setSelection(targetPos - (last-first)/2);
					}});
			}
		}

		final boolean hasLevel1 = (mList.numLevels() > 1);
		final boolean hasLevel2 = (mList.numLevels() > 2);

		if (hasLevel2) {
			holder.level2Text.setVisibility(View.VISIBLE);
			holder.level2Text.setText("");
		} else {
			holder.level2Text.setVisibility(View.GONE);
		}
		if (hasLevel1) {
			holder.level1Text.setVisibility(View.VISIBLE);
			holder.level1Text.setText("");
		} else
			holder.level1Text.setVisibility(View.GONE);

		// Update the header details
		if (count > 0)
			updateListHeader(holder, mTopRow, hasLevel1, hasLevel2);

		// Define a scroller to update header detail when top row changes
		lv.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if (mLastTop != firstVisibleItem) {
					updateListHeader(holder, firstVisibleItem, hasLevel1, hasLevel2);
				}
			}

			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
			}}
		);
		
		// Close old list
		if (oldList != null) {
			oldList.close();
		}
		long t1 = System.currentTimeMillis();
		System.out.println("displayList: " + (t1 - t0));
	}

	/**
	 * Update the list header to match the current top item.
	 * 
	 * @param holder		Holder object for header
	 * @param topItem		Top row
	 * @param hasLevel1		flag indicating level 1 is present
	 * @param hasLevel2		flag indicating level 2 is present
	 */
	private void updateListHeader(ListViewHolder holder, int topItem, boolean hasLevel1, boolean hasLevel2) {
		if (topItem < 0)
			topItem = 0;

		mLastTop = topItem;
		if (hasLevel1) {
			mList.moveToPosition(topItem);
			holder.level1Text.setText(mList.getRowView().getLevel1Data());
			String s = null;
			if (hasLevel2) {
				s = mList.getRowView().getLevel2Data();
				holder.level2Text.setText(s);
			}
		}		
	}

	/**
	 * Build the underlying flattened list of books.
	 * 
	 * @param isFullRebuild		Indicates a complete structural rebuild is required
	 *
	 * @return 	The BooklistBuilder object used to build the data
	 */
	private BooklistBuilder buildBooklist(boolean isFullRebuild) {
		// If not a full rebuild then just use the current builder to requery the underlying data
		if (mList != null && !isFullRebuild) {
			BooklistBuilder b = mList.getBuilder();
			b.rebuild();
			return b;
		} else {
			// Make sure we have a style chosen
			if (mCurrentStyle == null) {
				mBooklistStyles = BooklistStyles.getDefinedStyles();
				String prefStyle = BookCatalogueApp.getAppPreferences().getString(BookCataloguePreferences.PREF_BOOKLIST_STYLE, getString(R.string.sort_author_series));
				mCurrentStyle = mBooklistStyles.find(prefStyle);
				if (mCurrentStyle == null)
					mCurrentStyle = mBooklistStyles.get(0);
				BookCatalogueApp.getAppPreferences().setString(BookCataloguePreferences.PREF_BOOKLIST_STYLE, mCurrentStyle.getName());
			}

			// get a new builder and add the required extra domains
			BooklistBuilder builder = new BooklistBuilder(mDb, mCurrentStyle);

			builder.requireDomain(DOM_TITLE, TBL_BOOKS.dot(DOM_TITLE), true);
			builder.requireDomain(DOM_READ, TBL_BOOKS.dot(DOM_READ), false);
			
			// Close any existing builder
			if (mList != null && mList.getBuilder() != null)
				mList.getBuilder().close();

			// Build based on our current criteria and return
			builder.build(mMarkBookId, mCurrentBookshelf, "", "", "", "", "");

			return builder;			
		}
	}

	/**
	 * record to hold the current ListView header details.
	 * 
	 * @author Grunthos
	 */
	private class ListViewHolder {
		TextView level1Text;
		TextView level2Text;
		public ListViewHolder() {
			level1Text = (TextView)findViewById(R.id.level_1_text);
			level2Text = (TextView)findViewById(R.id.level_2_text);
		}
	}

	/**
	 * Save current position information, including view nodes that are expanded.
	 */
	private void savePosition() {
		// Save expanded nodes
		if (mList != null && mList.getBuilder() != null)
			mList.getBuilder().saveNodeSettings();

		// Save position in list
		final ListView lv = getListView();
		final Editor ed = BookCatalogueApp.getAppPreferences().edit();
		mTopRow = lv.getFirstVisiblePosition();
		ed.putInt(PREF_TOP_ROW, mTopRow);
		View v = lv.getChildAt(0);
		mTopRowTop = v == null ? 0 : v.getTop();
		ed.putInt(PREF_TOP_ROW_TOP, mTopRowTop);
		ed.commit();
	}

	/**
	 * Save position when paused
	 */
	@Override
	public void onPause() {
		super.onPause();
		savePosition();
	}

	/**
	 * Cleanup
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mList != null && mList.getBuilder() != null)
			mList.getBuilder().close();
		mDb.close();
		mIsDead = true;
		mListHandler = null;
	}
	
	/**
	 * Setup the bookshelf spinner. This function will also call fillData when 
	 * complete having loaded the appropriate bookshelf. 
	 */
	private Spinner mBookshelfSpinner;
	private ArrayAdapter<String> mBookshelfAdapter;
	
	private void initBookshelfSpinner() {
		// Setup the Bookshelf Spinner 
		mBookshelfSpinner = (Spinner) findViewById(R.id.bookshelf_name);
		mBookshelfAdapter = new ArrayAdapter<String>(this, R.layout.spinner_frontpage);
		mBookshelfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mBookshelfSpinner.setAdapter(mBookshelfAdapter);
		
		// Add the default All Books bookshelf
		int pos = 0;
		int bspos = pos;
		mBookshelfAdapter.add(getString(R.string.all_books)); 
		pos++;
		
		Cursor bookshelves = mDb.fetchAllBookshelves();
		if (bookshelves.moveToFirst()) { 
			do {
				String this_bookshelf = bookshelves.getString(1);
				if (this_bookshelf.equals(mCurrentBookshelf)) {
					bspos = pos;
				}
				pos++;
				mBookshelfAdapter.add(this_bookshelf); 
			} 
			while (bookshelves.moveToNext()); 
		} 
		bookshelves.close(); // close the cursor
		// Set the current bookshelf. We use this to force the correct bookshelf after
		// the state has been restored. 
		mBookshelfSpinner.setSelection(bspos);
		
		/**
		 * This is fired whenever a bookshelf is selected. It is also fired when the 
		 * page is loaded with the default (or current) bookshelf.
		 */
		mBookshelfSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
				String new_bookshelf = mBookshelfAdapter.getItem(position);
				if (position == 0) {
					new_bookshelf = "";
				}
				if (!new_bookshelf.equalsIgnoreCase(mCurrentBookshelf)) {
					mCurrentBookshelf = new_bookshelf;
					// save the current bookshelf into the preferences
					BookCatalogueApp.BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
					SharedPreferences.Editor ed = prefs.edit();
					ed.putString(PREF_BOOKSHELF, mCurrentBookshelf);
					ed.commit();
					setupList(true);
				}
			}
			
			public void onNothingSelected(AdapterView<?> parentView) {
				// Do Nothing				
			}
		});
		
		ImageView bookshelfDown = (ImageView) findViewById(R.id.bookshelf_down);
		bookshelfDown.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBookshelfSpinner.performClick();
				return;
			}
		});
		
		TextView bookshelfNum = (TextView) findViewById(R.id.bookshelf_num);
		if (bookshelfNum != null) {
			bookshelfNum.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mBookshelfSpinner.performClick();
					return;
				}
			});
		}
	}

	private MenuHandler mMenuHandler;
	private static final int MNU_SORT = MenuHandler.FIRST+1; 
	private static final int MNU_EXPAND = MenuHandler.FIRST+2; 
	private static final int MNU_COLLAPSE = MenuHandler.FIRST+3; 
	private static final int MNU_PREFS = MenuHandler.FIRST+4; 
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		mMenuHandler = new MenuHandler();
		mMenuHandler.init(menu);

		mMenuHandler.addCreateBookItems(menu);

		mMenuHandler.addItem(menu, MNU_SORT, R.string.menu_sort_by, android.R.drawable.ic_menu_sort_alphabetically);
		mMenuHandler.addItem(menu, MNU_PREFS, R.string.preferences, android.R.drawable.ic_menu_manage);

		mMenuHandler.addItem(menu, MNU_EXPAND, R.string.menu_sort_by_author_expanded, R.drawable.ic_menu_expand);
		mMenuHandler.addItem(menu, MNU_COLLAPSE, R.string.menu_sort_by_author_collapsed, R.drawable.ic_menu_collapse);

		mMenuHandler.addSearchItem(menu);

		mMenuHandler.addCreateHelpItem(menu);
		
		return super.onPrepareOptionsMenu(menu);
	}
	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (mMenuHandler != null && !mMenuHandler.onMenuItemSelected(this, featureId, item)) {
			switch(item.getItemId()) {
			case MNU_SORT:
				sortOptions();
				return true;
			case MNU_PREFS:
				doPreferences();
				return true;
			case MNU_EXPAND:
			{
				int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
				savePosition();
				mList.getBuilder().expandAll(true);
				mTopRow = mList.getBuilder().getPosition(oldAbsPos);
				BooklistCursor newList = mList.getBuilder().getList();
				displayList(newList, -1);							
				break;
			}
			case MNU_COLLAPSE:
			{
				int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
				savePosition();
				mList.getBuilder().expandAll(false);
				mTopRow = mList.getBuilder().getPosition(oldAbsPos);
				displayList(mList.getBuilder().getList(), -1);							
				break;
			}
			/*
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
				// Start the Main Menu, not just the Admin page
				mainMenuPage();
				return true;
			case SEARCH:
				onSearchRequested();
				return true;
			case INSERT_NAME_ID:
				createBookISBN("name");
				return true;
			*/
			}			
		}
		
		return super.onMenuItemSelected(featureId, item);
	}

	/**
	 * Called when an activity launched exits, giving you the requestCode you started it with, 
	 * the resultCode it returned, and any additional data from it. 
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		System.out.println("In onActivityResult for BooksOnBookshelf for request " + requestCode);

		mMarkBookId = 0;

		switch(requestCode) {
		case R.id.ACTIVITY_CREATE_BOOK_SCAN:
			try {
				if (intent != null && intent.hasExtra(CatalogueDBAdapter.KEY_ROWID)) {
					long newId = intent.getLongExtra(CatalogueDBAdapter.KEY_ROWID, 0);
					if (newId != 0) {
						mMarkBookId = newId;
						this.setupList(false);
					}
				}
			} catch (NullPointerException e) {
				// This is not a scan result, but a normal return
				//fillData();
			}
			break;
		case R.id.ACTIVITY_CREATE_BOOK_ISBN:
		case R.id.ACTIVITY_CREATE_BOOK_MANUALLY:
		case R.id.ACTIVITY_EDIT_BOOK:
			try {
				if (intent != null && intent.hasExtra(CatalogueDBAdapter.KEY_ROWID)) {
					long id = intent.getLongExtra(CatalogueDBAdapter.KEY_ROWID, 0);
					if (id != 0) {
						mMarkBookId = id;
						this.setupList(false);
					}
				}
			} catch (Exception e) {
				Logger.logError(e);
			}
			break;
		case R.id.ACTIVITY_PREFERENCES:
			this.savePosition();
			this.setupList(true);
			break;
		//case ACTIVITY_SORT:
		//case ACTIVITY_ADMIN:
			/*
			try {
				// Use the ADDED_* fields if present.
				if (intent != null && intent.hasExtra(BookEditFields.ADDED_HAS_INFO)) {
					if (sort == SORT_TITLE) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_TITLE);
						int position = mDbHelper.fetchBookPositionByTitle(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true, false);
					} else if (sort == SORT_AUTHOR) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_AUTHOR);
						int position = mDbHelper.fetchAuthorPositionByName(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true, false);
					} else if (sort == SORT_AUTHOR_GIVEN) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_AUTHOR);
						int position = mDbHelper.fetchAuthorPositionByGivenName(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true, false);
					} else if (sort == SORT_SERIES) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_SERIES);
						int position = mDbHelper.fetchSeriesPositionBySeries(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true, false);
					} else if (sort == SORT_GENRE) {
						justAdded = intent.getStringExtra(BookEditFields.ADDED_GENRE);
						int position = mDbHelper.fetchGenrePositionByGenre(justAdded, bookshelf);
						adjustCurrentGroup(position, 1, true, false);
					}					
				}
			} catch (Exception e) {
				Logger.logError(e);
			}
			*/
			// We call bookshelf not fillData in case the bookshelves have been updated.
		}
		
	}

	/**
	 * Setup the sort options. This function will also call fillData when 
	 * complete having loaded the appropriate view. 
	 */
	private void sortOptions() {
		ScrollView sv = new ScrollView(this);
		RadioGroup group = new RadioGroup(this);
		sv.addView(group);
		final AlertDialog sortDialog = new AlertDialog.Builder(this).setView(sv).create();
		sortDialog.setTitle(R.string.menu_sort_by);
		sortDialog.setIcon(android.R.drawable.ic_menu_info_details);
		sortDialog.show();
		
		Iterator<BooklistStyle> i = mBooklistStyles.iterator();
		while(i.hasNext()) {
			BooklistStyle style = i.next();
			makeRadio(sortDialog, group, style);
		}
	}

	/**
	 * Add a radio box to the sort options dialogue.
	 * 
	 * @param sortDialog
	 * @param group
	 * @param style
	 */
	private void makeRadio (final AlertDialog sortDialog, RadioGroup group, final BooklistStyle style) {
		RadioButton btn = new RadioButton(this);
		btn.setText(style.getName());
		group.addView(btn);
		if (mCurrentStyle.getName().equalsIgnoreCase(style.getName())) {
			btn.setChecked(true);
		} else {
			btn.setChecked(false);
		}
		btn.setOnClickListener( new OnClickListener() {
			@Override
			public void onClick(View v) {
				handleSelectedStyle(style.getName());
				sortDialog.dismiss();
				return;
			}
		});
	}

	/**
	 * Start the BooklistPreferences Activity
	 */
	public void doPreferences() {
		Intent i = new Intent(this, BooklistPreferences.class);
		startActivityForResult(i, R.id.ACTIVITY_PREFERENCES);		
	}

	@Override
	public void onBooklistChange(int flags) {
		if (flags != 0) {
			// Author or series changed. Just regenerate.
			savePosition();
			this.setupList(true);
		}
	}
}
