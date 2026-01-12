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
package com.eleybourn.bookcatalogue.utils;

public class AsinUtils {

	private static final String mValidAsinCharacters = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	
	/**
	 * Validate an ASIN
	 * Amazon have apparently designed ASINs without (public) validation methods. All we can do is check length and characters;
	 * the apparent rule is that it must be an ISBN10 or be a 10 character string containing at least on nonnumeric.
	 */
	public static boolean isValid(String asin) {
		try {
			asin = asin.toUpperCase().trim();
			if (asin.length() != 10)
				return false;

			// Check 10 char field for being ISBN; if true, then is also ASIN
			if (IsbnUtils.isValid(asin))
				return true;

			boolean foundAlpha = false;
			for(int i = 0; i < asin.length(); i++) {
				int pos = mValidAsinCharacters.indexOf(asin.charAt(i));
				// Make sure it's a valid char
				if (pos == -1)
					return false;
				// See if we got a non-numeric
				if (pos >= 10)
					foundAlpha = true;
			}
			return foundAlpha;
		} catch (Exception e) {
			return false;
		}
	}
}
