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

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.CUSTOM_DATA;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_COMMENT;
import static org.acra.ReportField.USER_CRASH_DATE;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;

import androidx.core.content.ContextCompat;

import com.eleybourn.bookcatalogue.booklist.AdminLibraryPreferences;
import com.eleybourn.bookcatalogue.utils.LocaleManager;
import com.eleybourn.bookcatalogue.utils.Terminator;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.ToastConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.data.StringFormat;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * BookCatalogue Application implementation. Useful for making globals available
 * and for being a central location for logically application-specific objects such
 * as preferences.
 *
 * @author Philip Warner
 *
 */
public class BookCatalogueApp extends Application {
    /**
     * Set of OnLocaleChangedListeners
     */
    private static final HashSet<WeakReference<OnLocaleChangedListener>> mOnLocaleChangedListeners = new HashSet<>();
    public static File externalCacheDir;
    public static File externalFilesDir;
    public static File filesDir;
    public static SharedPreferences mPrefs;
    private static Resources res;
    /**
     * Flag indicating the collation we use in the current database is case-sensitive
     */
    private static Boolean mCollationCaseSensitive = null;
    private BcQueueManager mQueueManager = null;
    // User-specified default locale
    private static final Locale mPreferredLocale = null;
    /**
     * List of supported locales
     */
    private static ArrayList<String> mSupportedLocales = null;
    /**
     * Shared Preferences Listener
     * <p>
     * Currently it just handles Locale changes and propagates it to any listeners.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener = (sharedPreferences, key) -> {
        if (key != null && key.equals(BookCataloguePreferences.PREF_APP_LOCALE)) {
            applyLocaleSettings();
        }
    };

    /**
     * There seems to be something fishy in creating locales from full names (like en_AU),
     * so we split it and process it manually.
     *
     * @param name Locale name (eg. 'en_AU')
     * @return Locale corresponding to passed name
     */
    public static Locale localeFromName(String name) {
        String[] parts;
        if (name.contains("_")) {
            parts = name.split("_");
        } else {
            parts = name.split("-");
        }
        Locale l;
        if (parts.length == 1) {
            l = new Locale(parts[0]);
        } else if (parts.length == 2) {
            l = new Locale(parts[0], parts[1]);
        } else {
            l = new Locale(parts[0], parts[1], parts[2]);
        }
        return l;
    }

    /**
     * Add a new OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void registerOnLocaleChangedListener(OnLocaleChangedListener listener) {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        boolean alreadyAdded = false;

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener l = ref.get();
            if (l == null)
                toRemove.add(ref);
            else if (l == listener)
                alreadyAdded = true;
        }
        if (!alreadyAdded)
            mOnLocaleChangedListeners.add(new WeakReference<>(listener));

        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Remove the passed OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void unregisterOnLocaleChangedListener(OnLocaleChangedListener listener) {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener l = ref.get();
            if ((l == null) || (l == listener))
                toRemove.add(ref);
        }
        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Check if sqlite collation is case sensitive; cache the result.
     * This bug was introduced in ICS and present in 4.0-4.0.3, at least that meant that
     * UNICODE collation became CS. We now use a LOCALIZED Collation, but still check if CI.
     *
     * @param db Any sqlite database connection
     * @return Flag indicating 'Collate <our-collation>' is broken.
     */
    public static boolean isCollationCaseSensitive(SQLiteDatabase db) {
        if (mCollationCaseSensitive == null)
            mCollationCaseSensitive = CollationCaseSensitive.isCaseSensitive(db);
        return mCollationCaseSensitive;
    }

