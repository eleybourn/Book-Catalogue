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
package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.CatalogueDBAdapter.EMPTY_STRING_ARRAY;
import static com.eleybourn.bookcatalogue.CatalogueDBAdapter.KEY_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.CatalogueDBAdapter.KEY_LOANED_TO;
import static com.eleybourn.bookcatalogue.CatalogueDBAdapter.encodeString;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.*;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ABSOLUTE_POSITION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ADDED_DATE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ADDED_DAY;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_SORT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOKSHELF_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK_COUNT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_EXPANDED;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_GENRE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_KIND;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LEVEL;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LOANED_TO_SORT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_MARK;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PRIMARY_SERIES_COUNT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_DAY;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_END;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_STATUS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_YEAR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_REAL_ROW_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ROOT_KEY;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NUM_FLOAT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_VISIBLE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_LIST_DEFN;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_LOAN;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_ROW_NAVIGATOR_DEFN;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_ROW_NAVIGATOR_FLATTENED_DEFN;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.TBL_SERIES;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map.Entry;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQuery;
import android.os.Build;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.OtherPreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.BooklistAuthorGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.BooklistSeriesGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.CompoundKey;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.database.DbUtils.JoinContext;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition.TableTypes;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Logger;


/**
 * Class used to build and populate temporary tables with details of a flattened book list used to
 * display books in a ListView control and perform operation like 'expand/collapse' on pseudo nodes
 * in the list.
 * 
 * @author Philip Warner
 */
public class BooklistBuilder {
	/** Counter for BooklistBuilder IDs */
	private static Integer mBooklistBuilderIdCounter = 0;

	/**
	 * Details of extra domain requested by caller before the build() method is called.
	 * 
	 * @author Philip Warner
	 */
	private class ExtraDomainDetails {
		/** Domain definition of domain to add */
		DomainDefinition	domain;
		/** Expression to use in deriving domain value */
		String sourceExpression;
		/** Indicates if domain is to be part of the list sort key */
		boolean isSorted;	
	};

	/** Collection of statements created by this Builder */
	private final SqlStatementManager mStatements;
	/** Database to use */
	private final SynchronizedDb mDb;
	/** Internal ID */
	private final int mBooklistBuilderId;

	/** List of columns for the group-by clause, including COLLATE clauses. Set by build() method. */
	//private String mGroupColumnList;
	/** Collection of 'extra' domains requested by caller */
	private Hashtable<String, ExtraDomainDetails> mExtraDomains = new Hashtable<String, ExtraDomainDetails>();
	/** Style to use in building the list */
	private final BooklistStyle mStyle;
	/** Local copy of the BOOK_LIST table definition, renamed to match this instance */
	private TableDefinition mListTable;
	/** Local copy of the navigation table definition, renamed to match this instance */
	private TableDefinition mNavTable;

	/** Object used in constructing the output table */
	private SummaryBuilder mSummary = null;

	/** Statement used to perform initial insert */
	private SynchronizedStatement mBaseBuildStmt = null;
	/** Collection of statements used to build remaining data */
	private ArrayList<SynchronizedStatement> mLevelBuildStmts = null;

	/** Debug counter */
	private static Integer mInstanceCount = 0;

	/**
	 * Constructor
	 * 
	 * @param adapter	Database Adapter to use
	 * @param style		Book list style to use
	 */
	public BooklistBuilder(CatalogueDBAdapter adapter, BooklistStyle style) {
		synchronized(mInstanceCount) {
			mInstanceCount++;
			System.out.println("Builder instances: " + mInstanceCount);
		}

		// Allocate ID
		synchronized(mBooklistBuilderIdCounter) {
			mBooklistBuilderId = ++mBooklistBuilderIdCounter;
		}
		// Get the database and create a statements collection
		mDb = adapter.getDb();
		mStatements = new SqlStatementManager(mDb);
		// Save the requested style
		mStyle = style;

		// Clone the temp. table definitions and append the ID to make new names in case
		// more than one view is open.
		mListTable = TBL_BOOK_LIST_DEFN.clone();
		mListTable.setName(mListTable.getName() + "_" + getId());
		mListTable.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY

		mNavTable = TBL_ROW_NAVIGATOR_DEFN.clone()
				.addReference(mListTable, DOM_REAL_ROW_ID)
				;
		mNavTable.setName(mNavTable.getName() + "_" + getId());
		mNavTable.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY
	}

	/** Counter for 'flattened' book temp tables */
	private static Integer mFlatNavCounter = 0;
	/**
	 * Construct a flattened table of ordered book IDs based on the underlying list
	 */
	public FlattenedBooklist createFlattenedBooklist() {
		int flatId;
		synchronized(mFlatNavCounter) {
			flatId = mFlatNavCounter++;
		}
		TableDefinition flat = TBL_ROW_NAVIGATOR_FLATTENED_DEFN.clone();
		flat.setName(flat.getName() + "_" + flatId);
		flat.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY
		flat.create(mDb, true);
		String sql = flat.getInsert(DOM_ID, DOM_BOOK)
					+ " select " + mNavTable.dot(DOM_ID) + ", " + mListTable.dot(DOM_BOOK) 
					+ " From " + mListTable.ref() 
					+ mListTable.join(mNavTable) 
					+ " Where " + mListTable.dot(DOM_BOOK) + " Not Null " 
					+ " Order by " + mNavTable.dot(DOM_ID);
		mDb.execSQL(sql);
		return new FlattenedBooklist(mDb, flat);
	}

	/**
	 * Accessor.
	 * 
	 * @return
	 */
	public int getId() {
		return mBooklistBuilderId;
	}

	/**
	 * Add a domain to the resulting flattened list based on the details provided.
	 * 
	 * @param domain			Domain to add (used for name)
	 * @param sourceExpression	Expression to generate date for this column
	 * @param isSorted			Indicates if it should be added to the sort key
	 * 
	 * @return		The builder (to allow chaining)
	 */
	public BooklistBuilder requireDomain(DomainDefinition domain, String sourceExpression, boolean isSorted) {
		// Save the details
		ExtraDomainDetails info = new ExtraDomainDetails();
		info.domain = domain;
		info.sourceExpression = sourceExpression;
		info.isSorted = isSorted;

		// Check if it already exists
		if (mExtraDomains.containsKey(domain.name)) {
			// Make sure it has the same definition.
			boolean ok = false;
			ExtraDomainDetails oldInfo = mExtraDomains.get(domain.name);
			if (oldInfo.sourceExpression == null) {
				if (info.sourceExpression == null ) {
					ok = true;
				} else {
					ok = info.sourceExpression.equals("");
				}
			} else {
				if (info.sourceExpression == null ) {
					ok = oldInfo.sourceExpression.equals("");
				} else {
					ok = oldInfo.sourceExpression.equalsIgnoreCase(info.sourceExpression);
				}					
			}
			if (!ok)
				throw new RuntimeException("Required domain '" + domain.name + "' added with differing source expression");
		} else
			// Add it.
			mExtraDomains.put(domain.name, info);

		return this;
	}

	public static class SortedDomainInfo {
		DomainDefinition domain;
		boolean isDescending;
		SortedDomainInfo(DomainDefinition domain, boolean isDescending) {
			this.domain = domain;
			this.isDescending = isDescending;
		}
	}

	/**
	 * Structure used to store components of the SQL required to build the list.
	 * We use this for experimenting with alternate means of construction.
	 */
	private static class SqlComponents {
		public String select;
		public String insert;
		public String insertSelect;
		public String insertValues;
		public String rootkeyExpression;
		public String join;
		public String where;
	}

	/**
	 * Utility class to accumulate date for the build() method.
	 * 
	 * @author Philip Warner
	 */
	private class SummaryBuilder {

		/** Flag indicating added domain has no special properties */
		public static final int FLAG_NONE = 0;
		/** Flag indicating added domain is SORTED */
		public static final int FLAG_SORTED = 1;
		/** Flag indicating added domain is GROUPED */
		public static final int FLAG_GROUPED = 2;
		// Not currently used.
		///** Flag indicating added domain is part of the unique key */
		//public static final int FLAG_KEY = 4;
		/** Flag indicating added domain should be SORTED in descending order. DO NOT USE FOR GROUPED DATA. See notes below. */
		public static final int FLAG_SORT_DESCENDING = 8;

		/** Domains required in output table */
		private ArrayList<DomainDefinition> mDomains = new ArrayList<DomainDefinition>();
		/** Source expressions for output domains */
		private ArrayList<String> mExpressions = new ArrayList<String>();
		/** Mapping from Domain to source Expression */
		private Hashtable<DomainDefinition, String> mExpressionMap = new Hashtable<DomainDefinition, String>();

		/** Domains that are GROUPED */
		private ArrayList<DomainDefinition> mGroups = new ArrayList<DomainDefinition>();
		// Not currently used.
		///** Domains that form part of accumulated unique key */
		//private ArrayList<DomainDefinition> mKeys = new ArrayList<DomainDefinition>();
		/**
		 * Domains that form part of the sort key. These are typically a reduced set of the GROUP domains since 
		 * the group domains may contain more than just the key
		 */
		private ArrayList<SortedDomainInfo> mSortedColumns = new ArrayList<SortedDomainInfo>();
		
		/**
		 * Add a domain and source expression to the summary.
		 * 
		 * @param domain		Domain to add
		 * @param expression	Source Expression
		 * @param flags			Flags indicating attributes of new domain
		 */
		public void addDomain(DomainDefinition domain, String expression, int flags) {
			// Add to various collections. We use a map to improve lookups and ArrayLists
			// so we can preserve order. Order preservation makes reading the SQL easier
			// but is unimportant for code correctness.

			// Add to table
			mListTable.addDomain(domain);

			// Domains and Expressions must be synchronized; we should probably use a map.
			// For now, just check if mExpression is null. If it IS null, it means that
			// the domain is just for the lowest level of the hierarchy.
			if (expression != null) {
				mDomains.add(domain);
				mExpressions.add(expression);
				mExpressionMap.put(domain, expression);				
			}

			// Based on the flags, add the domain to other lists.
			if ((flags & FLAG_GROUPED) != 0)
				mGroups.add(domain);

			if ((flags & FLAG_SORTED) != 0) {				
				mSortedColumns.add(new SortedDomainInfo(domain, (flags & FLAG_SORT_DESCENDING) != 0) );	
			}

			// Not currently used
			//if ((flags & FLAG_KEY) != 0)
			//	mKeys.add(domain);
		}

		/**
		 * Return a clone of the CURRENT groups. Since BooklistGroup objects are processed in order, this
		 * allows us to get the GROUP-BY fields applicable to the currently processed group, including all
		 * outer groups. Hence why it is cloned -- subsequent domains will modify this collection.
		 * 
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public ArrayList<DomainDefinition> cloneGroups() {
			return (ArrayList<DomainDefinition>)mGroups.clone();				
		}

		/**
		 * Return the collection of columns used to sort the output.
		 * 
		 * @return
		 */
		public ArrayList<SortedDomainInfo> getSortedColumns() {
			return mSortedColumns;				
		}

		/**
		 * Drop and recreate the underlying temp table
		 */
		public void recreateTable() {
			//mListTable.setIsTemporary(true);
			long t0 = System.currentTimeMillis();
			mListTable.drop(mDb);
			long t1 = System.currentTimeMillis();
			mListTable.create(mDb, false);
			long t2 = System.currentTimeMillis();
			System.out.println("Drop = " + (t1-t0));
			System.out.println("Create = " + (t2-t1));			
		}

		/**
		 * Using the collected domain info, create the various SQL phrases used to build the resulting
		 * flat list table and build the 'INSERT...SELECT...From'  portion of the SQL that does the 
		 * initial table load.
		 * 
		 * @param rootKey	The key for the root level group. Stored in each row and used to determine the
		 * expand/collapse results.
		 * 
		 * @return SqlComponents structure
		 * 
		 */
		public SqlComponents buildSqlComponents(CompoundKey rootKey) {
			SqlComponents cmp = new SqlComponents();

			// Rebuild the data table
			recreateTable();
			// List of column names for the INSERT... part
			StringBuilder columns = new StringBuilder();
			// List of expressions for the SELECT... part.
			StringBuilder expressions = new StringBuilder();
			// List of ?'s for the VALUES... part.
			StringBuilder values = new StringBuilder();

			// Build the lists. mDomains and mExpressions were built in synch with each other.
			for(int i = 0 ; i < mDomains.size(); i++) {
				DomainDefinition d = mDomains.get(i);
				String e = mExpressions.get(i);
				if (i > 0) {
					columns.append(",\n	");
					expressions.append(",\n	");
					values.append(", ");
				}
				columns.append(d.name);
				values.append("?");
				expressions.append(e);
				// This is not strictly necessary, but makes SQL more readable and debugging easier.
				expressions.append(" as ");
				expressions.append(d.name);
			}

			// Build the expression for the root key.
			String keyExpression = "'" + rootKey.prefix;
			for (DomainDefinition d: rootKey.domains) {
				keyExpression += "/'||Coalesce(" + mExpressionMap.get(d) + ",'')";
			}

			// Setup the SQL phrases.
			cmp.rootkeyExpression = keyExpression;
			cmp.insert = "Insert into " + mListTable + " (\n	" + columns.toString() + ",\n	" + DOM_ROOT_KEY + ")";
			cmp.select = "Select\n	" + expressions.toString() + ",\n	" + keyExpression;
			cmp.insertSelect = cmp.insert + "\n " + cmp.select + "\n From\n";
			cmp.insertValues = cmp.insert + "\n    Values (" + values.toString() + ", ?)";

			return cmp;
		}
	}

