package com.eleybourn.bookcatalogue;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class EditAuthorDialog {
	private final Context mContext;
	private final CatalogueDBAdapter mDbHelper;
	private final Runnable mOnChanged;

	EditAuthorDialog(Context context, CatalogueDBAdapter dbHelper, final Runnable onChanged) {
		mDbHelper = dbHelper;
		mContext = context;
		mOnChanged = onChanged;
	}

	public void editAuthor(final Author author) {
		final Dialog dialog = new Dialog(mContext);
		dialog.setContentView(R.layout.edit_author);
		dialog.setTitle(R.string.edit_author_details);
		EditText familyView = (EditText) dialog.findViewById(R.id.family_name);
		EditText givenView = (EditText) dialog.findViewById(R.id.given_names);
		familyView.setText(author.familyName);
		givenView.setText(author.givenNames);

		Button saveButton = (Button) dialog.findViewById(R.id.confirm);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText familyView = (EditText) dialog.findViewById(R.id.family_name);
				EditText givenView = (EditText) dialog.findViewById(R.id.given_names);
				String newFamily = familyView.getText().toString().trim();
				if (newFamily == null || newFamily.length() == 0) {
					Toast.makeText(mContext, R.string.author_is_blank, Toast.LENGTH_LONG).show();
					return;
				}
				String newGiven = givenView.getText().toString();
				Author newAuthor = new Author(newFamily, newGiven);
				dialog.dismiss();
				confirmEdit(author, newAuthor);
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
