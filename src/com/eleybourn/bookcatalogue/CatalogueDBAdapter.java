/*
* @copyright 2010 Evan Leybourn
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
package com.eleybourn.bookcatalogue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.widget.ImageView;

/**
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the 
 * ability to list all books as well as retrieve or modify a specific book.
 */
public class CatalogueDBAdapter {
	
	private ArrayList<SQLiteStatement> mStatements = new ArrayList<SQLiteStatement>();
	
	/* This is the list of all column names as static variables for reference
	 * 
	 * NOTE!!! Because Java String comparisons are not case-insensitive, it is 
	 * important that ALL these fields be listed in LOWER CASE.
	 */
	public static final String KEY_AUTHOR_OLD = "author";
	public static final String KEY_AUTHOR_ID = "author";
	public static final String KEY_AUTHOR_NAME = "author_name";
	public static final String KEY_AUTHOR_POSITION = "author_position";
	public static final String KEY_TITLE = "title";
	public static final String KEY_THUMBNAIL = "thumbnail";
	public static final String KEY_ISBN = "isbn";
	public static final String KEY_PUBLISHER = "publisher";
	public static final String KEY_DATE_PUBLISHED = "date_published";
	public static final String KEY_RATING = "rating";
	public static final String KEY_BOOKSHELF = "bookshelf";
	public static final String KEY_READ = "read";
	public static final String KEY_SERIES_ID = "series_id";
	public static final String KEY_SERIES_NAME = "series_name";
	public static final String KEY_SERIES_OLD = "series";
	public static final String KEY_PAGES = "pages";
	public static final String KEY_ROWID = "_id";
	public static final String KEY_AUTHOR_DETAILS = "author_details";
	public static final String KEY_AUTHOR_ARRAY = "author_array";
	public static final String KEY_FAMILY_NAME = "family_name";
	public static final String KEY_GIVEN_NAMES = "given_names";
	public static final String KEY_SERIES_DETAILS = "series_details";
	public static final String KEY_SERIES_ARRAY = "series_array";
	public static final String KEY_SERIES_NUM = "series_num";
	public static final String KEY_SERIES_POSITION = "series_position";
	public static final String KEY_NOTES = "notes";
	public static final String KEY_BOOK = "book";
	public static final String KEY_LOANED_TO = "loaned_to";
	public static final String KEY_LIST_PRICE = "list_price";
	public static final String KEY_POSITION = "position";
	public static final String KEY_ANTHOLOGY = "anthology";
	public static final String KEY_LOCATION = "location";
	public static final String KEY_READ_START = "read_start";
	public static final String KEY_READ_END = "read_end";
	public static final String KEY_FORMAT = "format";
	public static final String OLD_KEY_AUDIOBOOK = "audiobook";
	public static final String KEY_SIGNED = "signed";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_GENRE = "genre";
	
	public static final String KEY_AUTHOR_FORMATTED = "author_formatted";
	public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";
	public static final String KEY_SERIES_FORMATTED = "series_formatted";
	public static final String KEY_SERIES_NUM_FORMATTED = "series_num_formatted";
	
	// We tried 'Collate UNICODE' but it seemed to be case sensitive. We ended
	// up with 'Ursula Le Guin' and 'Ursula le Guin'.
	//public static final String COLLATION = "Collate NOCASE";
	public static final String COLLATION = " Collate UNICODE ";

	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	/* private database variables as static reference */
	private static final String DB_TB_BOOKS = "books";
	private static final String DB_TB_BOOK_AUTHOR = "book_author";
	private static final String DB_TB_BOOK_BOOKSHELF_WEAK = "book_bookshelf_weak";
	private static final String DB_TB_BOOK_SERIES = "book_series";
	private static final String DB_TB_ANTHOLOGY = "anthology";
	private static final String DB_TB_AUTHORS = "authors";
	private static final String DB_TB_BOOKSHELF = "bookshelf";
	private static final String DB_TB_LOAN = "loan";
	private static final String DB_TB_SERIES = "series";
	public static String message = "";
	public static String do_action = "";
	public static String DO_UPDATE_FIELDS = "do_update_fields";
	
	public static final int ANTHOLOGY_NO = 0;
	public static final int ANTHOLOGY_SAME_AUTHOR = 1;
	public static final int ANTHOLOGY_MULTIPLE_AUTHORS = 2;
	
	public static final String META_EMPTY_SERIES = "<Empty Series>";
	public static final String META_EMPTY_GENRE = "<Empty Genre>";
	
	/* Database creation sql statement */
	private static final String DATABASE_CREATE_AUTHORS =
		"create table " + DB_TB_AUTHORS + 
		" (_id integer primary key autoincrement, " +
		KEY_FAMILY_NAME + " text not null, " +
		KEY_GIVEN_NAMES + " text not null" +
		")";
	
	private static final String DATABASE_CREATE_BOOKSHELF =
		"create table " + DB_TB_BOOKSHELF + 
		" (_id integer primary key autoincrement, " +
		KEY_BOOKSHELF + " text not null " +
		")";
	
	private static final String DATABASE_CREATE_BOOKSHELF_DATA =
		"INSERT INTO " + DB_TB_BOOKSHELF + 
		" (" + KEY_BOOKSHELF + ") VALUES ('Default')";
	
	private static final String DATABASE_CREATE_BOOKS =
		"create table " + DB_TB_BOOKS + 
		" (_id integer primary key autoincrement, " +
		/* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
		KEY_TITLE + " text not null, " +
		KEY_ISBN + " text, " +
		KEY_PUBLISHER + " text, " +
		KEY_DATE_PUBLISHED + " date, " +
		KEY_RATING + " float not null default 0, " +
		KEY_READ + " boolean not null default 'f', " +
		/* KEY_SERIES + " text, " + */
		KEY_PAGES + " int, " +
		/* KEY_SERIES_NUM + " text, " + */
		KEY_NOTES + " text, " +
		KEY_LIST_PRICE + " text, " +
		KEY_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO + ", " + 
		KEY_LOCATION + " text, " +
		KEY_READ_START + " date, " +
		KEY_READ_END + " date, " +
		KEY_FORMAT + " text, " +
		KEY_SIGNED + " boolean not null default 'f', " +
		KEY_DESCRIPTION + " text, " +
		KEY_GENRE + " text " +
		")";

	private static final String KEY_AUDIOBOOK_V41 = "audiobook";
	private static final String DATABASE_CREATE_BOOKS_V41 =
		"create table " + DB_TB_BOOKS + 
		" (_id integer primary key autoincrement, " +
		KEY_AUTHOR_ID + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + 
		KEY_TITLE + " text not null, " +
		KEY_ISBN + " text, " +
		KEY_PUBLISHER + " text, " +
		KEY_DATE_PUBLISHED + " date, " +
		KEY_RATING + " float not null default 0, " +
		KEY_READ + " boolean not null default 'f', " +
		KEY_SERIES_OLD + " text, " +
		KEY_PAGES + " int, " +
		KEY_SERIES_NUM + " text, " +
		KEY_NOTES + " text, " +
		KEY_LIST_PRICE + " text, " +
		KEY_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO + ", " + 
		KEY_LOCATION + " text, " +
		KEY_READ_START + " date, " +
		KEY_READ_END + " date, " +
		KEY_AUDIOBOOK_V41 + " boolean not null default 'f', " +
		KEY_SIGNED + " boolean not null default 'f' " +
		")";

	private static final String DATABASE_CREATE_LOAN =
		"create table " + DB_TB_LOAN + 
		" (_id integer primary key autoincrement, " +
		KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
		KEY_LOANED_TO + " text " +
		")";
	
	private static final String DATABASE_CREATE_ANTHOLOGY =
		"create table " + DB_TB_ANTHOLOGY + 
		" (_id integer primary key autoincrement, " +
		KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
		KEY_AUTHOR_ID + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + 
		KEY_TITLE + " text not null, " +
		KEY_POSITION + " int" +
		")";
	
	private static final String DATABASE_CREATE_BOOK_BOOKSHELF_WEAK = 
		"create table " + DB_TB_BOOK_BOOKSHELF_WEAK + "(" + 
		KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
		KEY_BOOKSHELF + " integer REFERENCES " + DB_TB_BOOKSHELF + " ON DELETE SET NULL ON UPDATE SET NULL" +
		")";
	
	private static final String DATABASE_CREATE_SERIES =
		"create table " + DB_TB_SERIES + 
		" (_id integer primary key autoincrement, " +
		KEY_SERIES_NAME + " text not null " +
		")";
	
	private static final String DATABASE_CREATE_BOOK_SERIES = 
		"create table " + DB_TB_BOOK_SERIES + "(" + 
		KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
		KEY_SERIES_ID + " integer REFERENCES " + DB_TB_SERIES + " ON DELETE SET NULL ON UPDATE CASCADE, " +
		KEY_SERIES_NUM + " integer, " + 
		KEY_SERIES_POSITION + " integer," + 
		"PRIMARY KEY(" + KEY_BOOK + ", "  + KEY_SERIES_POSITION + ")" +
		")";
	
	private static final String DATABASE_CREATE_BOOK_AUTHOR = 
		"create table " + DB_TB_BOOK_AUTHOR + "(" + 
		KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
		KEY_AUTHOR_ID + " integer REFERENCES " + DB_TB_AUTHORS + " ON DELETE SET NULL ON UPDATE CASCADE, " +
		KEY_AUTHOR_POSITION + " integer NOT NULL, " + 
		"PRIMARY KEY(" + KEY_BOOK + ", "  + KEY_AUTHOR_POSITION + ")" +
		")";
	
	private static final String[] DATABASE_CREATE_INDICES = {
		"CREATE INDEX IF NOT EXISTS authors_given_names ON "+DB_TB_AUTHORS+" ("+KEY_GIVEN_NAMES+");", 
		"CREATE INDEX IF NOT EXISTS authors_given_names_ci ON "+DB_TB_AUTHORS+" ("+KEY_GIVEN_NAMES+" " + COLLATION + ");", 
		"CREATE INDEX IF NOT EXISTS authors_family_name ON "+DB_TB_AUTHORS+" ("+KEY_FAMILY_NAME+");",
		"CREATE INDEX IF NOT EXISTS authors_family_name_ci ON "+DB_TB_AUTHORS+" ("+KEY_FAMILY_NAME+" " + COLLATION + ");", 
		"CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON "+DB_TB_BOOKSHELF+" ("+KEY_BOOKSHELF+");",
		/* "CREATE INDEX IF NOT EXISTS books_author ON "+DB_TB_BOOKS+" ("+KEY_AUTHOR+");",*/
		/*"CREATE INDEX IF NOT EXISTS books_author_ci ON "+DB_TB_BOOKS+" ("+KEY_AUTHOR+" " + COLLATION + ");",*/
		"CREATE INDEX IF NOT EXISTS books_title ON "+DB_TB_BOOKS+" ("+KEY_TITLE+");",
		"CREATE INDEX IF NOT EXISTS books_title_ci ON "+DB_TB_BOOKS+" ("+KEY_TITLE+" " + COLLATION + ");",
		"CREATE INDEX IF NOT EXISTS books_isbn ON "+DB_TB_BOOKS+" ("+KEY_ISBN+");",
		/* "CREATE INDEX IF NOT EXISTS books_series ON "+DB_TB_BOOKS+" ("+KEY_SERIES+");",*/
		"CREATE INDEX IF NOT EXISTS books_publisher ON "+DB_TB_BOOKS+" ("+KEY_PUBLISHER+");",
		"CREATE INDEX IF NOT EXISTS anthology_book ON "+DB_TB_ANTHOLOGY+" ("+KEY_BOOK+");",
		"CREATE INDEX IF NOT EXISTS anthology_author ON "+DB_TB_ANTHOLOGY+" ("+KEY_AUTHOR_ID+");",
		"CREATE INDEX IF NOT EXISTS anthology_title ON "+DB_TB_ANTHOLOGY+" ("+KEY_TITLE+");",
		"CREATE UNIQUE INDEX IF NOT EXISTS series_series ON "+DB_TB_SERIES+" ("+KEY_ROWID+");",
		"CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON "+DB_TB_LOAN+" ("+KEY_BOOK+");",
		"CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON "+DB_TB_BOOK_BOOKSHELF_WEAK+" ("+KEY_BOOK+");",
		"CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON "+DB_TB_BOOK_BOOKSHELF_WEAK+" ("+KEY_BOOKSHELF+");",
		"CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON "+DB_TB_BOOK_SERIES+" ("+KEY_SERIES_ID+", " + KEY_BOOK + ", " + KEY_SERIES_NUM + ");",
		"CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON "+DB_TB_BOOK_SERIES+" ("+KEY_BOOK+", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ");",
		"CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON "+DB_TB_BOOK_AUTHOR+" ("+KEY_AUTHOR_ID+", " + KEY_BOOK + ");",
		"CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON "+DB_TB_BOOK_AUTHOR+" ("+KEY_BOOK+", " + KEY_AUTHOR_ID + ");",
		"CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_TITLE + ")"
		};
	
//	private static String AUTHOR_FIELDS = "a." + KEY_ROWID + " as " + KEY_AUTHOR_NAME + ", a." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME 
//						+ ", a." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES 
//						+ ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR_FORMATTED;

		private static String getAuthorFields(String alias, String idName) {
			String sql;
			if (idName != null && idName.length() > 0) {
				sql = " " + alias + "." + KEY_ROWID + " as " + idName + ", ";
			} else {
				sql = " ";
			}
			sql += alias + "." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME 
				+ ", " + alias + "." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES 
				+ ",  Case When " + alias + "." + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME 
				+ "        Else " + alias + "." + KEY_FAMILY_NAME + " || ', ' || " 
						+ alias + "." + KEY_GIVEN_NAMES + " End as " + KEY_AUTHOR_FORMATTED 
				+ ",  Case When " + alias + "." + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME 
				+ "        Else " + alias + "." + KEY_GIVEN_NAMES + " || ' ' || " 
						+ alias + "." + KEY_FAMILY_NAME + " End as " + KEY_AUTHOR_FORMATTED_GIVEN_FIRST + " ";
			return sql;
		}

		//		private static String BOOK_FIELDS = 
//		"b." + KEY_TITLE + " as " + KEY_TITLE + ", " +
//		"b." + KEY_ISBN + " as " + KEY_ISBN + ", " +
//		"b." + KEY_PUBLISHER + " as " + KEY_PUBLISHER + ", " +
//		"b." + KEY_DATE_PUBLISHED + " as " + KEY_DATE_PUBLISHED + ", " +
//		"b." + KEY_RATING + " as " + KEY_RATING + ", " +
//		"b." + KEY_READ + " as " + KEY_READ + ", " +
//		"b." + KEY_PAGES + " as " + KEY_PAGES + ", " +
//		"b." + KEY_NOTES + " as " + KEY_NOTES + ", " +
//		"b." + KEY_LIST_PRICE + " as " + KEY_LIST_PRICE + ", " +
//		"b." + KEY_ANTHOLOGY + " as " + KEY_ANTHOLOGY + ", " +
//		"b." + KEY_LOCATION + " as " + KEY_LOCATION + ", " +
//		"b." + KEY_READ_START + " as " + KEY_READ_START + ", " +
//		"b." + KEY_READ_END + " as " + KEY_READ_END + ", " +
//		"b." + KEY_FORMAT + " as " + KEY_FORMAT + ", " +
//		"b." + KEY_SIGNED + " as " + KEY_SIGNED + ", " + 
//		"b." + KEY_DESCRIPTION + " as " + KEY_DESCRIPTION + ", " + 
//		"b." + KEY_GENRE  + " as " + KEY_GENRE;

		private static String getBookFields(String alias, String idName) {
			String sql;
			if (idName != null && idName.length() > 0) {
				sql = alias + "." + KEY_ROWID + " as " + idName + ", ";
			} else {
				sql = "";
			}
			return sql + alias + "." + KEY_TITLE + " as " + KEY_TITLE + ", " +

			// Find FIRST series ID. 
			"(Select " + KEY_SERIES_ID + " From " + DB_TB_BOOK_SERIES + " bs " +
			" where bs." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + " Order by " + KEY_SERIES_POSITION + " asc  Limit 1) as " + KEY_SERIES_ID + ", " +
			// Find FIRST series NUM. 
			"(Select " + KEY_SERIES_NUM + " From " + DB_TB_BOOK_SERIES + " bs " +
			" where bs." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + " Order by " + KEY_SERIES_POSITION + " asc  Limit 1) as " + KEY_SERIES_NUM + ", " +
			// Get the total series count
			"(Select Count(*) from " + DB_TB_BOOK_SERIES + " bs Where bs." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + ") as _num_series," + 
			// Find the first AUTHOR ID
			"(Select " + KEY_AUTHOR_ID + " From " + DB_TB_BOOK_AUTHOR + " ba " + 
			"   where ba." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + 
			"   order by " + KEY_AUTHOR_POSITION + ", ba." + KEY_AUTHOR_ID + " Limit 1) as " + KEY_AUTHOR_ID + ", " +
			// Get the total author count
			"(Select Count(*) from " + DB_TB_BOOK_AUTHOR + " ba Where ba." + KEY_BOOK + " = " + alias + "." + KEY_ROWID + ") as _num_authors," + 

			alias + "." + KEY_ISBN + " as " + KEY_ISBN + ", " +
			alias + "." + KEY_PUBLISHER + " as " + KEY_PUBLISHER + ", " +
			alias + "." + KEY_DATE_PUBLISHED + " as " + KEY_DATE_PUBLISHED + ", " +
			alias + "." + KEY_RATING + " as " + KEY_RATING + ", " +
			alias + "." + KEY_READ + " as " + KEY_READ + ", " +
			alias + "." + KEY_PAGES + " as " + KEY_PAGES + ", " +
			alias + "." + KEY_NOTES + " as " + KEY_NOTES + ", " +
			alias + "." + KEY_LIST_PRICE + " as " + KEY_LIST_PRICE + ", " +
			alias + "." + KEY_ANTHOLOGY + " as " + KEY_ANTHOLOGY + ", " +
			alias + "." + KEY_LOCATION + " as " + KEY_LOCATION + ", " +
			alias + "." + KEY_READ_START + " as " + KEY_READ_START + ", " +
			alias + "." + KEY_READ_END + " as " + KEY_READ_END + ", " +
			alias + "." + KEY_FORMAT + " as " + KEY_FORMAT + ", " +
			alias + "." + KEY_SIGNED + " as " + KEY_SIGNED + ", " + 
			alias + "." + KEY_DESCRIPTION + " as " + KEY_DESCRIPTION + ", " + 
			alias + "." + KEY_GENRE  + " as " + KEY_GENRE;
		}

