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

package com.eleybourn.bookcatalogue.booklist;

import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.utils.TrackedCursor;
import com.eleybourn.bookcatalogue.utils.Utils;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

/**
 * Cursor object that makes the underlying BooklistBuilder available to users of the Cursor, as
 * well as providing some information about the builder objects.
 * 
 * @author Philip Warner
 */
public class BooklistCursor extends TrackedCursor implements BooklistSupportProvider {
	/** Underlying BooklistBuilder object */
	private final BooklistBuilder mBuilder;
	/** Cached RowView for this cursor */
	private BooklistRowView mRowView = null;
	/** ID counter */
	private static Integer mBooklistCursorIdCounter = 0;
	/** ID of this cursor */
	private final long mId;

	/** Utils object; we need an instance for cover retrieval because it uses a DB connection
	 * that we do not want to make static. This instance is used by BookMultitypeListHandler.
	 */
	private Utils mUtils = null;


	/**
	 * Constructor
	 * 
	 * @param db			Underlying DB. Part of standard cursor constructor.
	 * @param driver		Part of standard cursor constructor.
	 * @param editTable		Part of standard cursor constructor.
	 * @param query			Part of standard cursor constructor.
	 * @param builder		BooklistBuilder used to make the query on which this cursor is based.
	 * @param sync			Synchronizer object
	 */
	public BooklistCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query, BooklistBuilder builder, Synchronizer sync) {
		super(db, driver, editTable, query, sync);
		// Allocate ID
		synchronized(mBooklistCursorIdCounter) {
			mId = ++mBooklistCursorIdCounter;
		}
		// Save builder.
		mBuilder = builder;
	}

	/**
	 * Get/create the Utils object for accessing covers.
	 */
	public Utils getUtils() {
		if (mUtils == null)
			mUtils = new Utils();
		return mUtils;
	}

	/**
	 * Get the ID for this cursor.
	 * 
	 * @return
	 */
	public long getId() {
		return mId;
	}

	/**
	 * Get the builder used to make this cursor.
	 * 
	 * @return
	 */
	public BooklistBuilder getBuilder() {
		return mBuilder;
	}

	/**
	 * Get a RowView for this cursor. Constructs one if necessary.
	 * 
	 * @return
	 */
	public BooklistRowView getRowView() {
		if (mRowView == null)
			mRowView = new BooklistRowView(this, mBuilder);
		return mRowView;
	}

	/**
	 * Get the number of levels in the book list.
	 * 
	 * @return
	 */
	public int numLevels() {
		return mBuilder.numLevels();
	}	

	@Override
	public void close() {
		if (mUtils != null)
			mUtils.close();
		super.close();
	}
	/*
	 * no need for this yet; it may even die because table is deleted and recreated.
	 */
	//	public boolean requeryRebuild() {
	//		mBuilder.rebuild();
	//		return requery();
	//	}
}
