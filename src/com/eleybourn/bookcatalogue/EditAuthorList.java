package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
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
}
