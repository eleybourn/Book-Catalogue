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
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;

import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.BookCatalogueAPI.ApiListener;
import com.eleybourn.bookcatalogue.BookCatalogueAPICredentials.CredentialListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.lang.ref.WeakReference;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class AdminBackup extends ActivityWithTasks implements CredentialListener,
        OnImportTypeSelectionDialogResultListener,
        OnExportTypeSelectionDialogResultListener {
    private BookCatalogueAPICredentials mApiCredentials;
    private ProgressBar mSyncProgressBar;
    private TextView mBackupStatsField;
    private TextView mLastBackupDateField;
    private ApiListener mApiListener;
    private ActivityResultLauncher<String[]> mCsvImportPickerLauncher;
    private ActivityResultLauncher<String> mCsvExportPickerLauncher;


    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

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

        registerBackupExportPickerLauncher(ID.DIALOG_OPEN_IMPORT_TYPE);
        registerBackupImportPickerLauncher();
        mCsvImportPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                result -> {
                    if (result != null) {
                        DocumentFile f = DocumentFile.fromSingleUri(this, result);
                        if (f != null) {
                            mBackupFile = f;
                            importData(f);
                        }
                    }
                }
        );

        mCsvExportPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"),
                result -> {
                    if (result != null) {
                        DocumentFile f = DocumentFile.fromSingleUri(this, result);
                        if (f != null) {
                            mBackupFile = f;
                            exportData(f);
                        }
                    }
                }
        );
        /* Backup Catalogue Link */
        View backup = findViewById(R.id.backup_locally);
        backup.setOnClickListener(v -> launchBackupExport());

        /* Restore Catalogue Link */
        View restore = findViewById(R.id.import_locally);
        restore.setOnClickListener(v -> launchBackupImport());

        /* Export Link */
        View export = findViewById(R.id.export_csv_locally);
        export.setOnClickListener(v -> launchCsvExportPicker());

        /* Import Link */
        View imports = findViewById(R.id.import_csv_locally);
        imports.setOnClickListener(
                v -> {
                    // Verify - this can be a dangerous operation
                    AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(R.string.alert_import).create();
                    alertDialog.setTitle(R.string.label_import_books);
                    alertDialog.setIcon(R.drawable.ic_menu_upload);
                    alertDialog.setButton(
                            AlertDialog.BUTTON_POSITIVE,
                            this.getResources().getString(R.string.button_ok),
                            (dialog, which) -> launchCsvImportPicker());
                    alertDialog.setButton(
                            AlertDialog.BUTTON_NEGATIVE,
                            this.getResources().getString(R.string.button_cancel),
                            (dialog, which) -> {
                                //do nothing
                            });
                    alertDialog.show();
                });

        BookCataloguePreferences mPrefs = new BookCataloguePreferences();
        String email = mPrefs.getAccountEmail();
        boolean optIn = mPrefs.getAccountOptIn();
        String apiToken = mPrefs.getAccountApiToken();

        mApiListener = new StaticApiListener(this);
        mSyncProgressBar = findViewById(R.id.syncProgressBar);
        mBackupStatsField = findViewById(R.id.field_backup_stats);
        mLastBackupDateField = findViewById(R.id.field_last_backup_date);
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
            new BookCatalogueAPI(BookCatalogueAPI.REQUEST_INFO_COUNT, mApiListener);
            new BookCatalogueAPI(BookCatalogueAPI.REQUEST_INFO_LAST, mApiListener);
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
        /* Restore Now */
        Button restoreNow = findViewById(R.id.restore_now);
        restoreNow.setOnClickListener(v -> restore());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When the activity becomes active, set it as the current listener.
        // Any running background task will now send updates to this activity.
        BookCatalogueAPI.setActiveListener(mApiListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // When the activity is no longer in the foreground, clear the listener
        // to prevent updates from being sent to it while it's paused.
        // The WeakReference inside StaticApiListener will prevent crashes, but this is cleaner.
        BookCatalogueAPI.setActiveListener(null);
    }

    /**
     * Call the input picker.
     */
    private void launchCsvImportPicker() {
        // 3. Just LAUNCH the already-registered launcher. Do not register again.
        mCsvImportPickerLauncher.launch(new String[]{"*/*"});
    }

    /**
     * Call the output picker.
     */
    private void launchCsvExportPicker() {
        // 3. Just LAUNCH the already-registered launcher. Do not register again.
        mCsvExportPickerLauncher.launch("Export.csv");
    }

    /**
     * Export all data to a CSV file
     */
    private void exportData(DocumentFile file) {
        ExportThread thread = new ExportThread(this, getTaskManager(), file);
        thread.start();
    }

    /**
     * Import all data from the passed CSV file spec
     */
    private void importData(DocumentFile f) {
        ImportThread thread;
        try {
            thread = new ImportThread(this, getTaskManager(), f);
        } catch (IOException e) {
            Logger.logError(e);
            Toast.makeText(this, getString(R.string.alert_problem_starting_import_arg, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        thread.start();
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
        new BookCatalogueAPI(BookCatalogueAPI.REQUEST_BACKUP_ALL, mApiListener);
    }

    public void restore() {
        // Create a new API task to get the count. This will automatically run in the background.
        new BookCatalogueAPI(BookCatalogueAPI.REQUEST_RESTORE_ALL, mApiListener);
    }

    private void reload() {
        Intent intent = new Intent(this, AdminBackup.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onCredentialReceived() {
        new BookCatalogueAPI(BookCatalogueAPI.REQUEST_LOGIN, mApiListener);
    }

    @Override
    public void onCredentialError(String errorMessage) {

    }

    /**
     * Pass on the event to the relevant handler.
     *
     * @param dialogId As passed to us
     * @param dialog   As passed to us
     * @param settings As passed to us
     */
    @Override
    public void onExportTypeSelectionDialogResult(int dialogId, BookCatalogueDialogFragment dialog, ExportSettings settings) {
        mBackupExportManager.onExportTypeSelectionDialogResult(dialogId, dialog, settings);
    }

    @Override
    public void onImportTypeSelectionDialogResult(int dialogId, ImportTypeSelectionDialogFragment dialog, int rowId, DocumentFile file) {
        mBackupImportManager.onImportTypeSelectionDialogResult(dialogId, dialog, rowId, file);
    }

    // Define the listener as a static inner class
    private static class StaticApiListener implements ApiListener {
        private final WeakReference<AdminBackup> activityReference;

        StaticApiListener(AdminBackup activity) {
            // Use a WeakReference to avoid memory leaks
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void onApiProgress(String request, int current, int total) {
            AdminBackup activity = activityReference.get();
            // Only update UI if the activity is still alive
            if (activity == null || activity.isFinishing()) {
                return;
            }

            if (request.equals(BookCatalogueAPI.REQUEST_BACKUP_ALL)) {
                String statsText = current + " of " + total + " books backed up";
                activity.mBackupStatsField.setText(statsText);
                activity.mSyncProgressBar.setMax(total);
                activity.mSyncProgressBar.setProgress(current);
                activity.mSyncProgressBar.setVisibility(View.VISIBLE);
            }

            if (request.equals(BookCatalogueAPI.REQUEST_RESTORE_ALL)) {
                String statsText = current + " of " + total + " books restored";
                activity.mBackupStatsField.setText(statsText);
                activity.mSyncProgressBar.setMax(total);
                activity.mSyncProgressBar.setProgress(current);
                activity.mSyncProgressBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onApiComplete(String request, String message) {
            AdminBackup activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            switch (request) {
                case BookCatalogueAPI.REQUEST_INFO_COUNT: {
                    int totalLocalBooks = CatalogueDBAdapter.countBooks();
                    String statsText = message + " of " + totalLocalBooks + " books backed up";
                    activity.mBackupStatsField.setText(statsText);
                    break;
                }
                case BookCatalogueAPI.REQUEST_INFO_LAST: {
                    String statsText = "Last Backup: " + message;
                    activity.mLastBackupDateField.setText(statsText);
                    break;
                }
                case BookCatalogueAPI.REQUEST_BACKUP_ALL:
                    // Reload stats after full backup completes
                    new BookCatalogueAPI(BookCatalogueAPI.REQUEST_INFO_COUNT, this);
                    new BookCatalogueAPI(BookCatalogueAPI.REQUEST_INFO_LAST, this);
                    break;
                case BookCatalogueAPI.REQUEST_LOGIN:
                    activity.reload();
                    break;
            }

            // Hide progress bar on completion of any task except count/last_backup
            if (!request.equals(BookCatalogueAPI.REQUEST_INFO_COUNT) && !request.equals(BookCatalogueAPI.REQUEST_INFO_LAST)) {
                activity.mSyncProgressBar.setVisibility(View.GONE);
            }
        }

        @Override
        public void onApiError(String request, String error) {
            AdminBackup activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            // Show an error message to the user on the UI thread
            if (request.equals(BookCatalogueAPI.REQUEST_INFO_COUNT) || request.equals(BookCatalogueAPI.REQUEST_INFO_LAST)) {
                activity.mBackupStatsField.setText(R.string.alert_could_not_load_backup_statistics);
                Toast.makeText(activity, "Error: " + error, Toast.LENGTH_LONG).show();
            }
            activity.mSyncProgressBar.setVisibility(View.GONE);
        }
    }

}
