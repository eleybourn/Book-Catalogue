package com.eleybourn.bookcatalogue;

public class AsinUtils {

	private static final String mValidAsinCharacters = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	
	/**
	 * Validate an ASIN
	 * 
	 * Amazon have apparently designed ASINs without (public) validation methods. All we can do is check length and characters;
	 * the apparent rule is that it must be an ISBN10 or be a 10 character string containing at least on nonnumeric. 
	 * 
	 * @param asin
	 * @return
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
