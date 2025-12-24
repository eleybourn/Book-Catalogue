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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * This class is called by the BookEdit activity and displays the Loaned Tab
 * 
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * This will then be saved in the database for reference. 
 */
public class BookEditLoaned extends BookEditFragmentAbstract {

	/**
	 * Return a list of friends from your contact list. 
	 * This is for the autoComplete textView
	 *  
	 * @return an ArrayList of names
	 */
	protected ArrayList<String> getFriends() {
		ArrayList<String> friend_list = new ArrayList<String>();
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
		Cursor contactsCursor = getActivity().getContentResolver().query(baseUri, null, null, null, null);
		while (contactsCursor.moveToNext()) {
			String name = contactsCursor.getString(contactsCursor.getColumnIndex(display_name));
			friend_list.add(name);
		}
		return friend_list;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View root = inflater.inflate(R.layout.edit_book_loan_base, container, false);
		return root;
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
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}


	/**
	 * Display the loan to page. It is slightly different to the existing loan page
	 */
	private void loanTo() {
		ScrollView sv = (ScrollView) getView().findViewById(R.id.root);
		sv.removeAllViews();
		LayoutInflater inf = getActivity().getLayoutInflater();
		inf.inflate(R.layout.edit_book_loan, sv);

		AutoCompleteTextView mUserText = (AutoCompleteTextView) sv.findViewById(R.id.loan_to_who);
		try {
			ArrayAdapter<String> series_adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_dropdown_item_1line, getFriends());
			mUserText.setAdapter(series_adapter);
		} catch (Exception e) {
			Logger.logError(e);
		}
		Button mConfirmButton = (Button) sv.findViewById(R.id.button_confirm);
		mConfirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				String friend = saveLoan();
				loaned(friend);
			}
		});
	}
	
	/**
	 * Display the existing loan page. It is slightly different to the loan to page
	 * 
	 * @param user The user the book was loaned to
	 */
	private void loaned(String user) {
		ScrollView sv = (ScrollView)  getView().findViewById(R.id.root);
		sv.removeAllViews();
		LayoutInflater inf = getActivity().getLayoutInflater();
		inf.inflate(R.layout.edit_book_loaned, sv);

		TextView mWhoText = (TextView) sv.findViewById(R.id.who);
		mWhoText.setText(user);
		Button mConfirmButton = (Button) sv.findViewById(R.id.button_confirm);
		mConfirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				removeLoan();
				loanTo();
			}
		});
	}
	
	/**
	 * Save the user and book combination as a loan in the database
	 * 
	 * @return the user
	 */
	private String saveLoan() {
		AutoCompleteTextView mUserText = (AutoCompleteTextView) getView().findViewById(R.id.loan_to_who);
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
		return;
	}

	@Override
	protected void onLoadBookDetails(BookData book) {
		if (!false)
			mFields.setAll(book);
		// TODO Auto-generated method stub
		
	}
	

}
