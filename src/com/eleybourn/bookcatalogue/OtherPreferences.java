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
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;

import android.os.Bundle;
import android.view.ViewGroup;

/**
 * Activity to display the 'Other Preferences' dialog and maintain the preferences.
 * 
 * @author Philip Warner
 */
public class OtherPreferences extends PreferencesBase {

	private static final BooleanProperty mStartInMyBooksProperty = (BooleanProperty) new BooleanProperty(BookCataloguePreferences.PREF_START_IN_MY_BOOKS)
	.setDefaultValue(false)
	.setPreferenceKey(BookCataloguePreferences.PREF_START_IN_MY_BOOKS)
	.setGlobal(true)
	.setNameResourceId(R.string.start_in_my_books)
	.setGroup(PropertyGroup.GRP_USER_INTERFACE);

	//	addBooleanPreference(prefs, R.id.include_classic_checkbox, R.id.include_classic_label, BookCataloguePreferences.PREF_INCLUDE_CLASSIC_MY_BOOKS, false);
	private static final BooleanProperty mIncludeClassicProperty = (BooleanProperty) new BooleanProperty(BookCataloguePreferences.PREF_INCLUDE_CLASSIC_MY_BOOKS)
	.setDefaultValue(false)
	.setPreferenceKey(BookCataloguePreferences.PREF_INCLUDE_CLASSIC_MY_BOOKS)
	.setGlobal(true)
	.setNameResourceId(R.string.include_classic_catalogue_view)
	.setGroup(PropertyGroup.GRP_USER_INTERFACE);

	//	addBooleanPreference(prefs, R.id.disable_background_image_checkbox, R.id.disable_background_image_label, BookCataloguePreferences.PREF_DISABLE_BACKGROUND_IMAGE, false);
	private static final BooleanProperty mDisableBackgroundProperty = (BooleanProperty) new BooleanProperty(BookCataloguePreferences.PREF_DISABLE_BACKGROUND_IMAGE)
	.setDefaultValue(false)
	.setPreferenceKey(BookCataloguePreferences.PREF_DISABLE_BACKGROUND_IMAGE)
	.setGlobal(true)
	.setNameResourceId(R.string.disable_background_image)
	.setGroup(PropertyGroup.GRP_USER_INTERFACE);

	//	addBooleanPreference(prefs, R.id.beep_if_scanned_isbn_invalid_checkbox, R.id.beep_if_scanned_isbn_invalid_label, SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID, true);
	private static final BooleanProperty mBeepOnInvalidIsbnProperty = (BooleanProperty) new BooleanProperty(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID)
	.setDefaultValue(true)
	.setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID)
	.setGlobal(true)
	.setNameResourceId(R.string.beep_if_scanned_isbn_invalid)
	.setGroup(PropertyGroup.GRP_USER_INTERFACE);
	
	
	/** Camera image rotation property */
	private static ItemEntries<Integer> mRotationListItems = new ItemEntries<Integer>()
			.add(null, R.string.use_default_setting)
			.add(0, R.string.no)
			.add(90, R.string.menu_rotate_thumb_cw)
			.add(-90, R.string.menu_rotate_thumb_ccw)
			.add(180, R.string.menu_rotate_thumb_180);

	private static IntegerListProperty mRotationProperty = (IntegerListProperty) new IntegerListProperty( mRotationListItems, BookCataloguePreferences.PREF_AUTOROTATE_CAMERA_IMAGES)
		.setDefaultValue(90)
		.setPreferenceKey(BookCataloguePreferences.PREF_AUTOROTATE_CAMERA_IMAGES)
		.setGlobal(true)
		.setNameResourceId(R.string.auto_rotate_camera_images)
		.setGroup(PropertyGroup.GRP_GENERAL)
		;

	private static final BooleanProperty mExternalCropperProperty = (BooleanProperty) new BooleanProperty(BookCataloguePreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER)
		.setDefaultValue(false)
		.setPreferenceKey(BookCataloguePreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER)
		.setGlobal(true)
		.setNameResourceId(R.string.use_external_image_cropper)
		.setGroup(PropertyGroup.GRP_GENERAL);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.other_preferences);
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
	public void setupViews(final BookCataloguePreferences prefs, Properties globalProps) {
		// Add the locally constructed porperties
		Property[] props = new Property[] {mStartInMyBooksProperty, mIncludeClassicProperty, mDisableBackgroundProperty, mBeepOnInvalidIsbnProperty, mRotationProperty, mExternalCropperProperty};

		for(Property p: props)
			globalProps.add(p);
	}

	@Override
	public int getLayout() {
		return R.layout.other_preferences;
	}

}
