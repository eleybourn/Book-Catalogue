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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map.Entry;

import static com.eleybourn.bookcatalogue.CatalogueDBAdapter.*;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteStatement;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.BooklistAuthorGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.BooklistSeriesGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.CompoundKey;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.database.DbUtils.JoinContext;
import com.eleybourn.bookcatalogue.database.DbUtils.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition;
import com.eleybourn.bookcatalogue.database.DbUtils.Synchronizer.SyncLock;

/**
 * TODO: Document!
 * 
 * @author Grunthos
 */
public class BooklistBuilder {
	private static Integer mBooklistBuilderIdCounter = 0;

	//********************************************************************
	// CLASSES
	//********************************************************************

	private class ExtraDomainInfo {
		DomainDefinition	domain;
		String sourceExpression;
		boolean isSorted;	
	};

	//********************************************************************
	// INSTANCE DATA
	//********************************************************************

	private final SqlStatementManager mStatements;
	private final SynchronizedDb mDb;
	private final int mBooklistBuilderId;
	private String mSortColumnList;
	private String mGroupColumnList;
	private String mKeyColumnList;
	private Hashtable<String, ExtraDomainInfo> mExtraDomains = new Hashtable<String, ExtraDomainInfo>();
	private BooklistStyle mStyle = new BooklistStyle(0);
	private Hashtable<Integer,BooklistGroup> mMap = new Hashtable<Integer,BooklistGroup>();
	private TableDefinition mListTable;
	private TableDefinition mNavTable;

	BooklistBuilder(CatalogueDBAdapter adapter) {
		synchronized(mBooklistBuilderIdCounter) {
			mBooklistBuilderId = ++mBooklistBuilderIdCounter;
		}
		mDb = adapter.getDb();		
		mStatements = new SqlStatementManager(mDb);

	}

	public BooklistBuilder(CatalogueDBAdapter adapter, BooklistStyle style) {
		synchronized(mBooklistBuilderIdCounter) {
			mBooklistBuilderId = 1; // TODO XXX FIX: ++mBooklistBuilderIdCounter;
		}
		mDb = adapter.getDb();
		mStatements = new SqlStatementManager(mDb);
		mStyle = style;

		mListTable = TBL_BOOK_LIST_DEFN.clone();
		mListTable.setName(mListTable.getName() + "_" + getId());
		mListTable.setIsTemporary(true); //TODO Make sure is TEMPORARY

		mNavTable = TBL_ROW_NAVIGATOR_DEFN.clone()
				.addReference(mListTable, DOM_REAL_ROW_ID)
				;
		mNavTable.setName(mNavTable.getName() + "_" + getId());
		mNavTable.setIsTemporary(true); //TODO Make sure is TEMPORARY
	}

	public int getId() {
		return mBooklistBuilderId;
	}
	
	public BooklistBuilder addAuthorLevel(int flags) {
		if ((flags & ~BooklistGroup.FLAG_AUTHORS_MASK) != 0)
			throw new RuntimeException("Illegal flag value found");

		BooklistAuthorGroup level = new BooklistAuthorGroup();
		level.allAuthors = (flags & BooklistGroup.FLAG_AUTHORS_SHOW_ALL) != 0;
		level.givenName = (flags & BooklistGroup.FLAG_AUTHORS_USE_GIVEN_NAME) != 0;
		this.addLevel(level);
		return this;
	}

	public BooklistBuilder addSeriesLevel(int flags) {
		if ((flags & ~BooklistGroup.FLAG_SERIES_MASK) != 0)
			throw new RuntimeException("Illegal flag value found");

		BooklistSeriesGroup level = new BooklistSeriesGroup();
		level.allSeries = (flags & BooklistGroup.FLAG_SERIES_SHOW_ALL) != 0;
		this.addLevel(level);
		return this;
	}

	public BooklistBuilder addLevel(int kind) {
		BooklistGroup l;
		// Cleanup if someone was lazy; makes work easier later
		if (kind == ROW_KIND_SERIES) {
			l = new BooklistSeriesGroup();
		} else if (kind == ROW_KIND_AUTHOR) {
			l = new BooklistAuthorGroup();				
		} else {
			l = new BooklistGroup();
			l.kind = kind;
		}
		addLevel(l);
		return this;
	}

	public BooklistBuilder addLevel(BooklistGroup l) {
		if (mMap.containsKey(l.kind))
			throw new RuntimeException("A group of this type has already been added");
		mStyle.add(l);
		mMap.put(l.kind, l);
		return this;
	}


	public boolean hasLevel(int rowKind) {
		return mMap.containsKey(rowKind);
	}
	public BooklistGroup getLevel(int rowKind) {
		return mMap.get(rowKind);
	}
	
