package com.eleybourn.bookcatalogue.dialogs;

import static com.eleybourn.bookcatalogue.BookDetailsAbstract.BOOKSHELF_SEPERATOR;

import java.util.ArrayList;

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
import com.eleybourn.bookcatalogue.utils.Utils;

public class BookshelfDialogFragment extends BookCatalogueDialogFragment {
	private OnBookshelfChangeListener mListener;
	private Long mRowId;
	private String mCurrText;
	private String mCurrList;

	public interface OnBookshelfChangeListener {
		public void onCheckChanged(boolean checked, String shelf, String textList, String encodedList);
	}
	
	public BookshelfDialogFragment(Long rowId, String initialText, String initialList, OnBookshelfChangeListener listener) {
		mRowId = rowId;
		mListener = listener;
		mCurrText = initialText;
		mCurrList = initialList;
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View root = inflater.inflate(R.layout.bookshelf_dialog, null);
        return root;
    }

	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getDialog().setTitle(R.string.bookshelf_title);

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
    	
    		Button button = (Button) rootView.findViewById(R.id.bookshelf_dialog_button);
    		button.setOnClickListener(new View.OnClickListener() {
    			@Override
    			public void onClick(View v) {
    				BookshelfDialogFragment.this.dismiss();
    			}
    		});

    		LinearLayout cbRoot = (LinearLayout) rootView.findViewById(R.id.bookshelf_dialog_root);
    		
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
    						if (mListener != null) {
    							mListener.onCheckChanged(cb.isChecked(), name, mCurrText, mCurrList);
    						}
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
