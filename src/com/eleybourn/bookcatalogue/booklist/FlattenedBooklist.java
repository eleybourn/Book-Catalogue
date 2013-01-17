package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_ROW_NAVIGATOR_FLATTENED_DEFN;
import android.database.sqlite.SQLiteDoneException;

import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition.TableTypes;

/**
 * Class to provide a simple interface into a temp table containing a list of book IDs on
 * the same order as an underlying book list.
 * 
 * @author pjw
 */
public class FlattenedBooklist {
	private final TableDefinition mTable;
	private final SynchronizedDb mDb;
	private long mPosition = -1;
	private Long mBookId = null;

	public FlattenedBooklist(SynchronizedDb db, TableDefinition table) {
		mDb = db;
		mTable = table.clone();
	}

	public FlattenedBooklist(SynchronizedDb db, String tableName) {
		mDb = db;
		TableDefinition flat = TBL_ROW_NAVIGATOR_FLATTENED_DEFN.clone();
		flat.setName(tableName);
		flat.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY
		mTable = flat;
	}

	public TableDefinition getTable() {
		return mTable;
	}
	
	public Long getBookId() {
		return mBookId;
	}

	public void close() {
		mTable.drop(mDb);
		mTable.close();
	}
	
	private boolean updateDetailsFromStatement(SynchronizedStatement stmt) {
		String info;
		try {
			info = stmt.simpleQueryForString();		
		} catch (SQLiteDoneException e) {
			return false;
		}

		if (info == null)
			return false;

		final String[] data = info.split("/");
		mPosition = Long.parseLong(data[0]);
		mBookId = Long.parseLong(data[1]);

		return true;
	}

	private SynchronizedStatement mNextStmt = null;
	public boolean moveNext() {
		if (mNextStmt == null) {
			String sql = "Select " + mTable.dot(DOM_ID) + "|| '/' || " + mTable.dot(DOM_BOOK) 
					+ " From " + mTable.ref() + " Where " + mTable.dot(DOM_ID) + " > ?"
					+ " Order by " + mTable.dot(DOM_ID) + " Asc Limit 1";
			mNextStmt = mDb.compileStatement(sql);
		}
		mNextStmt.bindLong(1, mPosition);
		return updateDetailsFromStatement(mNextStmt);
	}

	private SynchronizedStatement mPrevStmt = null;
	public boolean movePrev() {
		if (mPrevStmt == null) {
			String sql = "Select " + mTable.dot(DOM_ID) + "|| '/' || " + mTable.dot(DOM_BOOK) 
					+ " From " + mTable.ref() + " Where " + mTable.dot(DOM_ID) + " < ?"
					+ " Order by " + mTable.dot(DOM_ID) + " Desc Limit 1";
			mPrevStmt = mDb.compileStatement(sql);
		}
		mPrevStmt.bindLong(1, mPosition);
		return updateDetailsFromStatement(mPrevStmt);
	}

	private SynchronizedStatement mMoveToStmt = null;
	public boolean moveTo(Integer pos) {
		if (mMoveToStmt == null) {
			String sql = "Select " + mTable.dot(DOM_ID) + "|| '/' || " + mTable.dot(DOM_BOOK) 
					+ " From " + mTable.ref() + " Where " + mTable.dot(DOM_ID) + " = ?";
			mMoveToStmt = mDb.compileStatement(sql);
		}
		mMoveToStmt.bindLong(1, pos);
		if ( updateDetailsFromStatement(mMoveToStmt) ) {
			return true;
		} else {
			long posSav = mPosition;
			mPosition = pos;
			if (moveNext() || movePrev()) {
				return true;
			} else {
				mPosition = posSav;
				return false;
			}
		}
	}
	public boolean moveFirst() {
		mPosition = -1;
		mBookId = null;
		return moveNext();
	}

	public boolean moveLast() {
		mPosition = Long.MAX_VALUE;
		mBookId = null;
		return movePrev();
	}

	private SynchronizedStatement mCountStmt = null;
	public long getCount() {
		if (mCountStmt == null) {
			String sql = "Select Count(*) From " + mTable.ref();
			mCountStmt = mDb.compileStatement(sql);
		}
		return mCountStmt.simpleQueryForLong();
	}
	
	private SynchronizedStatement mPositionStmt = null;
	public long getAbsolutePosition() {
		if (mPositionStmt == null) {
			String sql = "Select Count(*) From " + mTable.ref()
					+ " where " + mTable.dot(DOM_ID) + " <= ?";
			mPositionStmt = mDb.compileStatement(sql);
		}
		mPositionStmt.bindLong(1, mPosition);
		return mPositionStmt.simpleQueryForLong();		
	}
}