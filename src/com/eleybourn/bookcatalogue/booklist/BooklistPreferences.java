package com.eleybourn.bookcatalogue.booklist;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.Logger;
import com.eleybourn.bookcatalogue.PreferencesBase;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Utils;

/**
 * Activity to manage the preferences associate with Book lists (and the BooksOnBookshelf activity).
 * 
 * @author Grunthos
 */
public class BooklistPreferences extends PreferencesBase {

	/** Prefix for all prefs */
	public static final String TAG = "BookList";
	/** Show list of bookshelves for each book */
	public static final String PREF_SHOW_BOOKSHELVES = TAG + ".ShowBookshelves";
	/** Show location for each book */
	public static final String PREF_SHOW_LOCATION = TAG + ".ShowLocation";
	/** Show publisher for each book */
	public static final String PREF_SHOW_PUBLISHER = TAG + ".ShowPublisher";
	/** Show thumbnail image for each book */
	public static final String PREF_SHOW_THUMBNAILS = TAG + ".ShowThumbnails";
	/** Key added to resulting Intent */
	public static final String PREF_CHANGED = TAG + "PrefChanged";

	/**
	 * Build the activity UI
	 */
	@Override 
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * Return the layout to use for this subclass
	 */
	@Override
	public int getLayout() {
		return R.layout.booklist_preferences;
	}

	/**
	 * Setup each component of the layout using the passed preferences
	 */
	@Override
	public void setupViews(BookCataloguePreferences prefs) {
		addBooleanPreference(prefs, R.id.show_thumbnails_checkbox, R.id.show_thumbnails_label, BooklistPreferences.PREF_SHOW_THUMBNAILS);
		addBooleanPreference(prefs, R.id.large_thumbnails_checkbox, R.id.large_thumbnails_label, BookCataloguePreferences.PREF_LARGE_THUMBNAILS);
		addBooleanPreference(prefs, R.id.show_all_authors_checkbox, R.id.show_all_authors_label, BookCataloguePreferences.PREF_SHOW_ALL_AUTHORS);
		addBooleanPreference(prefs, R.id.show_all_series_checkbox, R.id.show_all_series_label, BookCataloguePreferences.PREF_SHOW_ALL_SERIES);
		addBooleanPreference(prefs, R.id.display_first_then_last_names_checkbox, R.id.display_first_then_last_names_label, BookCataloguePreferences.PREF_DISPLAY_FIRST_THEN_LAST_NAMES);
		addBooleanPreference(prefs, R.id.bookshelves_checkbox, R.id.bookshelves_label, BooklistPreferences.PREF_SHOW_BOOKSHELVES);
		addBooleanPreference(prefs, R.id.location_checkbox, R.id.location_label, BooklistPreferences.PREF_SHOW_LOCATION);
		addBooleanPreference(prefs, R.id.publisher_checkbox, R.id.publisher_label, BooklistPreferences.PREF_SHOW_PUBLISHER);
		addClickablePref(prefs, R.id.erase_cover_cache_label, new OnClickListener() {
			@Override
			public void onClick(View v) {
				Utils.eraseCoverCache();
				return;
			}});
	}

	/**
	 * Trap the onPause, and if the Activity is finishing then set the result.
	 */
	@Override
	public void onPause() {
		super.onPause();

		if (isFinishing()) {
			Intent i = new Intent();
			i.putExtra(PREF_CHANGED, true);
			if (getParent() == null) {
				setResult(RESULT_OK, i);
			} else {
				getParent().setResult(RESULT_OK, i);
			}
		}
	}

}
