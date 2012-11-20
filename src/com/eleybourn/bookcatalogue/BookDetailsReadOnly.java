package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.debug.Tracker;

import android.os.Bundle;

public class BookDetailsReadOnly extends BookDetailsAbstract {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		setContentView(R.layout.book_details);
		setRowIdByExtras();
		super.onCreate(savedInstanceState);
		
		if (mRowId != null && mRowId > 0) {
			populateFieldsFromDb(mRowId);
			
			fixupAuthorList();
			fixupSeriesList();
		}
	}
}
