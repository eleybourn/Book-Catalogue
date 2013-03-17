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
import android.os.Bundle;

import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;

/**
 * Fragment wrapper for the PartialDatePicker dialog
 * 
 * @author pjw
 */
public class TextFieldEditorFragment extends BookCatalogueDialogFragment {
	private int mDialogId;

	/**
	 * Listener interface to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public static interface OnTextFieldEditorListener {
		public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText);
		public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId	ID passed by caller. Can be 0, will be passed back in event
	 * @param titleId	Title to display
	 * @param text		Text to edit
	 *
	 * @return			Created fragment
	 */
	public static TextFieldEditorFragment newInstance(int dialogId, int titleId, String text) {
    	TextFieldEditorFragment frag = new TextFieldEditorFragment();
        Bundle args = new Bundle();
        args.putString("text", text);
        args.putInt("title", titleId);
        args.putInt("dialogId", dialogId);
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		if (! (a instanceof OnTextFieldEditorListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnTextFieldEditorListener");
		
	}

	/**
	 * Create the underlying dialog
	 */
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
