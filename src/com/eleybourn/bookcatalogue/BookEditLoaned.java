/*
 * @copyright 2010 Evan Leybourn
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.eleybourn.bookcatalogue.debug.Tracker;

/**
 * This class is called by the BookEdit activity and displays the Loaned Tab
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * This will then be saved in the database for reference.
 */
public class BookEditLoaned extends BookEditFragmentAbstract {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.book_edit_loan_base, container, false);
    }

    /**
     * Called when the activity is first created. This function will check whether a book has been loaned
     * and display the appropriate page as required.
     *
     * @param savedInstanceState The saved bundle (from pausing). Can be null.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        Tracker.enterOnCreate(this);
        if (savedInstanceState != null) {
            mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
        }

        try {
            super.onViewCreated(view, savedInstanceState);
            String user = mDbHelper.fetchLoanByBook(mEditManager.getBookData().getRowId());
            if (user == null) {
                loanTo();
            } else {
                loaned(user);
            }
        } finally {
            Tracker.exitOnCreate(this);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }


    /**
     * Display the loan to page. It is slightly different to the existing loan page
     */
    private void loanTo() {
        assert getView() != null;
        NestedScrollView sv = getView().findViewById(R.id.scrollView);
        try {
            sv.findViewById(R.id.layout_loan_to).setVisibility(View.VISIBLE);
            // Set the use of Caps for DE and FR
            AutoCompleteTextView mUserText = sv.findViewById(R.id.field_loan_to_who);
            mUserText.setText(R.string._empty_);
            mFields.add(R.id.field_loan_to_who, CatalogueDBAdapter.KEY_LOANED_TO, null);
            mFields.setAfterFieldChangeListener((field, newValue) -> mEditManager.setDirty(true));

            // TODO: Test code for setting CapSentences - need to create the preference and extend this across all relevant fields
            //String languageCode = Locale.getDefault().getLanguage();
            //if ("de".equals(languageCode) || "fr".equals(languageCode)) {
                // Get the original input type from the view
            //    int originalInputType = mUserText.getInputType();
                // Add the textCapSentences flag to the original input type
            //    mUserText.setInputType(originalInputType | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            //}

            Button mConfirmButton = sv.findViewById(R.id.button_loan);
            mConfirmButton.setText(R.string.button_loan_book);
            mConfirmButton.setOnClickListener(view -> {
                String friend = saveLoan();
                loaned(friend);
            });
        } catch (Exception ignored) {
        }
    }

    /**
     * Display the existing loan page. It is slightly different to the loan to page
     *
     * @param user The user the book was loaned to
     */
    private void loaned(String user) {
        assert getView() != null;
        NestedScrollView sv = getView().findViewById(R.id.scrollView);
        TextView mWhoText = sv.findViewById(R.id.field_loaned_to);
        mWhoText.setText(user);
        try {
            sv.findViewById(R.id.layout_loan_to).setVisibility(View.GONE);
            Button mConfirmButton = sv.findViewById(R.id.button_loan);
            mConfirmButton.setText(R.string.button_returned);
            mConfirmButton.setOnClickListener(view -> {
                removeLoan();
                loanTo();
            });
        } catch (Exception ignored) {
        }
    }

    /**
     * Save the user and book combination as a loan in the database
     *
     * @return the user
     */
    private String saveLoan() {
        assert getView() != null;
        AutoCompleteTextView mUserText = getView().findViewById(R.id.field_loan_to_who);
        String friend = mUserText.getText().toString();
        BookData values = mEditManager.getBookData();
        values.putString(CatalogueDBAdapter.KEY_LOANED_TO, friend);
        mDbHelper.createLoan(values, true);
        mUserText.setText(R.string._empty_);
        mEditManager.setDirty(false);
        return friend;
    }

    /**
     * Delete the user and book combination as a loan from the database
     */
    private void removeLoan() {
        mDbHelper.deleteLoan(mEditManager.getBookData().getRowId(), true);
        try {
            assert getView() != null;
            TextView loanedTo = getView().findViewById(R.id.field_loaned_to);
            loanedTo.setText(R.string.option_nobody);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onLoadBookDetails(BookData book) {
        mFields.setAll(book);
    }


}
