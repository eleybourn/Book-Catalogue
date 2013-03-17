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

import static com.eleybourn.bookcatalogue.BookDetailsAbstract.BOOKSHELF_SEPERATOR;

import java.util.ArrayList;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Fragment wrapper for the Bookshelf list
 * 
 * @author pjw
 */
public class BookshelfDialogFragment extends BookCatalogueDialogFragment {
	/** ID passed by caller. Can be 0, will be passed back in event */
	private int mDialogId;
	/** Book ID */
	private Long mRowId;
	/** Current display text for bookshelf list */
	private String mCurrText;
	/** Current encoded list of bookshelves */
	private String mCurrList;

	/**
	 * Interface for message sending
	 * 
	 * @author pjw
	 */
	public interface OnBookshelfCheckChangeListener {
		public void onBookshelfCheckChanged(int dialogId, BookshelfDialogFragment dialog, boolean checked, String shelf, String textList, String encodedList);
	}

	/**
	 * Constructor
	 * 
	 * @param dialogId		ID passed by caller. Can be 0, will be passed back in event
	 * @param rowId			Book ID
	 * @param initialText	Initial display text for bookshelf list 
	 * @param initialList	Initial encoded list of bookshelves
	 * 
	 * @return				Instance of dialog fragment
	 */
	public static BookshelfDialogFragment newInstance(int dialogId, Long rowId, String initialText, String initialList) {
		BookshelfDialogFragment frag = new BookshelfDialogFragment();
        Bundle args = new Bundle();
        args.putInt("dialogId", dialogId);
        args.putLong("rowId", rowId);
        args.putString("text", initialText);
        args.putString("list", initialList);
        frag.setArguments(args);
        return frag;
    }

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		if (! (a instanceof OnBookshelfCheckChangeListener))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnBookshelfCheckChangeListener");
		
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View root = inflater.inflate(R.layout.bookshelf_dialog, null);
        return root;
    }

	/**
	 * Save instance variables that we need
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("list", mCurrList);
		outState.putString("text", mCurrText);
	}

	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Grab the args
		final Bundle args = getArguments();
    	mDialogId = args.getInt("dialogId");
		mRowId = args.getLong("rowId");
		// Retrieve dynamic values
    	if (savedInstanceState != null && savedInstanceState.containsKey("text")) { 
        	mCurrText = savedInstanceState.getString("text");
    	} else {
        	mCurrText = args.getString("text");
    	}
    	
    	if (savedInstanceState != null && savedInstanceState.containsKey("list")) { 
        	mCurrList = savedInstanceState.getString("list");
    	} else {
        	mCurrList = args.getString("list");
    	}

    	// Setp the dialog
		getDialog().setTitle(R.string.select_bookshelves);

		// Build a list of shelves
		CatalogueDBAdapter db = new CatalogueDBAdapter(getActivity());
		Cursor bookshelves_for_book = null;
    	db.open();
    	try {
        	final View rootView = getView();

    		if (mRowId == null) {
    			bookshelves_for_book = db.fetchAllBookshelves();
    		} else {
    			bookshelves_for_book = db.fetchAllBookshelves(mRowId);
    		}
    	
    		// Handle the OK button
    		Button button = (Button) rootView.findViewById(R.id.bookshelf_dialog_button);
    		button.setOnClickListener(new View.OnClickListener() {
    			@Override
    			public void onClick(View v) {
    				BookshelfDialogFragment.this.dismiss();
    			}
    		});

    		// Get the root view for the list of checkboxes
    		LinearLayout cbRoot = (LinearLayout) rootView.findViewById(R.id.bookshelf_dialog_root);

    		// Loop through all bookshelves and build the checkbox list
    		if (bookshelves_for_book.moveToFirst()) { 
    			final String shelves = BOOKSHELF_SEPERATOR + mCurrList + BOOKSHELF_SEPERATOR;
    			do { 
    				final CheckBox cb = new CheckBox(getActivity());
    				boolean checked = false;
    				String db_bookshelf = bookshelves_for_book.getString(bookshelves_for_book.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)).trim();
    				String db_encoded_bookshelf = Utils.encodeListItem(db_bookshelf, BOOKSHELF_SEPERATOR);
    				if (shelves.indexOf(BOOKSHELF_SEPERATOR + db_encoded_bookshelf + BOOKSHELF_SEPERATOR) > -1) {
    					checked = true;
    				}
    				cb.setChecked(checked);
    				cb.setHintTextColor(Color.WHITE);
    				cb.setHint(db_bookshelf);
    				// Setup a click listener that sends all clicks back to the calling activity and maintains the two lists
    				cb.setOnClickListener(new OnClickListener() {
    					@Override
    					public void onClick(View v) {
    						String hint = cb.getHint() + "";
    						String name = hint.trim();
    						String encoded_name = Utils.encodeListItem(name, BOOKSHELF_SEPERATOR);
    						// If box is checked, then we just append to list
    						if (cb.isChecked()) {
    							String curr = mCurrText;
    							String list = mCurrList;
    							if (mCurrText == null || mCurrText.equals("")) {
    								mCurrText = name;
    								mCurrList = encoded_name;
    							} else {
    								mCurrText += ", " + name;
    								mCurrList += BOOKSHELF_SEPERATOR + encoded_name;
    							}
    						} else {
    							// Get the underlying list
    							ArrayList<String> shelves = Utils.decodeList(mCurrList, BOOKSHELF_SEPERATOR);
    							// Start a new list
    							String newList = "";
    							String newText = "";
    							for(String s : shelves) {
    								// If item in underlying list is non-blank...
    								if (s != null && !s.equals("")) {
    									// If item in underlying list does not match...
    									if (!s.equalsIgnoreCase(name)) {
    										// Encode item
    										String item = Utils.encodeListItem(s, BOOKSHELF_SEPERATOR);
    										// Append to list (or set to only element if list empty)
    										if (newList.equals("")) {
    											newList = Utils.encodeListItem(s, BOOKSHELF_SEPERATOR);
    											newText = s;
    										} else {
    											newList += BOOKSHELF_SEPERATOR + item;
    											newText += ", " + s;
    										}
    									}
    								}
    							}
    							mCurrList = newList;
    							mCurrText = newText;
    						}
    						((OnBookshelfCheckChangeListener)getActivity()).onBookshelfCheckChanged(
    								mDialogId, 
    								BookshelfDialogFragment.this, 
    								cb.isChecked(), name, mCurrText, mCurrList);    							
    					}
    				});
    				cbRoot.addView(cb, cbRoot.getChildCount()-1);
    			} 
    			while (bookshelves_for_book.moveToNext()); 
    		} 

    	} finally {
    		if (bookshelves_for_book != null && !bookshelves_for_book.isClosed()) {
    			bookshelves_for_book.close();
    		}
    		if (db != null)
	    		db.close();
    	}
	}

}
