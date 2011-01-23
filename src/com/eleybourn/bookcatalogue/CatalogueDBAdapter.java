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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.widget.ImageView;

/**
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the 
 * ability to list all books as well as retrieve or modify a specific book.
 */
public class CatalogueDBAdapter {
	
	/* This is the list of all column names as static variables for reference */
	public static final String KEY_AUTHOR = "author";
	public static final String KEY_TITLE = "title";
	public static final String KEY_ISBN = "isbn";
	public static final String KEY_PUBLISHER = "publisher";
	public static final String KEY_DATE_PUBLISHED = "date_published";
	public static final String KEY_RATING = "rating";
	public static final String KEY_BOOKSHELF = "bookshelf";
	public static final String KEY_READ = "read";
	public static final String KEY_SERIES = "series";
	public static final String KEY_PAGES = "pages";
	public static final String KEY_ROWID = "_id";
	public static final String KEY_FAMILY_NAME = "family_name";
	public static final String KEY_GIVEN_NAMES = "given_names";
	public static final String KEY_SERIES_NUM = "series_num";
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
	public static final String KEY_SERIES_FORMATTED = "series_formatted";
	public static final String KEY_SERIES_NUM_FORMATTED = "series_num_formatted";
	
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;
	
	/* private database variables as static reference */
	private static final String DB_TB_BOOKS = "books";
	private static final String DB_TB_AUTHORS = "authors";
	private static final String DB_TB_BOOKSHELF = "bookshelf";
	private static final String DB_TB_LOAN = "loan";
	private static final String DB_TB_ANTHOLOGY = "anthology";
	private static final String DB_TB_BOOK_BOOKSHELF_WEAK = "book_bookshelf_weak";
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
		KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + 
		KEY_TITLE + " text not null, " +
		KEY_ISBN + " text, " +
		KEY_PUBLISHER + " text, " +
		KEY_DATE_PUBLISHED + " date, " +
		KEY_RATING + " float not null default 0, " +
		KEY_READ + " boolean not null default 'f', " +
		KEY_SERIES + " text, " +
		KEY_PAGES + " int, " +
		KEY_SERIES_NUM + " text, " +
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
		KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + 
		KEY_TITLE + " text not null, " +
		KEY_POSITION + " int" +
		")";
	
	private static final String DATABASE_CREATE_BOOK_BOOKSHELF_WEAK = 
		"create table " + DB_TB_BOOK_BOOKSHELF_WEAK + "(" + 
		KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
		KEY_BOOKSHELF + " integer REFERENCES " + DB_TB_BOOKSHELF + " ON DELETE SET NULL ON UPDATE SET NULL" +
		")";
	
