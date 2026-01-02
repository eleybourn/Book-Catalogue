package com.eleybourn.bookcatalogue.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.utils.Logger;

import java.io.File;
import java.util.Date;

public class ExportTypeSelectionDialogFragment extends BookCatalogueDialogFragment {
    private int mDialogId;
    private DocumentFile mDocFile;
    private final OnClickListener mRowClickListener = this::handleClick;

    /**
     * Constructor
     *
     * @param dialogId ID passed by caller. Can be 0, will be passed back in event
     * @param file     file to use for output
     * @return            Created fragment
     */
    public static ExportTypeSelectionDialogFragment newInstance(int dialogId, File file) {
        ExportTypeSelectionDialogFragment frag = new ExportTypeSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putInt("dialogId", dialogId);
        args.putString("fileSpec", file.getAbsolutePath());
        frag.setArguments(args);
        return frag;
    }

    public static ExportTypeSelectionDialogFragment newInstance(int dialogId, DocumentFile file) {
        ExportTypeSelectionDialogFragment frag = new ExportTypeSelectionDialogFragment();
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
    public void onAttach(@NonNull Context a) {
        super.onAttach(a);

        if (!(a instanceof OnExportTypeSelectionDialogResultListener))
            throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnExportTypeSelectionDialogResultListener");

    }

    /**
     * Utility routine to set the OnClickListener for a given view item.
     *
     * @param root Root view to search
     * @param id   Id of view to watch
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
        mDocFile = DocumentFile.fromSingleUri(BookCatalogueApp.context, uri);

        View v = requireActivity().getLayoutInflater().inflate(R.layout.export_type_selection, null);
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setView(v).setTitle(R.string.label_backup_to_archive).create();
        alertDialog.setIcon(R.drawable.ic_menu_save);
        alertDialog.setCanceledOnTouchOutside(false);

        setOnClickListener(v, R.id.all_books_row);
        setOnClickListener(v, R.id.advanced_row);

        return alertDialog;
    }

    private void handleClick(View v) {
        try {
            if (v.getId() == R.id.advanced_row) {
                ExportAdvancedDialogFragment frag = ExportAdvancedDialogFragment.newInstance(1, mDocFile);
                frag.show(requireActivity().getSupportFragmentManager(), null);
            } else {
                OnExportTypeSelectionDialogResultListener a = (OnExportTypeSelectionDialogResultListener) getActivity();
                if (a != null) {
                    ExportSettings settings = new ExportSettings();
                    settings.file = mDocFile;
                    settings.options = Exporter.EXPORT_ALL;
                    a.onExportTypeSelectionDialogResult(mDialogId, this, settings);
                }
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        dismiss();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnExportTypeSelectionDialogResultListener {
        void onExportTypeSelectionDialogResult(int dialogId, BookCatalogueDialogFragment dialog, ExportSettings settings);
    }

    public static class ExportSettings {
        public DocumentFile file;
        public int options;
        public Date dateFrom;
    }

}
