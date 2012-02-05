package com.eleybourn.bookcatalogue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.IBinder;
import org.acra.*;
import org.acra.annotation.*;
import static org.acra.ReportField.*;

import net.philipwarner.taskqueue.QueueManager;

/**
 * BookCatalogue Application implementation. Useful for making globals available
 * and for being a central location for logically application-specific objects such
 * as preferences.
 * 
 * @author Grunthos
 *
 */
@ReportsCrashes(formKey = "", // will not be used
	mailTo = "grunthos@rhyme.com.au, pjw@rhyme.com.au",
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

	/**
	 * Constructor.
	 */
	public BookCatalogueApp() {
		super();
	}

	/**
	 * Most real initialization should go here, since before this point, the App is still
	 * 'Under Construction'.
	 */
	@Override
	public void onCreate() {
		// The following line triggers the initialization of ACRA
        ACRA.init(this);

        // Don't rely on the the context until now...
		BookCatalogueApp.context = this.getApplicationContext();

		// Start the queue manager
		startQueueManager();

		super.onCreate();
	}

	/**
	 * Currently the QueueManager is implemented as a service. This is not clearly necessary
	 * but has the huge advantage of making a 'context' object available in the Service
	 * implementation.
	 * 
	 * By binding it here, the service will not die when the last Activity is closed. We
	 * could call StartService to keep it awake indefinitely also, but we do want the binding
	 * object...so we bind it.
	 */
	private void startQueueManager() {
		doBindService();		
	}
	/**
	 * Points to the bound service, once it is started.
	 */
	private static BcQueueManager mBoundService = null;

	/**
	 * Utility routine to get the current QueueManager.
	 * 
	 * @return	QueueManager object
	 */
	public static BcQueueManager getQueueManager() {
		return mBoundService;
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

	/**
	 * Code based on Google sample code to bind the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        mBoundService = (BcQueueManager)((QueueManager.QueueManagerBinder)service).getService();

	        // Tell the user about this for our demo.
	        //Toast.makeText(BookCatalogueApp.this, "Connected", Toast.LENGTH_SHORT).show();
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        mBoundService = null;
	        //Toast.makeText(BookCatalogueApp.this, "Disconnected", Toast.LENGTH_SHORT).show();
	    }
	};

	/** Indicates service has been bound. Really. */
	boolean mIsBound;

	/**
	 * Establish a connection with the service.  We use an explicit
	 * class name because we want a specific service implementation that
	 * we know will be running in our own process (and thus won't be
	 * supporting component replacement by other applications).
	 */
	void doBindService() {
	    bindService(new Intent(BookCatalogueApp.this, BcQueueManager.class), mConnection, Context.BIND_AUTO_CREATE);
	    mIsBound = true;
	}
	/**
	 * Detach existiing service connection.
	 */
	void doUnbindService() {
	    if (mIsBound) {
	        unbindService(mConnection);
	        mIsBound = false;
	    }
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


	/**
	 * Used by the Manifest-based startup activity to determine the desired first activity for the user.
	 * 
	 * @return	Intent for preference-based startup activity.
	 */
	public Intent getStartupIntent() {
		BookCataloguePreferences prefs = getAppPreferences();

		Intent i;
		if (prefs.getStartInMyBook()) {
			i = new Intent(this, BookCatalogue.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		} else {
			i = new Intent(this, MainMenu.class);
		}
		return i;
	}

	/**
	 * Class to manage application preferences rather than rely on each activity knowing how to 
	 * access them.
	 * 
	 * @author Grunthos
	 */
	public static class BookCataloguePreferences {
		/** Underlying SharedPreferences */
		private SharedPreferences m_prefs = BookCatalogueApp.context.getSharedPreferences("bookCatalogue", MODE_PRIVATE);

		/** Name to use for global preferences; non-global should be moved to appropriate Activity code */
		public static final String PREF_START_IN_MY_BOOKS = "start_in_my_books";
		public static final String PREF_LARGE_THUMBNAILS = "APP.LargeThumbnails";
		public static final String PREF_SHOW_ALL_AUTHORS = "APP.ShowAllAuthors";
		public static final String PREF_SHOW_ALL_SERIES = "APP.ShowAllSeries";
		public static final String PREF_DISPLAY_FIRST_THEN_LAST_NAMES = "APP.DisplayFirstThenLast";
		public static final String PREF_BOOKLIST_STYLE = "APP.BooklistStyle";

		/** Get startup activity preference */
		public boolean getStartInMyBook() {
			return getBoolean(PREF_START_IN_MY_BOOKS,false);
		}
		/** Set startup activity preference */
		public BookCataloguePreferences setStartInMyBook(boolean value) {
			setBoolean(PREF_START_IN_MY_BOOKS,value);
			return this;
		}
		
		/** Get thumbnail size preference */
		public boolean getLargeThumbails() {
			return getBoolean(PREF_LARGE_THUMBNAILS,false);
		}
		/** Set startup activity preference */
		public BookCataloguePreferences setLargeThumbails(boolean value) {
			setBoolean(PREF_LARGE_THUMBNAILS,value);
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
}
