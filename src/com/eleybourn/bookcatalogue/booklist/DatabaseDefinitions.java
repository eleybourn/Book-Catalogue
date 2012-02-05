package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.CatalogueDBAdapter.*;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.database.DbUtils.TableDefinition;

/**
 * TODO: Document more!
 * 
 * Static definitions of database objects; this is an incomplete representation of the BookCatalogue database. It should
 * probably become the 'real' representation of the database whe DbUtils is more mature. For now, it suffices to build
 * the complex queries used in BooklistBuilder.
 * 
 * @author Grunthos
 */
public class DatabaseDefinitions {

	private static final String ALIAS_BOOK_BOOKSHELF = "bbsh";
	private static final String ALIAS_BOOKSHELF = "bsh";
	private static final String ALIAS_BOOK_SERIES = "bs";
	private static final String ALIAS_SERIES = "s";
	private static final String ALIAS_BOOKS = "b";
	private static final String ALIAS_BOOK_LIST = "bl";
	private static final String ALIAS_BOOK_LIST_ROW_POSITION = "blrp";
	private static final String ALIAS_AUTHORS = "a";
	private static final String ALIAS_BOOK_AUTHOR = "ba";
	private static final String ALIAS_LOAN = "l";

	public static final String TBL_BOOK_LIST_NAME = "book_list_tmp";
	public static final String KEY_LEVEL = "level";
	public static final String KEY_KIND = "kind";
	public static final String KEY_BOOK_COUNT = "book_count";
	public static final String KEY_PRIMARY_SERIES_COUNT = "primary_series_count";

	public static final DomainDefinition DOM_ID = new DomainDefinition("_id", "integer", "primary key autoincrement", "not null");
	public static final DomainDefinition DOM_LEVEL = new DomainDefinition("level", "integer", "",  "not null");

	public static final DomainDefinition DOM_ABSOLUTE_POSITION = new DomainDefinition("absolute_position", "integer", "", "not null");
	public static final DomainDefinition DOM_AUTHOR_ID = new DomainDefinition(KEY_AUTHOR_ID, "integer", "", "not null");
	public static final DomainDefinition DOM_AUTHOR_SORT = new DomainDefinition("author_sort", "text", "",  "not null");
	public static final DomainDefinition DOM_AUTHOR_FORMATTED = new DomainDefinition(KEY_AUTHOR_FORMATTED, "text", "",  "not null");
	public static final DomainDefinition DOM_AUTHOR_POSITION = new DomainDefinition(KEY_AUTHOR_POSITION, "integer", "", "not null");
	public static final DomainDefinition DOM_BOOK = new DomainDefinition(KEY_BOOK, "integer", "", "");
	public static final DomainDefinition DOM_BOOK_COUNT = new DomainDefinition(KEY_BOOK_COUNT, "integer", "", "");
	public static final DomainDefinition DOM_BOOKSHELF_NAME = new DomainDefinition(KEY_BOOKSHELF, "text", "",  "not null");
	public static final DomainDefinition DOM_BOOKSHELF_ID = new DomainDefinition(KEY_BOOKSHELF, "integer", "",  "not null");
	public static final DomainDefinition DOM_EXPANDED = new DomainDefinition("expanded", "int", "default 0", "");
	public static final DomainDefinition DOM_FAMILY_NAME = new DomainDefinition(KEY_FAMILY_NAME, "text", "", "");
	public static final DomainDefinition DOM_GENRE = new DomainDefinition("genre", "text", "", "");
	public static final DomainDefinition DOM_GIVEN_NAMES = new DomainDefinition(KEY_GIVEN_NAMES, "text", "", "");
	public static final DomainDefinition DOM_KIND = new DomainDefinition("kind", "integer", "",  "not null");
	public static final DomainDefinition DOM_LOANED_TO = new DomainDefinition("loaned_to", "text", "",  "not null");
	public static final DomainDefinition DOM_MARK = new DomainDefinition("mark", "boolean", "default 0",  "");
	//public static final DomainDefinition DOM_PARENT_KEY = new DomainDefinition("parent_key", "text", "", "");
	public static final DomainDefinition DOM_PRIMARY_SERIES_COUNT = new DomainDefinition(KEY_PRIMARY_SERIES_COUNT, "integer", "", "");
	public static final DomainDefinition DOM_PUBLICATION_YEAR = new DomainDefinition("publication_year", "int", "", "");
	public static final DomainDefinition DOM_PUBLICATION_MONTH = new DomainDefinition("publication_month", "int", "", "");
	public static final DomainDefinition DOM_PUBLISHER = new DomainDefinition(KEY_PUBLISHER, "text", "", "");
	public static final DomainDefinition DOM_READ = new DomainDefinition(KEY_READ, "integer", "", "");
	public static final DomainDefinition DOM_READ_STATUS = new DomainDefinition("read_status", "text", "", "not null");
	public static final DomainDefinition DOM_REAL_ROW_ID = new DomainDefinition("real_row_id", "int", "", "");
	public static final DomainDefinition DOM_ROOT_KEY = new DomainDefinition("root_key", "text", "", "");
	public static final DomainDefinition DOM_SERIES_ID = new DomainDefinition(KEY_SERIES_ID, "integer", "", "");
	public static final DomainDefinition DOM_SERIES_NAME = new DomainDefinition(KEY_SERIES_NAME, "text", "", "");
	public static final DomainDefinition DOM_SERIES_NUM = new DomainDefinition(KEY_SERIES_NUM, "integer", "", "");
	public static final DomainDefinition DOM_SERIES_POSITION = new DomainDefinition(KEY_SERIES_POSITION, "integer", "", "");
	public static final DomainDefinition DOM_TITLE = new DomainDefinition(KEY_TITLE, "text", "", "");
	public static final DomainDefinition DOM_TITLE_LETTER = new DomainDefinition("title_letter", "text", "", "");
	public static final DomainDefinition DOM_VISIBLE = new DomainDefinition("visible", "int", "default 0", "");

