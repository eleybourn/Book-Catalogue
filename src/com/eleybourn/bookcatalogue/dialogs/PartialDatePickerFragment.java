/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;

/**
 * Fragment wrapper for the PartialDatePicker dialog
 * 
 * @author pjw
 */
public class PartialDatePickerFragment extends BookCatalogueDialogFragment {
	/** Currently displayed year; null if empty/invalid */
	private Integer mYear;
	/** Currently displayed month; null if empty/invalid */
	private Integer mMonth;
	/** Currently displayed day; null if empty/invalid */
	private Integer mDay;
	/** Title id */
	private int mTitleId;
	/** ID passed from caller to identify this dialog */
	private int mDialogId;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public interface OnPartialDatePickerListener {
		void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day);
		void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog);
	}

	/**
	 * Constructor
	 * 
	 * @return		new instance
	 */
	public static PartialDatePickerFragment newInstance() {
        return new PartialDatePickerFragment();
    }

	/**
	 * Check the activity supports the interface
	 */
	@Override
    public void onAttach (@NonNull Context context) {
        super.onAttach(context);

        Activity a = null;
        if (context instanceof Activity){
            a=(Activity) context;
        }

		if (! (a instanceof OnPartialDatePickerListener)) {
            assert a != null;
            throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnPartialDatePickerListener");
        }
		
	}

	/**
	 * Create the underlying dialog
	 */
	@NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		// Restore saved state info
        if (savedInstanceState != null) {
        	if (savedInstanceState.containsKey("year"))
        		mYear = savedInstanceState.getInt("year");
        	if (savedInstanceState.containsKey("month"))
        		mMonth = savedInstanceState.getInt("month");
        	if (savedInstanceState.containsKey("day"))
        		mDay = savedInstanceState.getInt("day");
        	mTitleId = savedInstanceState.getInt("title");
        	mDialogId = savedInstanceState.getInt("dialogId");
        }

        // Create the dialog and listen (locally) for its events
        PartialDatePicker editor = new PartialDatePicker(getActivity());
        editor.setDate(mYear, mMonth, mDay);
        editor.setOnDateSetListener(mDialogListener);		
        if (mTitleId != 0)
	        editor.setTitle(mTitleId);
        return editor;
    }

	/** Accessor */
	public void setDialogId(int id) {
		mDialogId = id;
	}
	/** Accessor */
	public int getDialogId() {
		return mDialogId;
	}

	/** Accessor. Update dialog if available. */
	public void setTitle(int title) {
		mTitleId = title;
		PartialDatePicker d = (PartialDatePicker)getDialog();
		if (d != null) {
			d.setTitle(mTitleId);		
		}
	}

	/** Accessor. Update dialog if available. */
	public void setDate(Integer year, Integer month, Integer day) {
    	mYear = year;
    	mMonth = month;
    	mDay = day;
		PartialDatePicker d = (PartialDatePicker)getDialog();
		if (d != null) {
			d.setDate(mYear, mMonth, mDay);		
		}
	}

	@Override 
	public void onSaveInstanceState(Bundle state) {
		state.putInt("title", mTitleId);
		state.putInt("dialogId", mDialogId);
		if (mYear != null)
			state.putInt("year", mYear);
		if (mMonth != null)
			state.putInt("month", mMonth);
		if (mDay != null)
			state.putInt("day", mDay);
	}

	/**
	 * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views
	 */
	@Override
	public void onPause() {
		super.onPause();
		PartialDatePicker d = (PartialDatePicker)getDialog();
		if (d != null) {
			mYear = d.getYear();
			mMonth = d.getMonth();
			mDay = d.getDay();
		}		
	}
	
	/**
	 *  The callback received when the user "sets" the date in the dialog.
	 *  The event is passed on the the calling activity
	 */
	private final PartialDatePicker.OnDateSetListener mDialogListener = new PartialDatePicker.OnDateSetListener() {
		public void onDateSet(PartialDatePicker dialog, Integer year, Integer month, Integer day) {
			((OnPartialDatePickerListener) requireActivity()).onPartialDatePickerSet(mDialogId, PartialDatePickerFragment.this, year, month, day);
		}

		@Override
		public void onCancel(PartialDatePicker dialog) {
			((OnPartialDatePickerListener) requireActivity()).onPartialDatePickerCancel(mDialogId, PartialDatePickerFragment.this);
		}
	};

}
