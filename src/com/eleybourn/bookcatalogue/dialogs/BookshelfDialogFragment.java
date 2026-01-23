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

import static com.eleybourn.bookcatalogue.BookAbstract.BOOKSHELF_SEPARATOR;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;

/**
 * Fragment wrapper for the Bookshelf list
 *
 * @author pjw
 */
public class BookshelfDialogFragment extends BookCatalogueDialogFragment {
    /**
     * ID passed by caller. Can be 0, will be passed back in event
     */
    private int mDialogId;
    /**
     * Current display text for bookshelf list
     */
    private String mCurrText;
    /**
     * Current encoded list of bookshelves
     */
    private String mCurrList;

    /**
     * Constructor
     *
     * @param dialogId    ID passed by caller. Can be 0, will be passed back in event
     * @param rowId       Book ID
     * @param initialText Initial display text for bookshelf list
     * @param initialList Initial encoded list of bookshelves
     * @return                Instance of dialog fragment
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
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Activity a = null;
        if (context instanceof Activity) {
            a = (Activity) context;
        }

        if (!(a instanceof OnBookshelfCheckChangeListener)) {
            assert a != null;
            throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnBookshelfCheckChangeListener");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_bookshelf, null);
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Grab the args
        final Bundle args = getArguments();
        assert args != null;
        mDialogId = args.getInt("dialogId");
        // Book ID
        long mRowId = args.getLong("rowId");
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

        // Set up the dialog
        assert getDialog() != null;

        // Build a list of shelves
        CatalogueDBAdapter db = new CatalogueDBAdapter(getActivity());
        Cursor bookshelves_for_book = null;
        db.open();
        try {
            final View rootView = getView();

            bookshelves_for_book = db.fetchAllBookshelves(mRowId);
            Log.d("BookCatalogue", "getBookShelves");

            // Handle the OK button
            assert rootView != null;
            Button button = rootView.findViewById(R.id.bookshelf_dialog_button);
            button.setOnClickListener(v -> BookshelfDialogFragment.this.dismiss());

            // Get the root view for the list of checkboxes
            LinearLayout cbRoot = rootView.findViewById(R.id.bookshelf_dialog_root);

            // Loop through all bookshelves and build the checkbox list
            if (bookshelves_for_book.moveToFirst()) {
                Log.d("BookCatalogue", "bookshelf ");
                final String shelves = BOOKSHELF_SEPARATOR + mCurrList + BOOKSHELF_SEPARATOR;
                do {
                    final CheckBox cb = new CheckBox(getActivity());
                    boolean checked = false;
                    int bookshelf = bookshelves_for_book.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF);
                    String db_bookshelf = bookshelves_for_book.getString(bookshelf).trim();
                    Log.d("BookCatalogue", "bookshelf " + db_bookshelf);
                    Log.d("BookCatalogue", "bookshelf " + bookshelves_for_book.getString(bookshelf));
                    String db_encoded_bookshelf = Utils.encodeListItem(db_bookshelf, BOOKSHELF_SEPARATOR);
                    if (shelves.contains(BOOKSHELF_SEPARATOR + db_encoded_bookshelf + BOOKSHELF_SEPARATOR)) {
                        checked = true;
                    }
                    cb.setChecked(checked);
                    cb.setHint(db_bookshelf);
                    // Setup a click listener that sends all clicks back to the calling activity and maintains the two lists
                    cb.setOnClickListener(v -> {
                        String hint = cb.getHint() + "";
                        String name = hint.trim();
                        String encoded_name = Utils.encodeListItem(name, BOOKSHELF_SEPARATOR);
                        // If box is checked, then we just append to list
                        if (cb.isChecked()) {
                            if (mCurrText == null || mCurrText.isEmpty()) {
                                mCurrText = name;
                                mCurrList = encoded_name;
                            } else {
                                mCurrText += ", " + name;
                                mCurrList += BOOKSHELF_SEPARATOR + encoded_name;
                            }
                        } else {
                            // Get the underlying list
                            ArrayList<String> shelves1 = Utils.decodeList(mCurrList, BOOKSHELF_SEPARATOR);
                            // Start a new list
                            StringBuilder newList = new StringBuilder();
                            StringBuilder newText = new StringBuilder();
                            for (String s : shelves1) {
                                // If item in underlying list is non-blank...
                                if (s != null && !s.isEmpty()) {
                                    // If item in underlying list does not match...
                                    if (!s.equalsIgnoreCase(name)) {
                                        // Encode item
                                        String item = Utils.encodeListItem(s, BOOKSHELF_SEPARATOR);
                                        // Append to list (or set to only element if list empty)
                                        if (newList.length() == 0) {
                                            newList = new StringBuilder(Utils.encodeListItem(s, BOOKSHELF_SEPARATOR));
                                            newText = new StringBuilder(s);
                                        } else {
                                            newList.append(BOOKSHELF_SEPARATOR).append(item);
                                            newText.append(", ").append(s);
                                        }
                                    }
                                }
                            }
                            mCurrList = newList.toString();
                            mCurrText = newText.toString();
                        }
                        ((OnBookshelfCheckChangeListener) requireActivity()).onBookshelfCheckChanged(
                                mDialogId,
                                BookshelfDialogFragment.this,
                                cb.isChecked(), name, mCurrText, mCurrList);
                    });
                    cbRoot.addView(cb, cbRoot.getChildCount() - 1);
                    LayoutParams lp = cb.getLayoutParams();
                    lp.height = LayoutParams.WRAP_CONTENT;
                    lp.width = LayoutParams.WRAP_CONTENT;
                    cb.setLayoutParams(lp);
                }
                while (bookshelves_for_book.moveToNext());
            }

        } finally {
            if (bookshelves_for_book != null && !bookshelves_for_book.isClosed()) {
                bookshelves_for_book.close();
            }
            db.close();
        }
    }

    /**
     * Interface for message sending
     *
     * @author pjw
     */
    public interface OnBookshelfCheckChangeListener {
        void onBookshelfCheckChanged(int dialogId, BookshelfDialogFragment dialog, boolean checked, String shelf, String textList, String encodedList);
    }

}
