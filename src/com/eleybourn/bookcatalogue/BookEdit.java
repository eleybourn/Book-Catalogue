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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.eleybourn.bookcatalogue.booklist.LibraryBuilder;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment.OnBookshelfCheckChangeListener;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.BookEditPagerAdapter;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A tab host activity which holds the three edit book tabs 1. Edit Details /
 * Book Details 2. Edit Comments 3. Loan Book
 *
 * @author Evan Leybourn
 */
public class BookEdit extends BookCatalogueActivity implements BookEditFragmentAbstract.BookEditManager,
        OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {
    public static final String TAB = "tab";
    public static final int TAB_EDIT = 0;
    public static final int TAB_EDIT_NOTES = 1;
    public static final int TAB_EDIT_FRIENDS = 2;
    public static final String ADDED_HAS_INFO = "ADDED_HAS_INFO";
    public static final String ADDED_GENRE = "ADDED_GENRE";
    public static final String ADDED_SERIES = "ADDED_SERIES";
    public static final String ADDED_TITLE = "ADDED_TITLE";
    public static final String ADDED_AUTHOR = "ADDED_AUTHOR";
    /**
     * Key using in intent to start this class in read-only mode
     */
    public static final String KEY_READ_ONLY = "key_read_only";
    private final CatalogueDBAdapter mDbHelper = new CatalogueDBAdapter(this);
    private FlattenedBooklist mList = null;
    private GestureDetector mGestureDetector;
    private boolean mIsDirtyFlg = false;
    private String added_genre = "";
    private String added_series = "";
    private String added_title = "";
    private String added_author = "";
    private long mRowId;
    private BookData mBookData;
    private boolean mIsReadOnly;
    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     */
    GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
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
    private BookEditPagerAdapter mAdapter;
    private MenuHandler mMenuHandler;
    private ArrayList<String> mPublishers;
    private ArrayList<String> mGenres;
    /**
     * List of languages in database so far
     */
    private ArrayList<String> mLanguages;
    private ArrayList<String> mFormats;

    /**
     * Open book for viewing in edit or read-only mode. The mode depends on
     * {@link BookCataloguePreferences#PREF_OPEN_BOOK_READ_ONLY} preference
     * option. If it set book opened in read-only mode otherwise in edit mode
     * (default).
     *
     * @param a        current activity from which we start
     * @param id       The id of the book to view
     * @param builder  (Optional) builder for underlying book list. Only used in
     *                 read-only view.
     * @param position (Optional) position in underlying book list. Only used in
     *                 read-only view.
     */
    public static void openBook(Activity a, long id, LibraryBuilder builder, Integer position) {
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
     * @param id  The id of the book to edit
     * @param tab Which tab to open first
     */
    public static void editBook(Activity a, long id, int tab) {
        Intent i = new Intent(a, BookEdit.class);
        i.putExtra(CatalogueDBAdapter.KEY_ROW_ID, id);
        i.putExtra(BookEdit.TAB, tab);
        a.startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
    }

    /**
     * Load the EditBook tab activity in read-only mode. The first tab is book
     * details.
     *
     * @param a         current activity from which we start
     * @param id        The id of the book to view
     * @param listTable (Optional) name of the temp table containing a list of book
     *                  IDs.
     * @param position  (Optional) position in underlying book list. Only used in
     *                  read-only view.
     */
    public static void viewBook(Activity a, long id, String listTable, Integer position) {
        Intent i = new Intent(a, BookEdit.class);
        i.putExtra("FlattenedBooklist", listTable);
        if (position != null) {
            i.putExtra("FlattenedBooklistPosition", position);
        }
        i.putExtra(CatalogueDBAdapter.KEY_ROW_ID, id);
        i.putExtra(BookEdit.TAB, BookEdit.TAB_EDIT);
        i.putExtra(BookEdit.KEY_READ_ONLY, true);
        a.startActivityForResult(i, UniqueId.ACTIVITY_VIEW_BOOK);
    }

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    public void onCreate(Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        super.onCreate(savedInstanceState);

        // Register the back press callback
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If there are unsaved changes, ask for confirmation
                if (isDirty()) {
                    StandardDialogs.showConfirmUnsavedEditsDialog(BookEdit.this, null);
                } else {
                    // No changes, disable this callback so the default 'finish' behavior happens
                    // or call our custom finish logic
                    setEnabled(false);
                    doFinish();
                }
            }
        });
        // Get the extras; we use them a lot
        Bundle extras = getIntent().getExtras();

        // We need the row ID
        {
            Long rowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROW_ID) : null;
            if (rowId == null) {
                rowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROW_ID) : null;
            }
            mRowId = Objects.requireNonNullElse(rowId, 0L);
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

        int anthology_num = 0;
        if (mBookData.getRowId() > 0) {
            anthology_num = mBookData.getInt(BookData.KEY_ANTHOLOGY);
        }

        setContentView(R.layout.book_edit_base);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        topAppBar.setTitle(R.string.label_bookshelf);
        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 mViewPager = findViewById(R.id.view_pager);
        mAdapter = new BookEditPagerAdapter(this);
        mAdapter.READ_ONLY = mIsReadOnly;
        mAdapter.BLANK_BOOK = mRowId <= 0;
        mAdapter.ANTHOLOGY_TAB = anthology_num != 0;
        mViewPager.setAdapter(mAdapter);
        mViewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> {
                    // Set the text for each tab based on its position
                    switch (position) {
                        case 0:
                            tab.setText(R.string.label_details);
                            break;
                        case 1:
                            tab.setText(R.string.label_notes);
                            break;
                        case 2:
                            tab.setText(R.string.label_loan);
                            break;
                        case 3:
                            tab.setText(R.string.label_anthology);
                            break;
                    }
                }
        ).attach(); // Crucial: call attach() to establish the link

        // Class needed for the first tab: BookEditFields except when book is
        // exist and read-only mode enabled
        if (mIsReadOnly) {
            findViewById(R.id.buttons).setVisibility(View.GONE);
            tabLayout.setVisibility(View.GONE);
        }

        Button mConfirmButton = findViewById(R.id.button_confirm);
        Button mCancelButton = findViewById(R.id.button_cancel);

        // The behaviour changes depending on if it is adding or saving
        mCancelButton.setOnClickListener(view -> {
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
        });

        mConfirmButton.setOnClickListener(view -> saveState(new DoConfirmAction()));

        if (mRowId > 0) {
            mConfirmButton.setText(R.string.button_confirm_save);
        } else {
            mConfirmButton.setText(R.string.button_confirm_add);
        }

        initBooklist(extras, savedInstanceState);

        // Must come after all book data and list retrieved.
        setActivityTitle();

        Tracker.exitOnCreate(this);

    }

    public void setAnthologyVisibility(boolean visible) {
        if (mAdapter != null && mAdapter.ANTHOLOGY_TAB != visible) {
            mAdapter.ANTHOLOGY_TAB = visible;

            // The Anthology tab is at index 3 (0=Details, 1=Notes, 2=Loan, 3=Anthology)
            int anthologyPosition = 3;

            if (visible) {
                // If making it visible, we are adding a new tab at the end
                mAdapter.notifyItemInserted(anthologyPosition);
            } else {
                // If hiding it, we are removing the tab at the end
                mAdapter.notifyItemRemoved(anthologyPosition);
            }
        }
    }

    /**
     * If we are passed a flattened book list, get it and validate it
     */
    private void initBooklist(Bundle extras, Bundle savedInstanceState) {
        if (extras != null) {
            String list = extras.getString("FlattenedBooklist");
            if (list != null && !list.isEmpty()) {
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

    /**
     * We override the dispatcher because the ScrollView will consume
     * all events otherwise.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mGestureDetector != null && mGestureDetector.onTouchEvent(event))
            return true;
        super.dispatchTouchEvent(event);
        // Always return true; we want the events.
        return true;
    }

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
    }

    @Override
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        mDbHelper.close();
        Tracker.exitOnDestroy(this);
    }


    ////////////////////////////////////////////////////////
    // Standard STATIC Methods
    ////////////////////////////////////////////////////////

    /**
     * Close the list object (frees statements) and if we are finishing, delete the temp table.
     * This is an ESSENTIAL step; for some reason, in Android 2.1 if these statements are not
     * cleaned up, then the underlying SQLiteDatabase gets double-dereferenced, resulting in
     * the database being closed by the deeply dodgy auto-close code in Android.
     */
    @Override
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);
        super.onSaveInstanceState(outState);

        outState.putLong(CatalogueDBAdapter.KEY_ROW_ID, mRowId);
        outState.putBundle("bookData", mBookData.getRawData());
        if (mList != null) {
            outState.putInt("FlattenedBooklistPosition", (int) mList.getPosition());
        }
        Tracker.exitOnSaveInstanceState(this);
    }

    /**
     * Run each time the menu button is pressed. This will setup the options menu
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mMenuHandler = new MenuHandler();
        mMenuHandler.init(menu);

        if (mRowId != 0) {
            MenuItem delete = menu.add(0, R.id.MENU_DELETE_BOOK, 0, R.string.menu_delete);
            delete.setIcon(R.drawable.ic_menu_edit);

            MenuItem duplicate = menu.add(0, R.id.MENU_DUPLICATE_BOOK, 0, R.string.menu_duplicate);
            duplicate.setIcon(R.drawable.ic_menu_add);
        }

        // TODO: Consider allowing Tweets (or other sharing methods) to work on un-added books.
        MenuItem tweet = menu.add(0, R.id.MENU_SHARE, 0, R.string.menu_share_this);
        tweet.setIcon(R.drawable.ic_menu_share);
        // Very rarely used, and easy to miss-click.
        //tweet.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        if (mIsReadOnly) {
            menu.add(0, R.id.MENU_EDIT_BOOK, 0, R.string.edit_book)
                    .setIcon(R.drawable.ic_menu_edit)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        boolean hasAuthor = !this.getBookData().getAuthorList().isEmpty();
        if (mRowId != 0) {
            MenuItem item = menu.add(0, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0, R.string.amazon_books_by_author);
            item.setIcon(R.drawable.ic_menu_search_globe);
        }

        if (!this.getBookData().getSeriesList().isEmpty()) {
            if (hasAuthor) {
                MenuItem item = menu.add(0, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0, R.string.amazon_books_by_author_in_series);
                item.setIcon(R.drawable.ic_menu_search_globe);
            }
            {
                MenuItem item = menu.add(0, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0, R.string.amazon_books_in_series);
                item.setIcon(R.drawable.ic_menu_search_globe);
            }
        }

        boolean thumbVisible = BookCatalogueApp.getAppPreferences().getBoolean(FieldVisibility.prefix + "thumbnail", true);
        if (thumbVisible) {
            MenuItem thumbOptions = menu.add(0, R.id.MENU_THUMBNAIL_OPTIONS, 0, R.string.cover_options_cc_ellipsis);
            thumbOptions.setIcon(R.drawable.ic_menu_camera);
            thumbOptions.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mMenuHandler != null && !mMenuHandler.onMenuItemSelected(this, item)) {
            try {
                int id = item.getItemId();

                if (id == R.id.MENU_THUMBNAIL_OPTIONS) {
                    if (!mIsReadOnly) {
                        showCoverContextMenu();
                        return true;
                    }
                } else if (id == R.id.MENU_SHARE) {
                    BookUtils.shareBook(this, mDbHelper, mRowId);
                    return true;
                } else if (id == R.id.MENU_DELETE_BOOK) {
                    BookUtils.deleteBook(this, mDbHelper, mRowId,
                            this::finish);
                    return true;
                } else if (id == R.id.MENU_DUPLICATE_BOOK) {
                    BookUtils.duplicateBook(this, mDbHelper, mRowId);
                    return true;
                } else if (id == R.id.MENU_EDIT_BOOK) {
                    BookEdit.editBook(this, mRowId, BookEdit.TAB_EDIT);
                    return true;
                } else if (id == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR) {
                    String author = getAuthorFromBook();
                    Utils.openAmazonSearchPage(this, author, null);
                    return true;
                } else if (id == R.id.MENU_AMAZON_BOOKS_IN_SERIES) {
                    String series = getSeriesFromBook();
                    Utils.openAmazonSearchPage(this, null, series);
                    return true;
                } else if (id == R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES) {
                    String author = getAuthorFromBook();
                    String series = getSeriesFromBook();
                    Utils.openAmazonSearchPage(this, author, series);
                    return true;
                }
            } catch (NullPointerException e) {
                Logger.logError(e);
            }
        }
        return false;
    }

    private String getAuthorFromBook() {
        ArrayList<Author> authors = this.getBookData().getAuthorList();
        if (!authors.isEmpty())
            return authors.get(0).getDisplayName();
        else
            return null;
    }

    private String getSeriesFromBook() {
        ArrayList<Series> list = this.getBookData().getSeriesList();
        if (!list.isEmpty())
            return list.get(0).name;
        else
            return null;
    }

    /**
     * Show the context menu for the cover thumbnail
     */
    public void showCoverContextMenu() {
        View v = findViewById(R.id.row_img);
        v.showContextMenu();
    }

    /**
     * Get the current status of the data in this activity
     */
    public boolean isDirty() {
        return mIsDirtyFlg;
    }

    /**
     * Mark the data as dirty (or not)
     */
    public void setDirty(boolean dirty) {
        mIsDirtyFlg = dirty;
    }

    /**
     * Check if edits need saving, and finish the activity if not
     */
    private void doFinish() {
        if (isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this, this::finishAndSendIntent);
        } else {
            finishAndSendIntent();
        }
    }

    /**
     * Actually finish this activity making sure an intent is returned.
     */
    private void finishAndSendIntent() {
        Intent i = new Intent();
        i.putExtra(CatalogueDBAdapter.KEY_ROW_ID, mBookData.getRowId());
        setResult(Activity.RESULT_OK, i);
        finish();
    }

    /**
     * Show or hide the anthology tab
     */
    @Override
    public void setShowAnthology(boolean showAnthology) {
        setAnthologyVisibility(showAnthology);
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
            ViewPager2 viewPager = findViewById(R.id.view_pager);
            Fragment frag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
            if (frag instanceof DataEditor) {
                ((DataEditor) frag).reloadData(mBookData);
            }
            setActivityTitle();
        }
    }

    /**
     * Validate the current data in all fields that have validators. Display any
     * errors.
     */
    private void validate() {
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        Fragment frag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (frag instanceof DataEditor) {
            ((DataEditor) frag).saveAllEdits(mBookData);
        }
        if (!mBookData.validate()) {
            Toast.makeText(this, mBookData.getValidationExceptionMessage(getResources()), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * This will save a book into the database, by either updating or created a
     * book. Minor modifications will be made to the strings: - Titles will be
     * rewords so 'a', 'the', 'an' will be moved to the end of the string (this
     * is only done for NEW books)
     * - Date published will be converted from a date to a string
     * <p>
     * Thumbnails will also be saved to the correct location
     * It will check if the book already exists (isbn search) if you are
     * creating a book; if so the user will be prompted to confirm.
     * <p>
     * In all cases, once the book is added/created, or not, the appropriate
     * method of the passed nextStep parameter will be executed. Passing
     * nextStep is necessary because this method may return after displaying a
     * dialogue.
     *
     * @param nextStep The next step to be executed on success/failure.
     */
    private void saveState(final PostSaveAction nextStep) {
        // Ignore validation failures; we still validate to get the current values.
        validate();

        // However, there is some data that we really do require...
        if (mBookData.getAuthorList().isEmpty()) {
            Toast.makeText(this, getResources().getText(R.string.author_required), Toast.LENGTH_LONG).show();
            return;
        }
        if (!mBookData.containsKey(CatalogueDBAdapter.KEY_TITLE)
                || mBookData.getString(CatalogueDBAdapter.KEY_TITLE).trim().isEmpty()) {
            Toast.makeText(this, getResources().getText(R.string.title_required), Toast.LENGTH_LONG).show();
            return;
        }

        if (mRowId == 0) {
            String isbn = mBookData.getString(CatalogueDBAdapter.KEY_ISBN);
            /* Check if the book currently exists */
            if (!isbn.isEmpty()) {
                if (mDbHelper.checkIsbnExists(isbn, true)) {
                    /*
                     * If it exists, show a dialog and use it to perform the
                     * next action, according to the users choice.
                     */
                    SaveAlert alert = new SaveAlert();
                    alert.setMessage(getResources().getString(R.string.duplicate_book_message));
                    alert.setTitle(R.string.duplicate_book_title);
                    alert.setIcon(R.drawable.ic_menu_info);

                    // Modern replacement for setButton2 (Negative)
                    alert.setButton(android.content.DialogInterface.BUTTON_NEGATIVE,
                            this.getResources().getString(R.string.button_ok),
                            (dialog, which) -> {
                                updateOrCreate();
                                nextStep.success();
                            });

                    // Modern replacement for setButton (Positive)
                    alert.setButton(android.content.DialogInterface.BUTTON_POSITIVE,
                            this.getResources().getString(R.string.button_cancel),
                            (dialog, which) -> nextStep.failure());

                    alert.show();
                    return;
                }
            }
        }

        // No special actions required...just do it.
        updateOrCreate();
        nextStep.success();
    }

    /**
     * Save the collected book details
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void updateOrCreate() {
        if (mRowId == 0) {
            long id = mDbHelper.createBook(mBookData, 0);

            if (id > 0) {
                setRowId(id);
                File thumb = CatalogueDBAdapter.getTempThumbnail();
                File real = CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(mRowId));
                thumb.renameTo(real);
            }
        } else {
            mDbHelper.updateBook(mRowId, mBookData, 0);
        }

        /*
         * These are global variables that will be sent via intent back to the
         * list view, if added/created
         */
        try {
            ArrayList<Author> authors = mBookData.getAuthorList();
            if (!authors.isEmpty()) {
                added_author = authors.get(0).getSortName();
            } else {
                added_author = "";
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        try {
            ArrayList<Series> series = mBookData.getSeriesList();
            if (!series.isEmpty())
                added_series = series.get(0).name;
            else
                added_series = "";
        } catch (Exception e) {
            Logger.logError(e);
        }

        added_title = mBookData.getString(CatalogueDBAdapter.KEY_TITLE);
        added_genre = mBookData.getString(CatalogueDBAdapter.KEY_GENRE);
    }

    /**
     * Sets title of the parent activity in the next format:<br>
     * <i>"title"</i>
     */
    private void setActivityTitle() {
        ActionBar bar = this.getSupportActionBar();
        assert bar != null;
        if (mIsReadOnly && mList != null) {
            bar.setTitle(mBookData.getString(CatalogueDBAdapter.KEY_TITLE));
            bar.setSubtitle(mBookData.getAuthorTextShort() + " ("
                    + String.format(getResources().getString(R.string.x_of_y), mList.getAbsolutePosition(), mList.getCount()) + ")");
        } else if (mBookData.getRowId() > 0) {
            bar.setTitle(mBookData.getString(CatalogueDBAdapter.KEY_TITLE));
            bar.setSubtitle(mBookData.getAuthorTextShort());
        } else {
            bar.setTitle(this.getResources().getString(R.string.label_insert));
            bar.setSubtitle(null);
        }
    }

    /**
     * Load a publisher list; reloading this list every time a tab changes is
     * slow. So we cache it.
     *
     * @return List of publishers
     */
    public ArrayList<String> getPublishers() {
        if (mPublishers == null) {
            mPublishers = new ArrayList<>();
            Cursor publisher_cur = mDbHelper.fetchAllPublishers();
            try (publisher_cur) {
                final int col = publisher_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER);
                while (publisher_cur.moveToNext()) {
                    mPublishers.add(publisher_cur.getString(col));
                }
            }
        }
        return mPublishers;
    }

    /**
     * Load a genre list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of publishers
     */
    public ArrayList<String> getGenres() {
        if (mGenres == null) {
            mGenres = new ArrayList<>();
            Cursor genre_cur = mDbHelper.fetchAllGenres("");
            try (genre_cur) {
                final int col = genre_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROW_ID);
                while (genre_cur.moveToNext()) {
                    mGenres.add(genre_cur.getString(col));
                }
            }
        }
        return mGenres;
    }

    /**
     * Load a language list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of languages
     */
    @Override
    public ArrayList<String> getLanguages() {
        if (mLanguages == null) {
            mLanguages = new ArrayList<>();
            Cursor cur = mDbHelper.fetchAllLanguages("");
            try (cur) {
                final int col = cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROW_ID);
                while (cur.moveToNext()) {
                    String s = cur.getString(col);
                    if (s != null && !s.isEmpty()) {
                        mLanguages.add(cur.getString(col));
                    }
                }
            }
        }
        return mLanguages;
    }

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
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        Fragment frag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
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
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        Fragment frag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
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
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        Fragment frag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
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
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        Fragment frag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
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

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        Fragment frag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (frag instanceof OnBookshelfCheckChangeListener) {
            ((OnBookshelfCheckChangeListener) frag).onBookshelfCheckChanged(dialogId, dialog, checked, shelf, textList, encodedList);
        } else {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
            Logger.logError(new RuntimeException("Received onBookshelfCheckChanged result with no fragment to handle it"));
        }
    }

    public interface PostSaveAction {
        void success();

        void failure();
    }

    private class SaveAlert extends AlertDialog {

        protected SaveAlert() {
            super(BookEdit.this);
        }
    }

    private class DoConfirmAction implements PostSaveAction {

        DoConfirmAction() {
        }

        public void success() {
            Intent i = new Intent();
            i.putExtra(CatalogueDBAdapter.KEY_ROW_ID, mBookData.getRowId());
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

}
