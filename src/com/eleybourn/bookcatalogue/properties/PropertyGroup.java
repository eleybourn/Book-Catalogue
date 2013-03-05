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

import java.util.Hashtable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

/**
 * Class used to manage groups of properties in the UI
 * 
 * @author Philip Warner
 */
public class PropertyGroup {

	// Unique IDs for each group.
	private static int GRP_COUNT = 0;
	private static final int GRP_GENERAL_ID = ++GRP_COUNT;
	private static final int GRP_EXTRA_BOOK_DETAILS_ID = ++GRP_COUNT;
	private static final int GRP_AUTHOR_ID = ++GRP_COUNT;
	private static final int GRP_SERIES_ID = ++GRP_COUNT;
	private static final int GRP_EXTRA_FILTERS_ID = ++GRP_COUNT;
	private static final int GRP_USER_INTERFACE_ID = ++GRP_COUNT;
	private static final int GRP_THUMBNAILS_ID = ++GRP_COUNT;
	private static final int GRP_SCANNER_ID = ++GRP_COUNT;
	private static final int GRP_ADVANCED_OPTIONS_ID = ++GRP_COUNT;

	// Collection of all groups.
	private static final PropertyGroups mGroups = new PropertyGroups();

	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_GENERAL = mGroups.addGroup(GRP_GENERAL_ID, R.string.general, 0);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_EXTRA_BOOK_DETAILS = mGroups.addGroup(GRP_EXTRA_BOOK_DETAILS_ID, R.string.extra_book_details, 100);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_AUTHOR = mGroups.addGroup(GRP_AUTHOR_ID, R.string.author, 50);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_SERIES = mGroups.addGroup(GRP_SERIES_ID, R.string.series, 50);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_EXTRA_FILTERS = mGroups.addGroup(GRP_EXTRA_FILTERS_ID, R.string.extra_filters, 70);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_THUMBNAILS = mGroups.addGroup(GRP_THUMBNAILS_ID, R.string.thumbnails, 40);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_USER_INTERFACE = mGroups.addGroup(GRP_USER_INTERFACE_ID, R.string.user_interface, 35);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_SCANNER = mGroups.addGroup(GRP_SCANNER_ID, R.string.scanning, 70);
	/** Global PropertyGroup definition */
	public static final PropertyGroup GRP_ADVANCED_OPTIONS = mGroups.addGroup(GRP_ADVANCED_OPTIONS_ID, R.string.advanced_options, 80);

	/**
	 * Collection class for all PropertyGroups
	 * 
	 * @author Philip Warner
	 */
	public static class PropertyGroups extends Hashtable<Integer,PropertyGroup> {
		private static final long serialVersionUID = 1L;

		/**
		 * Add the passed group
		 *
		 * @param g
		 * @return
		 */
		public PropertyGroup addGroup(PropertyGroup g) {
			if (this.containsKey(g.id) && (this.get(g.id) != g))
				throw new RuntimeException("Duplicate PropertyGroup ID " + g.id);

			this.put(g.id , g);
			return g;
		}
		/**
		 * Construct and add a group based on parameters 
		 * 
		 * @param id
		 * @param nameId
		 * @param weight
		 * 
		 * @return
		 */
		public PropertyGroup addGroup(int id, int nameId, int weight) {
			PropertyGroup g = new PropertyGroup(id, nameId, weight);
			addGroup(g);
			return g;
		}
	}

	/** ID of this group */
	private final int id;
	/** String resource ID for group name */
	final int nameId;
	/** Weight of this group, for sorting */
	final Integer weight;
	/** Name of this group (from resource ID) */
	private String mName = null;

	/** Constructor */
	private PropertyGroup(int id, int nameId, int weight) {
		this.id = id;
		this.nameId = nameId;
		this.weight = weight;
		mGroups.addGroup(this);
	}
	/** Realize and return the group name */
	public String getName() {
		if (mName == null)
			mName = BookCatalogueApp.getResourceString(nameId);
		return mName;
	}

	/**
	 * Compare two groups for sorting purposes 
	 *
	 * @param lhs
	 * @param rhs
	 * @return
	 */
	public static int compare(PropertyGroup lhs, PropertyGroup rhs) {
		// Compare weights
		final int wCmp =  lhs.weight.compareTo(rhs.weight);
		if (wCmp != 0)
			return wCmp;

		// Weights match, compare names
		if (lhs.nameId != rhs.nameId)
			return lhs.getName().compareTo(rhs.getName());
		else
			return 0;
	}

}
