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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

public class BookEditSeriesList extends BookEditObjectList<Series> {
	
	private ArrayAdapter<String> mSeriesAdapter;
	
	public BookEditSeriesList() {
		super(CatalogueDBAdapter.KEY_SERIES_ARRAY, R.layout.edit_series_list, R.layout.row_edit_series_list);
	}
	
	@Override
	protected void onSetupView(View target, Series object) {
		if (object != null) {
			TextView dt = target.findViewById(R.id.row_series);
			if (dt != null)
				dt.setText(object.getDisplayName());
			
			TextView st = target.findViewById(R.id.row_series_sort);
			if (st != null) {
				if (object.getDisplayName().equals(object.getSortName())) {
					st.setVisibility(View.GONE);
				} else {
					st.setVisibility(View.VISIBLE);
					st.setText(object.getSortName());
				}
			}
		}
	}

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			
			mSeriesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, mDbHelper.fetchAllSeriesArray());
			((AutoCompleteTextView)this.findViewById(R.id.field_series)).setAdapter(mSeriesAdapter);
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	@Override
	protected void onAdd(View v) {
		AutoCompleteTextView t = BookEditSeriesList.this.findViewById(R.id.field_series);
		String s = t.getText().toString().trim();
		if (!s.isEmpty()) {
			EditText et = BookEditSeriesList.this.findViewById(R.id.field_series_num);
			String n = et.getText().toString();
            Series series = new Series(t.getText().toString(), n);
			series.id = mDbHelper.lookupSeriesId(series);
			boolean foundMatch = false;
			try {
				for(int i = 0; i < mList.size() && !foundMatch; i++) {
					if (series.name.equals(mList.get(i).name) && series.num.equals(mList.get(i).num)) {
						foundMatch = true;
					}
				}
			} catch (NullPointerException e) {
				Logger.logError(e, "while adding series");
			}
			if (foundMatch) {
				Toast.makeText(BookEditSeriesList.this, getResources().getString(R.string.series_already_in_list), Toast.LENGTH_LONG).show();
				return;							
			}
			mList.add(series);
			mAdapter.notifyDataSetChanged();
			t.setText("");
			et.setText("");
		} else {
			Toast.makeText(BookEditSeriesList.this, getResources().getString(R.string.series_is_blank), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onRowClick(View target, int position, final Series object) {
		editSeries(object);
	}
	
	private void editSeries(final Series series) {
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.dialog_edit_series);
		dialog.setTitle(R.string.edit_book_series);

		AutoCompleteTextView seriesView = dialog.findViewById(R.id.field_series);
		seriesView.setText(series.name);
		seriesView.setAdapter(mSeriesAdapter);

		EditText numView = dialog.findViewById(R.id.field_series_num);
		numView.setText(series.num);

		setTextOrHideView(dialog.findViewById(R.id.field_title), mBookTitle);

		Button saveButton = dialog.findViewById(R.id.button_confirm);
		saveButton.setOnClickListener(v -> {
            String newName = seriesView.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(BookEditSeriesList.this, R.string.series_is_blank, Toast.LENGTH_LONG).show();
                return;
            }
            Series newSeries = new Series(newName, numView.getText().toString());
            confirmEditSeries(series, newSeries);
            dialog.dismiss();
        });
		Button cancelButton = dialog.findViewById(R.id.button_cancel);
		cancelButton.setOnClickListener(v -> dialog.dismiss());
		
		dialog.show();		
	}

	private void confirmEditSeries(final Series oldSeries, final Series newSeries) {
		// First, deal with a some special cases...
		
		boolean nameIsSame = (newSeries.name.compareTo(oldSeries.name) == 0);
		// Case: Unchanged.
		if (nameIsSame && newSeries.num.compareTo(oldSeries.num) == 0) {
			// No change to anything; nothing to do
			return;
		}
		if (nameIsSame) {
			// Same name, different number... just update
			oldSeries.copyFrom(newSeries);
			Utils.pruneSeriesList(mList);
			Utils.pruneList(mDbHelper, mList);
			mAdapter.notifyDataSetChanged();
			return;
		}

		// Get the new IDs
		oldSeries.id = mDbHelper.lookupSeriesId(oldSeries);
		newSeries.id = mDbHelper.lookupSeriesId(newSeries);

		// See if the old series is used in any other books.
		long nRefs = mDbHelper.getSeriesBookCount(oldSeries);
		boolean oldHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

		// Case: series is the same (but different case), or is only used in this book
		if (newSeries.id == oldSeries.id || !oldHasOthers) {
			// Just update with the most recent spelling and format
			oldSeries.copyFrom(newSeries);
			Utils.pruneSeriesList(mList);
			Utils.pruneList(mDbHelper, mList);
			mDbHelper.sendSeries(oldSeries);
			mAdapter.notifyDataSetChanged();
			return;
		}

		// When we get here, we know the names are genuinely different and the old series is used in more than one place.
		String format = getResources().getString(R.string.changed_series_how_apply);
		String allBooks = getResources().getString(R.string.option_all_books);
		String thisBook = getResources().getString(R.string.this_book);
		String message = String.format(format, oldSeries.name, newSeries.name);

		final AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(message).create();

		alertDialog.setTitle(getResources().getString(R.string.scope_of_change));
		alertDialog.setIcon(R.drawable.ic_menu_info);
		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, thisBook, (dialog, which) -> {
            oldSeries.copyFrom(newSeries);
            Utils.pruneSeriesList(mList);
            Utils.pruneList(mDbHelper, mList);
            mAdapter.notifyDataSetChanged();
            alertDialog.dismiss();
        });

		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks, (dialog, which) -> {
            mDbHelper.globalReplaceSeries(oldSeries, newSeries);
            oldSeries.copyFrom(newSeries);
            Utils.pruneSeriesList(mList);
            Utils.pruneList(mDbHelper, mList);
            mAdapter.notifyDataSetChanged();
            alertDialog.dismiss();
        });

		alertDialog.show();
	}
	
	@Override
	protected boolean onSave(Intent intent) {
		final AutoCompleteTextView t = BookEditSeriesList.this.findViewById(R.id.field_series);
		Resources res = this.getResources();
		String s = t.getText().toString().trim();
		if (!s.isEmpty()) {
			final AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(res.getText(R.string.unsaved_edits)).create();
			
			alertDialog.setTitle(res.getText(R.string.unsaved_edits_title));
			alertDialog.setIcon(R.drawable.ic_menu_info);
			alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, res.getText(R.string.option_yes), (dialog, which) -> {
                t.setText("");
                findViewById(R.id.button_confirm).performClick();
            });
			
			alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, res.getText(R.string.option_no), (dialog, which) -> {
                //do nothing
            });
			
			alertDialog.show();
			return false;
		} else {
			return true;
		}
	}
}
