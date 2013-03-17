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

package com.eleybourn.bookcatalogue.properties;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Property.BooleanValue;

/**
 * Extends ListProperty to create a trinary value (or nullable boolean?) with associated editing support.
 * 
 * Resulting editing display is a list of values in a dialog.
 * 
 * @author Philip Warner
 */
public class BooleanListProperty extends ListProperty<Boolean> implements BooleanValue {

	public BooleanListProperty(ItemEntries<Boolean> list, String uniqueId, PropertyGroup group, int nameResourceId, Boolean value, String defaultPref, Boolean defaultValue) {
		super(list, uniqueId, group, nameResourceId, value, defaultPref, defaultValue);
	}
	public BooleanListProperty(ItemEntries<Boolean> list, String uniqueId, PropertyGroup group, int nameResourceId, String defaultPref) {
		super(list, uniqueId, group, nameResourceId, null, defaultPref, false);
	}
	public BooleanListProperty(ItemEntries<Boolean> list, String uniqueId, PropertyGroup group, int nameResourceId, Boolean value, Boolean defaultValue) {
		super(list, uniqueId, group, nameResourceId, value, null, defaultValue);
	}
	public BooleanListProperty(ItemEntries<Boolean> list, String uniqueId, PropertyGroup group, int nameResourceId, Boolean value) {
		super(list, uniqueId, group, nameResourceId, value, null, false);
	}
	public BooleanListProperty(ItemEntries<Boolean> list, String uniqueId, PropertyGroup group, int nameResourceId) {
		super(list, uniqueId, group, nameResourceId, null, null, false);
	}
	public BooleanListProperty(ItemEntries<Boolean> list, String uniqueId) {
		super(list, uniqueId, PropertyGroup.GRP_GENERAL, R.string.unknown, null, null, null);
	}

	@Override
	protected Boolean getGlobalDefault() {
		return BookCatalogueApp.getAppPreferences().getBoolean(getPreferenceKey(), getDefaultValue());
	}
	@Override
	protected BooleanListProperty setGlobalDefault(Boolean value) {
		BookCatalogueApp.getAppPreferences().setBoolean(getPreferenceKey(), value);		
		return this;
	}

	@Override
	public BooleanListProperty set(Property p) {
		if (! (p instanceof BooleanValue) )
			throw new RuntimeException("Can not find a compatible interface for boolean parameter");
		BooleanValue v = (BooleanValue) p;
		set(v.get());
		return this;
	}

}

