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

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
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
	resNotifIcon = android.R.drawable.stat_notify_error, // optional. default is a warning sign
	resDialogText = R.string.crash_dialog_text,
	resDialogIcon = android.R.drawable.ic_dialog_info, //optional. default is a warning sign
	resDialogTitle = R.string.crash_dialog_title, // optional. default is your application name
	resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. when defined, adds a user text field input with this text resource as a label
	resDialogOkToast = R.string.crash_dialog_ok_toast // optional. displays a Toast message when the user accepts to send a report.
)

public class BookCatalogueApp extends Application {
	
	/** Not sure this is a good idea. Stores the Application context once created */
	public static Context context = null;

	/** Flag indicating the collation we use in the current database is case-sensitive */
	private static Boolean mCollationCaseSensitive = null;
	
	/** Used to sent notifications regarding tasks */
	private static NotificationManager mNotifier;

	private static BcQueueManager mQueueManager = null;

    /** The locale used at startup; so that we can revert to system locale if we want to */
    private static Locale mInitialLocale = null;
    /** User-specified default locale */
    private static Locale mPreferredLocale = null;

	/**
	 * Constructor.
	 */
	public BookCatalogueApp() {
		super();

	}

	public class BcReportSender extends org.acra.sender.EmailIntentSender {

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
    	// Don't rely on the the context until now...
		BookCatalogueApp.context = this.getApplicationContext();

        // Get the preferred locale as soon as possible
		try {
			// Save the original locale
			mInitialLocale = Locale.getDefault();
			// See if user has set a preference
			String prefLocale = getAppPreferences().getString(BookCataloguePreferences.PREF_APP_LOCALE, null);
			//prefLocale = "ru";
			// If we have a preference, set it
			if (prefLocale != null && !prefLocale.equals("")) {
		        mPreferredLocale = new Locale(prefLocale);
		        applyPreferredLocaleIfNecessary(getBaseContext().getResources());
			}
		} catch (Exception e) {
			// Not much we can do...we want locale set early, but not fatal if it fails.
			Logger.logError(e);
		}

		Terminator.init();
		// The following line triggers the initialization of ACRA
        ACRA.init(this);
        BcReportSender bcSender = new BcReportSender(this);
        ErrorReporter.getInstance().setReportSender(bcSender);

        // Save the app signer
        ErrorReporter.getInstance().putCustomData("Signed-By", Utils.signedBy(this));

        // Create the notifier
    	mNotifier = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		// Start the queue manager
		if (mQueueManager == null)
			mQueueManager = new BcQueueManager(this.getApplicationContext());

		super.onCreate();
		
		if (Build.VERSION.SDK_INT < 16) {
			//
			// Avoid possible bug in SQLite which resuts in database being closed without an explicit call. 
			// Based on the grepcode Android sources, it looks like this bug was fixed an/or addressed in
			// 4.1.1, but not in 4.0.4.
			//
			// See:
			//
			//		https://code.google.com/p/android/issues/detail?id=4282
			//	    http://darutk-oboegaki.blogspot.com.au/2011/03/sqlitedatabase-is-closed-automatically.html
			//
			// a pdf of the second link is in 'support' folder. 
			//
			CatalogueDBAdapter dbh = new CatalogueDBAdapter(this);
			dbh.open();
			SQLiteDatabase db = dbh.getDb().getUnderlyingDatabase();
			db.acquireReference();
			if (Build.VERSION.SDK_INT < 8) {
				//
				// RELEASE: REMOVE THIS CODE When MinSDK becomes 8!
				//
				// Android 2.1 has a very nasty bug that can cause un-closed SQLiteStatements to dereference the
				// database when they have not referenced it.. SQLiteStatements can fail to be released in a timely
				// fashion when the screen is rotated, which will then result in an attempt to acess a closed closable.
				// ... so for Android 2.1...we take 1000 references and hope the user won't rotate the screen 1000
				// times while background tasks are running.
				//
				// We have made the best efforts to avoid this bug, this is just insurance.
				//
				// The key instance where this happens is if the GetListTask in BooksOnBookshelf is aborted due to 
				// a screen rotation; the onFinish() method is never called, so the statements are not deleted.
				//
				// We have added finalize() code to SynchronizedStatement so that IF it is called first (not 
				// guaranteed by Java spec) it will close the SQLiteStatement and try to avoid this issue.
				//
				for(int i = 0; i < 1000; i++)
					db.acquireReference();
			}
			dbh.close();
		}

