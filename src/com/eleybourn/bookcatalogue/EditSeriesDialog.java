package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class EditSeriesDialog {
	private final Context mContext;
	private final ArrayAdapter<String> mSeriesAdapter;
	private final CatalogueDBAdapter mDbHelper;
	private final Runnable mOnChanged;

	EditSeriesDialog(Context context, CatalogueDBAdapter dbHelper, final Runnable onChanged) {
		mDbHelper = dbHelper;
		mContext = context;
		mSeriesAdapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_dropdown_item_1line, mDbHelper.fetchAllSeriesArray());
		mOnChanged = onChanged;
	}

	public void editSeries(final Series series) {
		final Dialog dialog = new Dialog(mContext);
		dialog.setContentView(R.layout.edit_series);
		dialog.setTitle(R.string.edit_series);

		AutoCompleteTextView seriesView = (AutoCompleteTextView) dialog.findViewById(R.id.series);
		seriesView.setText(series.name);
		seriesView.setAdapter(mSeriesAdapter);

		Button saveButton = (Button) dialog.findViewById(R.id.confirm);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AutoCompleteTextView seriesView = (AutoCompleteTextView) dialog.findViewById(R.id.series);
				String newName = seriesView.getText().toString().trim();
				if (newName == null || newName.length() == 0) {
					Toast.makeText(mContext, R.string.series_is_blank, Toast.LENGTH_LONG).show();
					return;
				}
				Series newSeries = new Series(newName, "");
				confirmEditSeries(series, newSeries);
				dialog.dismiss();
			}
		});
		Button cancelButton = (Button) dialog.findViewById(R.id.cancel);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		
		dialog.show();		
	}

	private void confirmEditSeries(final Series oldSeries, final Series newSeries) {
		// First, deal with a some special cases...

		// Case: Unchanged.
		if (newSeries.name.compareTo(oldSeries.name) == 0) {
			// No change to anything; nothing to do
			return;
		}

		// Get the new IDs
		oldSeries.id = mDbHelper.lookupSeriesId(oldSeries);
		newSeries.id = mDbHelper.lookupSeriesId(newSeries);

		// Case: series is the same (but different case)
		if (newSeries.id == oldSeries.id) {
			// Just update with the most recent spelling and format
			oldSeries.copyFrom(newSeries);
			mDbHelper.sendSeries(oldSeries);
			mOnChanged.run();
			return;
		}

		mDbHelper.globalReplaceSeries(oldSeries, newSeries);
		oldSeries.copyFrom(newSeries);
		mOnChanged.run();
	}
}
