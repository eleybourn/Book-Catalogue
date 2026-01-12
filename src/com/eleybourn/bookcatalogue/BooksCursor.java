/*
 * @copyright 2012 Philip Warner
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

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.utils.TrackedCursor;

/**
 * Cursor implementation for book-related queries. The cursor wraps common
 * column lookups and reduces code clutter when accessing common columns.
 * The cursor also simulates a 'selected' flag for each book based on a
 * hashmap of book IDs.
 * 
 * @author Philip Warner
 *
 */
public class BooksCursor extends TrackedCursor {

    /**
	 * Constructor
	 */
	public BooksCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query, Synchronizer sync) {
		super(db, driver, editTable, query, sync);
	}

    /**
	 * Get the row ID; need a local implementation so that get/setSelected() works.
	 */
	private int mIdCol = -2;
	public final long getId() {
		if (mIdCol < 0) {
			mIdCol = getColumnIndex(CatalogueDBAdapter.KEY_ROW_ID);
			if (mIdCol < 0)
				throw new RuntimeException("ISBN column not in result set");
		}
		return getLong(mIdCol);// mCurrentRow[mIsbnCol];
	}

	/**
	 * Get a RowView
	 */
	BooksRowView mView;
	public BooksRowView getRowView() {
		if (mView == null)
			mView = new BooksRowView(this);
		return mView;
	}

    /**
	 * Clear the RowView and selections, if any
	 */
	@Override
	public void close() {
		super.close();
		mView = null;
	}
}
