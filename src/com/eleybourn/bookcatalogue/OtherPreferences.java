package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;

import android.os.Bundle;

/**
 * Activity to display the 'Other Preferences' dialog and maintain the preferences.
 * 
 * @author Grunthos
 */
public class OtherPreferences extends PreferencesBase {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	/**
	 * Display current preferences and set handlers to catch changes.
	 */
	public void setupViews(final BookCataloguePreferences prefs) {
		addBooleanPreference(prefs, R.id.startup_my_books_checkbox, R.id.startup_in_my_books_label, BookCataloguePreferences.PREF_START_IN_MY_BOOKS);
	}

	@Override
	public int getLayout() {
		// TODO Auto-generated method stub
		return R.layout.other_preferences;
	}

}
