package com.eleybourn.bookcatalogue.booklist;


import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.FieldVisibility;
import com.eleybourn.bookcatalogue.TrackedCursor;
import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.database.DbUtils.Synchronizer;
import com.eleybourn.bookcatalogue.database.DbUtils.Synchronizer.SyncLock;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

/**
 * TODO: Document!
 * 
 * @author Grunthos
 */
public class BooklistCursor extends TrackedCursor {
	private final BooklistBuilder mBuilder;
	private BooklistRowView mRowView = null;
	private static Integer mBooklistCursorIdCounter = 0;
	private final long mId;

	public BooklistCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query, BooklistBuilder builder) {
		super(db, driver, editTable, query);
		synchronized(mBooklistCursorIdCounter) {
			mId = ++mBooklistCursorIdCounter;
		}
		mBuilder = builder;
	}

	public long getId() {
		return mId;
	}

	public BooklistBuilder getBuilder() {
		return mBuilder;
	}

	public BooklistRowView getRowView() {
		if (mRowView == null)
			mRowView = new BooklistRowView(this, mBuilder);
		return mRowView;
	}
	
	public int numLevels() {
		return mBuilder.numLevels();
	}	

	@Override
	public boolean requery() {
		Synchronizer sync = CatalogueDBAdapter.getSynchronizer();
		SyncLock l = sync.getSharedLock();
		try {
			System.out.println("BLC RQ");
			return super.requery();
		} finally {
			l.unlock();
		}
	}

	/*
	 * no need for this yet; it may even die because table is deleted and reacreated.
	 */
//	public boolean requeryRebuild() {
//		mBuilder.rebuild();
//		return requery();
//	}
}
