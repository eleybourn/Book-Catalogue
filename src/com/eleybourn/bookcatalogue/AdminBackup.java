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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.eleybourn.bookcatalogue.BookCatalogueAPI.ApiListener;
import com.eleybourn.bookcatalogue.BookCatalogueAPICredentials.CredentialListener;
import com.google.android.material.appbar.MaterialToolbar;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class AdminBackup extends AppCompatActivity implements CredentialListener, ApiListener {
    private BookCatalogueAPICredentials mApiCredentials;
    private ProgressBar mSyncProgressBar;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_backup_preferences);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        topAppBar.setTitle(R.string.title_backup_preferences);
        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        BookCataloguePreferences mPrefs = new BookCataloguePreferences();
        String email = mPrefs.getAccountEmail();
        boolean optIn = mPrefs.getAccountOptIn();
        String apiToken = mPrefs.getAccountApiToken();

        mSyncProgressBar = findViewById(R.id.syncProgressBar);
        TextView emailView = findViewById(R.id.field_user_email);
        CheckBox optInView = findViewById(R.id.field_opt_in);
        if (email.isEmpty()) {
            findViewById(R.id.logged_out_view).setVisibility(View.VISIBLE);
            findViewById(R.id.logged_in_view).setVisibility(View.GONE);
        } else {
            findViewById(R.id.logged_out_view).setVisibility(View.GONE);
            findViewById(R.id.logged_in_view).setVisibility(View.VISIBLE);
            emailView.setText(email);
            optInView.setChecked(optIn);
            TextView token = findViewById(R.id.field_api_token);
            if (!apiToken.isEmpty()) {
                token.setText(apiToken);
            }
            new BookCatalogueAPI(BookCatalogueAPI.REQUEST_COUNT, this, this);
            new BookCatalogueAPI(BookCatalogueAPI.REQUEST_LAST_BACKUP, this, this);
        }

        /* Login */
        mApiCredentials = new BookCatalogueAPICredentials(this);
        Button login = findViewById(R.id.log_in_button);
        login.setOnClickListener(v -> mApiCredentials.getCredentials(this));
        /* Logout */
        Button logout = findViewById(R.id.log_out_button);
        logout.setOnClickListener(v -> logout());
        /* OptIn */
        optInView.setOnClickListener(v -> optIn(optInView.isChecked()));
        /* Backup Now */
        Button backupNow = findViewById(R.id.backup_now);
        backupNow.setOnClickListener(v -> backup());
    }

    public void logout() {
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        prefs.setAccountEmail("");
        prefs.setAccountOptIn(false);
        prefs.setAccountApiToken("");
        reload();
    }

    public void optIn(boolean checked) {
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        prefs.setAccountOptIn(checked);
        reload();
    }

    public void backup() {
        // Create a new API task to get the count. This will automatically run in the background.
        new BookCatalogueAPI(BookCatalogueAPI.REQUEST_FULL_BACKUP, this, this);
    }

    private void reload() {
        Intent intent = new Intent(this, AdminBackup.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onCredentialReceived() {
        reload();
    }

    @Override
    public void onCredentialError(String errorMessage) {

    }

    @Override
    public void onApiProgress(String request, int current, int total) {
        if (request.equals(BookCatalogueAPI.REQUEST_FULL_BACKUP)) {
            // This is called on the UI thread, so you can update views directly
            String statsText = current + " of " + total + " books backed up";
            TextView backupStatsField = findViewById(R.id.field_backup_stats);
            backupStatsField.setText(statsText);
            mSyncProgressBar.setMax(total);
            mSyncProgressBar.setProgress(current);
            mSyncProgressBar.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public void onApiComplete(String request, String message) {
        if (request.equals(BookCatalogueAPI.REQUEST_COUNT)) {
            // This is called on the UI thread, so you can update views directly
            int totalLocalBooks = CatalogueDBAdapter.countBooks();
            String statsText = message + " of " + totalLocalBooks + " books backed up";
            TextView backupStatsField = findViewById(R.id.field_backup_stats);
            backupStatsField.setText(statsText);
        } else if (request.equals(BookCatalogueAPI.REQUEST_LAST_BACKUP)) {
            String statsText = "Last Backup: " + message;
            TextView backupDateField = findViewById(R.id.field_last_backup_date);
            backupDateField.setText(statsText);
        }
        mSyncProgressBar.setVisibility(View.GONE);

    }

    @Override
    public void onApiError(String request, String error) {
        // Show an error message to the user on the UI thread
        if (request.equals(BookCatalogueAPI.REQUEST_COUNT) || request.equals(BookCatalogueAPI.REQUEST_LAST_BACKUP)) {
            TextView backupStatsField = findViewById(R.id.field_backup_stats);
            backupStatsField.setText("Could not load backup statistics.");
            Toast.makeText(AdminBackup.this, "Error: " + error, Toast.LENGTH_LONG).show();
        }
        mSyncProgressBar.setVisibility(View.GONE);

    }
}
