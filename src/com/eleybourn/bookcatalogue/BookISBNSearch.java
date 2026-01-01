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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.scanner.Scanner;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.utils.AsinUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SoundManager;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * This class is called by the BookCatalogue activity and will search the interwebs for
 * book details based on either a typed in or scanned ISBN.
 * It currently only searches Google Books, but Amazon will be coming soon.
 */
public class BookISBNSearch extends ActivityWithTasks {
    //private static final int CREATE_BOOK = 0;
    public static final String BY = "by";
    /*
     *  Mode this activity is in; MANUAL = data entry, SCAN = data from scanner.
     *  For SCAN, it loops repeatedly starting the scanner.
     */
    private static final int MODE_MANUAL = 1;
    private static final int MODE_SCAN = 2;
    // Object managing current search.
    long mSearchManagerId = 0;
    // A list of author names we have already searched for in this session
    ArrayList<String> mAuthorNames = new ArrayList<>();
    private boolean mScannerStarted = false;
    private EditText mIsbnText;
    private EditText mTitleText;
    private AutoCompleteTextView mAuthorText;
    private ArrayAdapter<String> mAuthorAdapter = null;
    private CatalogueDBAdapter mDbHelper;
    private String mAuthor;
    private String mTitle;
    private String mIsbn;
    private int mMode;
    // Flag to indicate the Activity should not 'finish()' because
    // an alert is being displayed. The Alter will call finish().
    private boolean mDisplayingAlert = false;
    // Object to manage preferred (or found) scanner
    private Scanner mScanner = null;
    // The last Intent returned as a result of creating a book.
    private Intent mLastBookIntent = null;
    // Define this at the class level (e.g., near mIsbnText or mScanner)
    private final androidx.activity.result.ActivityResultLauncher<Intent> mBookEditLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // This code replaces the logic inside onActivityResult for ACTIVITY_EDIT_BOOK
                        Intent intent = result.getData();

                        if (intent != null) {
                            mLastBookIntent = intent;
                        }

                        // Created a book; save the intent and restart scanner if necessary.
                        if (mMode == MODE_SCAN) {
                            startScannerActivity();
                        } else {
                            // If the 'Back' button is pressed on a normal activity,
                            // set the default result to cancelled by setting it here.
                            this.setResult(RESULT_CANCELED, mLastBookIntent);
                        }

