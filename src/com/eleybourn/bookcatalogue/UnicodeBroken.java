package com.eleybourn.bookcatalogue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Class to detect broken implementations of UNICODE collation 
 * in SQLite; (ie. case sensitive).
 * 
 * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
 * 
 * @author Philip Warner
 */
public class UnicodeBroken {
	public static boolean isCaseSensitive(SQLiteDatabase db) {
		Cursor c = null;

		// Drop and create table
		db.execSQL("Drop Table If Exists unicode_broken");
		db.execSQL("Create Table unicode_broken (t text, i int)");		
		try {
			// Row that *should* be returned first assuming 'a' <=> 'A' 
			db.execSQL("insert into unicode_broken values ('a', 1)");		
			// Row that *should* be returned second assuming 'a' <=> 'A'; will be returned first if 'A' < 'a'.
			db.execSQL("insert into unicode_broken values ('A', 2)");
			c = db.rawQuery("Select t, i from unicode_broken order by t collate UNICODE, i", new String[] {});
			c.moveToFirst();
			String s = c.getString(0);
			return !s.equals("a");
		} finally {
			// Cleanup
			try { 
				if (c != null)
					c.close();
			} catch (Exception e) {}
			try { db.execSQL("Drop Table If Exists unicode_broken"); } catch (Exception e) {}
		}
	}
}
