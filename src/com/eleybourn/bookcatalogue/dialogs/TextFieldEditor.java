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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;

/**
 * Dialog to edit a specific text field.
 * The constructors and interface are now protected because this really should
 * only be called as part of the fragment version.
 * 
 * @author pjw
 */
public class TextFieldEditor extends AlertDialog {
    /** View which displays the text */
	private final EditText mTextView;
	// Listener for dialog exit/save/cancel */
	private OnEditListener mListener;

	/**
	 * Listener to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	protected interface OnEditListener {
		void onSaved(TextFieldEditor dialog, String newText);
		void onCancel(TextFieldEditor dialog);
	}

	/**
	 * Constructor
	 * 
	 * @param context		Calling context
	 */
	protected TextFieldEditor(Context context) {
		super(context);

        // Make sure the buttons move if the keyboard appears
        assert getWindow() != null;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

		// Get the layout
		LayoutInflater inf = this.getLayoutInflater();
		View root = inf.inflate(R.layout.dialog_text_editor, null);

		// Setup the layout
		setView(root);

		// get the next view
		mTextView = root.findViewById(R.id.field_text);

		// Handle OK
		root.findViewById(R.id.button_ok).setOnClickListener(v -> mListener.onSaved(TextFieldEditor.this, mTextView.getText().toString())
        );

		// Handle Cancel
		root.findViewById(R.id.button_cancel).setOnClickListener(v -> mListener.onCancel(TextFieldEditor.this)
        );

		// Handle Cancel by any means
		this.setOnCancelListener(arg0 -> mListener.onCancel(TextFieldEditor.this));
	}

	/** Set the listener */
    protected void setOnEditListener(OnEditListener listener) {
		mListener= listener;		
	}

	/** Set the current text */
	public void setText(String text) {
        // Current text
        mTextView.setText(text);
	}

}