	private static final String DATABASE_CREATE_INDICES = 
		"CREATE INDEX IF NOT EXISTS authors_given_names ON "+DB_TB_AUTHORS+" ("+KEY_GIVEN_NAMES+");" + 
		"CREATE INDEX IF NOT EXISTS authors_family_name ON "+DB_TB_AUTHORS+" ("+KEY_FAMILY_NAME+");" + 
		"CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON "+DB_TB_BOOKSHELF+" ("+KEY_BOOKSHELF+");" + 
		"CREATE INDEX IF NOT EXISTS books_author ON "+DB_TB_BOOKS+" ("+KEY_AUTHOR+");" + 
		"CREATE INDEX IF NOT EXISTS books_title ON "+DB_TB_BOOKS+" ("+KEY_TITLE+");" + 
		"CREATE INDEX IF NOT EXISTS books_isbn ON "+DB_TB_BOOKS+" ("+KEY_ISBN+");" + 
		"CREATE INDEX IF NOT EXISTS books_series ON "+DB_TB_BOOKS+" ("+KEY_SERIES+");" + 
		"CREATE INDEX IF NOT EXISTS books_publisher ON "+DB_TB_BOOKS+" ("+KEY_PUBLISHER+");" + 
		"CREATE INDEX IF NOT EXISTS anthology_book ON "+DB_TB_ANTHOLOGY+" ("+KEY_BOOK+");" + 
		"CREATE INDEX IF NOT EXISTS anthology_author ON "+DB_TB_ANTHOLOGY+" ("+KEY_AUTHOR+");" + 
		"CREATE INDEX IF NOT EXISTS anthology_title ON "+DB_TB_ANTHOLOGY+" ("+KEY_TITLE+");" + 
		"CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON "+DB_TB_LOAN+" ("+KEY_BOOK+");" + 
		"CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON "+DB_TB_BOOK_BOOKSHELF_WEAK+" ("+KEY_BOOK+");" + 
		"CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON "+DB_TB_BOOK_BOOKSHELF_WEAK+" ("+KEY_BOOKSHELF+");" + 
		"CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + KEY_BOOK + ", " + KEY_AUTHOR + ", " + KEY_TITLE + ")";
		;
	
	private static String AUTHOR_FIELDS = "a." + KEY_FAMILY_NAME + " as " + KEY_FAMILY_NAME + ", a." + KEY_GIVEN_NAMES + " as " + KEY_GIVEN_NAMES + ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR_FORMATTED;
	private static String BOOK_FIELDS = "b." + KEY_AUTHOR + " as " + KEY_AUTHOR + ", " +
		"b." + KEY_TITLE + " as " + KEY_TITLE + ", " +
		"b." + KEY_ISBN + " as " + KEY_ISBN + ", " +
		"b." + KEY_PUBLISHER + " as " + KEY_PUBLISHER + ", " +
		"b." + KEY_DATE_PUBLISHED + " as " + KEY_DATE_PUBLISHED + ", " +
		"b." + KEY_RATING + " as " + KEY_RATING + ", " +
		"b." + KEY_READ + " as " + KEY_READ + ", " +
		"b." + KEY_SERIES + " as " + KEY_SERIES + ", " +
		"b." + KEY_PAGES + " as " + KEY_PAGES + ", " +
		"b." + KEY_SERIES_NUM + " as " + KEY_SERIES_NUM + ", " +
		"b." + KEY_NOTES + " as " + KEY_NOTES + ", " +
		"b." + KEY_LIST_PRICE + " as " + KEY_LIST_PRICE + ", " +
		"b." + KEY_ANTHOLOGY + " as " + KEY_ANTHOLOGY + ", " +
		"b." + KEY_LOCATION + " as " + KEY_LOCATION + ", " +
		"b." + KEY_READ_START + " as " + KEY_READ_START + ", " +
		"b." + KEY_READ_END + " as " + KEY_READ_END + ", " +
		"b." + KEY_FORMAT + " as " + KEY_FORMAT + ", " +
		"b." + KEY_SIGNED + " as " + KEY_SIGNED + ", " + 
		"b." + KEY_DESCRIPTION + " as " + KEY_DESCRIPTION + ", " + 
		"b." + KEY_GENRE  + " as " + KEY_GENRE + ", " + 
		"CASE WHEN " + KEY_SERIES + "='' THEN '' ELSE b." + KEY_SERIES + " || CASE WHEN " + KEY_SERIES_NUM + "='' THEN '' ELSE ' #' || b." + KEY_SERIES_NUM + " END END AS " + KEY_SERIES_FORMATTED;
	private static String BOOKSHELF_TABLES = DB_TB_BOOKS + " b LEFT OUTER JOIN " + DB_TB_BOOK_BOOKSHELF_WEAK + " w ON (b." + KEY_ROWID + "=w." + KEY_BOOK + ") LEFT OUTER JOIN " + DB_TB_BOOKSHELF + " bs ON (bs." + KEY_ROWID + "=w." + KEY_BOOKSHELF + ") ";
	
	
	private final Context mCtx;
	public static final int DATABASE_VERSION = 50;
	
	/**
	 * This is a specific version of the SQLiteOpenHelper class. It handles onCreate and onUpgrade events
	 * 
	 * @author evan
	 */
	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, BookCatalogue.DATABASE_NAME, null, DATABASE_VERSION);
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
			db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
			db.execSQL(DATABASE_CREATE_INDICES);
			new File(Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION + "/").mkdirs();
			try {
				new File(Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION + "/.nomedia").createNewFile();
			} catch (IOException e) {
				//error
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
			//Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", existing data will be saved");
			int curVersion = oldVersion;
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
				db.execSQL(DATABASE_CREATE_INDICES);
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
					//do nothing
				}
				try {
					db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_NOTES + " = ''");
				} catch (Exception e) {
					//do nothing
				}
				try {
					db.execSQL(DATABASE_CREATE_LOAN);
				} catch (Exception e) {
					//do nothing
				}
				try {
					db.execSQL(DATABASE_CREATE_INDICES);
				} catch (Exception e) {
					//do nothing
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
					//do nothing
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
					//do nothing
				}
				try {
					db.execSQL(DATABASE_CREATE_INDICES);
				} catch (Exception e) {
					//do nothing
				}
				try {
					db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO);
				} catch (Exception e) {
					//do nothing
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
					//do nothing
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
					//do nothing
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
					new File(Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION + "/.nomedia").createNewFile();
				} catch (Exception e) {
					//error
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
					db.execSQL("DROP TABLE tmp2");
					db.execSQL("DROP TABLE tmp3");
				} catch (Exception e) {
					//do nothing
				}
				
				try {
					db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
					db.execSQL(DATABASE_CREATE_INDICES);
					db.execSQL("INSERT INTO " + DB_TB_BOOK_BOOKSHELF_WEAK + " (" + KEY_BOOK + ", " + KEY_BOOKSHELF + ") SELECT " + KEY_ROWID + ", " + KEY_BOOKSHELF + " FROM " + DB_TB_BOOKS + "");
					db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + KEY_AUTHOR + ", " + KEY_TITLE + ", " + KEY_ISBN + ", " + KEY_PUBLISHER + ", " + 
						KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ + ", " + KEY_SERIES + ", " + KEY_PAGES + ", " + KEY_SERIES_NUM + ", " + KEY_NOTES + ", " + 
						KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY + ", " + KEY_LOCATION + ", " + KEY_READ_START + ", " + KEY_READ_END + ", " + OLD_KEY_AUDIOBOOK + ", " + 
						KEY_SIGNED + " FROM " + DB_TB_BOOKS);
					db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + KEY_BOOK + ", " + KEY_LOANED_TO + " FROM " + DB_TB_LOAN );
					db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + KEY_BOOK + ", " + KEY_AUTHOR + ", " + KEY_TITLE + ", " + KEY_POSITION + " FROM " + DB_TB_ANTHOLOGY);
					
					db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
					db.execSQL("DROP TABLE " + DB_TB_LOAN);
					db.execSQL("DROP TABLE " + DB_TB_BOOKS);
					
					db.execSQL(DATABASE_CREATE_BOOKS);
					db.execSQL(DATABASE_CREATE_LOAN);
					db.execSQL(DATABASE_CREATE_ANTHOLOGY);
					
					db.execSQL("INSERT INTO " + DB_TB_BOOKS + " SELECT * FROM tmp1");
					db.execSQL("INSERT INTO " + DB_TB_LOAN + " SELECT * FROM tmp2");
					db.execSQL("INSERT INTO " + DB_TB_ANTHOLOGY + " SELECT * FROM tmp3");
					
					db.execSQL("DROP TABLE tmp1");
					db.execSQL("DROP TABLE tmp2");
					db.execSQL("DROP TABLE tmp3");
				} catch (Exception e) {
					//do nothing
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
						" WHERE a." + KEY_BOOK + "=b." + KEY_BOOK + " AND a." + KEY_AUTHOR + "=b." + KEY_AUTHOR + " " +
						" AND a." + KEY_TITLE + "=b." + KEY_TITLE + " AND a." + KEY_ROWID + " > b." + KEY_ROWID + "" +
						")");
				db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + KEY_BOOK + ", " + KEY_AUTHOR + ", " + KEY_TITLE + ")");
			}
			if (curVersion == 44) {
				//do nothing
				curVersion++;
				
				try {
					db.execSQL("DROP TABLE tmp1");
					db.execSQL("DROP TABLE tmp2");
					db.execSQL("DROP TABLE tmp3");
				} catch (Exception e) {
					//do nothing
				}
				
				db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + KEY_AUTHOR + ", " + KEY_TITLE + ", " + KEY_ISBN + ", " + KEY_PUBLISHER + ", " + 
						KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ + ", " + KEY_SERIES + ", " + KEY_PAGES + ", " + KEY_SERIES_NUM + ", " + KEY_NOTES + ", " + 
						KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY + ", " + KEY_LOCATION + ", " + KEY_READ_START + ", " + KEY_READ_END + ", " +
						"CASE WHEN " + OLD_KEY_AUDIOBOOK + "='t' THEN 'Audiobook' ELSE 'Paperback' END AS " + OLD_KEY_AUDIOBOOK + ", " + 
						KEY_SIGNED + " FROM " + DB_TB_BOOKS);
				db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + KEY_BOOK + ", " + KEY_LOANED_TO + " FROM " + DB_TB_LOAN );
				db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + KEY_BOOK + ", " + KEY_AUTHOR + ", " + KEY_TITLE + ", " + KEY_POSITION + " FROM " + DB_TB_ANTHOLOGY);
				db.execSQL("CREATE TABLE tmp4 AS SELECT " + KEY_BOOK + ", " + KEY_BOOKSHELF+ " FROM " + DB_TB_BOOK_BOOKSHELF_WEAK);
				
				db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
				db.execSQL("DROP TABLE " + DB_TB_LOAN);
				db.execSQL("DROP TABLE " + DB_TB_BOOKS);
				db.execSQL("DROP TABLE " + DB_TB_BOOK_BOOKSHELF_WEAK);
				
				String TMP_DATABASE_CREATE_BOOKS =
					"create table " + DB_TB_BOOKS + 
					" (_id integer primary key autoincrement, " +
					KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + 
					KEY_TITLE + " text not null, " +
					KEY_ISBN + " text, " +
					KEY_PUBLISHER + " text, " +
					KEY_DATE_PUBLISHED + " date, " +
					KEY_RATING + " float not null default 0, " +
					KEY_READ + " boolean not null default 'f', " +
					KEY_SERIES + " text, " +
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
				
				db.execSQL(DATABASE_CREATE_INDICES);
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
				db.execSQL(DATABASE_CREATE_INDICES);
			}
			if (curVersion == 49) {
				curVersion++;
				message += "New in v3.2\n\n";
				message += "* Books can now be automatically added by searching for the author name and book title\n\n";
				message += "* Updating thumbnails, genre and description fields will also search by author name and title is the isbn does not exist\n\n";
				message += "* Expand/Collapse all bug fixed\n\n";
				message += "* The search query will be shown at the top of all search screens\n\n";
			}
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
		new File(Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION + "/").mkdirs();
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}
	
	/**
	 * Generic function to close the database
	 */
	public void close() {
		try {
			mDbHelper.close();
		} catch (Exception e) {
			//do nothing - already closed
		}
	}
	
	/**
	 * return the thumbnail (as a File object) for the given id
	 * 
	 * @param id The id of the book
	 * @return The File object
	 */
	public static File fetchThumbnail(long id) {
		String filename = "";
		File file = null;
		if (id == 0) {
			filename = Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION + "/tmp.png";
			file = new File(filename);
		} else {
			filename = Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION + "/" + id + ".jpg";
			file = new File(filename);
			if (!file.exists()) {
				filename = Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION + "/" + id + ".png";
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
		File file = fetchThumbnail(id);
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
		File file = fetchThumbnail(id);
		if (!file.exists()) {
	    	if (destView != null)
				destView.setImageResource(android.R.drawable.ic_menu_help);
			return null;
		}

		Bitmap bm = null;					// resultant Bitmap (which we will return) 
		String filename = file.getPath();	// Full file spec

		// Read the file to get file size
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inJustDecodeBounds = true;
	    BitmapFactory.decodeFile( filename, opt );

	    // If no size info, assume file bad and set the 'alert' icon
	    if ( opt.outHeight <= 0 || opt.outWidth <= 0 ) {
	    	if (destView != null)
	    		destView.setImageResource(android.R.drawable.ic_dialog_alert);
	    	return null;
	    }

	    // Next time we don't just want the bounds, we want the file
	    opt.inJustDecodeBounds = false;

	    // Work out how to scale the file to fit in required bbox
	    float widthRatio = (float)maxWidth / opt.outWidth; 
	    float heightRatio = (float)maxHeight / opt.outHeight;

	    // Work out scale so that it fit exactly
	    float ratio = widthRatio < heightRatio ? widthRatio : heightRatio;

	    // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
		// specify, so we just work with powers of 2.
	    int idealSampleSize = (int)Math.ceil(1/ratio); // This is the sample size we want to use
	    // Get the nearest *bigger* power of 2.
	    int samplePow2 = (int)Math.ceil(Math.log(idealSampleSize)/Math.log(2));

    	if (exact) {
    		// Create one bigger than needed and scale it; this is an attempt to improve quality.
		    opt.inSampleSize = samplePow2 / 2;
		    if (opt.inSampleSize < 1)
		    	opt.inSampleSize = 1;

		    bm = BitmapFactory.decodeFile( filename, opt );
		    android.graphics.Matrix matrix = new android.graphics.Matrix();
		    // Fixup ratio based on new sample size and scale it.
		    ratio = ratio / (1.0f / opt.inSampleSize);
		    matrix.postScale(ratio, ratio);
		    bm = Bitmap.createBitmap(bm, 0, 0, opt.outWidth, opt.outHeight, matrix, true); 
    	} else {
    		// Use a scale that will make image *no larger than* the desired size
    		if (ratio < 1.0f)
			    opt.inSampleSize = samplePow2;
		    bm = BitmapFactory.decodeFile( filename, opt );
    	}

    	// Set ImageView and return bitmap
    	if (destView != null)
		    destView.setImageBitmap(bm);
	 
	    return bm;
	}

	/**
	 * This will return the parsed author name based on a String. 
	 * The name can be in either "family, given" or "given family" format.
	 *
	 * @param name a String containing the name e.g. "Isaac Asimov" or "Asimov, Isaac"
	 * @return a String array containing the family and given names. e.g. ['Asimov', 'Isaac']
	 */
	public String[] processAuthorName(String name) {
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
		String sql = "SELECT count(*) as count FROM " + DB_TB_BOOKS + " b ";
		Cursor count = mDb.rawQuery(sql, new String[]{});
		count.moveToNext();
		int result = count.getInt(0);
		count.close();
		return result;
	}
	
	/**
	 * Return the number of books
	 * 
	 * @param string bookshelf the bookshelf the search within
	 * @return int The number of books
	 */
	public int countBooks(String bookshelf) {
		if (bookshelf.equals("All Books")) {
			return countBooks();
		}
		String sql = "SELECT count(DISTINCT b._id) as count " + 
			" FROM " + BOOKSHELF_TABLES +
			" WHERE bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		Cursor count = mDb.rawQuery(sql, new String[]{});
		count.moveToNext();
		int result = count.getInt(0);
		count.close();
		return result;
	}
	
	/**
	 * A complete export of all tables (flattened) in the database 
	 * 
	 * @return Cursor over all books, authors, etc
	 */
	public Cursor exportBooks() {
		String sql = "SELECT DISTINCT b." + KEY_ROWID + " as " + KEY_ROWID + ", " +
				AUTHOR_FIELDS + ", " + 
				BOOK_FIELDS + ", " +
				"l." + KEY_LOANED_TO + " as " + KEY_LOANED_TO + " " +  
			" FROM " + DB_TB_BOOKS + " b, " + DB_TB_AUTHORS + " a" +
				" LEFT OUTER JOIN " + DB_TB_LOAN +" l ON (l." + KEY_BOOK + "=b." + KEY_ROWID + ") " +
			" WHERE a._id=b." + KEY_AUTHOR + 
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
		String sql = "SELECT DISTINCT a._id as " + KEY_ROWID + ", " + AUTHOR_FIELDS + 
			" FROM " + DB_TB_AUTHORS + " a, " + DB_TB_BOOKS + " b " +
			" WHERE b." + KEY_AUTHOR + "=a." + KEY_ROWID + "" + 
			" ORDER BY lower(" + KEY_FAMILY_NAME + "), lower(" + KEY_GIVEN_NAMES + ")";
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
		}
		return returnable;
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthors(String bookshelf) {
		if (bookshelf.equals("All Books")) {
			return fetchAllAuthors();
		}
		String sql = "SELECT DISTINCT a._id as " + KEY_ROWID + ", " + AUTHOR_FIELDS +
			" FROM " + BOOKSHELF_TABLES + ", " + DB_TB_AUTHORS + " a " + 
			" WHERE a." + KEY_ROWID + "=b." + KEY_AUTHOR + " AND " +
				"bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "' " + 
			" ORDER BY lower(" + KEY_FAMILY_NAME + "), lower(" + KEY_GIVEN_NAMES + ")";
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
		}
		return returnable;
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllAuthorsIgnoreBooks() {
		String sql = "SELECT DISTINCT a._id as " + KEY_ROWID + ", " + AUTHOR_FIELDS + 
			" FROM " + DB_TB_AUTHORS + " a " +
			" ORDER BY lower(" + KEY_FAMILY_NAME + "), lower(" + KEY_GIVEN_NAMES + ")";
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
		}
		return returnable;
	}
	
	/**
	 * Return a list of all the first characters for book titles in the database
	 * 
	 * @param order What order to return the books
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public Cursor fetchAllBookChars(String order, String bookshelf) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT substr(b." + KEY_TITLE + ", 1, 1) as " + KEY_ROWID + " " +
			" FROM " + BOOKSHELF_TABLES + 
			" WHERE 1=1 " + where + 
			" ORDER BY " + KEY_ROWID + "";
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
		}
		returnable.moveToFirst();
		return returnable;
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooks(String order) {
		return fetchAllBooks(order, "All Books", ""); 
	}
	
	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooks(String order, String bookshelf) {
		return fetchAllBooks(order, bookshelf, ""); 
	}
	
	/**
	 * Return a list of all books in the database
	 * 
	 * @param order What order to return the books
	 * @param bookshelf Which bookshelf is it in. Can be "All Books"
	 * @return Cursor over all Books
	 */
	public Cursor fetchAllBooks(String order, String bookshelf, String where) {
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT b." + KEY_ROWID + " as " + KEY_ROWID + ", " +
				AUTHOR_FIELDS + ", " + 
				BOOK_FIELDS + 
			" FROM " + BOOKSHELF_TABLES + ", " + DB_TB_AUTHORS + " a " + 
			" WHERE a." + KEY_ROWID + "=b." + KEY_AUTHOR + where + 
			" ORDER BY " + order + "";
		Cursor returnable = null;
		try {
			returnable = mDb.rawQuery(sql, new String[]{});
		} catch (IllegalStateException e) {
			open();
			returnable = mDb.rawQuery(sql, new String[]{});
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
	public Cursor fetchAllBooksByAuthor(int author, String bookshelf) {
		String where = " AND a._id=" + author;
		String order = "b." + KEY_SERIES + ", substr('0000000000' || b." + KEY_SERIES_NUM + ", -10, 10), lower(b." + KEY_TITLE + ") ASC";
		return fetchAllBooks(order, bookshelf, where);
	}
	
	/**
	 * This will return a list of all books by a given first title character
	 * 
	 * @param char The first title character
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByChar(String first_char, String bookshelf) {
		String where = " AND substr(b." + KEY_TITLE + ", 1, 1)='"+first_char+"'";
		String order = "lower(b." + KEY_TITLE + ") ASC";
		return fetchAllBooks(order, bookshelf, where);
	}
	
	/**
	 * Return a Cursor over the list of all books in the database by genre
	 * 
	 * @param author The genre name to search by
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByGenre(String genre, String bookshelf) {
		String where = " AND ";
		if (genre.equals(META_EMPTY_GENRE)) {
			where += "(b." + KEY_GENRE + "='' OR b." + KEY_GENRE + " IS NULL)";
		} else {
			genre = encodeString(genre);
			where += "b." + KEY_GENRE + "='" + genre + "'";
		}
		String order = CatalogueDBAdapter.KEY_TITLE + ", " + CatalogueDBAdapter.KEY_FAMILY_NAME;
		return fetchAllBooks(order, bookshelf, where);
	}
	
	/**
	 * This will return a list of all books loaned to a given person
	 * 
	 * @param loaned_to The person who had books loaned to
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByLoan(String loaned_to) {
		String sql = "SELECT DISTINCT b." + KEY_ROWID + " as " + KEY_ROWID + ", " +
				AUTHOR_FIELDS + ", " + 
				BOOK_FIELDS + 
			" FROM " + BOOKSHELF_TABLES + ", " + DB_TB_LOAN + " l, " + DB_TB_AUTHORS + " a " + 
			" WHERE l." + KEY_BOOK + "=b." + KEY_ROWID + " AND " + 
				"a." + KEY_ROWID + "=b." + KEY_AUTHOR + " AND " +
				"l." + KEY_LOANED_TO + "='" + encodeString(loaned_to) + "'" + 
			" ORDER BY lower(b." + KEY_TITLE + ") ASC";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all books either read or unread
	 * 
	 * @param read "Read" or "Unread"
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksByRead(String read, String bookshelf) {
		String where = "";
		if (read.equals("Read")) {
			where += " AND b." + KEY_READ + "=1";
		} else {
			where += " AND b." + KEY_READ + "!=1";
		}
		String order = "lower(b." + KEY_TITLE + ") ASC";
		return fetchAllBooks(order, bookshelf, where);
	}
	
	/**
	 * Return a Cursor over the list of all books in the database by series
	 * 
	 * @param author The series name to search by
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all books
	 */
	public Cursor fetchAllBooksBySeries(String series, String bookshelf) {
		String where = " AND ";
		if (series.equals(META_EMPTY_SERIES)) {
			where += "(b." + KEY_SERIES + "='' OR b." + KEY_SERIES + " IS NULL)";
		} else {
			series = encodeString(series);
			where += "b." + KEY_SERIES + "='" + series + "'";
		}
		String order = "substr('0000000000' || b." + KEY_SERIES_NUM + ", -10, 10), lower(b." + KEY_TITLE + ") ASC";
		return fetchAllBooks(order, bookshelf, where);
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
			" ORDER BY bs." + KEY_BOOKSHELF + "";
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
			" ORDER BY bs." + KEY_BOOKSHELF + "";
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
			" ORDER BY bs." + KEY_BOOKSHELF + "";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * This will return a list of all genres within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllGenres(String bookshelf) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT b." + KEY_GENRE + " as " + KEY_ROWID + 
		" FROM " + BOOKSHELF_TABLES + 
		" WHERE b." + KEY_GENRE + "!= '' " + where + 
		" UNION SELECT \"" + META_EMPTY_GENRE + "\" as " + KEY_ROWID +
		" ORDER BY b." + KEY_GENRE + "";
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
		" ORDER BY l." + KEY_LOANED_TO + "";
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
			" ORDER BY " + KEY_LOCATION + "";
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
			" ORDER BY " + KEY_PUBLISHER + "";
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
	
	/**
	 * This will return a list of all series within the given bookshelf
	 * 
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return Cursor over all series
	 */
	public Cursor fetchAllSeries(String bookshelf, boolean include_blank) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT b." + KEY_SERIES + " as " + KEY_ROWID + 
		" FROM " + BOOKSHELF_TABLES + 
		" WHERE b." + KEY_SERIES + "!= '' " + where + 
		" UNION SELECT \"" + META_EMPTY_SERIES + "\" as " + KEY_ROWID +
		" ORDER BY b." + KEY_SERIES + "";
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
		String sql = "SELECT an." + KEY_ROWID + " as " + KEY_ROWID + ", an." + KEY_TITLE + " as " + KEY_TITLE + ", an." + KEY_POSITION + " as " + KEY_POSITION + ", au." + KEY_FAMILY_NAME + " || ', ' || au." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR + ", an." + KEY_BOOK + " as " + KEY_BOOK +  
			" FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au " +
			" WHERE an." + KEY_AUTHOR + "=au." + KEY_ROWID + " AND an." + KEY_BOOK + "='" + rowId + "'" +
			" ORDER BY an." + KEY_POSITION + "";
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
		String sql = "SELECT an." + KEY_ROWID + " as " + KEY_ROWID + ", an." + KEY_TITLE + " as " + KEY_TITLE + ", an." + KEY_POSITION + " as " + KEY_POSITION + 
			", au." + KEY_FAMILY_NAME + " || ', ' || au." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR + ", an." + KEY_BOOK + " as " + KEY_BOOK +
			" FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au " +
			" WHERE an." + KEY_AUTHOR + "=au." + KEY_ROWID + " AND an." + KEY_ROWID + "='" + rowId + "'";
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
	public int fetchAuthorPositionByName(String name, String bookshelf) {
		String where = "";
		String[] names = processAuthorName(name);
		if (bookshelf.equals("All Books")) {
			// do nothing
		} else {
			where += " AND a." + KEY_ROWID + " IN " +
				"(SELECT " + KEY_AUTHOR + 
				" FROM " + BOOKSHELF_TABLES + 
				" WHERE bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "') ";
		}
		String sql = "SELECT count(*) as count FROM " + DB_TB_AUTHORS + " a " +
			"WHERE (a." + KEY_FAMILY_NAME + "<'" + encodeString(names[0]) + "' " +
			"OR (a." + KEY_FAMILY_NAME + "='" + encodeString(names[0]) + "' AND a." + KEY_GIVEN_NAMES + "<'" + encodeString(names[1]) + "'))" + 
			where + 
			" ORDER BY a." + KEY_FAMILY_NAME + ", a." + KEY_GIVEN_NAMES;
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
		String where = " AND b." + KEY_ROWID + "=" + rowId;
		String order = "b." + KEY_ROWID;
		return fetchAllBooks(order, "All Books", where);
	}
	
	/**
	 * 
	 * @param isbn The isbn to search by
	 * @return Cursor of the book
	 */
	public Cursor fetchBookByISBN(String isbn) {
		String where = " AND b." + KEY_ISBN + "='" + encodeString(isbn) + "'";
		String order = "lower(b." + KEY_TITLE + ")";
		return fetchAllBooks(order, "All Books", where);
	}
	
	/**
	 * 
	 * @param isbn The isbn to search by
	 * @return Cursor of the book
	 */
	public Cursor fetchBookByISBNOrCombo(String isbn, String family, String given, String title) {
		String where = " AND ((b." + KEY_ISBN + "='" + encodeString(isbn) + "' AND b." + KEY_ISBN + "!='') OR " +
				"(b." + KEY_TITLE + "='" + encodeString(title) + "' AND a." + KEY_FAMILY_NAME + "='" + encodeString(family) + "' AND a." + KEY_GIVEN_NAMES + "='" + encodeString(given) + "'))";
		String order = "lower(b." + KEY_TITLE + ")";
		return fetchAllBooks(order, "All Books", where);
	}
	
	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param title The book title to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchBookPositionByTitle(String title, String bookshelf) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT count(*) as count " +
			"FROM " + BOOKSHELF_TABLES +
			"WHERE b.title < '" + encodeString(title) + "'" + where;
		Cursor results = mDb.rawQuery(sql, null);
		int pos = getIntValue(results, 0);
		return pos;
	}

	/**
	 * Return a Cursor positioned at the bookshelf that matches the given rowId
	 * 
	 * @param rowId id of bookshelf to retrieve
	 * @return Cursor positioned to matching note, if found
	 * @throws SQLException if note could not be found/retrieved
	 */
	public Cursor fetchBookshelf(long rowId) throws SQLException {
		String sql = "SELECT bs." + KEY_ROWID + ", bs." + KEY_BOOKSHELF + 
		" FROM " + DB_TB_BOOKSHELF + " bs " +  
		" WHERE bs." + KEY_ROWID + "=" + rowId + "";
		Cursor mCursor = mDb.rawQuery(sql, new String[]{});
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}
	
	/**
	 * This will return the author id based on the name. 
	 * The name can be in either "family, given" or "given family" format.
	 * 
	 * @param name The bookshelf name to search for
	 * @return A cursor containing all bookshelves with the given name
	 */
	public Cursor fetchBookshelfByName(String name) {
		String sql = "";
		sql = KEY_BOOKSHELF + "='" + encodeString(name) + "'";
		return mDb.query(DB_TB_BOOKSHELF, new String[] {"_id", KEY_BOOKSHELF}, sql, null, null, null, null);
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
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT count(DISTINCT b." + KEY_GENRE + ") as count " +
			"FROM " + BOOKSHELF_TABLES +
			"WHERE b." + KEY_GENRE + " < '" + encodeString(genre) + "'" + where;
		Cursor results = mDb.rawQuery(sql, null);
		int pos = (getIntValue(results, 0))-1;
		return pos;
	}
	
	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param title The book title to search for
	 * @param bookshelf The bookshelf to search within. Can be the string "All Books"
	 * @return The position of the book
	 */
	public int fetchSeriesPositionBySeries(String series, String bookshelf) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT count(DISTINCT b." + KEY_SERIES + ") as count " +
			"FROM " + BOOKSHELF_TABLES +
			"WHERE b." + KEY_SERIES + " < '" + encodeString(series) + "'" + where;
		Cursor results = mDb.rawQuery(sql, null);
		int pos = (getIntValue(results, 0))-1;
		return pos;
	}
	
	/**
	 * Return a Cursor over the author in the database which meet the provided search criteria
	 * 
	 * @param query The search query
	 * @param bookshelf The bookshelf to search within
	 * @return Cursor over all authors
	 */
	public Cursor searchAuthors(String query, String bookshelf) {
		String where = "";
		query = encodeString(query);
		if (bookshelf.equals("All Books")) {
			// do nothing
		} else {
			where += " AND a." + KEY_ROWID + " IN (SELECT " + KEY_AUTHOR + " " +
					"FROM " + BOOKSHELF_TABLES + " " +
					"WHERE bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "') ";
		}
		String sql = "SELECT a._id as " + KEY_ROWID + ", " + 
			AUTHOR_FIELDS + 
			" FROM " + DB_TB_AUTHORS + " a" + " " +
			"WHERE (a." + KEY_FAMILY_NAME + " LIKE '%" + query + "%' OR " +
				"a." + KEY_GIVEN_NAMES + " LIKE '%" + query + "%' OR " +
				"a." + KEY_ROWID + " IN (SELECT " + KEY_AUTHOR + " FROM " + DB_TB_BOOKS + " b " +
					"WHERE (b." + KEY_TITLE + " LIKE '%" + query + "%' OR " +
					" b." + KEY_ISBN + " LIKE '%" + query + "%' OR " +
					" b." + KEY_PUBLISHER + " LIKE '%" + query + "%' OR " +
					" b." + KEY_SERIES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_NOTES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_LOCATION + " LIKE '%" + query + "%')) )" + 
				where + 
			"ORDER BY " + KEY_FAMILY_NAME + ", " + KEY_GIVEN_NAMES + "";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	/**
	 * @see searchBooks
	 */
	public Cursor searchBooks(String query, String order, String bookshelf) {
		return searchBooks(query, order, bookshelf, "");
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
	public Cursor searchBooks(String query, String order, String bookshelf, String where) {
		query = encodeString(query);
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT b." + KEY_ROWID + " AS " + KEY_ROWID + ", " +
				AUTHOR_FIELDS + ", " + 
				BOOK_FIELDS + 
			" FROM " + BOOKSHELF_TABLES + ", " + DB_TB_AUTHORS + " a" + 
			" WHERE a._id=b." + KEY_AUTHOR + " AND " +
				"(a." + KEY_FAMILY_NAME + " LIKE '%" + query + "%' OR " +
				" a." + KEY_GIVEN_NAMES + " LIKE '%" + query + "%' OR " +
				" b." + KEY_TITLE + " LIKE '%" + query + "%' OR " +
				" b." + KEY_ISBN + " LIKE '%" + query + "%' OR " +
				" b." + KEY_PUBLISHER + " LIKE '%" + query + "%' OR " +
				" b." + KEY_SERIES + " LIKE '%" + query + "%' OR " +
				" b." + KEY_NOTES + " LIKE '%" + query + "%' OR " +
				" b." + KEY_LOCATION + " LIKE '%" + query + "%')" + 
				where + 
			" ORDER BY " + order + "";
		return mDb.rawQuery(sql, new String[]{});
	}

	public Cursor searchBooksByChar(String query, String first_char, String bookshelf) {
		String order = CatalogueDBAdapter.KEY_TITLE + ", " + CatalogueDBAdapter.KEY_FAMILY_NAME;
		return searchBooks(query, order, bookshelf, " AND substr(b." + KEY_TITLE + ", 1, 1)='" + first_char + "'");
	}

	public Cursor searchBooksByGenre(String query, String genre, String bookshelf) {
		String order = CatalogueDBAdapter.KEY_TITLE + ", " + CatalogueDBAdapter.KEY_FAMILY_NAME;
		return searchBooks(query, order, bookshelf, " AND " + KEY_GENRE + "='" + genre + "'");
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
	public Cursor searchBooksChars(String query, String order, String bookshelf) {
		String where = "";
		query = encodeString(query);
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT substr(b." + KEY_TITLE + ", 1, 1) AS " + KEY_ROWID + " " +
			" FROM " + BOOKSHELF_TABLES + ", " + DB_TB_AUTHORS + " a" + 
			" WHERE a._id=b." + KEY_AUTHOR + " AND " +
				"(a." + KEY_FAMILY_NAME + " LIKE '%" + query + "%' OR " +
				" a." + KEY_GIVEN_NAMES + " LIKE '%" + query + "%' OR " +
				" b." + KEY_TITLE + " LIKE '%" + query + "%' OR " +
				" b." + KEY_ISBN + " LIKE '%" + query + "%' OR " +
				" b." + KEY_PUBLISHER + " LIKE '%" + query + "%' OR " +
				" b." + KEY_SERIES + " LIKE '%" + query + "%' OR " +
				" b." + KEY_NOTES + " LIKE '%" + query + "%' OR " +
				" b." + KEY_LOCATION + " LIKE '%" + query + "%')" + 
				where + 
			" ORDER BY " + order + "";
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
	public Cursor searchGenres(String query, String bookshelf) {
		String where = "";
		query = encodeString(query);
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT b." + KEY_GENRE + " as " + KEY_ROWID + 
				" FROM " + BOOKSHELF_TABLES + ", " + DB_TB_AUTHORS + " a" + 
				" WHERE a._id=b." + KEY_AUTHOR + where + " AND " + 
					" (a." + KEY_FAMILY_NAME + " LIKE '%" + query + "%' OR " +
					" a." + KEY_GIVEN_NAMES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_TITLE + " LIKE '%" + query + "%' OR " +
					" b." + KEY_ISBN + " LIKE '%" + query + "%' OR " +
					" b." + KEY_PUBLISHER + " LIKE '%" + query + "%' OR " +
					" b." + KEY_SERIES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_NOTES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_LOCATION + " LIKE '%" + query + "%')" + 
				" ORDER BY b." + KEY_GENRE + "";
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
	public Cursor searchSeries(String query, String bookshelf) {
		String where = "";
		query = encodeString(query);
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT DISTINCT b." + KEY_SERIES + " as " + KEY_ROWID + 
				" FROM " + BOOKSHELF_TABLES + ", " + DB_TB_AUTHORS + " a" + 
				" WHERE a._id=b." + KEY_AUTHOR + where + " AND " + 
					" (a." + KEY_FAMILY_NAME + " LIKE '%" + query + "%' OR " +
					" a." + KEY_GIVEN_NAMES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_TITLE + " LIKE '%" + query + "%' OR " +
					" b." + KEY_ISBN + " LIKE '%" + query + "%' OR " +
					" b." + KEY_PUBLISHER + " LIKE '%" + query + "%' OR " +
					" b." + KEY_SERIES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_NOTES + " LIKE '%" + query + "%' OR " +
					" b." + KEY_LOCATION + " LIKE '%" + query + "%')" + 
				" ORDER BY b." + KEY_SERIES + "";
		return mDb.rawQuery(sql, new String[]{});
	}
	
	
	
	
	public long createAnthologyTitle(long book, String author, String title) {
		ContentValues initialValues = new ContentValues();
		String[] names = processAuthorName(author);
		Cursor authorId = getAuthorByName(names);
		int aRows = authorId.getCount();
		if (aRows == 0) {
			createAuthor(names[0], names[1]);
			authorId.close();
			authorId = getAuthorByName(names);
		}
		authorId.moveToFirst();
		
		int position = fetchAnthologyPositionByBook(book) + 1;
		
		initialValues.put(KEY_BOOK, book);
		initialValues.put(KEY_AUTHOR, authorId.getInt(0));
		initialValues.put(KEY_TITLE, title);
		initialValues.put(KEY_POSITION, position);
		long result = mDb.insert(DB_TB_ANTHOLOGY, null, initialValues);
		authorId.close();
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
	 * Create a new book using the details provided. If the book is
	 * successfully created return the new rowId for that book, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param author The author name
	 * @param title The title of the book
	 * @param isbn The isbn of the book
	 * @param publisher The book publisher
	 * @param date_published The date the book was published
	 * @param rating The user rating of the book
	 * @param bookshelf The virtual bookshelf the book sits on
	 * @param read Has the user read the book
	 * @param series What series does the book belong to
	 * @param pages How many pages in the book
	 * @param series_num What number in the series is the book
	 * @param notes Any user written notes
	 * @param list_price The list price of the book
	 * @param anthology Is the book an anthology
	 * @param location A location field for the book
	 * @param read_start When was the book started to be read
	 * @param read_end When was the book finished being read
	 * @param audiobook Is it an audiobook 
	 * @param signed Is this copy signed
	 * @return rowId or -1 if failed
	 */
	public long createBook(String author, String title, String isbn, String publisher, String date_published, float rating, String bookshelf, Boolean read, String series, int pages, String series_num, String notes, String list_price, int anthology, String location, String read_start, String read_end, String format, boolean signed, String description, String genre) {
		return createBook(0, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, format, signed, description, genre);
	}
	
	/**
	 * Create a new book using the details provided. If the book is
	 * successfully created return the new rowId for that book, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param id The ID of the book to insert (this will overwrite the normal autoIncrement)
	 * @param author The author name
	 * @param title The title of the book
	 * @param isbn The isbn of the book
	 * @param publisher The book publisher
	 * @param date_published The date the book was published
	 * @param rating The user rating of the book
	 * @param bookshelf The virtual bookshelf the book sits on
	 * @param read Has the user read the book
	 * @param series What series does the book belong to
	 * @param pages How many pages in the book
	 * @param series_num What number in the series is the book
	 * @param notes Any user written notes
	 * @param list_price The list price of the book
	 * @param anthology Is the book an anthology
	 * @param location A location field for the book
	 * @param read_start When was the book started to be read
	 * @param read_end When was the book finished being read
	 * @param audiobook Is it an audiobook 
	 * @param signed Is this copy signed
	 * @return rowId or -1 if failed
	 */
	public long createBook(long id, String author, String title, String isbn, String publisher, String date_published, float rating, String bookshelf, Boolean read, String series, int pages, String series_num, String notes, String list_price, int anthology, String location, String read_start, String read_end, String format, boolean signed, String description, String genre) {
		ContentValues initialValues = new ContentValues();
		String[] names = processAuthorName(author);
		Cursor authorId = getAuthorByName(names);
		int aRows = authorId.getCount();
		if (aRows == 0) {
			createAuthor(names[0], names[1]);
			authorId.close();
			authorId = getAuthorByName(names);
		}
		authorId.moveToFirst();
		
		if (id > 0) {
			initialValues.put(KEY_ROWID, id);
		}
		initialValues.put(KEY_AUTHOR, authorId.getInt(0));
		initialValues.put(KEY_TITLE, title);
		initialValues.put(KEY_ISBN, isbn);
		initialValues.put(KEY_PUBLISHER, publisher);
		initialValues.put(KEY_DATE_PUBLISHED, date_published);
		initialValues.put(KEY_RATING, rating);
		//initialValues.put(KEY_BOOKSHELF, bookshelf_id);
		initialValues.put(KEY_READ, read);
		initialValues.put(KEY_SERIES, series);
		initialValues.put(KEY_PAGES, pages);
		initialValues.put(KEY_SERIES_NUM, series_num);
		initialValues.put(KEY_NOTES, notes);
		initialValues.put(KEY_LIST_PRICE, list_price);
		initialValues.put(KEY_ANTHOLOGY, anthology);
		initialValues.put(KEY_LOCATION, location);
		initialValues.put(KEY_READ_START, read_start);
		initialValues.put(KEY_READ_END, read_end);
		initialValues.put(KEY_FORMAT, format);
		initialValues.put(KEY_SIGNED, signed);
		initialValues.put(KEY_DESCRIPTION, description);
		initialValues.put(KEY_GENRE, genre);
		authorId.close();
		
		long result = mDb.insert(DB_TB_BOOKS, null, initialValues);
		if (bookshelf != null) {
			createBookshelfBooks(result, bookshelf);
		}
		authorId.close();
		return result;
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
	
	/**
	 * Create each book/bookshelf combo in the weak entity
	 * 
	 * @param mRowId The book id
	 * @param bookshelf A separated string of bookshelf names
	 */
	public void createBookshelfBooks(long mRowId, String bookshelf) {
		mDb.delete(DB_TB_BOOK_BOOKSHELF_WEAK, KEY_BOOK + "='" + mRowId + "'", null);
		
		String sql = "SELECT count(*) as count FROM " + DB_TB_BOOK_BOOKSHELF_WEAK + " WHERE " + KEY_BOOK + "='" + mRowId + "'";
		Cursor foo = mDb.rawQuery(sql, new String[]{});
		foo.moveToFirst();
		foo.close();
		
		//Insert the new ones
		String[] bookshelves = bookshelf.split(BookEditFields.BOOKSHELF_SEPERATOR.trim());
		for (int i = 0; i<bookshelves.length; i++) {
			int bookshelf_id=1;
			String name = bookshelves[i].trim();
			if (name.equals("")) {
				continue;
			}
			ContentValues initialValues = new ContentValues();
			Cursor bookshelfId = fetchBookshelfByName(name);
			int bRows = bookshelfId.getCount();
			if (bRows == 0) {
				createBookshelf(name);
				bookshelfId.close();
				bookshelfId = fetchBookshelfByName(name);
			}
			bookshelfId.moveToFirst();
			bookshelf_id = bookshelfId.getInt(0);
			bookshelfId.close();
			initialValues.put(KEY_BOOK, mRowId);
			initialValues.put(KEY_BOOKSHELF, bookshelf_id);
			mDb.insert(DB_TB_BOOK_BOOKSHELF_WEAK, null, initialValues);
		}
	}
	
	/**
	 * This function will create a new loan in the database
	 * 
	 * @param book The id of the book
	 * @param friend A string containing the friend you are loaning to
	 * @return the ID of the loan
	 */
	public long createLoan(long book, String friend) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_BOOK, book);
		initialValues.put(KEY_LOANED_TO, friend);
		long result = mDb.insert(DB_TB_LOAN, null, initialValues);
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
		Cursor authorId = getAuthorByName(names);
		int aRows = authorId.getCount();
		if (aRows == 0) {
			createAuthor(names[0], names[1]);
			authorId.close();
			authorId = getAuthorByName(names);
		}
		authorId.moveToFirst();
		
		args.put(KEY_BOOK, book);
		args.put(KEY_AUTHOR, authorId.getInt(0));
		args.put(KEY_TITLE, title);
		boolean success = mDb.update(DB_TB_ANTHOLOGY, args, KEY_ROWID + "=" + rowId, null) > 0;
		deleteAuthors();
		authorId.close();
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
	
	/**
	 * Update the book using the details provided. The book to be updated is
	 * specified using the rowId, and it is altered to use values passed in
	 * 
	 * @param rowId The id of the book in the database
	 * @param author The author name
	 * @param title The title of the book
	 * @param isbn The isbn of the book
	 * @param publisher The book publisher
	 * @param date_published The date the book was published
	 * @param rating The user rating of the book
	 * @param bookshelf The virtual bookshelf the book sits on
	 * @param read Has the user read the book
	 * @param series What series does the book belong to
	 * @param pages How many pages in the book
	 * @param series_num What number in the series is the book
	 * @param notes Any user written notes
	 * @param list_price The list price of the book
	 * @param anthology Is the book an anthology
	 * @param location A location field for the book
	 * @param read_start When was the book started to be read
	 * @param read_end When was the book finished being read
	 * @param audiobook Is it an audiobook 
	 * @param signed Is this copy signed
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updateBook(long rowId, String author, String title, String isbn, String publisher, String date_published, float rating, String bookshelf, boolean read, String series, int pages, String series_num, String notes, String list_price, int anthology, String location, String read_start, String read_end, String format, boolean signed, String description, String genre) {
		boolean success;
		ContentValues args = new ContentValues();
		String[] names = processAuthorName(author);
		Cursor authorId = getAuthorByName(names);
		int aRows = authorId.getCount();
		if (aRows == 0) {
			createAuthor(names[0], names[1]);
			authorId.close();
			authorId = getAuthorByName(names);
		}
		
		authorId.moveToFirst();
		args.put(KEY_AUTHOR, authorId.getInt(0));
		args.put(KEY_TITLE, title);
		args.put(KEY_ISBN, isbn);
		args.put(KEY_PUBLISHER, publisher);
		args.put(KEY_DATE_PUBLISHED, date_published);
		args.put(KEY_RATING, rating);
		args.put(KEY_READ, read);
		args.put(KEY_SERIES, series);
		args.put(KEY_PAGES, pages);
		args.put(KEY_SERIES_NUM, series_num);
		args.put(KEY_NOTES, notes);
		args.put(KEY_LIST_PRICE, list_price);
		args.put(KEY_ANTHOLOGY, anthology);
		args.put(KEY_LOCATION, location);
		args.put(KEY_READ_START, read_start);
		args.put(KEY_READ_END, read_end);
		args.put(KEY_FORMAT, format);
		args.put(KEY_SIGNED, signed);
		args.put(KEY_DESCRIPTION, description);
		args.put(KEY_GENRE, genre);
		authorId.close();
		
		success = mDb.update(DB_TB_BOOKS, args, KEY_ROWID + "=" + rowId, null) > 0;
		if (bookshelf != null) {
			createBookshelfBooks(rowId, bookshelf);
		}
		deleteAuthors();
		authorId.close();
		return success;
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
		deleteAuthors();
		return success;
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
		boolean success;
		// Delete the title
		success = mDb.delete(DB_TB_ANTHOLOGY, KEY_ROWID + "=" + rowId, null) > 0;
		deleteAuthors();
		// Move all titles past the deleted book up one position
		String sql = "UPDATE " + DB_TB_ANTHOLOGY + 
			" SET " + KEY_POSITION + "=" + KEY_POSITION + "-1" +
			" WHERE " + KEY_POSITION + ">" + position + " AND " + KEY_BOOK + "=" + book + "";
		mDb.execSQL(sql);
		return success;
	}
	
	/** 
	 * Delete the author with the given rowId
	 * 
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteAuthors() {
		boolean success = mDb.delete(DB_TB_AUTHORS, "_id NOT IN (SELECT DISTINCT " + KEY_AUTHOR + " FROM " + DB_TB_BOOKS + ") AND _id NOT IN (SELECT DISTINCT " + KEY_AUTHOR + " FROM " + DB_TB_ANTHOLOGY + ")", null) > 0;
		return success;
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
		deleteAuthors();
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
		return success;
	}
	
	
	
	
/**************************************************************************************/
	
	
	
	
	
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public Cursor getAuthorByName(String name) {
    	String[] names = processAuthorName(name);
    	String sql = "";
    	
    	sql = KEY_FAMILY_NAME + "='" + encodeString(names[0]) + "' AND " + KEY_GIVEN_NAMES + "='" + encodeString(names[1]) + "'";
        return mDb.query(DB_TB_AUTHORS, new String[] {"_id", KEY_FAMILY_NAME, KEY_GIVEN_NAMES}, sql, null, null, null, null);
    }
    
   
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public Cursor getAuthorByName(String[] names) {
    	String sql = "";
    	sql = KEY_FAMILY_NAME + "='" + encodeString(names[0]) + "' AND " + KEY_GIVEN_NAMES + "='" + encodeString(names[1]) + "'";
        return mDb.query(DB_TB_AUTHORS, new String[] {"_id", KEY_FAMILY_NAME, KEY_GIVEN_NAMES}, sql, null, null, null, null);
    }
    
    
    

    /**
     * Return a Cursor over the list of all series in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor fetchAllSeries() {
    	String sql = "SELECT DISTINCT " + KEY_SERIES +  
    		" FROM " + DB_TB_BOOKS + "" +  
    		" ORDER BY " + KEY_SERIES + "";
    	return mDb.rawQuery(sql, new String[]{});
    }
    
    

    /**
     * Return a Cursor positioned at the books that matches the given rowId
     * 
     * @param rowId id of note to retrieve
     * @return Cursor positioned to matching note, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public Cursor fetchAuthor(long rowId) throws SQLException {
    	String sql = "SELECT a." + KEY_ROWID + ", a." + KEY_FAMILY_NAME + ", a." + KEY_GIVEN_NAMES + 
		" FROM " + DB_TB_AUTHORS + " a " +  
		" WHERE a." + KEY_ROWID + "=" + rowId + "";

    	Cursor mCursor = mDb.rawQuery(sql, new String[]{});
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

    public String encodeString(String value) {
    	return value.replace("'", "''");
    }
    
}
