package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.scanner.pic2shop.Scan;

/**
 * Based on the pic2shop client code at github, this object will start pic2shop and extract the data
 * from the resulting intent when the activity completes.
 * 
 * It also has a static method to check if the intent is present.
 * 
 * @author pjw
 */
public class Pic2ShopScanner implements Scanner {
	private static final String BARCODE_FORMAT = "EAN13";

	/**
	 * Check if we have a valid intent available.
	 * @return true if present
	 */
	public static boolean isIntentAvailable() {
		if (com.eleybourn.bookcatalogue.scanner.pic2shop.Utils.isFreeScannerAppInstalled(BookCatalogueApp.context)) {
			return true;
		} else if (com.eleybourn.bookcatalogue.scanner.pic2shop.Utils.isProScannerAppInstalled(BookCatalogueApp.context)) {
			return true;
		} else {
			return false;
		}
	}

	private Handler mHandler = new Handler();
	/**
	 * Start the activity with the passed request code.
	 * 
	 * Note that we always send an intent; the caller should have checked that
	 * one of the intents is valid, or catch the resulting errors.
	 */
	@Override
	public void startActivityForResult(final Activity a, final int requestCode) {
		Intent i = null;
		if (com.eleybourn.bookcatalogue.scanner.pic2shop.Utils.isFreeScannerAppInstalled(a)) {
			i = new Intent(Scan.ACTION);
			//i.putExtra(Scan.Pro.FORMATS, BARCODE_FORMAT);		
		} else {
			i = new Intent(Scan.Pro.ACTION);
			i.putExtra(Scan.Pro.FORMATS, BARCODE_FORMAT);		
		}

		a.startActivityForResult(i, requestCode);
	}

	/**
	 * Extract the barcode from the result
	 */
	@Override
	public String getBarcode(Intent intent) {
		String barcode = intent.getStringExtra(Scan.BARCODE);
		String barcodeFormat = intent.getStringExtra(Scan.Pro.FORMAT);
		if (barcodeFormat != null && !barcodeFormat.equalsIgnoreCase(BARCODE_FORMAT)) {
			throw new RuntimeException("Unexpected format for barcode: " + barcodeFormat);
		}
		return barcode;
	}
	
}