	public static final TableDefinition TBL_BOOK_LIST_DEFN = new TableDefinition(TBL_BOOK_LIST_NAME, DOM_ID, DOM_LEVEL, DOM_KIND, 
			// Many others...this is a temp table created at runtime.
			DOM_BOOK_COUNT, DOM_PRIMARY_SERIES_COUNT)
					.setIsTemporary(true)
					.setPrimaryKey(DOM_ID)
					.setAlias(ALIAS_BOOK_LIST)
					;		

	public static final TableDefinition TBL_BOOKS = new TableDefinition(CatalogueDBAdapter.DB_TB_BOOKS)
		.addDomains(DOM_ID, DOM_TITLE)
		.setAlias(ALIAS_BOOKS)
		.setPrimaryKey(DOM_ID);
	
	public static final TableDefinition TBL_AUTHORS = new TableDefinition(CatalogueDBAdapter.DB_TB_AUTHORS)
		.addDomains(DOM_ID, DOM_GIVEN_NAMES, DOM_FAMILY_NAME)
		.setAlias(ALIAS_AUTHORS)
		.setPrimaryKey(DOM_ID);

	public static final TableDefinition TBL_BOOK_AUTHOR = new TableDefinition(CatalogueDBAdapter.DB_TB_BOOK_AUTHOR)
		.addDomains(DOM_BOOK, DOM_AUTHOR_ID)
		.setAlias(ALIAS_BOOK_AUTHOR)
		.addReference(TBL_BOOKS, DOM_BOOK)
		.addReference(TBL_AUTHORS, DOM_AUTHOR_ID);
		;

	public static final TableDefinition TBL_SERIES = new TableDefinition(CatalogueDBAdapter.DB_TB_SERIES)
		.addDomains(DOM_ID, DOM_SERIES_NAME)
		.setAlias(ALIAS_SERIES)
		.setPrimaryKey(DOM_ID);

	public static final TableDefinition TBL_BOOK_SERIES = new TableDefinition(CatalogueDBAdapter.DB_TB_BOOK_SERIES)
		.addDomains(DOM_BOOK, DOM_SERIES_ID, DOM_SERIES_NUM, DOM_SERIES_POSITION)
		.setAlias(ALIAS_BOOK_SERIES)
		.setPrimaryKey(DOM_BOOK, DOM_SERIES_POSITION)
		.addReference(TBL_BOOKS, DOM_BOOK)
		.addReference(TBL_SERIES, DOM_SERIES_ID)
		;

	public static final TableDefinition TBL_BOOKSHELF = new TableDefinition(CatalogueDBAdapter.DB_TB_BOOKSHELF)
		.addDomains(DOM_ID, DOM_BOOKSHELF_NAME)
		.setAlias(ALIAS_BOOKSHELF)
		.setPrimaryKey(DOM_ID)
		;

	public static final TableDefinition TBL_BOOK_BOOKSHELF = new TableDefinition(CatalogueDBAdapter.DB_TB_BOOK_BOOKSHELF_WEAK)
		.addDomains(DOM_BOOK, DOM_BOOKSHELF_ID)
		.setAlias(ALIAS_BOOK_BOOKSHELF)
		.setPrimaryKey(DOM_BOOK, DOM_BOOKSHELF_ID)
		.addReference(TBL_BOOKS, DOM_BOOK)
		.addReference(TBL_BOOKSHELF, DOM_BOOKSHELF_ID)
		;

	public static final TableDefinition TBL_LOAN = new TableDefinition(CatalogueDBAdapter.DB_TB_LOAN)
		.addDomains(DOM_ID, DOM_BOOK, DOM_LOANED_TO)
		.setPrimaryKey(DOM_ID)
		.setAlias(ALIAS_LOAN)
		.addReference(TBL_BOOKS, DOM_BOOK)
	;

	public static final TableDefinition TBL_ROW_NAVIGATOR_DEFN = new TableDefinition(TBL_BOOK_LIST_NAME + "_row_pos", 
			DOM_ID, DOM_REAL_ROW_ID, DOM_LEVEL, DOM_VISIBLE, DOM_EXPANDED, DOM_ROOT_KEY)
		.setIsTemporary(true)
		.addReference(TBL_BOOK_LIST_DEFN, DOM_REAL_ROW_ID)
		.setAlias(ALIAS_BOOK_LIST_ROW_POSITION)
	;

	public static final TableDefinition TBL_BOOK_LIST_NODE_SETTINGS = new TableDefinition(TBL_BOOK_LIST_NAME + "_node_settings",
			DOM_ID, DOM_KIND, DOM_ROOT_KEY)
		.setAlias("blns")
		.addIndex("ROOT_KIND", true, DOM_ROOT_KEY, DOM_KIND)
		.addIndex("KIND_ROOT", true, DOM_KIND, DOM_ROOT_KEY);
		;
}