        // Watch the preferences and handle changes as necessary
        //BookCataloguePreferences ap = getPreferences();
        SharedPreferences p = BookCataloguePreferences.getSharedPreferences();
        p.registerOnSharedPreferenceChangeListener(mPrefsListener);
	}

    /**
     * Shared Preferences Listener
     *
     * Currently it just handles Locale changes and propagates it to any listeners.
     */
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(BookCataloguePreferences.PREF_APP_LOCALE)) {
                String prefLocale = getAppPreferences().getString(BookCataloguePreferences.PREF_APP_LOCALE, null);
                //prefLocale = "ru";
                // If we have a preference, set it
                if (prefLocale != null && !prefLocale.equals("")) {
                    mPreferredLocale = new Locale(prefLocale);
                    applyPreferredLocaleIfNecessary(getBaseContext().getResources());
                    notifyLocaleChanged();
                }
            }
        }
    };

    /**
     * Send a message to all registered OnLocaleChangedListeners, and cleanup any dead references.
     */
    private void notifyLocaleChanged() {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<WeakReference<OnLocaleChangedListener>>();

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
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<WeakReference<OnLocaleChangedListener>>();

        boolean alreadyAdded = false;

        for(WeakReference<OnLocaleChangedListener> ref: mOnLocaleChangedListeners) {
            OnLocaleChangedListener l = ref.get();
            if (l == null)
                toRemove.add(ref);
            else if (l == listener)
                alreadyAdded = true;
        }
        if (!alreadyAdded)
            mOnLocaleChangedListeners.add(new WeakReference<OnLocaleChangedListener>(listener));

        for(WeakReference<OnLocaleChangedListener> ref: toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Remove the passed OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void unregisterOnLocaleChangedListener(OnLocaleChangedListener listener) {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<WeakReference<OnLocaleChangedListener>>();

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
    private static HashSet<WeakReference<OnLocaleChangedListener>> mOnLocaleChangedListeners = new HashSet<WeakReference<OnLocaleChangedListener>>();

    /**
     * Interface definition
     */
    public static interface OnLocaleChangedListener {
        public void onLocaleChanged();
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

//	/**
//	 * Currently the QueueManager is implemented as a service. This is not clearly necessary
//	 * but has the huge advantage of making a 'context' object available in the Service
//	 * implementation.
//	 * 
//	 * By binding it here, the service will not die when the last Activity is closed. We
//	 * could call StartService to keep it awake indefinitely also, but we do want the binding
//	 * object...so we bind it.
//	 */
//	private void startQueueManager() {
//		doBindService();		
//	}
//
//	/**
//	 * Points to the bound service, once it is started.
//	 */
//	private static BcQueueManager mBoundService = null;
//
//	/**
//	 * Utility routine to get the current QueueManager.
//	 * 
//	 * @return	QueueManager object
//	 */
//	public static BcQueueManager getQueueManager() {
//		return mBoundService;
//	}

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
	public final static String getResourceString(int resId) {
		return context.getString(resId);
	}

	/**
	 * Wrapper to reduce explicit use of the 'context' member.
	 * 
	 * @param resId		Resource ID
	 * 
	 * @return			Localized resource string
	 */
	public final static String getResourceString(int resId, Object...objects) {
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

	public static boolean isBackgroundImageDisabled() {
		return getAppPreferences().getBoolean(BookCataloguePreferences.PREF_DISABLE_BACKGROUND_IMAGE, false);
	}
//	/**
//	 * Code based on Google sample code to bind the service.
//	 */
//	private ServiceConnection mConnection = new ServiceConnection() {
//	    public void onServiceConnected(ComponentName className, IBinder service) {
//	        // This is called when the connection with the service has been
//	        // established, giving us the service object we can use to
//	        // interact with the service.  Because we have bound to a explicit
//	        // service that we know is running in our own process, we can
//	        // cast its IBinder to a concrete class and directly access it.
//	        mBoundService = (BcQueueManager)((QueueManager.QueueManagerBinder)service).getService();
//
//	        // Tell the user about this for our demo.
//	        //Toast.makeText(BookCatalogueApp.this, "Connected", Toast.LENGTH_SHORT).show();
//	    }
//
//	    public void onServiceDisconnected(ComponentName className) {
//	        // This is called when the connection with the service has been
//	        // unexpectedly disconnected -- that is, its process crashed.
//	        // Because it is running in our same process, we should never
//	        // see this happen.
//	        mBoundService = null;
//	        //Toast.makeText(BookCatalogueApp.this, "Disconnected", Toast.LENGTH_SHORT).show();
//	    }
//	};
//
//	/** Indicates service has been bound. Really. */
//	boolean mIsBound;
//
//	/**
//	 * Establish a connection with the service.  We use an explicit
//	 * class name because we want a specific service implementation that
//	 * we know will be running in our own process (and thus won't be
//	 * supporting component replacement by other applications).
//	 */
//	void doBindService() {
//	    bindService(new Intent(BookCatalogueApp.this, BcQueueManager.class), mConnection, Context.BIND_AUTO_CREATE);
//	    mIsBound = true;
//	}
//	/**
//	 * Detach existiing service connection.
//	 */
//	void doUnbindService() {
//	    if (mIsBound) {
//	        unbindService(mConnection);
//	        mIsBound = false;
//	    }
//	}

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


//	/**
//	 * Used by the Manifest-based startup activity to determine the desired first activity for the user.
//	 * 
//	 * @return	Intent for preference-based startup activity.
//	 */
//	public Intent getStartupIntent() {
//		BookCataloguePreferences prefs = getAppPreferences();
//
//		Intent i;
//		if (prefs.getStartInMyBook()) {
//			i = new Intent(this, BookCatalogue.class);
//		} else {
//			i = new Intent(this, MainMenu.class);
//		}
//		return i;
//	}

	public static void startPreferencesActivity(Activity a) {
		Intent i = new Intent(a, BooklistPreferencesActivity.class);
		a.startActivity(i);
	}

	/**
     * Show a notification while this app is running.
	 * 
	 * @param title
	 * @param message
	 */
    public static void showNotification(int id, String title, String message, Intent i) {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = message; //getText(R.string.local_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_stat_logo, text, System.currentTimeMillis());
        // Auto-cancel the notification
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, i, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(context, title, //getText(R.string.local_service_label),
                       text, contentIntent);

        // Send the notification.
        mNotifier.notify(id, notification);
    }

    /**
     * Get the current preferred locale, or null
     *
     * @return  locale, or null
     */
    public static Locale getPreferredLocale() {
        return mPreferredLocale;
    }

    /**
     * Set the current preferred locale in the passed resources.
     *
     * @param res   Resources to use
     * @return  true if it was actually changed
     */
    public static boolean applyPreferredLocaleIfNecessary(Resources res) {
        if (mPreferredLocale == null)
            return false;

        if (res.getConfiguration().locale.equals(mPreferredLocale))
            return false;
        Locale.setDefault(mPreferredLocale);
        Configuration config = new Configuration();
        config.locale = mPreferredLocale;
        res.updateConfiguration(config, res.getDisplayMetrics());

        return true;
    }

    /**
     * Monitor configuration changes (like rotation) to make sure we reset the
     * locale.
     *
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        if (mPreferredLocale != null)
        {
        	applyPreferredLocaleIfNecessary(getBaseContext().getResources());
        }
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
    		mSupportedLocales = new ArrayList<String>();
    		mSupportedLocales.add("de_DE");
    		mSupportedLocales.add("en_AU");
    		mSupportedLocales.add("es_ES");
    		mSupportedLocales.add("fr_FR");
    		mSupportedLocales.add("it_IT");
    		mSupportedLocales.add("nl_NL");
    		mSupportedLocales.add("ru_RU");
    		mSupportedLocales.add("tr_TR");
    	}
    	return mSupportedLocales;
    }
}
