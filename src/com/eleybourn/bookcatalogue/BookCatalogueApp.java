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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build.VERSION;

import com.eleybourn.bookcatalogue.booklist.AdminLibraryPreferences;
import com.eleybourn.bookcatalogue.utils.Terminator;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.acra.ACRA;
import org.acra.ErrorReporter;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSenderException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.CUSTOM_DATA;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_COMMENT;
import static org.acra.ReportField.USER_CRASH_DATE;

/**
 * BookCatalogue Application implementation. Useful for making globals available
 * and for being a central location for logically application-specific objects such
 * as preferences.
 * 
 * @author Philip Warner
 *
 */
@ReportsCrashes(formKey = "", // will not be used
	mailTo = "philip.warner@rhyme.com.au,eleybourn@gmail.com",
	mode = ReportingInteractionMode.DIALOG,
	customReportContent = { USER_COMMENT, USER_APP_START_DATE, USER_CRASH_DATE, APP_VERSION_NAME, APP_VERSION_CODE, ANDROID_VERSION, PHONE_MODEL, CUSTOM_DATA, STACK_TRACE },
	//optional, displayed as soon as the crash occurs, before collecting data which can take a few seconds
	resToastText = R.string.crash_toast_text, 
	resNotifTickerText = R.string.crash_notif_ticker_text,
	resNotifTitle = R.string.crash_notif_title,
	resNotifText = R.string.crash_notif_text,
	resNotifIcon = R.drawable.ic_alert_warning, // optional. default is a warning sign
	resDialogText = R.string.crash_dialog_text,
	resDialogIcon = R.drawable.ic_menu_info, //optional. default is a warning sign
	resDialogTitle = R.string.crash_dialog_title, // optional. default is your application name
	resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. when defined, adds a user text field input with this text resource as a label
	resDialogOkToast = R.string.crash_dialog_ok_toast // optional. displays a Toast message when the user accepts to send a report.
)

public class BookCatalogueApp extends Application {
	
	/** Not sure this is a good idea. Stores the Application context once created */
	public static Context context = null;

	/** Flag indicating the collation we use in the current database is case-sensitive */
	private static Boolean mCollationCaseSensitive = null;

    private static BcQueueManager mQueueManager = null;

	/** The locale used at startup; so that we can revert to system locale if we want to */
	private static final Locale mInitialLocale = Locale.getDefault();
	@SuppressLint("ConstantLocale")
	// User-specified default locale
	private static Locale mPreferredLocale = null;

	/**
	 * There seems to be something fishy in creating locales from full names (like en_AU),
	 * so we split it and process it manually.
	 *
	 * @param name  Locale name (eg. 'en_AU')
	 *
	 * @return  Locale corresponding to passed name
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
			l = Locale.of(parts[0]);
		} else if (parts.length ==   2) {
			l = Locale.of(parts[0], parts[1]);
		} else {
			l = Locale.of(parts[0], parts[1], parts[2]);
		}
		return l;
	}

    public static class BcReportSender extends org.acra.sender.EmailIntentSender {

		public BcReportSender(Context ctx) {
			super(ctx);
		}

		@Override
	    public void send(CrashReportData report) throws ReportSenderException {
			//report.put(USER_COMMENT, report.get(USER_COMMENT) + "\n\n" + Tracker.getEventsInfo());
			super.send(report);
	    }
	}

	/**
	 * Most real initialization should go here, since before this point, the App is still
	 * 'Under Construction'.
	 */
	@Override
	public void onCreate() {

		context = setLocale(getApplicationContext());

		Terminator.init();
		// The following line triggers the initialization of ACRA
        ACRA.init(this);
        BcReportSender bcSender = new BcReportSender(this);
        ErrorReporter.getInstance().setReportSender(bcSender);

        // Save the app signer
        ErrorReporter.getInstance().putCustomData("Signed-By", Utils.signedBy(this));

		// Start the queue manager
		if (mQueueManager == null)
			mQueueManager = new BcQueueManager(this.getApplicationContext());

		super.onCreate();

		applyLocaleSettings();

		// Watch the preferences and handle changes as necessary
		//BookCataloguePreferences ap = getPreferences();
		SharedPreferences p = BookCataloguePreferences.getSharedPreferences();

		p.registerOnSharedPreferenceChangeListener(mPrefsListener);
	}

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

