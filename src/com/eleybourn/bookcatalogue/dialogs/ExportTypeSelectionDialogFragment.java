package com.eleybourn.bookcatalogue.dialogs;

import java.io.File;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.utils.Logger;

public class ExportTypeSelectionDialogFragment extends BookCatalogueDialogFragment {
	private int mDialogId;
	private File mFile;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public static interface OnExportTypeSelectionDialogResultListener {
		public void onExportTypeSelectionDialogResult(int dialogId, BookCatalogueDialogFragment dialog, ExportSettings settings);
	}

	public static class ExportSettings {
		public File 	file;
		public int		options;
		public Date		dateFrom;
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 *
	 * @return			Created fragment
	 */
	public static ExportTypeSelectionDialogFragment newInstance(int dialogId, File file) {
		ExportTypeSelectionDialogFragment frag = new ExportTypeSelectionDialogFragment();
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

		if (! (a instanceof OnExportTypeSelectionDialogResultListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnExportTypeSelectionDialogResultListener");
		
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

        View v = getActivity().getLayoutInflater().inflate(R.layout.export_type_selection, null);
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(v).setTitle(R.string.backup_to_archive).create();
		alertDialog.setIcon(R.drawable.ic_menu_help);
		alertDialog.setCanceledOnTouchOutside(false);

		setOnClickListener(v, R.id.all_books_row);
		setOnClickListener(v, R.id.advanced_row);

        return alertDialog;
    }
    
    private void handleClick(View v) {
    	try {
    		if (v.getId() == R.id.advanced_row) {
    			ExportAdvancedDialogFragment frag = ExportAdvancedDialogFragment.newInstance(1, mFile);
    			frag.show(getActivity().getSupportFragmentManager(), null);
    		} else {
        		OnExportTypeSelectionDialogResultListener a = (OnExportTypeSelectionDialogResultListener)getActivity();
        		if (a != null) {
        			ExportSettings settings = new ExportSettings();
        			settings.file = mFile;
        			settings.options = Exporter.EXPORT_ALL;
    	        	a.onExportTypeSelectionDialogResult(mDialogId, this, settings);
        		}
    		}
    	} catch (Exception e) {
    		Logger.logError(e);
    	}
    	dismiss();
    }

}
