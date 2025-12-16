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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment.OnMessageDialogResultListener;
import com.eleybourn.bookcatalogue.utils.AlertDialogUtils;
import com.eleybourn.bookcatalogue.utils.AlertDialogUtils.AlertDialogItem;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

/**
 * Implement the 'Main Menu' for BookCatalogue. This is one of two possible start screens.
 * <p>
 * - 'My Books' -> preferred bookshelf view
 * - 'Add Book' -> Add Method Dialog
 * - 'Loan/Return/Edit Book'
 * - 'Search'
 * - 'Admin & Preferences'
 * - Help
 * - Export/Import/Sync
 *
 * @author Philip Warner
 *
 */
public class MainMenu extends BookCatalogueActivity implements OnMessageDialogResultListener {

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register any common launchers defined in parents.
        super.onCreate(savedInstanceState);

        // If we get here, we're meant to be in this activity.
        setContentView(R.layout.main_menu);
        setTitle(R.string.app_name);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        topAppBar.setLogo(this.getResources().getIdentifier("ic_launcher4", "drawable", this.getPackageName()));

        // Setup handlers for items. It's just a menu after all.
        setOnClickListener(R.id.cardLibrary, mBrowseHandler);
        setOnClickListener(R.id.cardAddBook, mAddBookHandler);
        setOnClickListener(R.id.cardSearch, mSearchHandler);
        setOnClickListener(R.id.cardSettings, mAdminHandler);
        setOnClickListener(R.id.cardHelp, mHelpHandler);
        setOnClickListener(R.id.cardAbout, mAboutHandler);
        if (BuildConfig.IS_DONATE_ALLOWED) {
            setOnClickListener(R.id.cardDonate, mDonateHandler);
        } else {
            findViewById(R.id.cardDonate).setVisibility(View.GONE);
        }
        // Goodreads will be shown/hidden in onResume()
        //setOnClickListener(R.id.goodreads_label, mGoodreadsHandler);

        if (savedInstanceState == null) {
            HintManager.displayHint(this, R.string.hint_startup_screen, R.string.hint_startup_screen_heading, null);
        }
    }

    /**
     * Fix background
     */
    @Override
    public void onResume() {
        super.onResume();

        if (CatalogueDBAdapter.DEBUG_INSTANCES)
            CatalogueDBAdapter.dumpInstances();

    }

    /**
     * Add Book Menu Handler
     */
    private final OnClickListener mAddBookHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            ArrayList<AlertDialogItem> items = new ArrayList<>();
            items.add(new AlertDialogItem(getString(R.string.scan_barcode_isbn), mCreateBookScan));
            items.add(new AlertDialogItem(getString(R.string.enter_isbn), mCreateBookIsbn));
            items.add(new AlertDialogItem(getString(R.string.search_internet), mCreateBookName));
            items.add(new AlertDialogItem(getString(R.string.add_manually), mCreateBookManually));
            AlertDialogUtils.showContextDialogue(MainMenu.this, getString(R.string.label_insert), items);
        }
    };

    /**
     * Search Menu Handler
     */
    private final OnClickListener mSearchHandler = v -> {
        Intent i = new Intent(MainMenu.this, SearchCatalogue.class);
        //i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    };

    /**
     * Admin Menu Handler
     */
    private final OnClickListener mAdminHandler = v -> {
        Intent i = new Intent(MainMenu.this, MainAdministration.class);
        startActivity(i);
    };

    /**
     * Browse Handler
     */
    private final OnClickListener mBrowseHandler = v -> {
        Intent i = new Intent(MainMenu.this, BooksOnBookshelf.class);
        startActivity(i);
    };

    /**
     * About Menu Handler
     */
    private final OnClickListener mAboutHandler = v -> {
        Intent i = new Intent(MainMenu.this, MainAbout.class);
        startActivity(i);
    };

    /**
     * Help Menu Handler
     */
    private final OnClickListener mHelpHandler = v -> {
        Intent i = new Intent(MainMenu.this, MainHelp.class);
        startActivity(i);
    };

    /**
     * Donate Menu Handler
     */
    private final OnClickListener mDonateHandler = v -> {
        Intent i = new Intent(MainMenu.this, MainDonate.class);
        startActivity(i);
    };

    /**
     * Utility routine to set the OnClickListener for a given view item.
     *
     * @param id Sub-View ID
     * @param l  Listener
     */
    private void setOnClickListener(int id, OnClickListener l) {
        View v = this.findViewById(id);
        if (v != null) {
            v.setOnClickListener(l);
            // v.setBackgroundResource(android.R.drawable.list_selector_background);
        }
    }

    /**
     * Add Book Sub-Menu: Load the BookEdit Activity
     */
    private final Runnable mCreateBookManually = () -> {
        Intent i = new Intent(MainMenu.this, BookEdit.class);
        startActivity(i);
    };

    /**
     * Add Book Sub-Menu: Load the Search by ISBN Activity
     */
    private final Runnable mCreateBookIsbn = () -> {
        Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
        i.putExtra(BookISBNSearch.BY, "isbn");
        startActivity(i);
    };

    /**
     * Add Book Sub-Menu: Load the Search by ISBN Activity
     */
    private final Runnable mCreateBookName = () -> {
        Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
        i.putExtra(BookISBNSearch.BY, "name");
        startActivity(i);
    };

    /**
     * Add Book Sub-Menu: Load the Search by ISBN Activity to begin scanning.
     */
    private final Runnable mCreateBookScan = () -> {
        Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
        i.putExtra(BookISBNSearch.BY, "scan");
        startActivity(i);
    };

    /**
     * Cleanup!
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
