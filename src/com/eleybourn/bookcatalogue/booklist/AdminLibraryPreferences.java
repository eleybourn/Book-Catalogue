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

package com.eleybourn.bookcatalogue.booklist;

import android.content.Intent;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.PreferencesBase;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.LibraryGroup.RowKinds;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.ValuePropertyWithGlobalDefault;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Activity to manage the preferences associate with Library (and the BooksOnBookshelf activity).
 * 
 * @author Philip Warner
 */
public class AdminLibraryPreferences extends PreferencesBase {

	/** Prefix for all preferences */
	public static final String TAG = "Library.Global.";

	/** Show flat backgrounds in Library */
	public static final String PREF_theme_surfaceContainerNAILS = TAG + "BackgroundThumbnails";
	public static final String PREF_CACHE_THUMBNAILS = TAG + "CacheThumbnails";

	/** Key added to resulting Intent */
	public static final String PREF_CHANGED = TAG + "PrefChanged";
	/** Always expand/collapse/preserve library state */
	public static final String PREF_LIBRARY_STATE = TAG + "LibraryState";

	// ID values for state preservation property
	public static final int LIBRARY_ALWAYS_EXPANDED = 1;
	public static final int LIBRARY_ALWAYS_COLLAPSED = 2;
	public static final int LIBRARY_STATE_PRESERVED = 3;

	/** Library state preservation property */
	private static final ItemEntries<Integer> mLibraryStateListItems = new ItemEntries<>();
	private static final IntegerListProperty mLibraryStateProperty = new IntegerListProperty(
            mLibraryStateListItems,
            PREF_LIBRARY_STATE,
			PropertyGroup.GRP_GENERAL, 
			R.string.preference_library_state, null, PREF_LIBRARY_STATE, LIBRARY_ALWAYS_EXPANDED);
	static {
		mLibraryStateListItems.add(null, R.string.option_use_default_setting);
		mLibraryStateListItems.add(LIBRARY_ALWAYS_EXPANDED, R.string.option_always_start_library_expanded);
		mLibraryStateListItems.add(LIBRARY_ALWAYS_COLLAPSED, R.string.option_always_start_library_collapsed);
		mLibraryStateListItems.add(LIBRARY_STATE_PRESERVED, R.string.option_remember_library_state);
		mLibraryStateProperty.setGlobal(true);
	}

	/** Enable Thumbnail Cache property definition */
	private static final ItemEntries<Boolean> mCacheThumbnailsListItems = new ItemEntries<>();
	private static final BooleanListProperty mCacheThumbnailsProperty = new BooleanListProperty(
				mCacheThumbnailsListItems, 
				PREF_CACHE_THUMBNAILS, 
				PropertyGroup.GRP_THUMBNAILS, 
				R.string.preference_resizing_cover_thumbnails, null, PREF_CACHE_THUMBNAILS, false);
	static {
		mCacheThumbnailsListItems.add(null, R.string.option_use_default_setting);
		mCacheThumbnailsListItems.add(false, R.string.option_resize_each_time);
		mCacheThumbnailsListItems.add(true, R.string.option_cache_resized_thumbnails_for_later_use);
		mCacheThumbnailsProperty.setWeight(100);
		mCacheThumbnailsProperty.setGlobal(true);
	}

	/** Enable Background Thumbnail fetch property definition */
	private static final ItemEntries<Boolean> mBackgroundThumbnailsListItems = new ItemEntries<>();
	private static final BooleanListProperty mBackgroundThumbnailsProperty = new BooleanListProperty(
				mBackgroundThumbnailsListItems, 
				PREF_theme_surfaceContainerNAILS, 
				PropertyGroup.GRP_THUMBNAILS, 
				R.string.preference_generating_cover_thumbnails, null, PREF_theme_surfaceContainerNAILS, false);
	static {
		mBackgroundThumbnailsListItems.add(null, R.string.option_use_default_setting);
		mBackgroundThumbnailsListItems.add(false, R.string.option_generate_immediately);
		mBackgroundThumbnailsListItems.add(true, R.string.option_use_background_thread);
		mBackgroundThumbnailsProperty.setWeight(100);
		mBackgroundThumbnailsProperty.setGlobal(true);
	}

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	/**
	 * Build the activity UI
	 */
	@Override 
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			if (savedInstanceState == null)
				HintManager.displayHint(this, R.string.hint_library_global_properties, null, null);

		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * Get the current preferred rebuild state for the list
	 * @return	Library state
	 */
	public static int getRebuildState() {
		return mLibraryStateProperty.get();
	}

	/**
	 * Return the layout to use for this subclass
	 */
	@Override
	public int getLayout() {
		return R.layout.admin_library_preferences;
	}

    /**
     * Return the layout to use for this subclass
     */
    @Override
    public int getPageTitle() {
        return R.string.title_library_preferences;
    }

	/**
	 * Setup each component of the layout using the passed preferences
	 */
	@Override
	public void setupViews(BookCataloguePreferences prefs, Properties globalProps) {
		//This activity predominantly shows 'Property' objects; we build that collection here.

		// Create a dummy style and add one group of each kind
		LibraryStyle style = new LibraryStyle("");
		for(int i: LibraryGroup.getRowKinds()) {
			if (i != RowKinds.ROW_KIND_BOOK)
				style.addGroup(i);
		}
		
		// Get all the properties from the style that have global defaults.
		Properties allProps = style.getProperties();
		for(Property p: allProps) {
			if (p instanceof ValuePropertyWithGlobalDefault) {
				ValuePropertyWithGlobalDefault<?> gp = (ValuePropertyWithGlobalDefault<?>)p;
				if (gp.hasGlobalDefault()) {
					gp.setGlobal(true);
					globalProps.add(gp);
				}
			}
		}
		// Add the locally constructed properties
		globalProps.add(mLibraryStateProperty);
		globalProps.add(mCacheThumbnailsProperty);
		globalProps.add(mBackgroundThumbnailsProperty);

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

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
	}

	/**
	 * Convenience Accessor.
	 *
	 * @return Value of the property
	 */
	public static boolean isThumbnailCacheEnabled() {
		return mCacheThumbnailsProperty.getResolvedValue();
	}

	/**
	 * Convenience Accessor.
	 *
	 * @return Value of the property
	 */
	public static boolean isBackgroundThumbnailsEnabled() {
		return mBackgroundThumbnailsProperty.getResolvedValue();
	}
}
