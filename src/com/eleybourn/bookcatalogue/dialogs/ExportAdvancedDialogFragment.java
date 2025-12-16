package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import androidx.documentfile.provider.DocumentFile;

public class ExportAdvancedDialogFragment extends BookCatalogueDialogFragment {
	private int mDialogId;
	private DocumentFile mFile;

//	/**
//	 * Listener interface to receive notifications when dialog is closed by any means.
//	 * 
//	 * @author pjw
//	 */
//	public static interface OnExportAdvancedDialogResultListener {
//		public void onExportAdvancedDialogResult(int dialogId, ExportAdvancedDialogFragment dialog, int rowId, File file);
//	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 *
	 * @return			Created fragment
	 */
	public static ExportAdvancedDialogFragment newInstance(int dialogId, DocumentFile file) {
		ExportAdvancedDialogFragment frag = new ExportAdvancedDialogFragment();
        Bundle args = new Bundle();
        args.putInt("dialogId", dialogId);
        args.putString("fileSpec", file.getUri().toString());
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


	/**
	 * Utility routine to set the OnClickListener for a given view to change a checkbox.
	 * 
	 * @param cbId		checkbox view id
	 * @param relId		Related view id
	 */
	private void setRelatedView(View root, int cbId, int relId) {
		final CheckBox cb = (CheckBox)root.findViewById(cbId);
		final View rel = root.findViewById(relId);
		rel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				cb.setChecked(!cb.isChecked());
			}});
	}
	
//	private OnClickListener mRowClickListener = new OnClickListener() {
//		@Override
//		public void onClick(View v) {
//			handleClick(v);
//		}};
//
//	/**
//	 * Utility routine to set the OnClickListener for a given view item.
//	 * 
//	 * @param id		Sub-View ID
//	 * @param l			Listener
//	 */
//	private void setOnClickListener(View root, int id) {
//		View v = root.findViewById(id);
//		v.setOnClickListener(mRowClickListener);
//		v.setBackgroundResource(android.R.drawable.list_selector_background);
//	}

	/**
	 * Create the underlying dialog
	 */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt("dialogId");
		Uri uri = Uri.parse(getArguments().getString("fileSpec"));
    	mFile = DocumentFile.fromSingleUri(getContext(), uri);

        View v = getActivity().getLayoutInflater().inflate(R.layout.export_advanced_options, null);
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(v).setTitle(R.string.label_advanced_options).create();
		alertDialog.setIcon(R.drawable.ic_menu_help_old);
		alertDialog.setCanceledOnTouchOutside(false);

		v.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}});

		v.findViewById(R.id.ok).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				handleClick(v);
			}});

		setRelatedView(v, R.id.books_check, R.id.all_books_row);
		setRelatedView(v, R.id.covers_check, R.id.covers_row);
		
        return alertDialog;
    }
    
    private void handleClick(View v) {
    	try {
    		OnExportTypeSelectionDialogResultListener a = (OnExportTypeSelectionDialogResultListener)getActivity();
    		if (a != null) {
    			ExportSettings settings = createSettings();
    			if (settings != null) {
		        	a.onExportTypeSelectionDialogResult(mDialogId, this, settings);
		        	dismiss();
    			}
    		} else {
            	dismiss();    			
    		}
    	} catch (Exception e) {
    		Logger.logError(e);
    	}
    }

    private ExportSettings createSettings() {
		ExportSettings settings = new ExportSettings();

		settings.file = mFile;
		settings.options = 0;
		settings.dateFrom = null;

		Dialog v = this.getDialog();
		if (((CheckBox)v.findViewById(R.id.books_check)).isChecked()) 
			settings.options |= Exporter.EXPORT_DETAILS;
		if (((CheckBox)v.findViewById(R.id.covers_check)).isChecked()) 
			settings.options |= Exporter.EXPORT_COVERS;
		if (((CheckBox)v.findViewById(R.id.preferences_check)).isChecked()) 
			settings.options |= Exporter.EXPORT_PREFERENCES | Exporter.EXPORT_STYLES;

		if (((RadioButton)v.findViewById(R.id.radioSinceLast)).isChecked()) {
			settings.options |= Exporter.EXPORT_SINCE;
			settings.dateFrom = null;
		} else if (((RadioButton)v.findViewById(R.id.radioSinceDate)).isChecked()) {
			String s = v.findViewById(R.id.txtDate).toString();
			try {
				settings.options |= Exporter.EXPORT_SINCE;
				settings.dateFrom = Utils.parseDate(s);
			} catch(Exception e) {
				Toast.makeText(getActivity(), R.string.no_date, Toast.LENGTH_LONG).show();
				return null;
			}
		}

		return settings;
    }
}
