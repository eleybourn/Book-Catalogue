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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.HintManager;
import com.eleybourn.bookcatalogue.Logger;
import com.eleybourn.bookcatalogue.PreferencesBase;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Utils;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.ValuePropertyWithGlobalDefault;

/**
 * Activity to manage the preferences associate with Book lists (and the BooksOnBookshelf activity).
 * 
 * @author Philip Warner
 */
public class BooklistPreferencesActivity extends PreferencesBase {

	/** Prefix for all preferences */
	public static final String TAG = "BookList.Global.";

	/** Show flat backgrounds in Book lists */
	public static final String PREF_FLAT_BACKGROUND = TAG + "FlatBackground";
	
	/** Key added to resulting Intent */
	public static final String PREF_CHANGED = TAG + "PrefChanged";
	/** Always expand/collapse/preserve book list state */
	public static final String PREF_BOOKLISTS_STATE = TAG + "BooklistState";

	// ID values for state preservation property
	public static final int BOOKLISTS_ALWAYS_EXPANDED = 1;
	public static final int BOOKLISTS_ALWAYS_COLLAPSED = 2;
	public static final int BOOKLISTS_STATE_PRESERVED = 3;

	/** Booklist state preservation property */
	private static ItemEntries<Integer> mBooklistStateListItems = new ItemEntries<Integer>();
	private static IntegerListProperty mBooklistStateProperty = new IntegerListProperty(
			mBooklistStateListItems, 
			PREF_BOOKLISTS_STATE, 
			PropertyGroup.GRP_GENERAL, 
			R.string.book_list_state, null, PREF_BOOKLISTS_STATE, BOOKLISTS_ALWAYS_EXPANDED);
	static {
		mBooklistStateListItems.add(null, R.string.use_default_setting);
		mBooklistStateListItems.add(BOOKLISTS_ALWAYS_EXPANDED, R.string.always_start_booklists_expanded);
		mBooklistStateListItems.add(BOOKLISTS_ALWAYS_COLLAPSED, R.string.always_start_booklists_collapsed);
		mBooklistStateListItems.add(BOOKLISTS_STATE_PRESERVED, R.string.remember_booklists_state);
		mBooklistStateProperty.setGlobal(true);
	}

	/** Flat Backgrounds property definition */
	private static ItemEntries<Boolean> mFlatBackgroundListItems = new ItemEntries<Boolean>();
	private static BooleanListProperty mFlatBackgroundProperty = new BooleanListProperty(
				mFlatBackgroundListItems, 
				PREF_FLAT_BACKGROUND, 
				PropertyGroup.GRP_GENERAL, 
				R.string.booklist_background_style, null, PREF_FLAT_BACKGROUND, false);
	static {
		mFlatBackgroundListItems.add(null, R.string.use_default_setting);
		mFlatBackgroundListItems.add(false, R.string.textured_backgroud);
		mFlatBackgroundListItems.add(true, R.string.plain_background_b_reduces_flicker_b);
		mFlatBackgroundProperty.setGlobal(true);
	}

	/**
	 * Build the activity UI
	 */
	@Override 
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);	
			setTitle(R.string.booklist_preferences);
			if (savedInstanceState == null)
				HintManager.displayHint(this, R.string.hint_booklist_global_properties, null);
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * Get the current preferred rebuild state for the list
	 * @return
	 */
	public static int getRebuildState() {
		return mBooklistStateProperty.get();
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
		addClickablePref(prefs, R.id.erase_cover_cache_label, new OnClickListener() {
			@Override
			public void onClick(View v) {
				Utils utils = new Utils();
				try {
					utils.eraseCoverCache();					
				} finally {
					utils.close();
				}
				return;
			}});

		addClickablePref(prefs, R.id.edit_styles_label, new OnClickListener() {
			@Override
			public void onClick(View v) {
				BooklistStyles.startEditActivity(BooklistPreferencesActivity.this);
			}});

		/**
		 * This activity predominantly shows 'Property' objects; we build that collection here.
		 */
		
		// Create a dummy style and add one group of each kind
		BooklistStyle style = new BooklistStyle("");
		for(int i: BooklistGroup.getRowKinds()) {
			if (i != RowKinds.ROW_KIND_BOOK)
				style.addGroup(i);
		}
		
		// Get all the properties from the style that have global defaults.
		Properties allProps = style.getProperties();
		Properties globalProps = new Properties();
		for(Property p: allProps) {
			if (p instanceof ValuePropertyWithGlobalDefault) {
				ValuePropertyWithGlobalDefault gp = (ValuePropertyWithGlobalDefault)p;
				if (gp.hasGlobalDefault()) {
					gp.setGlobal(true);
					globalProps.add(gp);
				}
			}
		}
		// Add the locally constructed porperties
		globalProps.add(mFlatBackgroundProperty);
		globalProps.add(mBooklistStateProperty);

		// Get the parent view and put the properties under it.
		ViewGroup styleProps = (ViewGroup) findViewById(R.id.style_properties);
		globalProps.buildView(getLayoutInflater(), styleProps);
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
	 * Convenience Accessor.
	 * 
	 * @return
	 */
	public static final boolean isBackgroundFlat() {
		return mFlatBackgroundProperty.getResolvedValue();
	}
}
