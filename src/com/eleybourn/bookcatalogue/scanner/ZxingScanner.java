package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * This object will start a Zxing compatible scanner and extract the data
 * from the resulting intent when the activity completes.
 * 
 * It also has a static method to check if the intent is present.
 * 
 * @author pjw
 */
public class ZxingScanner implements Scanner {
	public static final String ACTION = "com.google.zxing.client.android.SCAN";

	/**
	 * Check if we have a valid intent available.
	 * @return true if present
	 */
	public static boolean isIntentAvailable() {
		return isIntentAvailable(BookCatalogueApp.context, ACTION);
	}

	/**
	 * Start the activity with the passed request code.
	 */
	@Override
	public void startActivityForResult(Activity a, int requestCode) {
		Intent i = new Intent(ACTION);
		a.startActivityForResult(i, requestCode);
	}

	/**
	 * Check if the passed intent action is available.
	 * 
	 * @return true if present
	 */
	private static boolean isIntentAvailable(Context ctx, String action) {
		Intent test = new Intent(ACTION);
		return ctx.getPackageManager().resolveActivity(test, 0) != null;
	}

	/**
	 * Extract the barcode from the result
	 */
	@Override
	public String getBarcode(Intent intent) {
		return intent.getStringExtra("SCAN_RESULT");
	}

}
