package com.eleybourn.bookcatalogue;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Handles the Google Sign-In flow using Credential Manager to retrieve a user credential
 * for API authentication.
 */
@SuppressWarnings("deprecation")
public class BookCatalogueAPICredentials {

    private final CredentialManager mCredentialManager;
    private final Context mContext;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private CredentialListener mListener;
    private ActivityResultLauncher<Intent> mLegacySignInLauncher;
    private boolean mIsFallbackAttempted = false;

    public BookCatalogueAPICredentials(Context context) {
        this.mContext = context;
        this.mCredentialManager = CredentialManager.create(context);
    }

    public void setLegacySignInLauncher(ActivityResultLauncher<Intent> launcher) {
        this.mLegacySignInLauncher = launcher;
    }

    /**
     * Initiates the Google Sign-In flow to get an ID token.
     *
     * @param listener The callback to be invoked with the result.
     */
    public void getCredentials(CredentialListener listener) {
        this.mListener = listener;
        this.mIsFallbackAttempted = false;

        // Ensure we are using an Activity context as required by SDKs for UI
        if (!(mContext instanceof android.app.Activity)) {
            if (mListener != null) mListener.onCredentialError("Login failed: Activity context required.");
            return;
        }
        android.app.Activity activity = (android.app.Activity) mContext;

        // Before starting any new flow, sign out of any legacy sessions using our specific Client ID
        // to clear stale state and prevent "already logged in" conflicts on some devices (like Samsung).
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
                .build();

        GoogleSignIn.getClient(activity, gso).signOut().addOnCompleteListener(task -> {
            // Now proceed with normal checks and flows
            checkPlayServicesAndStart(activity);
        });
    }

    private void checkPlayServicesAndStart(android.app.Activity activity) {
        // Check Play Services
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(mContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                Objects.requireNonNull(apiAvailability.getErrorDialog(activity, resultCode, 9000)).show();
            } else {
                if (mListener != null) mListener.onCredentialError("Google Play Services are not available on this device.");
            }
            return;
        }

        String clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID;

