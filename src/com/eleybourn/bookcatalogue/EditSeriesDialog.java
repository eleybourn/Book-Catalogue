/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.utils.Logger;

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
		try {
			seriesView.setText(series.name);
		} catch (NullPointerException e) {
			Logger.logError(e);
		}
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
		try {
			if (newSeries.name.compareTo(oldSeries.name) == 0) {
				// No change to anything; nothing to do
				return;
			}
		} catch (NullPointerException e) {
			Logger.logError(e);
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
