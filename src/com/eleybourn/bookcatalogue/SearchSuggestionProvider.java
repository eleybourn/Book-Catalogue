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
    CatalogueDBAdapter mDbHelper = null;

    public SearchSuggestionProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        assert selectionArgs != null;
        if (selectionArgs[0].isEmpty()) {
            return null;
        }
        if (mDbHelper == null) {
            mDbHelper = new CatalogueDBAdapter(getContext());
            mDbHelper.open();
        }
        return mDbHelper.fetchSearchSuggestions(selectionArgs[0]);
    }
}
