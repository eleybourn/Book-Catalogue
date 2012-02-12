package com.eleybourn.bookcatalogue.booklist;


import com.eleybourn.bookcatalogue.TrackedCursor;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

/**
 * Cursor object that makes the underlying BooklistBuilder available to users of the Cursor, as
 * well as providing some information about the builder objects.
 * 
 * @author Grunthos
 */
public class BooklistCursor extends TrackedCursor {
	/** Underlying BooklistBuilder object */
	private final BooklistBuilder mBuilder;
	/** Cached RowView for this cursor */
	private BooklistRowView mRowView = null;
	/** ID counter */
	private static Integer mBooklistCursorIdCounter = 0;
	/** ID of this cursor */
	private final long mId;

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

	/*
	 * no need for this yet; it may even die because table is deleted and recreated.
	 */
	//	public boolean requeryRebuild() {
	//		mBuilder.rebuild();
	//		return requery();
	//	}
}
