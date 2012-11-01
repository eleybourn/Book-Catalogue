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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.ValidationException;

/**
 * Class to manage a set of properties.
 * 
 * @author Philip Warner
 */
public class Properties implements Iterable<Property> {
	private ArrayList<Property> mList = new ArrayList<Property>();
	private Hashtable<String,Property> mHash = new Hashtable<String,Property>();

	/**
	 * Sort the properties based on their group weight, group name, weight and name.
	 */
	private void sort() {
		Collections.sort(mList, new PropertyComparator());
	}

	/**
	 * Add a property to this collection
	 * 
	 * @param p
	 */
	public Properties add(Property p) {
		mList.add(p);
		mHash.put(p.getUniqueName(), p);
		return this;
	}

	/** 
	 * Get the named property from this collection.
	 * 
	 * @param name
	 * @return
	 */
	public Property get(String name) {
		return mHash.get(name);
	}

	/**
	 * Passed a parent ViewGroup, build the property editors for all properties
	 * inside the parent.
	 * 
	 * @param inflater
	 * @param parent
	 */
	public void buildView(LayoutInflater inflater, ViewGroup parent) {
		// Sort them correctly
		sort();
		// Record last group used, so we know when to output a header.
		PropertyGroup lastGroup = null;
		// Loop
		for(Property p: mList) {
			PropertyGroup currGroup = p.getGroup();
			if (currGroup != lastGroup) {
				// Add a new header
				View v = inflater.inflate(R.layout.property_group, null);
				TextView text = (TextView) v.findViewById(R.id.name);
				if (text != null)
					text.setText(currGroup.nameId);
				parent.addView(v);
			}
			// Just add the property editor
			View pv = p.getView(inflater);
			parent.addView(pv);
			lastGroup = currGroup;
		}
	}

	/**
	 * Iterator
	 */
	@Override
	public Iterator<Property> iterator() {
		return mList.iterator();
	}

	/**
	 * Call the validate() method on all properties. Errors will be thrown.
	 */
	public void validate() throws ValidationException {
		for(Property p: mList) {
			p.validate();
		}
	}
}
