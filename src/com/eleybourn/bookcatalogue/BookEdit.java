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
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.view.MenuItem;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.compat.BookCatalogueFragment;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment.OnBookshelfCheckChangeListener;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
//import android.app.LocalActivityManager;

/**
 * A tab host activity which holds the three edit book tabs 1. Edit Details /
 * Book Details 2. Edit Comments 3. Loan Book
 * 
 * @author Evan Leybourn
 */
public class BookEdit extends BookCatalogueActivity implements BookEditFragmentAbstract.BookEditManager,
		OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {
	private FlattenedBooklist mList = null;
	private GestureDetector mGestureDetector;

	private boolean mIsDirtyFlg = false;

	public static final String TAB = "tab";
	public static final int TAB_EDIT = 0;
	public static final int TAB_EDIT_NOTES = 1;
	public static final int TAB_EDIT_FRIENDS = 2;
	private String added_genre = "";
	private String added_series = "";
	private String added_title = "";
	private String added_author = "";
	public static final String ADDED_HAS_INFO = "ADDED_HAS_INFO";
	public static final String ADDED_GENRE = "ADDED_GENRE";
	public static final String ADDED_SERIES = "ADDED_SERIES";
	public static final String ADDED_TITLE = "ADDED_TITLE";
	public static final String ADDED_AUTHOR = "ADDED_AUTHOR";

	/**
	 * Standardize the names of tabs ANY NEW NAMES NEED TO BE ADDED TO
	 * mTabNames, below
	 */
	private static String TAB_NAME_EDIT_BOOK = "edit_book";
	private static String TAB_NAME_EDIT_NOTES = "edit_book_notes";
	private static String TAB_NAME_EDIT_FRIENDS = "edit_book_friends";
	private static String TAB_NAME_EDIT_ANTHOLOGY = "edit_book_anthology";

	/** Key using in intent to start this class in read-only mode */
	public static final String KEY_READ_ONLY = "key_read_only";

	//public int mCurrentTab = 0;
	private long mRowId;
	private CatalogueDBAdapter mDbHelper = new CatalogueDBAdapter(this);
	private Tab mAnthologyTab = null;
	private BookData mBookData;
	private boolean mIsReadOnly;

	private Button mConfirmButton;
	private Button mCancelButton;

	public void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.book_edit_base);

		final ActionBar actionBar = getSupportActionBar();

		// Get the extras; we use them a lot
		Bundle extras = getIntent().getExtras();

		// We need the row ID
		{
			Long rowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (rowId == null) {
				rowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			}
			if (rowId == null) {
				mRowId = 0;
			} else {
				mRowId = rowId;
			}
		}

		// Various functions depend on read-only state
		if (extras != null && extras.containsKey(KEY_READ_ONLY) && extras.getBoolean(KEY_READ_ONLY)) {
			mIsReadOnly = (mRowId != 0);
		} else {
			mIsReadOnly = false;
		}

		mDbHelper.open();

		// Get the book data from the bundle or the database
		loadBookData(mRowId, savedInstanceState == null ? extras : savedInstanceState);

		// get the passed parameters
		int tabIndex;
		if (savedInstanceState != null && savedInstanceState.containsKey(BookEdit.TAB)) {
			tabIndex = savedInstanceState.getInt(BookEdit.TAB);
		} else if (extras != null && extras.containsKey(BookEdit.TAB)) {
			tabIndex = extras.getInt(BookEdit.TAB);
		} else {
			tabIndex = 0;
		}

		int anthology_num = 0;
		if (mBookData.getRowId() > 0) {
			anthology_num = mBookData.getInt(BookData.KEY_ANTHOLOGY);
		}

		// Class needed for the first tab: BookEditFields except when book is
		// exist and read-only mode enabled
		if (mIsReadOnly) {
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

			BookDetailsReadOnly details = new BookDetailsReadOnly();
			details.setArguments(getIntent().getExtras());
			getSupportFragmentManager().beginTransaction().replace(R.id.fragment, details).commit();
		} else {
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			initTab(actionBar, new TabListener<BookEditFields>(this, TAB_NAME_EDIT_BOOK, BookEditFields.class), R.string.details,
					R.drawable.ic_tab_edit, extras);
			initTab(actionBar, new TabListener<BookEditNotes>(this, TAB_NAME_EDIT_NOTES, BookEditNotes.class), R.string.notes,
					R.drawable.ic_tab_notes, extras);
			// Only show the other tabs if it is not new book, otherwise only
			// show the first tab
			if (mRowId > 0) {
				initTab(actionBar, new TabListener<BookEditLoaned>(this, TAB_NAME_EDIT_FRIENDS, BookEditLoaned.class),
						R.string.loan, R.drawable.ic_tab_friends, extras);

				// Only show the anthology tab if the book is marked as an
				// anthology
				if (anthology_num != 0) {
					mAnthologyTab = initTab(actionBar, new TabListener<BookEditAnthology>(this, TAB_NAME_EDIT_ANTHOLOGY,
							BookEditAnthology.class), R.string.edit_book_anthology, R.drawable.ic_tab_anthology, extras);
				}
			}
			actionBar.setSelectedNavigationItem(tabIndex);
		}

		if (mIsReadOnly) {
			findViewById(R.id.buttons).setVisibility(View.GONE);
		} else {
			findViewById(R.id.buttons).setVisibility(View.VISIBLE);
		}

		mConfirmButton = (Button) findViewById(R.id.confirm);
		mCancelButton = (Button) findViewById(R.id.cancel);

		// mConfirmButton.setOnClickListener - This is set in populate fields.
		// The behaviour changes depending on if it is adding or saving
		mCancelButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				// Cleanup because we may have made global changes
				mDbHelper.purgeAuthors();
				mDbHelper.purgeSeries();
				// We're done.
				setResult(Activity.RESULT_OK);

				if (isDirty()) {
					StandardDialogs.showConfirmUnsavedEditsDialog(BookEdit.this, null);
				} else {
					finish();
				}
			}
		});

		mConfirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				saveState(new DoConfirmAction());
			}
		});

		if (mRowId > 0) {
			mConfirmButton.setText(R.string.confirm_save);
		} else {
			mConfirmButton.setText(R.string.confirm_add);
		}

		initBooklist(extras, savedInstanceState);

		// Must come after all book data and list retrieved.
		setActivityTitle();

		// Setup the background
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);

		Tracker.exitOnCreate(this);

	}

	/**
	 * If we are passed a flatened book list, get it and validate it
	 * 
	 * @param extras
	 * @param savedInstanceState
	 */
	private void initBooklist(Bundle extras, Bundle savedInstanceState) {
		if (extras != null) {
			String list = extras.getString("FlattenedBooklist");
			if (list != null && !list.equals("")) {
				mList = new FlattenedBooklist(mDbHelper.getDb(), list);
				// Check to see it really exists. The underlying table
				// disappeared once in testing
				// which is hard to explain; it theoretically should only happen
				// if the app closes
				// the database or if the activity pauses with 'isFinishing()'
				// returning true.
				if (mList.exists()) {
					int pos;
					if (savedInstanceState != null && savedInstanceState.containsKey("FlattenedBooklistPosition")) {
						pos = savedInstanceState.getInt("FlattenedBooklistPosition");
					} else if (extras.containsKey("FlattenedBooklistPosition")) {
						pos = extras.getInt("FlattenedBooklistPosition");
					} else {
						pos = 0;
					}
					mList.moveTo(pos);
					while (!mList.getBookId().equals(mRowId)) {
						if (!mList.moveNext())
							break;
					}
					if (!mList.getBookId().equals(mRowId)) {
						mList.close();
						mList = null;
					} else {
						// Add a gesture lister for 'swipe' gestures
						mGestureDetector = new GestureDetector(this, mGestureListener);
					}

				} else {
					mList.close();
					mList = null;
				}
			}
		}
	}

	@Override
	/**
	 * We override the dispatcher because the ScrollView will consume
	 * all events otherwise.
	 */
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (mGestureDetector != null && mGestureDetector.onTouchEvent(event))
			return true;
		super.dispatchTouchEvent(event);
		// Always return true; we want the events.
		return true;
	}

	/**
	 * Listener to handle 'fling' events; we could handle others but need to be
	 * careful about possible clicks and scrolling.
	 */
	GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			if (mList == null)
				return false;

			// Make sure we have considerably more X-velocity than Y-velocity;
			// otherwise it might be a scroll.
			if (Math.abs(velocityX / velocityY) > 2) {
				boolean moved;
				// Work out which way to move, and do it.
				if (velocityX > 0) {
					moved = mList.movePrev();
				} else {
					moved = mList.moveNext();
				}
				if (moved) {
					setRowId(mList.getBookId());
				}
				return true;
			} else {
				return false;
			}
		}
	};

	/**
	 * This function will populate the forms elements in three different ways 1.
	 * If a valid rowId exists it will populate the fields from the database 2.
	 * If fields have been passed from another activity (e.g. ISBNSearch) it
	 * will populate the fields from the bundle 3. It will leave the fields
	 * blank for new books.
	 */
	private void loadBookData(Long rowId, Bundle bestBundle) {
		if (bestBundle != null && bestBundle.containsKey("bookData")) {
			// If we have saved book data, use it
			mBookData = new BookData(rowId, bestBundle.getBundle("bookData"));
		} else {
			// Just load based on rowId
			mBookData = new BookData(rowId);
		}
	}

	/**
	 * This is a straight passthrough
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		// 1. the call to duplicateBook() no longer uses this ID
		// 2. We can't just finish(); there might be unsaved edits.
		// 3. if we want to finish on creating a new book, we should do it when
		// we start the activity
		// switch (requestCode) {
		// setResult(resultCode, intent);
		// finish();
		// break;
		// }
	}

	@Override
	protected void onDestroy() {
		Tracker.enterOnDestroy(this);
		super.onDestroy();
		mDbHelper.close();
		Tracker.exitOnDestroy(this);
	}

	@Override
	/**
	 * Close the list object (frees statements) and if we are finishing, delete the temp table.
	 * 
	 * This is an ESSENTIAL step; for some reason, in Android 2.1 if these statements are not
	 * cleaned up, then the underlying SQLiteDatabase gets double-dereferenced, resulting in
	 * the database being closed by the deeply dodgy auto-close code in Android.
	 */
	public void onPause() {
		if (mList != null) {
			mList.close();
			if (this.isFinishing()) {
				mList.deleteData();
			}
		}
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Tracker.enterOnSaveInstanceState(this);
		super.onSaveInstanceState(outState);
	
		ActionBar actionBar = this.getSupportActionBar();

		outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		outState.putBundle("bookData", mBookData.getRawData());
		if (mList != null) {
			outState.putInt("FlattenedBooklistPosition", (int) mList.getPosition());
		}
		outState.putInt(BookEdit.TAB, actionBar.getSelectedNavigationIndex());
		Tracker.exitOnSaveInstanceState(this);
	}

	/********************************************************
	 * Standard STATIC Methods
	 * ******************************************************
	 */

	/**
	 * Open book for viewing in edit or read-only mode. The mode depends on
	 * {@link BookCataloguePreferences#PREF_OPEN_BOOK_READ_ONLY} preference
	 * option. If it set book opened in read-only mode otherwise in edit mode
	 * (default).
	 * 
	 * @param a
	 *            current activity from which we start
	 * @param id
	 *            The id of the book to view
	 * @param builder
	 *            (Optional) builder for underlying book list. Only used in
	 *            read-only view.
	 * @param position
	 *            (Optional) position in underlying book list. Only used in
	 *            read-only view.
	 */
	public static void openBook(Activity a, long id, BooklistBuilder builder, Integer position) {
		boolean isReadOnly = BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_OPEN_BOOK_READ_ONLY, true);
		if (isReadOnly) {
			// Make a flattened copy of the list of books, if available
			String listTable = null;
			if (builder != null) {
				listTable = builder.createFlattenedBooklist().getTable().getName();
			}
			BookEdit.viewBook(a, id, listTable, position);
		} else {
			BookEdit.editBook(a, id, BookEdit.TAB_EDIT);
		}
	}

	/**
	 * Load the EditBook activity based on the provided id in edit mode. Also
	 * open to the provided tab.
	 * 
	 * @param id
	 *            The id of the book to edit
	 * @param tab
	 *            Which tab to open first
	 */
	public static void editBook(Activity a, long id, int tab) {
		Intent i = new Intent(a, BookEdit.class);
		i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
		i.putExtra(BookEdit.TAB, tab);
		a.startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
		return;
	}

	/**
	 * Load the EditBook tab activity in read-only mode. The first tab is book
	 * details.
	 * 
	 * @param a
	 *            current activity from which we start
	 * @param id
	 *            The id of the book to view
	 * @param listTable
	 *            (Optional) name of the temp table comtaining a list of book
	 *            IDs.
	 * @param position
	 *            (Optional) position in underlying book list. Only used in
	 *            read-only view.
	 */
	public static void viewBook(Activity a, long id, String listTable, Integer position) {
		Intent i = new Intent(a, BookEdit.class);
		i.putExtra("FlattenedBooklist", listTable);
		if (position != null) {
			i.putExtra("FlattenedBooklistPosition", position);
		}
		i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
		i.putExtra(BookEdit.TAB, BookEdit.TAB_EDIT); // needed extra for
														// creating BookEdit
		i.putExtra(BookEdit.KEY_READ_ONLY, true);
		a.startActivityForResult(i, UniqueId.ACTIVITY_VIEW_BOOK);
		return;
	}

	/**
	 * Initialize a TabSpec according to defined parameters and add it to the
	 * TabHost.
	 * 
	 * @param tabHost
	 *            parent TabHost
	 * @param intentClass
	 *            class for specifying intent. It`s the Activity class contained
	 *            in this tab.
	 * @param tabTag
	 *            required tag of tab
	 * @param titleResId
	 *            resource id of a title of the tab
	 * @param iconResId
	 *            resource id of an icon (drawable) of the tab
	 * @param extras
	 *            extras for putting in the intent. If extras is null they will
	 *            not be added.
	 */
	private <T extends BookCatalogueFragment> Tab initTab(ActionBar actionBar, TabListener<T> listener, int titleResId, int iconResId,
			Bundle extras) {

		Resources resources = getResources();
		String tabTitle = resources.getString(titleResId);

		Tab tab = actionBar.newTab().setText(tabTitle).setTabListener(listener).setTag(titleResId);
		// tab.setIcon(iconResId);

		actionBar.addTab(tab);
		return tab;
	}

	/**
	 * Mark the data as dirty (or not)
	 */
	public void setDirty(boolean dirty) {
		mIsDirtyFlg = dirty;
	}

	/**
	 * Get the current status of the data in this activity
	 */
	public boolean isDirty() {
		return mIsDirtyFlg;
	}

	/**
	 * If 'back' is pressed, and the user has made changes, ask them if they
	 * really want to lose the changes.
	 * 
	 * We don't use onBackPressed because it does not work with API level 4.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (isDirty()) {
				StandardDialogs.showConfirmUnsavedEditsDialog(this, null);
			} else {
				doFinish();
			}
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	/**
	 * Check if edits need saving, and finish the activity if not
	 */
	private void doFinish() {
		if (isDirty()) {
			StandardDialogs.showConfirmUnsavedEditsDialog(this, new Runnable() {
				@Override
				public void run() {
					finishAndSendIntent();
				}});
		} else {
			finishAndSendIntent();
		}
	}

	/**
	 * Actually finish this activity making sure an intent is returned.
	 */
	private void finishAndSendIntent() {
		Intent i = new Intent();
		i.putExtra(CatalogueDBAdapter.KEY_ROWID, mBookData.getRowId());
		setResult(Activity.RESULT_OK, i);
		finish();		
	}

	/**
	 * Show or hide the anthology tab
	 */
	@Override
	public void setShowAnthology(boolean showAnthology) {
		ActionBar actionBar = this.getSupportActionBar();
		if (showAnthology) {
			if (mAnthologyTab == null) {
				mAnthologyTab = initTab(actionBar, new TabListener<BookEditAnthology>(this, TAB_NAME_EDIT_ANTHOLOGY,
						BookEditAnthology.class), R.string.edit_book_anthology, R.drawable.ic_tab_anthology, getIntent()
						.getExtras());
			}
		} else {
			if (mAnthologyTab != null) {
				actionBar.removeTab(mAnthologyTab);
				mAnthologyTab = null;
			}
		}

	}

	/**
	 * Listener to create/show the relevant tabs
	 * 
	 * @author pjw
	 *
	 * @param <T>		Fragment type
	 */
	public static class TabListener<T extends BookCatalogueFragment> implements ActionBar.TabListener {
		private Fragment mFragment;
		private final Activity mActivity;
		private final String mTag;
		private final Class<T> mClass;

		/**
		 * Constructor used each time a new tab is created.
		 * 
		 * @param activity
		 *            The host Activity, used to instantiate the fragment
		 * @param tag
		 *            The identifier tag for the fragment
		 * @param clz
		 *            The fragment's Class, used to instantiate the fragment
		 */
		public TabListener(Activity activity, String tag, Class<T> clz) {
			mActivity = activity;
			mTag = tag;
			mClass = clz;
		}

		/* The following are each of the ActionBar.TabListener callbacks */

		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			// Check if the fragment is already initialized
			if (mFragment == null) {
				// If not, instantiate and add it to the activity
				mFragment = T.instantiate(mActivity, mClass.getName());
				// ft.add(android.R.id.content, mFragment, mTag);
				ft.replace(R.id.fragment, mFragment, mTag);
			} else {
				// If it exists, simply attach it in order to show it
				ft.attach(mFragment);
			}
		}

		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			if (mFragment != null) {
				// Detach the fragment, because another one is being attached
				ft.detach(mFragment);
			}
		}

		public void onTabReselected(Tab tab, FragmentTransaction ft) {
			// User selected the already selected tab. Usually do nothing.
		}
	}

	@Override
	public BookData getBookData() {
		return mBookData;
	}

	@Override
	public void setRowId(Long id) {
		if (mRowId != id) {
			mRowId = id;
			loadBookData(id, null);
			Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
			if (frag instanceof DataEditor) {
				((DataEditor) frag).reloadData(mBookData);
			}
			setActivityTitle();
		}
	}

	/**
	 * Validate the current data in all fields that have validators. Display any
	 * errors.
	 * 
	 * @param values
	 *            The values to use
	 * 
	 * @return Boolean success or failure.
	 */
	private boolean validate() {
		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
		if (frag instanceof DataEditor) {
			((DataEditor) frag).saveAllEdits(mBookData);
		}
		if (!mBookData.validate()) {
			Toast.makeText(this, mBookData.getValidationExceptionMessage(getResources()), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	/**
	 * This will save a book into the database, by either updating or created a
	 * book. Minor modifications will be made to the strings: - Titles will be
	 * rewords so 'a', 'the', 'an' will be moved to the end of the string (this
	 * is only done for NEW books)
	 * 
	 * - Date published will be converted from a date to a string
	 * 
	 * Thumbnails will also be saved to the correct location
	 * 
	 * It will check if the book already exists (isbn search) if you are
	 * creating a book; if so the user will be prompted to confirm.
	 * 
	 * In all cases, once the book is added/created, or not, the appropriate
	 * method of the passed nextStep parameter will be executed. Passing
	 * nextStep is necessary because this method may return after displaying a
	 * dialogue.
	 * 
	 * @param nextStep
	 *            The next step to be executed on success/failure.
	 * 
	 * @throws IOException
	 */
	private void saveState(final PostSaveAction nextStep) {
		// Ignore validation failures; we still validate to get the current
		// values.
		if (!validate()) {
			// nextStep.failure();
			// return;
		}

		// However, there is some data that we really do require...
		if (mBookData.getAuthorList().size() == 0) {
			Toast.makeText(this, getResources().getText(R.string.author_required), Toast.LENGTH_LONG).show();
			return;
		}
		if (!mBookData.containsKey(CatalogueDBAdapter.KEY_TITLE)
				|| mBookData.getString(CatalogueDBAdapter.KEY_TITLE).trim().length() == 0) {
			Toast.makeText(this, getResources().getText(R.string.title_required), Toast.LENGTH_LONG).show();
			return;
		}

		if (mRowId == 0) {
			String isbn = mBookData.getString(CatalogueDBAdapter.KEY_ISBN);
			/* Check if the book currently exists */
			if (!isbn.equals("")) {
				if (mDbHelper.checkIsbnExists(isbn)) {
					/*
					 * If it exists, show a dialog and use it to perform the
					 * next action, according to the users choice.
					 */
					SaveAlert alert = new SaveAlert();
					alert.setMessage(getResources().getString(R.string.duplicate_book_message));
					alert.setTitle(R.string.duplicate_book_title);
					alert.setIcon(android.R.drawable.ic_menu_info_details);
					alert.setButton2(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							updateOrCreate();
							nextStep.success();
							return;
						}
					});
					alert.setButton(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							nextStep.failure();
							return;
						}
					});
					alert.show();
					return;
				}
			}
		}

		// No special actions required...just do it.
		updateOrCreate();
		nextStep.success();
		return;
	}

	private class SaveAlert extends AlertDialog {

		protected SaveAlert() {
			super(BookEdit.this);
		}
	}

	/**
	 * Save the collected book details
	 */
	private void updateOrCreate() {
		if (mRowId == 0) {
			long id = mDbHelper.createBook(mBookData);

			if (id > 0) {
				setRowId(id);
				File thumb = CatalogueDBAdapter.getTempThumbnail();
				File real = CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(mRowId));
				thumb.renameTo(real);
			}
		} else {
			mDbHelper.updateBook(mRowId, mBookData, true);
		}

		/*
		 * These are global variables that will be sent via intent back to the
		 * list view, if added/created
		 */
		try {
			ArrayList<Author> authors = mBookData.getAuthorList();
			if (authors.size() > 0) {
				added_author = authors.get(0).getSortName();
			} else {
				added_author = "";
			}
		} catch (Exception e) {
			Logger.logError(e);
		}
		;
		try {
			ArrayList<Series> series = mBookData.getSeriesList();
			if (series.size() > 0)
				added_series = series.get(0).name;
			else
				added_series = "";
		} catch (Exception e) {
			Logger.logError(e);
		}
		;

		added_title = mBookData.getString(CatalogueDBAdapter.KEY_TITLE);
		added_genre = mBookData.getString(CatalogueDBAdapter.KEY_GENRE);
	}

	public interface PostSaveAction {
		public void success();

		public void failure();
	}

	private class DoConfirmAction implements PostSaveAction {

		DoConfirmAction() {
		}

		public void success() {
			Intent i = new Intent();
			i.putExtra(CatalogueDBAdapter.KEY_ROWID, mBookData.getRowId());
			i.putExtra(ADDED_HAS_INFO, true);
			i.putExtra(ADDED_GENRE, added_genre);
			i.putExtra(ADDED_SERIES, added_series);
			i.putExtra(ADDED_TITLE, added_title);
			i.putExtra(ADDED_AUTHOR, added_author);

			setResult(Activity.RESULT_OK, i);
			finish();
		}

		public void failure() {
			// Do nothing
		}
	}

	/**
	 * Sets title of the parent activity in the next format:<br>
	 * <i>"title"</i>
	 * 
	 * @param title
	 */
	private void setActivityTitle() {
		ActionBar bar = this.getSupportActionBar();
		if (mIsReadOnly && mList != null) {
			bar.setTitle(mBookData.getString(CatalogueDBAdapter.KEY_TITLE));
			bar.setSubtitle(mBookData.getAuthorTextShort() + " ("
					+ String.format(getResources().getString(R.string.x_of_y), mList.getAbsolutePosition(), mList.getCount()) + ")");
		} else if (mBookData.getRowId() > 0) {
			bar.setTitle(mBookData.getString(CatalogueDBAdapter.KEY_TITLE));
			bar.setSubtitle(mBookData.getAuthorTextShort());
		} else {
			bar.setTitle(this.getResources().getString(R.string.menu_insert));
			bar.setSubtitle(null);
		}
	}

	private ArrayList<String> mPublishers;

	/**
	 * Load a publisher list; reloading this list every time a tab changes is
	 * slow. So we cache it.
	 * 
	 * @return List of publishers
	 */
	public ArrayList<String> getPublishers() {
		if (mPublishers == null) {
			mPublishers = new ArrayList<String>();
			Cursor publisher_cur = mDbHelper.fetchAllPublishers();
			final int col = publisher_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER);
			try {
				while (publisher_cur.moveToNext()) {
					mPublishers.add(publisher_cur.getString(col));
				}
			} finally {
				publisher_cur.close();
			}
		}
		return mPublishers;
	}

	private ArrayList<String> mGenres;

	/**
	 * Load a genre list; reloading this list every time a tab changes is slow.
	 * So we cache it.
	 * 
	 * @return List of publishers
	 */
	public ArrayList<String> getGenres() {
		if (mGenres == null) {
			mGenres = new ArrayList<String>();
			Cursor genre_cur = mDbHelper.fetchAllGenres("");
			final int col = genre_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID);
			try {
				while (genre_cur.moveToNext()) {
					mGenres.add(genre_cur.getString(col));
				}
			} finally {
				genre_cur.close();
			}
		}
		return mGenres;
	}

	/** List of languages in database so far */
	private ArrayList<String> mLanguages;
	/**
	 * Load a language list; reloading this list every time a tab changes is slow.
	 * So we cache it.
	 * 
	 * @return List of languages
	 */
	@Override
	public ArrayList<String> getLanguages() {
		if (mLanguages == null) {
			mLanguages = new ArrayList<String>();
			Cursor cur = mDbHelper.fetchAllLanguages("");
			final int col = cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID);
			try {
				while (cur.moveToNext()) {
					String s = cur.getString(col);
					if (s != null && !s.equals("")) {
						mLanguages.add(cur.getString(col));						
					}
				}
			} finally {
				cur.close();
			}
		}
		return mLanguages;
	}

	private ArrayList<String> mFormats;

	/**
	 * Load a format list; reloading this list every time a tab changes is slow.
	 * So we cache it.
	 * 
	 * @return List of publishers
	 */
	public ArrayList<String> getFormats() {
		if (mFormats == null) {
			mFormats = mDbHelper.getFormats();
		}
		return mFormats;
	}

	/**
	 * Dialog handler; pass results to relevant destination
	 */
	@Override
	public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
		if (frag instanceof OnPartialDatePickerListener) {
			((OnPartialDatePickerListener) frag).onPartialDatePickerSet(dialogId, dialog, year, month, day);
		} else {
			Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
			Logger.logError(new RuntimeException("Received date dialog result with no fragment to handle it"));
		}
		// Make sure it's dismissed
		if (dialog.isVisible())
			dialog.dismiss();
	}

	/**
	 * Dialog handler; pass results to relevant destination
	 */
	@Override
	public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
		if (frag instanceof OnPartialDatePickerListener) {
			((OnPartialDatePickerListener) frag).onPartialDatePickerCancel(dialogId, dialog);
		} else {
			Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
			Logger.logError(new RuntimeException("Received date dialog cancellation with no fragment to handle it"));
		}
		// Make sure it's dismissed
		if (dialog.isVisible())
			dialog.dismiss();

	}

	/**
	 * Dialog handler; pass results to relevant destination
	 */
	@Override
	public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText) {
		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
		if (frag instanceof OnTextFieldEditorListener) {
			((OnTextFieldEditorListener) frag).onTextFieldEditorSave(dialogId, dialog, newText);
		} else {
			Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
			Logger.logError(new RuntimeException("Received onTextFieldEditorSave result with no fragment to handle it"));
		}
		// Make sure it's dismissed
		if (dialog.isVisible())
			dialog.dismiss();
	}

	/**
	 * Dialog handler; pass results to relevant destination
	 */
	@Override
	public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog) {
		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
		if (frag instanceof OnTextFieldEditorListener) {
			((OnTextFieldEditorListener) frag).onTextFieldEditorCancel(dialogId, dialog);
		} else {
			Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
			Logger.logError(new RuntimeException("Received onTextFieldEditorCancel result with no fragment to handle it"));
		}
		// Make sure it's dismissed
		if (dialog.isVisible())
			dialog.dismiss();
	}
	
	/**
	 * Dialog handler; pass results to relevant destination
	 */
	@Override
	public void onBookshelfCheckChanged(int dialogId, BookshelfDialogFragment dialog, boolean checked, String shelf,
			String textList, String encodedList) {

		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
		if (frag instanceof OnBookshelfCheckChangeListener) {
			((OnBookshelfCheckChangeListener) frag).onBookshelfCheckChanged(dialogId, dialog, checked, shelf, textList, encodedList);
		} else {
			Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
			Logger.logError(new RuntimeException("Received onBookshelfCheckChanged result with no fragment to handle it"));
		}
	}

	/**
	 * menu handler; handle the 'home' key, otherwise, pass on the event
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

        case android.R.id.home:
        	doFinish();
			return true;

        default:
            return super.onOptionsItemSelected(item);
		}
		
	}

}
