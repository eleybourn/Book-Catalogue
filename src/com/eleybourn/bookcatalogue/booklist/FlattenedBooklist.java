package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_ROW_NAVIGATOR_FLATTENED_DEF;
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
		TableDefinition flat = TBL_ROW_NAVIGATOR_FLATTENED_DEF.clone();
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
	 */
	public TableDefinition getTable() {
		return mTable;
	}
	
	/**
	 * Accessor
	 */
	public Long getBookId() {
		return mBookId;
	}

	/**
	 * Release resource-consuming stuff
	 */
	public void close() {
		mStatements.close();
	}
	
	/**
	 * Cleanup the underlying table
	 */
	public void deleteData() {
		mTable.drop(mDb);
		mTable.close();
	}

	/**
	 * Passed a statement update the 'current' row details based on the columns returned
	 */
	private boolean updateDetailsFromStatement(SynchronizedStatement stmt) {
		// Get a pair of ID's separated by a '/'
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

	/**
	 * Check that the referenced table exists. This is important for resumed activities
	 * where th underlying database connection may have closed and the table been deleted
	 * as a result.
	 */
	public boolean exists() {
		return mTable.exists(mDb);
	}

	/**
	 * Name for the 'next' statement
	 */
	private static final String NEXT_STMT_NAME = "next";
	/**
	 * Move to the next book row
	 * 
	 * @return	true if successful
	 */
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

	/**
	 * Name for the 'prev' statement
	 */
	private static final String PREV_STMT_NAME = "prev";
	/**
	 * Move to the previous book row
	 * 
	 * @return	true if successful
	 */
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

	/**
	 * Name for the 'move-to' statement
	 */
	private static final String MOVE_STMT_NAME = "move";
	/**
     * Move to the specified book row, based on the row ID, not the book ID or row number.
     * The row ID should be the row number in the table, including header-related rows.
     */
	public void moveTo(Integer pos) {
		SynchronizedStatement stmt = mStatements.get(MOVE_STMT_NAME);
		if (stmt == null) {
			String sql = "Select " + mTable.dot(DOM_ID) + "|| '/' || " + mTable.dot(DOM_BOOK) 
					+ " From " + mTable.ref() + " Where " + mTable.dot(DOM_ID) + " = ?";
			stmt = mStatements.add(MOVE_STMT_NAME, sql);
		}
		stmt.bindLong(1, pos);
		if ( updateDetailsFromStatement(stmt) ) {
        } else {
			long posSav = mPosition;
			mPosition = pos;
			if (moveNext() || movePrev()) {
            } else {
				mPosition = posSav;
            }
		}
	}
	
	/**
	 * Move to the first row
	 * 
	 * @return	true if successful
	 */
	public boolean moveFirst() {
		mPosition = -1;
		mBookId = null;
		return moveNext();
	}

	/**
	 * Move to the last row
	 * 
	 * @return	true if successful
	 */
	public boolean moveLast() {
		mPosition = Long.MAX_VALUE;
		mBookId = null;
		return movePrev();
	}

	/**
	 * Get the underlying row position (row ID)
	 */
	public long getPosition() {
		return mPosition;
	}

	/**
	 * Name for the 'count' statement
	 */
	private static final String COUNT_STMT_NAME = "count";
	/**
	 * Get the total row count
	 * 
	 * @return	number of rows
	 */
	public long getCount() {
		SynchronizedStatement stmt = mStatements.get(COUNT_STMT_NAME);
		if (stmt == null) {
			String sql = "Select Count(*) From " + mTable.ref();
			stmt = mStatements.add(COUNT_STMT_NAME, sql);
		}
		return stmt.simpleQueryForLong();
	}
	
	/**
	 * Name for the 'absolute-position' statement
	 */
	private static final String POSITION_STMT_NAME = "position";
	/**
	 * Get the position of the current record in the table
	 * 
	 * @return	position
	 */
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

	/**
	 * Cleanup the statements
	 */
    protected void finalize() {
		close();
	}
}