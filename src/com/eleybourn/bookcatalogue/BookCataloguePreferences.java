package com.eleybourn.bookcatalogue;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * Class to manage application preferences rather than rely on each activity knowing how to 
 * access them.
 * 
 * @author Philip Warner
 */
public class BookCataloguePreferences {
	/** Underlying SharedPreferences */
	private final SharedPreferences m_prefs = BookCatalogueApp.mPrefs;

	/** Name to use for global preferences; non-global should be moved to appropriate Activity code */
	public static final String PREF_START_IN_MY_BOOKS = "start_in_my_books";
	public static final String PREF_SHOW_ALL_AUTHORS = "APP.ShowAllAuthors";
	public static final String PREF_SHOW_ALL_SERIES = "APP.ShowAllSeries";
	public static final String PREF_DISPLAY_FIRST_THEN_LAST_NAMES = "APP.DisplayFirstThenLast";
	public static final String PREF_BOOKLIST_STYLE = "APP.LibraryStyle";
	public static final String PREF_USE_EXTERNAL_IMAGE_CROPPER = "App.UseExternalImageCropper";
	public static final String PREF_AUTOROTATE_CAMERA_IMAGES = "App.AutorotateCameraImages";
	public static final String PREF_CROP_FRAME_WHOLE_IMAGE = "App.CropFrameWholeImage";
	///** Force list construction to compatible mode (compatible with Android 1.6) */
	//public static final String PREF_BOOKLIST_GENERATION_MODE = "App.BooklistGenerationMode";
	/** Is book info opened in read-only mode. Default is false (opened in edit mode) */
	public static final String PREF_OPEN_BOOK_READ_ONLY = "App.OpenBookReadOnly";
	/** Last full backup date */
	public static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
	/** Last full backup file path */
	public static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";
	/** Preferred interface locale */
	public static final String PREF_APP_LOCALE = "App.Locale";
	/** Preferred interface locale */
	private static final String PREF_CHECKED_MISSING_COVERS = "App.CheckedMissingCovers";
    public static final String PREF_ACCOUNT_EMAIL = "Account.Email";
    public static final String PREF_ACCOUNT_OPT_IN = "Account.OptIn";
    public static final String PREF_ACCOUNT_API_TOKEN = "Account.ApiToken";

	/** Get startup activity preference */
	public boolean getStartInMyBook() {
		return getBoolean(PREF_START_IN_MY_BOOKS,false);
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

    /** Static preference object so that we can respond to events relating to changes */
    private static SharedPreferences mPrefs = null;
    /** Get (or create) the static shared preferences */
	public static SharedPreferences getSharedPreferences() {
		if (mPrefs == null) {
            mPrefs = BookCatalogueApp.mPrefs;
        }
		return mPrefs;
	}

	public boolean hasCheckedForMissingCovers() {
		return getBoolean(PREF_CHECKED_MISSING_COVERS, false);
	}

	public void setCheckedForMissingCovers(boolean checked) {
		setBoolean(PREF_CHECKED_MISSING_COVERS, checked);
	}

    /** Get stored account email */
    public String getAccountEmail() {
        return getString(PREF_ACCOUNT_EMAIL, "");
    }

    /** Set account email */
    public void setAccountEmail(String email) {
        setString(PREF_ACCOUNT_EMAIL, email);
    }

    /** Get opt-in status */
    public boolean getAccountOptIn() {
        return getBoolean(PREF_ACCOUNT_OPT_IN, false);
    }

    /** Set opt-in status */
    public void setAccountOptIn(boolean optIn) {
        setBoolean(PREF_ACCOUNT_OPT_IN, optIn);
    }

    /** Get API Token */
    public String getAccountApiToken() {
        return getString(PREF_ACCOUNT_API_TOKEN, "");
    }

    /** Set API Token */
    public void setAccountApiToken(String token) {
        setString(PREF_ACCOUNT_API_TOKEN, token);
    }
}