                        // Rebuild the author list in case a new author was added.
                        initAuthorList();
                    }
            );
    private final SearchManager.SearchListener mSearchHandler = BookISBNSearch.this::onSearchFinished;

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return mInternetPermissions;
    }

    /**
     * Called when the activity is first created. This function will search the interwebs for
     * book details based on either a typed in or scanned ISBN.
     *
     * @param savedInstanceState The saved bundle (from pausing). Can be null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            super.onCreate(savedInstanceState);

            if (savedInstanceState != null)
                mSearchManagerId = savedInstanceState.getLong("SearchManagerId");

            //System.out.println("BookISBNSearch OnCreate SIS=" + (savedInstanceState == null? "N" : "Y"));

            //do we have a network connection?
            boolean network_available = Utils.isNetworkAvailable(this);
            if (!network_available) {
                Toast.makeText(this, R.string.no_connection, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            Bundle extras = getIntent().getExtras();
            mDbHelper = new CatalogueDBAdapter(this);
            mDbHelper.open();

            assert extras != null;
            mIsbn = extras.getString("isbn");
            String by = extras.getString(BY);

            if (savedInstanceState != null) {
                if (savedInstanceState.containsKey("mScannerStarted")) {
                    mScannerStarted = savedInstanceState.getBoolean("mScannerStarted");
                }
            }

            // BUG NOTE 1:
            //
            // There is a bizarre bug that seems to only affect some users in which this activity
            // is called AFTER the user has finished and the passed Intent has neither a ISBN nor a
            // "BY" in the Extras. Following all the code that starts this activity suggests that
            // the activity is ALWAYS started with the intent data. The problems always occur AFTER
            // adding a book, which confirms that the activity has been started correctly.
            //
            // In order to avoid this problem, we just check for nulls and finish(). THIS IS NOT A FIX
            // it is a MESSY WORK-AROUND.
            //
            // TODO: Find out why BookISBNSearch gets restarted with no data
            //
            // So...we save the extras in savedInstanceState, and look for it when missing
            //
            if (mIsbn == null && (by == null || by.isEmpty())) {
                Logger.logError(new RuntimeException("Empty args for BookISBNSearch"));
                if (savedInstanceState != null) {
                    if (mIsbn == null && savedInstanceState.containsKey("isbn"))
                        mIsbn = savedInstanceState.getString("isbn");
                    if (savedInstanceState.containsKey(BY))
                        by = savedInstanceState.getString(BY);
                }
                // If they are still null, we can't proceed.
                if (mIsbn == null && (by == null || by.isEmpty())) {
                    finish();
                    return;
                }
            }

            // Default to MANUAL
            mMode = MODE_MANUAL;

            Button mConfirmButton;
            if (mIsbn != null) {
                //System.out.println(mId + " OnCreate got ISBN");
                //ISBN has been passed by another component
                setContentView(R.layout.search_isbn);
                mIsbnText = findViewById(R.id.field_isbn);
                mIsbnText.setText(mIsbn);
                go(mIsbn, "", "");
            } else if (by.equals("isbn")) {
                setContentView(R.layout.search_isbn);
                mIsbnText = findViewById(R.id.field_isbn);
                mConfirmButton = findViewById(R.id.label_search);

                // For now, just make sure it's hidden on entry
                getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                final CheckBox allowAsinCb = BookISBNSearch.this.findViewById(R.id.field_allow_asin);
                allowAsinCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        mIsbnText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_CLASS_TEXT);
                        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                    } else {
                        mIsbnText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_CLASS_TEXT);
                        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                    }
                });

                // Set the number buttons
                Button button1 = findViewById(R.id.button_isbn_1);
                button1.setOnClickListener(view -> handleIsbnKey("1"));
                Button button2 = findViewById(R.id.button_isbn_2);
                button2.setOnClickListener(view -> handleIsbnKey("2"));
                Button button3 = findViewById(R.id.button_isbn_3);
                button3.setOnClickListener(view -> handleIsbnKey("3"));
                Button button4 = findViewById(R.id.button_isbn_4);
                button4.setOnClickListener(view -> handleIsbnKey("4"));
                Button button5 = findViewById(R.id.button_isbn_5);
                button5.setOnClickListener(view -> handleIsbnKey("5"));
                Button button6 = findViewById(R.id.button_isbn_6);
                button6.setOnClickListener(view -> handleIsbnKey("6"));
                Button button7 = findViewById(R.id.button_isbn_7);
                button7.setOnClickListener(view -> handleIsbnKey("7"));
                Button button8 = findViewById(R.id.button_isbn_8);
                button8.setOnClickListener(view -> handleIsbnKey("8"));
                Button button9 = findViewById(R.id.button_isbn_9);
                button9.setOnClickListener(view -> handleIsbnKey("9"));
                Button buttonX = findViewById(R.id.button_isbn_X);
                buttonX.setOnClickListener(view -> handleIsbnKey("X"));
                Button button0 = findViewById(R.id.button_isbn_0);
                button0.setOnClickListener(view -> handleIsbnKey("0"));
                ImageButton buttonDel = findViewById(R.id.button_isbn_del);
                buttonDel.setOnClickListener(view -> {
                    try {
                        int start = mIsbnText.getSelectionStart();
                        int end = mIsbnText.getSelectionEnd();
                        if (start < end) {
                            // We have a selection. Delete it.
                            mIsbnText.getText().replace(start, end, "");
                            mIsbnText.setSelection(start, start);
                        } else {
                            // Delete char before cursor
                            if (start > 0) {
                                mIsbnText.getText().replace(start - 1, start, "");
                                mIsbnText.setSelection(start - 1, start - 1);
                            }
                        }
                    } catch (StringIndexOutOfBoundsException e) {
                        //do nothing - empty string
                    }
                });

                mConfirmButton.setOnClickListener(view -> {
                    String mIsbn = mIsbnText.getText().toString();
                    go(mIsbn, "", "");
                });
            } else if (by.equals("name")) {
                // System.out.println(mId + " OnCreate BY NAME");
                setContentView(R.layout.search_name);
                this.setTitle(R.string.label_search_by);

                this.initAuthorList();

                mTitleText = findViewById(R.id.field_title);
                mConfirmButton = findViewById(R.id.label_search);

                mConfirmButton.setOnClickListener(view -> {
                    String mAuthor = mAuthorText.getText().toString();
                    String mTitle = mTitleText.getText().toString();

                    ArrayAdapter<String> adapter = mAuthorAdapter;
                    if (adapter.getPosition(mAuthor) < 0) {
                        // Based on code from filipeximenes we also need to update the adapter here in
                        // case no author or book is added, but we still want to see 'recent' entries.
                        if (!mAuthor.trim().isEmpty()) {
                            boolean found = false;
                            for (String s : mAuthorNames) {
                                if (s.equalsIgnoreCase(mAuthor)) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                // Keep a list of names as typed to use when we recreate list
                                mAuthorNames.add(mAuthor);
                                // Add to adapter, in case search produces no results
                                adapter.add(mAuthor);
                            }
                        }
                    }

                    go("", mAuthor, mTitle);

                });
            } else if (by.equals("scan")) {
                // System.out.println(mId + " OnCreate BY SCAN");
                // Use the scanner to get ISBNs
                mMode = MODE_SCAN;
                setContentView(R.layout.search_isbn);
                mIsbnText = findViewById(R.id.field_isbn);

                // Use the preferred barcode scanner to search for a isbn
                //Prompt users to install the application if they do not have it installed.
                try {
                    // Start the scanner IF this is a real 'first time' call.
                    if (savedInstanceState == null) {
                        startScannerActivity();
                    } else {
                        // It's a saved state, so see if we have an ISBN
                        if (savedInstanceState.containsKey("isbn")) {
                            go(savedInstanceState.getString("isbn"), "", "");
                        }
                    }
                } catch (java.lang.SecurityException | ActivityNotFoundException e) {
                    // Verify - this can be a dangerous operation
                    AlertDialog alertDialog = getAlertDialog();
                    // Prevent the activity result from closing this activity.
                    mDisplayingAlert = true;
                    alertDialog.show();
                }
            }

            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            setSupportActionBar(topAppBar);
            topAppBar.setTitle(R.string.title_add_book);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        } finally {
            Tracker.exitOnCreate(this);
        }
    }

    private AlertDialog getAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(BookISBNSearch.this);
        builder.setMessage(R.string.bad_scanner);
        builder.setTitle(R.string.install_scan_title);
        builder.setIcon(R.drawable.ic_menu_info);

        // setButton2 -> setNegativeButton
        builder.setNegativeButton("ZXing", (dialog, which) -> {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.zxing.client.android"));
            startActivity(marketIntent);
            finish();
        });

        // setButton -> setPositiveButton
        builder.setPositiveButton("Cancel", (dialog, which) -> {
            //do nothing
            finish();
        });

        return builder.create();
    }

    /*
     * Handle character insertion at cursor position in EditText
     */
    private void handleIsbnKey(String key) {
        int start = mIsbnText.getSelectionStart();
        int end = mIsbnText.getSelectionEnd();
        mIsbnText.getText().replace(start, end, key);
        mIsbnText.setSelection(start + 1, start + 1);
    }

    /*
     * Clear any data-entry fields that have been set.
     * Used when a book has been successfully added as we want to get ready for another.
     */
    private void clearFields() {
        if (mIsbnText != null)
            mIsbnText.setText("");
        if (mAuthorText != null)
            mAuthorText.setText("");
        if (mTitleText != null)
            mTitleText.setText("");
    }

    /**
     * This function takes the isbn and search google books (and soon amazon)
     * to extract the details of the book. The details will then get sent to the
     * BookEdit activity
     *
     * @param isbn The ISBN to search
     */
    protected void go(String isbn, String author, String title) {
        //System.out.println(mId + " GO: isbn=" + isbn + ", author=" + author + ", title=" + title);

        // Save the details because we will do some async processing or an alert
        mIsbn = isbn;
        mAuthor = author;
        mTitle = title;

        // If the book already exists, do not continue
        try {
            if (isbn != null && !isbn.isEmpty()) {

                // If the layout has an 'Allow ASIN' checkbox, see if it is checked.
                final CheckBox allowAsinCb = BookISBNSearch.this.findViewById(R.id.field_allow_asin);
                final boolean allowAsin = allowAsinCb != null && allowAsinCb.isChecked();

                if (!IsbnUtils.isValid(isbn) && (!allowAsin || !AsinUtils.isValid(isbn))) {
                    int msg;
                    if (allowAsin) {
                        msg = R.string.x_is_not_a_valid_isbn_or_asin;
                    } else {
                        msg = R.string.x_is_not_a_valid_isbn;
                    }
                    Toast.makeText(this, getString(msg, isbn), Toast.LENGTH_LONG).show();
                    if (mMode == MODE_SCAN) {
                        // Optionally beep if scan failed.
                        SoundManager.beepLow();
                        // reset the now-discarded details
                        mIsbn = "";
                        mAuthor = "";
                        mTitle = "";
                        startScannerActivity();
                    }
                    return;
                } else {
                    if (mMode == MODE_SCAN) {
                        // Optionally beep if scan was valid.
                        SoundManager.beepHigh();
                    }
                    // See if ISBN exists in catalogue
                    final long existingId = mDbHelper.getIdFromIsbn(isbn, true);
                    if (existingId > 0) {
                        // Verify - this can be a dangerous operation
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setMessage(R.string.duplicate_book_message);
                        builder.setTitle(R.string.duplicate_book_title);
                        builder.setIcon(R.drawable.ic_menu_info);

                        // setButton2 -> setNegativeButton (Add)
                        builder.setNegativeButton(this.getResources().getString(R.string.label_add), (dialog, which) -> doSearchBook());

                        // setButton3 -> setNeutralButton (Edit)
                        builder.setNeutralButton(this.getResources().getString(R.string.menu_edit_book), (dialog, which) -> BookEdit.editBook(BookISBNSearch.this, existingId, BookEdit.TAB_EDIT));

                        // setButton -> setPositiveButton (Cancel)
                        builder.setPositiveButton(this.getResources().getString(R.string.button_cancel), (dialog, which) -> {
                            //do nothing
                            if (mMode == MODE_SCAN) {
                                // reset the now-discarded details
                                mIsbn = "";
                                mAuthor = "";
                                mTitle = "";
                                startScannerActivity();
                            }
                        });

                        builder.show();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Logger.logError(e);
        }

        if (mSearchManagerId == 0)
            doSearchBook();

    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void doSearchBook() {
        // System.out.println(mId + " doSearchBook");
        /* Delete any hanging around temporary thumbs */
        try {
            File thumb = CatalogueDBAdapter.getTempThumbnail();
            thumb.delete();
        } catch (Exception e) {
            // do nothing - this is the expected behaviour
        }

        if ((mAuthor != null && !mAuthor.isEmpty()) || (mTitle != null && !mTitle.isEmpty()) || (mIsbn != null && !mIsbn.isEmpty())) {
            //System.out.println(mId + " doSearchBook searching");
            /* Get the book */
            try {
                // Start the lookup in background.
                //mTaskManager.doProgress("Searching");
                SearchManager sm = new SearchManager(getTaskManager(), mSearchHandler);
                mSearchManagerId = sm.getSenderId();
                Tracker.handleEvent(this, "Searching" + mSearchManagerId, Tracker.States.Running);

                this.getTaskManager().doProgress(getString(R.string.searching_ellipsis));
                sm.search(mAuthor, mTitle, mIsbn, true, SearchManager.SEARCH_ALL);
                // reset the details so we don't restart the search unnecessarily
                mAuthor = "";
                mTitle = "";
                mIsbn = "";
            } catch (Exception e) {
                Logger.logError(e);
                Toast.makeText(this, R.string.search_fail, Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            // System.out.println(mId + " doSearchBook no criteria");
            if (mMode == MODE_SCAN)
                startScannerActivity();
        }
    }

    private boolean onSearchFinished(Bundle bookData, boolean cancelled) {
        //// Debugging search results

        Tracker.handleEvent(this, "onSearchFinished" + mSearchManagerId, Tracker.States.Running);
        try {
            //System.out.println(mId + " onSearchFinished");
            if (cancelled || bookData == null) {
                if (mMode == MODE_SCAN)
                    startScannerActivity();
            } else {
                getTaskManager().doProgress(getString(R.string.status_adding_book_ellipsis));
                createBook(bookData);
                // Clear the data entry fields ready for the next one
                clearFields();
            }
            return true;
        } finally {
            // Clean up
            mSearchManagerId = 0;
            // Make sure the base message will be empty.
            this.getTaskManager().doProgress(null);
        }
    }

    @Override
    protected void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();
        if (mSearchManagerId != 0)
            SearchManager.getMessageSwitch().removeListener(mSearchManagerId, mSearchHandler);
        Tracker.exitOnPause(this);
    }

    @Override
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mSearchManagerId != 0)
            SearchManager.getMessageSwitch().addListener(mSearchManagerId, mSearchHandler, true);
        Tracker.exitOnResume(this);
    }

    @Override
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        if (mDbHelper != null)
            mDbHelper.close();
        Tracker.exitOnDestroy(this);
    }

    /*
     * Load the BookEdit Activity
     *
     * return void
     */
    private void createBook(Bundle book) {
        Intent i = new Intent(this, BookEdit.class);
        i.putExtra("bookData", book);
        mBookEditLauncher.launch(i);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        //System.out.println("BookISBNSearch onActivityResult " + resultCode);
        super.onActivityResult(requestCode, resultCode, intent);// Only handle legacy Scanner requests here now
        if (requestCode == UniqueId.ACTIVITY_SCAN) {
            mScannerStarted = false;
            try {
                if (resultCode == RESULT_OK) {
                    // Scanner returned an ISBN...process it.
                    String contents = mScanner.getBarcode(intent);
                    mIsbnText.setText(contents);
                    go(contents, "", "");
                } else {
                    // Scanner Cancelled/failed. Exit if no dialog present.
                    if (mLastBookIntent != null)
                        this.setResult(RESULT_OK, mLastBookIntent);
                    else
                        this.setResult(RESULT_CANCELED, mLastBookIntent);
                    if (!mDisplayingAlert)
                        finish();
                }
            } catch (NullPointerException e) {
                Logger.logError(e);
                finish();
            }

            // Rebuild the author list (preserved from original logic)
            initAuthorList();
        }
    }

    private void initAuthorList() {
        // Get the author field, if present
        mAuthorText = findViewById(R.id.field_author);
        if (mAuthorText != null) {
            // Get all known authors and build a hash of the names
            final ArrayList<String> authors = mDbHelper.getAllAuthors();
            final HashSet<String> uniqueNames = new HashSet<>();
            for (String s : authors)
                uniqueNames.add(s.toUpperCase());

            // Add the names the user has already tried (to handle errors and mistakes)
            for (String s : mAuthorNames) {
                if (!uniqueNames.contains(s.toUpperCase()))
                    authors.add(s);
            }

            // Now get an adapter based on the combined names
            mAuthorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, authors);

            // Set it
            mAuthorText.setAdapter(mAuthorAdapter);
        }
    }

    /*
     * Start scanner activity.
     */
    private void startScannerActivity() {
        //System.out.println(mId + " startScannerActivity");
        if (mScanner == null) {
            mScanner = ScannerManager.getScanner();
        }
        if (!mScannerStarted) {
            //System.out.println(mId + " startScannerActivity STARTING");
            mScannerStarted = true;
            mScanner.startActivityForResult(this, UniqueId.ACTIVITY_SCAN);
        }
    }

    /**
     * Ensure the TaskManager is restored.
     */
    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        //System.out.println(mId + " onRestoreInstanceState");

        mSearchManagerId = inState.getLong("SearchManagerId");

        // Now do 'standard' stuff
        mLastBookIntent = inState.getParcelable("LastBookIntent");

        // Call the super method only after we have the searchManager set up
        super.onRestoreInstanceState(inState);
    }

    @Override
    protected void onSaveInstanceState(Bundle inState) {
        super.onSaveInstanceState(inState);

        // Saving intent data is a kludge due to an apparent Android bug in some
        // handsets. Search for "BUG NOTE 1" in this source file for a discussion
        Bundle b = getIntent().getExtras();
        if (b != null) {
            if (b.containsKey("isbn"))
                inState.putString("isbn", b.getString("isbn"));
            if (b.containsKey(BY))
                inState.putString(BY, b.getString(BY));
        }

        inState.putParcelable("LastBookIntent", mLastBookIntent);
        // Save the current search details as this may be called as a result of a rotate during an alert dialog.
        inState.putString("author", mAuthor);
        inState.putString("isbn", mIsbn);
        inState.putString("title", mTitle);
        inState.putBoolean("mScannerStarted", mScannerStarted);
        if (mSearchManagerId != 0)
            inState.putLong("SearchManagerId", mSearchManagerId);
    }

}
