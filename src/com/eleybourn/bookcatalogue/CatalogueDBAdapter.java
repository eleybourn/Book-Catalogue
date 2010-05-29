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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

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
	public static final String LOCATION = "bookCatalogue";
	public static final String KEY_SERIES_NUM = "series_num";
	public static final String KEY_NOTES = "notes";
	public static final String KEY_BOOK = "book";
	public static final String KEY_LOANED_TO = "loaned_to";

	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	/* private database variables as static reference */
	private static final String DATABASE_NAME = "book_catalogue";
	private static final String DATABASE_TABLE_BOOKS = "books";
	private static final String DATABASE_TABLE_AUTHORS = "authors";
	private static final String DATABASE_TABLE_BOOKSHELF = "bookshelf";
	private static final String DATABASE_TABLE_LOAN = "loan";
	public static String message = "";

	/* Database creation sql statement */
	private static final String DATABASE_CREATE_AUTHORS =
		"create table " + DATABASE_TABLE_AUTHORS + 
		" (_id integer primary key autoincrement, " +
		KEY_FAMILY_NAME + " text not null, " +
		KEY_GIVEN_NAMES + " text not null" +
		")";

	private static final String DATABASE_CREATE_BOOKSHELF =
		"create table " + DATABASE_TABLE_BOOKSHELF + 
		" (_id integer primary key autoincrement, " +
		KEY_BOOKSHELF + " text not null " +
		")";

	private static final String DATABASE_CREATE_BOOKSHELF_DATA =
		"INSERT INTO " + DATABASE_TABLE_BOOKSHELF + 
		" (" + KEY_BOOKSHELF + ") VALUES ('Default')";

	private static final String DATABASE_CREATE_BOOKS =
		"create table " + DATABASE_TABLE_BOOKS + 
		" (_id integer primary key autoincrement, " +
		KEY_AUTHOR + " integer not null REFERENCES " + DATABASE_TABLE_AUTHORS + ", " + 
		KEY_TITLE + " text not null, " +
		KEY_ISBN + " text, " +
		KEY_PUBLISHER + " text, " +
		KEY_DATE_PUBLISHED + " date, " +
		KEY_RATING + " float not null default 0, " +
		KEY_BOOKSHELF + " integer REFERENCES " + DATABASE_TABLE_BOOKSHELF + " ON DELETE SET NULL ON UPDATE SET NULL, " + 
		KEY_READ + " boolean not null default 'f', " +
		KEY_SERIES + " text, " +
		KEY_PAGES + " int, " +
		KEY_SERIES_NUM + " text, " +
		KEY_NOTES + " text " +
		")";

	private static final String DATABASE_CREATE_LOAN =
		"create table " + DATABASE_TABLE_LOAN + 
		" (_id integer primary key autoincrement, " +
		KEY_BOOK + " integer REFERENCES " + DATABASE_TABLE_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
		KEY_LOANED_TO + " text " +
		")";
	
	private static final String DATABASE_CREATE_INDICES = 
		"CREATE INDEX IF NOT EXISTS authors_given_names ON "+DATABASE_TABLE_AUTHORS+" ("+KEY_GIVEN_NAMES+");" + 
		"CREATE INDEX IF NOT EXISTS authors_family_name ON "+DATABASE_TABLE_AUTHORS+" ("+KEY_FAMILY_NAME+");" + 
		"CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON "+DATABASE_TABLE_BOOKSHELF+" ("+KEY_BOOKSHELF+");" + 
		"CREATE INDEX IF NOT EXISTS books_author ON "+DATABASE_TABLE_BOOKS+" ("+KEY_AUTHOR+");" + 
		"CREATE INDEX IF NOT EXISTS books_title ON "+DATABASE_TABLE_BOOKS+" ("+KEY_TITLE+");" + 
		"CREATE INDEX IF NOT EXISTS books_isbn ON "+DATABASE_TABLE_BOOKS+" ("+KEY_ISBN+");" + 
		"CREATE INDEX IF NOT EXISTS books_bookshelf ON "+DATABASE_TABLE_BOOKS+" ("+KEY_BOOKSHELF+");" + 
		"CREATE INDEX IF NOT EXISTS books_series ON "+DATABASE_TABLE_BOOKS+" ("+KEY_SERIES+");" + 
		"CREATE INDEX IF NOT EXISTS books_publisher ON "+DATABASE_TABLE_BOOKS+" ("+KEY_PUBLISHER+");" + 
		"CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON "+DATABASE_TABLE_LOAN+" ("+KEY_BOOK+");" 
		;

	private final Context mCtx;
	private static final int DATABASE_VERSION = 23;

	/**
	 * This is a specific version of the SQLiteOpenHelper class. It handles onCreate and onUpgrade events
	 * 
	 * @author evan
	 */
	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
			db.execSQL(DATABASE_CREATE_INDICES);
			new File(Environment.getExternalStorageDirectory() + "/" + LOCATION + "/").mkdirs();
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
				db.execSQL("ALTER TABLE " + DATABASE_TABLE_BOOKS + " ADD " + KEY_SERIES_NUM + " text");
				db.execSQL("UPDATE " + DATABASE_TABLE_BOOKS + " SET " + KEY_SERIES_NUM + " = ''");
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
				db.execSQL("ALTER TABLE " + DATABASE_TABLE_BOOKS + " ADD " + KEY_NOTES + " text");
				db.execSQL("UPDATE " + DATABASE_TABLE_BOOKS + " SET " + KEY_NOTES + " = ''");
				curVersion++;
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
		new File(Environment.getExternalStorageDirectory() + "/" + LOCATION + "/").mkdirs();
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	/**
	 * Generic function to close the database
	 */
	public void close() {
		mDbHelper.close();
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
	 * Return the position of an author in a list of all authors (within a bookshelf)
	 *  
	 * @param author The author to search for
	 * @param bookshelf The bookshelf to search within
	 * @return The position of the author
	 */
	public int fetchAuthorPositionByName(String name, String bookshelf) {
		String where = "";
		String[] names = processAuthorName(name);
		if (bookshelf.equals("All Books")) {
			// do nothing
		} else {
			where += " AND a." + KEY_ROWID + " IN (SELECT " + KEY_AUTHOR + " FROM " + 
				DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs WHERE bs." + KEY_ROWID + "=b." + KEY_BOOKSHELF +
				" AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "') ";
		}
		String sql = "SELECT count(*) as count FROM " + DATABASE_TABLE_AUTHORS + " a " +
			"WHERE a." + KEY_FAMILY_NAME + "<'" + encodeString(names[0]) + "' " +
			"OR (a." + KEY_FAMILY_NAME + "='" + encodeString(names[0]) + "' AND a." + KEY_GIVEN_NAMES + "<'" + encodeString(names[1]) + "')" + 
			where;
		Log.e("BC", sql);
		Cursor results = mDb.rawQuery(sql, null);
		int pos = getIntValue(results, 0);
		Log.e("BC", pos + " foo");
		return pos;
	}
	
	/**
	 * Return the position of a book in a list of all books (within a bookshelf)
	 *  
	 * @param title The book title to search for
	 * @param bookshelf The bookshelf to search within
	 * @return The position of the book
	 */
	public int fetchBookPositionByTitle(String title, String bookshelf) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT count(*) as count FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs " +
				"WHERE bs._id=b." + KEY_BOOKSHELF + " AND b.title < '" + encodeString(title) + "'" + where;
		Log.e("BC", sql);
		Cursor results = mDb.rawQuery(sql, null);
		int pos = getIntValue(results, 0);
		Log.e("BC", pos + " foo");
		return pos;
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
		return mDb.query(DATABASE_TABLE_BOOKSHELF, new String[] {"_id", KEY_BOOKSHELF}, sql, null, null, null, null);
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
		Cursor results = mDb.query(DATABASE_TABLE_LOAN, new String[] {KEY_BOOK, KEY_LOANED_TO}, sql, null, null, null, null);
		String user = getStringValue(results, 1);
		return user;
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
		return mDb.insert(DATABASE_TABLE_AUTHORS, null, initialValues);
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
		return mDb.insert(DATABASE_TABLE_LOAN, null, initialValues);
	}
	
	
	
	
	
	
	/** 
	 * Delete the book with the given rowId
	 * 
	 * @param rowId id of book to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteBook(long rowId) {
		boolean success;
		success = mDb.delete(DATABASE_TABLE_BOOKS, KEY_ROWID + "=" + rowId, null) > 0;
		deleteAuthors();
		return success;
	}
	
	/** 
	 * Delete the loan with the given rowId
	 * 
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteLoan(long rowId) {
		boolean success;
		success = mDb.delete(DATABASE_TABLE_LOAN, KEY_BOOK+ "=" + rowId, null) > 0;
		return success;
	}

	
	
	
	
	
	
	
	
	
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public Cursor getAuthorByName(String name) {
    	String[] names = processAuthorName(name);
    	String sql = "";
    	
    	sql = KEY_FAMILY_NAME + "='" + encodeString(names[0]) + "' AND " + KEY_GIVEN_NAMES + "='" + encodeString(names[1]) + "'";
        return mDb.query(DATABASE_TABLE_AUTHORS, new String[] {"_id", KEY_FAMILY_NAME, KEY_GIVEN_NAMES}, sql, null, null, null, null);
    }
    
   
    /*
     * This will return the author id based on the name. 
     * The name can be in either "family, given" or "given family" format.
     */
    public Cursor getAuthorByName(String[] names) {
    	String sql = "";

    	sql = KEY_FAMILY_NAME + "='" + encodeString(names[0]) + "' AND " + KEY_GIVEN_NAMES + "='" + encodeString(names[1]) + "'";
        return mDb.query(DATABASE_TABLE_AUTHORS, new String[] {"_id", KEY_FAMILY_NAME, KEY_GIVEN_NAMES}, sql, null, null, null, null);
    }
    
   
    public long createBookshelf(String bookshelf) {
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_BOOKSHELF, bookshelf);
        return mDb.insert(DATABASE_TABLE_BOOKSHELF, null, initialValues);
    }
    
    

	/**
	 * Create a new note using the title and body provided. If the note is
	 * successfully created return the new rowId for that note, otherwise return
	 * a -1 to indicate failure.
	 * 
	 * @param title the title of the note
	 * @param body the body of the note
	 * @return rowId or -1 if failed
	 */
	public long createBook(String author, String title, String isbn, String publisher, String date_published, float rating, String bookshelf, Boolean read, String series, int pages, String series_num, String notes) {
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
		
		int bookshelf_id=1;
		if (bookshelf != "") {
			Cursor bookshelfId = fetchBookshelfByName(bookshelf);
			int bRows = bookshelfId.getCount();
			if (bRows == 0) {
				createBookshelf(bookshelf);
				bookshelfId.close();
				bookshelfId = fetchBookshelfByName(bookshelf);
			}
			bookshelfId.moveToFirst();
			bookshelf_id = bookshelfId.getInt(0);
			bookshelfId.close();
		}

		initialValues.put(KEY_AUTHOR, authorId.getInt(0));
		initialValues.put(KEY_TITLE, title);
		initialValues.put(KEY_ISBN, isbn);
		initialValues.put(KEY_PUBLISHER, publisher);
		initialValues.put(KEY_DATE_PUBLISHED, date_published);
		initialValues.put(KEY_RATING, rating);
		initialValues.put(KEY_BOOKSHELF, bookshelf_id);
		initialValues.put(KEY_READ, read);
		initialValues.put(KEY_SERIES, series);
		initialValues.put(KEY_PAGES, pages);
		initialValues.put(KEY_SERIES_NUM, series_num);
		initialValues.put(KEY_NOTES, notes);
		authorId.close();

		return mDb.insert(DATABASE_TABLE_BOOKS, null, initialValues);
	}
    

    /** 
     * Delete the author with the given rowId
     * 
     * @param rowId id of note to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteAuthors() {
        return mDb.delete(DATABASE_TABLE_AUTHORS, "_id NOT IN (SELECT DISTINCT " + KEY_AUTHOR + " FROM " + DATABASE_TABLE_BOOKS + ")", null) > 0;
    }
    

    /** 
     * Delete the bookshelf with the given rowId
     * 
     * @param rowId id of note to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteBookshelf(long rowId) {
    	boolean deleteSuccess;
    	String sql = "UPDATE " + DATABASE_TABLE_BOOKS + " SET " + KEY_BOOKSHELF + "=1 WHERE " + KEY_BOOKSHELF + "='" + rowId + "'";
    	mDb.execSQL(sql);
        deleteSuccess = mDb.delete(DATABASE_TABLE_BOOKSHELF, KEY_ROWID + "=" + rowId, null) > 0;
        return deleteSuccess;
    }
    

	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllBooks(String order, String bookshelf) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT b." + KEY_ROWID + ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR + ", b." + KEY_TITLE + 
		", b." + KEY_ISBN + ", b." + KEY_PUBLISHER + ", b." + KEY_DATE_PUBLISHED + ", b." + KEY_RATING + ", bs." + KEY_BOOKSHELF + 
		", b." + KEY_READ + ", b." + KEY_SERIES + ", b." + KEY_PAGES + ", b." + KEY_SERIES_NUM + ", b." + KEY_BOOKSHELF + " as bookshelf_id" +
		", b." + KEY_NOTES + 
		" FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs, " + DATABASE_TABLE_AUTHORS + " a" + 
		" WHERE bs._id=b." + KEY_BOOKSHELF + " AND a._id=b." + KEY_AUTHOR + where + 
		" ORDER BY " + order + "";
		return mDb.rawQuery(sql, new String[]{});
		//return mDb.query(DATABASE_TABLE_BOOKS, new String[] {KEY_ROWID, KEY_AUTHOR, KEY_TITLE, KEY_ISBN, KEY_PUBLISHER, 
		//KEY_DATE_PUBLISHED, KEY_RATING, KEY_BOOKSHELF, KEY_READ, KEY_SERIES, KEY_PAGES}, null, null, null, null, null);
	}

    /**
     * Return a Cursor over the list of all books in the database
     * 
     * @return Cursor over all notes
     */
    public int getBooksCount(String bookshelf) {
    	String where = "";
    	if (bookshelf.equals("All Books")) {
    		// do nothing 
    	} else {
    		where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
    	}
    	String sql = "SELECT count(*) as count " + 
    		" FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs" + 
    		" WHERE bs._id=b." + KEY_BOOKSHELF + where;
    	Cursor count = mDb.rawQuery(sql, new String[]{});
    	count.moveToNext();
    	return count.getInt(0);
    }

    /**
     * Return a Cursor over the list of all books in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor fetchAllBooks(String order) {
    	return fetchAllBooks(order, "All Books"); 
    }

	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor exportBooks() {
		String sql = "SELECT b." + KEY_ROWID + ", b." + KEY_AUTHOR + ", a." + KEY_FAMILY_NAME + ", a." + KEY_GIVEN_NAMES + ", b." + KEY_TITLE + 
		", b." + KEY_ISBN + ", b." + KEY_PUBLISHER + ", b." + KEY_DATE_PUBLISHED + ", b." + KEY_RATING + 
		", b." + KEY_BOOKSHELF + " as bookshelf_id" + ", bs." + KEY_BOOKSHELF + ", b." + KEY_READ + ", b." + KEY_SERIES + 
		", b." + KEY_PAGES + ", b." + KEY_SERIES_NUM + ", b." + KEY_NOTES + 
		" FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs, " + DATABASE_TABLE_AUTHORS + " a" + 
		" WHERE bs._id=b." + KEY_BOOKSHELF + " AND a._id=b." + KEY_AUTHOR;
		return mDb.rawQuery(sql, new String[]{});
	}

	/**
	 * Return a Cursor over the list of all books in the database by author
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllBooksByAuthor(int author, String bookshelf) {
		String where = "";
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else 	{
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT b." + KEY_ROWID + ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR + ", b." + KEY_TITLE + 
		", b." + KEY_ISBN + ", b." + KEY_PUBLISHER + ", b." + KEY_DATE_PUBLISHED + ", b." + KEY_RATING + ", bs." + KEY_BOOKSHELF + 
		", b." + KEY_READ + ", b." + KEY_SERIES + ", b." + KEY_PAGES + ", b." + KEY_SERIES_NUM + ", b." + KEY_NOTES + 
		" FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs, " + DATABASE_TABLE_AUTHORS + " a" + 
		" WHERE bs._id=b." + KEY_BOOKSHELF + " AND a._id=b." + KEY_AUTHOR + " AND a._id=" + author + where + 
		" ORDER BY b." + KEY_SERIES + ", substr('0000000000' || b." + KEY_SERIES_NUM + ", -10, 10), lower(b." + KEY_TITLE + ") ASC";
		return mDb.rawQuery(sql, new String[]{});
	}

	public Cursor fetchBookByISBN(String isbn) {
		String sql = "SELECT b." + KEY_ROWID + ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR + ", b." + KEY_TITLE + 
		", b." + KEY_ISBN + ", b." + KEY_PUBLISHER + ", b." + KEY_DATE_PUBLISHED + ", b." + KEY_RATING + ", bs." + KEY_BOOKSHELF + 
		", b." + KEY_READ + ", b." + KEY_SERIES + ", b." + KEY_PAGES + ", b." + KEY_SERIES_NUM + ", b." + KEY_NOTES + 
		" FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs, " + DATABASE_TABLE_AUTHORS + " a" + 
		" WHERE bs._id=b." + KEY_BOOKSHELF + " AND a._id=b." + KEY_AUTHOR + " AND b." + KEY_ISBN + "='" + encodeString(isbn) + "'" +
		" ORDER BY lower(b." + KEY_TITLE + ")";
		return mDb.rawQuery(sql, new String[]{});
	}

    /**
     * Return a Cursor over the list of all books in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor fetchAllAuthors(String bookshelf) {
    	String where = "";
    	if (bookshelf.equals("All Books")) {
    		// do nothing
    	} else {
    		where += " WHERE a." + KEY_ROWID + " IN (SELECT " + KEY_AUTHOR + " FROM " + 
    			DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs WHERE bs." + KEY_ROWID + "=b." + KEY_BOOKSHELF +
    			" AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "') ";
    	}
    	String sql = "SELECT a._id, a." + KEY_FAMILY_NAME + ", a." + KEY_GIVEN_NAMES + 
    		" FROM " + DATABASE_TABLE_AUTHORS + " a" + where + 
    		" ORDER BY lower(" + KEY_FAMILY_NAME + "), lower(" + KEY_GIVEN_NAMES + ")";
    	return mDb.rawQuery(sql, new String[]{});
    }

    /**
     * Return a Cursor over the list of all series in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor fetchAllSeries() {
    	String sql = "SELECT DISTINCT " + KEY_SERIES +  
    		" FROM " + DATABASE_TABLE_BOOKS + "" +  
    		" ORDER BY " + KEY_SERIES + "";
    	return mDb.rawQuery(sql, new String[]{});
    }

    /**
     * Return a Cursor over the list of all publishers in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor fetchAllPublishers() {
    	String sql = "SELECT DISTINCT " + KEY_PUBLISHER +  
    		" FROM " + DATABASE_TABLE_BOOKS + "" +  
    		" ORDER BY " + KEY_PUBLISHER + "";
    	return mDb.rawQuery(sql, new String[]{});
    }
    

    /**
     * Return a Cursor over the list of all books in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor searchAuthors(String query, String bookshelf) {
    	String where = "";
    	query = encodeString(query);
    	if (bookshelf.equals("All Books")) {
    		// do nothing
    	} else {
    		where += " AND a." + KEY_ROWID + " IN (SELECT " + KEY_AUTHOR + " FROM " + 
    			DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs WHERE bs." + KEY_ROWID + "=b." + KEY_BOOKSHELF +
    			" AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "') ";
    	}
    	String sql = "SELECT a._id, a." + KEY_FAMILY_NAME + ", a." + KEY_GIVEN_NAMES + 
    		" FROM " + DATABASE_TABLE_AUTHORS + " a" + " WHERE " +
    		" (a." + KEY_FAMILY_NAME + " LIKE '%" + query + "%' OR a." + KEY_GIVEN_NAMES + " LIKE '%" + query + "%' OR " +
    				"a." + KEY_ROWID + " IN (SELECT " + KEY_AUTHOR + " FROM " + DATABASE_TABLE_BOOKS + " b WHERE b." + KEY_TITLE + " LIKE '%" + query + "%') )" + 
    		where + " ORDER BY " + KEY_FAMILY_NAME + ", " + KEY_GIVEN_NAMES + "";

    	return mDb.rawQuery(sql, new String[]{});
    }
    

	/**
	 * Return a Cursor over the list of all books in the database
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor searchBooks(String query, String order, String bookshelf) {
		String where = "";
		query = encodeString(query);
		if (bookshelf.equals("All Books")) {
			// do nothing 
		} else {
			where += " AND bs." + KEY_BOOKSHELF + "='" + encodeString(bookshelf) + "'";
		}
		String sql = "SELECT b." + KEY_ROWID + ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR + ", b." + KEY_TITLE + 
		", b." + KEY_ISBN + ", b." + KEY_PUBLISHER + ", b." + KEY_DATE_PUBLISHED + ", b." + KEY_RATING + ", bs." + KEY_BOOKSHELF + 
		", b." + KEY_READ + ", b." + KEY_SERIES + ", b." + KEY_PAGES + ", b." + KEY_SERIES_NUM + ", b." + KEY_NOTES + 
		" FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs, " + DATABASE_TABLE_AUTHORS + " a" + 
		" WHERE bs._id=b." + KEY_BOOKSHELF + " AND a._id=b." + KEY_AUTHOR + 
		" AND (a." + KEY_FAMILY_NAME + " LIKE '%" + query + "%' OR a." + KEY_GIVEN_NAMES + " LIKE '%" + query + "%' OR " +
		" b." + KEY_TITLE + " LIKE '%" + query + "%')" + 
		where + 
		" ORDER BY " + order + "";
		return mDb.rawQuery(sql, new String[]{});
	}

    /**
     * Return a Cursor over the list of all books in the database
     * 
     * @return Cursor over all notes
     */
    public Cursor fetchAllBookshelves() {
    	String sql = "SELECT b." + KEY_ROWID + ", b." + KEY_BOOKSHELF +  
    		" FROM " + DATABASE_TABLE_BOOKSHELF + " b" + 
    		" ORDER BY " + KEY_BOOKSHELF + "";
    	return mDb.rawQuery(sql, new String[]{});
    }

	/**
	 * Return a Cursor positioned at the books that matches the given rowId
	 * 
	 * @param rowId id of note to retrieve
	 * @return Cursor positioned to matching note, if found
	 * @throws SQLException if note could not be found/retrieved
	 */
	public Cursor fetchBook(long rowId) throws SQLException {
		String sql = "SELECT b." + KEY_ROWID + ", a." + KEY_FAMILY_NAME + " || ', ' || a." + KEY_GIVEN_NAMES + " as " + KEY_AUTHOR + 
		", b." + KEY_TITLE + ", b." + KEY_ISBN + ", b." + KEY_PUBLISHER + ", b." + KEY_DATE_PUBLISHED + ", b." + KEY_RATING + 
		", bs." + KEY_BOOKSHELF + 
		", b." + KEY_READ + ", b." + KEY_SERIES + ", b." + KEY_PAGES + ", b." + KEY_SERIES_NUM + ", b." + KEY_NOTES + 
		" FROM " + DATABASE_TABLE_BOOKS + " b, " + DATABASE_TABLE_BOOKSHELF + " bs, " + DATABASE_TABLE_AUTHORS + " a" + 
		" WHERE bs._id=b." + KEY_BOOKSHELF + " AND a._id=b." + KEY_AUTHOR + " AND b." + KEY_ROWID + "=" + rowId + "";

		Cursor mCursor = mDb.rawQuery(sql, new String[]{});
		//Cursor mCursor = mDb.query(true, DATABASE_TABLE_BOOKS, new String[] {KEY_ROWID, KEY_AUTHOR, KEY_TITLE, KEY_ISBN, KEY_PUBLISHER, 
		//		KEY_DATE_PUBLISHED, KEY_RATING, KEY_BOOKSHELF, KEY_READ, KEY_SERIES, KEY_PAGES}, KEY_ROWID + "=" + rowId, 
		//		null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

    /**
     * Return a Cursor positioned at the books that matches the given rowId
     * 
     * @param rowId id of note to retrieve
     * @return Cursor positioned to matching note, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public Cursor fetchBookshelf(long rowId) throws SQLException {
    	String sql = "SELECT b." + KEY_ROWID + ", b." + KEY_BOOKSHELF + 
		" FROM " + DATABASE_TABLE_BOOKSHELF + " b " +  
		" WHERE b." + KEY_ROWID + "=" + rowId + "";

    	Cursor mCursor = mDb.rawQuery(sql, new String[]{});
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

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
		" FROM " + DATABASE_TABLE_AUTHORS + " a " +  
		" WHERE a." + KEY_ROWID + "=" + rowId + "";

    	Cursor mCursor = mDb.rawQuery(sql, new String[]{});
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

	/**
	 * Update the note using the details provided. The note to be updated is
	 * specified using the rowId, and it is altered to use the title and body
	 * values passed in
	 * 
	 * @param rowId id of note to update
	 * @param title value to set note title to
	 * @param body value to set note body to
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updateBook(long rowId, String author, String title, String isbn, String publisher, String date_published, float rating, String bookshelf, Boolean read, String series, int pages, String series_num, String notes) {
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

		int bookshelf_id=1;
		if (bookshelf != "") {
			Cursor bookshelfId = fetchBookshelfByName(bookshelf);
			int bRows = bookshelfId.getCount();
			if (bRows == 0) {
				createBookshelf(bookshelf);
				bookshelfId.close();
				bookshelfId = fetchBookshelfByName(bookshelf);
			}
			bookshelfId.moveToFirst();
			bookshelf_id = bookshelfId.getInt(0);
			bookshelfId.close();
		}
		authorId.moveToFirst();
		args.put(KEY_AUTHOR, authorId.getInt(0));
		args.put(KEY_TITLE, title);
		args.put(KEY_ISBN, isbn);
		args.put(KEY_PUBLISHER, publisher);
		args.put(KEY_DATE_PUBLISHED, date_published);
		args.put(KEY_RATING, rating);
		args.put(KEY_BOOKSHELF, bookshelf_id);
		args.put(KEY_READ, read);
		args.put(KEY_SERIES, series);
		args.put(KEY_PAGES, pages);
		args.put(KEY_SERIES_NUM, series_num);
		args.put(KEY_NOTES, notes);
		authorId.close();

		success = mDb.update(DATABASE_TABLE_BOOKS, args, KEY_ROWID + "=" + rowId, null) > 0;
		deleteAuthors();
		return success;
	}

    /**
     * Update the note using the details provided. The note to be updated is
     * specified using the rowId, and it is altered to use the title and body
     * values passed in
     * 
     * @param rowId id of note to update
     * @param title value to set note title to
     * @param body value to set note body to
     * @return true if the note was successfully updated, false otherwise
     */
    public boolean updateBookshelf(long rowId, String bookshelf) {
    	boolean success;
        ContentValues args = new ContentValues();
        args.put(KEY_BOOKSHELF, bookshelf);

        success = mDb.update(DATABASE_TABLE_BOOKSHELF, args, KEY_ROWID + "=" + rowId, null) > 0;
        deleteAuthors();
        return success;
    }

    public String encodeString(String value) {
    	return value.replace("'", "''");
    }
    
}