	/** Convenience expression for the SQL which gets formatted author names in 'Last, Given' form */
	private static final String AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION = "Case "
			+ "When " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES) + " = '' Then " + TBL_AUTHORS.dot(DOM_FAMILY_NAME)
			+ " Else " + TBL_AUTHORS.dot(DOM_FAMILY_NAME) + "|| ', ' || " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES)
			+ " End";

	/** Convenience expression for the SQL which gets formatted author names in 'Given Last' form */
	private static final String AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION = "Case "
			+ "When " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES) + " = '' Then " + TBL_AUTHORS.dot(DOM_FAMILY_NAME)
			+ " Else " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES) + "|| ' ' || " + TBL_AUTHORS.dot(DOM_FAMILY_NAME)
			+ " End";

	///** Convenience expression for the SQL which gets the name of the person to whom a book has been loaned, if any 
	// *  We do not initialize it here because it needs the app context to be setup for R.string.avaiable */
	//private static String LOANED_TO_SQL = null;
	//private static String getLoanedToSql() {
	//	if (LOANED_TO_SQL == null) {
	//		LOANED_TO_SQL = "Coalesce( (Select " + TBL_LOAN.dot(KEY_LOANED_TO) + " From " + TBL_LOAN.ref() + 
	//				" Where " + TBL_LOAN.dot(DOM_BOOK) + " = " + TBL_BOOKS.dot(DOM_ID) + "), '" + BookCatalogueApp.getResourceString(R.string.available) + ")";			
	//	}
	//	return LOANED_TO_SQL;
	//}

	/**
	 * Drop and recreate all the data based on previous criteria
	 */
	public void rebuild() {
		mSummary.recreateTable();

		mNavTable.drop(mDb);
		mNavTable.create(mDb, true);
		
		// Build base data
		mBaseBuildStmt.execute();
		// Rebuild all the rest
		for(SynchronizedStatement s : mLevelBuildStmts)
			s.execute();
	}
	
	/**
	 * Utility function to retrun a glob expression to get the 'year' from a text date field in a standard way.
	 * 
	 * Just look for 4 leading numbers. We don't care about anything else.
	 * 
	 * @param fieldSpec fully qualified field name
	 * 
	 * @return expression
	 */
	private String yearGlob(final String fieldSpec) {
		return "case when " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]*'\n" +
				"	Then substr(" + fieldSpec + ", 1, 4) \n" +
				" else 'UNKNOWN' end";
	}

	/**
	 * Utility function to retrun a glob expression to get the 'month' from a text date field in a standard way.
	 * 
	 * Just look for 4 leading numbers followed by 2 or 1 digit. We don't care about anything else.
	 * 
	 * @param fieldSpec fully qualified field name
	 * 
	 * @return expression
	 */
	private String monthGlob(final String fieldSpec) {
		return "case when " + fieldSpec + 
								" glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789][01234567890]*'\n" +
								"	Then substr(" + fieldSpec + ", 6, 2) \n" +
								" when " + fieldSpec + 
								" glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789]*'\n" +
								"	Then substr(" + fieldSpec + ", 6, 1) \n" +
								" else 'UNKNOWN' end";
	}

	/**
	 * Utility function to retrun a glob expression to get the 'day' from a text date field in a standard way.
	 * 
	 * Just look for 4 leading numbers followed by 2 or 1 digit, and then 1 or two digits. We don't care about anything else.
	 * 
	 * @param fieldSpec fully qualified field name
	 * 
	 * @return expression
	 */
	private String dayGlob(final String fieldSpec) {
		// Just look for 4 leading numbers followed by 2 or 1 digit. We don't care about anything else.
		return "case " +
								" when " + fieldSpec + 
								" glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789][0123456789]*'\n" +
								"	Then substr(" + fieldSpec + ", 9, 2) \n" +
								" when " + fieldSpec + 
								" glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789][0123456789]*'\n" +
								"	Then substr(" + fieldSpec + ", 8, 2) \n" +
								" when " + fieldSpec + 
								" glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789]*'\n" +
								"	Then substr(" + fieldSpec + ", 9, 1) \n" +
								" when " + fieldSpec + 
								" glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789]*'\n" +
								"	Then substr(" + fieldSpec + ", 8, 1) \n" +
								" else " + fieldSpec + " end";
	}

	/**
	 * Clear and the build the temporary list of books based on the passed criteria.
	 * 
	 * @param preferredState		State to display: expanded, collaped or remembered
	 * @param markId				TODO: ID of book to 'mark'. DEPRECATED?
	 * @param bookshelf				Search criteria: limit to shelf
	 * @param authorWhere			Search criteria: additional conditions that apply to authors table
	 * @param bookWhere				Search criteria: additional conditions that apply to book table
	 * @param loaned_to				Search criteria: only books loaned to named person
	 * @param seriesName			Search criteria: only books in named series
	 * @param searchText			Search criteria: book details must in some way contain the passed text
	 * 
	 */
	public void build(int preferredState, long markId, String bookshelf, String authorWhere, String bookWhere, String loaned_to, String seriesName, String searchText) {
		Tracker.handleEvent(this, "build-" + getId(), Tracker.States.Enter);
		try {
			long t0 = System.currentTimeMillis();
	
			// Cleanup searchText
			//
			// Because FTS does not understand locales in all android up to 4.2,
			// we do case folding here using the default locale.
			//
			if (searchText != null) {
				searchText = searchText.toLowerCase(Locale.getDefault());
			}
			
			// Rebuild the main table definition
			mListTable = TBL_BOOK_LIST_DEFN.clone();
			mListTable.setName(mListTable.getName() + "_" + getId());
			mListTable.setType(TableTypes.Temporary); // RELEASE Make sure is TEMPORARY
	
			// Rebuild the navigation table definition
			mNavTable = TBL_ROW_NAVIGATOR_DEFN.clone()
					.addReference(mListTable, DOM_REAL_ROW_ID)
					;
			mNavTable.setName(mNavTable.getName() + "_" + getId());
			mNavTable.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY
	
			// Get a new summary builder utility object
			SummaryBuilder summary = new SummaryBuilder();
	
			// Add the minimum required domains which will have special handling
			mListTable.addDomain(DOM_ID); // Will use default value
			mListTable.addDomain(DOM_ROOT_KEY);	// Will use expression based on first group; determined later
	
			// Add the domains that have simple pre-determined expressions as sources
			summary.addDomain(DOM_LEVEL, Integer.toString(mStyle.size()+1), SummaryBuilder.FLAG_NONE);
			summary.addDomain(DOM_KIND, "" + ROW_KIND_BOOK, SummaryBuilder.FLAG_NONE);
			summary.addDomain(DOM_BOOK, TBL_BOOKS.dot(DOM_ID), SummaryBuilder.FLAG_NONE);
			summary.addDomain(DOM_BOOK_COUNT, "1", SummaryBuilder.FLAG_NONE);
	
			// Will be set to appropriate Group if a Series group exists in style
			BooklistSeriesGroup seriesGroup = null;
			// Will be set to appropriate Group if an Author group exists in style
			BooklistAuthorGroup authorGroup = null;
			// Will be set to TRUE if a LOANED group exists in style
			boolean hasGroupLOANED = false;
	
			// We can not use triggers to fill in headings in API < 8 since SQLite 3.5.9 is broken
			// Allow for the user preferences to override in case another build is borken.
			final boolean useTriggers = Build.VERSION.SDK_INT >= 8 && !OtherPreferences.isBooklistCompatibleMode();
			final int sortDescendingMask = ( useTriggers ? SummaryBuilder.FLAG_SORT_DESCENDING : 0);

			long t0a = System.currentTimeMillis();
	
			// Process each group in the style
			for (BooklistGroup g : mStyle) {
				//
				//	Build each row-kind group. 
				//
				//  ****************************************************************************************
				//  IMPORTANT NOTE: for each row kind, then FIRST SORTED AND GROUPED domain should be the one 
				//					that will be displayed and that level in the UI.
				//  ****************************************************************************************
				//
				switch (g.kind) {
	
				// NEWKIND: Add new kinds to this list
	
				case ROW_KIND_SERIES:
					g.displayDomain = DOM_SERIES_NAME;
					// Save this for later use
					seriesGroup = (BooklistSeriesGroup) g;
					// Group and sort by name
					summary.addDomain(DOM_SERIES_NAME, TBL_SERIES.dot(DOM_SERIES_NAME), SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
					// Group by ID (we want the ID available and there is a *chance* two series will have the same name...with bad data */
					summary.addDomain(DOM_SERIES_ID, TBL_BOOK_SERIES.dot(DOM_SERIES_ID), SummaryBuilder.FLAG_GROUPED);
					// We want the series position in the base data
					summary.addDomain(DOM_SERIES_POSITION, TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION), SummaryBuilder.FLAG_NONE);
					// We want a counter of how many books use the series as a primary series, so we can skip some series
					summary.addDomain(DOM_PRIMARY_SERIES_COUNT,"case when Coalesce(" + TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION) + ",1) == 1 then 1 else 0 end", SummaryBuilder.FLAG_NONE);
					// This group can be given a name of the form 's/<n>' where <n> is the series id, eg. 's/18'.
					g.setKeyComponents("s", DOM_SERIES_ID);
					break;
	
				case ROW_KIND_AUTHOR:
					g.displayDomain = DOM_AUTHOR_FORMATTED;
					// Save this for later use
					authorGroup = (BooklistAuthorGroup) g;
					// Always group & sort by 'Last, Given' expression
					summary.addDomain(DOM_AUTHOR_SORT, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
					// Add the 'formatted' field of the requested type
					if (authorGroup.getGivenName())
						summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
					else
						summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
					// We also want the ID
					summary.addDomain(DOM_AUTHOR_ID, TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID), SummaryBuilder.FLAG_GROUPED);
	
					// This group can be given a name of the form 'a/<n>' where <n> is the author id, eg. 's/18'.
					g.setKeyComponents("a", DOM_AUTHOR_ID);
	
					break;
	
				case ROW_KIND_GENRE:
					g.displayDomain = DOM_GENRE;
					summary.addDomain(DOM_GENRE, TBL_BOOKS.dot(DOM_GENRE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("g", DOM_GENRE);
					break;
	
				case ROW_KIND_LANGUAGE:
					// The domain used to display the data on the screen (not always the underlying domain)
					g.displayDomain = DOM_LANGUAGE;
					// Define how the new field is retrieved and sorted/grouped
					summary.addDomain(DOM_LANGUAGE, TBL_BOOKS.dot(DOM_LANGUAGE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					// Unique name for this field and the source data field
					g.setKeyComponents("lang", DOM_LANGUAGE);
					break;
	
				case ROW_KIND_LOCATION:
					g.displayDomain = DOM_LOCATION;
					summary.addDomain(DOM_LOCATION, "Coalesce(" + TBL_BOOKS.dot(DOM_LOCATION) + ", '')", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("loc", DOM_LOCATION);
					break;
	
				case ROW_KIND_PUBLISHER:
					g.displayDomain = DOM_PUBLISHER;
					summary.addDomain(DOM_PUBLISHER, TBL_BOOKS.dot(DOM_PUBLISHER), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("p", DOM_PUBLISHER);
					break;
	
				case ROW_KIND_FORMAT:
					g.displayDomain = DOM_FORMAT;
					summary.addDomain(DOM_FORMAT, "Coalesce(" + TBL_BOOKS.dot(DOM_FORMAT) + ", '')", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("fmt", DOM_FORMAT);
					break;
	
				case ROW_KIND_READ_AND_UNREAD:
					g.displayDomain = DOM_READ_STATUS;
					String unreadExpr = "Case When " + TBL_BOOKS.dot(DOM_READ) + " = 1\n" +
							"	Then '" + BookCatalogueApp.getResourceString(R.string.booklist_read) + "'\n" +
							" Else '" + BookCatalogueApp.getResourceString(R.string.booklist_unread) + "' end";
					summary.addDomain(DOM_READ_STATUS, unreadExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					// We want the READ flag at the lowest level only. Some bad data means that it may be 0 or 'f', so we don't group by it.
					summary.addDomain(DOM_READ, TBL_BOOKS.dot(DOM_READ), SummaryBuilder.FLAG_NONE);
					g.setKeyComponents("r", DOM_READ_STATUS);
					break;
	
				case ROW_KIND_LOANED:
					// Saved for later to indicate group was present
					hasGroupLOANED = true;
					g.displayDomain = DOM_LOANED_TO;
					summary.addDomain(DOM_LOANED_TO_SORT, "Case When " + TBL_LOAN.dot(KEY_LOANED_TO) + " is null then 1 else 0 end", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					summary.addDomain(DOM_LOANED_TO, "Case When " + TBL_LOAN.dot(KEY_LOANED_TO) + " is null then '" + BookCatalogueApp.getResourceString(R.string.available) + "'" +
										" else '" + BookCatalogueApp.getResourceString(R.string.loaned_to_2) + "' || " + TBL_LOAN.dot(KEY_LOANED_TO) + " end", 
										SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("l", DOM_LOANED_TO);
					break;
	
				case ROW_KIND_TITLE_LETTER:
					g.displayDomain = DOM_TITLE_LETTER;
					String titleLetterExpr = "substr(" + TBL_BOOKS.dot(DOM_TITLE) + ",1,1)";
					summary.addDomain(DOM_TITLE_LETTER, titleLetterExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("t", DOM_TITLE_LETTER);
					break;
	
				case ROW_KIND_YEAR_PUBLISHED:
					g.displayDomain = DOM_PUBLICATION_YEAR;
					// Use our standard glob expression
					String yearPubExpr = yearGlob(TBL_BOOKS.dot(KEY_DATE_PUBLISHED));
					summary.addDomain(DOM_PUBLICATION_YEAR, yearPubExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("yrp", DOM_PUBLICATION_YEAR);
					break;
	
				case ROW_KIND_MONTH_PUBLISHED:
					g.displayDomain = DOM_PUBLICATION_MONTH;
					// Use our standard glob expression
					String monthPubExpr = monthGlob(TBL_BOOKS.dot(KEY_DATE_PUBLISHED));
					summary.addDomain(DOM_PUBLICATION_MONTH, monthPubExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
					g.setKeyComponents("mnp", DOM_PUBLICATION_MONTH);
					break;
	
				case ROW_KIND_YEAR_ADDED:
					g.displayDomain = DOM_ADDED_YEAR;
					// Use our standard glob expression
					String yearAddedExpr = yearGlob(TBL_BOOKS.dot(DOM_ADDED_DATE));
					// TODO: Handle 'DESCENDING'. Requires the navigator construction to use max/min for non-grouped domains that appear in sublevels based on desc/asc.
					// We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
					summary.addDomain(DOM_ADDED_YEAR, yearAddedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask );
					g.setKeyComponents("yra", DOM_ADDED_YEAR);
					break;
	
				case ROW_KIND_MONTH_ADDED:
					g.displayDomain = DOM_ADDED_MONTH;
					// Use our standard glob expression
					String monthAddedExpr = monthGlob(TBL_BOOKS.dot(DOM_ADDED_DATE));
					// We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
					summary.addDomain(DOM_ADDED_MONTH, monthAddedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask );
					g.setKeyComponents("mna", DOM_ADDED_MONTH);
					break;
	
				case ROW_KIND_DAY_ADDED:
					g.displayDomain = DOM_ADDED_DAY;
					// Use our standard glob expression
					String dayAddedExpr = dayGlob(TBL_BOOKS.dot(DOM_ADDED_DATE));
					// We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
					summary.addDomain(DOM_ADDED_DAY, dayAddedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask );
					g.setKeyComponents("dya", DOM_ADDED_DAY);
					break;
	
				case ROW_KIND_YEAR_READ:
					g.displayDomain = DOM_READ_YEAR;
					// TODO: Handle 'DESCENDING'. Requires the navigator construction to use max/min for non-grouped domains that appear in sublevels based on desc/asc.
					// We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
					summary.addDomain(DOM_READ_YEAR, yearGlob(TBL_BOOKS.dot(DOM_READ_END)), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED) ; // | SummaryBuilder.FLAG_SORT_DESCENDING);
					g.setKeyComponents("yrr", DOM_READ_YEAR);
					break;
	
				case ROW_KIND_MONTH_READ:
					g.displayDomain = DOM_READ_MONTH;
					// We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
					summary.addDomain(DOM_READ_MONTH, monthGlob(TBL_BOOKS.dot(DOM_READ_END)), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED ); // | SummaryBuilder.FLAG_SORT_DESCENDING);
					g.setKeyComponents("mnr", DOM_READ_MONTH);
					break;
	
				case ROW_KIND_DAY_READ:
					g.displayDomain = DOM_READ_DAY;
					// We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
					summary.addDomain(DOM_READ_DAY, dayGlob(TBL_BOOKS.dot(DOM_READ_END)), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED ); // | SummaryBuilder.FLAG_SORT_DESCENDING);
					g.setKeyComponents("dyr", DOM_READ_DAY);
					break;
	
				default:
					throw new RuntimeException("Unsupported group type " + g.kind);
	
				}
				// Copy the current groups to this level item; this effectively accumulates 'group by' domains 
				// down each level so that the top has fewest groups and the bottom level has groups for all levels.
				g.groupDomains = summary.cloneGroups();
			}
			long t0b = System.currentTimeMillis();
	
			// Want the UUID for the book so we can get thumbs
			summary.addDomain(DOM_BOOK_UUID, TBL_BOOKS.dot(DOM_BOOK_UUID), SummaryBuilder.FLAG_NONE);
	
			// If we have a book ID to mark, then add the MARK field, and setup the expression.
			if (markId != 0) {
				summary.addDomain(DOM_MARK, TBL_BOOKS.dot(DOM_ID) + " = " + markId, SummaryBuilder.FLAG_NONE);
			}
	
			if (seriesGroup != null) {
				// We want the series number in the base data in sorted order
				
				// Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as a series name. so we convert it to 
				// a float. 
				summary.addDomain(DOM_SERIES_NUM_FLOAT, "cast(" + TBL_BOOK_SERIES.dot(DOM_SERIES_NUM) + " as float)", SummaryBuilder.FLAG_SORTED);		
				// We also add the base name as a sorted field for display purposes and in case of non-numeric data.
				summary.addDomain(DOM_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_SERIES_NUM), SummaryBuilder.FLAG_SORTED);			
			}
			summary.addDomain(DOM_LEVEL, null, SummaryBuilder.FLAG_SORTED);
	
			// Ensure any caller-specified extras (eg. title) are added at the end.
			for(Entry<String,ExtraDomainDetails> d : mExtraDomains.entrySet()) {
				ExtraDomainDetails info = d.getValue();
				int flags;
				if (info.isSorted)
					flags = SummaryBuilder.FLAG_SORTED;
				else
					flags = SummaryBuilder.FLAG_NONE;
				summary.addDomain(info.domain, info.sourceExpression, flags);
			}
			long t0c = System.currentTimeMillis();
	
			//
			// Build the initial insert statement: 'insert into <tbl> (col-list) select (expr-list) from'.
			// We just need to add the 'from' tables. It is a fairly static list, for the most part we just
			// add extra criteria as needed.
			//
			// The seriesLevel and authorLevel fields will influend the nature of the join. If at a later
			// stage some row kinds introduce more table dependencies, a flag (or object) can be set
			// when processing the level to inform the joining code (below) which tables need to be added.
			// 
			// Aside: The sql used prior to using DbUtils is included as comments below the doce that replaced it.
			//
			SqlComponents sqlCmp = summary.buildSqlComponents(mStyle.getGroupAt(0).getCompoundKey());

			long t0d = System.currentTimeMillis();
	
			//
			// Now build the 'join' statement based on the groups and extra criteria
			//
			JoinContext join;
	
			// If there is a bookshelf specified, start the join there. Otherwise, start with the BOOKS table.
			if (!bookshelf.equals("")) {
				join = new JoinContext(TBL_BOOKSHELF)
					.start()
					.join(TBL_BOOK_BOOKSHELF)
					.join(TBL_BOOKS);
			} else {
				join = new JoinContext(TBL_BOOKS).start();
			}
				/*
				if (!bookshelf.equals("")) {
					sql += "	" + DB_TB_BOOKSHELF_AND_ALIAS + " join " + DB_TB_BOOK_BOOKSHELF_AND_ALIAS + 
							" On " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOKSHELF + " = " + ALIAS_BOOKSHELF + "." + KEY_ROWID ;
					sql +=	"    join " + DB_TB_BOOKS_AND_ALIAS + " on " + ALIAS_BOOKS + "." + KEY_ROWID + " = " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOK + "\n";
				} else {
					sql +=	"    " + DB_TB_BOOKS_AND_ALIAS + "\n";
				}
				*/
	
			// If a LOANED level is present, we are ONLY interested in loaned books. So cross it here.
			if (hasGroupLOANED) {
				join.leftOuterJoin(TBL_LOAN);
			}
			
			// Now join with author; we must specify a parent in the join, because the last table 
			// joined was one of BOOKS or LOAN and we don't know which. So we explicitly use books.
			join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);
			// If there is no author group, or the user only wants primary author, get primary only
			if (authorGroup == null || !authorGroup.getAllAuthors()) {
				join.append( "		and " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) + " == 1\n");
			}
			// Join with authors to make the names available
			join.join(TBL_AUTHORS);
			
			// Current table will be authors, so name parent explicitly to join books->book_series.
			join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);
				/*
				sql +=	"    join " + DB_TB_BOOK_AUTHOR_AND_ALIAS + " on " + ALIAS_BOOK_AUTHOR + "." + KEY_BOOK + " = " + ALIAS_BOOKS + "." + KEY_ROWID + "\n" +
						"    join " + DB_TB_AUTHORS_AND_ALIAS + " on " + ALIAS_AUTHORS + "." + KEY_ROWID + " = " + ALIAS_BOOK_AUTHOR + "." + KEY_AUTHOR_ID + "\n";
				sql +=	"    left outer join " + DB_TB_BOOK_SERIES_AND_ALIAS + " on " + ALIAS_BOOK_SERIES + "." + KEY_BOOK + " = " + ALIAS_BOOKS + "." + KEY_ROWID + "\n";
				*/
	
			// If there was no series group, or user requests primary series only, then just get primary series.
			if (seriesGroup == null || !seriesGroup.getAllSeries()) {
				join.append( "		and " + TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION) + " == 1\n");
			}
			// Join with series to get name
			join.leftOuterJoin(TBL_SERIES);
				/*
				if (seriesLevel == null || !seriesLevel.allSeries) {
					sql += "		and " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_POSITION + " == 1\n";
				}
				sql +=	"    left outer join " + DB_TB_SERIES_AND_ALIAS + " on " + ALIAS_SERIES + "." + KEY_ROWID + " = " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_ID;
				*/
	
			// Append the resulting join tables to our initial insert statement
			sqlCmp.join = join.toString();
	
			//
			// Now build the 'where' clause.
			//
			long t0e = System.currentTimeMillis();
			String where = "";
	
			if (!bookshelf.equals("")) {
				if (!where.equals(""))
					where += " and ";
				where += "(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF_NAME) + " = '" + CatalogueDBAdapter.encodeString(bookshelf) + "')";
			}
			if (!authorWhere.equals("")) {
				if (!where.equals(""))
					where += " and ";
				where += "(" + authorWhere + ")";
			}
			if (!bookWhere.equals("")) {
				if (!where.equals(""))
					where += " and ";
				where += "(" + bookWhere + ")";
			}
			if (!loaned_to.equals("")) {
				if (!where.equals(""))
					where += " and ";
				where += "Exists(Select NULL From " + TBL_LOAN.ref() + " Where " + TBL_LOAN.dot(DOM_LOANED_TO) + " = '" + encodeString(loaned_to) + "'" +
						" and " + TBL_LOAN.fkMatch(TBL_BOOKS) + ")";
						// .and()    .op(TBL_LOAN.dot(DOM_BOOK), "=", TBL_BOOKS.dot(DOM_ID)) + ")";
			}
			if (!seriesName.equals("")) {
				if (!where.equals(""))
					where += " and ";
				where += "(" + TBL_SERIES.dot(DOM_SERIES_NAME) + " = '" + encodeString(seriesName) + "')";
			}
			if(!searchText.equals("")) {
				if (!where.equals(""))
					where += " and ";
				where += "(" + TBL_BOOKS.dot(DOM_ID) + " in (select docid from " + TBL_BOOKS_FTS + " where " + TBL_BOOKS_FTS + " match '" + encodeString(searchText) + "'))";
			}
	
			// Add support for book filter: READ
			{
				String extra = null;
				switch(mStyle.getReadFilter()) {
					case BooklistStyle.FILTER_READ:
						extra = TBL_BOOKS.dot(DOM_READ) + " = 1\n";
						break;
					case BooklistStyle.FILTER_UNREAD:
						extra = TBL_BOOKS.dot(DOM_READ) + " = 0\n";
						break;
					default:
						break;
				}
				if (extra != null) {
					if (!where.equals(""))
						where += " and ";
					where += " " + extra;
				}
			}
	
			// If we got any conditions, add them to the initial insert statement
			if (!where.equals("")) {
				sqlCmp.where += " where " + where.toString();
			} else {
				sqlCmp.where = "";
			}
	
			long t1 = System.currentTimeMillis();
			// Check if the collation we use is case sensitive; bug introduced in ICS was to make UNICODE not CI.
			// Due to bugs in other language sorting, we are now forced to use a different collation  anyway, but
			// we still check if it is CI.
			boolean collationIsCs = BookCatalogueApp.isCollationCaseSensitive(mDb.getUnderlyingDatabase());
	
			// List of column names appropriate for 'Order By' clause
			String sortColNameList;
			// List of column names appropriate for 'Create Index' column list
			String sortIndexColumnList;
	
			// Process the 'sort-by' columns into a list suitable for a sort-by statement, or index
			{
				final ArrayList<SortedDomainInfo> sort = summary.getSortedColumns();
				final StringBuilder sortCols = new StringBuilder();
				final StringBuilder indexCols = new StringBuilder();
				for (SortedDomainInfo sdi: sort) {
					indexCols.append(sdi.domain.name);
					if (sdi.domain.type.toLowerCase().equals("text")) {
						indexCols.append(CatalogueDBAdapter.COLLATION);
	
						// *If* collations is case-sensitive, handle it.
						if (collationIsCs)
							sortCols.append("lower(");
						sortCols.append(sdi.domain.name);
						if (collationIsCs)
							sortCols.append(")");
						sortCols.append(CatalogueDBAdapter.COLLATION);
					} else {
						sortCols.append(sdi.domain.name);					
					}
					if (sdi.isDescending) {
						indexCols.append(" desc");					
						sortCols.append(" desc");					
					}
					sortCols.append(", ");
					indexCols.append(", ");
				}
				sortCols.append(DOM_LEVEL.name);
				indexCols.append(DOM_LEVEL.name);
				sortColNameList = sortCols.toString();
				sortIndexColumnList = indexCols.toString();
			}
	
			// Process the group-by columns suitable for a group-by statement or index
			{
				final ArrayList<DomainDefinition> group = summary.cloneGroups();
				final StringBuilder groupCols = new StringBuilder();;
				for (DomainDefinition d: group) {
					groupCols.append(d.name);
					groupCols.append(CatalogueDBAdapter.COLLATION);
					groupCols.append(", ");
				}
				groupCols.append( DOM_LEVEL.name );
				//mGroupColumnList = groupCols.toString();
			}
	
			String ix1Sql = "Create Index " + mListTable + "_IX1 on " + mListTable + "(" + sortIndexColumnList + ")";
			/* Indexes that were tried. None had a substantial impact with 800 books.
			String ix1aSql = "Create Index " + mListTable + "_IX1a on " + mListTable + "(" + DOM_LEVEL + ", " + mSortColumnList + ")";
			String ix2Sql = "Create Unique Index " + mListTable + "_IX2 on " + mListTable + "(" + DOM_BOOK + ", " + DOM_ID + ")";
	
			String ix3Sql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList + ")";
			String ix3aSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList + ")";
			String ix3bSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_LEVEL + ")";
			String ix3cSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_ROOT_KEY + CatalogueDBAdapter.COLLATION + ")";
			String ix3dSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList +  ", " + DOM_ROOT_KEY + ")";
			String ix3eSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_ROOT_KEY + "," + DOM_LEVEL + ")";
			String ix4Sql = "Create Index " + mListTable + "_IX4 on " + mListTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")";
			*/
	
			// We are good to go.
			long t1a = System.currentTimeMillis();
			//mDb.execSQL("PRAGMA synchronous = OFF"); -- Has very little effect
			SyncLock txLock = mDb.beginTransaction(true);
			long t1b = System.currentTimeMillis();
			try {
				//
				// This is experimental code that replaced the INSERT...SELECT with a cursor
				// and an INSERT statement. It was literally 10x slower, largely due to the
				// Android implementation of SQLiteStatement and the way it handles parameter
				// binding. Kept for future experiments
				//
				////
				//// Code to manually insert each row
				////
				//double TM0 = System.currentTimeMillis();
				//String selStmt = sqlCmp.select + " from " + sqlCmp.join + " " + sqlCmp.where + " Order by " + sortIndexColumnList;
				//final Cursor selCsr = mDb.rawQuery(selStmt);
				//final SynchronizedStatement insStmt = mDb.compileStatement(sqlCmp.insertValues);
				////final String baseIns = sqlCmp.insert + " Values (";
				////SQLiteStatement insStmt = insSyncStmt.getUnderlyingStatement();
				//
				//final int cnt = selCsr.getColumnCount();
				////StringBuilder insBuilder = new StringBuilder();
				//while(selCsr.moveToNext()) {
				//	//insBuilder.append(baseIns);
				//	for(int i = 0; i < cnt; i++) {
				//		//if (i > 0) {
				//		//	insBuilder.append(", ");
				//		//}
				//		// THIS IS SLOWER than using Strings!!!!
				//		//switch(selCsr.getType(i)) {
				//		//case android.database.Cursor.FIELD_TYPE_NULL:
				//		//	insStmt.bindNull(i+1);							
				//		//	break;
				//		//case android.database.Cursor.FIELD_TYPE_FLOAT:
				//		//	insStmt.bindDouble(i+1, selCsr.getDouble(i));
				//		//	break;
				//		//case android.database.Cursor.FIELD_TYPE_INTEGER:
				//		//	insStmt.bindLong(i+1, selCsr.getLong(i));
				//		//	break;
				//		//case android.database.Cursor.FIELD_TYPE_STRING:
				//		//	insStmt.bindString(i+1, selCsr.getString(i));
				//		//	break;
				//		//case android.database.Cursor.FIELD_TYPE_BLOB:
				//		//	insStmt.bindNull(i+1);							
				//		//	break;
				//		//}
				//		final String v = selCsr.getString(i);
				//		if (v == null) {
				//			//insBuilder.append("NULL");
				//			insStmt.bindNull(i+1);
				//		} else {
				//			//insBuilder.append("'");
				//			//insBuilder.append(CatalogueDBAdapter.encodeString(v));
				//			//insBuilder.append("'");
				//			insStmt.bindString(i+1, v);
				//		}
				//	}
				//	//insBuilder.append(")");
				//	//mDb.execSQL(insBuilder.toString());
				//	//insBuilder.setLength(0);
				//	insStmt.execute();
				//}
				//selCsr.close();
				//double TM1 = System.currentTimeMillis();
				//System.out.println("Time to MANUALLY INSERT: " + (TM1-TM0));

				mLevelBuildStmts = new ArrayList<SynchronizedStatement>();

				// Build the lowest level summary using our initial insert statement
				long t2;
				long t2a[] = new long[mStyle.size()];
				long t3;

				if (useTriggers) {
					// If we are using triggers, then we insert them in order and rely on the 
					// triggers to build the summary rows in the correct place.
					makeTriggers(summary);
					mBaseBuildStmt = mStatements.add("mBaseBuildStmt", sqlCmp.insertSelect + sqlCmp.join + sqlCmp.where + " order by " + sortColNameList);
					//System.out.println("Base Build:\n" + sql);
					mBaseBuildStmt.execute();
					t2 = System.currentTimeMillis();
					t3=t2;
				} else {
					// Without triggers we just get the base rows and add summary later
					mBaseBuildStmt = mStatements.add("mBaseBuildStmt", sqlCmp.insertSelect + sqlCmp.join + sqlCmp.where);
					//System.out.println("Base Build:\n" + sql);
					mBaseBuildStmt.execute();
					t2 = System.currentTimeMillis();

					// Now build each summary level query based on the prior level.
					// We build and run from the bottom up.

					int pos=0;
					// Loop from innermost group to outermost, building summary at each level
					for (int i = mStyle.size()-1; i >= 0; i--) {
						final BooklistGroup g = mStyle.getGroupAt(i);
						final int levelId = i + 1;
						// cols is the list of column names for the 'Insert' and 'Select' parts
						String cols = "";
						// collatedCols is used for the group-by
						String collatedCols = "";
						
						// Build the column lists for this group
						for(DomainDefinition d  : g.groupDomains) {
							if (!collatedCols.equals(""))
								collatedCols += ",";
							cols += ",\n	" + d.name;
							collatedCols += "\n	" + d.name + CatalogueDBAdapter.COLLATION;
						}
						// Construct the summarization statement for this group
						String sql = "Insert Into " + mListTable + "(\n	" + DOM_LEVEL + ",\n	" + DOM_KIND + 
								cols + "," + DOM_ROOT_KEY +
								")" +
								"\n select " + levelId + " as " + DOM_LEVEL + ",\n	" + g.kind + " as " + DOM_KIND +
								cols + "," + DOM_ROOT_KEY +
								"\n from " + mListTable + "\n " + " where level = " + (levelId+1) +
								"\n Group by " + collatedCols + "," + DOM_ROOT_KEY + CatalogueDBAdapter.COLLATION;
								//"\n Group by " + DOM_LEVEL + ", " + DOM_KIND + collatedCols;

						// Save, compile and run this statement
						SynchronizedStatement stmt = mStatements.add("L" + i, sql);
						mLevelBuildStmts.add(stmt);
						stmt.execute();
						t2a[pos++] = System.currentTimeMillis();
					}
				
					// Build an index
					t3 = System.currentTimeMillis();
					// Build an index if it will help sorting
					// - *If* collation is case-sensitive, don't bother with index, since everything is wrapped in lower().
					// ENHANCE: ICS UNICODE: Consider adding a duplicate _lc (lower case) column to the SUMMARY table. Ugh.
					if (!collationIsCs) {
						SynchronizedStatement stmt = mStatements.add("ix1", ix1Sql);
						mLevelBuildStmts.add(stmt);
						stmt.execute();				
					}
				}	
				
				// Analyze the table
				long t3a = System.currentTimeMillis();
				mDb.execSQL("analyze " + mListTable);
				long t3b = System.currentTimeMillis();
				
				// Now build a lookup table to match row sort position to row ID. This is used to match a specific
				// book (or other row in result set) to a position directly without having to scan the database. This
				// is especially useful in expan/collapse operations.
				mNavTable.drop(mDb);
				mNavTable.create(mDb, true);

				String sortExpression;
				if (useTriggers) {
					sortExpression = mListTable.dot(DOM_ID);
				} else {
					sortExpression = sortColNameList;
				}

				// TODO: Rebuild with state preserved is SLOWEST option. Need a better way to preserve state.
				String insSql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) + 
						" Select " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
						" ,\n	Case When " + DOM_LEVEL + " = 1 Then 1 \n" +
						"	When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0\n	Else 1 end,\n "+ 
						"	Case When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0 Else 1 end\n"+
						" From " + mListTable.ref() + "\n	left outer join " + TBL_BOOK_LIST_NODE_SETTINGS.ref() + 
						"\n		On " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " = " + mListTable.dot(DOM_ROOT_KEY) +
						"\n			And " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_KIND) + " = " + mStyle.getGroupAt(0).kind +
						"\n	Order by " + sortExpression;
				// Always save the state-preserving navigator for rebuilds
				SynchronizedStatement navStmt = mStatements.add("InsNav", insSql);
				mLevelBuildStmts.add(navStmt);
	
				// On first-time builds, get the pref-based list
				if (preferredState == BooklistPreferencesActivity.BOOKLISTS_ALWAYS_COLLAPSED) {
					String sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) + 
							" Select " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
							" ,\n	Case When " + DOM_LEVEL + " = 1 Then 1 Else 0 End, 0\n" +
							" From " + mListTable.ref() +
							"\n	Order by " + sortExpression;				
					mDb.execSQL(sql);
				} else if (preferredState == BooklistPreferencesActivity.BOOKLISTS_ALWAYS_EXPANDED) {
					String sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) + 
							" Select " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
							" , 1, 1 \n" +
							" From " + mListTable.ref() +
							"\n	Order by " + sortExpression;
					mDb.execSQL(sql);
				} else {
					// Use already-defined SQL
					navStmt.execute();
				}
	
				long t4 = System.currentTimeMillis();
				// Create index on nav table
				{
					String sql = "Create Index " + mNavTable + "_IX1" + " On " + mNavTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")";
					SynchronizedStatement ixStmt = mStatements.add("navIx1", sql);
					mLevelBuildStmts.add(ixStmt);
					ixStmt.execute();				
				}
	
				long t4a = System.currentTimeMillis();
				{
					// Essential for main query! If not present, will make getCount() take ages because main query is a cross with no index.
					String sql = "Create Unique Index " + mNavTable + "_IX2" + " On " + mNavTable + "(" + DOM_REAL_ROW_ID + ")";
					SynchronizedStatement ixStmt = mStatements.add("navIx2", sql);
					mLevelBuildStmts.add(ixStmt);
					ixStmt.execute();
				}
	
				long t4b = System.currentTimeMillis();
				mDb.execSQL("analyze " + mNavTable);
				long t4c = System.currentTimeMillis();
	
				long t8 = System.currentTimeMillis();
				//stmt = makeStatement(ix1Sql);
				//mLevelBuildStmts.add(stmt);
				//stmt.execute();
				long t9 = System.currentTimeMillis();
				//mDb.execSQL(ix2Sql);
				long t10 = System.currentTimeMillis();
				//mDb.execSQL("analyze " + mTableName);
				long t11 = System.currentTimeMillis();
				
				System.out.println("T0a: " + (t0a-t0));
				System.out.println("T0b: " + (t0b-t0a));
				System.out.println("T0c: " + (t0c-t0b));
				System.out.println("T0d: " + (t0d-t0c));
				System.out.println("T0e: " + (t0e-t0d));
				System.out.println("T1: " + (t1-t0));
				System.out.println("T1a: " + (t1a-t1));
				System.out.println("T1b: " + (t1b-t1a));
				System.out.println("T1c: " + (t2-t1b));
				System.out.println("T2a[0]: " + (t2a[0]-t2));
				for(int i = 1; i < mStyle.size(); i++) {
					System.out.println("T2a[" + i + "]: " + (t2a[i]-t2a[i-1]));				
				}
				System.out.println("T3: " + (t3-t2a[mStyle.size()-1]));
				System.out.println("T3a: " + (t3a-t3));
				System.out.println("T3b: " + (t3b-t3a));
				System.out.println("T4: " + (t4-t3b));
				System.out.println("T4a: " + (t4a-t4));
				System.out.println("T4b: " + (t4b-t4a));
				System.out.println("T4c: " + (t4c-t4b));
				//System.out.println("T5: " + (t5-t4));
				//System.out.println("T6: " + (t6-t5));
				//System.out.println("T7: " + (t7-t6));
				System.out.println("T8: " + (t8-t4c));
				System.out.println("T9: " + (t9-t8));
				System.out.println("T10: " + (t10-t9));
				System.out.println("T11: " + (t11-t10));
	
				mDb.setTransactionSuccessful();
	
				mSummary = summary;
	
				//if (markId > 0)
				//	ensureBookVisible(markId);
				
				// Get the final result			
				//return getList();
				//sql = "select * from " + mTableName + " Order by " + mSortColumnList;
	
				//return (BooklistCursor) mDb.rawQueryWithFactory(mBooklistCursorFactory, sql, EMPTY_STRING_ARRAY, "");					
	
				return;
	
			} finally {
				mDb.endTransaction(txLock);
				//mDb.execSQL("PRAGMA synchronous = FULL");
	
			}
		} finally {
			Tracker.handleEvent(this, "build-" + getId(), Tracker.States.Exit);			
		}
	}

	private SynchronizedStatement mDeleteListNodeSettingsStmt = null;

	/**
	 * Clear the list of expanded nodes in the current view
	 */
	private void deleteListNodeSettings() {
		SyncLock l = null;

		try {
			if (!mDb.inTransaction())
				l = mDb.beginTransaction(true);

			int kind = mStyle.getGroupAt(0).kind;
			if (mDeleteListNodeSettingsStmt == null) {
				String sql = "Delete from " + TBL_BOOK_LIST_NODE_SETTINGS + " Where kind = ?";
				mDeleteListNodeSettingsStmt = mStatements.add("mDeleteListNodeSettingsStmt", sql);
			}
			mDeleteListNodeSettingsStmt.bindLong(1, kind);
			mDeleteListNodeSettingsStmt.execute();
			if (l != null)
				mDb.setTransactionSuccessful();
		} finally {
			if (l != null)
				mDb.endTransaction(l);
		}
	}

	/**
	 * Build a collection of triggers on the list table designed to fill in the summary/header records
	 * as the data records are added in sorted order.
	 * 
	 * This approach is both a performance improvement and a means to allow DESCENDING sort orders.
	 * 
	 * @param summary
	 */
	private void makeTriggers(SummaryBuilder summary) {
		/*
		 * Create a trigger to forward all row detais to real table
		 */
		/*
		String fullInsert = "Insert into " + mListTable + "(";
		{
			String fullValues = "Values (";
			boolean firstCol = true;
			for (DomainDefinition d: mListTable.domains) {
				if (!d.equals(DOM_ID)) {
					if (firstCol)
						firstCol = false;
					else {
						fullInsert+=", ";
						fullValues += ", ";
					}
					fullInsert += d; 
					fullValues += "new." + d; 
				}
			}
			fullInsert += ") " + fullValues + ");";			

			String tgForwardName = "header_Z_F";
			mDb.execSQL("Drop Trigger if exists " + tgForwardName);
			String tgForwardSql = "Create Trigger " + tgForwardName + " instead of  insert on " + TBL_BOOK_LIST_DEFN + " for each row \n" +
					"	Begin\n" +
					"		" + fullInsert + "\n" +
					"	End";
			SQLiteStatement stmt = mStatements.add("TG " + tgForwardName, tgForwardSql);
			mLevelBuildStmts.add(stmt);
			stmt.execute();			
		}
		*/

		// Now make some BEFORE INSERT triggers to build hierarchy; no trigger on root level (index = 0).
		//String[] tgLines = new String[mLevels.size()];

		// Name of a table to store the snapshot of the most recent/current row headings
		final String currTblName =  mListTable + "_curr";
		// List of cols we sort by
		String sortedCols = "";
		// SQL statement to update the 'current' table
		String currInsertSql = "";
		// List of domain names for sorting
		HashSet<String> sortedDomainNames = new HashSet<String>();
		// Build the 'current' header table definition and the sort column list 
		for(SortedDomainInfo i: summary.getSortedColumns()) {
			if (!sortedDomainNames.contains(i.domain.name)) {
				sortedDomainNames.add(i.domain.name);
				if (!sortedCols.equals("")) {
					sortedCols += ", ";
					currInsertSql += ", ";
				}
				sortedCols += i.domain.name;
				currInsertSql += "new." + i.domain.name;				
			}
		}

		//
		// Create a temp table to store the most recent header details from the last row.
		// We use this in determining what needs to be inserted as header records for 
		// any given row.
		//
		// This is just a simple technique to provide persistent context to the trigger.
		//
		mDb.execSQL("Create Temp Table " + currTblName + " (" + sortedCols + ")");

		//mDb.execSQL("Create Unique Index " + mListTable + "_IX_TG1 on " + mListTable + "(" + DOM_LEVEL + ", " + sortedCols + ", " + DOM_BOOK + ")");

		// For each grouping, starting with the lowest, build a trigger to update the next level up as necessary
		for (int i = mStyle.size()-1; i >= 0; i--) {
			// Get the group
			final BooklistGroup l = mStyle.getGroupAt(i);
			// Get the level number for this group
			final int levelId = i + 1;
			// Create an INSERT statement for the next level up
			String insertSql = "Insert into " + mListTable + "( " + DOM_LEVEL + "," + DOM_KIND + ", " + DOM_ROOT_KEY + "\n";

			// EXPERIMENTAL: If inserting with forwarding table: String insertSql = "Insert into " + TBL_BOOK_LIST_DEFN + "( " + DOM_LEVEL + "," + DOM_KIND + ", " + DOM_ROOT_KEY + "\n";
			// EXPERIMENTAL: If inserting in one trigger using multiple 'exists': String valuesSql = levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";

			// Create the VALUES statement for the next levekl up
			String valuesSql = "Values (" + levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";
			// Create the conditional to detect if next level up is already defined (by checking the 'current' record/table)
			String conditionSql = "";// "l." + DOM_LEVEL + " = " + levelId + "\n";
			// Update the statement components
			for(DomainDefinition d  : l.groupDomains) {
				insertSql += ", " + d;
				valuesSql += ", new." + d;
				// Only update the 'condition' part if it is part of the SORT list
				if (sortedDomainNames.contains(d.name)) {
					if (!conditionSql.equals(""))
						conditionSql += "	and ";
					conditionSql += "l." + d + " = new." + d + CatalogueDBAdapter.COLLATION + "\n";					
				}
			}
			//insertSql += ")\n	Select " + valuesSql + " Where not exists(Select 1 From " + mListTable + " l where " + conditionSql + ")";
			//tgLines[i] = insertSql;

			insertSql += ")\n" + valuesSql + ")";
			String tgName = "header_A_tgL" + i;
			// Drop trigger if necessary
			mDb.execSQL("Drop Trigger if exists " + tgName);
			// EXPERIMENTAL: If using forwarding table: String tgSql = "Create Trigger " + tgName + " instead of  insert on " + TBL_BOOK_LIST_DEFN + " for each row when new.level = " + (levelId+1) +

			// Create the trigger
			String tgSql = "Create Temp Trigger " + tgName + " before insert on " + mListTable + " for each row when new.level = " + (levelId+1) +
					" and not exists(Select 1 From " + currTblName + " l where " + conditionSql + ")\n" +
					"	Begin\n" +
					"		" + insertSql + ";\n" +
					"	End";
			SynchronizedStatement stmt = mStatements.add("TG " + tgName, tgSql);
			mLevelBuildStmts.add(stmt);
			stmt.execute();
		}
		
		// Create a trigger to maintaint the 'current' value -- just delete and insert
		String currTgName = mListTable + "_TG_ZZZ";
		mDb.execSQL("Drop Trigger if exists " + currTgName);
		String tgSql = "Create Temp Trigger " + currTgName + " after insert on " + mListTable + " for each row when new.level = " + mStyle.size() +
				//" and not exists(Select 1 From " + currTblName + " l where " + conditionSql + ")\n" +
				"	Begin\n" +
				"		Delete from " + currTblName + ";\n" +
				"		Insert into " + currTblName + " values (" + currInsertSql + ");\n" +
				"	End";

		SynchronizedStatement stmt = mStatements.add(currTgName, tgSql);
		mLevelBuildStmts.add(stmt);
		stmt.execute();
	}
	
	private SynchronizedStatement mSaveListNodeSettingsStmt = null;
	/**
	 * Save the currently expanded top level nodes, and the top level group kind, to the database
	 * so that the next time this view is opened, the user will see the same opened/closed nodes.
	 */
	public void saveListNodeSettings() {
		SyncLock l = null;
		try {
			if (!mDb.inTransaction())
				l = mDb.beginTransaction(true);

			deleteListNodeSettings();

			if (mSaveListNodeSettingsStmt == null) {
				String sql = TBL_BOOK_LIST_NODE_SETTINGS.getInsert(DOM_KIND,DOM_ROOT_KEY) + 
						" Select Distinct ?, " + DOM_ROOT_KEY + " From " + mNavTable + " Where expanded = 1 and level = 1";
				mSaveListNodeSettingsStmt = mStatements.add("mSaveListNodeSettingsStmt", sql);
			}
			int kind = mStyle.getGroupAt(0).kind;

			mSaveListNodeSettingsStmt.bindLong(1, kind);
			mSaveListNodeSettingsStmt.execute();			
			if (l != null)
				mDb.setTransactionSuccessful();
		} finally {
			if (l != null)
				mDb.endTransaction(l);
		}
	}

	private SynchronizedStatement mDeleteListNodeSettingStmt = null;

	/**
	 * Clear the list of expanded nodes in the current view
	 */
	private void deleteListNodeSetting(long rowId) {
		SyncLock l = null;

		try {
			if (!mDb.inTransaction())
				l = mDb.beginTransaction(true);

			int kind = mStyle.getGroupAt(0).kind;
			if (mDeleteListNodeSettingStmt == null) {
				String sql = "Delete from " + TBL_BOOK_LIST_NODE_SETTINGS + " Where kind = ? and " + DOM_ROOT_KEY +
						" In (Select Distinct " + DOM_ROOT_KEY + " From " + mNavTable + " Where " + DOM_ID + " = ?)"
						;
				mDeleteListNodeSettingStmt = mStatements.add("mDeleteSettingsStmt", sql);
			}
			mDeleteListNodeSettingStmt.bindLong(1, kind);
			mDeleteListNodeSettingStmt.bindLong(2, rowId);
			mDeleteListNodeSettingStmt.execute();		
		} finally {
			if (l != null)
				mDb.endTransaction(l);
		}
	}

	private SynchronizedStatement mSaveListNodeSettingStmt = null;
	/**
	 * Save the specified node state.
	 */
	private void saveListNodeSetting(long rowId, boolean expanded) {
		SyncLock l = null;
		try {
			if (!mDb.inTransaction())
				l = mDb.beginTransaction(true);

			deleteListNodeSetting(rowId);

			if (mSaveListNodeSettingStmt == null) {
				String sql = TBL_BOOK_LIST_NODE_SETTINGS.getInsert(DOM_KIND,DOM_ROOT_KEY) + 
						" Select ?, " + DOM_ROOT_KEY + " From " + mNavTable + " Where expanded = 1 and level = 1 and " + DOM_ID + " = ?";
				mSaveListNodeSettingStmt = mStatements.add("mSaveListNodeSettingStmt", sql);
			}
			int kind = mStyle.getGroupAt(0).kind;

			mSaveListNodeSettingStmt.bindLong(1, kind);
			mSaveListNodeSettingStmt.bindLong(2, rowId);
			mSaveListNodeSettingStmt.execute();			
			mDb.setTransactionSuccessful();
		} finally {
			if (l != null)
				mDb.endTransaction(l);
		}
	}

	/**
	 * Record containing details of the positions of all instances of a single book.
	 * 
	 * @author Philip Warner
	 */
	public static class BookRowInfo {
		public int absolutePosition;
		public boolean visible;
		public int listPosition;
		BookRowInfo(int absPos, int listPos, int vis) {
			absolutePosition = absPos;
			listPosition = listPos;
			visible = (vis == 1);
		}
	}

	/**
	 * Get all positions at which the specified book appears.
	 * 
	 * @param bookId
	 * 
	 * @return		Array of row details, including absolute positions and visibility. Null if not present
	 */
	public ArrayList<BookRowInfo> getBookAbsolutePositions(long bookId) {
		String sql = "select " + mNavTable.dot(DOM_ID) + ", " + mNavTable.dot(DOM_VISIBLE) + " From " + mListTable + " bl " 
				+ mListTable.join(mNavTable) + " Where " + mListTable.dot(DOM_BOOK) + " = " + bookId;

		Cursor c = mDb.rawQuery(sql, EMPTY_STRING_ARRAY);
		try {
			ArrayList<BookRowInfo> rows = new ArrayList<BookRowInfo>();
			if (c.moveToFirst()) {
				do {
					int absPos = c.getInt(0) - 1;
					rows.add(new BookRowInfo(absPos, getPosition(absPos), c.getInt(1)));
				} while (c.moveToNext());
				return rows;
			} else {
				return null;
			}
		} finally {
			c.close();
		}
	}

	/**
	 * Utility routine to return a list of column names that will be in the list
	 * for cursor implementations.
	 */
	public String[] getListColumnNames() {
		// Get the domains
		ArrayList<DomainDefinition> domains = mListTable.getDomains();
		// Make the array and allow for ABSOLUTE_POSITION
		String[] names = new String[domains.size()+1];
		// Copy domains
		for(int i = 0; i < domains.size(); i++)
			names[i] = domains.get(i).name;
		// Add ABSOLUTE_POSITION
		names[domains.size()] = DOM_ABSOLUTE_POSITION.name;
		return names;
	}

	/**
	 * Return a list cursor starting at a given offset, using a given limit.
	 */
	public BooklistCursor getOffsetCursor(int position, int size) {
		// Get the domains
		StringBuilder domains = new StringBuilder();
		final String prefix = mListTable.getAlias() + ".";
		for(DomainDefinition d: mListTable.getDomains()) {
			domains.append(prefix);
			domains.append(d.name);
			domains.append(" as ");
			domains.append(d.name);
			domains.append(", ");
		}

		// Build the SQL, adding ABS POS.
		final String sql = "select " + domains + " (" + mNavTable.dot(DOM_ID) + " - 1) As " + DOM_ABSOLUTE_POSITION + 
				" from " + mListTable.ref() + mListTable.join(mNavTable) + 
				" Where " + mNavTable.dot(DOM_VISIBLE) + " = 1 Order by " + mNavTable.dot(DOM_ID) +
				" Limit " + size + " Offset " + position 
				;	

		// Get and return the cursor
		return (BooklistCursor) mDb.rawQueryWithFactory(mBooklistCursorFactory, sql, EMPTY_STRING_ARRAY, "");		
	}

	/**
	 * Return a BooklistPseudoCursor instead of a real cursor.
	 */
	public BooklistPseudoCursor getList() {
		return new BooklistPseudoCursor(this);		
	}


//	private void psuedoCursor(String sql, int pos) {
//		long tc0 = System.currentTimeMillis();
//		final BooklistCursor cfoo = (BooklistCursor) mDb.rawQueryWithFactory(mBooklistCursorFactory, sql + " Limit 80 offset " + pos, EMPTY_STRING_ARRAY, "");
//		long tc1 = System.currentTimeMillis();
//		long cCnt = cfoo.getCount();
//		long tc2 = System.currentTimeMillis();
//		cfoo.close();
//
//		System.out.println("Limit cursor @" + pos + " create in " + (tc1 - tc0) + "ms, count (" + cCnt + ") in " + (tc2-tc1) + "ms");			
//	}

//	private void pseudoCount(TableDefinition table, String condition) {
//		String foo = "Select count(*) from " + table + (condition == null ? "" : condition);
//		pseudoCount(table.getName(), foo);
//	}


	/**
	 * All pseudo list cursors work with the static data in the tenp. table. Get the
	 * logical count of rows using a simple query rather than scanning the entire result set.
	 */
	public int getPseudoCount() {
		return pseudoCount("NavTable", "Select count(*) from " + mNavTable + " Where " + DOM_VISIBLE + " = 1");
	}

	/**
	 * Get the number of book records in the list
	 */
	public int getBookCount() {
		return pseudoCount("ListTableBooks", "Select count(*) from " + mListTable + " Where " + DOM_LEVEL + " = " + (mStyle.size()+1) );
	}

	/**
	 * Get the number of unique book records in the list
	 */
	public int getUniqueBookCount() {
		return pseudoCount("ListTableUniqueBooks", "Select count(distinct " + DOM_BOOK + ") from " + mListTable + " Where " + DOM_LEVEL + " = " + (mStyle.size()+1) );
	}

	/**
	 * Utiity routine to perform a single count query.
	 */
	private int pseudoCount(String name, String foo) {
		long tc0 = System.currentTimeMillis();
		SynchronizedStatement fooStmt = mDb.compileStatement(foo);
		int cnt = (int)fooStmt.simpleQueryForLong();
		fooStmt.close();
		long tc1 = System.currentTimeMillis();
		System.out.println("Pseudo-count (" + name + ") = " + cnt + " completed in " + (tc1 - tc0) + "ms");
		return cnt;
	}

	/**
	 * Using a 1-based level index, retrieve the domain that is displayed in the summary for the specified level.
	 * 
	 * @param level		1-based level to check
	 * 
	 * @return			Name of the display field for this level
	 */
	public DomainDefinition getDisplayDomain(int level) {
		return mStyle.getGroupAt(level-1).displayDomain;
	}

	/**
	 * Get the number of levels in the list, including the 'base' books level
	 * 
	 * @return
	 */
	public int numLevels() {
		return mStyle.size()+1;
	}

	private SynchronizedStatement mGetPositionCheckVisibleStmt = null;
	private SynchronizedStatement mGetPositionStmt = null;
	/**
	 * Given an absolute position, return the actual list position for a row taking into
	 * account invisible rows.
	 * 
	 * @param absolutePosition	Abs. position to check
	 * 
	 * @return		Actual list position.
	 */
	public int getPosition(int absolutePosition) {
		if (mGetPositionCheckVisibleStmt == null) {
			String sql = "Select visible from " + mNavTable + " Where " + DOM_ID + " = ?";
			mGetPositionCheckVisibleStmt = mStatements.add("mGetPositionCheckVisibleStmt", sql);	
		}
		if (mGetPositionStmt == null) {
			String sql = "Select count(*) From " + mNavTable + " Where visible = 1 and " + DOM_ID + " < ?";
			mGetPositionStmt = mStatements.add("mGetPositionStmt", sql);					
		}

		// Check the absolute position is visible
		final long rowId = absolutePosition + 1;
		mGetPositionCheckVisibleStmt.bindLong(1, rowId);
		long isVis;
		try {
			isVis = mGetPositionCheckVisibleStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			// row is not in the current list at all
			isVis = 0;
		}
		
		// Count the number of *visible* rows *before* the specified one.
		mGetPositionStmt.bindLong(1, rowId);
		int newPos = (int)mGetPositionStmt.simpleQueryForLong();
		// If specified row is visible, the the position is the count, otherwise, count -1 (ie. the
		// previous visible row).
		if (isVis == 1) 
			return newPos;
		else
			return newPos > 0 ? newPos - 1 : 0;
	}

	private SynchronizedStatement mGetNodeRootStmt = null;
	/**
	 * Find the visible root node for a given absolute position and ensure it is visible.
	 * 
	 * @param absPos
	 */
	public void ensureAbsolutePositionVisible(long absPos) {
		// If <0 then no previous node.
		if (absPos < 0)
			return;

		final long rowId = absPos + 1;

		if (mGetNodeRootStmt == null) {
			String sql = "Select " + DOM_ID + "||'/'||" + DOM_EXPANDED + " From " + mNavTable + " Where " + DOM_LEVEL + " = 1 and " + DOM_ID + " <= ? Order by " + DOM_ID + " Desc Limit 1";
			mGetNodeRootStmt = mStatements.add("mGetNodeRootStmt", sql);
		}

		// Get the root node, and expanded flag
		mGetNodeRootStmt.bindLong(1, rowId);
		String info[] = mGetNodeRootStmt.simpleQueryForString().split("/");
		long rootId = Long.parseLong(info[0]);
		long isExp = Long.parseLong(info[1]);
		// If root node is not the node we are checking, and root node is not expanded, expand it.
		if (rootId != rowId && isExp == 0)
			toggleExpandNode(rootId - 1);
	}

	private SynchronizedStatement mGetNodeLevelStmt = null;
	private SynchronizedStatement mGetNextAtSameLevelStmt = null;
	private SynchronizedStatement mShowStmt = null;
	private SynchronizedStatement mExpandStmt = null;

	/**
	 * Build statements used by expand/collapse code.
	 */
	private void buildExpandNodeStatements() {
		if (mGetNodeLevelStmt == null) {
			String sql = "Select " + DOM_LEVEL + "||'/'||" + DOM_EXPANDED + " From " + mNavTable.ref() + " Where " + mNavTable.dot(DOM_ID) + " = ?";
			mGetNodeLevelStmt = mStatements.add("mGetNodeLevelStmt", sql);	
		}
		if (mGetNextAtSameLevelStmt == null) {
			String sql = "Select Coalesce( max(" + DOM_ID + "), -1) From (" +
					" Select " + DOM_ID + " From " + mNavTable.ref() + " Where " + 
					mNavTable.dot(DOM_ID) + " > ?" +
					" and " + mNavTable.dot(DOM_LEVEL) + " = ?" +
					" Order by " + DOM_ID + " Limit 1) zzz";
			mGetNextAtSameLevelStmt = mStatements.add("mGetNextAtSameLevelStmt", sql);				
		}
		if (mShowStmt == null) {
			String sql = "Update " + mNavTable +
					" Set " + DOM_VISIBLE + " = ?," + DOM_EXPANDED + " = ?" + 
					" where " + DOM_ID + " > ? and " + DOM_LEVEL + " > ? and " + DOM_ID + " < ?";
			mShowStmt = mStatements.add("mShowStmt", sql);
		}
		if (mExpandStmt == null) {
			String sql = "Update " + mNavTable +
					" Set " + DOM_EXPANDED + " = ?" + 
					" where " + DOM_ID + " = ?";
			mExpandStmt = mStatements.add("mExpandStmt", sql);
		}			
	}

	/**
	 * For EXPAND: Mark all rows as visible/expanded
	 * For COLLAPSE: Mark all non-root rows as invisible/unexpanded and mark all root nodes as visible/unexpanded.
	 * 
	 * @param expand
	 */
	public void expandAll(boolean expand) {
		long t0 = System.currentTimeMillis();
		if (expand) {
			String sql = "Update " + mNavTable + " Set expanded = 1, visible = 1";
			mDb.execSQL(sql);
			saveListNodeSettings();
		} else {				
			String sql = "Update " + mNavTable + " Set expanded = 0, visible = 0 Where level > 1";
			mDb.execSQL(sql);
			sql = "Update " + mNavTable + " Set expanded = 0 Where level = 1";
			mDb.execSQL(sql);
			deleteListNodeSettings();
		}
		long t1 = System.currentTimeMillis() - t0;
		System.out.println("Expand All: " + t1);
	}

	/**
	 * Toggle the expand/collapse status of the node as the specified absolute position
	 * 
	 * @param absPos
	 */
	public void toggleExpandNode(long absPos) {
		// This seems to get called sometimes after the database is closed...
		// RELEASE: remove statements as members, and look them up in mStatements via static keys

		buildExpandNodeStatements();

		// row position starts at 0, id's start at 1...
		final long rowId = absPos + 1;

		// Get the details of the passed row position.
		mGetNodeLevelStmt.bindLong(1, rowId);
		String info[] = mGetNodeLevelStmt.simpleQueryForString().split("/");
		long level = Long.parseLong(info[0]);
		int exp = ( Integer.parseInt(info[1]) == 1 ) ? 0 : 1;

		// Find the next row at the same level
		mGetNextAtSameLevelStmt.bindLong(1, rowId);
		mGetNextAtSameLevelStmt.bindLong(2, level);
		long next = mGetNextAtSameLevelStmt.simpleQueryForLong();
		if (next < 0) 
			next = Long.MAX_VALUE;

		// Mark intervening nodes as visible/invisible
		mShowStmt.bindLong(1, exp);
		mShowStmt.bindLong(2, exp);
		mShowStmt.bindLong(3, rowId);
		mShowStmt.bindLong(4, level);
		mShowStmt.bindLong(5, next);

		mShowStmt.execute();

		// Mark this node as expanded.
		mExpandStmt.bindLong(1, exp);
		mExpandStmt.bindLong(2, rowId);
		mExpandStmt.execute();

		// Update settings
		saveListNodeSetting(rowId, exp == 1);
	}
	
	/**
	 * Instance-based cursor factory so that the builder can be associated with the cursor and the rowview. We could
	 * probably send less context, but in the first instance this guarantees we get all the info we need downstream.
	 */
	private final CursorFactory mBooklistCursorFactory = new CursorFactory() {
			@Override
			public Cursor newCursor(
					SQLiteDatabase db,
					SQLiteCursorDriver masterQuery, 
					String editTable,
					SQLiteQuery query) 
			{
				return new BooklistCursor(db, masterQuery, editTable, query, BooklistBuilder.this, CatalogueDBAdapter.getSynchronizer());
			}
	};

	/**
	 * Get the style used by this builder.
	 * 
	 * @return
	 */
	public BooklistStyle getStyle() {
		return mStyle;
	}

	private boolean mReferenceDecremented = false;
	/**
	 * General cleanup routine called by both 'close()' and 'finalize()'
	 *
	 * @param isFinalize
	 */
	private void cleanup(final boolean isFinalize) {
		if (mStatements.size() != 0) {
			if (isFinalize) {
				System.out.println("Finalizing BooklistBuilder with active statements");				
			}
			try { mStatements.close(); } catch(Exception e) { Logger.logError(e); };
		}
		if (mNavTable != null) {
			if (isFinalize) {
				System.out.println("Finalizing BooklistBuilder with nav table");				
			}
			try { 
				mNavTable.close();
				mNavTable.drop(mDb);
			} 
			catch(Exception e) {
				Logger.logError(e); 
			};
		}
		if (mListTable != null) {
			if (isFinalize) {
				System.out.println("Finalizing BooklistBuilder with list table");				
			}
			try { 
				mListTable.close();
				mListTable.drop(mDb);
			} 
			catch(Exception e) {
				Logger.logError(e); 
			};
		}

		if (!mReferenceDecremented) {
			// Only de-reference once!
			synchronized(mInstanceCount) {
				mInstanceCount--;
				System.out.println("Builder instances: " + mInstanceCount);
			}
			mReferenceDecremented = true;
		}
	}
	
	/**
	 * Close the builder.
	 */
	public void close() {
		cleanup(false);
	}

	public void finalize() {
		cleanup(true);
	}
}


/*
 * Below was an interesting experiment, kept because it may one day get resurrected.
 * 
 * The basic idea was to create the list in sorted order to start with by using a BEFORE INSERT trigger on the
 * book_list table. The problems were two-fold:
 * 
 * - Android 1.6 has a buggy SQLite that does not like insert triggers that insert on the same table (this is fixed by
 *   android 2.0). So the old code would have to be kept anyway, to deal with old Android.
 *   
 * - it did not speed things up, probably because of index maintenance. It took about 5% longer.
 * 
 * Tests were performed on Android 2.2 using approx. 800 books.
 * 
 * Circumstances under which this may be resurrected include:
 * 
 * - Huge libraries (so that index build times are compensated for by huge tables). 800 books is close to break-even
 * - Stored procedures in SQLite become available to avoid multiple nested trigger calls.
 * 
 */
//
//	/**
//	 * Clear and the build the temporary list of books based on the passed criteria.
//	 * 
//	 * @param primarySeriesOnly		Only fetch books primary series
//	 * @param showSeries			If false, will not cross with series at al
//	 * @param bookshelf				Search criteria: limit to shelf
//	 * @param authorWhere			Search criteria: additional conditions that apply to authors table
//	 * @param bookWhere				Search criteria: additional conditions that apply to book table
//	 * @param loaned_to				Search criteria: only books loaned to named person
//	 * @param seriesName			Search criteria: only books in named series
//	 * @param searchText			Search criteria: book details must in some way contain the passed text
//	 * 
//	 */
//	public BooklistCursor build(long markId, String bookshelf, String authorWhere, String bookWhere, String loaned_to, String seriesName, String searchText) {
//		long t0 = System.currentTimeMillis();
//		SummaryBuilder summary = new SummaryBuilder();
//		// Add the minimum required domains
//
//		summary.addDomain(DOM_ID);
//		summary.addDomain(DOM_LEVEL, Integer.toString(mLevels.size()+1), SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_KIND, "" + ROW_KIND_BOOK, SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_BOOK, TBL_BOOKS.dot(DOM_ID), SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_BOOK_COUNT, "1", SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_ROOT_KEY);
//		//summary.addDomain(DOM_PARENT_KEY);		
//		
//		BooklistSeriesLevel seriesLevel = null;
//		BooklistAuthorLevel authorLevel = null;
//		boolean hasLevelLOANED = false;
//		
//		long t0a = System.currentTimeMillis();
//	
//		for (BooklistLevel l : mLevels) {
//			//
//			//	Build each row-kind group. 
//			//
//			//  ****************************************************************************************
//			//  IMPORTANT NOTE: for each row kind, then FIRST SORTED AND GROUPED domain should be the one 
//			//					that will be displayed and that level in the UI.
//			//  ****************************************************************************************
//			//
//			switch (l.kind) {
//			
//			case ROW_KIND_SERIES:
//				l.displayDomain = DOM_SERIES_NAME;
//				seriesLevel = (BooklistSeriesLevel) l; // getLevel(ROW_KIND_SERIES);
//				summary.addDomain(DOM_SERIES_NAME, TBL_SERIES.dot(DOM_SERIES_NAME), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				summary.addDomain(DOM_SERIES_ID, TBL_BOOK_SERIES.dot(DOM_SERIES_ID), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//				summary.addDomain(DOM_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_SERIES_NUM), SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_SERIES_POSITION, TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION), SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_PRIMARY_SERIES_COUNT,"case when Coalesce(" + TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION) + ",1) == 1 then 1 else 0 end", SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_SERIES_NUM), SummaryBuilder.FLAG_SORTED);
//				//summary.addKeyComponents("'s'", TBL_BOOK_SERIES.dot(DOM_SERIES_ID));
//				l.setKeyComponents("s", DOM_SERIES_ID);
//				break;
//
//			case ROW_KIND_AUTHOR:
//				l.displayDomain = DOM_AUTHOR_FORMATTED;
//				authorLevel = (BooklistAuthorLevel) l; //getLevel(ROW_KIND_AUTHOR);
//				summary.addDomain(DOM_AUTHOR_SORT, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				if (authorLevel.givenName)
//					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
//				else
//					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
//
//				summary.addDomain(DOM_AUTHOR_ID, TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//
//				//summary.addKeyComponents("'a'", TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID));
//				l.setKeyComponents("a", DOM_AUTHOR_ID);
//
//				break;
//
//			case ROW_KIND_GENRE:
//				l.displayDomain = DOM_GENRE;
//				summary.addDomain(DOM_GENRE, TBL_BOOKS.dot(DOM_GENRE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'g'", TBL_BOOKS.dot(DOM_GENRE));
//				l.setKeyComponents("g", DOM_GENRE);
//				break;
//
//			case ROW_KIND_PUBLISHER:
//				l.displayDomain = DOM_PUBLISHER;
//				summary.addDomain(DOM_PUBLISHER, TBL_BOOKS.dot(DOM_PUBLISHER), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'p'", TBL_BOOKS.dot(DOM_PUBLISHER));
//				l.setKeyComponents("p", DOM_PUBLISHER);
//				break;
//
//			case ROW_KIND_UNREAD:
//				l.displayDomain = DOM_READ_STATUS;
//				String unreadExpr = "Case When " + TBL_BOOKS.dot(DOM_READ) + " = 1 " +
//						" Then '" + BookCatalogueApp.getResourceString(R.string.booklist_read) + "'" +
//						" Else '" + BookCatalogueApp.getResourceString(R.string.booklist_unread) + "' end";
//				summary.addDomain(DOM_READ_STATUS, unreadExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				summary.addDomain(DOM_READ, TBL_BOOKS.dot(DOM_READ), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'r'", TBL_BOOKS.dot(DOM_READ));
//				l.setKeyComponents("r", DOM_READ);
//				break;
//
//			case ROW_KIND_LOANED:
//				hasLevelLOANED = true;
//				l.displayDomain = DOM_LOANED_TO;
//				summary.addDomain(DOM_LOANED_TO, LOANED_TO_EXPRESSION, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'l'", DatabaseDefinitions.TBL_LOAN.dot(DOM_LOANED_TO));
//				l.setKeyComponents("l", DOM_LOANED_TO);
//				break;
//
//			case ROW_KIND_TITLE_LETTER:
//				l.displayDomain = DOM_TITLE_LETTER;
//				String titleLetterExpr = "substr(" + TBL_BOOKS.dot(DOM_TITLE) + ",1,1)";
//				summary.addDomain(DOM_TITLE_LETTER, titleLetterExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'t'", titleLetterExpr);
//				l.setKeyComponents("t", DOM_TITLE_LETTER);
//				break;
//
//			default:
//				throw new RuntimeException("Unsupported group type " + l.kind);
//
//			}
//			// Copy the current groups to this level item; this effectively accumulates 'group by' domains 
//			// down each level so that the top has fewest groups and the bottom level has groups for all levels.
//			l.groupDomains = (ArrayList<DomainDefinition>) summary.cloneGroups();
//		}
//		long t0b = System.currentTimeMillis();
//
//		if (markId != 0) {
//			summary.addDomain(DOM_MARK, TBL_BOOKS.dot(DOM_ID) + " = " + markId, SummaryBuilder.FLAG_NONE);
//		}
//
//		// Ensure any caller-specified extras (eg. title) are added at the end.
//		for(Entry<String,ExtraDomainInfo> d : mExtraDomains.entrySet()) {
//			ExtraDomainInfo info = d.getValue();
//			int flags;
//			if (info.isSorted)
//				flags = SummaryBuilder.FLAG_SORTED;
//			else
//				flags = SummaryBuilder.FLAG_NONE;
//			summary.addDomain(info.domain, info.sourceExpression, flags);
//		}
//		long t0c = System.currentTimeMillis();
//
//		//
//		// Build the initial insert statement: 'insert into <tbl> (col-list) select (expr-list) from'.
//		// We just need to add the 'from' tables. It is a fairly static list, for the most part we just
//		// add extra criteria as needed.
//		//
//		// The seriesLevel and authorLevel fields will influend the nature of the join. If at a later
//		// stage some row kinds introduce more table dependencies, a flag (or object) can be set
//		// when processing the level to inform the joining code (below) which tables need to be added.
//		// 
//		// Aside: The sql used prior to using DbUtils is included as comments below the doce that replaced it.
//		//
//		String sql = summary.buildBaseInsert(mLevels.get(0).getCompoundKey());
//
//		long t0d = System.currentTimeMillis();
//
//		JoinContext join;
//
//		if (!bookshelf.equals("")) {
//			join = new JoinContext(TBL_BOOKSHELF)
//				.start()
//				.join(TBL_BOOK_BOOKSHELF)
//				.join(TBL_BOOKS);
//		} else {
//			join = new JoinContext(TBL_BOOKS).start();
//		}
//			/*
//			if (!bookshelf.equals("")) {
//				sql += "	" + DB_TB_BOOKSHELF_AND_ALIAS + " join " + DB_TB_BOOK_BOOKSHELF_AND_ALIAS + 
//						" On " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOKSHELF + " = " + ALIAS_BOOKSHELF + "." + KEY_ROWID ;
//				sql +=	"    join " + DB_TB_BOOKS_AND_ALIAS + " on " + ALIAS_BOOKS + "." + KEY_ROWID + " = " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOK + "\n";
//			} else {
//				sql +=	"    " + DB_TB_BOOKS_AND_ALIAS + "\n";
//			}
//			*/
//
//		// If a LOANED level is present, we are ONLY interested in loaned books. So cross it here.
//		if (hasLevelLOANED) {
//			join.join(TBL_LOAN);
//		}
//		
//		// Specify a parent in the join, because the last table joined was one of BOOKS or LOAN.
//		join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);
//		if (authorLevel == null || !authorLevel.allAuthors) {
//			join.append( "		and " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) + " == 1\n");
//		}
//		join.join(TBL_AUTHORS);
//		join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);
//			/*
//			sql +=	"    join " + DB_TB_BOOK_AUTHOR_AND_ALIAS + " on " + ALIAS_BOOK_AUTHOR + "." + KEY_BOOK + " = " + ALIAS_BOOKS + "." + KEY_ROWID + "\n" +
//					"    join " + DB_TB_AUTHORS_AND_ALIAS + " on " + ALIAS_AUTHORS + "." + KEY_ROWID + " = " + ALIAS_BOOK_AUTHOR + "." + KEY_AUTHOR_ID + "\n";
//			sql +=	"    left outer join " + DB_TB_BOOK_SERIES_AND_ALIAS + " on " + ALIAS_BOOK_SERIES + "." + KEY_BOOK + " = " + ALIAS_BOOKS + "." + KEY_ROWID + "\n";
//			*/
//
//		if (seriesLevel == null || !seriesLevel.allSeries) {
//			join.append( "		and " + TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION) + " == 1\n");
//		}
//		join.leftOuterJoin(TBL_SERIES);
//			/*
//			if (seriesLevel == null || !seriesLevel.allSeries) {
//				sql += "		and " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_POSITION + " == 1\n";
//			}
//			sql +=	"    left outer join " + DB_TB_SERIES_AND_ALIAS + " on " + ALIAS_SERIES + "." + KEY_ROWID + " = " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_ID;
//			*/
//
//		// Append the joined tables to our initial insert statement
//		sql += join.toString();
//
//		//
//		// Now build the 'where' clause.
//		//
//		long t0e = System.currentTimeMillis();
//		String where = "";
//
//		if (!bookshelf.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF_NAME) + " = '" + CatalogueDBAdapter.encodeString(bookshelf) + "')";
//		}
//		if (!authorWhere.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + authorWhere + ")";
//		}
//		if (!bookWhere.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + bookWhere + ")";
//		}
//		if (!loaned_to.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "Exists(Select NULL From " + TBL_LOAN.ref() + " Where " + TBL_LOAN.dot(DOM_LOANED_TO) + " = '" + encodeString(loaned_to) + "'" +
//					" and " + TBL_LOAN.fkMatch(TBL_BOOKS) + ")";
//					// .and()    .op(TBL_LOAN.dot(DOM_BOOK), "=", TBL_BOOKS.dot(DOM_ID)) + ")";
//		}
//		if (!seriesName.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_SERIES.dot(DOM_SERIES_NAME) + " = '" + encodeString(seriesName) + "')";
//		}
//		if(!searchText.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_BOOKS.dot(DOM_ID) + " in (select docid from " + DB_TB_BOOKS_FTS + " where " + DB_TB_BOOKS_FTS + " match '" + encodeString(searchText) + "'))";
//		}
//
//		// If we got any conditions, add them to the initial insert statement
//		if (!where.equals(""))
//			sql += " where " + where.toString();
//
//		long t1 = System.currentTimeMillis();
//
//		{
//			final ArrayList<DomainDefinition> sort = summary.getExtraSort();
//			final StringBuilder sortCols = new StringBuilder();
//			for (DomainDefinition d: sort) {
//				sortCols.append(d.name);
//				sortCols.append(CatalogueDBAdapter.COLLATION + ", ");
//			}
//			sortCols.append(DOM_LEVEL.name);
//			mSortColumnList = sortCols.toString();
//		}
//
//		{
//			final ArrayList<DomainDefinition> group = summary.cloneGroups();
//			final StringBuilder groupCols = new StringBuilder();;
//			for (DomainDefinition d: group) {
//				groupCols.append(d.name);
//				groupCols.append(CatalogueDBAdapter.COLLATION + ", ");
//			}
//			groupCols.append( DOM_LEVEL.name );
//			mGroupColumnList = groupCols.toString();
//		}
//
//		{
//			final ArrayList<DomainDefinition> keys = summary.getKeys();
//			final StringBuilder keyCols = new StringBuilder();;
//			for (DomainDefinition d: keys) {
//				keyCols.append(d.name);
//				keyCols.append(CatalogueDBAdapter.COLLATION + ", ");
//			}
//			keyCols.append( DOM_LEVEL.name );
//			mKeyColumnList = keyCols.toString();
//		}
//
//		mLevelBuildStmts = new ArrayList<SQLiteStatement>();
//
//		String listNameSave = mListTable.getName();
//		try {
//			mListTable.setName(TBL_BOOK_LIST_DEFN.getName());
//			mListTable.drop(mDb);
//			mListTable.create(mDb, false);
//		} finally {
//			mListTable.setName(listNameSave);			
//		}
//		//mDb.execSQL("Create View " + TBL_BOOK_LIST_DEFN + " as select * from " + mListTable);
//
//		// Make sure triggers can check easily
//		//mDb.execSQL("Create Index " + mListTable + "_IX_TG on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList + ")");
//		mDb.execSQL("Create Unique Index " + mListTable + "_IX_TG1 on " + mListTable + "(" + DOM_LEVEL + ", " + mKeyColumnList + ", " + DOM_BOOK + ")");
//
//		/*
//		 * Create a trigger to forward all row detais to real table
//		 */
//		/*
//		String fullInsert = "Insert into " + mListTable + "(";
//		{
//			String fullValues = "Values (";
//			boolean firstCol = true;
//			for (DomainDefinition d: mListTable.domains) {
//				if (!d.equals(DOM_ID)) {
//					if (firstCol)
//						firstCol = false;
//					else {
//						fullInsert+=", ";
//						fullValues += ", ";
//					}
//					fullInsert += d; 
//					fullValues += "new." + d; 
//				}
//			}
//			fullInsert += ") " + fullValues + ");";			
//
//			String tgForwardName = "header_Z_F";
//			mDb.execSQL("Drop Trigger if exists " + tgForwardName);
//			String tgForwardSql = "Create Trigger " + tgForwardName + " instead of  insert on " + TBL_BOOK_LIST_DEFN + " for each row \n" +
//					"	Begin\n" +
//					"		" + fullInsert + "\n" +
//					"	End";
//			SQLiteStatement stmt = mStatements.add("TG " + tgForwardName, tgForwardSql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();			
//		}
//		*/
//
//		// Now make some BEFORE INSERT triggers to build hierarchy; no trigger on root level (index = 0).
//		//String[] tgLines = new String[mLevels.size()];
//		
//		for (int i = mLevels.size()-1; i >= 0; i--) {
//			final BooklistLevel l = mLevels.get(i);
//			final int levelId = i + 1;
//			String insertSql = "Insert into " + mListTable + "( " + DOM_LEVEL + "," + DOM_KIND + ", " + DOM_ROOT_KEY + "\n";
//			// If inserting with forwarding table: String insertSql = "Insert into " + TBL_BOOK_LIST_DEFN + "( " + DOM_LEVEL + "," + DOM_KIND + ", " + DOM_ROOT_KEY + "\n";
//			// If inserting in one trigger using multiple 'exists': String valuesSql = levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";
//			String valuesSql = "Values (" + levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";
//			String conditionSql = "l." + DOM_LEVEL + " = " + levelId + "\n";
//			for(DomainDefinition d  : l.groupDomains) {
//				insertSql += ", " + d;
//				valuesSql += ", new." + d;
//				if (summary.getKeys().contains(d))
//					conditionSql += "	and l." + d + " = new." + d + CatalogueDBAdapter.COLLATION + "\n";
//			}
//			//insertSql += ")\n	Select " + valuesSql + " Where not exists(Select 1 From " + mListTable + " l where " + conditionSql + ")";
//			//tgLines[i] = insertSql;
//
//			insertSql += ")\n" + valuesSql + ")";
//			String tgName = "header_A_tgL" + i;
//			mDb.execSQL("Drop Trigger if exists " + tgName);
//			// If using forwarding table: String tgSql = "Create Trigger " + tgName + " instead of  insert on " + TBL_BOOK_LIST_DEFN + " for each row when new.level = " + (levelId+1) +
//			String tgSql = "Create Temp Trigger " + tgName + " before insert on " + mListTable + " for each row when new.level = " + (levelId+1) +
//					" and not exists(Select 1 From " + mListTable + " l where " + conditionSql + ")\n" +
//					"	Begin\n" +
//					"		" + insertSql + ";\n" +
//					"	End";
//			SQLiteStatement stmt = mStatements.add("TG " + tgName, tgSql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//		}
//		/*
//		String tgName = "header_tg";
//		mDb.execSQL("Drop Trigger if exists " + tgName);
//		String tgSql = "Create Trigger " + tgName + " instead of  insert on " + TBL_BOOK_LIST_DEFN + " for each row when new.level = " + (mLevels.size()+1) +
//				"	Begin\n";
//		for(String s: tgLines) {
//			tgSql += "		" + s + ";\n";			
//		}
//
//		tgSql += "\n	" + fullIns + ") " + vals + ");\n";
//		tgSql += "	End";
//		
//		SQLiteStatement tgStmt = mStatements.add("TG " + tgName, tgSql);
//		mLevelBuildStmts.add(tgStmt);
//		tgStmt.execute();
//		*/
//
//		// We are good to go.
//		long t1a = System.currentTimeMillis();
//		mDb.beginTransaction();
//		long t1b = System.currentTimeMillis();
//		try {
//			// Build the lowest level summary using our initial insert statement
//			//mBaseBuildStmt = mStatements.add("mBaseBuildStmt", sql + ") zzz Order by " + mGroupColumnList);
//			mBaseBuildStmt = mStatements.add("mBaseBuildStmt", sql );
//			mBaseBuildStmt.execute();
//
//			/*
//			 Create table foo(level int, g1 text, g2 text, g3 text);
//			 Create Temp Trigger foo_tgL2 before insert on foo for each row when new.level = 3 and not exists(Select * From foo where level = 2 and g1=new.g1 and g2= foo.g2)
//			 Begin
//			    Insert into foo(level, g1, g2) values (2, new.g1, new.g2);
//			   End;
//			 Create Temp Trigger foo_tgL1 before insert on foo for each row when new.level = 2 and not exists(Select * From foo where level = 1 and g1=new.g1)
//			 Begin
//			    Insert into foo(level, g1) values (1, new.g1);
//			   End;
//
//			 Create Temp Trigger L3 After Insert On mListTable For Each Row When LEVEL = 3 Begin
//			 Insert into mListTable (level, kind, root_key, <cols>) values (new.level-1, new.<cols>)
//			 Where not exists
//			 */
//			long t1c = System.currentTimeMillis();
//			//mDb.execSQL(ix3cSql);
//			long t1d = System.currentTimeMillis();
//			//mDb.execSQL("analyze " + mListTable);
//			
//			long t2 = System.currentTimeMillis();
//
//			// Now build each summary level query based on the prior level.
//			// We build and run from the bottom up.
//
//			/*
//			long t2a[] = new long[mLevels.size()];
//			int pos=0;
//			for (int i = mLevels.size()-1; i >= 0; i--) {
//				final BooklistLevel l = mLevels.get(i);
//				final int levelId = i + 1;
//				String cols = "";
//				String collatedCols = "";
//				for(DomainDefinition d  : l.groupDomains) {
//					if (!collatedCols.equals(""))
//						collatedCols += ",";
//					cols += ",\n	" + d.name;
//					//collatedCols += ",\n	" + d.name + CatalogueDBAdapter.COLLATION;
//					collatedCols += "\n	" + d.name + CatalogueDBAdapter.COLLATION;
//				}
//				sql = "Insert Into " + mListTable + "(\n	" + DOM_LEVEL + ",\n	" + DOM_KIND + 
//						//",\n	" + DOM_PARENT_KEY +
//						cols + "," + DOM_ROOT_KEY +
//						")" +
//						"\n select " + levelId + " as " + DOM_LEVEL + ",\n	" + l.kind + " as " + DOM_KIND +
//						//l.getKeyExpression() +
//						cols + "," + DOM_ROOT_KEY +
//						"\n from " + mListTable + "\n " + " where level = " + (levelId+1) +
//						"\n Group by " + collatedCols + "," + DOM_ROOT_KEY + CatalogueDBAdapter.COLLATION;
//						//"\n Group by " + DOM_LEVEL + ", " + DOM_KIND + collatedCols;
//
//				SQLiteStatement stmt = mStatements.add("L" + i, sql);
//				mLevelBuildStmts.add(stmt);
//				stmt.execute();
//				t2a[pos++] = System.currentTimeMillis();
//			}
//			*/
//
//			String ix1Sql = "Create Index " + mListTable + "_IX1 on " + mListTable + "(" + mSortColumnList + ")";
//
//			SQLiteStatement stmt;
//			long t3 = System.currentTimeMillis();
//			stmt = mStatements.add("ix1", ix1Sql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//			long t3a = System.currentTimeMillis();
//			mDb.execSQL("analyze " + mListTable);
//			long t3b = System.currentTimeMillis();
//			
//			// Now build a lookup table to match row sort position to row ID. This is used to match a specific
//			// book (or other row in result set) to a position directly.
//			mNavTable.drop(mDb);
//			mNavTable.create(mDb, true);
//			sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) + 
//					" Select " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
//					" ,\n	Case When " + DOM_LEVEL + " = 1 Then 1 \n" +
//					"	When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0\n	Else 1 end,\n "+ 
//					"	Case When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0 Else 1 end\n"+
//					" From " + mListTable.ref() + "\n	left outer join " + TBL_BOOK_LIST_NODE_SETTINGS.ref() + 
//					"\n		On " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " = " + mListTable.dot(DOM_ROOT_KEY) +
//					"\n			And " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_KIND) + " = " + mLevels.get(0).kind +
//					"\n	Order by " + mSortColumnList;
//
//			stmt = mStatements.add("InsNav", sql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//
//			long t4 = System.currentTimeMillis();
//			mDb.execSQL("Create Index " + mNavTable + "_IX1" + " On " + mNavTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")");
//			long t4a = System.currentTimeMillis();
//			// Essential for main query! If not present, will make getCount() take ages because main query is a cross with no index.
//			mDb.execSQL("Create Unique Index " + mNavTable + "_IX2" + " On " + mNavTable + "(" + DOM_REAL_ROW_ID + ")");
//			long t4b = System.currentTimeMillis();
//			mDb.execSQL("analyze " + mNavTable);
//			long t4c = System.currentTimeMillis();
//			
//			/*
//			// Create Index book_list_tmp_row_pos_1_ix2 on book_list_tmp_row_pos_1(level, expanded, root_key);
//			long t5 = System.currentTimeMillis();
//			sql = "Update " + navName + " set expanded = 1 where level = 1 and " +
//					"exists(Select _id From " + TBL_BOOK_LIST_NODE_SETTINGS + " x2 Where x2.kind = " + mLevels.get(0).kind + " and x2.root_key = " + navName + ".root_key)";
//			mDb.execSQL(sql);
//			long t6 = System.currentTimeMillis();
//			sql = "Update " + navName + " set visible = 1, expanded = 1 where level > 1 and " +
//					"exists(Select _id From " + navName + " x2 Where x2.level = 1 and x2.root_key = " + navName + ".root_key and x2.expanded=1)";
//			mDb.execSQL(sql);
//			long t7 = System.currentTimeMillis();
//			sql = "Update " + navName + " set visible = 1 where level = 1";
//			mDb.execSQL(sql);
//			*/
//
//			long t8 = System.currentTimeMillis();
//			//stmt = makeStatement(ix1Sql);
//			//mLevelBuildStmts.add(stmt);
//			//stmt.execute();
//			long t9 = System.currentTimeMillis();
//			//mDb.execSQL(ix2Sql);
//			long t10 = System.currentTimeMillis();
//			//mDb.execSQL("analyze " + mTableName);
//			long t11 = System.currentTimeMillis();
//			
//			System.out.println("T0a: " + (t0a-t0));
//			System.out.println("T0b: " + (t0b-t0a));
//			System.out.println("T0c: " + (t0c-t0b));
//			System.out.println("T0d: " + (t0d-t0c));
//			System.out.println("T0e: " + (t0e-t0d));
//			System.out.println("T1: " + (t1-t0));
//			System.out.println("T1a: " + (t1a-t1));
//			System.out.println("T1b: " + (t1b-t1a));
//			System.out.println("T1c: " + (t1c-t1b));
//			System.out.println("T1d: " + (t1d-t1c));
//			System.out.println("T2: " + (t2-t1d));
//			//System.out.println("T2a[0]: " + (t2a[0]-t2));
//			//for(int i = 1; i < mLevels.size(); i++) {
//			//	System.out.println("T2a[" + i + "]: " + (t2a[i]-t2a[i-1]));				
//			//}
//			//System.out.println("T3: " + (t3-t2a[mLevels.size()-1]));
//			System.out.println("T3a: " + (t3a-t3));
//			System.out.println("T3b: " + (t3b-t3a));
//			System.out.println("T4: " + (t4-t3b));
//			System.out.println("T4a: " + (t4a-t4));
//			System.out.println("T4b: " + (t4b-t4a));
//			System.out.println("T4c: " + (t4c-t4b));
//			//System.out.println("T5: " + (t5-t4));
//			//System.out.println("T6: " + (t6-t5));
//			//System.out.println("T7: " + (t7-t6));
//			System.out.println("T8: " + (t8-t4c));
//			System.out.println("T9: " + (t9-t8));
//			System.out.println("T10: " + (t10-t9));
//			System.out.println("T10: " + (t11-t10));
//
//			mDb.setTransactionSuccessful();
//
//			mSummary = summary;
//
//			
//			// Get the final result			
//			return getList();
//			//sql = "select * from " + mTableName + " Order by " + mSortColumnList;
//
//			//return (BooklistCursor) mDb.rawQueryWithFactory(mBooklistCursorFactory, sql, EMPTY_STRING_ARRAY, "");					
//
//		} finally {
//			mDb.endTransaction();
//			//mDb.execSQL("PRAGMA synchronous = FULL");
//
//		}
//	}

