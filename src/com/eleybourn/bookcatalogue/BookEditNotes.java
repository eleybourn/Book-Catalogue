/*
 * @copyright 2010 Evan Leybourn
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

//import android.R;
import java.util.ArrayList;
import java.util.Date;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.datamanager.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookEditNotes extends BookEditFragmentAbstract implements OnPartialDatePickerListener {

	/**
	 * Returns a unique list of all locations in the database
	 *
	 * @return The list
	 */
	protected ArrayList<String> getLocations() {
		ArrayList<String> location_list = new ArrayList<String>();
		Cursor location_cur = mDbHelper.fetchAllLocations();
		try {
			while (location_cur.moveToNext()) {
				String location = location_cur.getString(location_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION));
				try {
					if (location.length() > 2) {
						location_list.add(location);
					}
				} catch (NullPointerException e) {
					// do nothing
				}
			}
		} finally {
			location_cur.close();
		}
		return location_list;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View root = inflater.inflate(R.layout.edit_book_notes, container, false);

		return root;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		try {
			super.onActivityCreated(savedInstanceState);

			if (savedInstanceState != null) {
				mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
			}

			Field f;

			//FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());
			FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

			mFields.add(R.id.rating, CatalogueDBAdapter.KEY_RATING, null);
			mFields.add(R.id.rating_label, "",  CatalogueDBAdapter.KEY_RATING, null);
			mFields.add(R.id.read, CatalogueDBAdapter.KEY_READ, null);
			mFields.add(R.id.notes, CatalogueDBAdapter.KEY_NOTES, null);

			ArrayAdapter<String> location_adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_dropdown_item_1line, getLocations());
			mFields.add(R.id.location, CatalogueDBAdapter.KEY_LOCATION, null);
			mFields.setAdapter(R.id.location, location_adapter);

			// ENHANCE: Add a partial date validator. Or not.
			f = mFields.add(R.id.read_start, CatalogueDBAdapter.KEY_READ_START, null, dateFormatter);
			f.getView().setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					BookEditNotes.this.showReadStartDialog();
				}
			});

			f = mFields.add(R.id.read_end, CatalogueDBAdapter.KEY_READ_END, null, dateFormatter);
			f.getView().setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					BookEditNotes.this.showReadEndDialog();
				}
			});

			mFields.add(R.id.signed, CatalogueDBAdapter.KEY_SIGNED,  null);

			mFields.addCrossValidator(new Fields.FieldCrossValidator() {
				public void validate(Fields fields, Bundle values) {
					String start = values.getString(CatalogueDBAdapter.KEY_READ_START);
					if (start == null || start.equals(""))
						return;
					String end = values.getString(CatalogueDBAdapter.KEY_READ_END);
					if (end == null || end.equals(""))
						return;
					if (start.compareToIgnoreCase(end) > 0)
						throw new ValidatorException(R.string.vldt_read_start_after_end,new Object[]{});
				}
			});

			try {
				Utils.fixFocusSettings(getView());				
			} catch (Exception e) {
				// Log, but ignore. This is a non-critical feature that prevents crashes when the
				// 'next' key is pressed and some views have been hidden.
				Logger.logError(e);
			}

			mFields.setAfterFieldChangeListener(new AfterFieldChangeListener(){
				@Override
				public void afterFieldChange(Field field, String newValue) {
					mEditManager.setDirty(true);
				}});
			
		} catch (Exception e) {
			Logger.logError(e);
		}
		Tracker.exitOnCreate(this);
	}

	private void showReadStartDialog() {
		PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance();
		
		frag.setTitle(R.string.read_start);
		frag.setDialogId(R.id.read_start); // Set to the destination field ID
		try {
			String dateString;
			Object o = mFields.getField(R.id.read_start).getValue();
			if (o == null || o.toString().equals("")) {
				dateString = Utils.toSqlDateTime(new Date());
			} else {
				dateString = o.toString();
			}
			Utils.prepareDateDialogFragment(frag, dateString);
		} catch (Exception e) {
			// use the default date
		}

		frag.show(getFragmentManager(), null);
	}

	private void showReadEndDialog() {
		PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance();
		
		frag.setTitle(R.string.read_end);
		frag.setDialogId(R.id.read_end); // Set to the destination field ID

		try {
			String dateString;
			Object o = mFields.getField(R.id.read_end).getValue();
			if (o == null || o.toString().equals("")) {
				dateString = Utils.toSqlDateTime(new Date());
			} else {
				dateString = o.toString();
			}
			Utils.prepareDateDialogFragment(frag, dateString);
		} catch (Exception e) {
			// use the default date
		}
		frag.show(getFragmentManager(), null);
	}

	/**
	 *  The callback received when the user "sets" the date in the dialog.
	 *  
	 *  Build a full or partial date in SQL format
	 */
	@Override
	public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
		String value = Utils.buildPartialDate(year, month, day);
		mFields.getField(dialogId).setValue(value);
		dialog.dismiss();
	}

	/**
	 *  The callback received when the user "cancels" the date in the dialog.
	 *  
	 *  Dismiss it.
	 */
	@Override
	public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
		dialog.dismiss();
	}

	@Override
	public void onPause() {
		Tracker.enterOnPause(this);
		BookData book = mEditManager.getBookData();
		mFields.getAll(book);
		super.onPause();
		Tracker.exitOnPause(this);
	}

	@Override
	protected void onLoadBookDetails(BookData book, boolean setAllDone) {
		if (!setAllDone)
			mFields.setAll(book);
		// No special handling required; the setAll() done by the caller is enough
		// Restore default visibility and hide unused/unwanted and empty fields
		showHideFields(false);
	}

}