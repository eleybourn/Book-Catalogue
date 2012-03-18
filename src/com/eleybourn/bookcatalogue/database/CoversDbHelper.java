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

import java.io.ByteArrayOutputStream;
import java.util.Date;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.StorageUtils;
import com.eleybourn.bookcatalogue.TrackedCursor;
import com.eleybourn.bookcatalogue.Utils;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.DbUtils.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;
import android.graphics.Bitmap;

/**
 * DB Helper for Covers DB on external storage.
 * 
 * In the initial pass, the covers database has a single table whose members are accessed via unique
 * 'file names'.
 * 
 * @author Philip Warner
 */
public class CoversDbHelper extends GenericOpenHelper {
	private SynchronizedDb mDb;

	/** Debug counter */
	private static Integer mInstanceCount = 0;

	/** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync */
	private static final Synchronizer mSynchronizer = new Synchronizer();

	/** List of statements we create so we can close them when object is closed. */
	private SqlStatementManager mStatements = new SqlStatementManager();

	/** DB location */
	private static final String COVERS_DATABASE_NAME = StorageUtils.getSharedStoragePath() + "/covers.db";
	/** DB Version */
	private static final int COVERS_DATABASE_VERSION = 1;

	// Domain and table definitions
	
	/** Static Factory object to create the custom cursor */
	public static final CursorFactory mTrackedCursorFactory = new CursorFactory() {
			@Override
			public Cursor newCursor(
					SQLiteDatabase db,
					SQLiteCursorDriver masterQuery, 
					String editTable,
					SQLiteQuery query) 
			{
				return new TrackedCursor(db, masterQuery, editTable, query, mSynchronizer);
			}
	};

	public static final DomainDefinition DOM_ID = new DomainDefinition( "_id", "integer",  "primary key autoincrement", "");
	public static final DomainDefinition DOM_DATE = new DomainDefinition( "date", "datetime", "default current_timestamp", "not null");
	public static final DomainDefinition DOM_TYPE = new DomainDefinition( "type", "text", "", "not null");	// T = Thumbnail; C = cover?
	public static final DomainDefinition DOM_IMAGE = new DomainDefinition( "image", "blob", "",  "not null");
	public static final DomainDefinition DOM_WIDTH = new DomainDefinition( "width", "integer", "", "not null");
	public static final DomainDefinition DOM_HEIGHT = new DomainDefinition( "height", "integer", "",  "not null");
	public static final DomainDefinition DOM_SIZE = new DomainDefinition( "size", "integer", "",  "not null");
	public static final DomainDefinition DOM_FILENAME = new DomainDefinition( "filename", "text", "", "");
	public static final TableDefinition TBL_IMAGE = new TableDefinition("image", DOM_ID, DOM_TYPE, DOM_IMAGE, DOM_DATE, DOM_WIDTH, DOM_HEIGHT, DOM_SIZE, DOM_FILENAME );
	static {
		TBL_IMAGE
			.addIndex("id", true, DOM_ID)
			.addIndex("file", true, DOM_FILENAME)
			.addIndex("file_date", true, DOM_FILENAME, DOM_DATE);
	};

	public static final TableDefinition TABLES[] = new TableDefinition[] {TBL_IMAGE};

	/**
	 * Constructor. Fill in required fields. This is NOT based on SQLiteOpenHelper so does not need a context.
	 */
	public CoversDbHelper() {
		super(COVERS_DATABASE_NAME, mTrackedCursorFactory, COVERS_DATABASE_VERSION);
		synchronized(mInstanceCount) {
			mInstanceCount++;
			System.out.println("CovDBA instances: " + mInstanceCount);
		}
	}
	/**
	 * As with SQLiteOpenHelper, routine called to create DB
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		DbUtils.createTables(new SynchronizedDb(db, mSynchronizer), TABLES, true );
	}
	/**
	 * As with SQLiteOpenHelper, routine called to upgrade DB
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		throw new RuntimeException("Upgrades not handled yet!");
	}

	private SynchronizedDb getDb() {
		if (mDb == null)
			mDb = new SynchronizedDb(this, mSynchronizer);
		return mDb;
	}
	/**
	 * Delete the named 'file'
	 * 
	 * @param filename
	 */
	public void deleteFile(final String filename) {
		SynchronizedDb db = getDb();
		SyncLock txLock = db.beginTransaction(true);
		try {
			db.execSQL("Drop table " + TBL_IMAGE);
			DbUtils.createTables(db, new TableDefinition[] {TBL_IMAGE}, true);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction(txLock);
		}
	}

