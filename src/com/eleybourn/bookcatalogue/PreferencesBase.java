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
import android.view.ViewGroup;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Base class to display simple preference-based options to the user.
 * 
 * @author Philip Warner
 */
public abstract class PreferencesBase extends BookCatalogueActivity {

	/** Get the layout of the subclass */
	public abstract int getLayout();
    public abstract int getPageTitle();
	/** Setup the views in the layout */
	public abstract void setupViews(BookCataloguePreferences prefs, Properties globalProps);
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			System.out.println("In onCreate in PreferencesBase");
			setContentView(this.getLayout());
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            setSupportActionBar(topAppBar);
            topAppBar.setTitle(this.getPageTitle());
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

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

}
