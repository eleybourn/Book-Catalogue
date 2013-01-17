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

import net.philipwarner.taskqueue.QueueManager;
import android.app.AlertDialog;
import android.app.ExpandableListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnGroupCollapseListener;
import android.widget.ExpandableListView.OnGroupExpandListener;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ResourceCursorTreeAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.SendOneBookTask;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.eleybourn.bookcatalogue.widgets.FastScrollExpandableListView;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookCatalogueClassic extends ExpandableListActivity {
	
	// Target size of a thumbnail in a list (bbox dim)
	private static final int LIST_THUMBNAIL_SIZE=60;
		
	private CatalogueDBAdapter mDbHelper;
	private static final int SORT_BY_AUTHOR_EXPANDED = MenuHandler.FIRST + 1; 
	private static final int SORT_BY_AUTHOR_COLLAPSED = MenuHandler.FIRST + 2;
	private static final int SORT_BY = MenuHandler.FIRST + 3; 
	private static final int DELETE_ID = MenuHandler.FIRST + 7;
	private static final int EDIT_BOOK = MenuHandler.FIRST + 10;
	private static final int EDIT_BOOK_NOTES = MenuHandler.FIRST + 11;
	private static final int EDIT_BOOK_FRIENDS = MenuHandler.FIRST + 12;
	private static final int DELETE_SERIES_ID = MenuHandler.FIRST + 15;
	private static final int EDIT_AUTHOR_ID = MenuHandler.FIRST + 16;
	private static final int EDIT_SERIES_ID = MenuHandler.FIRST + 17;
	private static final int EDIT_BOOK_SEND_TO_GR = MenuHandler.FIRST + 19;
	
	private String bookshelf = "";
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
	private static final int SORT_AUTHOR_ONE = 7;
	private static final int SORT_PUBLISHED = 8;
	private ArrayList<Integer> currentGroup = new ArrayList<Integer>();
	private Long mLoadingGroups = 0L;
	private boolean collapsed = false;

	/** Utils object; we need an instance for cover retrieval because it uses a DB connection
	 * that we do not want to make static.
	 */
	private Utils mUtils = new Utils();

	private SimpleTaskQueue mTaskQueue = null;

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
	
	private String justAdded = ""; 
	private String search_query = "";
	// These are the states that get saved onPause
	private static final String STATE_SORT = "state_sort"; 
	//private static final String STATE_BOOKSHELF = "state_bookshelf"; 
	private static final String STATE_CURRENT_GROUP_COUNT = "state_current_group_count"; 
	private static final String STATE_CURRENT_GROUP = "state_current_group"; 

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		//check which strings.xml file is currently active
		if (!getString(R.string.system_app_name).equals(Utils.APP_NAME)) {
			throw new NullPointerException();
		}

		bookshelf = getString(R.string.all_books);
		try {
			super.onCreate(savedInstanceState);

			// In V4.0 the startup activity is StartupActivity, but we need to deal with old icons. 
			// So we check the intent.
			// TODO: Consider renaming 'BookCatalogue' activity to 'BookCatalogueClassic' and creating a dummy BookCatalgue activity stub to avoid this check
			if ( ! StartupActivity.hasBeenCalled() ) {
				// The startup activity has NOT been called; this may be because of a restart after FC, in which case the action may be null, or may be valid
				Intent i = getIntent();
				final String action = i.getAction();
				if (action != null && action.equals("android.intent.action.MAIN") && i.hasCategory("android.intent.category.LAUNCHER")) {
					// This is a startup for the main application, so defer it to the StartupActivity
					System.out.println("Old shortcut detected, redirecting");
					i = new Intent(this.getApplicationContext(), StartupActivity.class);
					startActivity(i);
					finish();
					return;
				}
			}

			// Extract the sort type from the bundle. getInt will return 0 if there is no attribute 
			// sort (which is exactly what we want)
			try {
				mPrefs = getSharedPreferences("bookCatalogue", MODE_PRIVATE);
				sort = mPrefs.getInt(STATE_SORT, sort);
				bookshelf = mPrefs.getString(BooksOnBookshelf.PREF_BOOKSHELF, bookshelf);
				loadCurrentGroup();
			} catch (Exception e) {
				Logger.logError(e);
			}
			// This sets the search capability to local (application) search
			setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
			setContentView(R.layout.list_authors);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
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

			if (CatalogueDBAdapter.do_action.equals(CatalogueDBAdapter.DO_UPDATE_FIELDS)) {
				AlertDialog alertDialog = new AlertDialog.Builder(BookCatalogueClassic.this).setMessage(R.string.auto_update).create();
				alertDialog.setTitle(R.string.import_data);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(BookCatalogueClassic.this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Administration.adminPage(BookCatalogueClassic.this, "update_fields", UniqueId.ACTIVITY_ADMIN);
						return;
					}
				}); 
				alertDialog.setButton2(BookCatalogueClassic.this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
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
			// Need to finish this activity, otherwise we end up in an invalid state.
			finish();
		}
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
				if (position == 0) {
					new_bookshelf = "";
				}
				if (!new_bookshelf.equals(bookshelf)) {
					currentGroup = new ArrayList<Integer>();
				}
				bookshelf = new_bookshelf;
				// save the current bookshelf into the preferences
				SharedPreferences.Editor ed = mPrefs.edit();
				ed.putString(BooksOnBookshelf.PREF_BOOKSHELF, bookshelf);
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
	 * 
	 * All child views are assumed to have books in them and a single
	 * method call is made to bind a child view.
	 */
	private abstract class ViewManager {
		protected int mGroupIdColumnIndex;
		protected int mLayout = -1;			// Top level resource I
		protected int mChildLayout = -1;	// Child resource ID
		protected Cursor mCursor = null;	// Top level cursor
		protected String[] mFrom = null;	// Source fields for top level resource
		protected int[] mTo = null;			// Dest field resource IDs for top level
		// Methods to 'get' list/view related items
		public int getLayout() { return mLayout; };
		public int getLayoutChild() { return mChildLayout; };
		public Cursor getCursor() {
			if (mCursor == null) {
				newGroupCursor();
				BookCatalogueClassic.this.startManagingCursor(mCursor);
			}
			return mCursor;
		};
		abstract public Cursor newGroupCursor();
		public String[] getFrom() { return mFrom; };
		public int[] getTo() { return mTo; };
		/**
		 * Method to return the group cursor column that contains text that can be used 
		 * to derive the section name used by the FastScroller overlay.
		 * 
		 * @return	column number
		 */
		abstract public int getSectionNameColum();

		/**
		 * Get a cursor to retrieve list of children; must be a database cursor
		 * and will be converted to a CursorSnapshotCursor
		 */
		public abstract SQLiteCursor getChildrenCursor(Cursor groupCursor);

		public BasicBookListAdapter newAdapter(Context context) {
			return new BasicBookListAdapter(context);
		}

		/**
		 * Record to store the details of a TextView in the list items.
		 */
		private class TextViewInfo {
			boolean show;
			TextView view;
		}
		/**
		 * Record to store the details of a ImafeView in the list items.
		 */
		private class ImageViewInfo {
			boolean show;
			ImageView view;
		}

		/**
		 * Record to implement the 'holder' model for the list.
		 */
		private class BookHolder {
			TextViewInfo author = new TextViewInfo();
			TextViewInfo title = new TextViewInfo();
			TextViewInfo series = new TextViewInfo();
			ImageViewInfo image = new ImageViewInfo();
			TextViewInfo publisher = new TextViewInfo();
			ImageViewInfo read = new ImageViewInfo();
		}

		/**
		 * Adapter for the the expandable list of books. Uses ViewManager to manage
		 * cursor.
		 * 
		 * @author Philip Warner
		 */
		public class BasicBookListAdapter extends ResourceCursorTreeAdapter implements android.widget.SectionIndexer {

			/** A local Inflater for convenience */
			LayoutInflater mInflater;
			
			/**
			 * 
			 * Pass the parameters directly to the overridden function and
			 * create an Inflater for use later.
			 * 
			 * Note: It would be great to pass a ViewManager to the constructor
			 * as an instance variable, but the 'super' initializer calls 
			 * getChildrenCursor which needs the ViewManager...which can not be set
			 * before the call to 'super'...so we use an instance variable in the
			 * containing class.
			 * 
			 * @param context
			 */
			private int[] mFromCols = null;
			private final int[] mToIds;
			public BasicBookListAdapter(Context context) {
				super(context, ViewManager.this.getCursor(), ViewManager.this.getLayout(), ViewManager.this.getLayoutChild());

				mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				mToIds = ViewManager.this.getTo();
			}

			/**
			 * Bind the passed 'from' text fields to the 'to' fields.
			 * 
			 * If anything more fancy is needed, we probably need to implement it
			 * in the subclass.
			 */
			@Override
			protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
				if (mFromCols == null) {
					String [] fromNames = ViewManager.this.getFrom();
					mFromCols = new int[fromNames.length];
					for(int i = 0 ; i < fromNames.length; i++ )
						mFromCols[i] = cursor.getColumnIndex(fromNames[i]);
				}
		        for (int i = 0; i < mToIds.length; i++) {
		            View v = view.findViewById(mToIds[i]);
		            if (v != null) {
		                String text = cursor.getString(mFromCols[i]);
		                if (text == null) {
		                    text = "";
		                }
		                if (v instanceof TextView) {
		                    ((TextView) v).setText(text);
		                } else {
		                    throw new IllegalStateException("Can only bind to TextView for groups");
		                }
		            }
		        }
			}
			
			/**
			 * Override the getChildrenCursor. This runs the SQL to extract the titles per author
			 */
			@Override
			protected Cursor getChildrenCursor(Cursor groupCursor) {
				if (mDbHelper == null) // We are terminating
					return null;

				// Get the DB cursor
				SQLiteCursor children = ViewManager.this.getChildrenCursor(groupCursor);

				// // Make a snapshot of it to avoid keeping potentially hundreds of cursors open
				// // If we ever set Android 2.0 as minimum, do this...
				// CursorSnapshotCursor csc;
				// if (children instanceof BooksCursor) {
				// 	csc = new BooksSnapshotCursor(children);
				// } else {
				// 	csc = new CursorSnapshotCursor(children);
				// }
				// children.close();
				// BookCatalogue.this.startManagingCursor(csc);

				// TODO FIND A BETTER CURSOR MANAGEMENT SOLUTION!
				// THIS CAUSES CRASH IN HONEYCOMB when viewing book details then clicking 'back', so we have 
				// overridden startManagingCursor to only close cursors in onDestroy().
				BookCatalogueClassic.this.startManagingCursor(children);
				return children;
			}

			/**
			 * Setup the related info record based on actual View contents
			 */
			private void initViewInfo(View v, TextViewInfo info, int id, String setting) {
				info.show = mPrefs.getBoolean(setting, true);
				info.view = (TextView) v.findViewById(id);
				if (!info.show) {
					if (info.view != null)
						info.view.setVisibility(View.GONE);
				} else {
					info.show = (info.view != null);
					if (info.show)
						info.view.setVisibility(View.VISIBLE);						
				}				
			}
			/**
			 * Setup the related info record based on actual View contents
			 */
			private void initViewInfo(View v, ImageViewInfo info, int id, String setting) {
				info.show = mPrefs.getBoolean(setting, true);
				info.view = (ImageView) v.findViewById(id);
				if (!info.show) {
					info.view.setVisibility(View.GONE);
				} else {
					info.show = (info.view != null);
					if (info.show)
						info.view.setVisibility(View.VISIBLE);						
				}				
			}

			/**
			 * Override the newChildView method so we can implement a holder model to improve performance.
			 */
			@Override
			public View newChildView(Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
				View v = mInflater.inflate(ViewManager.this.getLayoutChild(), parent, false);
				BookHolder holder = new BookHolder();

				initViewInfo(v, holder.author, R.id.row_author, FieldVisibility.prefix + CatalogueDBAdapter.KEY_AUTHOR_NAME);
				initViewInfo(v, holder.title, R.id.row_title, FieldVisibility.prefix + CatalogueDBAdapter.KEY_TITLE);
				initViewInfo(v, holder.image, R.id.row_image_view, FieldVisibility.prefix + "thumbnail");
				initViewInfo(v, holder.publisher, R.id.row_publisher, FieldVisibility.prefix + CatalogueDBAdapter.KEY_PUBLISHER);
				initViewInfo(v, holder.read, R.id.row_read_image_view, FieldVisibility.prefix + "read");
				initViewInfo(v, holder.series, R.id.row_series, FieldVisibility.prefix + CatalogueDBAdapter.KEY_SERIES_NAME);

				ViewTagger.setTag(v, R.id.TAG_HOLDER, holder);

				return v;
			}
			
			/**
			 * Rather than having setText/setImage etc, or using messy from/to fields, 
			 * we just bind child views using a Holder object and the cursor RowView.
			 */
			@Override 
			protected void bindChildView(View view, Context context, Cursor origCursor, boolean isLastChild) {
				BookHolder holder = (BookHolder) ViewTagger.getTag(view, R.id.TAG_HOLDER);
				final BooksCursor snapshot = (BooksCursor) origCursor;
				final BooksRowView rowView = snapshot.getRowView();

				if (holder.author.show)
					holder.author.view.setText(rowView.getPrimaryAuthorName());

				if (holder.title.show)
					holder.title.view.setText(rowView.getTitle());

				if (holder.image.show) {
					//CatalogueDBAdapter.fetchThumbnailIntoImageView(cursor.getId(),holder.image.view, LIST_THUMBNAIL_SIZE, LIST_THUMBNAIL_SIZE, true, mTaskQueue);
					mUtils.fetchBookCoverIntoImageView(holder.image.view, LIST_THUMBNAIL_SIZE, LIST_THUMBNAIL_SIZE, true, rowView.getBookUuid(), 
														BooklistPreferencesActivity.isThumbnailCacheEnabled(), BooklistPreferencesActivity.isBackgroundThumbnailsEnabled());
				}

				if (holder.read.show) {
					int read;
					try {
						read = rowView.getRead();
					} catch (Exception e) {
						read = 0;
					}
					if (read == 1) {
						holder.read.view.setImageResource(R.drawable.btn_check_buttonless_on);
					} else {
						holder.read.view.setImageResource(R.drawable.btn_check_buttonless_off);
					}
				}
				
				if (holder.series.show) {
					String series = rowView.getSeries();
					if (sort == SORT_SERIES || series.length() == 0) {
						holder.series.view.setText("");						
					} else {
						holder.series.view.setText("[" + series + "]");						
					}					
				}

				if (holder.publisher.show)
					holder.publisher.view.setText(rowView.getPublisher());
			}

			/**
			 * Utility routine to regenerate the groups cursor using the enclosing ViewManager.
			 */
			private void regenGroups() {
				setGroupCursor(newGroupCursor());
				notifyDataSetChanged();	
				// Reset the scroller, just in case
				FastScrollExpandableListView fselv = (FastScrollExpandableListView)BookCatalogueClassic.this.getExpandableListView();
				fselv.setFastScrollEnabled(false);
				fselv.setFastScrollEnabled(true);
			}

			/**
			 * Get section names for the FastScroller. We just return all the groups.
			 */
			@Override
			public Object[] getSections() {
				// Get the group cursor and save its position
				Cursor c = ViewManager.this.getCursor();
				int savedPosition = c.getPosition();
				// Create the string array
				int count = c.getCount();
				String[] sections = new String[count];
				c.moveToFirst();
				// Get the column number from the cursor column we use for sections.
				int sectionCol = ViewManager.this.getSectionNameColum();
				// Populate the sections
				for(int i = 0; i < count; i++) {
					sections[i] = c.getString(sectionCol);
					c.moveToNext();
				}
				// Reset cursor and return
				c.moveToPosition(savedPosition);
				return sections;
			}

			/**
			 * Passed a section number, return the flattened position in the list
			 */
			@Override
			public int getPositionForSection(int section) {
				return getExpandableListView().getFlatListPosition(ExpandableListView.getPackedPositionForGroup(section));
			}

			/**
			 * Passed a flattened position in the list, return the section number
			 */
			@Override
			public int getSectionForPosition(int position) {
				final ExpandableListView list = getExpandableListView();
				long packedPos = list.getExpandableListPosition(position);
				return ExpandableListView.getPackedPositionGroup(packedPos);
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
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.equals("")) {
				return mDbHelper.fetchAllBooksByChar(groupCursor.getString(mGroupIdColumnIndex), bookshelf, "");
			} else {
				return mDbHelper.searchBooksByChar(search_query, groupCursor.getString(mGroupIdColumnIndex), bookshelf);
			}
		}
		@Override
		public Cursor newGroupCursor() {
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
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
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
		}
		public SQLiteCursor getChildrenCursor(final Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), bookshelf, search_query, false);
		}
		@Override
		public Cursor newGroupCursor() {
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
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
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
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), bookshelf, search_query, false);
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.equals("")) {
				// Return all books for the given bookshelf
				mCursor = mDbHelper.fetchAllAuthors(bookshelf, false, false);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDbHelper.searchAuthors(search_query, bookshelf, false, false);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);
		}
	}
	
	/*
	 * ViewManager for sorting by Author
	 */
	private class AuthorOneViewManager extends ViewManager {
		AuthorOneViewManager() {
			mLayout = R.layout.row_authors;
			mChildLayout = R.layout.row_authors_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_AUTHOR_FORMATTED};
			mTo = new int[]{R.id.row_family};	
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), bookshelf, search_query, true);
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.equals("")) {
				// Return all books for the given bookshelf
				mCursor = mDbHelper.fetchAllAuthors(bookshelf, true, true);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDbHelper.searchAuthors(search_query, bookshelf, true, true); 
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
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
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksBySeries(groupCursor.getString(groupCursor.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_NAME)), bookshelf, search_query);
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.equals("")) {
				mCursor = mDbHelper.fetchAllSeries(bookshelf, true);
			} else {
				mCursor = mDbHelper.searchSeries(search_query, bookshelf);
			}
			BookCatalogueClassic.this.startManagingCursor(mCursor);
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_NAME);
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
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByLoan(groupCursor.getString(mGroupIdColumnIndex), search_query);
		}
		@Override
		public Cursor newGroupCursor() {
			mCursor = mDbHelper.fetchAllLoans();
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
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
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDbHelper.fetchAllBooksByRead(groupCursor.getString(mGroupIdColumnIndex), bookshelf, search_query);
		}
		@Override
		public Cursor newGroupCursor() {
			mCursor = mDbHelper.fetchAllUnreadPsuedo();
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
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
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.equals("")) {
				return mDbHelper.fetchAllBooksByGenre(groupCursor.getString(mGroupIdColumnIndex), bookshelf, "");
			} else {
				return mDbHelper.searchBooksByGenre(search_query, groupCursor.getString(mGroupIdColumnIndex), bookshelf);
			}
		}
		@Override
		public Cursor newGroupCursor() {
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
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
		}
	}
	
	/*
	 * ViewManager for sorting by Genre
	 */
	private class PublishedViewManager extends ViewManager {
		PublishedViewManager() {
			mLayout = R.layout.row_authors;
			mChildLayout = R.layout.row_books;
			mFrom = new String[]{CatalogueDBAdapter.KEY_ROWID};
			mTo = new int[]{R.id.row_family};	
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.equals("")) {
				return mDbHelper.fetchAllBooksByDatePublished(groupCursor.getString(mGroupIdColumnIndex), bookshelf, "");
			} else {
				return mDbHelper.searchBooksByDatePublished(search_query, groupCursor.getString(mGroupIdColumnIndex), bookshelf);
			}
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.equals("")) {
				// Return all books (for the bookshelf)
				mCursor = mDbHelper.fetchAllDatePublished(bookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDbHelper.searchDatePublished(search_query, bookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			return mCursor;
		}
		@Override
		public int getSectionNameColum() {
			return mCursor.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
		}
	}
	
	/**
	 * Build the tree view
	 */
	private void fillData() {
		//check and reset mDbHelper. Avoid leaking cursors. Each one is 1MB (allegedly)!
		Cursor c = null;
		try {
			c = mDbHelper.fetchAllAuthors(bookshelf);
		} catch (NullPointerException e) {
			//reset
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
		} finally {
			if (c != null)
				c.close();
		}
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
		case SORT_AUTHOR_ONE:
			vm = new AuthorOneViewManager();
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
		case SORT_PUBLISHED:
			vm = new PublishedViewManager();
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
				if (mLoadingGroups == 0)
					adjustCurrentGroup(groupPosition, 1, false, false);
			}
		});
		// Extend the onGroupClick (Close) - Every click should remove from the currentGroup array
		expandableList.setOnGroupCollapseListener(new OnGroupCollapseListener() {
			@Override
			public void onGroupCollapse(int groupPosition) {
				if (mLoadingGroups == 0)
					adjustCurrentGroup(groupPosition, -1, false, false);
			}
		});
		
		/* Hide the default expandable icon, and use a different icon (actually the same icon)
		 * The override is for when changing back from the title view and it has hidden the icon. */
		Drawable indicator = this.getResources().getDrawable(R.drawable.expander_group); 
		expandableList.setGroupIndicator(indicator);
		
		setListAdapter(adapter);		
		// Force a rebuild of the fast scroller
		adapterChanged();

		adapter.notifyDataSetChanged();
		
		gotoCurrentGroup();
		/* Add number to bookshelf */
		TextView mBookshelfNumView = (TextView) findViewById(R.id.bookshelf_num);
		try {
			int numBooks = mDbHelper.countBooks(bookshelf);
			mBookshelfNumView.setText("(" + numBooks + ")");
		} catch (IllegalStateException e) {
			Logger.logError(e);
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
		
		RadioButton radio_author = new RadioButton(this);
		radio_author.setText(R.string.sortby_author);
		group.addView(radio_author);
		if (sort == SORT_AUTHOR) {
			radio_author.setChecked(true);
		} else {
			radio_author.setChecked(false);
		}
		radio_author.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_AUTHOR);
				sortDialog.dismiss();
				return;
			}
		});
		
		RadioButton radio_author_one = new RadioButton(this);
		radio_author_one.setText(R.string.sortby_author_one);
		group.addView(radio_author_one);
		if (sort == SORT_AUTHOR_ONE) {
			radio_author_one.setChecked(true);
		} else {
			radio_author_one.setChecked(false);
		}
		radio_author_one.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_AUTHOR_ONE);
				sortDialog.dismiss();
				return;
			}
		});
		
		RadioButton radio_author_given = new RadioButton(this);
		radio_author_given.setText(R.string.sortby_author_given);
		group.addView(radio_author_given);
		if (sort == SORT_AUTHOR_GIVEN) {
			radio_author_given.setChecked(true);
		} else {
			radio_author_given.setChecked(false);
		}
		radio_author_given.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_AUTHOR_GIVEN);
				sortDialog.dismiss();
				return;
			}
		});
		
		RadioButton radio_title = new RadioButton(this);
		radio_title.setText(R.string.sortby_title);
		group.addView(radio_title);
		if (sort == SORT_TITLE) {
			radio_title.setChecked(true);
		} else {
			radio_title.setChecked(false);
		}
		radio_title.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_TITLE);
				sortDialog.dismiss();
				return;
			}
		});
		
		RadioButton radio_series = new RadioButton(this);
		radio_series.setText(R.string.sortby_series);
		group.addView(radio_series);
		if (sort == SORT_SERIES) {
			radio_series.setChecked(true);
		} else {
			radio_series.setChecked(false);
		}
		radio_series.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_SERIES);
				sortDialog.dismiss();
				return;
			}
		});
		
		RadioButton radio_genre = new RadioButton(this);
		radio_genre.setText(R.string.sortby_genre);
		group.addView(radio_genre);
		if (sort == SORT_GENRE) {
			radio_genre.setChecked(true);
		} else {
			radio_genre.setChecked(false);
		}
		radio_genre.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_GENRE);
				sortDialog.dismiss();
				return;
			}
		});

		
		RadioButton radio_loan = new RadioButton(this);
		radio_loan.setText(R.string.sortby_loan);
		group.addView(radio_loan);
		if (sort == SORT_LOAN) {
			radio_loan.setChecked(true);
		} else {
			radio_loan.setChecked(false);
		}
		radio_loan.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_LOAN);
				sortDialog.dismiss();
				return;
			}
		});
		
		RadioButton radio_unread = new RadioButton(this);
		radio_unread.setText(R.string.sortby_unread);
		group.addView(radio_unread);
		if (sort == SORT_UNREAD) {
			radio_unread.setChecked(true);
		} else {
			radio_unread.setChecked(false);
		}
		radio_unread.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_UNREAD);
				sortDialog.dismiss();
				return;
			}
		});
		
		RadioButton radio_published = new RadioButton(this);
		radio_published.setText(R.string.sortby_published);
		group.addView(radio_published);
		if (sort == SORT_PUBLISHED) {
			radio_published.setChecked(true);
		} else {
			radio_published.setChecked(false);
		}
		radio_published.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveSortBy(SORT_PUBLISHED);
				sortDialog.dismiss();
				return;
			}
		});
	}
	
	private MenuHandler mMenuHandler;
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		mMenuHandler = new MenuHandler();
		mMenuHandler.init(menu);
		mMenuHandler.addCreateBookItems(menu);

		if (collapsed == true || currentGroup.size() == 0) {
			mMenuHandler.addItem(menu, SORT_BY_AUTHOR_COLLAPSED, R.string.menu_sort_by_author_expanded, R.drawable.ic_menu_expand);
		} else {
			mMenuHandler.addItem(menu, SORT_BY_AUTHOR_EXPANDED, R.string.menu_sort_by_author_collapsed, R.drawable.ic_menu_collapse);
		}
		mMenuHandler.addItem(menu, SORT_BY, R.string.menu_sort_by, android.R.drawable.ic_menu_sort_alphabetically);

		mMenuHandler.addCreateHelpAndAdminItems(menu);
		mMenuHandler.addSearchItem(menu);

		return super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		// MenuHandler handles the 'standard' items, we just handle local items.
		if (mMenuHandler == null || !mMenuHandler.onMenuItemSelected(this, featureId, item)) {
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
			}			
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
				int currentValue = arrayIterator.next();
				ed.putInt(STATE_CURRENT_GROUP + " " + i, currentValue);
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
				if (pos >= 0) {
					adjustCurrentGroup(pos, 1, true, false);
				}
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
			synchronized(mLoadingGroups) {
				mLoadingGroups += 1; 
			}
			// DEBUG:
			//System.gc();
			//Debug.MemoryInfo before = new Debug.MemoryInfo();
			//Debug.getMemoryInfo(before);
			//long t0 = System.currentTimeMillis();

			ExpandableListView view = this.getExpandableListView();
			ArrayList<Integer> localCurrentGroup = currentGroup;
			Iterator<Integer> arrayIterator = localCurrentGroup.iterator();
			while(arrayIterator.hasNext()) {
				view.expandGroup(arrayIterator.next());
				//System.out.println("Cursor count: " + TrackedCursor.getCursorCountApproximate());
			}

			// DEBUG:
			//Debug.MemoryInfo after = new Debug.MemoryInfo();
			//t0 = System.currentTimeMillis() - t0;
			//System.gc();
			//Debug.getMemoryInfo(after);
			//
			//int delta = (after.dalvikPrivateDirty + after.nativePrivateDirty + after.otherPrivateDirty) 
			//				- (before.dalvikPrivateDirty + before.nativePrivateDirty + before.otherPrivateDirty);
			//System.out.println("Usage Change = " + delta + " (completed in " + t0 + "ms)");

			int pos = localCurrentGroup.size()-1;
			if (pos >= 0) {
				view.setSelectedGroup(localCurrentGroup.get(pos));
			}
		} catch (NoSuchFieldError e) {
			//do nothing
		} catch (Exception e) {
			Logger.logError(e);
		} finally {
			synchronized(mLoadingGroups) {
				mLoadingGroups -= 1; 
			}
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
	public void adjustCurrentGroup(int pos, int adj, boolean force, boolean save) {
		int index = currentGroup.indexOf(pos);
		if (index == -1) {
			//it does not exist (so is not open), so if adj=1, add to the list
			if (adj > 0) {
				currentGroup.add(pos);
				/* Add the latest position to the preferences */
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
				}				
			}
		}
		collapsed = (currentGroup.size() == 0);
		if (save)
			saveCurrentGroup();
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
			adjustCurrentGroup(i, 1, false, false);
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
				// Send book to goodreads
				MenuItem edit_book_send_to_gr = menu.add(0, EDIT_BOOK_SEND_TO_GR, 0, R.string.edit_book_send_to_gr);
				edit_book_send_to_gr.setIcon(R.drawable.ic_menu_cc);
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
			BookEdit.editBook(this, info.id, BookEdit.TAB_EDIT);
			return true;

		case EDIT_BOOK_NOTES:
			BookEdit.editBook(this, info.id, BookEdit.TAB_EDIT_NOTES);
			return true;

		case EDIT_BOOK_FRIENDS:
			BookEdit.editBook(this, info.id, BookEdit.TAB_EDIT_FRIENDS);
			return true;

		case EDIT_BOOK_SEND_TO_GR:
			// Get a GoodreadsManager and make sure we are authorized.
			GoodreadsManager grMgr = new GoodreadsManager();
			if (!grMgr.hasValidCredentials()) {
				try {
					grMgr.requestAuthorization(this);
				} catch (NetworkException e) {
					Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				}
			}
			// get a QueueManager and queue the task.
			QueueManager qm = BookCatalogueApp.getQueueManager();
			SendOneBookTask task = new SendOneBookTask(info.id);
			qm.enqueueTask(task, BcQueueManager.QUEUE_MAIN, 0);
			return true;

		case EDIT_SERIES_ID:
			{
				if (info.id==-1) {
					Toast.makeText(this, R.string.cannot_edit_system, Toast.LENGTH_LONG).show();
				} else {
					Series s = mDbHelper.getSeriesById(info.id);
					EditSeriesDialog d = new EditSeriesDialog(this, mDbHelper, new Runnable() {
						@Override
						public void run() {
							mDbHelper.purgeSeries();
							regenGroups();
						}});
					d.editSeries(s);
				} 
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
	private void saveSortBy(int sortType) {
		sort = sortType;
		currentGroup = new ArrayList<Integer>();
		fillData();
		/* Save the current sort settings */
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sortType);
		ed.commit();
		
	}
	
	@Override
	public boolean onChildClick(ExpandableListView l, View v, int position, int childPosition, long id) {
		boolean result = super.onChildClick(l, v, position, childPosition, id);
		adjustCurrentGroup(position, 1, true, false);
		BookEdit.openBook(this, id, null, null);
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
		case UniqueId.ACTIVITY_CREATE_BOOK_SCAN:
			try {
				String contents = intent.getStringExtra("SCAN_RESULT");
				// Handle the possibility of null/empty scanned string
				if (contents != null && !contents.equals("")) {
					Toast.makeText(this, R.string.isbn_found, Toast.LENGTH_LONG).show();
					Intent i = new Intent(this, BookISBNSearch.class);
					i.putExtra("isbn", contents);
					startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_SCAN);
				} else {
					fillData();				
				}
			} catch (NullPointerException e) {
				// This is not a scan result, but a normal return
				fillData();
			}
			break;
		case UniqueId.ACTIVITY_CREATE_BOOK_ISBN:
		case UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY:
		case UniqueId.ACTIVITY_EDIT_BOOK:
		case UniqueId.ACTIVITY_SORT:
		case UniqueId.ACTIVITY_ADMIN:
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
			// We call bookshelf not fillData in case the bookshelves have been updated.
			bookshelf();
			break;
		case UniqueId.ACTIVITY_ADMIN_FINISH:
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
			bookshelf = mPrefs.getString(BooksOnBookshelf.PREF_BOOKSHELF, bookshelf);
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
		saveCurrentGroup();
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.putString(BooksOnBookshelf.PREF_BOOKSHELF, bookshelf);
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
		if (mTaskQueue != null) {
			try {
				mTaskQueue.finish();
			} catch (Exception e) {};
			mTaskQueue = null;				
		}
		if (mUtils != null) {
			try {
				mUtils.close();
			} catch (Exception e) {};
			mUtils = null;				
		}
		super.onDestroy();
	} 
	
	//@Override
	//public boolean onKeyDown(int keyCode, KeyEvent event) {
	//	if (keyCode == KeyEvent.KEYCODE_BACK) {
	//		if (search_query.equals("")) {
	//			int opened = mPrefs.getInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
	//			SharedPreferences.Editor ed = mPrefs.edit();
	//			if (opened == 0){
	//				ed.putInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
	//				ed.commit();
	//				BookCatalogueApp.backupPopup(this);
	//				return true;
	//			} else {
	//				ed.putInt(STATE_OPENED, opened - 1);
	//				ed.commit();
	//			}
	//		}
	//	}
	//	return super.onKeyDown(keyCode, event);
	//}
	
	
	/**
	 * When the adapter is changed, we need to rebuild the FastScroller.
	 */
	public void adapterChanged() {
		// Reset the fast scroller
		FastScrollExpandableListView lv = (FastScrollExpandableListView)this.getExpandableListView();
		lv.setFastScrollEnabled(false);
		lv.setFastScrollEnabled(true);
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