//	private static String SERIES_FIELDS = "s." + KEY_ROWID + " as " + KEY_SERIES_ID
//		+ " CASE WHEN s." + KEY_SERIES_NAME + "='' THEN '' ELSE s." + KEY_SERIES_NAME + " || CASE WHEN s." + KEY_SERIES_NUM + "='' THEN '' ELSE ' #' || s." + KEY_SERIES_NUM + " END END AS " + KEY_SERIES_FORMATTED;
//
//	private static String BOOKSHELF_TABLES = DB_TB_BOOKS + " b LEFT OUTER JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " w ON (b." + KEY_ROWID + "=w." + KEY_BOOK + ") LEFT OUTER JOIN " + DB_TB_BOOKSHELF + " bs ON (bs." + KEY_ROWID + "=w." + KEY_BOOKSHELF + ") ";
//	private static String SERIES_TABLES = DB_TB_BOOKS 
//						+ " b LEFT OUTER JOIN " + DB_TB_BOOK_SERIES + " w "
//						+ " ON (b." + KEY_ROWID + "=w." + KEY_BOOK + ") "
//						+ " LEFT OUTER JOIN " + DB_TB_SERIES + " s ON (s." + KEY_ROWID + "=w." + KEY_SERIES_ID + ") ";
		
	private final Context mCtx;
	//TODO: Update database version
	public static final int DATABASE_VERSION = 59;

	private TableInfo mBooksInfo = null;

	/**
	 * This is a specific version of the SQLiteOpenHelper class. It handles onCreate and onUpgrade events
	 * 
	 * @author evan
	 */
	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, Utils.DATABASE_NAME, null, DATABASE_VERSION);
		}
		
		/**
		 * This function is called when the database is first created
		 * 
		 * @param db The database to be created
		 */
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE_AUTHORS);
			db.execSQL(DATABASE_CREATE_BOOKSHELF);
			db.execSQL(DATABASE_CREATE_BOOKS);
			db.execSQL(DATABASE_CREATE_BOOKSHELF_DATA);
			db.execSQL(DATABASE_CREATE_LOAN);
			db.execSQL(DATABASE_CREATE_ANTHOLOGY);
			db.execSQL(DATABASE_CREATE_SERIES);
			db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
			db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
			db.execSQL(DATABASE_CREATE_BOOK_SERIES);
			createIndices(db);

			new File(Utils.EXTERNAL_FILE_PATH + "/").mkdirs();
			try {
				new File(Utils.EXTERNAL_FILE_PATH + "/.nomedia").createNewFile();
			} catch (IOException e) {
				Logger.logError(e);
			}
		}

		private void createIndices(SQLiteDatabase db) {
			//delete all indices first
			String sql = "select name from sqlite_master where type = 'index' and sql is not null;";
			Cursor current = db.rawQuery(sql, new String[]{});
			while (current.moveToNext()) {
				String index_name = current.getString(0);
				String delete_sql = "DROP INDEX " + index_name;
				//db.beginTransaction();
				try {
					db.execSQL(delete_sql);
					//db.setTransactionSuccessful();
				} catch (Exception e) {
					Logger.logError(e, "Index deletion failed (probably not a problem)");
				} finally {
					//db.endTransaction();
				}
			}
			
			String[] indices = DATABASE_CREATE_INDICES;
			for (int i = 0; i < indices.length; i++) {
				// Avoid index creation killing an upgrade because this 
				// method may be called by any upgrade script and some tables
				// may not yet exist. We probably still want indexes.
				// 
				// Ideally, whenever an upgrade script is written, the 'createIndices()' call
				// from prior upgrades should be removed saved and used and a new version of this
				// script written for the new DB.
				//db.beginTransaction();
				try {
					db.execSQL(indices[i]);	
					//db.setTransactionSuccessful();
				} catch (Exception e) {
					// Expected on multi-version upgrades.
					Logger.logError(e, "Index creation failed (probably not a problem), definition was: " + indices[i]);
				} finally {
					//db.endTransaction();
				}
			}			
		}
		
		/**
		 * This function is called each time the database is upgraded. The function will run all 
		 * upgrade scripts between the oldVersion and the newVersion. 
		 * 
		 * @see DATABASE_VERSION
		 * @param db The database to be upgraded
		 * @param oldVersion The current version number of the database
		 * @param newVersion The new version number of the database
		 */
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			int curVersion = oldVersion;
			
			if (oldVersion != newVersion)
				backupDbFile(db, "dbUpgrade-" + oldVersion + "-" + newVersion);
			
			if (curVersion < 11) {
				onCreate(db);
			}
			if (curVersion == 11) {
				db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_SERIES_NUM + " text");
				db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_SERIES_NUM + " = ''");
				curVersion++;
			}
			if (curVersion == 12) {
				//do nothing except increment
				curVersion++;
			}
			if (curVersion == 13) {
				//do nothing except increment
				curVersion++;
			}
			if (curVersion == 14) {
				//do nothing except increment
				curVersion++;
			}
			if (curVersion == 15) {
				//do nothing except increment
				curVersion++;
			}
			if (curVersion == 16) {
				//do nothing except increment
				curVersion++;
				message += "* This message will now appear whenever you upgrade\n\n";
				message += "* Various SQL bugs have been resolved\n\n";
			}
			if (curVersion == 17) {
				//do nothing except increment
				curVersion++;
			}
			if (curVersion == 18) {
				//do nothing except increment
				curVersion++;
			}
			if (curVersion == 19) {
				curVersion++;
				db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_NOTES + " text");
				db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_NOTES + " = ''");
			}
			if (curVersion == 20) {
				curVersion++;
				db.execSQL(DATABASE_CREATE_LOAN);
				//createIndices(db); // All createIndices prior to the latest have been removed
			}
			if (curVersion == 21) {
				//do nothing
				curVersion++;
			}
			if (curVersion == 22) {
				curVersion++;
				message += "* There is a new tabbed 'edit' interface to simplify editing books.\n\n"; 
				message += "* The new comments tab also includes a notes field where you can add personal notes for any book (Requested by Luke).\n\n";
				message += "* The new loaned books tab allows you to record books loaned to friends. This will lookup your phone contacts to pre-populate the list (Requested by Luke)\n\n";
				message += "* Scanned books that already exist in the database (based on ISBN) will no longer be added (Identified by Colin)\n\n";
				message += "* After adding a book, the main view will now scroll to a appropriate location. \n\n";
				message += "* Searching has been made significantly faster.\n\n";
			}
			if (curVersion == 23) {
				//do nothing
				curVersion++;
			}
			if (curVersion == 24) {
				curVersion++;
				try {
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_NOTES + " text");
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				try {
					db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_NOTES + " = ''");
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				try {
					db.execSQL(DATABASE_CREATE_LOAN);
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				try {
					//createIndices(db); // All createIndices prior to the latest have been removed
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
			}
			if (curVersion == 25) {
				//do nothing
				curVersion++;
				message += "* Your sort order will be automatically saved when to close the application (Requested by Martin)\n\n";
				message += "* There is a new 'about this app' view available from the administration tabs (Also from Martin)\n\n"; 
			}
			if (curVersion == 26) {
				//do nothing
				curVersion++;
				message += "* There are two additional sort functions, by series and by loaned (Request from N4ppy)\n\n";
				message += "* Your bookshelf and current location will be saved when you exit (Feedback from Martin)\n\n"; 
				message += "* Minor interface improvements when sorting by title \n\n"; 
			}
			if (curVersion == 27) {
				//do nothing
				curVersion++;
				message += "* The book thumbnail now appears in the list view\n\n";
				message += "* Emailing the developer now works from the admin page\n\n";
				message += "* The Change Bookshelf option is now more obvious (Thanks Mike)\n\n";
				message += "* The exports have been renamed to csv, use the correct published date and are now unicode safe (Thanks Mike)\n\n";
			}
			if (curVersion == 28) {
				curVersion++;
				try {
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_LIST_PRICE + " text");
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
			}
			if (curVersion == 29) {
				//do nothing
				curVersion++;
				message += "* Adding books will now (finally) search Amazon\n\n";
				message += "* A field for list price has been included (Requested by Brenda)\n\n";
				message += "* You can bulk update the thumbnails for all books with ISBN's from the Admin page\n\n";
			}
			if (curVersion == 30) {
				//do nothing
				curVersion++;
				message += "* You can now delete individual thumbnails by holding on the image and selecting delete.\n\n";
			}
			if (curVersion == 31) {
				//do nothing
				curVersion++;
				message += "* There is a new Admin option (Field Visibility) to hide unused fields\n\n";
				message += "* 'All Books' should now be saved as a bookshelf preference correctly\n\n";
				message += "* When adding books the bookshelf will default to your currently selected bookshelf (Thanks Martin)\n\n";
			}
			if (curVersion == 32) {
				//do nothing
				curVersion++;
				try {
					db.execSQL(DATABASE_CREATE_ANTHOLOGY);
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				try {
					//createIndices(db); // All createIndices prior to the latest have been removed
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				try {
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO);
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				message += "* There is now support to record books as anthologies and it's titles. \n\n";
				message += "* There is experimental support to automatically populate the anthology titles \n\n";
				message += "* You can now take photos for the book cover (long click on the thumbnail in edit) \n\n";
			}
			if (curVersion == 33) {
				//do nothing
				curVersion++;
				message += "* Minor enhancements\n\n";
				message += "* Online help has been written\n\n";
				message += "* Thumbnails can now be hidden just like any other field (Thanks Martin)\n\n";
				message += "* You can also rotate thumbnails; useful for thumbnails taken with the camera\n\n";
				message += "* Bookshelves will appear in the menu immediately (Thanks Martin/Julia)\n\n";
			}
			if (curVersion == 34) {
				curVersion++;
				try {
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_LOCATION + " text");
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_READ_START + " date");
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_READ_END + " date");
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + OLD_KEY_AUDIOBOOK + " boolean not null default 'f'");
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_SIGNED + " boolean not null default 'f'");
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
			}
			if (curVersion == 35) {
				curVersion++;
				try {
					db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_LOCATION + "=''");
					db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_READ_START + "=''");
					db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_READ_END + "=''");
					db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + OLD_KEY_AUDIOBOOK + "='f'");
					db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_SIGNED + "='f'");
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
			}
			if (curVersion == 36) {
				//do nothing
				curVersion++;
				message += "* Fixed several crashing defects when adding books\n\n";
				message += "* Added Autocompleting Location Field (For Cam)\n\n";
				message += "* Added Read Start & Read End Fields (For Robert)\n\n";
				message += "* Added an Audiobook Checkbox Field (For Ted)\n\n";
				message += "* Added a Book Signed Checkbox Field (For me)\n\n";
				message += "*** Don't forget you can hide any of the new fields that you do not want to see.\n\n";
				message += "* Series Number now support decimal figures (Requested by Beth)\n\n";
				message += "* List price now support decimal figures (Requested by eleavings)\n\n";
				message += "* Fixed Import Crashes (Thanks Roydalg) \n\n";
				message += "* Fixed several defects for Android 1.6 users - I do not have a 1.6 device to test on so please let me know if you discover any errors\n\n";
			}
			if (curVersion == 37) {
				//do nothing
				curVersion++;
				message += "Tip: If you long click on a book title on the main list you can delete it\n\n";
				message += "Tip: If you want to see all books, change the bookshelf to 'All Books'\n\n";
				message += "Tip: You can find the correct barcode for many modern paperbacks on the inside cover\n\n";
				message += "* There is now a 'Sort by Unread' option, as well as a 'read' icon on the main list (requested by Angel)\n\n";
				message += "* If you long click on the (?) thumbnail you can now select a new thumbnail from the gallery (requested by Giovanni)\n\n";
				message += "* Bookshelves, loaned books and anthology titles will now import correctly\n\n";
			}
			if (curVersion == 38) {
				//do nothing
				curVersion++;
				db.execSQL("DELETE FROM " + DB_TB_LOAN + " WHERE (" + KEY_LOANED_TO + "='' OR " + KEY_LOANED_TO + "='null')");
			}
			if (curVersion == 39) {
				curVersion++;
				try {
					new File(Utils.EXTERNAL_FILE_PATH + "/.nomedia").createNewFile();
				} catch (Exception e) {
					// Don't care about this exception
					Logger.logError(e);
				}
			}
			if (curVersion == 40) {
				curVersion++;
			}
			if (curVersion == 41) {
				//do nothing
				curVersion++;
				message += "Tip: You can find the correct barcode for many modern paperbacks on the inside cover\n\n";
				message += "* Added app2sd support (2.2 users only)\n\n";
				message += "* You can now assign books to multiple bookshelves (requested by many people)\n\n";
				message += "* A .nomedia file will be automatically created which will stop the thumbnails showing up in the gallery (thanks Brandon)\n\n";
				message += "* The 'Add Book by ISBN' page has been redesigned to be simpler and more stable (thanks Vinikia)\n\n";
				message += "* The export file is now formatted correctly (.csv) (thanks glohr)\n\n";
				message += "* You will be prompted to backup your books on a regular basis \n\n";
				
				try {
					db.execSQL("DROP TABLE tmp1");
				} catch (Exception e) {
					// Don't really care about this
				}				
				try {
					db.execSQL("DROP TABLE tmp2");
				} catch (Exception e) {
					// Don't really care about this
				}
				try {
					db.execSQL("DROP TABLE tmp3");
				} catch (Exception e) {
					// Don't really care about this
				}
				
				try {
					db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
					//createIndices(db); // All createIndices prior to the latest have been removed
					db.execSQL("INSERT INTO " + DB_TB_BOOK_BOOKSHELF_WEAK + " (" + KEY_BOOK + ", " + KEY_BOOKSHELF + ") SELECT " + KEY_ROWID + ", " + KEY_BOOKSHELF + " FROM " + DB_TB_BOOKS + "");
					db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_ISBN + ", " + KEY_PUBLISHER + ", " + 
						KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ + ", " + KEY_SERIES_OLD + ", " + KEY_PAGES + ", " + KEY_SERIES_NUM + ", " + KEY_NOTES + ", " + 
						KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY + ", " + KEY_LOCATION + ", " + KEY_READ_START + ", " + KEY_READ_END + ", " + OLD_KEY_AUDIOBOOK + ", " + 
						KEY_SIGNED + " FROM " + DB_TB_BOOKS);
					db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + KEY_BOOK + ", " + KEY_LOANED_TO + " FROM " + DB_TB_LOAN );
					db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + KEY_BOOK + ", " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_POSITION + " FROM " + DB_TB_ANTHOLOGY);
					
					db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
					db.execSQL("DROP TABLE " + DB_TB_LOAN);
					db.execSQL("DROP TABLE " + DB_TB_BOOKS);
					
					db.execSQL(DATABASE_CREATE_BOOKS_V41);
					db.execSQL(DATABASE_CREATE_LOAN);
					db.execSQL(DATABASE_CREATE_ANTHOLOGY);
					
					db.execSQL("INSERT INTO " + DB_TB_BOOKS + " SELECT * FROM tmp1");
					db.execSQL("INSERT INTO " + DB_TB_LOAN + " SELECT * FROM tmp2");
					db.execSQL("INSERT INTO " + DB_TB_ANTHOLOGY + " SELECT * FROM tmp3");
					
					db.execSQL("DROP TABLE tmp1");
					db.execSQL("DROP TABLE tmp2");
					db.execSQL("DROP TABLE tmp3");
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
			}
			if (curVersion == 42) {
				//do nothing
				curVersion++;
				message += "* Export bug fixed\n\n";
				message += "* Further enhancements to the new ISBN screen\n\n";
				message += "* Filtering by bookshelf on title view is now fixed\n\n";
				message += "* There is now an <Empty Series> when sorting by Series (thanks Vinikia)\n\n";
				message += "* App will now search all fields (Thanks Michael)\n\n";
			}
			if (curVersion == 43) {
				curVersion++;
				db.execSQL("DELETE FROM " + DB_TB_ANTHOLOGY + " WHERE " + KEY_ROWID + " IN (" +
						"SELECT a." + KEY_ROWID + " FROM " + DB_TB_ANTHOLOGY + " a, " + DB_TB_ANTHOLOGY + " b " +
						" WHERE a." + KEY_BOOK + "=b." + KEY_BOOK + " AND a." + KEY_AUTHOR_OLD + "=b." + KEY_AUTHOR_OLD + " " +
						" AND a." + KEY_TITLE + "=b." + KEY_TITLE + " AND a." + KEY_ROWID + " > b." + KEY_ROWID + "" +
						")");
				db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + KEY_BOOK + ", " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ")");
			}
			if (curVersion == 44) {
				//do nothing
				curVersion++;
				
				try {
					db.execSQL("DROP TABLE tmp1");
				} catch (Exception e) {
					// Don't care
				}
				try {
					db.execSQL("DROP TABLE tmp2");
				} catch (Exception e) {
					// Don't care
				}
				try {
					db.execSQL("DROP TABLE tmp3");
				} catch (Exception e) {
					// Don't care
				}
				
				db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_ISBN + ", " + KEY_PUBLISHER + ", " + 
						KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ + ", " + KEY_SERIES_OLD + ", " + KEY_PAGES + ", " + KEY_SERIES_NUM + ", " + KEY_NOTES + ", " + 
						KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY + ", " + KEY_LOCATION + ", " + KEY_READ_START + ", " + KEY_READ_END + ", " +
						"CASE WHEN " + OLD_KEY_AUDIOBOOK + "='t' THEN 'Audiobook' ELSE 'Paperback' END AS " + OLD_KEY_AUDIOBOOK + ", " + 
						KEY_SIGNED + " FROM " + DB_TB_BOOKS);
				db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + KEY_BOOK + ", " + KEY_LOANED_TO + " FROM " + DB_TB_LOAN );
				db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + KEY_BOOK + ", " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_POSITION + " FROM " + DB_TB_ANTHOLOGY);
				db.execSQL("CREATE TABLE tmp4 AS SELECT " + KEY_BOOK + ", " + KEY_BOOKSHELF+ " FROM " + DB_TB_BOOK_BOOKSHELF_WEAK);
				
				db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
				db.execSQL("DROP TABLE " + DB_TB_LOAN);
				db.execSQL("DROP TABLE " + DB_TB_BOOKS);
				db.execSQL("DROP TABLE " + DB_TB_BOOK_BOOKSHELF_WEAK);
				
				String TMP_DATABASE_CREATE_BOOKS =
					"create table " + DB_TB_BOOKS + 
					" (_id integer primary key autoincrement, " +
					KEY_AUTHOR_OLD + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + 
					KEY_TITLE + " text not null, " +
					KEY_ISBN + " text, " +
					KEY_PUBLISHER + " text, " +
					KEY_DATE_PUBLISHED + " date, " +
					KEY_RATING + " float not null default 0, " +
					KEY_READ + " boolean not null default 'f', " +
					KEY_SERIES_OLD + " text, " +
					KEY_PAGES + " int, " +
					KEY_SERIES_NUM + " text, " +
					KEY_NOTES + " text, " +
					KEY_LIST_PRICE + " text, " +
					KEY_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO + ", " + 
					KEY_LOCATION + " text, " +
					KEY_READ_START + " date, " +
					KEY_READ_END + " date, " +
					KEY_FORMAT + " text, " +
					KEY_SIGNED + " boolean not null default 'f' " +
					")";

				
				db.execSQL(TMP_DATABASE_CREATE_BOOKS);
				db.execSQL(DATABASE_CREATE_LOAN);
				db.execSQL(DATABASE_CREATE_ANTHOLOGY);
				db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
				
				db.execSQL("INSERT INTO " + DB_TB_BOOKS + " SELECT * FROM tmp1");
				db.execSQL("INSERT INTO " + DB_TB_LOAN + " SELECT * FROM tmp2");
				db.execSQL("INSERT INTO " + DB_TB_ANTHOLOGY + " SELECT * FROM tmp3");
				db.execSQL("INSERT INTO " + DB_TB_BOOK_BOOKSHELF_WEAK + " SELECT * FROM tmp4");
				
				db.execSQL("DROP TABLE tmp1");
				db.execSQL("DROP TABLE tmp2");
				db.execSQL("DROP TABLE tmp3");
				db.execSQL("DROP TABLE tmp4");
				
				//createIndices(db); // All createIndices prior to the latest have been removed
			}
			if (curVersion == 45) {
				//do nothing
				curVersion++;
				db.execSQL("DELETE FROM " + DB_TB_LOAN + " WHERE " + KEY_LOANED_TO + "='null'" );
			}
			if (curVersion == 46) {
				curVersion++;
				db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_DESCRIPTION + " text");
				db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_GENRE + " text");
			}
			if (curVersion == 47) {
				curVersion++;
				do_action = DO_UPDATE_FIELDS;
				message += "New in v3.1\n\n";
				message += "* The audiobook checkbox has been replaced with a format selector (inc. paperback, hardcover, companion etc)\n\n";
				message += "* When adding books the current bookshelf will be selected as the default bookshelf\n\n";
				message += "* Genre/Subject and Description fields have been added (Requested by Tosh) and will automatically populate based on Google Books and Amazon information\n\n";
				message += "* The save button will always be visible on the edit book screen\n\n";
				message += "* Searching for a single space will clear the search results page\n\n";
				message += "* The Date Picker will now appear in a popup in order to save space on the screen (Requested by several people)\n\n";
				message += "* To improve speed when sorting by title, the titles will be broken up by the first character. Remember prefixes such as 'the' and 'a' are listed after the title, e.g. 'The Trigger' becomes 'Trigger, The'\n\n";
			}
			if (curVersion == 48) {
				curVersion++;
				db.execSQL("delete from loan where loaned_to='null';");
				db.execSQL("delete from loan where _id!=(select max(l2._id) from loan l2 where l2.book=loan.book);");
				db.execSQL("delete from anthology where _id!=(select max(a2._id) from anthology a2 where a2.book=anthology.book AND a2.author=anthology.author AND a2.title=anthology.title);");
				//createIndices(db); // All createIndices prior to the latest have been removed
			}
			if (curVersion == 49) {
				curVersion++;
				message += "New in v3.2\n\n";
				message += "* Books can now be automatically added by searching for the author name and book title\n\n";
				message += "* Updating thumbnails, genre and description fields will also search by author name and title is the isbn does not exist\n\n";				message += "* Expand/Collapse all bug fixed\n\n";
				message += "* The search query will be shown at the top of all search screens\n\n";
			}
			if (curVersion == 50) {
				curVersion++;
				//createIndices(db); // All createIndices prior to the latest have been removed
			}
			if (curVersion == 51) {
				curVersion++;
				message += "New in v3.3 - Updates courtesy of Grunthos\n\n";
				message += "* The application should be significantly faster now - Fixed a bug with database index creation\n\n";
				message += "* The thumbnail can be rotated in both directions now\n\n";
				message += "* You can zoom in the thumbnail to see full detail\n\n";
				message += "* The help page will redirect to the, more frequently updated, online wiki\n\n";
				message += "* Dollar signs in the text fields will no longer FC on import/export\n\n";
			}
			if (curVersion == 52) {
				curVersion++;
				message += "New in v3.3.1\n\n";
				message += "* Minor bug fixes and error logging. Please email me if you have any further issues.\n\n";
			}
			if (curVersion == 53) {
				curVersion++;
				//There is a conflict between eleybourn released branch (3.3.1) and grunthos HEAD (3.4). 
				// This is to check and skip as required
				boolean go = false;
				String checkSQL = "SELECT * FROM " + DB_TB_BOOKS;
				Cursor results = db.rawQuery(checkSQL, new String[]{});
				if (results.getCount() > 0) {
					if (results.getColumnIndex(KEY_AUTHOR_OLD) > -1) {
						go = true;
					}
				}
				if (go == true) {
					try {
						message += "New in v3.4 - Updates courtesy of (mainly) Grunthos (blame him, politely, if it toasts your data)\n\n";
						message += "* Multiple Authors per book\n\n";
						message += "* Multiple Series per book\n\n";
						message += "* Fetches series (and other stuff) from LibraryThing\n\n";
						message += "* Can now make global changes to Author and Series (Access this feature via long-click on the catalogue screen)\n\n";
						message += "* Can replace a cover thumbnail from a different edition via LibraryThing.\n\n";
						message += "* Does concurrent ISBN searches at Amazon, Google and LibraryThing (it's faster)\n\n";
						message += "* Displays a progress dialog while searching for a book on the internet.\n\n";
						message += "* Adding by Amazon's ASIN is supported on the 'Add by ISBN' page\n\n";
						message += "* Duplicate books allowed, with a warning message\n\n";
						message += "* User-selectable fields when reloading data from internet (eg. just update authors).\n\n";
						message += "* Unsaved edits are retained when rotating the screen.\n\n";
						message += "* Changed the ISBN data entry screen when the device is in landscape mode.\n\n";
						message += "* Displays square brackets around the series name when displaying a list of books.\n\n";
						message += "* Suggestions available when searching\n\n";
						message += "* Removed the need for contacts permission. Though it does mean you will need to manually enter everyone you want to loan to.\n\n";
						message += "* Preserves *all* open groups when closing application.\n\n";
						message += "* The scanner and book search screens remain active after a book has been added or a search fails. It was viewed that this more closely represents the work-flow of people adding or scanning books.\n\n";
						message += "See the web site (from the Admin menu) for more details\n\n";
						
						db.execSQL(DATABASE_CREATE_SERIES);
						// We need to create a series table with series that are unique wrt case and unicode. The old
						// system allowed for series with slightly different case. So we capture these by using
						// max() to pick and arbitrary matching name to use as our canonical version.
						db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + KEY_SERIES_NAME + ") "
									+ "SELECT name from ("
									+ "    SELECT Upper(" + KEY_SERIES_OLD + ") " + COLLATION + " as ucName, "
									+ "    max(" + KEY_SERIES_OLD + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
									+ "    WHERE Coalesce(" + KEY_SERIES_OLD + ",'') <> ''"
									+ "    Group By Upper(" + KEY_SERIES_OLD + ")"
									+ " )"
									);
						
						db.execSQL(DATABASE_CREATE_BOOK_SERIES);
						db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
						
						//createIndices(db); // All createIndices prior to the latest have been removed
						
						db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + KEY_BOOK + ", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ", " + KEY_SERIES_POSITION + ") "
								+ "SELECT DISTINCT b." + KEY_ROWID + ", s." + KEY_ROWID + ", b." + KEY_SERIES_NUM + ", 1"
								+ " FROM " + DB_TB_BOOKS + " b "
								+ " Join " + DB_TB_SERIES + " s On Upper(s." + KEY_SERIES_NAME + ") = Upper(b." + KEY_SERIES_OLD + ")" + COLLATION
								+ " Where Coalesce(b." + KEY_SERIES_OLD + ", '') <> ''");
						
						db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_AUTHOR_POSITION + ") "
								+ "SELECT b." + KEY_ROWID + ", b." + KEY_AUTHOR_OLD + ", 1 FROM " + DB_TB_BOOKS + " b ");
						
						String tmpFields = KEY_ROWID + ", " /* + KEY_AUTHOR + ", " */ + KEY_TITLE + ", " + KEY_ISBN 
						+ ", " + KEY_PUBLISHER + ", " + KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ 
						+ /* ", " + KEY_SERIES + */ ", " + KEY_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + KEY_NOTES 
						+ ", " + KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY + ", " + KEY_LOCATION + ", " + KEY_READ_START 
						+ ", " + KEY_READ_END + ", " + KEY_FORMAT + ", " + KEY_SIGNED + ", " + KEY_DESCRIPTION
						+ ", " + KEY_GENRE;
						db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields + " FROM " + DB_TB_BOOKS);
						
						db.execSQL("DROP TABLE " + DB_TB_BOOKS);
						
						db.execSQL(DATABASE_CREATE_BOOKS);
						
						db.execSQL("INSERT INTO " + DB_TB_BOOKS + "( " + tmpFields + ")  SELECT * FROM tmpBooks");
						
						db.execSQL("DROP TABLE tmpBooks");
					} catch (Exception e) {
						Logger.logError(e);
						throw new RuntimeException("Failed to upgrade database", e);
					}
				} 
				if (curVersion == 54) {
					//createIndices(db); // All createIndices prior to the latest have been removed
					curVersion++;
				}
				if (curVersion == 55) {
					//There is a conflict between eleybourn released branch (3.3.1) and grunthos HEAD (3.4). 
					// This is to check and skip as required
					boolean go2 = false;
					String checkSQL2 = "SELECT * FROM " + DB_TB_BOOKS;
					Cursor results2 = db.rawQuery(checkSQL2, new String[]{});
					if (results2.getCount() > 0) {
						if (results2.getColumnIndex(KEY_AUTHOR_OLD) > -1) {
							go2 = true;
						}
					} else {
						go2 = true;
					}
					if (go2 == true) {
						try {
							db.execSQL(DATABASE_CREATE_SERIES);
							// We need to create a series table with series that are unique wrt case and unicode. The old
							// system allowed for series with slightly different case. So we capture these by using
							// max() to pick and arbitrary matching name to use as our canonical version.
							db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + KEY_SERIES_NAME + ") "
										+ "SELECT name from ("
										+ "    SELECT Upper(" + KEY_SERIES_OLD + ") " + COLLATION + " as ucName, "
										+ "    max(" + KEY_SERIES_OLD + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
										+ "    WHERE Coalesce(" + KEY_SERIES_OLD + ",'') <> ''"
										+ "    Group By Upper(" + KEY_SERIES_OLD + ")"
										+ " )"
										);
							
							db.execSQL(DATABASE_CREATE_BOOK_SERIES);
							db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
							
							db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + KEY_BOOK + ", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ", " + KEY_SERIES_POSITION + ") "
									+ "SELECT DISTINCT b." + KEY_ROWID + ", s." + KEY_ROWID + ", b." + KEY_SERIES_NUM + ", 1"
									+ " FROM " + DB_TB_BOOKS + " b "
									+ " Join " + DB_TB_SERIES + " s On Upper(s." + KEY_SERIES_NAME + ") = Upper(b." + KEY_SERIES_OLD + ")" + COLLATION
									+ " Where Coalesce(b." + KEY_SERIES_OLD + ", '') <> ''");
							
							db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_AUTHOR_POSITION + ") "
									+ "SELECT b." + KEY_ROWID + ", b." + KEY_AUTHOR_OLD + ", 1 FROM " + DB_TB_BOOKS + " b ");
							
							String tmpFields = KEY_ROWID + ", " /* + KEY_AUTHOR + ", " */ + KEY_TITLE + ", " + KEY_ISBN 
							+ ", " + KEY_PUBLISHER + ", " + KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ 
							+ /* ", " + KEY_SERIES + */ ", " + KEY_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + KEY_NOTES 
							+ ", " + KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY + ", " + KEY_LOCATION + ", " + KEY_READ_START 
							+ ", " + KEY_READ_END + ", " + KEY_FORMAT + ", " + KEY_SIGNED + ", " + KEY_DESCRIPTION
							+ ", " + KEY_GENRE;
							db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields + " FROM " + DB_TB_BOOKS);
							
							db.execSQL("DROP TABLE " + DB_TB_BOOKS);
							
							db.execSQL(DATABASE_CREATE_BOOKS);
							
							db.execSQL("INSERT INTO " + DB_TB_BOOKS + "( " + tmpFields + ")  SELECT * FROM tmpBooks");
							
							db.execSQL("DROP TABLE tmpBooks");
						} catch (Exception e) {
							Logger.logError(e);
							throw new RuntimeException("Failed to upgrade database", e);
						} finally {
						}
					} 
					//createIndices(db); // All createIndices prior to the latest have been removed
					curVersion++;
				}
				if (curVersion == 56) {
					createIndices(db);
					curVersion++;
					message += "New in v3.5.4\n\n";
					message += "* French translation available\n\n";
					message += "* There is an option to create a duplicate book (requested by Vinika)\n\n";
					message += "* Fixed errors caused by failed upgrades\n\n";
					message += "* Fixed errors caused by trailing spaces in bookshelf names\n\n";
				}
				if (curVersion == 57) {
					curVersion++;
					Cursor results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_AUTHORS + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_AUTHORS);
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOKSHELF + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_BOOKSHELF);
						db.execSQL(DATABASE_CREATE_BOOKSHELF_DATA);
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_SERIES + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_SERIES);
						Cursor results57_2 = db.rawQuery("SELECT * FROM " + DB_TB_BOOKS, new String[]{});
						if (results57_2.getCount() > 0) {
							if (results57_2.getColumnIndex(KEY_SERIES_OLD) > -1) {
								db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + KEY_SERIES_NAME + ") "
										+ "SELECT name from ("
										+ "    SELECT Upper(" + KEY_SERIES_OLD + ") " + COLLATION + " as ucName, "
										+ "    max(" + KEY_SERIES_OLD + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
										+ "    WHERE Coalesce(" + KEY_SERIES_OLD + ",'') <> ''"
										+ "    Group By Upper(" + KEY_SERIES_OLD + ")"
										+ " )"
										);
							}
						}
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOKS + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_BOOKS);
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_LOAN + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_LOAN);
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_ANTHOLOGY + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_ANTHOLOGY);
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_BOOKSHELF_WEAK + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_SERIES + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_BOOK_SERIES);
						db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + KEY_BOOK + ", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ", " + KEY_SERIES_POSITION + ") "
								+ "SELECT DISTINCT b." + KEY_ROWID + ", s." + KEY_ROWID + ", b." + KEY_SERIES_NUM + ", 1"
								+ " FROM " + DB_TB_BOOKS + " b "
								+ " Join " + DB_TB_SERIES + " s On Upper(s." + KEY_SERIES_NAME + ") = Upper(b." + KEY_SERIES_OLD + ")" + COLLATION
								+ " Where Coalesce(b." + KEY_SERIES_OLD + ", '') <> ''");
					}
					results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_AUTHOR + "'", new String[]{});
					if (results57.getCount() == 0) {
						//table does not exist
						db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
						db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_AUTHOR_POSITION + ") "
								+ "SELECT b." + KEY_ROWID + ", b." + KEY_AUTHOR_OLD + ", 1 FROM " + DB_TB_BOOKS + " b ");
					}
					Cursor results57_3 = db.rawQuery("SELECT * FROM " + DB_TB_BOOKS, new String[]{});
					if (results57_3.getCount() > 0) {
						if (results57_3.getColumnIndex(KEY_SERIES_OLD) > -1) {
							String tmpFields = KEY_ROWID + ", " /* + KEY_AUTHOR + ", " */ + KEY_TITLE + ", " + KEY_ISBN 
							+ ", " + KEY_PUBLISHER + ", " + KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ 
							+ /* ", " + KEY_SERIES + */ ", " + KEY_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + KEY_NOTES 
							+ ", " + KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY + ", " + KEY_LOCATION + ", " + KEY_READ_START 
							+ ", " + KEY_READ_END + ", " + KEY_FORMAT + ", " + KEY_SIGNED + ", " + KEY_DESCRIPTION
							+ ", " + KEY_GENRE;
							db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields + " FROM " + DB_TB_BOOKS);
							db.execSQL("DROP TABLE " + DB_TB_BOOKS);
							db.execSQL(DATABASE_CREATE_BOOKS);
							db.execSQL("INSERT INTO " + DB_TB_BOOKS + "( " + tmpFields + ")  SELECT * FROM tmpBooks");
							db.execSQL("DROP TABLE tmpBooks");
						}
					}
					createIndices(db);
				}
				if (curVersion == 58) {
					curVersion++;
					db.delete(DB_TB_LOAN, "("+KEY_BOOK+ "='' OR " + KEY_BOOK+ "=null OR " + KEY_LOANED_TO + "='' OR " + KEY_LOANED_TO + "=null) ", null);
					message += "New in v3.6\n\n";
					message += "* The LibraryThing Key will be verified when added\n\n";
					message += "* When entering ISBN's manually, each button with vibrate the phone slightly\n\n";
					message += "* When automatically updating fields click on each checkbox twice will give you the option to override existing fields (rather than populate only blank fields)\n\n";
					message += "* Fixed a crash when exporting over 2000 books\n\n";
					message += "* Orphan loan records will now be correctly managed\n\n";
				}
			}
			//TODO: NOTE: END OF UPDATE
		}
	}
	
	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param ctx the Context within which to work
	 */
	public CatalogueDBAdapter(Context ctx) {
		this.mCtx = ctx;
	}
	
	/**
	 * Open the books database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an initialisation call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public CatalogueDBAdapter open() throws SQLException {
		/* Create the bookCatalogue directory if it does not exist */
		new File(Utils.EXTERNAL_FILE_PATH + "/").mkdirs();
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		// Turn on foreign key support so that CASCADE works.
		mDb.execSQL("PRAGMA foreign_keys = ON");
		return this;
	}
	
	/**
	 * Generic function to close the database
	 */
	public void close() {
		try {
			for(SQLiteStatement s : mStatements)
				try {
					s.close();
				} catch (Exception e)
				{};

			mDbHelper.close();
		} catch (Exception e) {
			//do nothing - already closed
		} finally {
			mStatements.clear();
		}
	}
	
	private String authorFormattedSource(String alias) {
		if (alias == null)
			alias = "";
		else if (alias.length() > 0)
			alias += ".";

		return alias + KEY_FAMILY_NAME + "||', '||" + KEY_GIVEN_NAMES;
		//return alias + KEY_GIVEN_NAMES + "||' '||" + KEY_FAMILY_NAME;
	}
	
	/**
	 * Backup database file using default file name
	 * @throws Exception 
	 */
	public void backupDbFile() {
		backupDbFile("dbExport.db");
	}
	
	/**
	 * Backup database file to the specified filename
	 *
	 * @throws Exception 
	 */
	public void backupDbFile(String filename) {
		try {
			backupDbFile(mDb, filename);
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * Backup database file
	 * @throws Exception 
	 */
	private static void backupDbFile(SQLiteDatabase db, String filename) {
		try {
			java.io.InputStream dbOrig = new java.io.FileInputStream(db.getPath());
			File dir = new File(Utils.EXTERNAL_FILE_PATH);
			dir.mkdir();
			// Path to the external backup
			java.io.OutputStream dbCopy = new java.io.FileOutputStream(dir.getPath() + "/" + filename);
			
			byte[] buffer = new byte[1024];
			int length;
			while ((length = dbOrig.read(buffer))>0) {
				dbCopy.write(buffer, 0, length);
			}
			
			dbCopy.flush();
			dbCopy.close();
			dbOrig.close();
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * return the thumbnail (as a File object) for the given id
	 * 
	 * @param id The id of the book
	 * @return The File object
	 */
	public static File fetchThumbnail(long id) {
		return fetchThumbnail(id, "");
	}

	/**
	 * return the thumbnail (as a File object) for the given id. Optionally use a suffix
	 * on the file name.
	 * 
	 * @param id The id of the book
	 * @return The File object
	 */
	public static File fetchThumbnail(long id, String suffix) {
		String filename = "";
		File file = null;
		if (id == 0) {
			filename = Utils.EXTERNAL_FILE_PATH + "/tmp" + suffix + ".png";
			file = new File(filename);
		} else {
			filename = Utils.EXTERNAL_FILE_PATH + "/" + id + suffix + ".jpg";
			file = new File(filename);
			if (!file.exists()) {
				filename = Utils.EXTERNAL_FILE_PATH + "/" + id + suffix + ".png";
				file = new File(filename);
			}
		}
		return file;
	}
	
	/**
	 * Return the filename for the thumbnail. Usually used to loadup bitmaps into imageviews
	 * 
	 * @param id The id of the book
	 * @param force Normally this function will return "" if the file does not exist. If forces it will return the filename regardless of whether the file exists
	 * @return The filename string
	 */
	public static String fetchThumbnailFilename(long id, boolean force) {
		return fetchThumbnailFilename(id, force, "");
	}

	public static String fetchThumbnailFilename(long id, boolean force, String suffix) {
		File file = fetchThumbnail(id, suffix);
		String filename = null;
		if (force == true || file.exists()) {
			filename = file.getPath(); 
		}
		return filename;
	}

	/**
	 * This function will load the thumbnail bitmap with a guaranteed maximum size; it
	 * prevents OutOfMemory exceptions on large files and reduces memory usage in lists.
	 * It can also scale images to the exact requested size.
	 * 
	 * @param id The id of the book
	 * @param destView The ImageView to load with the bitmap or an appropriate icon
	 * @param maxWidth Maximum desired width of the image
	 * @param maxHeight Maximum desired height of the image
	 * @param exact if true, the image will be propertionally scaled to fit bbox. 
	 * 
	 * @return The scaled bitmap for the file, or null if no file or bad file.
	 */
	public static Bitmap fetchThumbnailIntoImageView(long id, ImageView destView, int maxWidth, int maxHeight, boolean exact) {
		// Get the file, if it exists. Otherwise set 'help' icon and exit.
		Bitmap image = null;
		try {
			File file = fetchThumbnail(id);
			image = Utils.fetchFileIntoImageView(file, destView, maxWidth, maxHeight, exact );
		} catch (IllegalArgumentException e) {
			Logger.logError(e);
		}
		return image;
	}

	/**
	 * This will return the parsed author name based on a String. 
	 * The name can be in either "family, given" or "given family" format.
	 *
	 * @param name a String containing the name e.g. "Isaac Asimov" or "Asimov, Isaac"
	 * @return a String array containing the family and given names. e.g. ['Asimov', 'Isaac']
	 */
	static public String[] processAuthorName(String name) {
		String[] author = {"", ""};
		String family = "";
		String given = "";
		String names[];
		int commaIndex = name.indexOf(",");
		if (commaIndex > 0) {
			family = name.substring(0, commaIndex);
			given = name.substring(commaIndex+1);
		} else {
			names = name.split(" ");
			int flen = 1;
			if (names.length > 2) {
				String sname = names[names.length-2];
				/* e.g. Ursula Le Guin or Marianne De Pierres */
				if (sname.matches("[LlDd]e")) {
					family = names[names.length-2] + " ";
					flen = 2;
				}
			}
			family += names[names.length-1];
			for (int i=0; i<names.length-flen; i++) {
				given += names[i] + " ";
			}
		}
		family = family.trim();
		given = given.trim();
		author[0] = family;
		author[1] = given;
		return author;
	}
	
	/**
	 * A helper function to get a single int value (from the first row) from a cursor
	 * 
	 * @param results The Cursor the extract from
	 * @param index The index, or column, to extract from
	 * @return
	 */
	private int getIntValue(Cursor results, int index) {
		int value = 0;
		try {
			if (results != null) {
				results.moveToFirst();
				value = results.getInt(index);
			}
		} catch (CursorIndexOutOfBoundsException e) {
			value = 0;
		}
		return value;
		
	}
	
	/**
	 * A helper function to get a single string value (from the first row) from a cursor
	 * 
	 * @param results The Cursor the extract from
	 * @param index The index, or column, to extract from
	 * @return
	 */
	private String getStringValue(Cursor results, int index) {
		String value = null;
		try {
			if (results != null) {
				results.moveToFirst();
				value = results.getString(index);
			}
		} catch (CursorIndexOutOfBoundsException e) {
			value = null;
		}
		return value;
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * Return the number of books
	 * 
	 * @return int The number of books
	 */
	public int countBooks() {
		int result = 0;
		try {
			String sql = "SELECT count(*) as count FROM " + DB_TB_BOOKS + " b ";
			Cursor count = mDb.rawQuery(sql, new String[]{});
			count.moveToNext();
			result = count.getInt(0);
			count.close();
		} catch (IllegalStateException e) {
			Logger.logError(e);
		}
		return result;
	}
	
	/**
	 * Return the number of books
	 * 
	 * @param string bookshelf the bookshelf the search within
	 * @return int The number of books
	 */
	public int countBooks(String bookshelf) {
		int result = 0;
		try {
			if (bookshelf.equals("")) {
				return countBooks();
			}
			String sql = "SELECT count(DISTINCT b._id) as count " + 
				" FROM " + DB_TB_BOOKSHELF + " bs " +
				" Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs " +
				"     On bbs." + KEY_BOOKSHELF + " = bs." + KEY_ROWID +
				" Join " + DB_TB_BOOKS + " b " +
				"     On bbs." + KEY_BOOK + " = b." + KEY_ROWID + 
				" WHERE " + makeTextTerm("bs." + KEY_BOOKSHELF, "=", bookshelf);
			Cursor count = mDb.rawQuery(sql, new String[]{});
			count.moveToNext();
			result = count.getInt(0);
			count.close();
		} catch (IllegalStateException e) {
			Logger.logError(e);
		}
		return result;
	}
	
	/**
	 * A complete export of all tables (flattened) in the database 
	 * 
	 * @return Cursor over all books, authors, etc
	 */
	public Cursor exportBooks() {
		String sql = "SELECT DISTINCT " +
				getBookFields("b",KEY_ROWID) + ", " +
				"l." + KEY_LOANED_TO + " as " + KEY_LOANED_TO + " " +  
			" FROM " + DB_TB_BOOKS + " b" +
				" LEFT OUTER JOIN " + DB_TB_LOAN +" l ON (l." + KEY_BOOK + "=b." + KEY_ROWID + ") " +
			" ORDER BY b._id";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors() {
		return fetchAllAuthors(true);
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors(boolean sortByFamily) {
		String order = "";
		if (sortByFamily == true) {
			order = " ORDER BY Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION;
		} else {
			order = " ORDER BY Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + KEY_FAMILY_NAME + ") " + COLLATION;
		}
		
		String sql = "SELECT DISTINCT " + getAuthorFields("a", KEY_ROWID) + 
			" FROM " + DB_TB_AUTHORS + " a, " + DB_TB_BOOK_AUTHOR + " ab " + 
			" WHERE a." + KEY_ROWID + "=ab." + KEY_AUTHOR_ID + " " + 
			order;
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
			Logger.logError(e);
		} catch (Exception e) {
			Logger.logError(e, "fetchAllAuthors catchall");
		}
		return returnable;
	}

	/**
	 * Return a complete list of author names from the database; used for AutoComplete.
	 *  
	 * @return
	 */
	protected ArrayList<String> getAllAuthors() {
		ArrayList<String> author_list = new ArrayList<String>();
		Cursor author_cur = fetchAllAuthorsIgnoreBooks();
		try {
			while (author_cur.moveToNext()) {
				String name = author_cur.getString(author_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
				author_list.add(name);
			}
			return author_list;			
		} finally {
			author_cur.close();			
		}
	}

	private String authorOnBookshelfSql(String bookshelf, String authorIdSpec) {
		return " Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba"
		+ "               JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs"
		+ "                  On bbs." + KEY_BOOK + " = ba." + KEY_BOOK
		+ "               Join " + DB_TB_BOOKSHELF + " bs"
		+ "                  On bs." + KEY_ROWID + " = bbs." + KEY_BOOKSHELF
		+ "               Where ba." + KEY_AUTHOR_ID + " = " + authorIdSpec
		+ "               	And " + makeTextTerm("bs." + KEY_BOOKSHELF, "=", bookshelf)
		+ "              )";
		
	}

	/**
	 * Return a Cursor over the list of all authors in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors(String bookshelf) {
		return fetchAllAuthors(bookshelf, true);
	}

	/**
	 * Return a Cursor over the list of all authors in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors(String bookshelf, boolean sortByFamily) {
		if (bookshelf.equals("")) {
			return fetchAllAuthors(sortByFamily);
		}
		String order = "";
		if (sortByFamily == true) {
			order = " ORDER BY Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION;
		} else {
			order = " ORDER BY Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + KEY_FAMILY_NAME + ") " + COLLATION;
		}
		
		String sql = "SELECT " + getAuthorFields("a", KEY_ROWID)
		+ " FROM " + DB_TB_AUTHORS + " a "
		+ " WHERE " + authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID)
		+ order;
		
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
			Logger.logError(e);
		}
		return returnable;
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * Deprecated: Since authors only exist if they have a book in the database, 
	 * the book check is no longer made.
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all notes
	 */
	private Cursor fetchAllAuthorsIgnoreBooks() {
		return fetchAllAuthors();
	}
	
	/**
	 * Return a list of all the first characters for book titles in the database
	 * 
	 * @param order What order to return the books
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public Cursor fetchAllBookChars(String bookshelf) {
		String baseSql = this.fetchAllBooksSql("1", bookshelf, "", "", "", "", "");
		String sql = "SELECT DISTINCT upper(substr(b." + KEY_TITLE + ", 1, 1)) AS " + KEY_ROWID + " " + baseSql;

		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
			Logger.logError(e);
		}
		returnable.moveToFirst();
		return returnable;
	}
	
	/**
	 * Return the SQL for a list of all books in the database matching the passed criteria. The SQL
	 * returned can only be assumed to have the books table with alias 'b' and starts at the 'FROM'
	 * clause.
	 * 
	 * A possible addition would be to specify the conditions under which other data may be present.
	 * 
	 * @param order What order to return the books
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @param authorWhere 	Extra SQL pertaining to author predicate to be applied
	 * @param bookWhere 	Extra SQL pertaining to book predicate to be applied
	 * @param searchText	Raw text string to search for
	 * @param loaned_to		Name of person to whom book was loaned 
	 * @param seriesName	Name of series to match
	 * 
	 * @return SQL text for basic lookup
	 * 
	 * TODO: Replace exact-match String parameters with long parameters containing the ID.
	 */
	public String fetchAllBooksSql(String order, String bookshelf, String authorWhere, String bookWhere, String searchText, String loaned_to, String seriesName) {
		String where = "";

		if (bookWhere.length() > 0) {
			if (where.length() > 0)
				where += " and";
			where += " (" + bookWhere + ")";
		}

		if (searchText.length() > 0) {
			searchText = encodeString(searchText);
			if (where.length() > 0)
				where += " and";
			where += "( (" + bookSearchPredicate(searchText) + ") "
			+ " OR Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba"
			+ "            Join " + DB_TB_AUTHORS + " a On a." + KEY_ROWID + " = ba." + KEY_AUTHOR_ID
			+ "           Where " + authorSearchPredicate(searchText) + " and ba." + KEY_BOOK + " = b." + KEY_ROWID + ")"
			+ ")";
			// This is done in bookSearchPredicate().
			//+ " OR Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs"
			//+ "            Join " + DB_TB_SERIES + " s On s." + KEY_ROWID + " = bs." + KEY_SERIES_ID
			//+ "           Where s." + KEY_SERIES_NAME + " Like '%" + searchText + "' and bs." + KEY_BOOK + " = b." + KEY_ROWID + ")"
			//+ ")";
		}

		if (authorWhere.length() > 0) {
			if (where.length() > 0)
				where += " and";
			where += " Exists(Select NULL From " + DB_TB_AUTHORS + " a "
					+ " Join " + DB_TB_BOOK_AUTHOR + " ba "
					+ "     On ba." + KEY_AUTHOR_ID + " = a." + KEY_ROWID
					+ " Where " + authorWhere + " And ba." + KEY_BOOK + " = b." + KEY_ROWID
					+ ")";
		}

		if (loaned_to.length() > 0) {
			if (where.length() > 0)
				where += " and";
			where += " Exists(Select NULL From " + DB_TB_LOAN + " l Where "
					+ " l." + KEY_BOOK + "=b." + KEY_ROWID
					+ " And " + makeTextTerm("l." + KEY_LOANED_TO, "=", loaned_to) + ")";
		}

		if (seriesName.length() > 0 && seriesName.equals(META_EMPTY_SERIES)) {
			if (where.length() > 0)
				where += " and";
			where += " Not Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where "
					+ " bs." + KEY_BOOK + "=b." + KEY_ROWID + ")";
		}

		String sql = " FROM " + DB_TB_BOOKS + " b";

		if (!bookshelf.equals("") && bookshelf.trim().length() > 0) {
			// Join with specific bookshelf
			sql += " Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbsx On bbsx." + KEY_BOOK + " = b." + KEY_ROWID;
			sql += " Join " + DB_TB_BOOKSHELF + " bsx On bsx." + KEY_ROWID + " = bbsx." + KEY_BOOKSHELF
					+ " and " + makeTextTerm("bsx." + KEY_BOOKSHELF, "=", bookshelf);
		}

		if (seriesName.length() > 0 && !seriesName.equals(META_EMPTY_SERIES))
			sql += " Join " + DB_TB_BOOK_SERIES + " bs On (bs." + KEY_BOOK + " = b." + KEY_ROWID + ")"
					+ " Join " + DB_TB_SERIES + " s On (s." + KEY_ROWID + " = bs." + KEY_SERIES_ID 
					+ " and " + makeTextTerm("s." + KEY_SERIES_NAME, "=", seriesName) + ")";


		if (where.length() > 0)
			sql += " WHERE " + where;

		// NULL order suppresses order-by
		if (order != null) {
			if (order.length() > 0)
				// TODO Assess if ORDER is used and how
				sql += " ORDER BY " + order + "";
			else
				sql += " ORDER BY Upper(b." + KEY_TITLE + ") " + COLLATION + " ASC";
		}

		return sql;
	}

	/**
	 * Return a list of all books in the database
	 * 
	 * @param order What order to return the books
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public Cursor fetchAllBooks(String order, String bookshelf, String authorWhere, String bookWhere, String searchText, String loaned_to, String seriesName) {
		String baseSql = this.fetchAllBooksSql("", bookshelf, authorWhere, bookWhere, searchText, loaned_to, seriesName);

		// Get the basic query; we will use it as a sub-query
		String sql = "SELECT DISTINCT " + getBookFields("b", KEY_ROWID) + baseSql;
		String fullSql = "Select b.*, " + 
		"a." + KEY_AUTHOR_ID + ", " +
		"a." + KEY_FAMILY_NAME + ", " +
		"a." + KEY_GIVEN_NAMES + ", " +
		" Case When _num_authors < 2 Then a." + KEY_AUTHOR_FORMATTED + 
		" Else " + KEY_AUTHOR_FORMATTED + "|| ' et. al.' End as " + KEY_AUTHOR_FORMATTED + ", " +
		"Coalesce(s." + KEY_SERIES_ID + ", 0) as " + KEY_SERIES_ID + ", " +
		"Coalesce(s." + KEY_SERIES_NAME + ", '') as " + KEY_SERIES_NAME + ", " +
		"Coalesce(s." + KEY_SERIES_NUM + ", '') as " + KEY_SERIES_NUM + ", " +
		" Case When _num_series < 2 Then Coalesce(s." + KEY_SERIES_FORMATTED + ", '')" +
		" Else " + KEY_SERIES_FORMATTED + "||' et. al.' End as " + KEY_SERIES_FORMATTED + " " +
		" from (" + sql + ") b";

		// Get the 'default' author...defined in getBookFields()
		fullSql += " Join (Select " 
			+ KEY_AUTHOR_ID + ", " 
			+ KEY_FAMILY_NAME + ", " 
			+ KEY_GIVEN_NAMES + ", " 
			+ "ba." + KEY_BOOK + " as " + KEY_BOOK + ", "
			+ " Case When " + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME
			+ " Else " + authorFormattedSource("") + " End as " + KEY_AUTHOR_FORMATTED
			+ " From " + DB_TB_BOOK_AUTHOR + " ba Join " + DB_TB_AUTHORS + " a"
			+ "    On ba." + KEY_AUTHOR_ID + " = a." + KEY_ROWID + ") a "
			+ " On a." + KEY_BOOK + " = b." + KEY_ROWID + " and a." + KEY_AUTHOR_ID + " = b." + KEY_AUTHOR_ID;

		if (seriesName.length() > 0 && !seriesName.equals(META_EMPTY_SERIES)) {
			// Get the specified series...
			fullSql += " Left Outer Join (Select " 
				+ KEY_SERIES_ID + ", " 
				+ KEY_SERIES_NAME + ", " 
				+ KEY_SERIES_NUM  + ", "
				+ "bs." + KEY_BOOK + " as " + KEY_BOOK + ", "
				+ " Case When " + KEY_SERIES_NUM + " = '' Then " + KEY_SERIES_NAME 
				+ " Else " + KEY_SERIES_NAME + "||' #'||" + KEY_SERIES_NUM + " End as " + KEY_SERIES_FORMATTED
				+ " From " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s"
				+ "    On bs." + KEY_SERIES_ID + " = s." + KEY_ROWID + ") s "
				+ " On s." + KEY_BOOK + " = b." + KEY_ROWID 
				+ " and " + makeTextTerm("s." + KEY_SERIES_NAME, "=", seriesName); 
				//+ " and " + this.makeEqualFieldsTerm("s." + KEY_SERIES_NUM, "b." + KEY_SERIES_NUM);
		} else {
			// Get the 'default' series...defined in getBookFields()
			fullSql += " Left Outer Join (Select " 
				+ KEY_SERIES_ID + ", " 
				+ KEY_SERIES_NAME + ", " 
				+ KEY_SERIES_NUM  + ", "
				+ "bs." + KEY_BOOK + " as " + KEY_BOOK + ", "
				+ " Case When " + KEY_SERIES_NUM + " = '' Then " + KEY_SERIES_NAME 
				+ " Else " + KEY_SERIES_NAME + "||' #'||" + KEY_SERIES_NUM + " End as " + KEY_SERIES_FORMATTED
				+ " From " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s"
				+ "    On bs." + KEY_SERIES_ID + " = s." + KEY_ROWID + ") s "
				+ " On s." + KEY_BOOK + " = b." + KEY_ROWID 
				+ " and s." + KEY_SERIES_ID + " = b." + KEY_SERIES_ID
				+ " and " + this.makeEqualFieldsTerm("s." + KEY_SERIES_NUM, "b." + KEY_SERIES_NUM);
		}
		if (!order.equals("")) {
			fullSql += " ORDER BY " + order;
		}
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(fullSql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(fullSql, new String[]{});
			Logger.logError(e);
		}
		return returnable;
	}
	
	/**
	 * Return a list of all books in the database by author
	 * 
	 * @param author The author to search for
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public Cursor fetchAllBooksByAuthor(int author, String bookshelf, String search_term) {
		String where = " a._id=" + author;
		String order = "s." + KEY_SERIES_NAME + ", substr('0000000000' || s." + KEY_SERIES_NUM + ", -10, 10), lower(b." + KEY_TITLE + ") ASC";
		return fetchAllBooks(order, bookshelf, where, "", search_term, "", "");
	}
	
	/**
	 * This will return a list of all books by a given first title character
	 * 
	 * @param char The first title character
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByChar(String first_char, String bookshelf, String search_term) {
		String where = " " + makeTextTerm("substr(b." + KEY_TITLE + ",1,1)", "=", first_char);
		return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
	}
	
	/**
	 * Return a Cursor over the list of all books in the database by genre
	 * 
	 * @param author The genre name to search by
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByGenre(String genre, String bookshelf, String search_term) {
		String where = "";
		if (genre.equals(META_EMPTY_GENRE)) {
			where = "(b." + KEY_GENRE + "='' OR b." + KEY_GENRE + " IS NULL)";
		} else {
			where = makeTextTerm("b." + KEY_GENRE, "=", genre);
		}
		return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
	}
	
	/**
	 * This will return a list of all books loaned to a given person
	 * 
	 * @param loaned_to The person who had books loaned to
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByLoan(String loaned_to, String search_term) {
		return fetchAllBooks("", "", "", "", search_term, loaned_to, "");
	}
	
	/**
	 * This will return a list of all books either read or unread
	 * 
	 * @param read "Read" or "Unread"
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByRead(String read, String bookshelf, String search_term) {
		String where = "";
		if (read.equals("Read")) {
			where += " b." + KEY_READ + "=1";
		} else {
			where += " b." + KEY_READ + "!=1";
		}
		return fetchAllBooks("", bookshelf, "", where, search_term, "", "");
	}
	
	/**
	 * Return a Cursor over the list of all books in the database by series
	 * 
	 * @param series The series name to search by
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksBySeries(String series, String bookshelf, String search_term) {
		if (series.length() == 0 || series.equals(META_EMPTY_SERIES)) {
			return fetchAllBooks("", bookshelf, "", "", search_term, "", META_EMPTY_SERIES);
		} else {
			String order = "substr('0000000000' || s." + KEY_SERIES_NUM + ", -10, 10), b." + KEY_TITLE + " " + COLLATION + " ASC";
			return fetchAllBooks(order, bookshelf, "", "", search_term, "", series);
		}
	}

	/**
	 * Return a Cursor over the list of all bookshelves in the database
	 * 
	 * @return Cursor over all bookshelves
	 */
	public Cursor fetchAllBookshelves() {
		String sql = "SELECT DISTINCT bs." + KEY_ROWID + " as " + KEY_ROWID + ", " +
				"bs." + KEY_BOOKSHELF + " as " + KEY_BOOKSHELF + ", " +
				"0 as " + KEY_BOOK + 
			" FROM " + DB_TB_BOOKSHELF + " bs" + 
			" ORDER BY Upper(bs." + KEY_BOOKSHELF + ") " + COLLATION ;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a Cursor over the list of all bookshelves in the database
	 * 
	 * @param long rowId the rowId of a book, which in turn adds a new field on each row as to the active state of that bookshelf for the book
	 * @return Cursor over all bookshelves
	 */
	public Cursor fetchAllBookshelves(long rowId) {
		String sql = "SELECT DISTINCT bs." + KEY_ROWID + " as " + KEY_ROWID + ", " +
				"bs." + KEY_BOOKSHELF + " as " + KEY_BOOKSHELF + ", " +
				"CASE WHEN w." + KEY_BOOK + " IS NULL THEN 0 ELSE 1 END as " + KEY_BOOK + 
			" FROM " + DB_TB_BOOKSHELF + " bs LEFT OUTER JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " w ON (w." + KEY_BOOKSHELF + "=bs." + KEY_ROWID + " AND w." + KEY_BOOK + "=" + rowId + ") " + 
			" ORDER BY Upper(bs." + KEY_BOOKSHELF + ") " + COLLATION;
		try {
			return mDb.rawQuery(sql, new String[]{});
		} catch (NullPointerException e) {
			// there is now rowId
			return fetchAllBookshelves();
		}
	}
	
	/**
	 * Return a Cursor over the list of all bookshelves in the database for the given book
	 * 
	 * @param long rowId the rowId of the book
	 * @return Cursor over all bookshelves
	 */
	public Cursor fetchAllBookshelvesByBook(long rowId) {
		String sql = "SELECT DISTINCT bs." + KEY_ROWID + " as " + KEY_ROWID + ", bs." + KEY_BOOKSHELF + " as " + KEY_BOOKSHELF + 
			" FROM " + DB_TB_BOOKSHELF + " bs, " + DB_TB_BOOK_BOOKSHELF_WEAK + " w " +
			" WHERE w." + KEY_BOOKSHELF + "=bs." + KEY_ROWID + " AND w." + KEY_BOOK + "=" + rowId + " " + 
			" ORDER BY Upper(bs." + KEY_BOOKSHELF + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all genres within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllGenres(String bookshelf) {
		// Null 'order' to suppress ordering
		String baseSql = fetchAllBooksSql(null, bookshelf, "", "", "", "", "");

		String sql = "SELECT DISTINCT "
				+ " Case When (b." + KEY_GENRE + " = '' or b." + KEY_GENRE + " is NULL) Then '" + META_EMPTY_GENRE + "'"
				+ " Else b." + KEY_GENRE + " End as " + KEY_ROWID + baseSql +
		" ORDER BY Upper(b." + KEY_GENRE + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all loans
	 * 
	 * @return Cursor over all series
	 */
	public Cursor fetchAllLoans() {
		//cleanup SQL
		//String cleanup = "DELETE FROM " + DATABASE_TABLE_LOAN + " " +
		//		" WHERE " + KEY_BOOK + " NOT IN (SELECT " + KEY_ROWID + " FROM " + DATABASE_TABLE_BOOKS + ") ";
		//mDb.rawQuery(cleanup, new String[]{});
		
		//fetch books
		String sql = "SELECT DISTINCT l." + KEY_LOANED_TO + " as " + KEY_ROWID + 
		" FROM " + DB_TB_LOAN + " l " + 
		" ORDER BY Upper(l." + KEY_LOANED_TO + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a Cursor over the list of all locations in the database
	 * 
	 * @return Cursor over all locations
	 */
	public Cursor fetchAllLocations() {
		String sql = "SELECT DISTINCT " + KEY_LOCATION +  
			" FROM " + DB_TB_BOOKS + "" +  
			" ORDER BY Upper(" + KEY_LOCATION + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a Cursor over the list of all publishers in the database
	 * 
	 * @return Cursor over all publisher
	 */
	public Cursor fetchAllPublishers() {
		String sql = "SELECT DISTINCT " + KEY_PUBLISHER +  
			" FROM " + DB_TB_BOOKS + "" +  
			" ORDER BY Upper(" + KEY_PUBLISHER + ") " + COLLATION;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	
	/**
	 * This will return a list of all series within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllSeries(String bookshelf) {
		return fetchAllSeries(bookshelf, false);
	}

	private String sqlAllSeriesOnBookshelf(String bookshelf) {
		return "select distinct s." + KEY_ROWID + " as " + KEY_ROWID + ", s." + KEY_SERIES_NAME + " as " + KEY_SERIES_NAME//+ ", s." + KEY_SERIES_NAME + " as series_sort "
				 + " From " + DB_TB_SERIES + " s "
				 + " join " + DB_TB_BOOK_SERIES + " bsw "
				 + "    on bsw." + KEY_SERIES_ID + " = s." + KEY_ROWID 
				 + " join " + DB_TB_BOOKS + " b "
				 + "    on b." + KEY_ROWID + " = bsw." + KEY_BOOK
				 + " join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbw"
				 + "    on bbw." + KEY_BOOK + " = b." + KEY_ROWID 
				 + " join " + DB_TB_BOOKSHELF + " bs "
				 + "    on bs." + KEY_ROWID + " = bbw." + KEY_BOOKSHELF
				 + " where " + makeTextTerm("bs." + KEY_BOOKSHELF, "=", bookshelf);
	}
	private String sqlAllSeries() {
		return "select distinct s." + KEY_ROWID + " as " + KEY_ROWID + ", s."+ KEY_SERIES_NAME + " as " + KEY_SERIES_NAME //+ ", s." + KEY_SERIES_NAME + " as series_sort "
				 + " From " + DB_TB_SERIES + " s ";
	}
	/**
	 * This will return a list of all series within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllSeries(String bookshelf, boolean include_blank) {
		String series;
		if (bookshelf.equals("")) {
			series = sqlAllSeries();
		} else {
			series = sqlAllSeriesOnBookshelf(bookshelf);
		}
		// Display blank series as '<Empty Series>' BUT sort as ''. Using a UNION
		// seems to make ordering fail.
		String sql = "Select " + KEY_ROWID + ", Case When " + KEY_SERIES_NAME + " = '' Then '" + META_EMPTY_SERIES + "' Else " + KEY_SERIES_NAME + " End  as " + KEY_SERIES_NAME
					+ " From ( " + series 
					+ "       UNION Select -1 as " + KEY_ROWID + ", '' as " + KEY_SERIES_NAME
					+ "       ) s"
					+ " Order by Upper(s." + KEY_SERIES_NAME + ") " + COLLATION + " asc ";

		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list consisting of "Read" and "Unread"
	 * 
	 * @return Cursor over all the psuedo list
	 */
	public Cursor fetchAllUnreadPsuedo() {
		String sql = "SELECT 'Unread' as " + KEY_ROWID + "" +
				" UNION SELECT 'Read' as " + KEY_ROWID + "";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return all the anthology titles and authors recorded for book
	 * 
	 * @param rowId id of book to retrieve
	 * @return Cursor containing all records, if found
	 */
	public Cursor fetchAnthologyTitlesByBook(long rowId) {
		String sql = "SELECT an." + KEY_ROWID + " as " + KEY_ROWID 
				+ ", an." + KEY_TITLE + " as " + KEY_TITLE 
				+ ", an." + KEY_POSITION + " as " + KEY_POSITION 
				+ ", au." + KEY_FAMILY_NAME + " || ', ' || au." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR_NAME 
				+ ", an." + KEY_BOOK + " as " + KEY_BOOK
				+ ", an." + KEY_AUTHOR_ID + " as " + KEY_AUTHOR_ID
			+ " FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au "
			+ " WHERE an." + KEY_AUTHOR_ID + "=au." + KEY_ROWID + " AND an." + KEY_BOOK + "='" + rowId + "'"
			+ " ORDER BY an." + KEY_POSITION + "";
		Cursor mCursor = mDb.rawQuery(sql, new String[]{});
		return mCursor;
	}
	
	/**
	 * Return a specific anthology titles and authors recorded for book
	 * 
	 * @param rowId id of anthology to retrieve
	 * @return Cursor containing all records, if found
	 */
	public Cursor fetchAnthologyTitleById(long rowId) {
		String sql = "SELECT an." + KEY_ROWID + " as " + KEY_ROWID 
			+ ", an." + KEY_TITLE + " as " + KEY_TITLE 
			+ ", an." + KEY_POSITION + " as " + KEY_POSITION + 
			", au." + KEY_FAMILY_NAME + " || ', ' || au." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR_NAME 
			+ ", an." + KEY_BOOK + " as " + KEY_BOOK
			+ ", an." + KEY_AUTHOR_ID + " as " + KEY_AUTHOR_ID
			+ " FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au "
			+ " WHERE an." + KEY_AUTHOR_ID + "=au." + KEY_ROWID + " AND an." + KEY_ROWID + "='" + rowId + "'";
		Cursor mCursor = mDb.rawQuery(sql, new String[]{});
		return mCursor;
	}
	
	/**
	 * Return the largest anthology position (usually used for adding new titles)
	 * 
	 * @param rowId id of book to retrieve
	 * @return An integer of the highest position. 0 if it is not an anthology
	 */
	public int fetchAnthologyPositionByBook(long rowId) {
		String sql = "SELECT max(" + KEY_POSITION + ") FROM " + DB_TB_ANTHOLOGY + 
			" WHERE " + KEY_BOOK + "='" + rowId + "'";
		Cursor mCursor = mDb.rawQuery(sql, new String[]{});
		int position = getIntValue(mCursor, 0);
		mCursor.close();
		return position;
	}
	
	/**
	 * Return the position of an author in a list of all authors (within a bookshelf)
	 *  
	 * @param author The author to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the author
	 */
	public int fetchAuthorPositionByGivenName(String name, String bookshelf) {

		String where = "";
		String[] names = processAuthorName(name);
		if (bookshelf.equals("")) {
			// do nothing
		} else {
			where += authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID);
		}
		if (where != null && where.length() > 0)
			where = " and " + where;

		String sql = "SELECT count(*) as count FROM " + DB_TB_AUTHORS + " a " +
			"WHERE ( " + makeTextTerm("a." + KEY_GIVEN_NAMES, "<", names[0]) +
			"OR ( " + makeTextTerm("a." + KEY_GIVEN_NAMES, "=", names[0]) + 
			"     AND " + makeTextTerm("a." + KEY_FAMILY_NAME, "<", names[1]) + ")) " + 
			where + 
			" ORDER BY Upper(a." + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(a." + KEY_FAMILY_NAME + ") " + COLLATION;
		Cursor results = mDb.rawQuery(sql, null);
		int pos = getIntValue(results, 0);
		results.close();
		return pos;
	}
	
	/**
	 * Return the position of an author in a list of all authors (within a bookshelf)
	 *  
	 * @param author The author to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the author
	 */
	public int fetchAuthorPositionByName(String name, String bookshelf) {

		String where = "";
		String[] names = processAuthorName(name);
		if (bookshelf.equals("")) {
			// do nothing
		} else {
			where += authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID);
		}
		if (where != null && where.length() > 0)
			where = " and " + where;

		String sql = "SELECT count(*) as count FROM " + DB_TB_AUTHORS + " a " +
			"WHERE ( " + makeTextTerm("a." + KEY_FAMILY_NAME, "<", names[0]) +
			"OR ( " + makeTextTerm("a." + KEY_FAMILY_NAME, "=", names[0]) + 
			"     AND " + makeTextTerm("a." + KEY_GIVEN_NAMES, "<", names[1]) + ")) " + 
			where + 
			" ORDER BY Upper(a." + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(a." + KEY_GIVEN_NAMES + ") " + COLLATION;
		Cursor results = mDb.rawQuery(sql, null);
		int pos = getIntValue(results, 0);
		results.close();
		return pos;
	}
	
	/**
	 * Return a book (Cursor) that matches the given rowId
	 * 
	 * @param rowId id of book to retrieve
	 * @return Cursor positioned to matching book, if found
	 * @throws SQLException if note could not be found/retrieved
	 */
	public Cursor fetchBookById(long rowId) throws SQLException {
		String where = "b." + KEY_ROWID + "=" + rowId;
		return fetchAllBooks("", "", "", where, "", "", "");
	}
	
	/**
	 * Return a book (Cursor) that matches the given rowId
	 * 
	 * @param rowId id of book to retrieve
	 * @return Cursor positioned to matching book, if found
	 * @throws SQLException if note could not be found/retrieved
	 */
	private SQLiteStatement mCheckBookExistsStmt = null;
	public boolean checkBookExists(long rowId) {
		if (mCheckBookExistsStmt == null) {
			mCheckBookExistsStmt = compileStatement("Select " + KEY_ROWID + " From " + DB_TB_BOOKS + " Where " + KEY_ROWID + " = ?");
		}
		mCheckBookExistsStmt.bindLong(1, rowId);
		try {
			mCheckBookExistsStmt.simpleQueryForLong();
			return true;
		} catch (SQLiteDoneException e) {
			return false;
		}
	}
	
	private SQLiteStatement mCheckIsbnExistsStmt = null;
	/**
	 * 
	 * @param isbn The isbn to search by
	 * @return boolean indicating ISBN already in DB
	 */
	public boolean checkIsbnExists(String isbn) {
		if (mCheckIsbnExistsStmt == null) {
			mCheckIsbnExistsStmt = compileStatement("Select Count(*) From " + DB_TB_BOOKS + " Where Upper(" + KEY_ISBN + ") = Upper(?)");
		}
		mCheckIsbnExistsStmt.bindString(1, isbn);
		return mCheckIsbnExistsStmt.simpleQueryForLong() > 0;
	}
	
	/**
	 *
	 * @param family Family name of author
	 * @param given Given name of author
	 * @param title Title of book
	 * @return Cursor of the book
	 */
	public Cursor fetchByAuthorAndTitle(String family, String given, String title) {
		String authorWhere = makeTextTerm("a." + KEY_FAMILY_NAME, "=", family) 
							+ " AND " + makeTextTerm("a." + KEY_GIVEN_NAMES, "=", given);
		String bookWhere = makeTextTerm("b." + KEY_TITLE, "=", title);
		return fetchAllBooks("", "", authorWhere, bookWhere, "", "", "" );
	}

	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param title The book title to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchBookPositionByTitle(String title, String bookshelf) {
		String baseSql = this.fetchAllBooksSql("1", bookshelf, "", makeTextTerm("Substr(b." + KEY_TITLE + ",1,1)", "<", title.substring(0,1)), "", "", "");
		String sql = "SELECT Count(Distinct Upper(Substr(" + KEY_TITLE + ",1,1))" + COLLATION + ") as count " + baseSql;

		Cursor results = mDb.rawQuery(sql, null);
		int pos = getIntValue(results, 0);
		results.close();
		return pos;
	}

	private SQLiteStatement mGetBookshelfNameStmt = null;
	/**
	 * Return a Cursor positioned at the bookshelf that matches the given rowId
	 * 
	 * @param rowId id of bookshelf to retrieve
	 * @return Name of bookshelf, if found, or throws SQLiteDoneException
	 */
	public String getBookshelfName(long rowId) throws SQLiteDoneException {
		if (mGetBookshelfNameStmt == null) {
			mGetBookshelfNameStmt = compileStatement("Select " + KEY_BOOKSHELF + " From " + DB_TB_BOOKSHELF + " Where " + KEY_ROWID + " = ?");
		}
		mGetBookshelfNameStmt.bindLong(1, rowId);
		return mGetBookshelfNameStmt.simpleQueryForString();
	}
	
//	/**
//	 * Return a Cursor positioned at the bookshelf that matches the given rowId
//	 * 
//	 * @param rowId id of bookshelf to retrieve
//	 * @return Cursor positioned to matching note, if found
//	 * @throws SQLException if note could not be found/retrieved
//	 */
//	public Cursor fetchBookshelf(long rowId) throws SQLException {
//		String sql = "SELECT bs." + KEY_ROWID + ", bs." + KEY_BOOKSHELF + 
//		" FROM " + DB_TB_BOOKSHELF + " bs " +  
//		" WHERE bs." + KEY_ROWID + "=" + rowId + "";
//		Cursor mCursor = mDb.rawQuery(sql, new String[]{});
//		if (mCursor != null) {
//			mCursor.moveToFirst();
//		}
//		return mCursor;
//	}
//	
//	/**
//	 * This will return the bookshelf id based on the name. 
//	 * 
//	 * @param name The bookshelf name to search for
//	 * @return A cursor containing all bookshelves with the given name
//	 */
//	public Cursor fetchBookshelfByName(String name) {
//		String sql = "";
//		sql = makeTextTerm(KEY_BOOKSHELF, "=", name);
//		return mDb.query(DB_TB_BOOKSHELF, new String[] {"_id", KEY_BOOKSHELF}, sql, null, null, null, null);
//	}
	
	/**
	 * This will return JUST the bookshelf id based on the name. 
	 * The name can be in either "family, given" or "given family" format.
	 * 
	 * @param name The bookshelf name to search for
	 * @return A cursor containing all bookshelves with the given name
	 */
	private SQLiteStatement mFetchBookshelfIdByNameStmt = null;
	public long fetchBookshelfIdByName(String name) {
		if (mFetchBookshelfIdByNameStmt == null) {
			mFetchBookshelfIdByNameStmt = compileStatement("Select " + KEY_ROWID + " From " + KEY_BOOKSHELF 
											+ " Where Upper(" + KEY_BOOKSHELF + ") = Upper(?)" + COLLATION);
		}
		mFetchBookshelfIdByNameStmt.bindString(1, name);
		long id;
		try {
			id = mFetchBookshelfIdByNameStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			id = 0L;
		}
		return id;
	}
	
	/**
	 * This will return the borrower for a given book, if any
	 * 
	 * @param mRowId The book id to search for
	 * @return Who the book is loaned to, can be blank.
	 */
	public String fetchLoanByBook(Long mRowId) {
		String sql = "";
		sql = KEY_BOOK + "=" + mRowId + "";
		Cursor results = mDb.query(DB_TB_LOAN, new String[] {KEY_BOOK, KEY_LOANED_TO}, sql, null, null, null, null);
		String user = getStringValue(results, 1);
		results.close();
		return user;
	}
	
	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param genre The book genre to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchGenrePositionByGenre(String genre, String bookshelf) {
		if (genre.equals(META_EMPTY_GENRE))
			return 0;

		String where = makeTextTerm("b." + KEY_GENRE, "<", genre);
		String baseSql = fetchAllBooksSql("", bookshelf, "", where, "", "", "");

		String sql = "SELECT Count(DISTINCT Upper(" + KEY_GENRE + "))" + baseSql;
		Cursor results = mDb.rawQuery(sql, null);
		int pos = (getIntValue(results, 0));
		results.close();
		return pos;
	}
	
	/**
	 * 
	 * @param query The query string
	 * @return Cursor of search suggestions
	 */
	public Cursor fetchSearchSuggestions(String query) {
		String sql = "Select * From (SELECT \"BK\" || b." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", b." + KEY_TITLE + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", b." + KEY_TITLE + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_BOOKS + " b" + 
			" WHERE b." + KEY_TITLE + " LIKE '"+query+"%'" +
			" UNION " + 
			" SELECT \"AF\" || a." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", a." + KEY_FAMILY_NAME + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", a." + KEY_FAMILY_NAME + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_AUTHORS + " a" + 
			" WHERE a." + KEY_FAMILY_NAME + " LIKE '"+query+"%'" +
			" UNION " + 
			" SELECT \"AG\" || a." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", a." + KEY_GIVEN_NAMES + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", a." + KEY_GIVEN_NAMES + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_AUTHORS + " a" + 
			" WHERE a." + KEY_GIVEN_NAMES + " LIKE '"+query+"%'" +
			" UNION " + 
			" SELECT \"BK\" || b." + KEY_ROWID + " as " + BaseColumns._ID 
				+ ", b." + KEY_ISBN + " as " + SearchManager.SUGGEST_COLUMN_TEXT_1 
				+ ", b." + KEY_ISBN + " as " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
			" FROM " + DB_TB_BOOKS + " b" + 
			" WHERE b." + KEY_ISBN + " LIKE '"+query+"%'" +
			" ) as zzz " + 
			" ORDER BY Upper(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ") " + COLLATION;
			;
		Cursor results = mDb.rawQuery(sql, null);
		return results;
	}
	
	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param title The book title to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchSeriesPositionBySeries(String seriesName, String bookshelf) {
		String seriesSql;
		if (bookshelf.equals("")) {
			seriesSql = sqlAllSeries();
		} else {
			seriesSql = sqlAllSeriesOnBookshelf(bookshelf);
		}
		if (seriesName.equals(META_EMPTY_SERIES))
			seriesName = "";

		// Display blank series as '<Empty Series>' BUT sort as ''. Using a UNION
		// seems to make ordering fail.
		String sql = "Select Count(Distinct " + KEY_SERIES_NAME + ") as count"
					+ " From ( " + seriesSql 
					+ "       UNION Select -1 as " + KEY_ROWID + ", '' as " +  KEY_SERIES_NAME
					+ "       ) s "
					+ " WHERE " + makeTextTerm("s." + KEY_SERIES_NAME, "<", seriesName)
					+ " Order by s." + KEY_SERIES_NAME + COLLATION + " asc ";

		Cursor results = mDb.rawQuery(sql, null);
		int pos = (getIntValue(results, 0));
		results.close();
		return pos;
	}
	
	/**
	 * Return a Cursor over the author in the database which meet the provided search criteria
	 * 
	 * @param query The search query
	 * @param bookshelf The bookshelf to search within
	 * @return Cursor over all authors
	 */
	public Cursor searchAuthors(String searchText, String bookshelf) {
		return searchAuthors(searchText, bookshelf, true);
	}
	
	/**
	 * Return a Cursor over the author in the database which meet the provided search criteria
	 * 
	 * @param query The search query
	 * @param bookshelf The bookshelf to search within
	 * @return Cursor over all authors
	 */
	public Cursor searchAuthors(String searchText, String bookshelf, boolean sortByFamily) {
		String where = "";
		searchText = encodeString(searchText);
		if (bookshelf.equals("")) {
			// do nothing
		} else {
			where += this.authorOnBookshelfSql(bookshelf, "a." + KEY_ROWID);
		}
		if (where != null && where.trim().length() > 0)
			where = " and " + where;
		
		String order = "";
		if (sortByFamily == true) {
			order = " ORDER BY Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + ", Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION;
		} else {
			order = " ORDER BY Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + KEY_FAMILY_NAME + ") " + COLLATION;
		}
		
		String sql = "SELECT " + getAuthorFields("a", KEY_ROWID) +
			" FROM " + DB_TB_AUTHORS + " a" + " " +
			"WHERE (" + authorSearchPredicate(searchText) +  " OR " +
				"a." + KEY_ROWID + " IN (SELECT ba." + KEY_AUTHOR_ID + 
				" FROM " + DB_TB_BOOKS + " b Join " + DB_TB_BOOK_AUTHOR + " ba " + 
				 		" On ba." + KEY_BOOK + " = b." + KEY_ROWID + " " + 
					"WHERE (" + bookSearchPredicate(searchText)  + ") ) )" + 
				where + order;
		return mDb.rawQuery(sql, new String[]{});
	}

	private String makeSearchTerm(String key, String text) {
		return "Upper(" + key + ") LIKE Upper('%" + text + "%') " + COLLATION;
	}

	private String makeEqualFieldsTerm(String v1, String v2) {
		return "Upper(" + v1 + ") = Upper(" + v2 + ") " + COLLATION;
	}

	private String makeTextTerm(String field, String op, String text) {
		return "Upper(" + field + ") " + op + " Upper('" + encodeString(text) + "') " + COLLATION;
	}

	private String authorSearchPredicate(String search_term) {
		return "(" + makeSearchTerm(KEY_FAMILY_NAME, search_term) + " OR " +
				makeSearchTerm(KEY_GIVEN_NAMES, search_term) + ")";
	}

	private String bookSearchPredicate(String search_term) {
		StringBuilder result = new StringBuilder("(");

		// Just do a simple search of a bunch of fields.
		String[] keys = new String[] {KEY_TITLE, KEY_ISBN, KEY_PUBLISHER, KEY_NOTES, KEY_LOCATION, KEY_DESCRIPTION};
		for(String k : keys)
			result.append(makeSearchTerm(k, search_term) + " OR ");

		// And check the series too.
		result.append(" Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bsw "
						+ " Join " + DB_TB_SERIES + " s "
						+ "     On s." + KEY_ROWID + " = bsw." + KEY_SERIES_ID 
						+ "         And " + makeSearchTerm("s." + KEY_SERIES_NAME, search_term)
						+ " Where bsw." + KEY_BOOK + " = b." + KEY_ROWID + ") ");		
		
		//and check the anthologies too.
		result.append(" OR Exists (SELECT NULL FROM  " + DB_TB_ANTHOLOGY + " bsan, " + DB_TB_AUTHORS + " bsau "
				+ " WHERE bsan." + KEY_AUTHOR_ID + "= bsau." + KEY_ROWID + " AND bsan." + KEY_BOOK + " = b." + KEY_ROWID + " AND "
				+ "(" + makeSearchTerm("bsan." + KEY_TITLE, search_term) + " OR " 
				+ makeSearchTerm("bsau." + KEY_FAMILY_NAME, search_term) + " OR "
				+ makeSearchTerm("bsau." + KEY_GIVEN_NAMES, search_term) + ")) ");

		result.append( ")") ;

		return result.toString();
	}

	/**
	 * Returns a list of books, similar to fetchAllBooks but restricted by a search string. The
	 * query will be applied to author, title, and series
	 * 
	 * @param query The search string to restrict the output by
	 * @param order What order to return in 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return A Cursor of book meeting the search criteria
	 */
	public Cursor searchBooksByChar(String searchText, String first_char, String bookshelf) {
		String where = " " + makeTextTerm("substr(b." + KEY_TITLE + ",1,1)", "=", first_char);
		return fetchAllBooks("", bookshelf, "", where, searchText, "", "");
	}

	public Cursor searchBooksByGenre(String searchText, String genre, String bookshelf) {
		return fetchAllBooks("", bookshelf, "", " " + KEY_GENRE + "='" + genre + "' " + COLLATION + " ", searchText, "", "");
	}
	
	/**
	 * Returns a list of books title characters, similar to fetchAllBookChars but restricted by a search string. The
	 * query will be applied to author, title, and series
	 * 
	 * @param query The search string to restrict the output by
	 * @param order What order to return in 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return A Cursor of book meeting the search criteria
	 */
	public Cursor searchBooksChars(String searchText, String bookshelf) {
		String baseSql = this.fetchAllBooksSql("1", bookshelf, "", "", searchText, "", "");
		String sql = "SELECT DISTINCT upper(substr(b." + KEY_TITLE + ", 1, 1)) " + COLLATION + " AS " + KEY_ROWID + " " + baseSql;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all genres within the given bookshelf where the
	 * series, title or author meet the search string
	 * 
	 * @param query The query string to search for 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor searchGenres(String searchText, String bookshelf) {
		String baseSql = this.fetchAllBooksSql("1", bookshelf, "", "", searchText, "", "");
		String sql = "SELECT DISTINCT Case When " + KEY_GENRE + " = '' Then '" + META_EMPTY_GENRE + "' else " + KEY_GENRE + " End " + COLLATION + " AS " + KEY_ROWID + " " + baseSql;
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all series within the given bookshelf where the
	 * series, title or author meet the search string
	 * 
	 * @param query The query string to search for 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor searchSeries(String searchText, String bookshelf) {
		/// Need to know when to add the 'no series' series...
		String sql;
		String baseSql = this.fetchAllBooksSql("1", bookshelf, "", "", searchText, "", "");

		sql = "Select DISTINCT Case When s." + KEY_ROWID + " is NULL Then -1 Else s." + KEY_ROWID + " End as " + KEY_ROWID + ","
			+ " Case When s." + KEY_SERIES_NAME + " is NULL Then '" + META_EMPTY_SERIES + "'"
			+ "               Else " + KEY_SERIES_NAME + " End AS " + KEY_SERIES_NAME
			+ " From (Select b." + KEY_ROWID + " as " + KEY_ROWID + " " + baseSql + " ) MatchingBooks"
			+ " Left Outer Join " + DB_TB_BOOK_SERIES + " bs "
			+ "     On bs." + KEY_BOOK + " = MatchingBooks." + KEY_ROWID
			+ " Left Outer Join " + DB_TB_SERIES + " s "
			+ "     On s." + KEY_ROWID + " = bs." + KEY_SERIES_ID
			+ " Order by Upper(s." + KEY_SERIES_NAME + ") " + COLLATION + " ASC ";

		return mDb.rawQuery(sql, new String[]{});
	}
	
	public long createAnthologyTitle(long book, String author, String title) {
		ContentValues initialValues = new ContentValues();
		String[] names = processAuthorName(author);
		String authorId = getAuthorIdOrCreate(names);
		long result;
		int position = fetchAnthologyPositionByBook(book) + 1;
		
		initialValues.put(KEY_BOOK, book);
		initialValues.put(KEY_AUTHOR_ID, authorId);
		initialValues.put(KEY_TITLE, title);
		initialValues.put(KEY_POSITION, position);
		result = mDb.insert(DB_TB_ANTHOLOGY, null, initialValues);
			
		return result;
	}
	
	/**
	 * This function will create a new author in the database
	 * 
	 * @param family_name A string containing the family name
	 * @param given_names A string containing the given names
	 * @return the ID of the author
	 */
	public long createAuthor(String family_name, String given_names) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_FAMILY_NAME, family_name);
		initialValues.put(KEY_GIVEN_NAMES, given_names);
		long result = mDb.insert(DB_TB_AUTHORS, null, initialValues);
		return result;
	}
	
	/**
	 * This function will create a new series in the database
	 * 
	 * @param seriesName 	A string containing the series name
	 * @return the ID of the new series
	 */
	public long createSeries(String seriesName) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_SERIES_NAME, seriesName);
		long result = mDb.insert(DB_TB_SERIES, null, initialValues);
		return result;
	}
	
	/**
	 * Create a new book using the details provided. If the book is
	 * successfully created return the new rowId for that book, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id The ID of the book to insert (this will overwrite the normal autoIncrement)
	 * @param values A ContentValues collection with the columns to be updated. May contain extrat data.
	 *
	 * @return rowId or -1 if failed
	 */
	public long createBook(Bundle values) {
		return createBook(0, values);
	}
	
	/**
	 * Create a new book using the details provided. If the book is
	 * successfully created return the new rowId for that book, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id The ID of the book to insert (this will overwrite the normal autoIncrement)
	 * @param values A ContentValues collection with the columns to be updated. May contain extrat data.
	 *
	 * @return rowId or -1 if failed
	 */
	//public long createBook(long id, String author, String title, String isbn, String publisher, String date_published, float rating, String bookshelf, Boolean read, String series, int pages, String series_num, String notes, String list_price, int anthology, String location, String read_start, String read_end, String format, boolean signed, String description, String genre) {
	public long createBook(long id, Bundle values) {

		// Make sure we have the target table details
		if (mBooksInfo == null)
			mBooksInfo = new TableInfo(DB_TB_BOOKS);

		preprocessOutput(id, values);

		/* We may want to provide default values for these fields:
		 * KEY_RATING, KEY_READ, KEY_NOTES, KEY_LOCATION, KEY_READ_START, KEY_READ_END, KEY_SIGNED
		 */

		// Make sure we have an author
		ArrayList<Author> authors = values.getParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
		if (authors == null || authors.size() == 0)
			throw new IllegalArgumentException();

		ContentValues initialValues = filterValues(values, mBooksInfo);

		if (id > 0) {
			initialValues.put(KEY_ROWID, id);
		}

		long rowId = mDb.insert(DB_TB_BOOKS, null, initialValues);

		String bookshelf = values.getString("bookshelf_text");
		if (bookshelf != null) {
			createBookshelfBooks(rowId, bookshelf);
		}

		createBookAuthors(rowId, authors);
		ArrayList<Series> series = values.getParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY);
		createBookSeries(rowId, series);

		return rowId;
	}
	
	/**
	 * This function will create a new bookshelf in the database
	 * 
	 * @param bookshelf The bookshelf name
	 * @return
	 */
	public long createBookshelf(String bookshelf) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_BOOKSHELF, bookshelf);
		long result = mDb.insert(DB_TB_BOOKSHELF, null, initialValues);
		return result;
	}

	// Statements used by createBookshelfBooks
	private SQLiteStatement mDeleteBookshelfBooksStmt = null;
	private SQLiteStatement mInsertBookshelfBooksStmt = null;

	/**
	 * Create each book/bookshelf combo in the weak entity
	 * 
	 * @param mRowId The book id
	 * @param bookshelf A separated string of bookshelf names
	 */
	public void createBookshelfBooks(long mRowId, String bookshelf) {
		if (mDeleteBookshelfBooksStmt == null) {
			mDeleteBookshelfBooksStmt = compileStatement("Delete from " + DB_TB_BOOK_BOOKSHELF_WEAK + " Where " + KEY_BOOK + " = ?");
		}
		mDeleteBookshelfBooksStmt.bindLong(1, mRowId);
		mDeleteBookshelfBooksStmt.execute();

		if (mInsertBookshelfBooksStmt == null) {
			mInsertBookshelfBooksStmt = compileStatement("Insert Into " 
								+ DB_TB_BOOK_BOOKSHELF_WEAK + "(" + KEY_BOOK + ", " + KEY_BOOKSHELF + ")"
								+ " Values (?,?)");
		}

		//Insert the new ones
		String[] bookshelves = bookshelf.split(BookEditFields.BOOKSHELF_SEPERATOR.trim());
		for (int i = 0; i<bookshelves.length; i++) {
			String name = bookshelves[i].trim();
			if (name.equals("")) {
				continue;
			}
			
			//ContentValues initialValues = new ContentValues();
			long bookshelfId = fetchBookshelfIdByName(name);
			if (bookshelfId == 0) {
				bookshelfId = createBookshelf(name);
			}
			if (bookshelfId == 0)
				bookshelfId = 1;

			try {
				mInsertBookshelfBooksStmt.bindLong(1, mRowId);
				mInsertBookshelfBooksStmt.bindLong(2, bookshelfId);
				mInsertBookshelfBooksStmt.execute();
			} catch (Exception e) {
				Logger.logError(e, "Error assigning a book to a bookshelf.");
			}
		}
	}
	
	/**
	 * This function will create a new loan in the database
	 * 
	 * @param book The id of the book
	 * @param friend A string containing the friend you are loaning to
	 * @return the ID of the loan
	 */
	public long createLoan(Bundle values) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_BOOK, Utils.getAsLong(values,KEY_ROWID));
		initialValues.put(KEY_LOANED_TO, values.getString(KEY_LOANED_TO));
		long result = mDb.insert(DB_TB_LOAN, null, initialValues);
		//Special cleanup step - Delete all loans without books
		this.deleteLoanInvalids();
		return result;
	}
	
	
	
	/**
	 * Update the anthology title in the database
	 * 
	 * @param rowId The rowId of the anthology title 
	 * @param book The id of the book 
	 * @param author The author name
	 * @param title The title of the anthology story
	 * @return true/false on success
	 */
	public boolean updateAnthologyTitle(long rowId, long book, String author, String title) {
		ContentValues args = new ContentValues();
		String[] names = processAuthorName(author);
		long authorId = Long.parseLong(getAuthorIdOrCreate(names));
		
		args.put(KEY_BOOK, book);
		args.put(KEY_AUTHOR_ID, authorId);
		args.put(KEY_TITLE, title);
		boolean success = mDb.update(DB_TB_ANTHOLOGY, args, KEY_ROWID + "=" + rowId, null) > 0;
		purgeAuthors();
		return success;
	}
	
	/**
	 * Move the given title up/down one position
	 * 
	 * @param rowId The rowId of the title 
	 * @param up true if going up, false if going down
	 * @return true/false on success
	 */
	public int updateAnthologyTitlePosition(long rowId, boolean up) {
		Cursor title = fetchAnthologyTitleById(rowId);
		title.moveToFirst();
		int book = title.getInt(title.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOK)); 
		int position = title.getInt(title.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_POSITION)); 
		title.close();

		int max_position = fetchAnthologyPositionByBook(rowId);

		if (position == 1 && up == true) {
			return 0;
		}
		if (position == max_position && up == false) {
			return 0;
		}
		String sql = "";
		String dir = "";
		String opp_dir = "";
		if (up == true) {
			dir = "-1";
			opp_dir = "+1";
		} else {
			dir = "+1";
			opp_dir = "-1";
		}
		sql = "UPDATE " + DB_TB_ANTHOLOGY + " SET " + KEY_POSITION + "=" + KEY_POSITION + opp_dir + " " +
			" WHERE " + KEY_BOOK + "='" + book + "' AND " + KEY_POSITION + "=" + position + dir + " ";
		mDb.execSQL(sql);
		sql = "UPDATE " + DB_TB_ANTHOLOGY + " SET " + KEY_POSITION + "=" + KEY_POSITION + dir + " " +
		" WHERE " + KEY_BOOK + "='" + book + "' AND " + KEY_ROWID + "=" + rowId+ " ";
		mDb.execSQL(sql);
		return position;
	}

	private String getAuthorId(String name) {
		String[] names = processAuthorName(name);
		return getAuthorIdOrCreate(names);
	}

	private SQLiteStatement mGetSeriesIdStmt = null;

	private long getSeriesId(String name) {
		if (mGetSeriesIdStmt == null) {
			mGetSeriesIdStmt = compileStatement("Select " + KEY_ROWID + " From " + DB_TB_SERIES 
								+ " Where Upper(" + KEY_SERIES_NAME + ") = Upper(?)" + COLLATION);
		}
		long id;
		mGetSeriesIdStmt.bindString(1, name);
		try {
			id = mGetSeriesIdStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			id = 0;
		}
		return id;
	}

	private String getSeriesIdOrCreate(String name) {
		long id = getSeriesId(name);
		if (id == 0)
			id = createSeries(name);

		return Long.toString(id);
	}

	// Statements used by getAuthorId
	private SQLiteStatement mGetAuthorIdStmt = null;
	private long getAuthorId(String[] names) {
		if (mGetAuthorIdStmt == null) {
			mGetAuthorIdStmt = compileStatement("Select " + KEY_ROWID + " From " + DB_TB_AUTHORS 
								+ " Where Upper(" + KEY_FAMILY_NAME + ") = Upper(?) " + COLLATION
								+ " And Upper(" + KEY_GIVEN_NAMES + ") = Upper(?)" + COLLATION);
		}
		long id;
		try {
			mGetAuthorIdStmt.bindString(1, names[0]);
			mGetAuthorIdStmt.bindString(2, names[1]);
			id = mGetAuthorIdStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			id = 0;
		}
		return id;
	}

	private String getAuthorIdOrCreate(String[] names) {
		long id = getAuthorId(names);
		if (id == 0)
			id = createAuthor(names[0], names[1]);

		return Long.toString(id);
	}

	public long lookupAuthorId(Author a) {
		return getAuthorId(new String[] {a.familyName, a.givenNames});
	}

	public long lookupSeriesId(Series s) {
		return getSeriesId(s.name);
	}

	/**
	 * Return a Cursor over the list of all authors  in the database for the given book
	 * 
	 * @param long rowId the rowId of the book
	 * @return Cursor over all authors
	 */
	public Cursor fetchAllAuthorsByBook(long rowId) {
		String sql = "SELECT DISTINCT a." + KEY_ROWID + " as " + KEY_ROWID 
			+ ", a." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME
			+ ", a." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES
			+ ", Case When a." + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME
			+ "  Else " + authorFormattedSource("") 
			+ " End as " + KEY_AUTHOR_FORMATTED
			+ ", ba." + KEY_AUTHOR_POSITION
			+ " FROM " + DB_TB_BOOK_AUTHOR + " ba Join " + DB_TB_AUTHORS + " a "
			+ "       On a." + KEY_ROWID + " = ba." + KEY_AUTHOR_ID
			+ " WHERE ba." + KEY_BOOK + "=" + rowId + " "
			+ " ORDER BY ba." + KEY_AUTHOR_POSITION + " Asc, Upper(" + KEY_FAMILY_NAME + ") " + COLLATION + " ASC,"
			+ " Upper(" + KEY_GIVEN_NAMES + ") " + COLLATION + " ASC";
		return mDb.rawQuery(sql, new String[]{});
	}

	ArrayList<Author> getBookAuthorList(long id) {
		ArrayList<Author> authorList = new ArrayList<Author>();
		Cursor authors = null;
		try {
			authors = fetchAllAuthorsByBook(id);
			int count = authors.getCount();

			if (count == 0)
				return authorList;

			int idCol = authors.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			int familyCol = authors.getColumnIndex(CatalogueDBAdapter.KEY_FAMILY_NAME);
			int givenCol = authors.getColumnIndex(CatalogueDBAdapter.KEY_GIVEN_NAMES);

			while (authors.moveToNext()) {
				authorList.add(new Author(authors.getLong(idCol), authors.getString(familyCol), authors.getString(givenCol)));
			}			
		} finally {
			if (authors != null)
				authors.close();
		}
		return authorList;
	}

	ArrayList<Series> getBookSeriesList(long id) {
		ArrayList<Series> seriesList = new ArrayList<Series>();
		Cursor series = null;
		try {
			series = fetchAllSeriesByBook(id);
			int count = series.getCount();

			if (count == 0)
				return seriesList;

			int idCol = series.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			int nameCol = series.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_NAME);
			int numCol = series.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_NUM);

			while (series.moveToNext()) {
				seriesList.add(new Series(series.getLong(idCol), series.getString(nameCol), series.getString(numCol)));
			}			
		} finally {
			if (series != null)
				series.close();
		}
		return seriesList;
	}

	/**
	 * Return a Cursor over the list of all authors  in the database for the given book
	 * 
	 * @param long rowId the rowId of the book
	 * @return Cursor over all authors
	 */
	public Cursor fetchAllSeriesByBook(long rowId) {
		String sql = "SELECT DISTINCT s." + KEY_ROWID + " as " + KEY_ROWID 
			+ ", s." + KEY_SERIES_NAME + " as " + KEY_SERIES_NAME
			+ ", bs." + KEY_SERIES_NUM + " as " + KEY_SERIES_NUM
			+ ", bs." + KEY_SERIES_POSITION + " as " + KEY_SERIES_POSITION
			+ ", " + KEY_SERIES_NAME + "||' ('||" + KEY_SERIES_NUM + "||')' as " + KEY_SERIES_FORMATTED 
			+ " FROM " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s "
			+ "       On s." + KEY_ROWID + " = bs." + KEY_SERIES_ID
			+ " WHERE bs." + KEY_BOOK + "=" + rowId + " "
			+ " ORDER BY bs." + KEY_SERIES_POSITION + ", Upper(s." + KEY_SERIES_NAME + ") " + COLLATION + " ASC";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * Return a ContentValues collection containing only those values from 'source' that match columns in 'dest'.
	 * - Exclude the primary key from the list of columns.
	 * - data will be transformed based on the intended type of the underlying column based on column definition.
	 *  
	 * @param source	Source column data
	 * @param dest		Destination table definition
	 * 
	 * @return New, filtered, collection
	 */
	ContentValues filterValues(Bundle source, TableInfo dest) {
		ContentValues args = new ContentValues();

		Set<String> keys = source.keySet();
		// Create the arguments
		for (String key : keys) {
			// Get column info for this column.
			ColumnInfo c = mBooksInfo.getColumn(key);
			// Check if we actually have a matching column.
			if (c != null) {
				// Never update PK.
				if (!c.isPrimaryKey) {

					Object v = source.get(key);

					// Try to set the appropriate value, but if that fails, just use TEXT...
					try {

						switch(c.typeClass) {

						case TableInfo.CLASS_REAL:
							if (v instanceof Float)
								args.put(c.name, (Float)v);
							else
								args.put(c.name, Float.parseFloat(v.toString()));
							break;

						case TableInfo.CLASS_INTEGER:
							if (v instanceof Boolean) {
								if ((Boolean)v) {
									args.put(c.name, 1);
								} else {
									args.put(c.name, 0);
								}
							} else if (v instanceof Integer) {
								args.put(c.name, (Integer)v);
							} else {
								args.put(c.name, Integer.parseInt(v.toString()));
							}
							break;

						case TableInfo.CLASS_TEXT:
							if (v instanceof String)
								args.put(c.name, (String) v);
							else							
								args.put(c.name, v.toString());
							break;
						}

					} catch (Exception e) {
						args.put(c.name, v.toString());						
					}
				}
			}
		}
		return args;
	}

	/**
	 * Examine the values and make any changes necessary before writing the data.
	 * 
	 * @param values	Collection of field values.
	 */
	private void preprocessOutput(long rowId, Bundle values) {
		String authorId;

		// Handle AUTHOR
		// If present, get the author ID from the author name (it may have changed with a name change)
		if (values.containsKey(KEY_AUTHOR_FORMATTED)) {
			authorId = getAuthorId(values.getString(KEY_AUTHOR_FORMATTED));
			values.putString(KEY_AUTHOR_ID, authorId);
		} else {
			if (values.containsKey(KEY_FAMILY_NAME)) {
				String family = values.getString(KEY_FAMILY_NAME);
				String given;
				if (values.containsKey(KEY_GIVEN_NAMES)) {
					given = values.getString(KEY_GIVEN_NAMES);
				} else {
					given = "";
				}
				authorId = getAuthorIdOrCreate(new String[] {family, given});
				values.putString(KEY_AUTHOR_ID, authorId);
			}
		}
		
		// Handle TITLE; but only for new books
		if (rowId == 0 && values.containsKey(KEY_TITLE)) {
			/* Move "The, A, An" to the end of the string */
			String title = values.getString(KEY_TITLE);
			String newTitle = "";
			String[] title_words = title.split(" ");
			try {
				if (title_words[0].matches("a|A|an|An|the|The")) {
					for (int i = 1; i < title_words.length; i++) {
						if (i != 1) {
							newTitle += " ";
						}
						newTitle += title_words[i];
					}
					newTitle += ", " + title_words[0];
					values.putString(KEY_TITLE, newTitle);
				}
			} catch (Exception e) {
				//do nothing. Title stays the same
			}
		}

	}

	/**
	 * Update the book using the details provided. The book to be updated is
	 * specified using the rowId, and it is altered to use values passed in
	 * 
	 * @param rowId The id of the book in the database
	 * @param values A ContentValues collection with the columns to be updated. May contain extrat data.
	 * @param noPurge Skip doing the 'purge' step; mainy used in batch operations.
	 * 
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updateBook(long rowId, Bundle values, boolean doPurge) {
		boolean success = true;

		// Make sure we have the target table details
		if (mBooksInfo == null)
			mBooksInfo = new TableInfo(DB_TB_BOOKS);

		preprocessOutput(rowId, values);

		ContentValues args = filterValues(values, mBooksInfo);

		// Allow for the possibillity we are just updating series, or author lists.
		if (args.size() > 0)
			success = mDb.update(DB_TB_BOOKS, args, KEY_ROWID + "=" + rowId, null) > 0;
		else
			success = true;

		if (values.containsKey("bookshelf_text")) {
			String bookshelf = values.getString("bookshelf_text");
			if (bookshelf != null) {
				createBookshelfBooks(rowId, bookshelf);
			}			
		}

		if (values.containsKey(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)) {
			ArrayList<Author> authors = values.getParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
			createBookAuthors(rowId, authors);			
		}
		if (values.containsKey(CatalogueDBAdapter.KEY_SERIES_ARRAY)) {
			ArrayList<Series> series = values.getParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY);
			createBookSeries(rowId, series);			
		}

		// Only really skip the purge if a batch update of multiple books is being done.
		if (doPurge) {
			// Delete any unused authors
			purgeAuthors();
			// Delete any unused series
			purgeSeries();			
		}

		return success;
	}

	// Statements used by createBookAuthors
	private SQLiteStatement mDeleteBookAuthorsStmt = null;
	private SQLiteStatement mAddBookAuthorsStmt = null;

	/**
	 * If the passed ContentValues contains KEY_AUTHOR_LIST, parse them
	 * and add the authors.
	 * 
	 * @param bookId		ID of book
	 * @param bookData		Book fields
	 */
	private void createBookAuthors(long bookId, ArrayList<Author> authors) {
		// If we have AUTHOR_DETAILS, same them.
		if (authors != null) {
			if (mDeleteBookAuthorsStmt == null) {
				mDeleteBookAuthorsStmt = compileStatement("Delete from " + DB_TB_BOOK_AUTHOR + " Where " + KEY_BOOK + " = ?");
			}
			if (mAddBookAuthorsStmt == null) {
				mAddBookAuthorsStmt = compileStatement("Insert Into " + DB_TB_BOOK_AUTHOR 
															+ "(" + KEY_BOOK + "," + KEY_AUTHOR_ID + "," + KEY_AUTHOR_POSITION + ")"
															+ "Values(?,?,?)");

			}
			// Need to delete the current records because they may have been reordered and a simple set of updates
			// could result in unique key or index violations.
			mDeleteBookAuthorsStmt.bindLong(1, bookId);
			mDeleteBookAuthorsStmt.execute();

			mAddBookAuthorsStmt.bindLong(1, bookId);

			// Get the authors and turn into a list of names
			Iterator<Author> i = authors.iterator();
			// The list MAY contain duplicates (eg. from Internet lookups of multiple
			// sources), so we track them in a hash table
			Hashtable<String, Boolean> idHash = new Hashtable<String, Boolean>();
			int pos = 0;
			while (i.hasNext()) {
				// Get the name and find/add the author
				Author a = i.next();
				String authorIdStr = getAuthorIdOrCreate(new String[] {a.familyName, a.givenNames});
				long authorId = Long.parseLong(authorIdStr);
				if (!idHash.containsKey(authorIdStr)) {
					idHash.put(authorIdStr, true);
					pos++;
					mAddBookAuthorsStmt.bindLong(2, authorId);
					mAddBookAuthorsStmt.bindLong(3, pos);
					mAddBookAuthorsStmt.executeInsert();
				}
			}
		}
	}

	/**
	 * If the passed ContentValues contains KEY_SERIES_DETAILS, parse them
	 * and add the series.
	 * 
	 * @param bookId		ID of book
	 * @param bookData		Book fields
	 */
	//
	private SQLiteStatement mDeleteBookSeriesStmt = null;
	private SQLiteStatement mAddBookSeriesStmt = null;
	private void createBookSeries(long bookId, ArrayList<Series> series) {
		// If we have SERIES_DETAILS, same them.
		if (series != null) {
			if (mDeleteBookSeriesStmt == null) {
				mDeleteBookSeriesStmt = compileStatement("Delete from " + DB_TB_BOOK_SERIES + " Where " + KEY_BOOK + " = ?");
			}
			if (mAddBookSeriesStmt == null) {
				mAddBookSeriesStmt = compileStatement("Insert Into " + DB_TB_BOOK_SERIES 
															+ "(" + KEY_BOOK + "," + KEY_SERIES_ID + "," + KEY_SERIES_NUM + "," + KEY_SERIES_POSITION + ")"
															+ " Values(?,?,?,?)");

			}

			// Delete the current series
			mDeleteBookSeriesStmt.bindLong(1, bookId);
			mDeleteBookSeriesStmt.execute();

			// Setup the book in the ADD statement
			mAddBookSeriesStmt.bindLong(1, bookId);

			// Get the authors and turn into a list of names
			Iterator<Series> i = series.iterator();
			// The list MAY contain duplicates (eg. from Internet lookups of multiple
			// sources), so we track them in a hash table
			Hashtable<String, Boolean> idHash = new Hashtable<String, Boolean>();
			int pos = 0;
			while (i.hasNext()) {
				// Get the name and find/add the author
				Series s = i.next();
				String seriesName = s.name;
				String seriesIdTxt = getSeriesIdOrCreate(seriesName);
				long seriesId = Long.parseLong(seriesIdTxt);
				String uniqueId = seriesIdTxt + "(" + s.num.trim().toUpperCase() + ")";
				if (!idHash.containsKey(uniqueId)) {
					idHash.put(uniqueId, true);
					pos++;
					mAddBookSeriesStmt.bindLong(2, seriesId);
					mAddBookSeriesStmt.bindString(3, s.num);
					mAddBookSeriesStmt.bindLong(4, pos);
					mAddBookSeriesStmt.execute();
				}
			}
		}		
	}

	/**
	 * Update the bookshelf name
	 * 
	 * @param rowId id of bookshelf to update
	 * @param bookshelf value to set bookshelf name to
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updateBookshelf(long rowId, String bookshelf) {
		boolean success;
		ContentValues args = new ContentValues();
		args.put(KEY_BOOKSHELF, bookshelf);
		success = mDb.update(DB_TB_BOOKSHELF, args, KEY_ROWID + "=" + rowId, null) > 0;
		purgeAuthors();
		return success;
	}

	/**
	 * Update the author names or create a new one or update the passed object ID
	 * 
	 * @param a		Author in question

	 */
	public void syncAuthor(Author a) {
		long id = lookupAuthorId(a);
		// If we have a match, just update the object
		if (id != 0) {
			a.id = id;
			return;
		}

		if (a.id != 0) {
			ContentValues v = new ContentValues();
			v.put(KEY_FAMILY_NAME, a.familyName);
			v.put(KEY_GIVEN_NAMES, a.givenNames);
			mDb.update(DB_TB_AUTHORS, v, KEY_ROWID + " = " + a.id, null);
		} else {
			a.id = createAuthor(a.familyName, a.givenNames);
		}
		return;
	}

	/**
	 * Update or create the passed author.
	 * 
	 * @param a		Author in question

	 */
	public void sendAuthor(Author a) {
		if (a.id == 0) {
			a.id = lookupAuthorId(a);
		}

		if (a.id != 0) {
			ContentValues v = new ContentValues();
			v.put(KEY_FAMILY_NAME, a.familyName);
			v.put(KEY_GIVEN_NAMES, a.givenNames);
			mDb.update(DB_TB_AUTHORS, v, KEY_ROWID + " = " + a.id, null);
		} else {
			a.id = createAuthor(a.familyName, a.givenNames);
		}
		return;
	}

	/**
	 * Refresh the passed author from the database, if present. Used to ensure that
	 * the current record matches the current DB if some other task may have 
	 * changed the author.
	 * 
	 * @param a		Author in question
	 */
	public void refreshAuthor(Author a) {
		if (a.id == 0) {
			// It wasn't a known author; see if it is now. If so, update ID.
			long id = lookupAuthorId(a);
			// If we have a match, just update the object
			if (id != 0)
				a.id = id;
			return;
		} else {
			// It was a known author, see if it still is and update fields.
			Author newA = this.getAuthorById(a.id);
			if (newA != null) {
				a.familyName = newA.familyName;
				a.givenNames = newA.givenNames;
			} else {
				a.id = 0;
			}
		}
	}	

	/**
	 * Update the series name or create a new one or update the passed object ID
	 * 
	 * @param a		Series in question

	 */
	public void syncSeries(Series s) {
		long id = lookupSeriesId(s);
		// If we have a match, just update the object
		if (id != 0) {
			s.id = id;
			return;
		}

		if (s.id != 0) {
			ContentValues v = new ContentValues();
			v.put(KEY_SERIES_NAME, s.name);
			mDb.update(DB_TB_SERIES, v, KEY_ROWID + " = " + s.id, null);
		} else {
			s.id = createSeries(s.name);
		}
		return;
	}

	/**
	 * Update or create the passed series.
	 * 
	 * @param a		Author in question

	 */
	public void sendSeries(Series s) {
		if (s.id == 0) {
			s.id = lookupSeriesId(s);
		}

		if (s.id != 0) {
			ContentValues v = new ContentValues();
			v.put(KEY_SERIES_NAME, s.name);
			mDb.update(DB_TB_SERIES, v, KEY_ROWID + " = " + s.id, null);
		} else {
			s.id = createSeries(s.name);
		}
		return;
	}

	/**
	 * Delete the anthology record with the given rowId (not to be confused with the book rowId
	 * 
	 * @param rowId id of the anthology to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteAnthologyTitle(long rowId) {
		// Find the soon to be deleted title position#
		Cursor anthology = fetchAnthologyTitleById(rowId);
		anthology.moveToFirst();
		int position = anthology.getInt(anthology.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_POSITION));
		int book = anthology.getInt(anthology.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOK));
		anthology.close();

		boolean success;
		// Delete the title
		success = mDb.delete(DB_TB_ANTHOLOGY, KEY_ROWID + "=" + rowId, null) > 0;
		purgeAuthors();
		// Move all titles past the deleted book up one position
		String sql = "UPDATE " + DB_TB_ANTHOLOGY + 
			" SET " + KEY_POSITION + "=" + KEY_POSITION + "-1" +
			" WHERE " + KEY_POSITION + ">" + position + " AND " + KEY_BOOK + "=" + book + "";
		mDb.execSQL(sql);
		return success;
	}

	// Statements for purgeAuthors
	private SQLiteStatement mPurgeBookAuthorsStmt = null;
	private SQLiteStatement mPurgeAuthorsStmt = null;
	/** 
	 * Delete the author with the given rowId
	 * 
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean purgeAuthors() {
		// Delete DB_TB_BOOK_AUTHOR with no books
		if (mPurgeBookAuthorsStmt == null) {
			mPurgeBookAuthorsStmt = compileStatement("Delete from " + DB_TB_BOOK_AUTHOR + " Where " + KEY_BOOK 
									+ " Not In (SELECT DISTINCT " + KEY_ROWID + " FROM " + DB_TB_BOOKS + ") ");
		}

		boolean success;
		try {
			mPurgeBookAuthorsStmt.execute();
			success = true;
		} catch (Exception e) {
			Logger.logError(e, "Failed to purge Book Authors");
			success = false;
		}

		if (mPurgeAuthorsStmt == null) {
			mPurgeAuthorsStmt = compileStatement("Delete from " + DB_TB_AUTHORS + " Where "
					 			+ KEY_ROWID + " Not In (SELECT DISTINCT " + KEY_AUTHOR_ID + " FROM " + DB_TB_BOOK_AUTHOR + ")"
								+ " And " + KEY_ROWID + " Not In (SELECT DISTINCT " + KEY_AUTHOR_ID + " FROM " + DB_TB_ANTHOLOGY + ")");
		}
		
		try {
			mPurgeAuthorsStmt.execute();
			success = success && true;
		} catch (Exception e) {
			Logger.logError(e, "Failed to purge Authors");
			success = false;
		}

		return success;
	}

	// Statements for purgeSeries
	private SQLiteStatement mPurgeBookSeriesStmt = null;
	private SQLiteStatement mPurgeSeriesStmt = null;

	/** 
	 * Delete the series with no related books
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean purgeSeries() {
		if (mPurgeBookSeriesStmt == null) {
			mPurgeBookSeriesStmt = compileStatement("Delete From "+ DB_TB_BOOK_SERIES + " Where " 
									+ KEY_BOOK + " NOT IN (SELECT DISTINCT " + KEY_ROWID + " FROM " + DB_TB_BOOKS + ")");
		}
		boolean success;
		// Delete DB_TB_BOOK_SERIES with no books
		try {
			mPurgeBookSeriesStmt.execute();
			success = true;
		} catch (Exception e) {
			Logger.logError(e, "Failed to purge Book Authors");
			success = false;
		}

		if (mPurgeSeriesStmt == null) {
			mPurgeSeriesStmt = compileStatement("Delete from " + DB_TB_SERIES + " Where "
					+ KEY_ROWID + " NOT IN (SELECT DISTINCT " + KEY_SERIES_ID + " FROM " + DB_TB_BOOK_SERIES + ") ");
		}
		// Delete series entries with no Book_Series
		try {
			mPurgeSeriesStmt.execute();
			success = success && true;
		} catch (Exception e) {
			Logger.logError(e, "Failed to purge Book Authors");
			success = false;
		}
		return success;
	}

	/** 
	 * Delete the passed series
	 * 
	 * @param series 	series to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteSeries(Series series) {
		try {
			if (series.id == 0)
				series.id = lookupSeriesId(series);
			if (series.id == 0)
				return false;
		} catch (NullPointerException e) {
			Logger.logError(e);
			return false;
		}
		
		// Delete DB_TB_BOOK_SERIES for this series
		boolean success1 = mDb.delete(DB_TB_BOOK_SERIES, KEY_SERIES_ID + " = " + series.id, null) > 0;
		
		boolean success2 = false;
		if (success1)
			// Cleanup all series
			success2 = purgeSeries();
		
		return success1 || success2;
	}
	
	/** 
	 * Delete the book with the given rowId
	 * 
	 * @param rowId id of book to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteBook(long rowId) {
		boolean success;
		success = mDb.delete(DB_TB_BOOKS, KEY_ROWID + "=" + rowId, null) > 0;
		purgeAuthors();
		return success;
	}
	
	/** 
	 * Delete the bookshelf with the given rowId
	 * 
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteBookshelf(long rowId) {
		boolean deleteSuccess;
		//String sql = "UPDATE " + DB_TB_BOOKS + " SET " + KEY_BOOKSHELF + "=1 WHERE " + KEY_BOOKSHELF + "='" + rowId + "'";
		//mDb.execSQL(sql);
		deleteSuccess = mDb.delete(DB_TB_BOOK_BOOKSHELF_WEAK, KEY_BOOKSHELF + "=" + rowId, null) > 0;
		deleteSuccess = mDb.delete(DB_TB_BOOKSHELF, KEY_ROWID + "=" + rowId, null) > 0;
		return deleteSuccess;
	}
	
	/** 
	 * Delete the loan with the given rowId
	 * 
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteLoan(long rowId) {
		boolean success;
		success = mDb.delete(DB_TB_LOAN, KEY_BOOK+ "=" + rowId, null) > 0;
		//Special cleanup step - Delete all loans without books
		this.deleteLoanInvalids();
		return success;
	}
	
	/** 
	 * Delete all loan without a book or loanee
	 * 
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteLoanInvalids() {
		boolean success;
		success = mDb.delete(DB_TB_LOAN, "("+KEY_BOOK+ "='' OR " + KEY_BOOK+ "=null OR " + KEY_LOANED_TO + "='' OR " + KEY_LOANED_TO + "=null) ", null) > 0;
		return success;
	}
	
	
	
	
/**************************************************************************************/
	
	
	
	
	
    /*
     * This will return the author based on the ID.
     */
    public Author getAuthorById(long id) {
    	Cursor c = null;
    	try {
        	String sql = "Select " + KEY_FAMILY_NAME + ", " + KEY_GIVEN_NAMES + " From " + DB_TB_AUTHORS 
        				+ " Where " + KEY_ROWID + " = " + id;
            c = mDb.rawQuery(sql, null);
            if (!c.moveToFirst())
            	return null;
            return new Author(id, c.getString(0), c.getString(1)); 
    	} finally {
    		if (c != null)
	            c.close();    		
	    	}
    }
 
    /*
     * This will return the series based on the ID.
     */
    public Series getSeriesById(long id) {
    	Cursor c = null;
    	try {
        	String sql = "Select " + KEY_SERIES_NAME + " From " + DB_TB_SERIES 
        				+ " Where " + KEY_ROWID + " = " + id;
            c = mDb.rawQuery(sql, null);
            if (!c.moveToFirst())
            	return null;
            return new Series(id, c.getString(0), ""); 
    	} finally {
    		if (c != null)
	            c.close();    		
	    	}
    }
 
    private SQLiteStatement mGetAuthorBookCountQuery = null;
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public long getAuthorBookCount(Author a) {
    	if (a.id == 0)
    		a.id = lookupAuthorId(a);
    	if (a.id == 0)
    		return 0;
 
    	if (mGetAuthorBookCountQuery == null) {
        	String sql = "Select Count(" + KEY_BOOK + ") From " + DB_TB_BOOK_AUTHOR + " Where " + KEY_AUTHOR_ID + "=?";
        	mGetAuthorBookCountQuery = compileStatement(sql);
    	}
    	// Be cautious
    	synchronized(mGetAuthorBookCountQuery) {
        	mGetAuthorBookCountQuery.bindLong(1, a.id);
        	return mGetAuthorBookCountQuery.simpleQueryForLong();
    	}

    }
    
    private SQLiteStatement mGetAuthorAnthologyCountQuery = null;
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public long getAuthorAnthologyCount(Author a) {
    	if (a.id == 0)
    		a.id = lookupAuthorId(a);
    	if (a.id == 0)
    		return 0;
 
    	if (mGetAuthorAnthologyCountQuery == null) {
        	String sql = "Select Count(" + KEY_ROWID + ") From " + DB_TB_ANTHOLOGY + " Where " + KEY_AUTHOR_ID + "=?";
        	mGetAuthorAnthologyCountQuery = compileStatement(sql);
    	}
    	// Be cautious
    	synchronized(mGetAuthorAnthologyCountQuery) {
    		mGetAuthorAnthologyCountQuery.bindLong(1, a.id);
        	return mGetAuthorAnthologyCountQuery.simpleQueryForLong();
    	}

    }

    private SQLiteStatement mGetSeriesBookCountQuery = null;
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public long getSeriesBookCount(Series s) {
    	if (s.id == 0)
    		s.id = lookupSeriesId(s);
    	if (s.id == 0)
    		return 0;
 
    	if (mGetSeriesBookCountQuery == null) {
        	String sql = "Select Count(" + KEY_BOOK + ") From " + DB_TB_BOOK_SERIES + " Where " + KEY_SERIES_ID + "=?";
        	mGetSeriesBookCountQuery = compileStatement(sql);
    	}
    	// Be cautious
    	synchronized(mGetSeriesBookCountQuery) {
    		mGetSeriesBookCountQuery.bindLong(1, s.id);
        	return mGetSeriesBookCountQuery.simpleQueryForLong();
    	}

    }
    
    public void globalReplaceAuthor(Author oldAuthor, Author newAuthor) {
		// Create or update the new author
		if (newAuthor.id == 0)
			syncAuthor(newAuthor);
		else
			sendAuthor(newAuthor);

		// Do some basic sanity checks
		if (oldAuthor.id == 0)
			oldAuthor.id = lookupAuthorId(oldAuthor);
		if (oldAuthor.id == 0)
			throw new RuntimeException("Old Author is not defined");

		if (oldAuthor.id == newAuthor.id)
			return;

		try {
			mDb.beginTransaction();
			// First handle anthologies; they have a single author and are easy
			String sql = "Update " + DB_TB_ANTHOLOGY + " set " + KEY_AUTHOR_ID + " = " + newAuthor.id
						+ " Where " + KEY_AUTHOR_ID + " = " + oldAuthor.id;
			mDb.execSQL(sql);

			// Next, update books but prevent duplicate index errors
			sql = "Update " + DB_TB_BOOK_AUTHOR + " Set " + KEY_AUTHOR_ID + " = " + newAuthor.id 
					+ " Where " + KEY_AUTHOR_ID + " = " + oldAuthor.id
					+ " and Not Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba Where "
					+ "                 ba." + KEY_BOOK + " = " + DB_TB_BOOK_AUTHOR + "." + KEY_BOOK
					+ "                 and ba." + KEY_AUTHOR_ID + " = " + newAuthor.id + ")";
			mDb.execSQL(sql);	
			
			// Finally, delete the rows that would have caused duplicates. Be cautious by using the 
			// EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
			// loss if one of the prior statements failed silently.
			sql = "Delete from " + DB_TB_BOOK_AUTHOR + " Where " + KEY_AUTHOR_ID + " = " + oldAuthor.id 
			+ " And Exists(Select NULL From " + DB_TB_BOOK_AUTHOR + " ba Where "
			+ "                 ba." + KEY_BOOK + " = " + DB_TB_BOOK_AUTHOR + "." + KEY_BOOK
			+ "                 and ba." + KEY_AUTHOR_ID + " = " + newAuthor.id + ")";
			mDb.execSQL(sql);

			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

    public void globalReplaceSeries(Series oldSeries, Series newSeries) {
		// Create or update the new author
		if (newSeries.id == 0)
			syncSeries(newSeries);
		else
			sendSeries(newSeries);

		// Do some basic sanity checks
		if (oldSeries.id == 0)
			oldSeries.id = lookupSeriesId(oldSeries);
		if (oldSeries.id == 0)
			throw new RuntimeException("Old Series is not defined");

		if (oldSeries.id == newSeries.id)
			return;

		try {
			mDb.beginTransaction();
			String sql;

			// Update books but prevent duplicate index errors
			sql = "Update " + DB_TB_BOOK_SERIES + " Set " + KEY_SERIES_ID + " = " + newSeries.id 
					+ " Where " + KEY_SERIES_ID + " = " + oldSeries.id
					+ " and Not Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where "
					+ "                 bs." + KEY_BOOK + " = " + DB_TB_BOOK_SERIES + "." + KEY_BOOK
					+ "                 and bs." + KEY_SERIES_ID + " = " + newSeries.id + ")";
			mDb.execSQL(sql);	
			
			// Finally, delete the rows that would have caused duplicates. Be cautious by using the 
			// EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
			// loss if one of the prior statements failed silently.
			sql = "Delete from " + DB_TB_BOOK_SERIES + " Where " + KEY_SERIES_ID + " = " + oldSeries.id 
			+ " and Exists(Select NULL From " + DB_TB_BOOK_SERIES + " bs Where "
			+ "                 bs." + KEY_BOOK + " = " + DB_TB_BOOK_SERIES + "." + KEY_BOOK
			+ "                 and bs." + KEY_SERIES_ID + " = " + newSeries.id + ")";
			mDb.execSQL(sql);

			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

    /**
     * Utility routine to fill an array with the specified column from the passed SQL.
     * 
     * @param sql			SQL to execute
     * @param columnName	Column to fetch
     * 
     * @return				List of values
     */
    private ArrayList<String> fetchArray(String sql, String columnName) {
		ArrayList<String> list = new ArrayList<String>();

		Cursor cursor = mDb.rawQuery(sql, new String[]{});
		try {
			int column = cursor.getColumnIndexOrThrow(columnName);
			while (cursor.moveToNext()) {
				String name = cursor.getString(column);
				list.add(name);
			}
			return list;			
		} finally {
			cursor.close();			
		}    	
    }

    /**
     * Utility routine to build an arrayList of all series names.
     * 
     * @return
     */
	public ArrayList<String> fetchAllSeriesArray() {
    	String sql = "SELECT DISTINCT " + KEY_SERIES_NAME +  
					" FROM " + DB_TB_SERIES + "" +  
					" ORDER BY Upper(" + KEY_SERIES_NAME + ") " + COLLATION;
    	return fetchArray(sql, KEY_SERIES_NAME);
	}

    /**
     * Utility routine to build an arrayList of all author names.
     * 
     * @return
     */
	public ArrayList<String> fetchAllAuthorsArray() {

    	String sql = "SELECT DISTINCT Case When " + KEY_GIVEN_NAMES + " = '' Then " + KEY_FAMILY_NAME +  
    				" Else " + KEY_FAMILY_NAME + "||', '||" + KEY_GIVEN_NAMES +
    				" End as " + KEY_AUTHOR_FORMATTED + 
					" FROM " + DB_TB_AUTHORS + "" +  
					" ORDER BY Upper(" + KEY_AUTHOR_FORMATTED + ") " + COLLATION;
    	return fetchArray(sql, KEY_AUTHOR_FORMATTED);
	}

    public String encodeString(String value) {
    	return value.replace("'", "''");
    }
	
	/**
	 * Column info support. This is useful for auto-building queries from maps that have
	 * more columns than are in the table.
	 * 
	 * @author Grunthos
	 */
	@SuppressWarnings("unused")
	private class ColumnInfo {
		public int position;
		public String name;
		public String typeName;
		public boolean allowNull;
		public boolean isPrimaryKey;
		public String defaultValue;
		public int typeClass;
	}

	/**
	 * Details of a database table.
	 * 
	 * @author Grunthos
	 */
	private class TableInfo {
		private Map<String,ColumnInfo> mColumns;
		private String mName;
		
		public static final int CLASS_INTEGER = 1;
		public static final int CLASS_TEXT = 2;
		public static final int CLASS_REAL = 3;
		
		TableInfo(String tableName) {
			mName = tableName;
			mColumns = describeTable(mName);
		}
		
		public ColumnInfo getColumn(String name) {
			String lcName = name.toLowerCase();
			if (!mColumns.containsKey(lcName))
				return null;
			return mColumns.get(lcName);
		}
		
		/**
		 * Get the column details for the given table.
		 * 
		 * @param tableName	Name of the database table to lookup
		 * 
		 * @return	A collection of ColumnInfo objects.
		 */
		Map<String,ColumnInfo> describeTable(String tableName) {
			String sql = "PRAGMA table_info(" + tableName + ")";
			
			Map<String,ColumnInfo> cols = new Hashtable<String,ColumnInfo>();
			
			Cursor colCsr = mDb.rawQuery(sql, new String[]{});
			try {
				if (colCsr == null)
					throw new IllegalArgumentException();
				
				if (!colCsr.moveToFirst())
					throw new RuntimeException("Unable to get column details");
				
				
				while (true) {
					ColumnInfo col = new ColumnInfo();
					col.position = colCsr.getInt(0);
					col.name = colCsr.getString(1);
					col.typeName = colCsr.getString(2);
					col.allowNull = colCsr.getInt(3) == 0;
					col.defaultValue = colCsr.getString(4);
					col.isPrimaryKey = colCsr.getInt(5) == 1;
					String tName = col.typeName.toLowerCase();
					if (tName.equals("int") || tName.equals("integer")) {
						col.typeClass = CLASS_INTEGER;
					} else if (tName.equals("text")) {
						col.typeClass = CLASS_TEXT;            		
					} else if (tName.equals("float") || tName.equals("real") || tName.equals("double")) {
						col.typeClass = CLASS_REAL;
					} else if (tName.equals("date")) {
						col.typeClass = CLASS_TEXT;
					} else if (tName.equals("boolean")) {
						col.typeClass = CLASS_INTEGER;
					} else {
						throw new RuntimeException("Unknown data type '" + tName + "'");
					}
					
					cols.put(col.name.toLowerCase(),col);
					if (colCsr.isLast())
						break;
					colCsr.moveToNext();
				}
			} finally {
				if (colCsr != null)
					colCsr.close();
			}
			return cols;
		}
	}
	
	void startTransaction() {
		mDb.beginTransaction();
	}
	void endTransaction() {
		mDb.endTransaction();
	}
	void setTransactionSuccessful() {
		mDb.setTransactionSuccessful();
	}
	
	public void analyzeDb() {
		try {
			mDb.execSQL("analyze");    		
		} catch (Exception e) {
			Logger.logError(e, "Analyze failed");
		}
	}
	
	private SQLiteStatement compileStatement(String sql) {
		SQLiteStatement s = mDb.compileStatement(sql);
		mStatements.add(s);
		return s;
	}
}
