package com.eleybourn.bookcatalogue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Class to detect if collation implementations are case sensitive.
 * This was built because ICS broke the UNICODE collation (making it CS) and we needed
 * to check for collation case-sensitivity.
 * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
 * Now the code has been generalized to allow for arbitrary changes to choice of collation.
 * 
 * @author Philip Warner
 */
public class CollationCaseSensitive {
	public static boolean isCaseSensitive(SQLiteDatabase db) {
		Cursor c = null;

		// Drop and create table
		db.execSQL("Drop Table If Exists collation_cs_check");
		db.execSQL("Create Table collation_cs_check (t text, i int)");		
		try {
			// Row that *should* be returned first assuming 'a' <=> 'A' 
			db.execSQL("insert into collation_cs_check values ('a', 1)");		
			// Row that *should* be returned second assuming 'a' <=> 'A'; will be returned first if 'A' < 'a'.
			db.execSQL("insert into collation_cs_check values ('A', 2)");
			c = db.rawQuery("Select t, i from collation_cs_check order by t " + CatalogueDBAdapter.COLLATION + ", i", new String[] {});
			c.moveToFirst();
			String s = c.getString(0);
			return !s.equals("a");
		} finally {
			// Cleanup
			try { 
				if (c != null)
					c.close();
			} catch (Exception ignored) {}
			try { db.execSQL("Drop Table If Exists collation_cs_check"); } catch (Exception ignored) {}
		}
	}
}
