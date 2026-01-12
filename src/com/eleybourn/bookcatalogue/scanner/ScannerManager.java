package com.eleybourn.bookcatalogue.scanner;

import java.util.TreeMap;

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

	/** Unique IDs to associate with each supported scanner intent; we changes them in version 200 to force builtin */
	public static final int SCANNER_ZXING_COMPATIBLE = 101;
	public static final int SCANNER_PIC2SHOP = 102;
	public static final int SCANNER_ZXING = 103;
	public static final int SCANNER_BUILTIN = 4;

	/** 
	 * Support for creating scanner objects on the fly without know which ones are available.
	 * 
	 * @author pjw
	 */
	private interface ScannerFactory {
		/** Create a new scanner of the related type */
		Scanner newInstance();
		/** Check if this scanner is available */
		boolean isIntentAvailable();
	}

	/**
	 * Collection of ScannerFactory objects
	 */
	private static final TreeMap<Integer,ScannerFactory> myScannerFactories = new TreeMap<>();
	/*
	 * Build the collection
	 */
	static {
		myScannerFactories.put(SCANNER_BUILTIN, new ScannerFactory() {
			@Override
			public Scanner newInstance() {
				return new BuiltinScanner();
			}

			@Override
			public boolean isIntentAvailable() {
				return true;
			}});

		myScannerFactories.put(SCANNER_ZXING_COMPATIBLE, new ScannerFactory() {
			@Override
			public Scanner newInstance() {
				return new ZxingScanner(false);
			}

			@Override
			public boolean isIntentAvailable() {
				return ZxingScanner.isIntentAvailable(false);
			}});

		myScannerFactories.put(SCANNER_ZXING, new ScannerFactory() {
			@Override
			public Scanner newInstance() {
				return new ZxingScanner(true);
			}

			@Override
			public boolean isIntentAvailable() {
				return ZxingScanner.isIntentAvailable(true);
			}});

		myScannerFactories.put(SCANNER_PIC2SHOP, new ScannerFactory() {
			@Override
			public Scanner newInstance() {
				return new Pic2ShopScanner();
			}

			@Override
			public boolean isIntentAvailable() {
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
		int prefScanner = BookCatalogueApp.getAppPreferences().getInt( PREF_PREFERRED_SCANNER, SCANNER_BUILTIN);

		// See if preferred one is present, if so return a new instance
		ScannerFactory psf = myScannerFactories.get(prefScanner);
		if (psf != null && psf.isIntentAvailable()) {
			return psf.newInstance();
		}

		// Search all supported scanners. If none, just return a Builtin one
		for(ScannerFactory sf: myScannerFactories.values()) {
			if (sf != psf && sf.isIntentAvailable()) {
				return sf.newInstance();
			}
		}
		return new BuiltinScanner();
	}

}
