package com.eleybourn.bookcatalogue.booklist;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.BooklistGroup;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;

import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.*;

/**
 * TODO: Document!
 * 
 * @author Grunthos
 */
public class BooklistStyle extends ArrayList<BooklistGroup> {
	private static Integer mBooklistStylesIdCounter = 0;
	private static final long serialVersionUID = -5546218466096517529L;

	/**
	 * Static definitions of the kinds of rows that can be displayed and summarized.
	 * 
	 * @author Grunthos
	 *
	 */
	public static final class RowKinds {
		public static final int ROW_KIND_BOOK = 0;				// Supported
		public static final int ROW_KIND_AUTHOR = 1;			// Supported
		public static final int ROW_KIND_SERIES = 2;			// Supported
		public static final int ROW_KIND_GENRE = 3;				// Supported
		public static final int ROW_KIND_PUBLISHER = 4;			// Supported
		public static final int ROW_KIND_UNREAD = 5;			// Supported
		public static final int ROW_KIND_LOANED = 6;			// Supported
		public static final int ROW_KIND_YEAR_PUBLISHED = 7;	// Supported
		public static final int ROW_KIND_MONTH_PUBLISHED = 8;	// Supported
		public static final int ROW_KIND_TITLE_LETTER = 9;		// Supported
		public static final int ROW_KIND_MAX = 9; // **** NOTE **** ALWAYS update after adding a row kind...				
	}

	public static class CompoundKey {
		String prefix;
		DomainDefinition[] domains;
		CompoundKey(String prefix, DomainDefinition...domains) {
			this.prefix = prefix;
			this.domains = domains;
		}
	}

	public static class BooklistGroup {
		public static final int FLAG_AUTHORS_SHOW_ALL = 1;
		public static final int FLAG_AUTHORS_USE_GIVEN_NAME = 2;
		public static final int FLAG_AUTHORS_MASK = FLAG_AUTHORS_SHOW_ALL + FLAG_AUTHORS_USE_GIVEN_NAME;

		public static final int FLAG_SERIES_SHOW_ALL = 4;
		public static final int FLAG_SERIES_MASK = FLAG_SERIES_SHOW_ALL;

		public String[] groupFields;
		public int kind;
		public ArrayList<DomainDefinition> groupDomains;
		public DomainDefinition displayDomain;

		private CompoundKey mCompoundKey;
		public void setKeyComponents(String prefix, DomainDefinition...domains) {
			mCompoundKey = new CompoundKey(prefix, domains);
		}
		
		public CompoundKey getCompoundKey() {
			return mCompoundKey;
		}
	}

	public static class BooklistSeriesGroup extends BooklistGroup {
		public boolean allSeries = BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_SHOW_ALL_SERIES, false);
		BooklistSeriesGroup() { kind = ROW_KIND_SERIES; }
	}

	public static class BooklistAuthorGroup extends BooklistGroup {
		public boolean allAuthors = BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_SHOW_ALL_AUTHORS, false);
		public boolean givenName = BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_DISPLAY_FIRST_THEN_LAST_NAMES, false);
		BooklistAuthorGroup() { kind = ROW_KIND_AUTHOR; }
	}

	
	private final int mId;
	public int mStringId; // For system-defined
	public String mName; // For user-defined
	BooklistStyle(int stringId) {
		synchronized(mBooklistStylesIdCounter) {
			mId = ++mBooklistStylesIdCounter;
		}
		mStringId = stringId;
		mName = null;
	}
	BooklistStyle(String name) {
		synchronized(mBooklistStylesIdCounter) {
			mId = ++mBooklistStylesIdCounter;
		}
		mName = name;
		mStringId = 0;
	}
	public int getId() {
		return mId;
	}
	public String getName() {
		if (mStringId != 0)
			return BookCatalogueApp.getResourceString(mStringId);
		else
			return mName;
	}
	public BooklistGroup addGroup(int kind) {
		BooklistGroup l;
		switch(kind) {
		case ROW_KIND_AUTHOR:
			l = new BooklistAuthorGroup();
			break;
		case ROW_KIND_SERIES:
			l = new BooklistSeriesGroup();
			break;
		default:
			l = new BooklistGroup();
			l.kind = kind;
			break;
			
		}
		super.add(l);
		return l;
	}
}

