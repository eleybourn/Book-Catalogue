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

import android.os.Bundle;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.utils.SoundManager;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Activity to display the 'Other Preferences' dialog and maintain the preferences.
 * 
 * @author Philip Warner
 */
public class OtherPreferences extends PreferencesBase {

	/** Camera image rotation property values */
	private static ItemEntries<Integer> mRotationListItems = new ItemEntries<Integer>()
			.add(null, R.string.use_default_setting)
			.add(0, R.string.no)
			.add(90, R.string.menu_rotate_thumb_cw)
			.add(-90, R.string.menu_rotate_thumb_ccw)
			.add(180, R.string.menu_rotate_thumb_180);

	
	
	private static final Properties mProperties = new Properties()

	.add(new BooleanProperty(BookCataloguePreferences.PREF_START_IN_MY_BOOKS)
		.setDefaultValue(false)
		.setPreferenceKey(BookCataloguePreferences.PREF_START_IN_MY_BOOKS)
		.setGlobal(true)
		.setWeight(0)
		.setNameResourceId(R.string.start_in_my_books)
		.setGroup(PropertyGroup.GRP_USER_INTERFACE))	
	
		/*
		 * Enabling/disabling read-only mode when opening book. If enabled book
		 * is opened in read-only mode (editing through menu), else in edit mode.
		 */
	.add (new BooleanProperty(BookCataloguePreferences.PREF_OPEN_BOOK_READ_ONLY)
		.setDefaultValue(false)
		.setPreferenceKey(BookCataloguePreferences.PREF_OPEN_BOOK_READ_ONLY)
		.setGlobal(true)
		.setNameResourceId(R.string.prefs_global_opening_book_mode)
		.setGroup(PropertyGroup.GRP_USER_INTERFACE))
		
	.add(new BooleanProperty(BookCataloguePreferences.PREF_CROP_FRAME_WHOLE_IMAGE)
		.setDefaultValue(false)
		.setPreferenceKey(BookCataloguePreferences.PREF_CROP_FRAME_WHOLE_IMAGE)
		.setGlobal(true)
		.setNameResourceId(R.string.default_crop_frame_is_whole_image)
		.setGroup(PropertyGroup.GRP_THUMBNAILS))

	.add(new BooleanProperty(BookCataloguePreferences.PREF_INCLUDE_CLASSIC_MY_BOOKS)
		.setDefaultValue(false)
		.setPreferenceKey(BookCataloguePreferences.PREF_INCLUDE_CLASSIC_MY_BOOKS)
		.setGlobal(true)
		.setWeight(100)
		.setNameResourceId(R.string.include_classic_catalogue_view)
		.setGroup(PropertyGroup.GRP_USER_INTERFACE) )

	.add(new BooleanProperty(BookCataloguePreferences.PREF_DISABLE_BACKGROUND_IMAGE)
		.setDefaultValue(false)
		.setPreferenceKey(BookCataloguePreferences.PREF_DISABLE_BACKGROUND_IMAGE)
		.setGlobal(true)
		.setWeight(200)
		.setNameResourceId(R.string.disable_background_image)
		.setGroup(PropertyGroup.GRP_USER_INTERFACE) )

	.add(new BooleanProperty(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID)
		.setDefaultValue(true)
		.setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID)
		.setGlobal(true)
		.setWeight(300)
		.setNameResourceId(R.string.beep_if_scanned_isbn_invalid)
		.setGroup(PropertyGroup.GRP_USER_INTERFACE) )

	.add(new IntegerListProperty( mRotationListItems, BookCataloguePreferences.PREF_AUTOROTATE_CAMERA_IMAGES)
		.setDefaultValue(90)
		.setPreferenceKey(BookCataloguePreferences.PREF_AUTOROTATE_CAMERA_IMAGES)
		.setGlobal(true)
		.setNameResourceId(R.string.auto_rotate_camera_images)
		.setGroup(PropertyGroup.GRP_THUMBNAILS) )

	.add (new BooleanProperty(BookCataloguePreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER)
		.setDefaultValue(false)
		.setPreferenceKey(BookCataloguePreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER)
		.setGlobal(true)
		.setNameResourceId(R.string.use_external_image_cropper)
		.setGroup(PropertyGroup.GRP_THUMBNAILS))		
		;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.other_preferences);
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
	}

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);		
	}

	/**
	 * Display current preferences and set handlers to catch changes.
	 */
	public void setupViews(final BookCataloguePreferences prefs, Properties globalProps) {
		// Add the locally constructed porperties
		for(Property p: mProperties)
			globalProps.add(p);
	}

	@Override
	public int getLayout() {
		return R.layout.other_preferences;
	}

}
