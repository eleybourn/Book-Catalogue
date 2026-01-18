package com.eleybourn.bookcatalogue.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
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
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.IOException;
import java.text.DateFormat;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

public class ImportTypeSelectionDialogFragment extends BookCatalogueDialogFragment {
	private int mDialogId;
	private DocumentFile mDocFile;
	private boolean mArchiveHasValidDates;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 *
	 * @author pjw
	 */
	public interface OnImportTypeSelectionDialogResultListener {
		void onImportTypeSelectionDialogResult(int dialogId, ImportTypeSelectionDialogFragment dialog, int rowId, DocumentFile file);
	}

	/**
	 * Constructor
	 *
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param file		File to import
	 *
	 * @return			Created fragment
	 */
	public static ImportTypeSelectionDialogFragment newInstance(int dialogId, DocumentFile file) {
		ImportTypeSelectionDialogFragment frag = new ImportTypeSelectionDialogFragment();
		Bundle args = new Bundle();
		args.putInt("dialogId", dialogId);
		args.putString("fileUri", file.getUri().toString());
		frag.setArguments(args);
		return frag;
	}

	/**
	 * Ensure activity supports event
	 */

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		if (! (context instanceof OnImportTypeSelectionDialogResultListener))
			throw new RuntimeException("Activity " + context.getClass().getSimpleName() + " must implement OnImportTypeSelectionDialogResultListener");
	}

	private final OnClickListener mRowClickListener = this::handleClick;

	/**
	 * Utility routine to set the OnClickListener for a given view item.
	 *
	 * @param root		root to search
	 * @param id		Sub-View ID
	 */
	private void setOnClickListener(View root, int id) {
		View v = root.findViewById(id);
		v.setOnClickListener(mRowClickListener);
		v.setBackgroundResource(android.R.drawable.list_selector_background);
	}

	/**
	 * Create the underlying dialog
	 */
    @NonNull
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = requireArguments();
    	mDialogId = args.getInt("dialogId");
		Uri uri = Uri.parse(args.getString("fileUri"));

		mDocFile = DocumentFile.fromSingleUri(requireContext(), uri);
		assert(mDocFile != null);

		BackupInfo info = null;
        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_import_type_selection, null);

		try {
			BackupReader reader;
			reader = BackupManager.readBackup(v.getContext(), mDocFile);
			info = reader.getInfo();
			reader.close();
			mArchiveHasValidDates = info.getAppVersionCode() >= 152;
		} catch (IOException e) {
			Logger.logError(e);
			mArchiveHasValidDates = false;
		}

		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setIcon(R.drawable.ic_archive).setView(v).setTitle(R.string.label_import_from_archive).create();
		//alertDialog.setIcon(R.drawable.ic_menu_help);
		alertDialog.setCanceledOnTouchOutside(false);

		TextView mainText = v.findViewById(R.id.import_books_blurb);
		Resources r = getResources();
		String s;

		if (info != null) {
			String books = r.getQuantityString(R.plurals.n_books, info.getBookCount(), info.getBookCount());
			if (info.hasCoverCount()) {
				String covers = r.getQuantityString(R.plurals.n_covers,
													info.getCoverCount(),
													info.getCoverCount());
				s = r.getString(R.string.fragment_a_and_b, books, covers);
			} else {
				s = books;
			}
			String size = Utils.formatFileSize(mDocFile.length());
			String date = DateFormat.getDateTimeInstance().format(info.getCreateDate());
			String s_info = r.getString(R.string.para_selected_archive_info, size, date);
			s = s_info +"\n\n" + r.getString(R.string.para_selected_archive_contains, s);
		} else {
			String size = Utils.formatFileSize(mDocFile.length());
			String date = DateFormat.getDateTimeInstance().format(mDocFile.lastModified());
			s = r.getString(R.string.para_selected_archive_info, size, date);
		}
		s = s + "\n\n" + r.getString(R.string.description_import_books_blurb);
		mainText.setText(s);

		setOnClickListener(v, R.id.all_books_row);
		if (mArchiveHasValidDates) {
			setOnClickListener(v, R.id.new_and_changed_books_row);
		} else {
			TextView blurb = v.findViewById(R.id.new_and_changed_books_blurb);
			blurb.setText(R.string.alert_old_archive_blurb);
		}

        return alertDialog;
    }

    private void handleClick(View v) {
    	if (!mArchiveHasValidDates && v.getId() == R.id.new_and_changed_books_row) {
    		Toast.makeText(getActivity(), R.string.alert_old_archive_blurb, Toast.LENGTH_LONG).show();
    		return;
    	}

    	try {
    		OnImportTypeSelectionDialogResultListener a = (OnImportTypeSelectionDialogResultListener)getActivity();
    		if (a != null)
	        	a.onImportTypeSelectionDialogResult(mDialogId, this, v.getId(), mDocFile);
    	} catch (Exception e) {
    		Logger.logError(e);
    	}
    	dismiss();
    }

}
