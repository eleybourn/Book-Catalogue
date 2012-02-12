package com.eleybourn.bookcatalogue.database;

import java.util.Hashtable;

import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;

/**
 * Utility class to manage the construction and closure of persisted SQLiteStatement obejcts.
 * 
 * @author Grunthos
 */
public class SqlStatementManager {
	private final Hashtable<String, SynchronizedStatement> mStatements;
	private final SynchronizedDb mDb;
	
	public SqlStatementManager(SynchronizedDb db) {
		mDb = db;
		mStatements = new Hashtable<String, SynchronizedStatement>();
	}
	public SqlStatementManager() {
		mDb = null;
		mStatements = new Hashtable<String, SynchronizedStatement>();
	}
	
	public SynchronizedStatement add(final SynchronizedDb db, final String name, final String sql) {
		SynchronizedStatement stmt = db.compileStatement(sql);
		SynchronizedStatement old = mStatements.get(name);
		mStatements.put(name, stmt);
		if (old != null)
			old.close();
		return stmt;
	}
	
	public SynchronizedStatement add(String name, String sql) {
		if (mDb == null)
			throw new RuntimeException("Databse not set when SqlStatementManager created");
		return add(mDb, name, sql);
	}
	
	public void close() {
		synchronized(mStatements) {
			for(SynchronizedStatement s : mStatements.values()) {
				try {
					s.close();
				} catch (Exception e)
				{};
			}
			mStatements.clear();
		}
	}
	
	public int size() {
		return mStatements.size();
	}
}
