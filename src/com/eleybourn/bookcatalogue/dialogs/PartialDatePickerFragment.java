package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePicker.OnDateSetListener;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.Utils;

public class PartialDatePickerFragment extends SherlockDialogFragment {
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
	 * Listener to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public static interface OnPartialDatePickerListener {
		public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day);
		public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog);
	}

	public static PartialDatePickerFragment newInstance() {
    	PartialDatePickerFragment frag = new PartialDatePickerFragment();
        return frag;
    }

	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		if (! (a instanceof OnPartialDatePickerListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnPartialDatePickerListener");
		
	}

	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
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

        PartialDatePicker editor = new PartialDatePicker(getActivity());
        editor.setDate(mYear, mMonth, mDay);
        editor.setOnDateSetListener(mDialogListener);		
        if (mTitleId != 0)
	        editor.setTitle(mTitleId);
        return editor;
    }
	
	public void setDialogId(int id) {
		mDialogId = id;
	}
	public int getDialogId() {
		return mDialogId;
	}

	public void setTitle(int title) {
		mTitleId = title;
		PartialDatePicker d = (PartialDatePicker)getDialog();
		if (d != null) {
			d.setTitle(mTitleId);		
		}
	}

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
	 *  
	 *  The event is passed on the the calling activity
	 */
	private PartialDatePicker.OnDateSetListener mDialogListener = new PartialDatePicker.OnDateSetListener() {
		public void onDateSet(PartialDatePicker dialog, Integer year, Integer month, Integer day) {
			((OnPartialDatePickerListener)getActivity()).onPartialDatePickerSet(mDialogId, PartialDatePickerFragment.this, year, month, day);
		}

		@Override
		public void onCancel(PartialDatePicker dialog) {
			((OnPartialDatePickerListener)getActivity()).onPartialDatePickerCancel(mDialogId, PartialDatePickerFragment.this);
		}
	};

}