	public BooklistBuilder requireDomain(DomainDefinition domain, String sourceExpression, boolean isSorted) {
		ExtraDomainInfo info = new ExtraDomainInfo();
		info.domain = domain;
		info.sourceExpression = sourceExpression;
		info.isSorted = isSorted;

		if (mExtraDomains.containsKey(domain.name)) {
			boolean ok = false;
			ExtraDomainInfo oldInfo = mExtraDomains.get(domain.name);
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
			mExtraDomains.put(domain.name, info);

		return this;
	}

	public class SummaryBuilder {

		public static final int FLAG_NONE = 0;
		public static final int FLAG_SORTED = 1;
		public static final int FLAG_GROUPED = 2;
		public static final int FLAG_KEY = 4;
		
		private ArrayList<DomainDefinition> mDomains = new ArrayList<DomainDefinition>();
		private ArrayList<String> mExpressions = new ArrayList<String>();
		private Hashtable<DomainDefinition, String> mExpressionMap = new Hashtable<DomainDefinition, String>();
		
		private ArrayList<DomainDefinition> mGroups = new ArrayList<DomainDefinition>();
		private ArrayList<DomainDefinition> mKeys = new ArrayList<DomainDefinition>();
		private ArrayList<DomainDefinition> mExtraSorted = new ArrayList<DomainDefinition>();
		
		public TableDefinition getTabldeDefinition() {
			return mListTable;
		}
		
		private void init(DomainDefinition domain, String expression, int flags) { //, boolean isGroup, boolean isExtraSorted) {
			mDomains.add(domain);
			mExpressions.add(expression);
			mExpressionMap.put(domain, expression);

			mListTable.addDomain(domain);
			if ((flags & FLAG_GROUPED) != 0)
				mGroups.add(domain);
			if ((flags & FLAG_KEY) != 0)
				mKeys.add(domain);
			if ((flags & FLAG_SORTED) != 0)
				mExtraSorted.add(domain);				
		}
		public void addDomain(DomainDefinition domain, String expression, int flags) {
			init(domain, expression, flags);				
		}
		//public void addDomain(DomainDefinition domain, String expression, boolean isGroup, boolean isExtraSorted) {
		//	init(domain, expression, flags);				
		//}

		public void addDomain(DomainDefinition domain) {
			mListTable.addDomain(domain);
		}
		public ArrayList<DomainDefinition> cloneGroups() {
			return (ArrayList<DomainDefinition>)mGroups.clone();				
		}
		public ArrayList<DomainDefinition> getKeys() {
			return (ArrayList<DomainDefinition>)mKeys;				
		}
		public ArrayList<DomainDefinition> getExtraSort() {
			return (ArrayList<DomainDefinition>)mExtraSorted;				
		}

		private ArrayList<String> mKeyComponents = new ArrayList<String>();
		public void addKeyComponents(String...strings) {
			for(String s: strings)
				mKeyComponents.add(s);
		}
		public ArrayList<String> cloneKeyComponents() {
			return (ArrayList<String>)mKeyComponents.clone();
		}
		public String getKeyExpression() {
			StringBuilder s = new StringBuilder();
			s.append(mKeyComponents.get(0));
			for (int i = 1; i < mKeyComponents.size() ; i++) {
				s.append("||'/'||");
				s.append(mKeyComponents.get(i));
			}
			return s.toString();
		}
		
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

		public String buildBaseInsert(CompoundKey rootKey) {
			recreateTable();
			StringBuilder columns = new StringBuilder();
			StringBuilder expressions = new StringBuilder();
			for(int i = 0 ; i < mDomains.size(); i++) {
				DomainDefinition d = mDomains.get(i);
				String e = mExpressions.get(i);
				if (i > 0) {
					columns.append(",\n	");
					expressions.append(",\n	");
				}
				columns.append(d.name);
				expressions.append(e);
				expressions.append(" as ");
				expressions.append(d.name);
			}
			
			String keyExpression = "'" + rootKey.prefix;
			for (DomainDefinition d: rootKey.domains) {
				keyExpression += "/'||Coalesce(" + mExpressionMap.get(d) + ",'')";
			}
			return "Insert into " + mListTable + " (\n	" + columns.toString() + ",\n	" + DOM_ROOT_KEY + 
				// If using forward tables: return "Insert into " + TBL_BOOK_LIST_DEFN + " (\n	" + columns.toString() + ",\n	" + DOM_ROOT_KEY + 
					//")\n Select * From (Select\n	" + expressions.toString() + ",\n	" + keyExpression + 
					")\n Select\n	" + expressions.toString() + ",\n	" + keyExpression + 
					"\n From\n";
		}
	}
	
	private static final String AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION = "Case "
			+ "When " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES) + " = '' Then " + TBL_AUTHORS.dot(DOM_FAMILY_NAME)
			+ " Else " + TBL_AUTHORS.dot(DOM_FAMILY_NAME) + "|| ', ' || " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES)
			+ " End";
	private static final String AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION = "Case "
			+ "When " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES) + " = '' Then " + TBL_AUTHORS.dot(DOM_FAMILY_NAME)
			+ " Else " + TBL_AUTHORS.dot(DOM_GIVEN_NAMES) + "|| ' ' || " + TBL_AUTHORS.dot(DOM_FAMILY_NAME)
			+ " End";

	private static final String LOANED_TO_EXPRESSION = "(Select " + KEY_LOANED_TO + " From " + TBL_LOAN.ref() + 
															" Where " + TBL_LOAN.dot(DOM_BOOK) + " = " + TBL_BOOKS.dot(DOM_ID) + ")";

	private SummaryBuilder mSummary = null;

	private SQLiteStatement mBaseBuildStmt = null;
	private ArrayList<SQLiteStatement> mLevelBuildStmts = null;

	public void rebuild() {
		mSummary.recreateTable();

		mNavTable.drop(mDb);
		mNavTable.create(mDb, true);
		
		mBaseBuildStmt.execute();
		for(SQLiteStatement s : mLevelBuildStmts)
			s.execute();
	}