	/**
	 * Get the named 'file'
	 * 
	 * @param filename
	 * 
	 * @return	byte[] of image data
	 */
	public final byte[] getFile(final String filename, final Date lastModified) {
		SynchronizedDb db = this.getDb();

		Cursor c = db.query(TBL_IMAGE.getName(), new String[]{DOM_IMAGE.name}, DOM_FILENAME + "=? and " + DOM_DATE + " > ?", 
							new String[]{filename, Utils.toSqlDateTime(lastModified)}, null, null, null);
		try {
			if (!c.moveToFirst())
				return null;		
			return c.getBlob(0);
		} finally {
			c.close();
		}
	}

	/**
	 * Get the named 'file'
	 * 
	 * @param filename
	 * 
	 * @return	byet[] of image data
	 */
	public boolean isEntryValid(String filename, Date lastModified) {
		SynchronizedDb db = this.getDb();
		Cursor c = db.query(TBL_IMAGE.getName(), new String[]{DOM_ID.name}, DOM_FILENAME + "=? and " + DOM_DATE + " > ?", 
								new String[]{filename, Utils.toSqlDateTime(lastModified)}, null, null, null);
		try {
			return c.moveToFirst();
		} finally {
			c.close();
		}
	}
	/**
	 * Save the passed bitmap to a 'file'
	 * 
	 * @param filename
	 * @param bm
	 */
	public void saveFile(final String filename, final Bitmap bm) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bm.compress(Bitmap.CompressFormat.JPEG, 70, out);
		byte[] bytes = out.toByteArray();

		saveFile(filename, bm.getHeight(), bm.getWidth(), bytes);
	}

	/**
	 * Save the passed encoded image data to a 'file'
	 * 
	 * @param filename
	 * @param bm
	 */
	private SynchronizedStatement mExistsStmt = null;
	public void saveFile(final String filename, final int height, final int width, final byte[] bytes) {
		SynchronizedDb db = this.getDb();

		if (mExistsStmt == null) {
			String sql = "Select Count(" + DOM_ID + ") From " + TBL_IMAGE + " Where " + DOM_FILENAME + " = ?";
			mExistsStmt = mStatements.add(db, "mExistsStmt", sql);
		}

		ContentValues cv = new ContentValues();

		cv.put(DOM_FILENAME.name, filename);
		cv.put(DOM_IMAGE.name, bytes);

		cv.put(DOM_DATE.name, Utils.toSqlDateTime(new Date()));
		cv.put(DOM_TYPE.name, "T");
		cv.put(DOM_WIDTH.name, height);
		cv.put(DOM_HEIGHT.name, width);
		cv.put(DOM_SIZE.name, bytes.length);

		mExistsStmt.bindString(1, filename);
		long rows = 0;
		
		SyncLock txLock = db.beginTransaction(true);
		try {
			if (mExistsStmt.simpleQueryForLong() == 0) {
				rows = db.insert(TBL_IMAGE.getName(), null, cv);
			} else {
				rows = db.update(TBL_IMAGE.getName(), cv, DOM_FILENAME.name + " = ?", new String[] {filename});
			}
			if (rows == 0)
				throw new RuntimeException("Failed to insert data");
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction(txLock);
		}
	}

	/**
	 * Erase all images in the covers cache
	 */
	private SynchronizedStatement mEraseCoverCacheStmt = null;
	public void eraseCoverCache() {
		SynchronizedDb db = this.getDb();

		if (mEraseCoverCacheStmt == null) {
			String sql = "Delete From " + TBL_IMAGE;
			mEraseCoverCacheStmt = mStatements.add(db, "mEraseCoverCacheStmt",  sql);
		}
		mEraseCoverCacheStmt.execute();
	}

	/**
	 * Erase all cached images relating to the passed book UUID.
	 * 
	 * @param uuid
	 */
	public int eraseCachedBookCover(String uuid) {
		SynchronizedDb db = this.getDb();

		String sql = DOM_FILENAME + " glob '" + uuid + ".*'";
		return db.delete(TBL_IMAGE.getName(), sql, CatalogueDBAdapter.EMPTY_STRING_ARRAY);
	}
	
	/**
	 * Analyze the database
	 */
	public void analyze() {
		SynchronizedDb db = this.getDb();
		String sql;
		// Don't do VACUUM -- it's a complete rebuild
		//sql = "vacuum";
		//db.execSQL(sql);
		sql = "analyze";
		db.execSQL(sql);
	}

	@Override
	public void close() {
		mStatements.close();
		super.close();
		synchronized(mInstanceCount) {
			mInstanceCount--;
			System.out.println("CovDBA instances: " + mInstanceCount);
		}
	}
	
}
