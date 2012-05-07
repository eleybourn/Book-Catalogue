package com.eleybourn.bookcatalogue;

public class IsbnUtils {
	/**
	 * Validate an ISBN
	 * 
	 * @param isbn
	 * @return
	 */
	public static boolean isValid(String isbn) {
		try {
			boolean foundX = false;
			int[] digits = new int[13];
			int size = 0;
			for(int i = 0; i < isbn.length(); i++) {
				final Character c = isbn.charAt(i);
				int val;
				if (Character.isDigit(c)) {
					// X can only be at end of an ISBN10
					if (foundX)
						return false;
					val = Integer.parseInt(c.toString());
				} else if (Character.toUpperCase(c) == 'X' && size == 9) {
					// X can only be at end of an ISBN10
					if (foundX)
						return false;
					val = 10;
					foundX = true;
				} else {
					// Invalid character
					return false;
				}

				// Check if too long
				if (size >= 13)
					return false;
				digits[size] = val;
				size++;
			}
			if (size == 10) {
				return isValidIsbn10(digits);
			} else if (size == 13) {
				return isValidIsbn13(digits);
			} else {
				return false;
			}			
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Validate an ISBN10.
	 * See http://en.wikipedia.org/wiki/International_Standard_Book_Number
	 * 
	 * @param digits
	 * @return
	 */
	private static boolean isValidIsbn10(int[] digits) {
		int mult = 10;
		int check = 0;
		for(int i = 0; i < 10; i++) {
			check += digits[i] * mult;
			mult--;
		}
		return ((check % 11) == 0);
	}

	/**
	 * Validate an ISBN10.
	 * See http://en.wikipedia.org/wiki/International_Standard_Book_Number
	 * 
	 * @param digits
	 * @return
	 */
	private static boolean isValidIsbn13(int[] digits) {
		// Start with 978 or 979
		if (digits[0] != 9 || digits[1] != 7 || (digits[2] != 8 && digits[2] != 9))
			return false;

		int check = 0;
	    for (int i = 0; i <= 12; i += 2) {
	        check += digits[i];
	    }
	    for (int i = 1; i < 12; i += 2) {
	        check += digits[i] * 3;
	    }
	    return (check % 10) == 0;
	}

}