	private void applyLocaleSettings() {
		context = setLocale(getBaseContext());
		//applyPreferredLocaleIfNecessary(getBaseContext().getResources());
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
				try { l.onLocaleChanged(); } catch (Exception e) { /* Ignore */ }
		}
		for(WeakReference<OnLocaleChangedListener> ref: toRemove) {
			mOnLocaleChangedListeners.remove(ref);
		}
	}

	/**
	 * Add a new OnLocaleChangedListener, and cleanup any dead references.
	 */
	public static void registerOnLocaleChangedListener(OnLocaleChangedListener listener) {
		ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

		boolean alreadyAdded = false;

		for(WeakReference<OnLocaleChangedListener> ref: mOnLocaleChangedListeners) {
			OnLocaleChangedListener l = ref.get();
			if (l == null)
				toRemove.add(ref);
			else if (l == listener)
				alreadyAdded = true;
		}
		if (!alreadyAdded)
			mOnLocaleChangedListeners.add(new WeakReference<>(listener));

		for(WeakReference<OnLocaleChangedListener> ref: toRemove) {
			mOnLocaleChangedListeners.remove(ref);
		}
	}

	/**
	 * Remove the passed OnLocaleChangedListener, and cleanup any dead references.
	 */
	public static void unregisterOnLocaleChangedListener(OnLocaleChangedListener listener) {
		ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

		for(WeakReference<OnLocaleChangedListener> ref: mOnLocaleChangedListeners) {
			OnLocaleChangedListener l = ref.get();
			if ( (l == null) || (l == listener) )
				toRemove.add(ref);
		}
		for(WeakReference<OnLocaleChangedListener> ref: toRemove) {
			mOnLocaleChangedListeners.remove(ref);
		}
	}

	/** Set of OnLocaleChangedListeners */
	private static final HashSet<WeakReference<OnLocaleChangedListener>> mOnLocaleChangedListeners = new HashSet<>();

	/**
	 * Interface definition
	 */
	public interface OnLocaleChangedListener {
		void onLocaleChanged();
	}

	/**
	 * Check if sqlite collation is case sensitive; cache the result.
	 * This bug was introduced in ICS and present in 4.0-4.0.3, at least that meant that
	 * UNICODE collation became CS. We now use a LOCALIZED Collation, but still check if CI.
	 * 
	 * @param db	Any sqlite database connection
	 * 
	 * @return	Flag indicating 'Collate <our-collation>' is broken.
	 */
	public static boolean isCollationCaseSensitive(SQLiteDatabase db) {
		if (mCollationCaseSensitive == null)
			mCollationCaseSensitive = CollationCaseSensitive.isCaseSensitive(db);
		return mCollationCaseSensitive;
	}

    /**
	 * Utility routine to get the current QueueManager.
	 * 
	 * @return	QueueManager object
	 */
	public static BcQueueManager getQueueManager() {
		return mQueueManager;
	}

	/**
	 * Wrapper to reduce explicit use of the 'context' member.
	 * 
	 * @param resId		Resource ID
	 * 
	 * @return			Localized resource string
	 */
	public static String getResourceString(int resId) {
		return context.getString(resId);
	}

	/**
	 * Wrapper to reduce explicit use of the 'context' member.
	 * 
	 * @param resId		Resource ID
	 * 
	 * @return			Localized resource string
	 */
	public static String getResourceString(int resId, Object...objects) {
		return context.getString(resId, objects);
	}

	/**
	 * Utility routine to return as BookCataloguePreferences object.
	 * 
	 * @return	Application preferences object.
	 */
	public static BookCataloguePreferences getAppPreferences() {
		return new BookCataloguePreferences();
	}

	public static boolean hasPermission(String permission) {
		return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
	}

	/**
	 * Return the Intent that will be used by the notifications manager when a notification
	 * is clicked; should bring the app to the foreground.
	 */
	public static Intent getAppToForegroundIntent(Context c) {
		Intent i = new Intent (c, StartupActivity.class );
		i.setAction("android.intent.action.MAIN");
		i.addCategory(Intent.CATEGORY_LAUNCHER);
		// No idea what to do with this!
		i.putExtra("bringFg", true);
		return i;
	}

	public static void startPreferencesActivity(Activity a) {
		Intent i = new Intent(a, AdminLibraryPreferences.class);
		a.startActivity(i);
	}

	@Override
	protected void attachBaseContext(Context base) {
		// We need a context in order to get prefs
		context = base;
		// Find out preferred locale and set it
		context = setLocale(base);
		super.attachBaseContext(context);
	}

	/**
     * Get the current preferred locale, or null
     *
     * @return  locale, or null
     */
    public static Locale getPreferredLocale() {
        return mPreferredLocale;
    }

	///**
	// * Set the current preferred locale in the passed resources.
	// *
	// * @param res   Resources to use
	// * @return  true if it was actually changed
	// */
    public static Context setLocale(Context context) {
		String prefLocaleName = getAppPreferences().getString(BookCataloguePreferences.PREF_APP_LOCALE, null);
		// If we have a preference, set it
		if (prefLocaleName != null && !prefLocaleName.isEmpty()) {
			mPreferredLocale = localeFromName(prefLocaleName);
		} else {
			mPreferredLocale = getSystemLocale();
		}

		Locale.setDefault(mPreferredLocale);
		Resources resources = context.getResources();
		Configuration configuration = resources.getConfiguration();

		if (VERSION.SDK_INT >= 24) {
			configuration.setLocale(mPreferredLocale);
			configuration.setLayoutDirection(mPreferredLocale);

			return context.createConfigurationContext(configuration);

		} else {
			configuration.locale = mPreferredLocale;
			configuration.setLayoutDirection(mPreferredLocale);
			resources.updateConfiguration(configuration, resources.getDisplayMetrics());

			return context;
		}
	}

    /**
     * Monitor configuration changes (like rotation) to make sure we reset the
     * locale.
     *
     * @param newConfig		The new configuration
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
		context = setLocale(getBaseContext());
    }

    /** List of supported locales */
    private static ArrayList<String> mSupportedLocales = null;

    /**
     * Get the list of supported locale names
     *
     * @return  ArrayList of locale names
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
    	return mInitialLocale;
    }
}
