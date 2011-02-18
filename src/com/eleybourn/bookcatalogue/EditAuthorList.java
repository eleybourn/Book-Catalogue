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

import java.util.ArrayList;

import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity to edit a list of authors provided in an ArrayList<Author> and
 * return an updated list.
 * 
 * @author Grunthos
 */
public class EditAuthorList extends EditObjectList<Author> {

	/**
	 * Constructor; pass the superclass the main and row based layouts to use.
	 */
	public EditAuthorList() {
		super(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, R.layout.edit_author_list, R.layout.row_edit_author_list);
	}

	@Override
	protected void onSetupView(View target, Author object) {
        if (object != null) {
	        TextView at = (TextView) target.findViewById(R.id.row_author);
	        if (at != null) {
	              at.setText(object.getDisplayName());                            }
	        at = (TextView) target.findViewById(R.id.row_author_sort);
	        if (at != null) {
	              at.setText(object.getSortName());                            }
        }
	};

	/**
	 * Return a complete list of author names from the database; used for AutoComplete.
	 *  
	 * @return
	 */
	protected ArrayList<String> getAuthorsFromDb() {
		ArrayList<String> author_list = new ArrayList<String>();
		Cursor author_cur = mDbHelper.fetchAllAuthorsIgnoreBooks();
		startManagingCursor(author_cur);
		while (author_cur.moveToNext()) {
			String name = author_cur.getString(author_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
			author_list.add(name);
		}
		author_cur.close();
		return author_list;
	}

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			// Setup autocomplete for author name
			ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthorsFromDb());
			((AutoCompleteTextView)this.findViewById(R.id.author)).setAdapter(author_adapter);
		} catch (Exception e) {
			Log.e("BookCatalogue.EditAuthorList.onCreate","Failed to initialize", e);
		}
	}

	/**
	 * Do the work of the onClickListener for the 'Add' button.
	 *
	 * @param v
	 */
	protected void onAdd(View v) {
		// Get the text
		AutoCompleteTextView t = ((AutoCompleteTextView)EditAuthorList.this.findViewById(R.id.author));
		String s = t.getText().toString().trim();
		if (s.length() > 0) {
			// Get an author and try to find in DB.
			Author a = new Author(t.getText().toString());
			a.id = mDbHelper.lookupAuthorId(a);

			//
			boolean foundMatch = false;
			for(int i = 0; i < mList.size() && !foundMatch; i++) {
				if (a.id != 0L) {
					if (mList.get(i).id == a.id)
						foundMatch = true;
				} else {
					if (a.getDisplayName().equals(mList.get(i).getDisplayName()))
						foundMatch = true;
				}
			}
			if (foundMatch) {
				Toast.makeText(EditAuthorList.this, getResources().getString(R.string.author_already_in_list), Toast.LENGTH_LONG).show();						
				return;							
			}

			mList.add(a);
            mAdapter.notifyDataSetChanged();
		} else {
			Toast.makeText(EditAuthorList.this, getResources().getString(R.string.author_is_blank), Toast.LENGTH_LONG).show();
		}		
	}

	@Override
	protected void onRowClick(View target, final Author object) {
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.edit_author);
		dialog.setTitle(R.string.edit_author_details);
		EditText familyView = (EditText) dialog.findViewById(R.id.family_name);
		EditText givenView = (EditText) dialog.findViewById(R.id.given_names);
		familyView.setText(object.familyName);
		givenView.setText(object.givenNames);

		Button saveButton = (Button) dialog.findViewById(R.id.confirm);
		saveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText familyView = (EditText) dialog.findViewById(R.id.family_name);
				EditText givenView = (EditText) dialog.findViewById(R.id.given_names);
				String newFamily = familyView.getText().toString().trim();
				if (newFamily == null || newFamily.length() == 0) {
					Toast.makeText(EditAuthorList.this, R.string.author_is_blank, Toast.LENGTH_LONG).show();
					return;
				}
				object.familyName = newFamily;
				object.givenNames = givenView.getText().toString();
				mDbHelper.syncAuthor(object);
					
				dialog.dismiss();
				mAdapter.notifyDataSetChanged();
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

//	@Override
//	protected boolean onSave(Intent i) {
//		for(Author a : mList) {
//			if (a.requiresUpdate)
//				mDbHelper.updateAuthor(a);
//		}
//
//		return true;
//	};
}
