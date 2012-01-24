package com.eleybourn.bookcatalogue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.IBinder;
import android.widget.Toast;
import net.philipwarner.taskqueue.QueueManager;

/**
 * BookCatalogue Application implementation. Useful for making globals available
 * and for being a central location for logically application-specific objects such
 * as preferences.
 * 
 * @author Grunthos
 *
 */
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
	 * Initial pass at catching FC logs etc.
	 * 
	 * TODO: Remove/resolve for production, since it requires READ_LOGS priv.
	 */
	private class CheckForceCloseTask extends AsyncTask<Void, Void, Boolean> {
		LogCollector mLogCollector = null;

        @Override
        protected Boolean doInBackground(Void... params) {
    		mLogCollector = new LogCollector(context);
            return mLogCollector.hasForceCloseHappened();
        }
        
        @Override
        protected void onPostExecute(Boolean result) {
                if (true || result) {
            		// Start LogCollector
            		new AsyncTask<Void, Void, Boolean>() {
                        @Override
                        protected Boolean doInBackground(Void... params) {
                                return mLogCollector.collect();
                        }
                        @Override
                        protected void onPreExecute() {
                                //showDialog(DIALOG_PROGRESS_COLLECTING_LOG);
                        }
                        @Override
                        protected void onPostExecute(Boolean result) {
                                //dismissDialog(DIALOG_PROGRESS_COLLECTING_LOG);
                                if (result) {
                                        mLogCollector.sendLog("pjw@rhyme.com.au", "BookCatalogue Error Log", "Preface line 1\nPreface line 2");
                                        Toast.makeText(getApplicationContext(), "Logs sent.", Toast.LENGTH_LONG).show();
                                }
                                //else
                                //        showDialog(DIALOG_FAILED_TO_COLLECT_LOGS);
                        }
            		}.execute();
                } else
                        Toast.makeText(getApplicationContext(), "No force close detected.", Toast.LENGTH_LONG).show();
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
		// Check for FC
		CheckForceCloseTask fcTask = new CheckForceCloseTask();
		fcTask.execute();

		// Start the queue manager
		startQueueManager();
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

		/** Name to use for startup activity preferences */
		public static final String PREF_START_IN_MY_BOOKS = "start_in_my_books";

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
		/** Get a standard preferences editor for mass updates */
		public Editor edit() {
			return m_prefs.edit();
		}
	}
}
