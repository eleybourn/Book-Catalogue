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
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.CustomCredential;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment.OnMessageDialogResultListener;
import com.eleybourn.bookcatalogue.utils.AlertDialogUtils;
import com.eleybourn.bookcatalogue.utils.AlertDialogUtils.AlertDialogItem;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    // 1. Declare Credential Manager
    private CredentialManager mCredentialManager;

    private ProgressBar mSyncProgressBar;

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
        mCredentialManager = CredentialManager.create(this);
        setTitle(R.string.app_name);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        topAppBar.setLogo(R.drawable.ic_launcher4);
        topAppBar.setNavigationIcon(null);

        mSyncProgressBar = findViewById(R.id.syncProgressBar);

        // Setup handlers for items. It's just a menu after all.
        setOnClickListener(R.id.cardLibrary, mBrowseHandler);
        setOnClickListener(R.id.cardAddBook, mAddBookHandler);
        setOnClickListener(R.id.cardSearch, mSearchHandler);
        setOnClickListener(R.id.cardSync, mSyncHandler);
        setOnClickListener(R.id.cardSettings, mAdminHandler);
        setOnClickListener(R.id.cardHelp, mHelpHandler);
        setOnClickListener(R.id.cardAbout, mAboutHandler);
        if (BuildConfig.IS_DONATE_ALLOWED) {
            setOnClickListener(R.id.cardDonate, mDonateHandler);
        } else {
            findViewById(R.id.cardDonate).setVisibility(View.GONE);
        }

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
     * Sync Menu Handler (UPDATED FOR CREDENTIAL MANAGER)
     */
    private final OnClickListener mSyncHandler = v -> {
        // Build the Google ID Option
        // GOOGLE_OAUTH_CLIENT_ID should be stored in local.properties
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // Launch the flow asynchronously
        mCredentialManager.getCredentialAsync(
                this,
                request,
                null, // CancellationSignal
                mExecutor, // Execute on background executor or Context.getMainExecutor(this)
                new androidx.credentials.CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignInSuccess(result);
                    }

                    @Override
                    public void onError(@NonNull androidx.credentials.exceptions.GetCredentialException e) {
                        if (e instanceof androidx.credentials.exceptions.NoCredentialException) {
                            // This specific error usually means:
                            // 1. No Google Account on phone
                            // 2. Or SHA-1 mismatch in Cloud Console
                            Log.e("MainMenu", "No credentials available. Check SHA-1 in Console.", e);
                            runOnUiThread(() -> Toast.makeText(MainMenu.this, "Configuration Error: Check Logcat for details", Toast.LENGTH_LONG).show());
                        } else {
                            Log.e("MainMenu", "Sign-in failed", e);
                            runOnUiThread(() -> Toast.makeText(MainMenu.this, "Sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }
                }
        );
    };

    private void handleSignInSuccess(GetCredentialResponse result) {
        Credential credential = result.getCredential();

        if (credential instanceof CustomCredential) {
            CustomCredential customCredential = (CustomCredential) credential;
            try {
                GoogleIdTokenCredential googleId = GoogleIdTokenCredential.createFrom(customCredential.getData());
                String email = googleId.getId(); // Or extract email if distinct from ID

                runOnUiThread(() -> {
                    Toast.makeText(MainMenu.this, "Signed in as: " + email, Toast.LENGTH_SHORT).show();
                    // Show the Opt-In Dialog instead of syncing immediately
                    String savedEmail = new BookCataloguePreferences().getAccountEmail();
                    if (savedEmail.isEmpty()) {
                        showOptInDialog(email);
                    } else {
                        Toast.makeText(this, "Starting Cloud Backup...", Toast.LENGTH_SHORT).show();
                        BookCatalogueAPI.performCloudSync(email, new BookCataloguePreferences().getAccountOptIn(), new BookCatalogueAPITask.SyncListener() {
                            @Override
                            public void onSyncProgress(int current, int total) {
                                mSyncProgressBar.setMax(total);
                                mSyncProgressBar.setProgress(current);
                                mSyncProgressBar.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onSyncComplete(String message) {
                                mSyncProgressBar.setVisibility(View.GONE);
                                Toast.makeText(MainMenu.this, message, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onSyncError(String error) {
                                mSyncProgressBar.setVisibility(View.GONE);
                                Toast.makeText(MainMenu.this, "Backup Failed: " + error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });

            } catch (Exception e) {
                Log.e("MainMenu", "Invalid credential data", e);
                runOnUiThread(() -> Toast.makeText(MainMenu.this, "Invalid credential data", Toast.LENGTH_SHORT).show());
            }
        } else {
            Log.e("MainMenu", "Unexpected credential type: " + credential.getClass().getName());
        }
    }

    /**
     * Shows a dialog asking the user to opt-in to sharing data.
     */
    private void showOptInDialog(final String email) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.title_enhance_search)
                .setMessage(R.string.para_enhance_search)
                .setPositiveButton("Yes, I'll help", (dialog, which) -> savePreferencesAndSync(email, true))
                .setNegativeButton("No, keep private", (dialog, which) -> savePreferencesAndSync(email, false))
                .setCancelable(false) // Force them to choose
                .show();
    }

    /**
     * Saves the user's choice and email, then triggers the sync.
     */
    private void savePreferencesAndSync(String email, boolean optIn) {
        // Save to Preferences
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        prefs.setAccountEmail(email);
        prefs.setAccountOptIn(optIn);

        // Notify user
        String status = optIn ? "Opted In" : "Opted Out";
        Toast.makeText(this, "Preferences Saved: " + status, Toast.LENGTH_SHORT).show();

        // Pass the optIn boolean to the sync method so it sends the correct flag to the API
        Toast.makeText(this, "Starting Cloud Backup...", Toast.LENGTH_SHORT).show();
        BookCatalogueAPI.performCloudSync(email, optIn, new BookCatalogueAPITask.SyncListener() {
            @Override
            public void onSyncProgress(int current, int total) {
                mSyncProgressBar.setMax(total);
                mSyncProgressBar.setProgress(current);
                mSyncProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onSyncComplete(String message) {
                mSyncProgressBar.setVisibility(View.GONE);
                Toast.makeText(MainMenu.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onSyncError(String error) {
                mSyncProgressBar.setVisibility(View.GONE);
                Toast.makeText(MainMenu.this, "Backup Failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Add Book Menu Handler
     */
    private final OnClickListener mAddBookHandler = new OnClickListener() {
        @Override
        public void onClick(View v) {
            ArrayList<AlertDialogItem> items = new ArrayList<>();
            items.add(new AlertDialogItem(getString(R.string.label_scan_barcode_isbn), mCreateBookScan));
            items.add(new AlertDialogItem(getString(R.string.label_enter_isbn), mCreateBookIsbn));
            items.add(new AlertDialogItem(getString(R.string.label_search_internet), mCreateBookName));
            items.add(new AlertDialogItem(getString(R.string.label_add_manually), mCreateBookManually));
            AlertDialogUtils.showContextDialogue(MainMenu.this, getString(R.string.title_add_book), items);
        }
    };

    /**
     * Search Menu Handler
     */
    private final OnClickListener mSearchHandler = v -> {
        Intent i = new Intent(MainMenu.this, Library.class);
        i.putExtra("com.eleybourn.bookcatalogue.START_SEARCH", true);
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
        Intent i = new Intent(MainMenu.this, Library.class);
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
