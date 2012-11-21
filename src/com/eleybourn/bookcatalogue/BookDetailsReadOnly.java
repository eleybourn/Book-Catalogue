package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.view.View;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class for representing read-only book details.
 * @author n.silin
 */
public class BookDetailsReadOnly extends BookDetailsAbstract {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		setContentView(R.layout.book_details);
		setRowIdByExtras();
		/*
		 * In superclass onCreate method we initialize fields, background,
		 * display metrics and other. So see superclass onCreate method.
		 */
		super.onCreate(savedInstanceState);
		
		/*
		 * Disable Anthology field. User will see Anthology tab if it set,
		 * so we need show that in read-only details.
		 */
		findViewById(R.id.anthology).setVisibility(View.GONE);
		
		if (mRowId != null && mRowId > 0) {
			populateFieldsFromDb(mRowId);
			//Populate author and series fields
			populateAuthorList();
			populateSeriesList();
		}
	}
	
	@Override
	// Override only one line from superclass method. See description below
	protected void populateSeriesList(){
		String newText;
		int size = 0;
		try {
			size = mSeriesList.size();
		} catch (NullPointerException e) {
			size = 0;
		}
		if (size == 0)
			//Override only this line to show "Not set" text when there is no Series
			return;
		else {
			Utils.pruneSeriesList(mSeriesList);
			Utils.pruneList(mDbHelper, mSeriesList);
			newText = mSeriesList.get(0).getDisplayName();
			if (mSeriesList.size() > 1)
				newText += " " + getResources().getString(R.string.and_others);
		}
		mFields.getField(R.id.series).setValue(newText);
	}
}
