package com.eleybourn.bookcatalogue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;

import com.eleybourn.bookcatalogue.DbUtils.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;

/**
 * DB Helper for Covers DB on external storage.
 * 
 * In the initial pass, the covers database has a single table whose members are accessed via unique
 * 'file names'.
 * 
 * @author Grunthos
 */
public class CoversDbHelper extends GenericOpenHelper {
	/** List of statements we create so we can close them when object is closed. */
	private ArrayList<SQLiteStatement> mStatements = new ArrayList<SQLiteStatement>();

	/** DB location */
	private static final String COVERS_DATABASE_NAME = Utils.EXTERNAL_FILE_PATH + "/covers.db";
	/** DB Version */
	private static final int COVERS_DATABASE_VERSION = 1;

	// Domain and table definitions
	
	public static final DomainDefinition DOM_ID = new DomainDefinition( "_id", "integer primary key autoincrement");
	public static final DomainDefinition DOM_DATE = new DomainDefinition( "date", "datetime not null default current_timestamp");
	public static final DomainDefinition DOM_TYPE = new DomainDefinition( "type", "text not null");	// T = Thumbnail; C = cover?
	public static final DomainDefinition DOM_IMAGE = new DomainDefinition( "image", "blob not null");
	public static final DomainDefinition DOM_WIDTH = new DomainDefinition( "width", "integer not null");
	public static final DomainDefinition DOM_HEIGHT = new DomainDefinition( "height", "integer not null");
	public static final DomainDefinition DOM_SIZE = new DomainDefinition( "size", "integer not null");
	public static final DomainDefinition DOM_FILENAME = new DomainDefinition( "filename", "text");
	public static final TableDefinition TBL_IMAGE = new TableDefinition("image", DOM_ID, DOM_TYPE, DOM_IMAGE, DOM_DATE, DOM_WIDTH, DOM_HEIGHT, DOM_SIZE, DOM_FILENAME );
	static {
		TBL_IMAGE
			.addIndex(true, DOM_ID)
			.addIndex(true, DOM_FILENAME)
			.addIndex(true, DOM_FILENAME, DOM_DATE);
	};

	public static final TableDefinition TABLES[] = new TableDefinition[] {TBL_IMAGE};

	/**
	 * Constructor. Fill in required fields. This is NOT based on SQLiteOpenHelper so does not need a context.
	 */
	public CoversDbHelper() {
		super(COVERS_DATABASE_NAME, TrackedCursor.TrackedCursorFactory, COVERS_DATABASE_VERSION);
	}
	/**
	 * As with SQLiteOpenHelper, routine called to create DB
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		DbUtils.createTables(db, TABLES );
	}
	/**
	 * As with SQLiteOpenHelper, routine called to upgrade DB
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		throw new RuntimeException("Upgrades not handled yet!");
	}

	/**
	 * Delete the named 'file'
	 * 
	 * @param filename
	 */
	public void deleteFile(final String filename) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		try {
			db.execSQL("Drop table " + TBL_IMAGE);
			DbUtils.createTables(db, new TableDefinition[] {TBL_IMAGE});
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
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
		SQLiteDatabase db = this.getWritableDatabase();

		Cursor c = db.query(TBL_IMAGE.name, new String[]{DOM_IMAGE.name}, DOM_FILENAME + "=? and " + DOM_DATE + " > ?", 
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
		SQLiteDatabase db = this.getWritableDatabase();
		Cursor c = db.query(TBL_IMAGE.name, new String[]{DOM_ID.name}, DOM_FILENAME + "=? and " + DOM_DATE + " > ?", 
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
	private SQLiteStatement mExistsStmt = null;
	public void saveFile(final String filename, final int height, final int width, final byte[] bytes) {
		SQLiteDatabase db = this.getWritableDatabase();

		if (mExistsStmt == null) {
			String sql = "Select Count(" + DOM_ID + ") From " + TBL_IMAGE + " Where " + DOM_FILENAME + " = ?";
			mExistsStmt = db.compileStatement(sql);
			mStatements.add(mExistsStmt);
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
		
		db.beginTransaction();
		try {
			if (mExistsStmt.simpleQueryForLong() == 0) {
				rows = db.insert(TBL_IMAGE.name, null, cv);
			} else {
				rows = db.update(TBL_IMAGE.name, cv, DOM_FILENAME.name + " = ?", new String[] {filename});
			}
			if (rows == 0)
				throw new RuntimeException("Failed to insert data");
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	/**
	 * Erase all images in the covers cache
	 */
	private SQLiteStatement mEraseCoverCacheStmt = null;
	public void eraseCoverCache() {
		SQLiteDatabase db = this.getWritableDatabase();

		if (mEraseCoverCacheStmt == null) {
			String sql = "Delete From " + TBL_IMAGE;
			mEraseCoverCacheStmt = db.compileStatement(sql);
			mStatements.add(mEraseCoverCacheStmt);
		}
		mEraseCoverCacheStmt.execute();
	}

	/**
	 * Analyze the database
	 */
	public void analyze() {
		SQLiteDatabase db = this.getWritableDatabase();
		String sql;
		// Don't do VACUUM -- it's a complete rebuild
		//sql = "vacuum";
		//db.execSQL(sql);
		sql = "analyze";
		db.execSQL(sql);
	}

	@Override
	public void close() {
		for(SQLiteStatement s  : mStatements) {
			try { s.close(); } catch (Exception e) {};
		}
		mStatements.clear();
		super.close();
	}
	
}
