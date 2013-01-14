package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Intent;

/**
 * Interface defining required methods for any external scanner interface.
 * 
 * @author pjw
 */
public interface Scanner {
	/** Request a scan */
	public void startActivityForResult(Activity a, int requestCode);
	/** Get the barcode from the resulting intent */
	public String getBarcode(Intent intent);
}
