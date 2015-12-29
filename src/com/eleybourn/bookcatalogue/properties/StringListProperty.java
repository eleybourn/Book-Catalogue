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

/**
 * Extends ListProperty to create a nullable integer property with associated editing support.
 * 
 * @author Philip Warner
 */
public class StringListProperty extends ListProperty<String> implements Property.StringValue {

	public StringListProperty(ItemEntries<String> list, String uniqueId, PropertyGroup group, int nameResourceId, String value, String defaultPref, String defaultValue) {
		super(list, uniqueId, group, nameResourceId, value, defaultPref, defaultValue);
	}
	public StringListProperty(ItemEntries<String> list, String uniqueId, PropertyGroup group, int nameResourceId, String value, String defaultValue) {
		super(list, uniqueId, group, nameResourceId, value, null, defaultValue);
	}
	public StringListProperty(ItemEntries<String> list, String uniqueId, PropertyGroup group, int nameResourceId, String value) {
		super(list, uniqueId, group, nameResourceId, value, null, value);
	}
	public StringListProperty(ItemEntries<String> list, String uniqueId, PropertyGroup group, int nameResourceId) {
		super(list, uniqueId, group, nameResourceId, null, null, null);
	}
	public StringListProperty(ItemEntries<String> list, String uniqueId) {
		super(list, uniqueId, PropertyGroup.GRP_GENERAL, R.string.unknown, null, null, null);
	}

	@Override
	protected String getGlobalDefault() {
		return BookCatalogueApp.getAppPreferences().getString(getPreferenceKey(), getDefaultValue());
	}
	@Override
	protected StringListProperty setGlobalDefault(String value) {
		BookCatalogueApp.getAppPreferences().setString(getPreferenceKey(), value);
		return this;
	}

	@Override
	public StringListProperty set(Property p) {
		if (! (p instanceof StringValue) )
			throw new RuntimeException("Can not find a compatible interface for integer parameter");
        StringValue v = (StringValue) p;
		set(v.get());
		return this;
	}
}

