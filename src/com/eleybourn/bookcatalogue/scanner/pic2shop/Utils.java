package com.eleybourn.bookcatalogue.scanner.pic2shop;

import android.content.Context;
import android.content.Intent;

/**
 * Based on code from pic2shop sample at github
 */
public final class Utils {

    public static boolean isFreeScannerAppInstalled(Context ctx) {
		return isIntentAvailable(ctx, Scan.ACTION);
	}

	public static boolean isProScannerAppInstalled(Context ctx) {
		return isIntentAvailable(ctx, Scan.Pro.ACTION);
	}

    private static boolean isIntentAvailable(Context ctx, String action) {
		Intent test = new Intent(action);
		return ctx.getPackageManager().resolveActivity(test, 0) != null;
	}

}
