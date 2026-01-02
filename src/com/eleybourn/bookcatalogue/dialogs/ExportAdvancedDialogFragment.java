package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.utils.Logger;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.util.Objects;

public class ExportAdvancedDialogFragment extends BookCatalogueDialogFragment {
	private int mDialogId;
	private DocumentFile mFile;

    /**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param file	File to display
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
    public void onAttach (@NonNull Context context) {
        super.onAttach(context);

        Activity a = null;
        if (context instanceof Activity){
            a=(Activity) context;
        }

		if (! (a instanceof OnExportTypeSelectionDialogResultListener)) {
            assert a != null;
            throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnExportTypeSelectionDialogResultListener");
        }
		
	}


	/**
	 * Utility routine to set the OnClickListener for a given view to change a checkbox.
	 * 
	 * @param cbId		checkbox view id
	 * @param relId		Related view id
	 */
	private void setRelatedView(View root, int cbId, int relId) {
		final CheckBox cb = root.findViewById(cbId);
		final View rel = root.findViewById(relId);
		rel.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));
	}

    /**
	 * Create the underlying dialog
	 */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        assert getArguments() != null;
        mDialogId = getArguments().getInt("dialogId");
		Uri uri = Uri.parse(getArguments().getString("fileSpec"));
    	mFile = DocumentFile.fromSingleUri(Objects.requireNonNull(getContext()), uri);

        View v = Objects.requireNonNull(getActivity()).getLayoutInflater().inflate(R.layout.dialog_export_advanced_options, null);
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(v).setTitle(R.string.label_advanced_options).create();
		alertDialog.setIcon(R.drawable.ic_menu_help);
		alertDialog.setCanceledOnTouchOutside(false);

		v.findViewById(R.id.button_cancel).setOnClickListener(v1 -> dismiss());

		v.findViewById(R.id.button_ok).setOnClickListener(this::handleClick);

		setRelatedView(v, R.id.field_export_books, R.id.all_books_row);
		setRelatedView(v, R.id.field_export_covers, R.id.covers_row);
		
        return alertDialog;
    }
    
    private void handleClick(View v) {
    	try {
    		OnExportTypeSelectionDialogResultListener a = (OnExportTypeSelectionDialogResultListener)getActivity();
    		if (a != null) {
    			ExportSettings settings = createSettings();
                a.onExportTypeSelectionDialogResult(mDialogId, this, settings);
                dismiss();
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
        assert v != null;
        if (((CheckBox)v.findViewById(R.id.field_export_books)).isChecked())
			settings.options |= Exporter.EXPORT_DETAILS;
		if (((CheckBox)v.findViewById(R.id.field_export_covers)).isChecked())
			settings.options |= Exporter.EXPORT_COVERS;
		if (((CheckBox)v.findViewById(R.id.field_export_preferences)).isChecked())
			settings.options |= Exporter.EXPORT_PREFERENCES | Exporter.EXPORT_STYLES;

		if (((RadioButton)v.findViewById(R.id.field_export_since_last)).isChecked()) {
			settings.options |= Exporter.EXPORT_SINCE;
			settings.dateFrom = null;
		}

		return settings;
    }
}
