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

package com.eleybourn.bookcatalogue.database;

import java.util.Hashtable;

import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;

/**
 * Utility class to manage the construction and closure of persisted SQLiteStatement obejcts.
 * 
 * @author Philip Warner
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
	
	public SynchronizedStatement addOrGet(final SynchronizedDb db, final String name, final String sql) {
		SynchronizedStatement stmt = mStatements.get(name);
		if (stmt == null) {
			stmt = this.add(db, name, sql);
		}
		return stmt;
	}
	
	public SynchronizedStatement get(final String name) {
		return mStatements.get(name);
	}

	public SynchronizedStatement add(String name, String sql) {
		if (mDb == null)
			throw new RuntimeException("Databse not set when SqlStatementManager created");
		return add(mDb, name, sql);
	}
	
	public SynchronizedStatement addOrGet(String name, String sql) {
		if (mDb == null)
			throw new RuntimeException("Databse not set when SqlStatementManager created");
		return addOrGet(mDb, name, sql);
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
