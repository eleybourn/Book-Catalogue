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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.R;

/**
 * Dialog to edit a specific text field.
 * 
 * The constructors and interface are now protected because this really should 
 * only be called as part of the fragment version.
 * 
 * @author pjw
 */
public class TextFieldEditor extends AlertDialog {
	/** Current text */
	private String mText;
	/** View which displays the text */
	private EditText mTextView;
	// Listener for dialog exit/save/cancel */
	private OnEditListener mListener;

	/**
	 * Listener to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	protected static interface OnEditListener {
		public void onSaved(TextFieldEditor dialog, String newText);
		public void onCancel(TextFieldEditor dialog);
	}

	/**
	 * Constructor
	 * 
	 * @param context		Calling context
	 */
	protected TextFieldEditor(Context context) {
		super(context);

		// Get the layout
		LayoutInflater inf = this.getLayoutInflater();
		View root = inf.inflate(R.layout.text_field_editor_dialog, null);

		// Setup the layout
		setView(root);

		// get the next view
		mTextView = (EditText)root.findViewById(R.id.text);

		// Handle OK
		((Button)root.findViewById(R.id.ok)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mListener.onSaved(TextFieldEditor.this, mTextView.getText().toString());
			}}
		);

		// Handle Cancel
		((Button)root.findViewById(R.id.cancel)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mListener.onCancel(TextFieldEditor.this);				
			}}
		);

		// Handle Cancel by any means
		this.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface arg0) {
				mListener.onCancel(TextFieldEditor.this);				
			}});	
		
		// Make sure the buttons move if the keyboard appears
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
	}

	/** Set the listener */
	public void setOnEditListener(OnEditListener listener) {
		mListener= listener;		
	}

	/** Set the current text */
	public void setText(String text) {
		mText = text;
		mTextView.setText(mText);
	}

}
