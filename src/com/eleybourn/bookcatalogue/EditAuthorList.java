package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class EditAuthorList extends EditObjectList<Author> {

	public EditAuthorList() {
		super(R.layout.edit_author_list, R.layout.row_edit_author_list);
	}

	@Override
	protected boolean onSave(Intent i) {
		i.putExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mList);
		return true;
	}

	@Override
	protected boolean onCancel() {
		return true;
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

	@Override
	protected void onInitList(Bundle savedInstanceState) {
		if (savedInstanceState != null && savedInstanceState.containsKey(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)) {
			mList = Utils.getAuthorsFromBundle(savedInstanceState);
		}

		if (mList == null) {
			/* Get any information from the extras bundle */
			Bundle extras = getIntent().getExtras();
			if (extras != null && extras.containsKey(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)) {
				mList = Utils.getAuthorsFromBundle(extras);
			} else {
				mList = new ArrayList<Author>();				
			}
		}		
	}

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

			ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthorsFromDb());
			((AutoCompleteTextView)this.findViewById(R.id.author)).setAdapter(author_adapter);
			this.findViewById(R.id.add).setOnClickListener(mAddAuthorListener);

		} catch (Exception e) {
			Log.e("BookCatalogue.EditAuthorList.onCreate","Failed to initialize", e);
		}
	}

	private OnClickListener mAddAuthorListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Log.i("BC","Add");
			AutoCompleteTextView t = ((AutoCompleteTextView)EditAuthorList.this.findViewById(R.id.author));
			String s = t.getText().toString().trim();
			if (s.length() > 0) {
				mList.add(new Author(t.getText().toString()));
	            mAdapter.notifyDataSetChanged();
			} else {
				Toast.makeText(EditAuthorList.this, "Author is empty", Toast.LENGTH_LONG);
			}
		}
	};
}
