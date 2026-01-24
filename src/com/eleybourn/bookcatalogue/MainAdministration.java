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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment.OnMessageDialogResultListener;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to
 * manage bookshelves.
 *
 * @author Evan Leybourn
 */
public class MainAdministration extends ActivityWithTasks
        implements OnMessageDialogResultListener {
    private CatalogueDBAdapter mDbHelper;

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerOldFilesTreeCopyLauncher();
        try {
            super.onCreate(savedInstanceState);
            mDbHelper = new CatalogueDBAdapter(this);
            mDbHelper.open();
            setContentView(R.layout.main_administration);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            topAppBar.setTitle(R.string.title_settings);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

            setupAdmin();
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * This function builds the Administration page in 4 sections.
     * 1. The button to goto the manage bookshelves activity
     * 2. The button to export the database
     * 3. The button to import the exported file into the database
     * 4. The application version and link details
     * 5. The link to paypal for donation
     */
    public void setupAdmin() {
        /* Bookshelf Link */
        View bookshelf = findViewById(R.id.bookshelfLabel);
        bookshelf.setBackgroundResource(Utils.backgroundFlash(this));
        bookshelf.setOnClickListener(v -> manageBookshelves());

        /* Manage Fields Link */
        View fields = findViewById(R.id.fieldsLabel);
        fields.setBackgroundResource(Utils.backgroundFlash(this));
        fields.setOnClickListener(v -> manageFields());

        /* Book List Preferences Link */
        View blPrefs = findViewById(R.id.booklistPreferencesLabel);
        blPrefs.setBackgroundResource(Utils.backgroundFlash(this));
        blPrefs.setOnClickListener(v -> BookCatalogueApp.startPreferencesActivity(MainAdministration.this));

        /* Book List Preferences Link */
        View blBackupPrefs = findViewById(R.id.backupSyncLabel);
        // Make line flash when clicked.
        blBackupPrefs.setBackgroundResource(android.R.drawable.list_selector_background);
        blBackupPrefs.setOnClickListener(v -> manageBackup());

        // Edit Book list styles
        {
            View lbl = findViewById(R.id.editStylesLabel);
            // Make line flash when clicked.
            lbl.setBackgroundResource(android.R.drawable.list_selector_background);
            lbl.setOnClickListener(v -> BooklistStyles.startEditActivity(MainAdministration.this));
        }

        /* Other Prefs Link */
        View otherPrefs = findViewById(R.id.otherPrefsLabel);
        // Make line flash when clicked.
        otherPrefs.setBackgroundResource(android.R.drawable.list_selector_background);
        otherPrefs.setOnClickListener(v -> {
            Intent i = new Intent(MainAdministration.this, AdminOtherPreferences.class);
            startActivity(i);
        });

        {
            /* Update Fields Link */
            View thumb = findViewById(R.id.thumbLabel);
            // Make line flash when clicked.
            thumb.setBackgroundResource(android.R.drawable.list_selector_background);
            thumb.setOnClickListener(v -> updateThumbnails());
        }

        {
            /* Reset Hints Link */
            View hints = findViewById(R.id.resetHintsLabel);
            // Make line flash when clicked.
            hints.setBackgroundResource(android.R.drawable.list_selector_background);
            hints.setOnClickListener(v -> {
                HintManager.resetHints();
                Toast.makeText(MainAdministration.this, R.string.alert_hints_have_been_reset, Toast.LENGTH_LONG).show();
            });
        }

        // Erase cover cache
        {
            View erase = findViewById(R.id.eraseCoverCacheLabel);
            // Make line flash when clicked.
            erase.setBackgroundResource(android.R.drawable.list_selector_background);
            erase.setOnClickListener(v -> {
                Utils utils = new Utils();
                try {
                    utils.eraseCoverCache();
                } finally {
                    utils.close();
                }
            });
        }

        {
            // Debug ONLY!
            /* Backup Link */
            View backup = findViewById(R.id.backupLabel);
            // Make line flash when clicked.
            backup.setBackgroundResource(android.R.drawable.list_selector_background);
            backup.setOnClickListener(v -> {
                if (mDbHelper.backupDbFile() != null) {
                    Toast.makeText(MainAdministration.this, R.string.alert_backup_success, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainAdministration.this, R.string.alert_unexpected_error, Toast.LENGTH_LONG).show();
                }
            });
        }

        {
            /* Import old files */
            View imp = findViewById(R.id.importOldFilesLabel);
            // Make line flash when clicked.
            imp.setBackgroundResource(android.R.drawable.list_selector_background);
            imp.setOnClickListener(v -> startImportOldFiles());
        }

    }

    /**
     * Load the Bookshelf Activity
     */
    private void manageBookshelves() {
        Intent i = new Intent(this, AdminBookshelf.class);
        startActivity(i);
    }

    /**
     * Load the Manage Field Visibility Activity
     */
    private void manageFields() {
        Intent i = new Intent(this, AdminFieldVisibility.class);
        startActivity(i);
    }

    /**
     * Load the Manage Field Visibility Activity
     */
    private void manageBackup() {
        Intent i = new Intent(this, AdminBackup.class);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDbHelper.close();
    }

    /**
     * Fix background
     */
    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * Update all (non-existent) thumbnails
     * <p>
     * There is a current limitation that restricts the search to only books
     * with an ISBN
     */
    private void updateThumbnails() {
        Intent i = new Intent(this, AdminUpdateFromInternet.class);
        startActivity(i);
    }

    /**
     * Pass on the event to the relevant handler.
     *
     * @param dialogId As passed to us
     * @param dialog   As passed to us
     * @param button   As passed to us
     */
    @Override
    public void onMessageDialogResult(int dialogId, MessageDialogFragment dialog, int button) {
        // Do nothing. We just need this so we can display message dialogs.
        super.onMessageDialogResult(dialogId, dialog, button);
    }

}
