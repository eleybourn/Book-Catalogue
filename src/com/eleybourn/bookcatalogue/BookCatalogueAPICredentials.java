package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

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
                        errorMessage = "Sign-in failed: " + e.getMessage();
                        showToast("Sign-in failed");
                        if (mListener != null) {
                            mListener.onCredentialError(errorMessage);
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
                Log.d("APICredentials", "Successfully obtained ID token for: " + email);
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
        new AlertDialog.Builder(mContext)
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
