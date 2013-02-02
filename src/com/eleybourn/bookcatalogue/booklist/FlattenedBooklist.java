package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_ROW_NAVIGATOR_FLATTENED_DEFN;
import android.database.sqlite.SQLiteDoneException;

import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition.TableTypes;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;

/**
 * Class to provide a simple interface into a temporary table containing a list of book IDs on
 * the same order as an underlying book list.
 * 
 * @author pjw
 */
public class FlattenedBooklist {
	/** Underlying temporary table definition */
	private TableDefinition mTable;
	/** Connection to db; we need this to keep the table alive */
	private SynchronizedDb mDb;
	/** Default position (before start) */
	private long mPosition = -1;
	/** Book ID from the currently selected row */
	private Long mBookId = null;
	/** Collection of statements compiled for this object */
	private SqlStatementManager mStatements;

	/**
	 * Constructor
	 * 
	 * @param db	Database connection
	 * @param table	Table definition
	 */
	public FlattenedBooklist(SynchronizedDb db, TableDefinition table) {
		init(db, table.clone());
	}

	/**
	 * Constructor
	 * 
	 * @param db			Database connection
	 * @param tableName		Name of underlying table
	 */
	public FlattenedBooklist(SynchronizedDb db, String tableName) {
		TableDefinition flat = TBL_ROW_NAVIGATOR_FLATTENED_DEFN.clone();
		flat.setName(tableName);
		flat.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY
		init(db, flat);
	}

	/**
	 * Shared constructor utility routine.Save the passed values.
	 * 
	 * @param db	Database connection
	 * @param table	Table definition
	 */
	private void init(SynchronizedDb db, TableDefinition table) {
		mDb = db;
		mTable = table;
		mStatements = new SqlStatementManager(mDb);
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public TableDefinition getTable() {
		return mTable;
	}
	
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public Long getBookId() {
		return mBookId;
	}

	public void close() {
		mStatements.close();
	}
	
	public void deleteData() {
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

	public boolean exists() {
		return mTable.exists(mDb);
	}

	private static final String NEXT_STMT_NAME = "next";
	public boolean moveNext() {
		SynchronizedStatement stmt = mStatements.get(NEXT_STMT_NAME);
		if (stmt == null) {
			String sql = "Select " + mTable.dot(DOM_ID) + "|| '/' || " + mTable.dot(DOM_BOOK) 
					+ " From " + mTable.ref() 
					+ " Where " + mTable.dot(DOM_ID) + " > ? and " + mTable.dot(DOM_BOOK) + " <> Coalesce(?,-1)"
					+ " Order by " + mTable.dot(DOM_ID) + " Asc Limit 1";
			stmt = mStatements.add(NEXT_STMT_NAME, sql);
		}
		stmt.bindLong(1, mPosition);
		if (mBookId != null) {
			stmt.bindLong(2, mBookId);
		} else {
			stmt.bindNull(2);
		}
		return updateDetailsFromStatement(stmt);
	}

	private static final String PREV_STMT_NAME = "prev";
	public boolean movePrev() {
		SynchronizedStatement stmt = mStatements.get(PREV_STMT_NAME);
		if (stmt == null) {
			String sql = "Select " + mTable.dot(DOM_ID) + "|| '/' || " + mTable.dot(DOM_BOOK) 
					+ " From " + mTable.ref() 
					+ " Where " + mTable.dot(DOM_ID) + " < ? and " + mTable.dot(DOM_BOOK) + " <> Coalesce(?,-1)"
					+ " Order by " + mTable.dot(DOM_ID) + " Desc Limit 1";
			stmt = mStatements.add(PREV_STMT_NAME, sql);
		}
		stmt.bindLong(1, mPosition);
		if (mBookId != null) {
			stmt.bindLong(2, mBookId);
		} else {
			stmt.bindNull(2);
		}
		return updateDetailsFromStatement(stmt);
	}

	private static final String MOVE_STMT_NAME = "move";
	public boolean moveTo(Integer pos) {
		SynchronizedStatement stmt = mStatements.get(MOVE_STMT_NAME);
		if (stmt == null) {
			String sql = "Select " + mTable.dot(DOM_ID) + "|| '/' || " + mTable.dot(DOM_BOOK) 
					+ " From " + mTable.ref() + " Where " + mTable.dot(DOM_ID) + " = ?";
			stmt = mStatements.add(MOVE_STMT_NAME, sql);
		}
		stmt.bindLong(1, pos);
		if ( updateDetailsFromStatement(stmt) ) {
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

	public long getPosition() {
		return mPosition;
	}

	private static final String COUNT_STMT_NAME = "count";
	public long getCount() {
		SynchronizedStatement stmt = mStatements.get(COUNT_STMT_NAME);
		if (stmt == null) {
			String sql = "Select Count(*) From " + mTable.ref();
			stmt = mStatements.add(COUNT_STMT_NAME, sql);
		}
		return stmt.simpleQueryForLong();
	}
	
	private static final String POSITION_STMT_NAME = "position";
	public long getAbsolutePosition() {
		SynchronizedStatement stmt = mStatements.get(POSITION_STMT_NAME);
		if (stmt == null) {
			String sql = "Select Count(*) From " + mTable.ref()
					+ " where " + mTable.dot(DOM_ID) + " <= ?";
			stmt = mStatements.add(POSITION_STMT_NAME, sql);
		}
		stmt.bindLong(1, mPosition);
		return stmt.simpleQueryForLong();		
	}
	
	public void finalize() {
		close();
	}
}