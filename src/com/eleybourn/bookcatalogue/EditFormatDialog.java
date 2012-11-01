/*
 * @copyright 2011 Philip Warner
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

package com.eleybourn.bookcatalogue;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.utils.Logger;

public class EditFormatDialog {
	private final Context mContext;
	private final ArrayAdapter<String> mAdapter;
	private final CatalogueDBAdapter mDbHelper;
	private final Runnable mOnChanged;

	EditFormatDialog(Context context, CatalogueDBAdapter dbHelper, final Runnable onChanged) {
		mDbHelper = dbHelper;
		mContext = context;
		mAdapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_dropdown_item_1line, mDbHelper.getFormats());
		mOnChanged = onChanged;
	}

	public void edit(final String origFormat) {
		final Dialog dialog = new Dialog(mContext);
		dialog.setContentView(R.layout.edit_format);
		dialog.setTitle(R.string.edit_format_name);

		AutoCompleteTextView nameView = (AutoCompleteTextView) dialog.findViewById(R.id.name);
		try {
			nameView.setText(origFormat);
		} catch (NullPointerException e) {
			Logger.logError(e);
		}
		nameView.setAdapter(mAdapter);

		Button saveButton = (Button) dialog.findViewById(R.id.confirm);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AutoCompleteTextView nameView = (AutoCompleteTextView) dialog.findViewById(R.id.name);
				String newName = nameView.getText().toString().trim();
				if (newName == null || newName.length() == 0) {
					Toast.makeText(mContext, R.string.name_can_not_be_blank, Toast.LENGTH_LONG).show();
					return;
				}
				confirmEditFormat(origFormat, newName);
				dialog.dismiss();
			}
		});
		Button cancelButton = (Button) dialog.findViewById(R.id.cancel);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		
		dialog.show();		
	}

	private void confirmEditFormat(final String oldFormat, final String newFormat) {
		// First, deal with a some special cases...

		// Case: Unchanged.
		try {
			if (oldFormat.equals(newFormat)) {
				// No change to anything; nothing to do
				return;
			}
		} catch (NullPointerException e) {
			Logger.logError(e);
		}

		mDbHelper.globalReplaceFormat(oldFormat, newFormat);

		mOnChanged.run();
	}
}
