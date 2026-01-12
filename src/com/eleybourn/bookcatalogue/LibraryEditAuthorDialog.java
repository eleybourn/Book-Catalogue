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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.data.Author;

public class LibraryEditAuthorDialog {
	private final Context mContext;
	private final CatalogueDBAdapter mDbHelper;
	private final Runnable mOnChanged;

	LibraryEditAuthorDialog(Context context, CatalogueDBAdapter dbHelper, final Runnable onChanged) {
		mDbHelper = dbHelper;
		mContext = context;
		mOnChanged = onChanged;
	}

	public void editAuthor(final Author author) {
		final Dialog dialog = new Dialog(mContext);
		dialog.setContentView(R.layout.dialog_edit_author);
		dialog.setTitle(R.string.edit_author_details);
		EditText familyView = dialog.findViewById(R.id.field_family_name);
		EditText givenView = dialog.findViewById(R.id.field_given_names);
		familyView.setText(author.familyName);
		givenView.setText(author.givenNames);

		Button saveButton = dialog.findViewById(R.id.button_confirm);
		saveButton.setOnClickListener(v -> {
            String newFamily = familyView.getText().toString().trim();
            if (newFamily.isEmpty()) {
                Toast.makeText(mContext, R.string.author_is_blank, Toast.LENGTH_LONG).show();
                return;
            }
            String newGiven = givenView.getText().toString();
            Author newAuthor = new Author(newFamily, newGiven);
            dialog.dismiss();
            confirmEdit(author, newAuthor);
        });
		Button cancelButton = dialog.findViewById(R.id.button_cancel);
		cancelButton.setOnClickListener(v -> dialog.dismiss());
		
		dialog.show();
	}
	
	private void confirmEdit(final Author oldAuthor, final Author newAuthor) {
		// First, deal with a some special cases...
		
		// Case: Unchanged.
		if (newAuthor.familyName.compareTo(oldAuthor.familyName) == 0 
				&& newAuthor.givenNames.compareTo(oldAuthor.givenNames) == 0) {
			// No change; nothing to do
			return;
		}

		// Get the new author ID
		oldAuthor.id = mDbHelper.lookupAuthorId(oldAuthor);
		newAuthor.id = mDbHelper.lookupAuthorId(newAuthor);

		// Case: author is the same, or is only used in this book
		if (newAuthor.id == oldAuthor.id) {
			// Just update with the most recent spelling and format
			oldAuthor.copyFrom(newAuthor);
			mDbHelper.sendAuthor(oldAuthor);
		} else {
			mDbHelper.globalReplaceAuthor(oldAuthor, newAuthor);
			oldAuthor.copyFrom(newAuthor);
		}
		mOnChanged.run();
	}
}
