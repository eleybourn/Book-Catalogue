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

import java.util.ArrayList;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * This class is called by the BookEdit activity and displays the Loaned Tab
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * This will then be saved in the database for reference. 
 */
public class BookEditLoaned extends BookEditFragmentAbstract {

    // Define the permission launcher
    private final androidx.activity.result.ActivityResultLauncher<String> mRequestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission granted, refresh the view to load contacts
                    loanTo();
                }
            });

	/**
	 * Return a list of friends from your contact list. 
	 * This is for the autoComplete textView
	 *  
	 * @return an ArrayList of names
	 */
    protected ArrayList<String> getFriends() {
        ArrayList<String> friend_list = new ArrayList<>();
        Uri baseUri = null;
        String display_name = null;
        try {
            Class<?> c = Class.forName("android.provider.ContactsContract$Contacts");
            baseUri = (Uri) c.getField("CONTENT_URI").get(baseUri);
            display_name = (String) c.getField("DISPLAY_NAME").get(display_name);
        } catch (Exception e) {
            try {
                Class<?> c = Class.forName("android.provider.Contacts$People");
                baseUri = (Uri) c.getField("CONTENT_URI").get(baseUri);
                display_name = (String) c.getField("DISPLAY_NAME").get(display_name);
            } catch (Exception e2) {
                Logger.logError(e);
            }
        }
        assert baseUri != null;

        // Use try-with-resources to automatically close the cursor
        try (Cursor contactsCursor = requireActivity().getContentResolver().query(baseUri, null, null, null, null)) {
            if (contactsCursor != null) {
                // Calculate the index once, outside the loop for performance
                int nameColumnIndex = contactsCursor.getColumnIndex(display_name);

                while (contactsCursor.moveToNext()) {
                    // Check if the column actually exists (index >= 0) before reading
                    if (nameColumnIndex >= 0) {
                        String name = contactsCursor.getString(nameColumnIndex);
                        friend_list.add(name);
                    }
                }
            }
        }

        return friend_list;
    }

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
		sv.removeAllViews();
		LayoutInflater inf = requireActivity().getLayoutInflater();
		inf.inflate(R.layout.book_edit_loan, sv);

        //askPermission(sv);

		Button mConfirmButton = sv.findViewById(R.id.button_confirm);
		mConfirmButton.setOnClickListener(view -> {
            String friend = saveLoan();
            loaned(friend);
        });
	}

    private void askPermission(NestedScrollView sv) {
        AutoCompleteTextView mUserText = sv.findViewById(R.id.field_loan_to_who);
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {

            // We have permission, load the contacts safely
            try {
                ArrayAdapter<String> series_adapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_dropdown_item_1line, getFriends());
                mUserText.setAdapter(series_adapter);
            } catch (Exception e) {
                Logger.logError(e);
            }

        } else {
            // We don't have permission, request it
            // Note: You might want to show a rationale UI first if shouldShowRequestPermissionRationale returns true
            mRequestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS);
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
		sv.removeAllViews();
		LayoutInflater inf = requireActivity().getLayoutInflater();
		inf.inflate(R.layout.book_edit_loaned, sv);

		TextView mWhoText = sv.findViewById(R.id.field_loaned_to);
		mWhoText.setText(user);
		Button mConfirmButton = sv.findViewById(R.id.button_confirm);
		mConfirmButton.setOnClickListener(view -> {
            removeLoan();
            loanTo();
        });
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
		return friend;
	}
	
	/**
	 * Delete the user and book combination as a loan from the database
	 */
	private void removeLoan() {
		mDbHelper.deleteLoan(mEditManager.getBookData().getRowId(), true);
    }

	@Override
	protected void onLoadBookDetails(BookData book) {
        mFields.setAll(book);
	}
	

}
