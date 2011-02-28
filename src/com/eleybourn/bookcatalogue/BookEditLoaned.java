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
import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This class is called by the BookEdit activity and displays the Loaned Tab
 * 
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * This will then be saved in the database for reference. 
 */
public class BookEditLoaned extends Activity {
	/* mRowId contains the id of the book */
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;

	/**
	 * getRowId will extract the book id from either the saved bundle (from pausing the activity) 
	 * or from the passed extras Bundle.
	 * 
	 * @param savedInstanceState The saved bundle (from pausing). Can be null.
	 */
	protected void getRowId(Bundle savedInstanceState) {
		/* Get any information from the extras bundle */
		mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		if (mRowId == null) {
			Bundle extras = getIntent().getExtras();
			mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		}
	}

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
		Cursor contactsCursor = getContentResolver().query(baseUri, null, null, null, null);
		while (contactsCursor.moveToNext()) {
			String name = contactsCursor.getString(contactsCursor.getColumnIndex(display_name));
			friend_list.add(name);
		}
		return friend_list;
	}

	/**
	 * Called when the activity is first created. This function will check whether a book has been loaned
	 * and display the appropriate page as required. 
	 * 
	 * @param savedInstanceState The saved bundle (from pausing). Can be null.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		
		getRowId(savedInstanceState);
		if (mRowId == null || mRowId == 0) {
			/* This activity must have a row id, i.e. you can't loan a book you haven't created yet */
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
		try {
			Cursor book = mDbHelper.fetchBookById(mRowId);
			if (book != null) {
				book.moveToFirst();
			}
			startManagingCursor(book);
			String title = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)); 
			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + title);
		} catch (Exception e) {
			// do nothing - default title
		}
		
		String user = mDbHelper.fetchLoanByBook(mRowId);
		if (user == null) {
			loanTo();
		} else {
			loaned(user);
		}
		
	}
	
	/**
	 * Display the loan to page. It is slightly different to the existing loan page
	 */
	private void loanTo() {
		setContentView(R.layout.edit_book_loan);
		AutoCompleteTextView mUserText = (AutoCompleteTextView) findViewById(R.id.loan_to_who);
		try {
			ArrayAdapter<String> series_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getFriends());
			mUserText.setAdapter(series_adapter);
		} catch (Exception e) {
			Logger.logError(e);
		}
		Button mConfirmButton = (Button) findViewById(R.id.confirm);
		mConfirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				String friend = saveLoan();
				setResult(RESULT_OK);
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
		setContentView(R.layout.edit_book_loaned);
		TextView mWhoText = (TextView) findViewById(R.id.who);
		mWhoText.setText(user);
		Button mConfirmButton = (Button) findViewById(R.id.confirm);
		mConfirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				removeLoan();
				setResult(RESULT_OK);
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
		AutoCompleteTextView mUserText = (AutoCompleteTextView) findViewById(R.id.loan_to_who);
		String friend = mUserText.getText().toString();
		Bundle values = new Bundle();
		values.putString(CatalogueDBAdapter.KEY_ROWID, mRowId.toString());
		values.putString(CatalogueDBAdapter.KEY_LOANED_TO, friend);
		mDbHelper.createLoan(values);
		return friend;
	}
	
	/**
	 * Delete the user and book combination as a loan from the database
	 */
	private void removeLoan() {
		mDbHelper.deleteLoan(mRowId);
		return;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}

}
