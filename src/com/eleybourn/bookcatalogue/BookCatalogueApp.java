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
import android.app.AlertDialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import org.acra.*;
import org.acra.annotation.*;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;

import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;


import static org.acra.ReportField.*;

import net.philipwarner.taskqueue.QueueManager;

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
	mode = ReportingInteractionMode.NOTIFICATION,
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
		// The following line triggers the initialization of ACRA
        ACRA.init(this);
        BcReportSender bcSender = new BcReportSender(this);
        ErrorReporter.getInstance().setReportSender(bcSender);

        // Create the notifier
    	mNotifier = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

    	// Don't rely on the the context until now...
		BookCatalogueApp.context = this.getApplicationContext();

		// Start the queue manager
		if (mQueueManager == null)
			mQueueManager = new BcQueueManager(this.getApplicationContext());

		super.onCreate();
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

	/**
	 * Class to manage application preferences rather than rely on each activity knowing how to 
	 * access them.
	 * 
	 * @author Philip Warner
	 */
	public static class BookCataloguePreferences {
		/** Underlying SharedPreferences */
		private SharedPreferences m_prefs = BookCatalogueApp.context.getSharedPreferences("bookCatalogue", MODE_PRIVATE);

		/** Name to use for global preferences; non-global should be moved to appropriate Activity code */
		public static final String PREF_START_IN_MY_BOOKS = "start_in_my_books";
		public static final String PREF_INCLUDE_CLASSIC_MY_BOOKS = "App.includeClassicView";
		public static final String PREF_DISABLE_BACKGROUND_IMAGE = "App.DisableBackgroundImage";
		public static final String PREF_SHOW_ALL_AUTHORS = "APP.ShowAllAuthors";
		public static final String PREF_SHOW_ALL_SERIES = "APP.ShowAllSeries";
		public static final String PREF_DISPLAY_FIRST_THEN_LAST_NAMES = "APP.DisplayFirstThenLast";
		public static final String PREF_BOOKLIST_STYLE = "APP.BooklistStyle";
		public static final String PREF_USE_EXTERNAL_IMAGE_CROPPER = "App.UseExternalImageCropper";
		public static final String PREF_AUTOROTATE_CAMERA_IMAGES = "App.AutorotateCameraImages";

		/** Get startup activity preference */
		public boolean getStartInMyBook() {
			return getBoolean(PREF_START_IN_MY_BOOKS,false);
		}
		/** Set startup activity preference */
		public BookCataloguePreferences setStartInMyBook(boolean value) {
			setBoolean(PREF_START_IN_MY_BOOKS,value);
			return this;
		}

		/** Get a named boolean preference */
		public boolean getBoolean(String name, boolean defaultValue) {
			boolean result;
			try {
				result = m_prefs.getBoolean(name, defaultValue);
			} catch (Exception e) {
				result = defaultValue;
			}
			return result;
		}
		/** Set a named boolean preference */
		public void setBoolean(String name, boolean value) {
			Editor ed = this.edit();
			try {
				ed.putBoolean(name, value);
			} finally {
				ed.commit();
			}
		}
		/** Get a named string preference */
		public String getString(String name, String defaultValue) {
			String result;
			try {
				result = m_prefs.getString(name, defaultValue);
			} catch (Exception e) {
				result = defaultValue;
			}
			return result;
		}
		/** Set a named string preference */
		public void setString(String name, String value) {
			Editor ed = this.edit();
			try {
				ed.putString(name, value);
			} finally {
				ed.commit();
			}
		}
		/** Get a named string preference */
		public int getInt(String name, int defaultValue) {
			int result;
			try {
				result = m_prefs.getInt(name, defaultValue);
			} catch (Exception e) {
				result = defaultValue;
			}
			return result;
		}
		/** Set a named string preference */
		public void setInt(String name, int value) {
			Editor ed = this.edit();
			try {
				ed.putInt(name, value);
			} finally {
				ed.commit();
			}
		}
		/** Get a standard preferences editor for mass updates */
		public Editor edit() {
			return m_prefs.edit();
		}
	}

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

}
