/**
 * 
 */
package com.eleybourn.bookcatalogue;

import android.content.SearchRecentSuggestionsProvider;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author evan
 *
 */
public class SearchSuggestionProvider extends SearchRecentSuggestionsProvider {
	public final static String AUTHORITY = "com.eleybourn.bookcatalogue.SearchSuggestionProvider";
	public final static int MODE = DATABASE_MODE_QUERIES;
	
	public SearchSuggestionProvider() {
		setupSuggestions(AUTHORITY, MODE);
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		if (selectionArgs[0].equals("")) {
			return null;
		}
		CatalogueDBAdapter mDbHelper = new CatalogueDBAdapter(getContext());
		mDbHelper.open();
		Cursor mCursor = mDbHelper.fetchSearchSuggestions(selectionArgs[0]);
		return mCursor;
	}
}