/*
 * Below was an interesting experiment, kept because it may one day get resurrected.
 * 
 * The basic idea was to create the list in sorted order to start with by using a BEFORE INSERT trigger on the
 * book_list table. The problems were two-fold:
 * 
 * - Android 1.6 has a buggy SQLite that does not like insert triggers that insert on the same table (this is fixed by
 *   android 2.0). So the old code would have to be kept anyway.
 *   
 * - it did not speed things up, probably because of index maintenance. It took about 5% longer.
 * 
 * Tests were performed on Android 2.2 using approx. 800 books.
 * 
 * Circumstances under which this may be resurrected include:
 * 
 * - Huge libraries (so that index build times are compensated for by huge tables)
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
//		// TODO: Break published_date into year/month/day
//		//if (this.hasLevel(ROW_KIND_YEAR_PUBLISHED)) {
//		//	tblDef
//		//		.addDomain(DOM_PUBLICATION_YEAR);
//		//}
//		//if (this.hasLevel(ROW_KIND_MONTH_PUBLISHED)) {
//		//	tblDef
//		//		.addDomain(DOM_PUBLICATION_MONTH);
//		//}
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
//				sortCols.append(" Collate UNICODE, ");
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
//				groupCols.append(" Collate UNICODE, ");
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
//				keyCols.append(" Collate UNICODE, ");
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
//					conditionSql += "	and l." + d + " = new." + d + " Collate UNICODE\n";
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
//					//collatedCols += ",\n	" + d.name + " Collate UNICODE";
//					collatedCols += "\n	" + d.name + " Collate UNICODE";
//				}
//				sql = "Insert Into " + mListTable + "(\n	" + DOM_LEVEL + ",\n	" + DOM_KIND + 
//						//",\n	" + DOM_PARENT_KEY +
//						cols + "," + DOM_ROOT_KEY +
//						")" +
//						"\n select " + levelId + " as " + DOM_LEVEL + ",\n	" + l.kind + " as " + DOM_KIND +
//						//l.getKeyExpression() +
//						cols + "," + DOM_ROOT_KEY +
//						"\n from " + mListTable + "\n " + " where level = " + (levelId+1) +
//						"\n Group by " + collatedCols + "," + DOM_ROOT_KEY + " Collate UNICODE";
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
	
	/**
	 * Clear and the build the temporary list of books based on the passed criteria.
	 * 
	 * @param primarySeriesOnly		Only fetch books primary series
	 * @param showSeries			If false, will not cross with series at al
	 * @param bookshelf				Search criteria: limit to shelf
	 * @param authorWhere			Search criteria: additional conditions that apply to authors table
	 * @param bookWhere				Search criteria: additional conditions that apply to book table
	 * @param loaned_to				Search criteria: only books loaned to named person
	 * @param seriesName			Search criteria: only books in named series
	 * @param searchText			Search criteria: book details must in some way contain the passed text
	 * 
	 */
	public void build(long markId, String bookshelf, String authorWhere, String bookWhere, String loaned_to, String seriesName, String searchText) {
		long t0 = System.currentTimeMillis();

		mListTable = TBL_BOOK_LIST_DEFN.clone();
		mListTable.setName(mListTable.getName() + "_" + getId());
		mListTable.setIsTemporary(true); //TODO Make sure is TEMPORARY

		mNavTable = TBL_ROW_NAVIGATOR_DEFN.clone()
				.addReference(mListTable, DOM_REAL_ROW_ID)
				;
		mNavTable.setName(mNavTable.getName() + "_" + getId());
		mNavTable.setIsTemporary(true); //TODO Make sure is TEMPORARY

		SummaryBuilder summary = new SummaryBuilder();
		// Add the minimum required domains

		summary.addDomain(DOM_ID);
		summary.addDomain(DOM_LEVEL, Integer.toString(mStyle.size()+1), SummaryBuilder.FLAG_NONE);
		summary.addDomain(DOM_KIND, "" + ROW_KIND_BOOK, SummaryBuilder.FLAG_NONE);
		summary.addDomain(DOM_BOOK, TBL_BOOKS.dot(DOM_ID), SummaryBuilder.FLAG_NONE);
		summary.addDomain(DOM_BOOK_COUNT, "1", SummaryBuilder.FLAG_NONE);
		summary.addDomain(DOM_ROOT_KEY);
		//summary.addDomain(DOM_PARENT_KEY);		
		
		BooklistSeriesGroup seriesLevel = null;
		BooklistAuthorGroup authorLevel = null;
		boolean hasLevelLOANED = false;
		
		long t0a = System.currentTimeMillis();
	
		for (BooklistGroup l : mStyle) {
			//
			//	Build each row-kind group. 
			//
			//  ****************************************************************************************
			//  IMPORTANT NOTE: for each row kind, then FIRST SORTED AND GROUPED domain should be the one 
			//					that will be displayed and that level in the UI.
			//  ****************************************************************************************
			//
			switch (l.kind) {
			
			case ROW_KIND_SERIES:
				l.displayDomain = DOM_SERIES_NAME;
				seriesLevel = (BooklistSeriesGroup) l; // getLevel(ROW_KIND_SERIES);
				summary.addDomain(DOM_SERIES_NAME, TBL_SERIES.dot(DOM_SERIES_NAME), SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				summary.addDomain(DOM_SERIES_ID, TBL_BOOK_SERIES.dot(DOM_SERIES_ID), SummaryBuilder.FLAG_GROUPED);
				summary.addDomain(DOM_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_SERIES_NUM), SummaryBuilder.FLAG_NONE);
				summary.addDomain(DOM_SERIES_POSITION, TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION), SummaryBuilder.FLAG_NONE);
				summary.addDomain(DOM_PRIMARY_SERIES_COUNT,"case when Coalesce(" + TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION) + ",1) == 1 then 1 else 0 end", SummaryBuilder.FLAG_NONE);
				summary.addDomain(DOM_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_SERIES_NUM), SummaryBuilder.FLAG_SORTED);
				//summary.addKeyComponents("'s'", TBL_BOOK_SERIES.dot(DOM_SERIES_ID));
				l.setKeyComponents("s", DOM_SERIES_ID);
				break;

			case ROW_KIND_AUTHOR:
				l.displayDomain = DOM_AUTHOR_FORMATTED;
				authorLevel = (BooklistAuthorGroup) l; //getLevel(ROW_KIND_AUTHOR);
				summary.addDomain(DOM_AUTHOR_SORT, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				if (authorLevel.givenName)
					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
				else
					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);

				summary.addDomain(DOM_AUTHOR_ID, TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID), SummaryBuilder.FLAG_GROUPED);

				//summary.addKeyComponents("'a'", TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID));
				l.setKeyComponents("a", DOM_AUTHOR_ID);

				break;

			case ROW_KIND_GENRE:
				l.displayDomain = DOM_GENRE;
				summary.addDomain(DOM_GENRE, TBL_BOOKS.dot(DOM_GENRE), SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				//summary.addKeyComponents("'g'", TBL_BOOKS.dot(DOM_GENRE));
				l.setKeyComponents("g", DOM_GENRE);
				break;

			case ROW_KIND_PUBLISHER:
				l.displayDomain = DOM_PUBLISHER;
				summary.addDomain(DOM_PUBLISHER, TBL_BOOKS.dot(DOM_PUBLISHER), SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				//summary.addKeyComponents("'p'", TBL_BOOKS.dot(DOM_PUBLISHER));
				l.setKeyComponents("p", DOM_PUBLISHER);
				break;

			case ROW_KIND_UNREAD:
				l.displayDomain = DOM_READ_STATUS;
				String unreadExpr = "Case When " + TBL_BOOKS.dot(DOM_READ) + " = 1 " +
						" Then '" + BookCatalogueApp.getResourceString(R.string.booklist_read) + "'" +
						" Else '" + BookCatalogueApp.getResourceString(R.string.booklist_unread) + "' end";
				summary.addDomain(DOM_READ_STATUS, unreadExpr, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				summary.addDomain(DOM_READ, TBL_BOOKS.dot(DOM_READ), SummaryBuilder.FLAG_GROUPED);
				//summary.addKeyComponents("'r'", TBL_BOOKS.dot(DOM_READ));
				l.setKeyComponents("r", DOM_READ);
				break;

			case ROW_KIND_LOANED:
				hasLevelLOANED = true;
				l.displayDomain = DOM_LOANED_TO;
				summary.addDomain(DOM_LOANED_TO, LOANED_TO_EXPRESSION, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				//summary.addKeyComponents("'l'", DatabaseDefinitions.TBL_LOAN.dot(DOM_LOANED_TO));
				l.setKeyComponents("l", DOM_LOANED_TO);
				break;

			case ROW_KIND_TITLE_LETTER:
				l.displayDomain = DOM_TITLE_LETTER;
				String titleLetterExpr = "substr(" + TBL_BOOKS.dot(DOM_TITLE) + ",1,1)";
				summary.addDomain(DOM_TITLE_LETTER, titleLetterExpr, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				//summary.addKeyComponents("'t'", titleLetterExpr);
				l.setKeyComponents("t", DOM_TITLE_LETTER);
				break;

			case ROW_KIND_YEAR_PUBLISHED:
				l.displayDomain = DOM_PUBLICATION_YEAR;
				String yearPubExpr = "case when " + TBL_BOOKS.dot(KEY_DATE_PUBLISHED) + " glob '[0123456789][01234567890][01234567890][01234567890]*'" +
										" Then substr(date_published, 1, 4) \n" +
										" else 'UNKNOWN' end";
				summary.addDomain(DOM_PUBLICATION_YEAR, yearPubExpr, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				l.setKeyComponents("yr", DOM_PUBLICATION_YEAR);
				break;

			case ROW_KIND_MONTH_PUBLISHED:
				l.displayDomain = DOM_PUBLICATION_MONTH;
				String monthPubExpr = "case when " + TBL_BOOKS.dot(KEY_DATE_PUBLISHED) + 
										" glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789][01234567890]*'" +
										" Then substr(date_published, 6, 2) \n" +
										" when " + TBL_BOOKS.dot(KEY_DATE_PUBLISHED) + 
										" glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789]*'" +
										" Then substr(date_published, 6, 1) \n" +
										" else 'UNKNOWN' end";
				summary.addDomain(DOM_PUBLICATION_MONTH, monthPubExpr, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
				l.setKeyComponents("mn", DOM_PUBLICATION_MONTH);
				break;

			default:
				throw new RuntimeException("Unsupported group type " + l.kind);

			}
			// Copy the current groups to this level item; this effectively accumulates 'group by' domains 
			// down each level so that the top has fewest groups and the bottom level has groups for all levels.
			l.groupDomains = (ArrayList<DomainDefinition>) summary.cloneGroups();
		}
		long t0b = System.currentTimeMillis();

		if (markId != 0) {
			summary.addDomain(DOM_MARK, TBL_BOOKS.dot(DOM_ID) + " = " + markId, SummaryBuilder.FLAG_NONE);
		}
		// TODO: Break published_date into year/month/day
		//if (this.hasLevel(ROW_KIND_YEAR_PUBLISHED)) {
		//	tblDef
		//		.addDomain(DOM_PUBLICATION_YEAR);
		//}
		//if (this.hasLevel(ROW_KIND_MONTH_PUBLISHED)) {
		//	tblDef
		//		.addDomain(DOM_PUBLICATION_MONTH);
		//}

		// Ensure any caller-specified extras (eg. title) are added at the end.
		for(Entry<String,ExtraDomainInfo> d : mExtraDomains.entrySet()) {
			ExtraDomainInfo info = d.getValue();
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
		String sql = summary.buildBaseInsert(mStyle.get(0).getCompoundKey());

		long t0d = System.currentTimeMillis();

		JoinContext join;

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
		if (hasLevelLOANED) {
			join.join(TBL_LOAN);
		}
		
		// Specify a parent in the join, because the last table joined was one of BOOKS or LOAN.
		join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);
		if (authorLevel == null || !authorLevel.allAuthors) {
			join.append( "		and " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) + " == 1\n");
		}
		join.join(TBL_AUTHORS);
		join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);
			/*
			sql +=	"    join " + DB_TB_BOOK_AUTHOR_AND_ALIAS + " on " + ALIAS_BOOK_AUTHOR + "." + KEY_BOOK + " = " + ALIAS_BOOKS + "." + KEY_ROWID + "\n" +
					"    join " + DB_TB_AUTHORS_AND_ALIAS + " on " + ALIAS_AUTHORS + "." + KEY_ROWID + " = " + ALIAS_BOOK_AUTHOR + "." + KEY_AUTHOR_ID + "\n";
			sql +=	"    left outer join " + DB_TB_BOOK_SERIES_AND_ALIAS + " on " + ALIAS_BOOK_SERIES + "." + KEY_BOOK + " = " + ALIAS_BOOKS + "." + KEY_ROWID + "\n";
			*/

		if (seriesLevel == null || !seriesLevel.allSeries) {
			join.append( "		and " + TBL_BOOK_SERIES.dot(DOM_SERIES_POSITION) + " == 1\n");
		}
		join.leftOuterJoin(TBL_SERIES);
			/*
			if (seriesLevel == null || !seriesLevel.allSeries) {
				sql += "		and " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_POSITION + " == 1\n";
			}
			sql +=	"    left outer join " + DB_TB_SERIES_AND_ALIAS + " on " + ALIAS_SERIES + "." + KEY_ROWID + " = " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_ID;
			*/

		// Append the joined tables to our initial insert statement
		sql += join.toString();

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
			where += "(" + TBL_BOOKS.dot(DOM_ID) + " in (select docid from " + DB_TB_BOOKS_FTS + " where " + DB_TB_BOOKS_FTS + " match '" + encodeString(searchText) + "'))";
		}

		// If we got any conditions, add them to the initial insert statement
		if (!where.equals(""))
			sql += " where " + where.toString();

		long t1 = System.currentTimeMillis();

		{
			final ArrayList<DomainDefinition> sort = summary.getExtraSort();
			final StringBuilder sortCols = new StringBuilder();
			for (DomainDefinition d: sort) {
				sortCols.append(d.name);
				sortCols.append(" Collate UNICODE, ");
			}
			sortCols.append(DOM_LEVEL.name);
			mSortColumnList = sortCols.toString();
		}

		{
			final ArrayList<DomainDefinition> group = summary.cloneGroups();
			final StringBuilder groupCols = new StringBuilder();;
			for (DomainDefinition d: group) {
				groupCols.append(d.name);
				groupCols.append(" Collate UNICODE, ");
			}
			groupCols.append( DOM_LEVEL.name );
			mGroupColumnList = groupCols.toString();
		}

		String ix1Sql = "Create Index " + mListTable + "_IX1 on " + mListTable + "(" + mSortColumnList + ")";
		String ix1aSql = "Create Index " + mListTable + "_IX1a on " + mListTable + "(" + DOM_LEVEL + ", " + mSortColumnList + ")";
		String ix2Sql = "Create Unique Index " + mListTable + "_IX2 on " + mListTable + "(" + DOM_BOOK + ", " + DOM_ID + ")";

		String ix3Sql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList + ")";
		String ix3aSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList + ")";
		String ix3bSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_LEVEL + ")";
		String ix3cSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_ROOT_KEY + " Collate UNICODE)";
		String ix3dSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList +  ", " + DOM_ROOT_KEY + ")";
		String ix3eSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_ROOT_KEY + "," + DOM_LEVEL + ")";
		String ix4Sql = "Create Index " + mListTable + "_IX4 on " + mListTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")";

		// We are good to go.
		long t1a = System.currentTimeMillis();
		//mDb.execSQL("PRAGMA synchronous = OFF");
		SyncLock txLock = mDb.beginTransaction(true);
		long t1b = System.currentTimeMillis();
		try {
			// Build the lowest level summary using our initial insert statement
			mBaseBuildStmt = mStatements.add("mBaseBuildStmt", sql);
			//System.out.println("Base Build:\n" + sql);
			mBaseBuildStmt.execute();

			/*
			 Create table foo(level int, g1 text, g2 text, g3 text);
			 Create Temp Trigger foo_tgL2 before insert on foo for each row when new.level = 3 and not exists(Select * From foo where level = 2 and g1=new.g1 and g2= foo.g2)
			 Begin
			    Insert into foo(level, g1, g2) values (2, new.g1, new.g2);
			   End;
			 Create Temp Trigger foo_tgL1 before insert on foo for each row when new.level = 2 and not exists(Select * From foo where level = 1 and g1=new.g1)
			 Begin
			    Insert into foo(level, g1) values (1, new.g1);
			   End;

			 Create Temp Trigger L3 After Insert On mListTable For Each Row When LEVEL = 3 Begin
			 Insert into mListTable (level, kind, root_key, <cols>) values (new.level-1, new.<cols>)
			 Where not exists
			 */
			long t1c = System.currentTimeMillis();
			//mDb.execSQL(ix3cSql);
			long t1d = System.currentTimeMillis();
			//mDb.execSQL("analyze " + mListTable);
			
			long t2 = System.currentTimeMillis();

			// Now build each summary level query based on the prior level.
			// We build and run from the bottom up.
			mLevelBuildStmts = new ArrayList<SQLiteStatement>();

			long t2a[] = new long[mStyle.size()];
			int pos=0;
			for (int i = mStyle.size()-1; i >= 0; i--) {
				final BooklistGroup l = mStyle.get(i);
				final int levelId = i + 1;
				String cols = "";
				String collatedCols = "";
				for(DomainDefinition d  : l.groupDomains) {
					if (!collatedCols.equals(""))
						collatedCols += ",";
					cols += ",\n	" + d.name;
					//collatedCols += ",\n	" + d.name + " Collate UNICODE";
					collatedCols += "\n	" + d.name + " Collate UNICODE";
				}
				sql = "Insert Into " + mListTable + "(\n	" + DOM_LEVEL + ",\n	" + DOM_KIND + 
						//",\n	" + DOM_PARENT_KEY +
						cols + "," + DOM_ROOT_KEY +
						")" +
						"\n select " + levelId + " as " + DOM_LEVEL + ",\n	" + l.kind + " as " + DOM_KIND +
						//l.getKeyExpression() +
						cols + "," + DOM_ROOT_KEY +
						"\n from " + mListTable + "\n " + " where level = " + (levelId+1) +
						"\n Group by " + collatedCols + "," + DOM_ROOT_KEY + " Collate UNICODE";
						//"\n Group by " + DOM_LEVEL + ", " + DOM_KIND + collatedCols;

				SQLiteStatement stmt = mStatements.add("L" + i, sql);
				mLevelBuildStmts.add(stmt);
				stmt.execute();
				t2a[pos++] = System.currentTimeMillis();
			}

			SQLiteStatement stmt;
			long t3 = System.currentTimeMillis();
			stmt = mStatements.add("ix1", ix1Sql);
			mLevelBuildStmts.add(stmt);
			stmt.execute();
			long t3a = System.currentTimeMillis();
			mDb.execSQL("analyze " + mListTable);
			long t3b = System.currentTimeMillis();
			
			// Now build a lookup table to match row sort position to row ID. This is used to match a specific
			// book (or other row in result set) to a position directly.
			mNavTable.drop(mDb);
			mNavTable.create(mDb, true);
			sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) + 
					" Select " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
					" ,\n	Case When " + DOM_LEVEL + " = 1 Then 1 \n" +
					"	When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0\n	Else 1 end,\n "+ 
					"	Case When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0 Else 1 end\n"+
					" From " + mListTable.ref() + "\n	left outer join " + TBL_BOOK_LIST_NODE_SETTINGS.ref() + 
					"\n		On " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " = " + mListTable.dot(DOM_ROOT_KEY) +
					"\n			And " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_KIND) + " = " + mStyle.get(0).kind +
					"\n	Order by " + mSortColumnList;

			stmt = mStatements.add("InsNav", sql);
			mLevelBuildStmts.add(stmt);
			stmt.execute();

			long t4 = System.currentTimeMillis();
			mDb.execSQL("Create Index " + mNavTable + "_IX1" + " On " + mNavTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")");
			long t4a = System.currentTimeMillis();
			// Essential for main query! If not present, will make getCount() take ages because main query is a cross with no index.
			mDb.execSQL("Create Unique Index " + mNavTable + "_IX2" + " On " + mNavTable + "(" + DOM_REAL_ROW_ID + ")");
			long t4b = System.currentTimeMillis();
			mDb.execSQL("analyze " + mNavTable);
			long t4c = System.currentTimeMillis();
			
			/*
			// Create Index book_list_tmp_row_pos_1_ix2 on book_list_tmp_row_pos_1(level, expanded, root_key);
			long t5 = System.currentTimeMillis();
			sql = "Update " + navName + " set expanded = 1 where level = 1 and " +
					"exists(Select _id From " + TBL_BOOK_LIST_NODE_SETTINGS + " x2 Where x2.kind = " + mLevels.get(0).kind + " and x2.root_key = " + navName + ".root_key)";
			mDb.execSQL(sql);
			long t6 = System.currentTimeMillis();
			sql = "Update " + navName + " set visible = 1, expanded = 1 where level > 1 and " +
					"exists(Select _id From " + navName + " x2 Where x2.level = 1 and x2.root_key = " + navName + ".root_key and x2.expanded=1)";
			mDb.execSQL(sql);
			long t7 = System.currentTimeMillis();
			sql = "Update " + navName + " set visible = 1 where level = 1";
			mDb.execSQL(sql);
			*/

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
			System.out.println("T1c: " + (t1c-t1b));
			System.out.println("T1d: " + (t1d-t1c));
			System.out.println("T2: " + (t2-t1d));
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
			System.out.println("T10: " + (t11-t10));

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
	}

	public void saveNodeSettings() {
		TBL_BOOK_LIST_NODE_SETTINGS.createIfNecessary(mDb, true);
		
		// TODO: Standardize
		// Create if necessary
		long t0 = System.currentTimeMillis();
		int kind = mStyle.get(0).kind;
		String sql = "Delete from " + TBL_BOOK_LIST_NODE_SETTINGS + " Where kind = " + kind;
		mDb.execSQL(sql);
		sql = TBL_BOOK_LIST_NODE_SETTINGS.getInsert(DOM_KIND,DOM_ROOT_KEY) + 
				" Select Distinct " + kind + ", " + DOM_ROOT_KEY + " From " + mNavTable + " Where expanded = 1 and level = 1";
		mDb.execSQL(sql);
		//SQLiteStatement stmt = mDb.compileStatement("select count(*) from " + TBL_BOOK_LIST_NODE_SETTINGS);
		//long count = stmt.simpleQueryForLong();
		//stmt.close();
		//long t1 = System.currentTimeMillis() - t0;
		//System.out.println("Save " + count + " Nodes: " + t1);
	}
	public int[] getBookAbsolutePositions(long bookId) {
		String sql = "select " + mNavTable.dot(DOM_ID) + " From " + mListTable + " bl " 
				+ mListTable.join(mNavTable) + " Where " + mListTable.dot(DOM_BOOK) + " = " + bookId;

		Cursor c = mDb.rawQuery(sql, EMPTY_STRING_ARRAY);
		try {
			ArrayList<Integer> rows = new ArrayList<Integer>();
			if (c.moveToFirst()) {
				do {
					rows.add(c.getInt(0));
				} while (c.moveToNext());
				int[] rowsInt = new int[rows.size()];
				int pos = 0;
				// Copy the IDs to row #, and fix (IDs start at 1)
				for(Integer i : rows)
					rowsInt[pos++] = i-1;
				return rowsInt;
			} else {
				return null;
			}			
		} finally {
			c.close();
		}
	}

	public BooklistCursor getList() {
		StringBuilder domains = new StringBuilder();
		final String prefix = mListTable.getAlias() + ".";
		for(DomainDefinition d: mListTable.domains) {
			domains.append(prefix);
			domains.append(d.name);
			domains.append(", ");
		}
		StringBuilder sortCols = new StringBuilder();
		for(DomainDefinition d: mSummary.getExtraSort()) {
			sortCols.append(prefix);
			sortCols.append(d.name);
			sortCols.append(" Collate UNICODE,");
		}
		sortCols.append(prefix);
		sortCols.append(DOM_LEVEL.name);
		//
		// We can not use '.*' notation here because something (Android/SQLite) seems to remember previous queries
		// and incorrectly assume the table is unchanged in the background with the result that subtle changes in table
		// format result in off-by-one errors in sqlite getColumnIndex(name). Putting explicit column names here fixes it.
		//
		//final String sql = "select " + mListTable.dot("*") + ", (" + mNavTable.dot(DOM_ID) + " - 1) As " + DOM_ABSOLUTE_POSITION + 
		final String sql = "select " + domains + " (" + mNavTable.dot(DOM_ID) + " - 1) As " + DOM_ABSOLUTE_POSITION + 
				" from " + mListTable.ref() + mListTable.join(mNavTable) + 
				" Where " + mNavTable.dot(DOM_VISIBLE) + " = 1 Order by " + sortCols;	

		final BooklistCursor c = (BooklistCursor) mDb.rawQueryWithFactory(mBooklistCursorFactory, sql, EMPTY_STRING_ARRAY, "");
		//System.out.print("BooklistBuilder returning cursor " + c.getId() + " with " + c.getColumnCount() + " columns: ");
		//for(int i = 0; i< c.getColumnCount(); i++) {
		//	if (i > 0)
		//		System.out.print(", ");
		//	System.out.print(c.getColumnName(i));
		//}

		return c;			
	}
	public BooklistCursor getNavigator() {
		String sql = "select * from " + mListTable + " Where level <= " + mStyle.size() + " Order by " + mSortColumnList;
		return (BooklistCursor) mDb.rawQueryWithFactory(mBooklistCursorFactory, sql, EMPTY_STRING_ARRAY, "");
	}
	/**
	 * Using a 1-based index, retrieve the domain that is displayed in the summary for this level.
	 * 
	 * @param level
	 * 
	 * @return
	 */
	public DomainDefinition getDisplayDomain(int level) {
		return mStyle.get(level-1).displayDomain;
	}
	
	public int numLevels() {
		return mStyle.size()+1;
	}

	private SQLiteStatement mGetPositionCheckVisibleStmt = null;
	private SQLiteStatement mGetPositionStmt = null;
	public int getPosition(int absolutePosition) {
		if (mGetPositionCheckVisibleStmt == null) {
			String sql = "Select visible from " + mNavTable + " Where " + DOM_ID + " = ?";
			mGetPositionCheckVisibleStmt = mStatements.add("mGetPositionCheckVisibleStmt", sql);	
		}
		if (mGetPositionStmt == null) {
			String sql = "Select count(*) From " + mNavTable + " Where visible = 1 and " + DOM_ID + " < ?";
			mGetPositionStmt = mStatements.add("mGetPositionStmt", sql);					
		}

		mGetPositionCheckVisibleStmt.bindLong(1, absolutePosition);
		long isVis;
		try {
			isVis = mGetPositionCheckVisibleStmt.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			// row is not in the vurrent list at all
			isVis = 0;
		}
		mGetPositionStmt.bindLong(1, absolutePosition);
		int newPos = (int)mGetPositionStmt.simpleQueryForLong();
		if (isVis == 1) 
			return newPos;
		else
			return newPos - 1;

	}

	private SQLiteStatement mGetNodeRootStmt = null;
	public void ensureAbsolutePositionVisible(long absPos) {
		if (absPos < 0)
			return;

		final long rowId = absPos + 1;

		if (mGetNodeRootStmt == null) {
			String sql = "Select " + DOM_ID + "||'/'||" + DOM_EXPANDED + " From " + mNavTable + " Where " + DOM_LEVEL + " = 1 and " + DOM_ID + " < ? Order by " + DOM_ID + " Desc Limit 1";
			mGetNodeRootStmt = mStatements.add("mGetNodeRootStmt", sql);
		}

		mGetNodeRootStmt.bindLong(1, rowId);
		String info[] = mGetNodeRootStmt.simpleQueryForString().split("/");
		long rootId = Long.parseLong(info[0]);
		long isExp = Long.parseLong(info[1]);
		if (isExp == 0)
			toggleExpandNode(rootId -1);
	}

	private SQLiteStatement mGetNodeLevelStmt = null;
	private SQLiteStatement mGetNextAtSameLevelStmt = null;
	private SQLiteStatement mShowStmt = null;
	private SQLiteStatement mExpandStmt = null;

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
	
	public void expandAll(boolean expand) {
		long t0 = System.currentTimeMillis();
		if (expand) {
			String sql = "Update " + mNavTable + " Set expanded = 1, visible = 1";
			mDb.execSQL(sql);
		} else {				
			String sql = "Update " + mNavTable + " Set expanded = 0, visible = 0 Where level > 1";
			mDb.execSQL(sql);
			sql = "Update " + mNavTable + " Set expanded = 0 Where level = 1";
			mDb.execSQL(sql);
		}
		long t1 = System.currentTimeMillis() - t0;
		System.out.println("Expand All: " + t1);
	}

	public void toggleExpandNode(long absPos) {
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
				return new BooklistCursor(db, masterQuery, editTable, query, BooklistBuilder.this);
			}
	};


	public void close() {
		mStatements.close();
	}
	
	public void finalize() {
		if (mStatements.size() != 0) {
			System.out.println("Finalizing BooklistBuilder with active statements");
			mStatements.close();
		}
	}
}
