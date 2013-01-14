package com.eleybourn.bookcatalogue.scanner;

import java.util.Hashtable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * Class to handle details of specific scanner interfaces and return a 
 * Scanner object to the caller.
 * 
 * @author pjw
 */
public class ScannerManager {
	/** Preference key */
	public static final String PREF_PREFERRED_SCANNER = "ScannerManager.PreferredScanner";

	/** Unique IDs to associate with each supported scanner intent */
	public static final int SCANNER_ZXING_COMPATIBLE = 1;
	public static final int SCANNER_PIC2SHOP = 2;

	/** 
	 * Support for creating scanner objects on the fly without know which ones are available.
	 * 
	 * @author pjw
	 */
	private interface ScannerFactory {
		/** Create a new scanner of the related type */
		public Scanner newInstance();
		/** Check if this scanner is available */
		public boolean isIntentAvaiable();
	}

	/**
	 * Collection of ScannerFactory objects
	 */
	private static final Hashtable<Integer,ScannerFactory> myScannerFactories = new Hashtable<Integer,ScannerFactory>();
	/**
	 * Build the collection
	 */
	static {
		myScannerFactories.put(SCANNER_ZXING_COMPATIBLE, new ScannerFactory() {
			@Override
			public Scanner newInstance() {
				return new ZxingScanner();
			}

			@Override
			public boolean isIntentAvaiable() {
				return ZxingScanner.isIntentAvailable();
			}});

		myScannerFactories.put(SCANNER_PIC2SHOP, new ScannerFactory() {
			@Override
			public Scanner newInstance() {
				return new Pic2ShopScanner();
			}

			@Override
			public boolean isIntentAvaiable() {
				return Pic2ShopScanner.isIntentAvailable();
			}});
	}

	/**
	 * Return a Scanner object based on the current environment and user preferences.
	 * 
	 * @return	A Scanner
	 */
	public static Scanner getScanner() {
		// Find out what the user prefers if any
		int prefScanner = BookCatalogueApp.getAppPreferences().getInt( PREF_PREFERRED_SCANNER, SCANNER_ZXING_COMPATIBLE);

		// See if preferred one is present, if so return a new instance
		ScannerFactory psf = myScannerFactories.get(prefScanner);
		if (psf != null && psf.isIntentAvaiable()) {
			return psf.newInstance();
		}

		// Search all supported scanners. If none, just return a Zxing one
		for(ScannerFactory sf: myScannerFactories.values()) {
			if (sf != psf && sf.isIntentAvaiable()) {
				return sf.newInstance();
			}
		}
		return new ZxingScanner();
	}

}
