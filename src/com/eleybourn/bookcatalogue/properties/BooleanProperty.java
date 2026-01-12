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

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.BooleanValue;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Extends ValuePropertyWithGlobalDefault to create a trinary value (or nullable boolean?) with 
 * associated editing support.
 * Resulting editing display is a checkbox that cycles between 3 values.
 * 
 * @author Philip Warner
 */
public class BooleanProperty extends ValuePropertyWithGlobalDefault<Boolean> implements BooleanValue {

	public BooleanProperty(String uniqueId, PropertyGroup group, int nameResourceId, Boolean value, String preferenceKey, Boolean defaultValue) {
		super(uniqueId, group, nameResourceId, value, preferenceKey, defaultValue);
	}
	public BooleanProperty(String uniqueId, PropertyGroup group, int nameResourceId, String defaultPref) {
		super(uniqueId, group, nameResourceId, null, defaultPref, false);
	}
	public BooleanProperty(String uniqueId, PropertyGroup group, int nameResourceId, String preferenceKey, Boolean defaultValue) {
		super(uniqueId, group, nameResourceId, null, preferenceKey, defaultValue);
	}
	public BooleanProperty(String uniqueId, PropertyGroup group, int nameResourceId, Boolean value, Boolean defaultValue) {
		super(uniqueId, group, nameResourceId, value, null, defaultValue);
	}
	public BooleanProperty(String uniqueId, PropertyGroup group, int nameResourceId, Boolean value) {
		super(uniqueId, group, nameResourceId, value, null, false);
	}
	public BooleanProperty(String uniqueId, PropertyGroup group, int nameResourceId) {
		super(uniqueId, group, nameResourceId, false, null, false);
	}
	public BooleanProperty(String uniqueId) {
		super(uniqueId, PropertyGroup.GRP_GENERAL, R.string.unknown, false, null, false);
	}

	private static class Holder {
		CheckBox cb;
		TextView name;
		TextView value;
		BooleanProperty p;
	}
	@Override
	public View getView(LayoutInflater inflater) {
		// Get the view and setup holder
		View v = inflater.inflate(R.layout.property_value_boolean, null);
		final Holder h = new Holder();
	
		h.p = this;
		h.cb = v.findViewById(R.id.checkbox);
		h.name = v.findViewById(R.id.field_name);
		h.value = v.findViewById(R.id.value);
		
		ViewTagger.setTag(v, R.id.TAG_PROPERTY, h);
		ViewTagger.setTag(h.cb, R.id.TAG_PROPERTY, h);

		// Set the ID so weird stuff does not happen on activity reload after config changes.
		h.cb.setId(nextViewId());

		h.name.setText(this.getNameResourceId());

		// Set initial checkbox state
		Boolean b = get();
		setViewValues(h, b);

		// Setup click handlers for view and checkbox		
		h.cb.setOnClickListener(this::handleClick);

		v.setOnClickListener(this::handleClick);

		return v;
	}

	private void handleClick(View v) {
		Holder h = ViewTagger.getTag(v, R.id.TAG_PROPERTY);
		Boolean b = h.p.get();
		// Cycle through three values: 'null', 'true', 'false'. If the value is 'global' omit 'null'.
		if (b == null) {
			b = true;
		} else if (b) {
			b = false;
		} else {
			if (isGlobal())
				b = true;
			else
				b = null;
		}
		h.p.set(b);
		h.p.setViewValues(h, b);
	}

	/**
	 * Set the checkbox and text fields based on passed value.
	 */
	private void setViewValues(Holder h, Boolean b) {
		if (b != null) {
			// We have a value, so setup based on it
			h.cb.setChecked(b);
			h.name.setText(this.getNameResourceId());
			if (b)
				h.value.setText(R.string.option_yes);
			else
				h.value.setText(R.string.option_no);
			h.cb.setPressed(false);
		} else {
			// Null value; use defaults.
			Boolean resolved = getResolvedValue();
			if (resolved == null)
				resolved = false;
			h.cb.setChecked(resolved);
			h.name.setText(this.getName());
			h.value.setText(R.string.option_use_default_setting);
			h.cb.setPressed(false);
		}			
	}

	@Override
	protected Boolean getGlobalDefault() {
		return BookCatalogueApp.getAppPreferences().getBoolean(getPreferenceKey(), getDefaultValue());
	}
	@Override
	protected BooleanProperty setGlobalDefault(Boolean value) {
		BookCatalogueApp.getAppPreferences().setBoolean(getPreferenceKey(), value);
		return this;
	}

	@Override
	public BooleanProperty set(Property p) {
		if (! (p instanceof BooleanValue) )
			throw new RuntimeException("Can not find a compatible interface for boolean parameter");
		BooleanValue bv = (BooleanValue) p;
		set(bv.get());
		return this;
	}

	@Override
	public BooleanProperty setGlobal(boolean isGlobal) {
		super.setGlobal(isGlobal);
		return this;
	}
	
	@Override
	public BooleanProperty setDefaultValue(Boolean value) {
		super.setDefaultValue(value);
		return this;
	}

	/**
	 * Accessor
	 */
	public BooleanProperty setPreferenceKey(String key) {
		super.setPreferenceKey(key);
		return this;
	}

}

