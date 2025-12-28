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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Base class to display simple preference-based optins to the user.
 * 
 * @author Philip Warner
 */
public abstract class PreferencesBase extends BookCatalogueActivity {

	/** Get the layout of the subclass */
	public abstract int getLayout();
	/** Setup the views in the layout */
	public abstract void setupViews(BookCataloguePreferences prefs, Properties globalProps);
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			System.out.println("In onCreate in PreferencesBase");
			setContentView(this.getLayout());
			final BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

			// Get a properties collection.
			Properties globalProps = new Properties();
			setupViews(prefs, globalProps);

			ViewGroup styleProps = findViewById(R.id.dynamic_properties);
			globalProps.buildView(getLayoutInflater(), styleProps);

		} catch (Exception e) {
			Logger.logError(e);
		}
		
	}

	/**
	 * Utility routine to setup a checkobox based on a preference.
	 * 
	 * @param prefs		Preferences to use
	 * @param cbId		CheckBox ID from XML file
	 * @param viewId	Containing ViewGroup from XML file (for clicking and highlighting)
	 * @param key		Preferences key associated with this CheckBox
	 */
	protected void addBooleanPreference(final BookCataloguePreferences prefs, final int cbId, int viewId, final String key, final boolean defaultValue) {
		// Setup the checkbox
		{
			CheckBox v = this.findViewById(cbId);
			v.setChecked(prefs.getBoolean(key, defaultValue));
			v.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					prefs.setBoolean(key, isChecked);
				}});
		}
		// Allow clicking of entire row.
		{
			View v = this.findViewById(viewId);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					CheckBox cb = v.findViewById(cbId);
					cb.setChecked(!prefs.getBoolean(key, defaultValue));
				}});
		}
	}

	// Add an item that has a creator-define click event
	public void addClickablePref(final BookCataloguePreferences prefs, final int viewId, final OnClickListener listener) {
		/* Erase covers cache Link */
		View v = findViewById(viewId);
		// Make line flash when clicked.
		v.setBackgroundResource(android.R.drawable.list_selector_background);
		v.setOnClickListener(listener);
	}
}
