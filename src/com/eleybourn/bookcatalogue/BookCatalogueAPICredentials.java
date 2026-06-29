package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

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

        // Ensure we are using an Activity context as required by CredentialManager for UI
        if (!(mContext instanceof android.app.Activity)) {
            String error = "Login failed: Activity context required.";
            Log.e("APICredentials", error);
            if (mListener != null) mListener.onCredentialError(error);
            return;
        }

        String clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID;

        // Build the Google ID Option
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false) // Force picker to avoid issues on some devices (like Samsung)
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
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        String email = handleSignInSuccess(result);
                        if (email != null && !email.isEmpty()) {
                            // Post UI work to the main thread
                            mMainThreadHandler.post(() -> showOptInDialog(email));
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.e("APICredentials", "Sign-in failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                        
                        if (e instanceof NoCredentialException) {
                            // Fallback for Samsung/specialized devices where Credential Manager is blocked by Autofill settings
                            tryLegacySignIn();
                            return;
                        }

                        String errorMessage;
                        if (e instanceof GetCredentialCancellationException) {
                            errorMessage = "Sign-in cancelled.";
                        } else {
                            errorMessage = "Sign-in failed: " + e.getMessage();
                        }

                        if (mListener != null) {
                            final String finalMessage = errorMessage;
                            mMainThreadHandler.post(() -> mListener.onCredentialError(finalMessage));
                        }
                    }
                }
        );
    }

    /**
     * Legacy Google Sign-In as a fallback for devices where Credential Manager fails.
     */
    private void tryLegacySignIn() {
        if (!(mContext instanceof android.app.Activity)) return;
        android.app.Activity activity = (android.app.Activity) mContext;

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        // Silent sign-in approach which works if they are already signed into the phone.
        GoogleSignIn.getClient(activity, gso).silentSignIn().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                GoogleSignInAccount account = task.getResult();
                if (account != null && account.getEmail() != null) {
                    final String email = account.getEmail();
                    mMainThreadHandler.post(() -> showOptInDialog(email));
                }
            } else {
                // If silent fails, we have to show the error.
                if (mListener != null) {
                    mMainThreadHandler.post(() -> mListener.onCredentialError("No Google accounts found. Please check your device account settings."));
                }
            }
        });
    }
    /**
     * Handles a successful sign-in response, extracts the ID token, and passes it to the listener.
     */
    private String handleSignInSuccess(GetCredentialResponse result) {
        Credential credential = result.getCredential();

        if (credential instanceof CustomCredential) {
            CustomCredential customCredential = (CustomCredential) credential;
            try {
                // The CustomCredential's 'data' Bundle contains the Google ID token.
                GoogleIdTokenCredential googleId = GoogleIdTokenCredential.createFrom(customCredential.getData());
                return googleId.getEmail();
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
        return "";
    }

    /**
     * Shows a dialogue asking the user to opt in to sharing data.
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
