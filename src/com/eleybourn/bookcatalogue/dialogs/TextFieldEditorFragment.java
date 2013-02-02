package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;

public class TextFieldEditorFragment extends BookCatalogueDialogFragment {
	private int mDialogId;

	public static interface OnTextFieldEditorListener {
		public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText);
		public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog);
	}

	public static TextFieldEditorFragment newInstance(int dialogId, int titleId, String text) {
    	TextFieldEditorFragment frag = new TextFieldEditorFragment();
        Bundle args = new Bundle();
        args.putString("text", text);
        args.putInt("title", titleId);
        args.putInt("dialogId", dialogId);
        frag.setArguments(args);
        return frag;
    }

	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		if (! (a instanceof OnTextFieldEditorListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnTextFieldEditorListener");
		
	}

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	mDialogId = getArguments().getInt("dialogId");
        int title = getArguments().getInt("title");
        String text = getArguments().getString("text");

        TextFieldEditor editor = new TextFieldEditor(getActivity());
        editor.setText(text);
        editor.setTitle(title);
        editor.setOnEditListener(mEditListener);
        return editor;
    }
    
	/**
	 * Object to handle changes to a description field.
	 */
	private TextFieldEditor.OnEditListener mEditListener = new TextFieldEditor.OnEditListener(){
		@Override
		public void onSaved(TextFieldEditor dialog, String newText) {
			((OnTextFieldEditorListener)getActivity()).onTextFieldEditorSave(mDialogId, TextFieldEditorFragment.this, newText);
		}
		@Override
		public void onCancel(TextFieldEditor dialog) {
			((OnTextFieldEditorListener)getActivity()).onTextFieldEditorCancel(mDialogId, TextFieldEditorFragment.this);
		}
	};
}
