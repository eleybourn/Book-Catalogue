package com.eleybourn.bookcatalogue;

import java.io.ByteArrayOutputStream;

import com.eleybourn.bookcatalogue.DbUtils.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
	/** DB location */
	private static final String COVERS_DATABASE_NAME = Utils.EXTERNAL_FILE_PATH + "/covers.db";
	/** DB Version */
	private static final int COVERS_DATABASE_VERSION = 1;

	// Domain and table definitions
	
	public static final DomainDefinition DOM_ID = new DomainDefinition( "_id", "integer primary key autoincrement");
	public static final DomainDefinition DOM_TYPE = new DomainDefinition( "type", "text not null");	// T = Thumbnail; C = cover?
	public static final DomainDefinition DOM_IMAGE = new DomainDefinition( "image", "blob not null");
	public static final DomainDefinition DOM_WIDTH = new DomainDefinition( "width", "integer not null");
	public static final DomainDefinition DOM_HEIGHT = new DomainDefinition( "height", "integer not null");
	public static final DomainDefinition DOM_SIZE = new DomainDefinition( "size", "integer not null");
	public static final DomainDefinition DOM_FILENAME = new DomainDefinition( "filename", "text");
	public static final TableDefinition TBL_IMAGE = new TableDefinition("image", DOM_ID, DOM_TYPE, DOM_IMAGE, DOM_WIDTH, DOM_HEIGHT, DOM_SIZE, DOM_FILENAME);

	public static final IndexDefinition INDEXES[] = new IndexDefinition[] { 
														new IndexDefinition(true, TBL_IMAGE, DOM_ID),
														new IndexDefinition(true, TBL_IMAGE, DOM_FILENAME),
													};

	public static final TableDefinition TABLES[] = new TableDefinition[] {TBL_IMAGE};

	/**
	 * Constructor. Fill in required fields. This is NOT based on SQLiteOpenHelper so does not need a context.
	 */
	public CoversDbHelper() {
		super(COVERS_DATABASE_NAME, null, COVERS_DATABASE_VERSION);
	}
	/**
	 * As with SQLiteOpenHelper, routine called to create DB
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		DbUtils.createDatabase(db, TABLES, INDEXES);
	}
	/**
	 * As with SQLiteOpenHelper, routine called to upgrade DB
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		throw new RuntimeException("Upgrades not handled yet!");
	}

	/**
	 * Get the named 'file'
	 * 
	 * @param filename
	 * 
	 * @return	byet[] of image data
	 */
	public byte[] getFile(String filename) {
		String sql = "select " + DOM_IMAGE.name + " from " + TBL_IMAGE.name + " Where " + DOM_FILENAME.name + " = ?";
		SQLiteDatabase db = this.getWritableDatabase();
		Cursor c = db.query(TBL_IMAGE.name, new String[]{DOM_IMAGE.name}, DOM_FILENAME.name + "=?", new String[]{filename}, null, null, null);
		try {
			if (!c.moveToFirst())
				return null;		
			return c.getBlob(0);
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
	public void saveFile(String filename, Bitmap bm) {
		ContentValues cv = new ContentValues();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bm.compress(Bitmap.CompressFormat.JPEG, 70, out);
		byte[] bytes = out.toByteArray();

		cv.put(DOM_FILENAME.name, filename);
		cv.put(DOM_IMAGE.name, bytes);
		
		cv.put(DOM_TYPE.name, "T");
		cv.put(DOM_WIDTH.name, bm.getWidth());
		cv.put(DOM_HEIGHT.name, bm.getHeight());
		cv.put(DOM_SIZE.name, bytes.length);

		SQLiteDatabase db = this.getWritableDatabase();

		if (db.update(TBL_IMAGE.name, cv, DOM_FILENAME.name + " = ?", new String[] {filename}) == 0) {
			if (db.insert(TBL_IMAGE.name, null, cv) == 0)
				throw new RuntimeException("Failed to insert data");
		}
	}
	
}
