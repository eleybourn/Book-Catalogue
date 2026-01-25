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
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.documentfile.provider.DocumentFile;

import com.eleybourn.bookcatalogue.BookCatalogueAPI.ApiListener;
import com.eleybourn.bookcatalogue.BookCatalogueAPICredentials.CredentialListener;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class AdminBackup extends ActivityWithTasks implements CredentialListener,
        OnImportTypeSelectionDialogResultListener,
        OnExportTypeSelectionDialogResultListener {
    private BookCatalogueAPICredentials mApiCredentials;
    private ProgressBar mSyncProgressBar;
    private TextView mBackupStatsField;
    private Button mBackupNowButton;
    private Button mRestoreNowButton;
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
        topAppBar.setTitle(R.string.title_backup_preferences_tmp);
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
                    new MaterialAlertDialogBuilder(this)
                            .setMessage(R.string.alert_import)
                            .setTitle(R.string.label_import_books)
                            .setIcon(R.drawable.ic_menu_upload)
                            .setPositiveButton(R.string.button_ok, (dialog1, which) -> {
                                dialog1.dismiss();
                                launchCsvImportPicker();
                            })
                            .setNegativeButton(R.string.button_cancel, (dialog2, which) -> dialog2.dismiss())
                            .create().show();
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
            // TODO: Enable the visibility of the login screen
            //findViewById(R.id.logged_out_view).setVisibility(View.VISIBLE);
            findViewById(R.id.logged_out_view).setVisibility(View.GONE);
            findViewById(R.id.logged_in_view).setVisibility(View.GONE);
        } else {
            findViewById(R.id.logged_out_view).setVisibility(View.GONE);
            //findViewById(R.id.logged_in_view).setVisibility(View.VISIBLE);
            findViewById(R.id.logged_in_view).setVisibility(View.GONE);
            emailView.setText(email);
            optInView.setChecked(optIn);
            TextView token = findViewById(R.id.field_api_token);
            if (!apiToken.isEmpty()) {
                token.setText(apiToken);
            }
            if (!BookCatalogueAPI.isBackupRunning && !BookCatalogueAPI.isRestoreRunning) {
                new BookCatalogueAPI(this, BookCatalogueAPI.REQUEST_INFO_COUNT, mApiListener);
                new BookCatalogueAPI(this, BookCatalogueAPI.REQUEST_INFO_LAST, mApiListener);
            }
        }

        /* Login */
        mApiCredentials = new BookCatalogueAPICredentials(this);
        Button login = findViewById(R.id.log_in_button);
        login.setOnClickListener(v -> mApiCredentials.getCredentials(this));
        /* Logout */
        Button logout = findViewById(R.id.log_out_button);
        logout.setOnClickListener(v -> logout());
        /* OptIn */
        // ... inside onCreate() ...
        /* OptIn */
        optInView.setOnClickListener(v -> optIn(optInView.isChecked()));
        /* Backup Now */
        mBackupNowButton = findViewById(R.id.backup_now); // Use the member variable
        mBackupNowButton.setOnClickListener(v -> backup());
        /* Restore Now */
        mRestoreNowButton = findViewById(R.id.restore_now); // Use the member variable
        mRestoreNowButton.setOnClickListener(v -> restore());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When the activity becomes active, set it as the current listener.
        // Any running background task will now send updates to this activity.
        BookCatalogueAPI.setActiveListener(mApiListener);
        // Disable buttons if a backup or restore is currently in progress
        if (BookCatalogueAPI.isBackupRunning || BookCatalogueAPI.isRestoreRunning) {
            mBackupNowButton.setEnabled(false);
            mRestoreNowButton.setEnabled(false);
        } else {
            mBackupNowButton.setEnabled(true);
            mRestoreNowButton.setEnabled(true);
        }
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
        mBackupNowButton.setEnabled(false);
        mRestoreNowButton.setEnabled(false);
        BookCatalogueAPI.isBackupRunning = true;
        new BookCatalogueAPI(this, BookCatalogueAPI.REQUEST_BACKUP_ALL, mApiListener);
    }

    public void restore() {
        mBackupNowButton.setEnabled(false);
        mRestoreNowButton.setEnabled(false);
        BookCatalogueAPI.isRestoreRunning = true;
        // Create a new API task to get the count. This will automatically run in the background.
        new BookCatalogueAPI(this, BookCatalogueAPI.REQUEST_RESTORE_ALL, mApiListener);
    }

    private void reload() {
        Intent intent = new Intent(this, AdminBackup.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onCredentialReceived() {
        new BookCatalogueAPI(this, BookCatalogueAPI.REQUEST_LOGIN, mApiListener);
    }

    @Override
    public void onCredentialError(String errorMessage) {
        runOnUiThread(() -> Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show());
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
            onApiProgress(request, current, total, "");
        }

        @Override
        public void onApiProgress(String request, int current, int total, String message) {
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
                String statsText;
                if (message.isEmpty()) {
                    statsText = current + " of " + total + " books restored";
                } else {
                    statsText = current + " of " + total + " " + message;
                }
                activity.mBackupStatsField.setText(statsText);
                activity.mSyncProgressBar.setMax(total);
                activity.mSyncProgressBar.setProgress(current);
                activity.mSyncProgressBar.setVisibility(View.VISIBLE);
            }

            // Disable buttons if backup or restore is running
            if (request.equals(BookCatalogueAPI.REQUEST_BACKUP_ALL) || request.equals(BookCatalogueAPI.REQUEST_RESTORE_ALL)) {
                if (current != total) {
                    activity.mBackupNowButton.setEnabled(false);
                    activity.mRestoreNowButton.setEnabled(false);
                }
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
                    new BookCatalogueAPI(activity.getApplicationContext(), BookCatalogueAPI.REQUEST_INFO_COUNT, this);
                    new BookCatalogueAPI(activity.getApplicationContext(), BookCatalogueAPI.REQUEST_INFO_LAST, this);
                    BookCatalogueAPI.isBackupRunning = false;
                    break;
                case BookCatalogueAPI.REQUEST_RESTORE_ALL:
                    BookCatalogueAPI.isRestoreRunning = false;
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
            if (request.equals(BookCatalogueAPI.REQUEST_BACKUP_ALL) || request.equals(BookCatalogueAPI.REQUEST_RESTORE_ALL)) {
                if (activity.mBackupNowButton != null) {
                    activity.mBackupNowButton.setEnabled(true);
                }
                if (activity.mRestoreNowButton != null) {
                    activity.mRestoreNowButton.setEnabled(true);
                }
                Toast.makeText(activity, "Error: " + error, Toast.LENGTH_LONG).show(); // Show error for backup/restore
            }
        }
    }

    /**
     * Called when any background task completes
     */
    @Override
    public void onTaskEnded(ManagedTask task) {
        // If it's an export, then handle it
        if (task instanceof ExportThread) {
            onExportFinished((ExportThread) task);
        }
    }

    public void onExportFinished(final ExportThread task) {
        if (!isFinishing()) {
            try {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.label_email_export)
                        .setIcon(R.drawable.ic_menu_send)
                        .setPositiveButton(
                                getResources().getString(R.string.button_ok),
                                (dialog, which) -> {
                                    // setup the mail message
                                    final Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                                    emailIntent.setType("plain/text");
                                    String subject = "[" + getString(R.string.app_name) + "] " + getString(R.string.label_export_to_csv);
                                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                                    //has to be an ArrayList
                                    ArrayList<Uri> uris = new ArrayList<>();
                                    // Find all files of interest to send
                                    try {
                                        uris.add(task.getFile().getUri());
                                        // Send it, if there are any files to send.
                                        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                                        startActivity(Intent.createChooser(emailIntent, "Send mail..."));
                                    } catch (NullPointerException e) {
                                        Logger.logError(e);
                                        Toast.makeText(this, R.string.alert_export_failed_sdcard, Toast.LENGTH_LONG).show();
                                    }

                                    dialog.dismiss();
                                })
                        .setNegativeButton(
                                getResources().getString(R.string.button_cancel),
                                (dialog, which) -> {
                                    //do nothing
                                    dialog.dismiss();
                                })
                        // Catch errors resulting from 'back' being pressed multiple times so that the activity is destroyed
                        // before the dialog can be shown.
                        // See http://code.google.com/p/android/issues/detail?id=3953
                        .create().show();
            } catch (Exception e) {
                Logger.logError(e);
            }
        }
    }

}
