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

import java.util.Comparator;

/**
 * Class to compare to properties for the purpose of sorting.
 * 
 * @author Philip Warner
 */
public class PropertyComparator implements Comparator<Property> {
	@Override
	public int compare(Property lhs, Property rhs) {	
		// First compare their groups
		int gCmp = PropertyGroup.compare(lhs.getGroup(), rhs.getGroup());
		if (gCmp != 0)
			return gCmp;

		// Same group, compare weights
		if (lhs.getWeight() < rhs.getWeight())
			return -1;
		else if (lhs.getWeight() > rhs.getWeight())
			return 1;

		// Same weights, compare names
		if (lhs.getNameResourceId() != rhs.getNameResourceId()) 
			return lhs.getName().compareTo(rhs.getName());
		else
			return 0;
	}
}