        // If client ID is missing, skip to legacy flow as GetGoogleIdOption requires it
        if (clientId == null || clientId.isEmpty()) {
            Log.w("APICredentials", "Google Client ID is missing. Falling back to legacy sign-in.");
            mMainThreadHandler.post(this::tryLegacySignIn);
            return;
        }

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
                new androidx.credentials.CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        String email = handleSignInSuccess(result);
                        if (email != null && !email.isEmpty()) {
                            // Post UI work to the main thread
                            mMainThreadHandler.post(() -> showOptInDialog(email));
                        } else {
                            Log.w("APICredentials", "Credential Manager returned success but no email found. Falling back to legacy.");
                            mMainThreadHandler.post(BookCatalogueAPICredentials.this::tryLegacySignIn);
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.e("APICredentials", "Credential Manager failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());

                        // If it's a cancellation, we just stop.
                        if (e instanceof GetCredentialCancellationException) {
                            Log.d("APICredentials", "Credential Manager sign-in cancelled by user.");
                            return;
                        }

                        // For ANY other error (including NoCredentialException), try the legacy fallback on the main thread.
                        mMainThreadHandler.post(BookCatalogueAPICredentials.this::tryLegacySignIn);
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

        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile();

        String clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID;
        if (clientId != null && !clientId.isEmpty()) {
            builder.requestIdToken(clientId);
        }

        GoogleSignInOptions gso = builder.build();
        GoogleSignInClient client = GoogleSignIn.getClient(activity, gso);

        // Try silent first to avoid jarring UI if possible
        client.silentSignIn().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                GoogleSignInAccount account = task.getResult();
                if (account != null && account.getEmail() != null) {
                    final String email = account.getEmail();
                    mMainThreadHandler.post(() -> showOptInDialog(email));
                    return;
                }
            }

            // If silent fails, force the interactive account picker
            if (mLegacySignInLauncher != null) {
                mMainThreadHandler.post(() -> {
                    try {
                        mLegacySignInLauncher.launch(client.getSignInIntent());
                    } catch (Exception e) {
                        Log.e("APICredentials", "Failed to launch legacy sign-in", e);
                        if (mListener != null) mListener.onCredentialError("Unable to start sign-in process.");
                    }
                });
            } else if (mListener != null) {
                mMainThreadHandler.post(() -> mListener.onCredentialError("Unable to find a Google account (Code L1). Please ensure you are signed into Google on this device and your device's date/time is set to Automatic."));
            }
        });
    }

    /**
     * Handles the result from the legacy sign-in activity.
     */
    public void handleLegacySignInResult(Intent data) {
        // If the result is from the AccountManager fallback, it will contain the email directly.
        if (data != null && data.hasExtra(AccountManager.KEY_ACCOUNT_NAME)) {
            String email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (email != null && !email.isEmpty()) {
                mMainThreadHandler.post(() -> showOptInDialog(email));
                return;
            }
        }

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && account.getEmail() != null) {
                final String email = account.getEmail();
                mMainThreadHandler.post(() -> showOptInDialog(email));
            }
        } catch (ApiException e) {
            int statusCode = e.getStatusCode();

            // SPECIAL FALLBACK: If we got a Code 10 (Developer Error), it's likely a token mismatch.
            // Try the AccountManager fallback which doesn't require Client ID registration.
            if (statusCode == 10 && mLegacySignInLauncher != null && !mIsFallbackAttempted) {
                Log.w("APICredentials", "Legacy sign-in failed with Code 10. Attempting AccountManager fallback.");
                mIsFallbackAttempted = true;
                tryAccountManagerFallback();
                return;
            }

            Log.e("APICredentials", "Legacy sign-in failed: Status Code " + statusCode, e);

            String message;
            switch (statusCode) {
                case 10: // DEVELOPER_ERROR
                    message = "Sign-in configuration error (Code 10). Please ensure your device's date/time is correct and the app is correctly registered in Google Cloud.";
                    break;
                case 7: // NETWORK_ERROR
                    message = "Network error. Please check your internet connection.";
                    break;
                case 12500: // SIGN_IN_FAILED
                    message = "Google Sign-In failed (Code 12500). Please ensure your Google Play Services are enabled and updated.";
                    break;
                case 12501: // SIGN_IN_CANCELLED
                    Log.d("APICredentials", "Legacy sign-in cancelled by user.");
                    return;
                default:
                    message = "Sign-in failed (Error " + statusCode + "). Please check your Google account settings.";
                    break;
            }
            if (mListener != null) {
                final String finalMessage = message;
                mMainThreadHandler.post(() -> mListener.onCredentialError(finalMessage));
            }
        }
    }

    /**
     * A fallback that uses the device's Account Manager to let the user pick a Google account.
     * This bypasses all OAuth/Client ID requirements and only retrieves the email address.
     */
    private void tryAccountManagerFallback() {
        if (!(mContext instanceof android.app.Activity)) {
            if (mListener != null) mListener.onCredentialError("Login failed: Activity context required.");
            return;
        }
        if (mLegacySignInLauncher == null) {
            if (mListener != null) mListener.onCredentialError("Login failed: Sign-in launcher not initialized.");
            return;
        }

        // Create the system intent to choose an account
        Intent intent = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.google"}, false, null, null, null, null);

        mMainThreadHandler.post(() -> {
            try {
                mLegacySignInLauncher.launch(intent);
            } catch (Exception e) {
                Log.e("APICredentials", "Failed to launch AccountManager choice intent", e);
                if (mListener != null) mListener.onCredentialError("Unable to start account selection process.");
            }
        });
    }

    /**
     * Fallback to using AccountManager directly to get the email of a Google account on the device.
     */
    public void getDeviceAccount(CredentialListener listener) {
        this.mListener = listener;
        this.mIsFallbackAttempted = true;

        // On modern Android (API 30+), getAccountsByType() is restricted and requires GET_ACCOUNTS permission,
        // which is no longer recommended. Using newChooseAccountIntent() is the correct, privacy-respecting
        // way to let the user pick an account and share its name with the app.
        tryAccountManagerFallback();
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
                .setNeutralButton(R.string.button_cancel, null)
                .setCancelable(true)
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
