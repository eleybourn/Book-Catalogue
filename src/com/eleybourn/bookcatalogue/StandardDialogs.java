package com.eleybourn.bookcatalogue;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class StandardDialogs {

	public static void deleteSeriesAlert(Context context, final CatalogueDBAdapter dbHelper, final Series series, final Runnable onDeleted) {

		// When we get here, we know the names are genuinely different and the old series is used in more than one place.
		String message = String.format(context.getResources().getString(R.string.really_delete_series), series.name);
		final AlertDialog alertDialog = new AlertDialog.Builder(context).setMessage(message).create();

		alertDialog.setTitle(R.string.delete_series);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dbHelper.deleteSeries(series);
				alertDialog.dismiss();
				onDeleted.run();
			}
		});

		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				alertDialog.dismiss();
			}
		}); 

		alertDialog.show();
	}

}
