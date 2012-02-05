package com.eleybourn.bookcatalogue.database;

import java.util.Hashtable;

import com.eleybourn.bookcatalogue.database.DbUtils.SynchronizedDb;


import android.database.sqlite.SQLiteStatement;

/**
 * Utility class to manage the construction and closure of persisted SQLiteStatement obejcts.
 * 
 * TODO: Integrate with SynchronizedDb and create SynchronizedStatement objects so that
 * calls to 'execute' etc will be synchronized using write locks.
 * 
 * @author Grunthos
 *
 */
public class SqlStatementManager {
	private final Hashtable<String, SQLiteStatement> mStatements;
	private final SynchronizedDb mDb;
	
	public SqlStatementManager(SynchronizedDb db) {
		mDb = db;
		mStatements = new Hashtable<String, SQLiteStatement>();
	}
	
	public SQLiteStatement add(String name, String sql) {
		SQLiteStatement stmt = mDb.compileStatement(sql);
		SQLiteStatement old = mStatements.get(name);
		mStatements.put(name, stmt);
		if (old != null)
			old.close();
		return stmt;
	}
	
	public void close() {
		synchronized(mStatements) {
			for(SQLiteStatement s : mStatements.values()) {
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