    /**
     * Utility routine to return as BookCataloguePreferences object.
     *
     * @return Application preferences object.
     */
    public static BookCataloguePreferences getAppPreferences() {
        return new BookCataloguePreferences();
    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static void startPreferencesActivity(Activity a) {
        Intent i = new Intent(a, AdminLibraryPreferences.class);
        a.startActivity(i);
    }

    /**
     * Get the current preferred locale, or null
     *
     * @return locale, or null
     */
    public static Locale getPreferredLocale() {
        return mPreferredLocale;
    }

    /**
     * Get the list of supported locale names
     *
     * @return ArrayList of locale names
     */
    public static ArrayList<String> getSupportedLocales() {
        if (mSupportedLocales == null) {
            mSupportedLocales = new ArrayList<>();
            mSupportedLocales.add("de_DE");
            mSupportedLocales.add("en_AU");
            mSupportedLocales.add("es_ES");
            mSupportedLocales.add("fr_FR");
            mSupportedLocales.add("it_IT");
            mSupportedLocales.add("nl_NL");
            mSupportedLocales.add("ru_RU");
            mSupportedLocales.add("tr_TR");
            mSupportedLocales.add("el_GR");
        }
        return mSupportedLocales;
    }

    public static Locale getSystemLocale() {
        // Return the current system default from Resources.
        // This is more reliable than Locale.getDefault() in some edge cases.
        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    public static Resources getRes() {
        return res;
    }

    /**
     * Most real initialization should go here, since before this point, the App is still
     * 'Under Construction'.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        externalCacheDir = getExternalCacheDir();
        externalFilesDir = getExternalFilesDir(null);
        filesDir = getFilesDir();
        mPrefs = getSharedPreferences("bookCatalogue", BookCatalogueApp.MODE_PRIVATE);
        applyLocaleSettings();
        res = getResources();

        Terminator.init();

        // *** START ACRA CONFIGURATION ***
        CoreConfigurationBuilder builder = new CoreConfigurationBuilder()
                // Core ACRA settings
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.KEY_VALUE_LIST)
                // Customize the report content
                .withReportContent(
                        USER_COMMENT, USER_APP_START_DATE, USER_CRASH_DATE, APP_VERSION_NAME,
                        APP_VERSION_CODE, ANDROID_VERSION, PHONE_MODEL, CUSTOM_DATA, STACK_TRACE
                )
                // Configure the Toast notification
                .withPluginConfigurations(
                        new ToastConfigurationBuilder()
                                .withText(this.getString(R.string.crash_toast_text))
                                .build(),
                        // Configure the email sender
                        new DialogConfigurationBuilder()
                                .withTitle(this.getString(R.string.crash_dialog_title)) // Title of the dialog
                                .withText(this.getString(R.string.crash_dialog_text))   // Main message
                                .withPositiveButtonText(this.getString(R.string.crash_dialog_ok_toast)) // "Send Report" button
                                .withNegativeButtonText(this.getString(R.string.button_cancel)) // "Cancel" button
                                .build(),
                        // If the user agrees, this configuration will be used to send the email
                        new MailSenderConfigurationBuilder()
                                .withMailTo("philip.warner@rhyme.com.au,eleybourn@gmail.com")
                                .withReportAsFile(true)
                                .withSubject(this.getString(R.string.crash_dialog_title))
                                .withBody(this.getString(R.string.crash_dialog_text))
                                .build()
                );

        // Initialize ACRA
        ACRA.init(this, builder);
        // *** END ACRA CONFIGURATION ***
        // Save the app signer
        ACRA.getErrorReporter().putCustomData("Signed-By", Utils.signedBy(this));

        // Start the queue manager
        if (mQueueManager == null)
            mQueueManager = new BcQueueManager(this.getApplicationContext());

        // Watch the preferences and handle changes as necessary
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
    }

    private void applyLocaleSettings() {
        // Get your saved language preference (e.g., "fr-FR", "es-ES")
        String prefLocaleName = getAppPreferences().getString(BookCataloguePreferences.PREF_APP_LOCALE, "");

        if (prefLocaleName != null && !prefLocaleName.isEmpty()) {
            // Use the new centralized LocaleManager
            LocaleManager.setAppLocale(prefLocaleName.replace("_", "-"));
        } else {
            // To revert to the system default, call with an empty or null string.
            // Let's modify LocaleManager slightly to handle this cleanly.
            LocaleManager.setAppLocale(""); // An empty tag list results in using system default.
        }

        // You no longer need to manually update the context here.
        // The system handles recreating activities.
        // You can still notify your listeners if they need to update non-activity components.
        notifyLocaleChanged();
    }

    /**
     * Send a message to all registered OnLocaleChangedListeners, and cleanup any dead references.
     */
    private void notifyLocaleChanged() {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener l = ref.get();
            if (l == null)
                toRemove.add(ref);
            else
                try {
                    l.onLocaleChanged();
                } catch (Exception e) { /* Ignore */ }
        }
        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Interface definition
     */
    public interface OnLocaleChangedListener {
        void onLocaleChanged();
    }
}
