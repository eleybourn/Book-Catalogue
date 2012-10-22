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

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;

import android.os.Bundle;

/**
 * Activity to display the 'Other Preferences' dialog and maintain the preferences.
 * 
 * @author Philip Warner
 */
public class OtherPreferences extends PreferencesBase {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this);
	}

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this);		
	}

	/**
	 * Display current preferences and set handlers to catch changes.
	 */
	public void setupViews(final BookCataloguePreferences prefs) {
		addBooleanPreference(prefs, R.id.startup_my_books_checkbox, R.id.startup_in_my_books_label, BookCataloguePreferences.PREF_START_IN_MY_BOOKS, false);
		addBooleanPreference(prefs, R.id.include_classic_checkbox, R.id.include_classic_label, BookCataloguePreferences.PREF_INCLUDE_CLASSIC_MY_BOOKS, false);
		addBooleanPreference(prefs, R.id.disable_background_image_checkbox, R.id.disable_background_image_label, BookCataloguePreferences.PREF_DISABLE_BACKGROUND_IMAGE, false);
		addBooleanPreference(prefs, R.id.beep_if_scanned_isbn_invalid_checkbox, R.id.beep_if_scanned_isbn_invalid_label, SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID, true);
		addBooleanPreference(prefs, R.id.use_external_image_cropper_checkbox, R.id.use_external_image_cropper_label, BookCataloguePreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER, false);
	}

	@Override
	public int getLayout() {
		return R.layout.other_preferences;
	}

}
