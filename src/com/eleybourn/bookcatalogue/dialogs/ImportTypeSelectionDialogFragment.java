package com.eleybourn.bookcatalogue.dialogs;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.utils.Logger;

public class ImportTypeSelectionDialogFragment extends BookCatalogueDialogFragment {
	private int mDialogId;
	private File mFile;
	private boolean mArchiveHasValidDates;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public static interface OnImportTypeSelectionDialogResultListener {
		public void onImportTypeSelectionDialogResult(int dialogId, ImportTypeSelectionDialogFragment dialog, int rowId, File file);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 *
	 * @return			Created fragment
	 */
	public static ImportTypeSelectionDialogFragment newInstance(int dialogId, File file) {
		ImportTypeSelectionDialogFragment frag = new ImportTypeSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putInt("dialogId", dialogId);
        args.putString("fileSpec", file.getAbsolutePath());
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		if (! (a instanceof OnImportTypeSelectionDialogResultListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnDialogResultListener");
		
	}

	private OnClickListener mRowClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			handleClick(v);
		}};

	/**
	 * Utility routine to set the OnClickListener for a given view item.
	 * 
	 * @param id		Sub-View ID
	 * @param l			Listener
	 */
	private void setOnClickListener(View root, int id) {
		View v = root.findViewById(id);
		v.setOnClickListener(mRowClickListener);
		v.setBackgroundResource(android.R.drawable.list_selector_background);
	}

	/**
	 * Create the underlying dialog
	 */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt("dialogId");
    	mFile = new File(getArguments().getString("fileSpec"));

		try {
			BackupReader reader = BackupManager.readBackup(mFile);
			BackupInfo info = reader.getInfo();
			reader.close();
			mArchiveHasValidDates = info.getAppVersionCode() >= 152;
		} catch (IOException e) {
			Logger.logError(e);
			mArchiveHasValidDates = false;
		}

        View v = getActivity().getLayoutInflater().inflate(R.layout.import_type_selection, null);
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(v).setTitle(R.string.import_from_archive).create();
		alertDialog.setIcon(R.drawable.ic_menu_help);
		alertDialog.setCanceledOnTouchOutside(false);

		setOnClickListener(v, R.id.all_books_row);
		if (mArchiveHasValidDates) {
			setOnClickListener(v, R.id.new_and_changed_books_row);
		} else {
			TextView blurb = (TextView)v.findViewById(R.id.new_and_changed_books_blurb);
			blurb.setText(R.string.old_archive_blurb);
		}

        return alertDialog;
    }
    
    private void handleClick(View v) {
    	if (!mArchiveHasValidDates && v.getId() == R.id.new_and_changed_books_row) {
    		Toast.makeText(getActivity(), R.string.old_archive_blurb, Toast.LENGTH_LONG).show();
    		return;
    	}

    	try {
    		OnImportTypeSelectionDialogResultListener a = (OnImportTypeSelectionDialogResultListener)getActivity();
    		if (a != null)
	        	a.onImportTypeSelectionDialogResult(mDialogId, this, v.getId(), mFile);
    	} catch (Exception e) {
    		Logger.logError(e);
    	}
    	dismiss();
    }

}
