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

import java.util.Arrays;

public class IsbnUtils {

	private static class IsbnInfo {
		public int[] digits;
		public int size;
		public boolean foundX;
		public boolean isValid;
		
		public IsbnInfo(String isbn) {
			foundX = false;
			digits = new int[13];
			size = 0;
			for(int i = 0; i < isbn.length(); i++) {
				final Character c = isbn.charAt(i);
				int val;
				if (Character.isDigit(c)) {
					// X can only be at end of an ISBN10
					if (foundX) {
						isValid = false;
						return;
					}
					val = Integer.parseInt(c.toString());
				} else if (Character.toUpperCase(c) == 'X' && size == 9) {
					// X can only be at end of an ISBN10
					if (foundX) {
						isValid = false;
						return;						
					}
					val = 10;
					foundX = true;
				} else {
					// Invalid character
					isValid = false;
					return;
				}

				// Check if too long
				if (size >= 13) {
					isValid = false;
					return;					
				}
				digits[size] = val;
				size++;
			}
			if (size == 10) {
				isValid = isValidIsbn10(digits);
			} else if (size == 13) {
				isValid = isValidIsbn13(digits);
			} else {
				isValid = false;
			}						
		}
		
		public boolean equals(IsbnInfo cmp) {
			// If either is an invalid ISBN, require they match exactly
			if (!this.isValid || !cmp.isValid) {
				if (this.size != cmp.size)
					return false;
				return  digitsMatch(this.size, this.digits, 0, cmp.digits, 0);
			}

			// We know the lengths are either 10 or 13 when we get here. So ... compare
			if (this.size == 10) {
				if (cmp.size == 10) {
					return digitsMatch(9, this.digits, 0, cmp.digits, 0);
				} else {
					return digitsMatch(9, this.digits, 0, cmp.digits, 3);
				}
			} else {
				if (cmp.size == 13) {
					return digitsMatch(13, this.digits, 0, cmp.digits, 0);
				} else {
					return digitsMatch(9, this.digits, 3, cmp.digits, 0);
				}
				
			}
		}
		
		private static boolean digitsMatch(final int len, final int[] dig1, int pos1, final int[] dig2, int pos2) {
			for(int i = 0; i < len; i++) {
				if (dig1[pos1++] != dig2[pos2++])
					return false;
			}
			return true;
		}
		
		public String getIsbn10() {
			StringBuilder sb = new StringBuilder();
			int[] vals;
			if (size == 10) {
				vals = digits;
			} else {
				int p = 0;
				vals = new int[10];
				for(int i = 3; i < 12; i++) {
					vals[p++] = digits[i];
				}
				vals[9] = 0;
				vals[9] = (11 - getIsbn10Check(vals)) % 11;
			}
			for(int i = 0; i < 10; i++) {
				final int d = vals[i];
				if (d == 10) {
					sb.append('X');					
				} else {
					sb.append(d);					
				}
			}
			return sb.toString();
		}

		public String getIsbn13() {
			StringBuilder sb = new StringBuilder();
			int[] vals;
			if (size == 13) {
				vals = digits;
			} else {
				vals = new int[13];
				vals[0] = 9;
				vals[1] = 7;
				vals[2] = 8;
				int p = 3;
				for(int i = 0; i < 9; i++) {
					vals[p++] = digits[i];					
				}
				vals[12] = 0;
				vals[12] = (10 - getIsbn13Check(vals)) % 10;
			}

			for(int d: vals) {
				if (d == 10) {
					sb.append('X');					
				} else {
					sb.append(d);					
				}
			}
			return sb.toString();
		}
	}
	/**
	 * Validate an ISBN
	 */
	public static boolean isValid(String isbn) {
		try {
			IsbnInfo info = new IsbnInfo(isbn);
			return info.isValid;
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
     * Validate an ISBN10.
     * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">...</a>
     */
	private static int getIsbn10Check(int[] digits) {
		int mult = 10;
		int check = 0;
		for(int i = 0; i < 10; i++) {
			check += digits[i] * mult;
			mult--;
		}
		return (check % 11);
	}

	/**
     * Validate an ISBN10.
     * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">...</a>
     */
	private static boolean isValidIsbn10(int[] digits) {
		return (getIsbn10Check(digits) == 0);
	}

	/**
     * Validate an ISBN10.
     * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">...</a>
     */
	private static int getIsbn13Check(int[] digits) {
		int check = 0;
	    for (int i = 0; i <= 12; i += 2) {
	        check += digits[i];
	    }
	    for (int i = 1; i < 12; i += 2) {
	        check += digits[i] * 3;
	    }
	    return (check % 10);
	}

	/**
     * Validate an ISBN10.
     * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">...</a>
     */
	private static boolean isValidIsbn13(int[] digits) {
		// Start with 978 or 979
		if (digits[0] != 9 || digits[1] != 7 || (digits[2] != 8 && digits[2] != 9))
			return false;

	    return (getIsbn13Check(digits) == 0);
	}

	public static boolean matches(String isbn1, String isbn2) {
		final int l1 = isbn1.length();
		final int l2 = isbn2.length();
		// Deal with the trivial case
		if (l1 == l2)
			return isbn1.equalsIgnoreCase(isbn2);

		// Different lengths; sanity check...if either is invalid, we consider them different
		IsbnInfo info1 = new IsbnInfo(isbn1);
		if (!info1.isValid)
			return false;

		IsbnInfo info2 = new IsbnInfo(isbn2);
		if (!info2.isValid)
			return false;

		return info1.equals(info2);
	}

	public static String isbn2isbn(String isbn) {
		IsbnInfo info = new IsbnInfo(isbn);
		if (!info.isValid)
			throw new RuntimeException("Unable to convert invalid ISBN");

		if (isbn.length() == 10) {
			return info.getIsbn13();
		} else {
			return info.getIsbn10();
		}
	}
}
