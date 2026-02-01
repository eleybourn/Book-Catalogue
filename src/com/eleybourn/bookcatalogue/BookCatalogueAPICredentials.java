package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Handles the Google Sign-In flow using Credential Manager to retrieve a user credential
 * for API authentication.
 */
public class BookCatalogueAPICredentials {

    private final CredentialManager mCredentialManager;
    private final Context mContext;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private CredentialListener mListener;

    public BookCatalogueAPICredentials(Context context) {
        this.mContext = context;
        this.mCredentialManager = CredentialManager.create(context);
    }

    /**
     * Initiates the Google Sign-In flow to get an ID token.
     *
     * @param listener The callback to be invoked with the result.
     */
    public void getCredentials(CredentialListener listener) {
        this.mListener = listener;

        // Build the Google ID Option
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
                mContext,
                request,
                null, // CancellationSignal
                mExecutor,
                new androidx.credentials.CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        String email = handleSignInSuccess(result);
                        if (email != null && !email.isEmpty()) {
                            showToast("Signed in as " + email);
                            // Post UI work to the main thread
                            mMainThreadHandler.post(() -> showOptInDialog(email));
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        String errorMessage;
                        Log.e("APICredentials", "Sign-in failed", e);
                        // Check for specific exception types to provide a better user experience
                        if (e instanceof GetCredentialCancellationException) {
                            // User cancelled the sign-in flow, often no message is needed
                            errorMessage = "Sign-in cancelled by user.";
                            // You might choose not to show a toast in this case
                        } else if (e instanceof NoCredentialException) {
                            // This is expected if the user has no saved credentials
                            errorMessage = "No accounts found on this device. Please add a Google account.";
                            showToast(errorMessage);
                        } else {
                            // For all other errors, show a generic failure message
                            errorMessage = "Sign-in failed: " + e.getMessage();
                            showToast("Sign-in failed");
                        }

                        if (mListener != null) {
                            // Only report more critical errors, or handle cancellation silently
                            if (!(e instanceof GetCredentialCancellationException)) {
                                mListener.onCredentialError(errorMessage);
                            }
                        }
                    }
                }
        );
    }

    /**
     * Safely shows a Toast on the UI thread.
     * @param message The text to display.
     */
    @SuppressWarnings("SameParameterValue")
    private void showToast(final String message) {
        mMainThreadHandler.post(() -> Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Handles a successful sign-in response, extracts the ID token, and passes it to the listener.
     */
    private String handleSignInSuccess(GetCredentialResponse result) {
        String email = "";
        Credential credential = result.getCredential();

        if (credential instanceof CustomCredential) {
            CustomCredential customCredential = (CustomCredential) credential;
            try {
                // The CustomCredential's 'data' Bundle contains the Google ID token.
                GoogleIdTokenCredential googleId = GoogleIdTokenCredential.createFrom(customCredential.getData());
                //String idToken = googleId.getIdToken();
                email = googleId.getId(); // Or extract email if distinct from ID
            } catch (Exception e) {
                Log.e("APICredentials", "Failed to create GoogleIdTokenCredential from data", e);
                if (mListener != null) {
                    mListener.onCredentialError("Invalid credential data received.");
                }
            }
        } else {
            String unexpectedType = credential.getClass().getName();
            Log.e("APICredentials", "Unexpected credential type: " + unexpectedType);
            if (mListener != null) {
                mListener.onCredentialError("Unexpected credential type: " + unexpectedType);
            }
        }
        return email;
    }

    /**
     * Shows a dialog asking the user to opt-in to sharing data.
     */
    private void showOptInDialog(final String email) {
        new MaterialAlertDialogBuilder(mContext)
                .setTitle(R.string.title_enhance_search)
                .setMessage(R.string.para_enhance_search)
                .setPositiveButton("Yes, I'll help", (dialog, which) -> savePreferences(email, true))
                .setNegativeButton("No, keep private", (dialog, which) -> savePreferences(email, false))
                .setCancelable(false) // Force them to choose
                .show();
    }

    /**
     * Saves the user's choice and email, then triggers the sync.
     */
    private void savePreferences(String email, boolean optIn) {
        // Save to Preferences
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        prefs.setAccountEmail(email);
        prefs.setAccountOptIn(optIn);

        // Now that preferences are saved, notify the listener to trigger the UI reload.
        if (mListener != null) {
            mMainThreadHandler.post(mListener::onCredentialReceived);
        }
    }

    /**
     * Listener interface to handle the result of the credential request.
     */
    public interface CredentialListener {
        void onCredentialReceived();

        void onCredentialError(String errorMessage);
    }

}
