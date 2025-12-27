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

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.StringValue;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Implement a String-based property with optional (non-empty) validation.
 * 
 * @author Philip Warner
 */
public class StringProperty extends ValuePropertyWithGlobalDefault<String> implements StringValue {
	/** Flag indicating value must be non-blabnk */
	private boolean mRequireNonBlank = false;

	public StringProperty(String uniqueId, PropertyGroup group, int nameResourceId, String value, String defaultPref, String defaultValue) {
		super(uniqueId, group, nameResourceId, value, defaultPref, defaultValue);
	}
	public StringProperty(String uniqueId, PropertyGroup group, int nameResourceId, String defaultPref) {
		super(uniqueId, group, nameResourceId, null, defaultPref, "");
	}
	public StringProperty(String uniqueId, PropertyGroup group, int nameResourceId) {
		super(uniqueId, group, nameResourceId, null, null, "");
	}

	/** Accessor */
	public void setRequireNonBlank(boolean requireNonBlank) {
		mRequireNonBlank = requireNonBlank;
	}

	/** Accessor */
	public boolean getRequireNonBlank() {
		return mRequireNonBlank;
	}

	/**
	 * Build the editor for this property
	 */
	@Override
	public View getView(LayoutInflater inflater) {
		// Get base view and components. Tag them.
		View v = inflater.inflate(R.layout.property_value_string, null);
		ViewTagger.setTag(v, R.id.TAG_PROPERTY, this);
		final TextView name = (TextView)v.findViewById(R.id.field_name);
		final EditText value = (EditText)v.findViewById(R.id.value);

		// Set the current values
		name.setText(getName());
		value.setHint(getName());
		value.setText(get());

		// Reflect all changes in underlying data
		value.addTextChangedListener(new TextWatcher(){
			@Override
			public void afterTextChanged(Editable s) {
				set(s.toString());
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }
		});

		return v;
	}

	/**
	 * Get underlying preferences value
	 */
	@Override
	protected String getGlobalDefault() {
		return BookCatalogueApp.getAppPreferences().getString(getPreferenceKey(), getDefaultValue());
	}
	/**
	 * Set underlying preferences value
	 */
	@Override
	protected StringProperty setGlobalDefault(String value) {
		BookCatalogueApp.getAppPreferences().setString(getPreferenceKey(), value);	
		return this;
	}

	@Override
	public StringProperty set(Property p) {
		if (! (p instanceof StringValue) )
			throw new RuntimeException("Can not find a compatible interface for string parameter");
		StringValue bv = (StringValue) p;
		set(bv.get());
		return this;
	}

	/**
	 * Optional validator.
	 */
	@Override
	public void validate() {
		if (getRequireNonBlank()) {
			String s = get();
			if (s == null || s.trim().equals(""))
				throw new ValidationException(BookCatalogueApp.getResourceString(R.string.thing_must_not_be_blank, getName()));
		}
	}

}

