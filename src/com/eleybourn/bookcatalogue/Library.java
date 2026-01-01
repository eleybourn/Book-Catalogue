/*
 * @copyright 2012 Philip Warner
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

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOKS;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.LibraryMultitypeHandler.BooklistChangeListener;
import com.eleybourn.bookcatalogue.booklist.LibraryBuilder;
import com.eleybourn.bookcatalogue.booklist.LibraryBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.LibraryGroup.RowKinds;
import com.eleybourn.bookcatalogue.booklist.AdminLibraryPreferences;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.LibraryStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStylePropertiesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogMenuItem;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.TrackedCursor;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class Library extends BookCatalogueActivity implements BooklistChangeListener {
    /**
     * Prefix used in preferences for this activity
     */
    private final static String TAG = "BooksOnBookshelf";
    /**
     * Preference name
     */
    public final static String PREF_BOOKSHELF = TAG + ".BOOKSHELF";
    /**
     * Preference name
     */
    private final static String PREF_TOP_ROW = TAG + ".TOP_ROW";
    /**
     * Preference name
     */
    private final static String PREF_TOP_ROW_TOP = TAG + ".TOP_ROW_TOP";
    /**
     * Preference name
     */
    private final static String PREF_LIST_STYLE = TAG + ".LIST_STYLE";
    private static final int MENU_SORT = MenuHandler.FIRST + 1;
    private static final int MENU_EXPAND = MenuHandler.FIRST + 2;
    private static final int MENU_COLLAPSE = MenuHandler.FIRST + 3;
    private static final int MENU_EDIT_STYLE = MenuHandler.FIRST + 4;
    /**
     * Counter for debug purposes
     */
    private static final Object sInstanceLock = new Object();
    private static Integer mInstanceCount = 0;
    /**
     * Task queue to get book lists in background
     */
    private final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("BoB-List", 1);
    /**
     * Currently selected list style
     */
    LibraryStyle mCurrentStyle = null;
    /**
     * Currently selected bookshelf
     */
    private String mCurrentBookshelf = ""; //getString(R.string.all_books);
    /**
     * Flag indicating activity has been destroyed. Used for background tasks
     */
    private boolean mIsDead = false;
    /**
     * Flag to indicate that a list has been successfully loaded -- affects the way we save state
     */
    private boolean mListHasBeenLoaded = false;
    /**
     * Used by onScroll to detect when the top row has actually changed.
     */
    private int mLastTop = -1;
    /**
     * ProgressDialog used to display "Getting books...". Needed here so we can dismiss it on close.
     */
    private android.widget.ProgressBar mProgressBar;
    /**
     * A book ID used for keeping/updating current list position, eg. when a book is edited.
     */
    private long mMarkBookId = 0;
    /**
     * Text to use in search query
     */
    private String mSearchText = "";
    /**
     * Saved position of last top row
     */
    private int mTopRow = 0;
    /**
     * Saved position of last top row offset from view top
     */
    private int mTopRowTop = 0;
    /**
     * Database connection
     */
    private CatalogueDBAdapter mDb;
    /**
     * Handler to manage all Views on the list
     */
    private LibraryMultitypeHandler mListHandler;
    /**
     * Current displayed list cursor
     */
    private BooklistPseudoCursor mList;
    /**
     * Multi-type adapter to manage list connection to cursor
     */
    private MultitypeListAdapter mAdapter;
    /**
     * Preferred booklist state in next rebuild
     */
    private int mRebuildState;
    /**
     * Total number of books in current list
     */
    private int mTotalBooks = 0;
    /**
     * Total number of unique books in current list
     */
    private int mUniqueBooks = 0;
    /**
     * Setup the bookshelf spinner. This function will also call fillData when
     * complete having loaded the appropriate bookshelf.
     */
    private Spinner mBookshelfSpinner;
    private ArrayAdapter<String> mBookshelfAdapter;
    private MenuHandler mMenuHandler;
    // Define the ActivityResultLauncher
    private final androidx.activity.result.ActivityResultLauncher<Intent> mEditStyleLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // This replaces the logic in onActivityResult for ACTIVITY_BOOKLIST_STYLE_PROPERTIES
                        if (result.getResultCode() == RESULT_OK) {
                            Intent intent = result.getData();
                            if (intent != null && intent.hasExtra(BooklistStylePropertiesActivity.KEY_STYLE)) {
                                LibraryStyle style = (LibraryStyle) intent.getSerializableExtra(BooklistStylePropertiesActivity.KEY_STYLE);
                                if (style != null)
                                    mCurrentStyle = style;
                            }
                        }
                        // These calls were common to the case in the switch statement
                        this.savePosition();
                        this.setupList();
                    }
            );

    /**
     * TODO DEBUG ONLY. Count instances
     */
    public Library() {
        super();
        synchronized (sInstanceLock) {
            mInstanceCount--;
            System.out.println("BoB instances: " + mInstanceCount);
        }
    }

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.library);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            setSupportActionBar(topAppBar);
            topAppBar.setTitle(R.string.title_library);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            mProgressBar = findViewById(R.id.loading_progress);

            if (savedInstanceState == null)
                // Get preferred booklist state to use from preferences; default to always expanded (MUCH faster than 'preserve' with lots of books)
                mRebuildState = AdminLibraryPreferences.getRebuildState();
            else
                // Always preserve state when rebuilding/recreating etc
                mRebuildState = AdminLibraryPreferences.LIBRARY_STATE_PRESERVED;

            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            // Extract the sort type from the bundle. getInt will return 0 if there is no attribute
            // sort (which is exactly what we want)
            try {
                BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
                // Restore bookshelf and position
                mCurrentBookshelf = prefs.getString(PREF_BOOKSHELF, mCurrentBookshelf);
                mTopRow = prefs.getInt(PREF_TOP_ROW, 0);
                mTopRowTop = prefs.getInt(PREF_TOP_ROW_TOP, 0);
            } catch (Exception e) {
                Logger.logError(e);
            }

            // Restore view style
            refreshStyle();

            // This sets the search capability to local (application) search
            setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

            // This sets the search capability to local (application) search
            setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

            Intent intent = getIntent();
            if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                // Return the search results instead of all books (for the bookshelf)
                mSearchText = Objects.requireNonNull(intent.getStringExtra(SearchManager.QUERY)).trim();
            } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
                mSearchText = intent.getDataString();
            }
            if (mSearchText == null || mSearchText.equals(".")) {
                mSearchText = "";
            }

            TextView searchTextView = findViewById(R.id.search_result_summary);
            if (mSearchText.isEmpty()) {
                searchTextView.setVisibility(View.GONE);
            } else {
                searchTextView.setVisibility(View.VISIBLE);
                searchTextView.setText(getString(R.string.label_search) + ": " + mSearchText);
            }

            // We want context menus to be available
            getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                mList.moveToPosition(position);
                ArrayList<SimpleDialogItem> menu = new ArrayList<>();
                mListHandler.buildContextMenu(mDb, mList.getRowView(), menu);
                if (!menu.isEmpty()) {
                    StandardDialogs.selectItemDialog(getLayoutInflater(), null, menu, null, item -> {
                        mList.moveToPosition(position);
                        int id1 = ((SimpleDialogMenuItem) item).getItemId();
                        mListHandler.onContextItemSelected(mDb, mList.getRowView(), Library.this, mDb, id1);

                        // If data changed, we need to update display
                        if (id1 == R.id.MENU_MARK_AS_UNREAD || id1 == R.id.MENU_MARK_AS_READ) {
                            setupList();
                        }
                    });
                }
                return true;
            });

            // use the custom fast scroller (the ListView in the XML is our custom version).
            getListView().setFastScrollEnabled(true);

            // Handle item click events
            getListView().setOnItemClickListener(this::handleItemClick);

            // Debug; makes list structures vary across calls to ensure code is correct...
            mMarkBookId = -1;

            // This will cause the list to be generated.
            initBookshelfSpinner();
            setupList();

            if (savedInstanceState == null) {
                HintManager.displayHint(this, R.string.hint_view_only_book_details, null, null);
                HintManager.displayHint(this, R.string.hint_library, null, null);
                // This hint is only displayed for users with missing covers who might have upgraded from an earlier version and missed the update message
                // that explains how to update covers...
                HintManager.displayHint(this, R.string.hint_missing_covers, null, null, getString(R.string.title_settings), getString(R.string.label_import_old_files));
            }

            if (intent.getBooleanExtra("com.eleybourn.bookcatalogue.START_SEARCH", false)) {
                this.onSearchRequested();
            }
        } finally {
            Tracker.exitOnCreate(this);
        }
    }

    /**
     * Support routine now that this activity is no longer a ListActivity
     */
    private ListView getListView() {
        return findViewById(R.id.list);
    }

    /**
     * Handle a list item being clicked.
     *
     * @param arg0     Parent adapter
     * @param view     Row View that was clicked
     * @param position Position of view in listView
     * @param rowId    _id field from cursor
     */
    private void handleItemClick(AdapterView<?> arg0, View view, int position, long rowId) {
        // Move the cursor to the position
        mList.moveToPosition(position);
        // If it's a book, edit it.
        if (mList.getRowView().getKind() == RowKinds.ROW_KIND_BOOK) {
            BookEdit.openBook(this, mList.getRowView().getBookId(), mList.getBuilder(), position);
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
     * Handle selections from context menu
     */
    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        assert info != null;
        mList.moveToPosition(info.position);
        if (mListHandler.onContextItemSelected(mDb, mList.getRowView(), this, mDb, item.getItemId()))
            return true;
        else
            return super.onContextItemSelected(item);
    }

    /**
     * Handle the style that a user has selected.
     *
     * @param name Name of the selected style
     */
    private void handleSelectedStyle(String name) {
        // Find the style, if no match warn user and exit
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
        LibraryStyle style = styles.findCanonical(name);
        if (style == null) {
            Toast.makeText(this, "Could not find appropriate list", Toast.LENGTH_LONG).show();
            return;
        }

        // Set the rebuild state like this is the first time in, which it sort of is, given we are changing style.
        // There is very little ability to preserve position when going from a list sorted by author/series to
        // on sorted by unread/addedDate/publisher. Keeping the current row/pos is probably the most useful
        // thing we can do since we *may* come back to a similar list.
        try {
            ListView lv = getListView();
            mTopRow = lv.getFirstVisiblePosition();
            View v = lv.getChildAt(0);
            mTopRowTop = v == null ? 0 : v.getTop();
        } catch (Exception ignored) {
        }

        // New style, so use user-pref for rebuild
        mRebuildState = AdminLibraryPreferences.getRebuildState();
        // Do a rebuild
        mCurrentStyle = style;
        setupList();
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     */
    private void setupList() {
        boolean isFullRebuild = true;
        mTaskQueue.enqueue(new GetListTask(isFullRebuild));
        showProgress();
    }

    /**
     * Fix background
     */
    @Override
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        // Try to prevent null-pointer errors for rapidly pressing 'back'; this
        // is in response to errors reporting NullPointerException when, most likely,
        // a null is returned by getResources(). The most likely explanation for that
        // is the call occurs after Activity is destroyed.
        if (mIsDead)
            return;

        Tracker.exitOnResume(this);
    }

    /**
     * Display the passed cursor in the ListView, and change the position to targetRow.
     *
     * @param newList    New cursor to use
     * @param targetRows Target to show
     */
    private void displayList(BooklistPseudoCursor newList, final ArrayList<BookRowInfo> targetRows) {
        if (newList == null) {
            throw new RuntimeException("Unexpected empty list");
        }

        final int showHeaderFlags = (mCurrentStyle == null ? LibraryStyle.SUMMARY_SHOW_ALL : mCurrentStyle.getShowHeaderInfo());

        TextView bookCounts = findViewById(R.id.label_bookshelf_count);
        if ((showHeaderFlags & LibraryStyle.SUMMARY_SHOW_COUNT) != 0) {
            if (mUniqueBooks != mTotalBooks)
                bookCounts.setText(this.getString(R.string.displaying_n_books_in_m_entries, String.valueOf(mUniqueBooks), String.valueOf(mTotalBooks)));
            else
                bookCounts.setText(this.getString(R.string.displaying_n_books, String.valueOf(mUniqueBooks)));
            bookCounts.setVisibility(View.VISIBLE);
        } else {
            bookCounts.setVisibility(View.GONE);
        }

        long t0 = System.currentTimeMillis();
        // Save the old list so we can close it later, and set the new list locally
        BooklistPseudoCursor oldList = mList;
        mList = newList;

        // Get new handler and adapter since list may be radically different structure
        mListHandler = new LibraryMultitypeHandler();
        mAdapter = new MultitypeListAdapter(this, mList, mListHandler);

        // Get the ListView and set it up
        final ListView lv = getListView();
        final ListViewHolder lvHolder = new ListViewHolder();
        ViewTagger.setTag(lv, R.id.TAG_HOLDER, lvHolder);

        lv.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        // Force a rebuild of FastScroller
        lv.setFastScrollEnabled(false);
        lv.setFastScrollEnabled(true);

        // Restore saved position
        final int count = mList.getCount();
        try {
            if (mTopRow >= count) {
                mTopRow = count - 1;
                lv.setSelection(mTopRow);
            } else {
                lv.setSelectionFromTop(mTopRow, mTopRowTop);
            }
        } catch (Exception ignored) {
        }// Don't really care

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and once we can tell
        // if it is already in the view.
        if (targetRows != null) {
            // post a runnable to fix the position once the control is drawn
            getListView().post(() -> {
                // Find the actual extend of the current view and get centre.
                int first = lv.getFirstVisiblePosition();
                int last = lv.getLastVisiblePosition();
                int centre = (last + first) / 2;
                System.out.println("New List: (" + first + ", " + last + ")<-" + centre);
                // Get the first 'target' and make it 'best candidate'
                BookRowInfo best = targetRows.get(0);
                int dist = Math.abs(best.listPosition - centre);
                // Scan all other rows, looking for a nearer one
                for (int i = 1; i < targetRows.size(); i++) {
                    BookRowInfo ri = targetRows.get(i);
                    int newDist = Math.abs(ri.listPosition - centre);
                    if (newDist < dist) {
                        dist = newDist;
                        best = ri;
                    }
                }

                System.out.println("Best @" + best.listPosition);
                // Try to put at top if not already visible, or only partially visible
                if (first >= best.listPosition || last <= best.listPosition) {
                    System.out.println("Adjusting position");
                    //
                    // setSelectionFromTop does not seem to always do what is expected.
                    // But adding smoothScrollToPosition seems to get the job done reasonably well.
                    //
                    // Specific problem occurs if:
                    // - put phone in portrait mode
                    // - edit a book near bottom of list
                    // - turn phone to landscape
                    // - save the book (don't cancel)
                    // Book will be off bottom of screen without the smoothScroll in the second Runnable.
                    //
                    lv.setSelectionFromTop(best.listPosition, 0);
                    // Code below does not behave as expected. Results in items often being near bottom.
                    //lv.setSelectionFromTop(best.listPosition, lv.getHeight() / 2);

                    // smoothScrollToPosition is only available at API level 8.
                    // Without this call some positioning may be off by one row (see above).
                    final int newPos = best.listPosition;
                    getListView().post(() -> lv.smoothScrollToPosition(newPos));

                }
            });
            //}
        }

        final boolean hasLevel1 = (mList.numLevels() > 1);
        final boolean hasLevel2 = (mList.numLevels() > 2);

        if (hasLevel2 && (showHeaderFlags & LibraryStyle.SUMMARY_SHOW_LEVEL_2) != 0) {
            lvHolder.level2Text.setVisibility(View.VISIBLE);
            lvHolder.level2Text.setText("");
        } else {
            lvHolder.level2Text.setVisibility(View.GONE);
        }
        if (hasLevel1 && (showHeaderFlags & LibraryStyle.SUMMARY_SHOW_LEVEL_1) != 0) {
            lvHolder.level1Text.setVisibility(View.VISIBLE);
            lvHolder.level1Text.setText("");
        } else
            lvHolder.level1Text.setVisibility(View.GONE);

        // Update the header details
        if (count > 0 && (showHeaderFlags & (LibraryStyle.SUMMARY_SHOW_LEVEL_1 ^ LibraryStyle.SUMMARY_SHOW_LEVEL_2)) != 0)
            updateListHeader(lvHolder, mTopRow, hasLevel1, hasLevel2, showHeaderFlags);

        // Define a scroller to update header detail when top row changes
        lv.setOnScrollListener(new OnScrollListener() {
                                   @Override
                                   public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                                       // TODO: Investigate why BooklistPseudoCursor causes a scroll even when it is closed!
                                       // Need to check isDead because BooklistPseudoCursor misbehaves when activity terminates and closes cursor
                                       if (mLastTop != firstVisibleItem && !mIsDead && (showHeaderFlags != 0)) {
                                           ListViewHolder holder = ViewTagger.getTag(view, R.id.TAG_HOLDER);
                                           updateListHeader(holder, firstVisibleItem, hasLevel1, hasLevel2, showHeaderFlags);
                                       }
                                   }

                                   @Override
                                   public void onScrollStateChanged(AbsListView view, int scrollState) {
                                   }
                               }
        );

        assert getSupportActionBar() != null;
        if (mCurrentStyle == null)
            this.getSupportActionBar().setSubtitle("");
        else
            this.getSupportActionBar().setSubtitle(mCurrentStyle.getDisplayName());

        // Close old list
        if (oldList != null) {
            if (mList.getBuilder() != oldList.getBuilder())
                oldList.getBuilder().close();
            oldList.close();
        }
        long t1 = System.currentTimeMillis();
        System.out.println("displayList: " + (t1 - t0));
    }

    /**
     * Update the list header to match the current top item.
     *
     * @param holder    Holder object for header
     * @param topItem   Top row
     * @param hasLevel1 flag indicating level 1 is present
     * @param hasLevel2 flag indicating level 2 is present
     */
    private void updateListHeader(ListViewHolder holder, int topItem, boolean hasLevel1, boolean hasLevel2, int flags) {
        if (topItem < 0)
            topItem = 0;

        mLastTop = topItem;
        if (hasLevel1 && (flags & LibraryStyle.SUMMARY_SHOW_LEVEL_1) != 0) {
            if (mList.moveToPosition(topItem)) {
                holder.level1Text.setText(mList.getRowView().getLevel1Data());
                String s;
                if (hasLevel2 && (flags & LibraryStyle.SUMMARY_SHOW_LEVEL_2) != 0) {
                    s = mList.getRowView().getLevel2Data();
                    holder.level2Text.setText(s);
                }
            }
        }
    }

    /**
     * Build the underlying flattened list of books.
     *
     * @param isFullRebuild Indicates a complete structural rebuild is required
     * @return The LibraryBuilder object used to build the data
     */
    private LibraryBuilder buildBooklist(boolean isFullRebuild) {
        // If not a full rebuild then just use the current builder to requery the underlying data
        if (mList != null && !isFullRebuild) {
            System.out.println("Doing rebuild()");
            LibraryBuilder b = mList.getBuilder();
            b.rebuild();
            return b;
        } else {
            System.out.println("Doing full reconstruct");
            // Make sure we have a style chosen
            BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
            if (mCurrentStyle == null) {
                String prefStyle = BookCatalogueApp.getAppPreferences().getString(BookCataloguePreferences.PREF_BOOKLIST_STYLE, getString(R.string.sort_author_series));
                mCurrentStyle = styles.findCanonical(prefStyle);
                if (mCurrentStyle == null)
                    mCurrentStyle = styles.get(0);
                BookCatalogueApp.getAppPreferences().setString(BookCataloguePreferences.PREF_BOOKLIST_STYLE, mCurrentStyle.getCanonicalName());
            }

            // get a new builder and add the required extra domains
            LibraryBuilder builder = new LibraryBuilder(mDb, mCurrentStyle);

            builder.requireDomain(DOM_TITLE, TBL_BOOKS.dot(DOM_TITLE), true);
            builder.requireDomain(DOM_READ, TBL_BOOKS.dot(DOM_READ), false);

            // Build based on our current criteria and return
            builder.build(mRebuildState, mMarkBookId, mCurrentBookshelf, "", "", "", "", mSearchText);

            // After first build, always preserve this object state
            mRebuildState = AdminLibraryPreferences.LIBRARY_STATE_PRESERVED;

            return builder;
        }
    }

    /**
     * Save current position information, including view nodes that are expanded.
     * Deleting a book by 'n' authors from the last author in list results
     * in the list decreasing in length by, potentially, n*2 items. The
     * current 'savePosition()' code will return to the old position in the
     * list after such an operation...which will be too far down.
     * ENHANCE: Handle positions a little better when books are deleted.
     */
    private void savePosition() {
        if (mIsDead)
            return;

        final Editor ed = BookCatalogueApp.getAppPreferences().edit();

        // Save position in list
        if (mListHasBeenLoaded) {
            final ListView lv = getListView();
            mTopRow = lv.getFirstVisiblePosition();
            ed.putInt(PREF_TOP_ROW, mTopRow);
            View v = lv.getChildAt(0);
            mTopRowTop = v == null ? 0 : v.getTop();
            ed.putInt(PREF_TOP_ROW_TOP, mTopRowTop);
        }

        if (mCurrentStyle != null)
            ed.putString(PREF_LIST_STYLE, mCurrentStyle.getCanonicalName());

        ed.commit();
    }

    /**
     * Save position when paused
     */
    @Override
    public void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();
        System.out.println("onPause");
        if (mSearchText == null || mSearchText.isEmpty())
            savePosition();

        if (isFinishing())
            mTaskQueue.finish();

        hideProgress();

        Tracker.exitOnPause(this);
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        System.out.println("onDestroy");
        mIsDead = true;

        mTaskQueue.finish();

        try {
            if (mList != null) {
                try {
                    if (mList.getBuilder() != null)
                        mList.getBuilder().close();
                } catch (Exception e) {
                    Logger.logError(e);
                }
                mList.close();
            }
            mDb.close();
        } catch (Exception e) {
            Logger.logError(e);
        }
        mListHandler = null;
        mAdapter = null;
        mBookshelfSpinner = null;
        mBookshelfAdapter = null;
        synchronized (sInstanceLock) {
            mInstanceCount--;
            System.out.println("BoB instances: " + mInstanceCount);
        }
        TrackedCursor.dumpCursors();
        Tracker.exitOnDestroy(this);
    }

    private void initBookshelfSpinner() {
        // Setup the Bookshelf Spinner
        mBookshelfSpinner = findViewById(R.id.field_library_bookshelf);
        mBookshelfAdapter = new ArrayAdapter<>(this, R.layout.library_spinner);
        mBookshelfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfAdapter);

        // Add the default All Books bookshelf
        int pos = 0;
        int bookshelf_pos = pos;
        mBookshelfAdapter.add(getString(R.string.option_all_books));
        pos++;

        Cursor bookshelves = mDb.fetchAllBookshelves();
        if (bookshelves.moveToFirst()) {
            do {
                String this_bookshelf = bookshelves.getString(1);
                if (this_bookshelf.equals(mCurrentBookshelf)) {
                    bookshelf_pos = pos;
                }
                pos++;
                mBookshelfAdapter.add(this_bookshelf);
            }
            while (bookshelves.moveToNext());
        }
        bookshelves.close(); // close the cursor
        // Set the current bookshelf. We use this to force the correct bookshelf after
        // the state has been restored.
        mBookshelfSpinner.setSelection(bookshelf_pos);

        // This is fired whenever a bookshelf is selected. It is also fired when the
        // page is loaded with the default (or current) bookshelf.
        mBookshelfSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                // Check to see if mBookshelfAdapter is null, which should only occur if
                // the activity is being torn down: see Issue 370.
                if (mBookshelfAdapter == null)
                    return;

                String new_bookshelf = mBookshelfAdapter.getItem(position);
                if (position == 0) {
                    new_bookshelf = "";
                }
                assert new_bookshelf != null;
                if (!new_bookshelf.equalsIgnoreCase(mCurrentBookshelf)) {
                    mCurrentBookshelf = new_bookshelf;
                    // save the current bookshelf into the preferences
                    BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
                    SharedPreferences.Editor ed = prefs.edit();
                    ed.putString(PREF_BOOKSHELF, mCurrentBookshelf);
                    ed.commit();
                    setupList();
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
                // Do Nothing
            }
        });

        ImageView bookshelfDown = findViewById(R.id.button_bookshelf);
        bookshelfDown.setOnClickListener(v -> mBookshelfSpinner.performClick());
    }

    /**
     * Run each time the menu button is pressed. This will setup the options menu
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem i;
        mMenuHandler = new MenuHandler();
        mMenuHandler.init(menu);
        mMenuHandler.addCreateBookItems(menu);
        i = mMenuHandler.addItem(menu, MENU_SORT, R.string.sort_and_style_ellipsis, R.drawable.ic_menu_sort);
        i.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        mMenuHandler.addItem(menu, MENU_EXPAND, R.string.menu_sort_by_author_expanded, R.drawable.ic_menu_expand);
        mMenuHandler.addItem(menu, MENU_COLLAPSE, R.string.menu_sort_by_author_collapsed, R.drawable.ic_menu_collapse);
        mMenuHandler.addSearchItem(menu)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        mMenuHandler.addCreateHelpAndAdminItems(menu);

        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This will be called when a menu item is selected. A large switch statement to
     * call the appropriate functions (or other activities)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mMenuHandler != null && !mMenuHandler.onMenuItemSelected(this, item)) {
            switch (item.getItemId()) {

                case MENU_SORT:
                    HintManager.displayHint(this, R.string.hint_library_style_menu, null, () -> doSortMenu(false));
                    return true;

                case MENU_EDIT_STYLE:
                    doEditStyle();
                    return true;

                case MENU_EXPAND: {
                    // It is possible that the list will be empty, if so, ignore
                    if (getListView().getChildCount() != 0) {
                        int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                        savePosition();
                        mList.getBuilder().expandAll(true);
                        mTopRow = mList.getBuilder().getPosition(oldAbsPos);
                        BooklistPseudoCursor newList = mList.getBuilder().getList();
                        displayList(newList, null);
                    }
                    break;
                }
                case MENU_COLLAPSE: {
                    // It is possible that the list will be empty, if so, ignore
                    if (getListView().getChildCount() != 0) {
                        int oldAbsPos = mListHandler.getAbsolutePosition(getListView().getChildAt(0));
                        savePosition();
                        mList.getBuilder().expandAll(false);
                        mTopRow = mList.getBuilder().getPosition(oldAbsPos);
                        displayList(mList.getBuilder().getList(), null);
                    }
                    break;
                }
            }
        }

        return super.onOptionsItemSelected(item);
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

        switch (requestCode) {
            case UniqueId.ACTIVITY_CREATE_BOOK_SCAN:
                try {
                    if (intent != null && intent.hasExtra(CatalogueDBAdapter.KEY_ROW_ID)) {
                        long newId = intent.getLongExtra(CatalogueDBAdapter.KEY_ROW_ID, 0);
                        if (newId != 0) {
                            mMarkBookId = newId;
                        }
                    }
                    // Always rebuild, even after a cancelled edit because the series may have had global edits
                    // ENHANCE: Allow detection of global changes to avoid unnecessary rebuilds
                    this.setupList();
                } catch (NullPointerException e) {
                    // This is not a scan result, but a normal return
                    //fillData();
                }
                break;
            case UniqueId.ACTIVITY_CREATE_BOOK_ISBN:
            case UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY:
            case UniqueId.ACTIVITY_VIEW_BOOK:
            case UniqueId.ACTIVITY_EDIT_BOOK:
                try {
                    if (intent != null && intent.hasExtra(CatalogueDBAdapter.KEY_ROW_ID)) {
                        long id = intent.getLongExtra(CatalogueDBAdapter.KEY_ROW_ID, 0);
                        if (id != 0) {
                            mMarkBookId = id;
                        }
                    }
                    // Always rebuild, even after a cancelled edit because the series may have had global edits
                    // ENHANCE: Allow detection of global changes to avoid unnecessary rebuilds
                    this.setupList();
                } catch (Exception e) {
                    Logger.logError(e);
                }
                break;
            case UniqueId.ACTIVITY_BOOKLIST_STYLES:
            case UniqueId.ACTIVITY_ADMIN:
            case UniqueId.ACTIVITY_PREFERENCES:
                // Refresh the style because prefs may have changed
                refreshStyle();
                this.savePosition();
                this.setupList();
                break;
        }

    }

    /**
     * Update and/or create the current style definition.
     */
    private void refreshStyle() {
        BooklistStyles styles = BooklistStyles.getAllStyles(mDb);
        String styleName;

        if (mCurrentStyle == null) {
            BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
            styleName = prefs.getString(PREF_LIST_STYLE, "");
        } else {
            styleName = mCurrentStyle.getCanonicalName();
        }

        LibraryStyle style = styles.findCanonical(styleName);
        if (style != null)
            mCurrentStyle = style;
        if (mCurrentStyle == null)
            mCurrentStyle = styles.get(0);
    }

    /**
     * Setup the sort options. This function will also call fillData when
     * complete having loaded the appropriate view.
     */
    private void doSortMenu(final boolean showAll) {
        LayoutInflater inf = this.getLayoutInflater();
        View root = inf.inflate(R.layout.menu_style, null);
        RadioGroup group = root.findViewById(R.id.radio_buttons);
        LinearLayout main = root.findViewById(R.id.menu);

        final AlertDialog sortDialog = new AlertDialog.Builder(this).setView(root).create();
        sortDialog.setTitle(R.string.select_style);
        sortDialog.show();

        Iterator<LibraryStyle> i;
        if (!showAll)
            i = BooklistStyles.getPreferredStyles(mDb).iterator();
        else
            i = BooklistStyles.getAllStyles(mDb).iterator();

        while (i.hasNext()) {
            LibraryStyle style = i.next();
            makeRadio(sortDialog, inf, group, style);
        }
        int moreLess;

        if (showAll)
            moreLess = R.string.show_fewer_ellipsis;
        else
            moreLess = R.string.show_more_ellipsis;

        makeText(main, inf, moreLess, v -> {
            sortDialog.dismiss();
            doSortMenu(!showAll);
        });

        makeText(main, inf, R.string.customize_ellipsis, v -> {
            sortDialog.dismiss();
            BooklistStyles.startEditActivity(Library.this);
        });
    }

    /**
     * Add a radio box to the sort options dialogue.
     */
    private void makeRadio(final AlertDialog sortDialog, final LayoutInflater inf, RadioGroup group, final LibraryStyle style) {
        View v = inf.inflate(R.layout.menu_style_radio, group, false);
        RadioButton btn = (RadioButton) v;
        btn.setText(style.getDisplayName());

        btn.setChecked(mCurrentStyle.getCanonicalName().equalsIgnoreCase(style.getCanonicalName()));
        group.addView(btn);

        btn.setOnClickListener(v1 -> {
            handleSelectedStyle(style.getCanonicalName());
            sortDialog.dismiss();
        });
    }

    /**
     * Add a text box to the sort options dialogue.
     */
    private void makeText(final LinearLayout parent, final LayoutInflater inf, final int stringId, OnClickListener listener) {
        TextView view = (TextView) inf.inflate(R.layout.menu_style_text, parent, false);
        Typeface tf = view.getTypeface();
        view.setTypeface(tf, Typeface.ITALIC);
        view.setText(stringId);
        view.setOnClickListener(listener);
        parent.addView(view);
    }

    /**
     * Start the BooklistPreferences Activity
     */
    public void doEditStyle() {
        Intent i = new Intent(this, BooklistStylePropertiesActivity.class);
        i.putExtra(BooklistStylePropertiesActivity.KEY_STYLE, mCurrentStyle);
        i.putExtra(BooklistStylePropertiesActivity.KEY_SAVE_TO_DATABASE, false);
        mEditStyleLauncher.launch(i);
    }

    @Override
    public void onBooklistChange(int flags) {
        if (flags != 0) {
            // Author or series changed. Just regenerate.
            savePosition();
            this.setupList();
        }
    }

    private void showProgress() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
        // Optional: specific logic if you want to disable list interaction
        // getListView().setEnabled(false);
    }

    private void hideProgress() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }
        // Optional: re-enable list interaction
        // getListView().setEnabled(true);
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     *
     * @author Philip Warner
     */
    private class GetListTask implements SimpleTask {
        /**
         * Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
         */
        private final boolean mIsFullRebuild;
        /**
         * Resulting Cursor
         */
        BooklistPseudoCursor mTempList = null;
        /**
         * used to determine new cursor position
         */
        ArrayList<BookRowInfo> mTargetRows = null;

        /**
         * Constructor.
         *
         * @param isFullRebuild Indicates whole table structure needs rebuild, vs. just do a reselect of underlying data
         */
        public GetListTask(boolean isFullRebuild) {
            mIsFullRebuild = isFullRebuild;
        }

        @Override
        public void run(SimpleTaskContext taskContext) {
            try {
                long t0 = System.currentTimeMillis();
                // Build the underlying data
                LibraryBuilder b = buildBooklist(mIsFullRebuild);
                long t1 = System.currentTimeMillis();
                // Try to sync the previously selected book ID
                if (mMarkBookId != 0) {
                    // get all positions of the book
                    mTargetRows = b.getBookAbsolutePositions(mMarkBookId);

                    if (mTargetRows != null && !mTargetRows.isEmpty()) {
                        // First, get the ones that are currently visible...
                        ArrayList<BookRowInfo> visRows = new ArrayList<>();
                        for (BookRowInfo i : mTargetRows) {
                            if (i.visible) {
                                visRows.add(i);
                            }
                        }
                        // If we have any visible rows, only consider them for the new position
                        if (!visRows.isEmpty())
                            mTargetRows = visRows;
                        else {
                            // Make them ALL visible
                            for (BookRowInfo i : mTargetRows) {
                                if (!i.visible) {
                                    b.ensureAbsolutePositionVisible(i.absolutePosition);
                                }
                            }
                            // Recalculate all positions
                            for (BookRowInfo i : mTargetRows) {
                                i.listPosition = b.getPosition(i.absolutePosition);
                            }
                        }
                    }
                } else
                    mTargetRows = null;
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
                mUniqueBooks = mTempList.getUniqueBookCount();
                long t5 = System.currentTimeMillis();
                mTotalBooks = mTempList.getBookCount();
                long t6 = System.currentTimeMillis();

                System.out.println("Build: " + (t1 - t0));
                System.out.println("Position: " + (t2 - t1));
                System.out.println("Select: " + (t3 - t2));
                System.out.println("Count(" + count + "): " + (t4 - t3) + "/" + (t5 - t4) + "/" + (t6 - t5));
                System.out.println("====== ");
                System.out.println("Total: " + (t6 - t0));
                // Save a flag to say list was loaded at least once successfully
                mListHasBeenLoaded = true;

            } finally {
                if (taskContext.isTerminating()) {
                    // onFinish() will not be called, and we can discard our
                    // work...
                    if (mTempList != null && mTempList != mList) {
                        if (mList == null || mTempList.getBuilder() != mList.getBuilder())
                            try {
                                mTempList.getBuilder().close();
                            } catch (Exception e) { /* Ignore */ }

                        try {
                            mTempList.close();
                        } catch (Exception e) { /* Ignore */ }

                    }
                }
            }

        }

        @Override
        public void onFinish(Exception e) {
            // If activity dead, just do a local cleanup and exit.
            if (mIsDead) {
                mTempList.close();
                return;
            }
            // Dismiss the progress dialog, if present
            if (!mTaskQueue.hasActiveTasks()) {
                hideProgress();
            }
            // Update the data
            if (mTempList != null) {
                displayList(mTempList, mTargetRows);
            }
            mTempList = null;
        }

    }

    /**
     * record to hold the current ListView header details.
     *
     * @author Philip Warner
     */
    private class ListViewHolder {
        TextView level1Text;
        TextView level2Text;

        public ListViewHolder() {
            level1Text = findViewById(R.id.label_level_1);
            level2Text = findViewById(R.id.label_level_2);
        }
    }
}
