package com.eleybourn.bookcatalogue.scanner.pic2shop;

/**
 * Based on code from pic2shop sample at github
 */
public final class Scan {

	/**
	 * When a barcode is read, pic2shop returns Activity.RESULT_OK in
	 * onActivityResult() of the activity which requested the scan using
	 * startActivityForResult(). The read barcode can be retrieved with
	 * intent.getStringExtra(BARCODE).<br>
	 * If the user exits pic2shop by pressing Back before a barcode is read, the
	 * result code will be Activity.RESULT_CANCELED in onActivityResult().
	 */
	public static final String PACKAGE = "com.visionsmarts.pic2shop";
	public static final String ACTION = PACKAGE + ".SCAN";
	// response Intent
	public static final String BARCODE = "BARCODE";

	public interface Pro {

		String PACKAGE = "com.visionsmarts.pic2shoppro";
		String ACTION = PACKAGE + ".SCAN";

		// request Intent
        String FORMATS = "formats";
		// response Intent
        String FORMAT = "format";
	}

	private Scan() {
	}
}