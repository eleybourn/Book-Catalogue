package com.eleybourn.bookcatalogue.booklist;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.BooklistGroup;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;

import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.*;

/**
 * Represents a specific style of book list (eg. authors/series). Individual BooklistGroup objects
 * are added to a style in order to describe the resulting list style.
 * 
 * ENHANCE: Allow for style-based overrides of things currently stored in preferences
 * 			This should include thumbnail presence/size, book-in-each-series etc. as well as font sizes.
 * 
 * How to add a new Group:
 * 
 * - add it to RowKinds. Update ROW_KIND_MAX.
 * - add new domain to DatabaseDefinitions (if necessary)
 * - modify BooklistBuilder.build() to add the necessary grouped/sorted domains
 * - modify BooksMultitypeListHandler; if it is just a string field, then use a GenericHolder. Otherwise add a new holder.
 *   Need to at least modify BooksMultitypeListHandler.newHolder().
 * 
 * @author Grunthos
 */
public class BooklistStyle extends ArrayList<BooklistGroup> {
	private static Integer mBooklistStylesIdCounter = 0;
	private static final long serialVersionUID = -5546218466096517529L;

	/**
	 * Static definitions of the kinds of rows that can be displayed and summarized.
	 * Adding new row types needs to involve changes to:
	 * 
	 *	- BooklistBuilder (to build the correct SQL)
	 *	- BooksMultitypeListHandler (to know what to do with the new type)
	 * 
	 * @author Grunthos
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
		public static final int ROW_KIND_YEAR_ADDED = 10;		// Supported
		public static final int ROW_KIND_MONTH_ADDED = 11;		// Supported
		public static final int ROW_KIND_DAY_ADDED = 12;		// Supported
		public static final int ROW_KIND_MAX = 12; 				// **** NOTE **** ALWAYS update after adding a row kind...				
	}

	/** 
	 * Represents a collection of domains that make a unique key for a given group.
	 * 
	 * @author Grunthos
	 */
	public static class CompoundKey {
		/** Unique prefix used to represent a key in the hierarchy */
		String prefix;
		/** List of domains in key */
		DomainDefinition[] domains;
		/** Constructor */
		CompoundKey(String prefix, DomainDefinition...domains) {
			this.prefix = prefix;
			this.domains = domains;
		}
	}

	/**
	 * Class representing a single level in the booklist hierarchy.
	 * 
	 * @author Grunthos
	 */
	public static class BooklistGroup {
		/** The Row Kind of this group */
		public int kind;
		/** The domains represented by this group. Set at runtime by builder based on current group and outer groups */
		public ArrayList<DomainDefinition> groupDomains;
		/** The domain used to display this group. Set at runtime by builder based on internal logic of builder */
		public DomainDefinition displayDomain;

		/** Compound key of this group. Set at runtime by builder based on current group and outer groups */
		private CompoundKey mCompoundKey;

		/** Setter for compound key */
		public void setKeyComponents(String prefix, DomainDefinition...domains) {
			mCompoundKey = new CompoundKey(prefix, domains);
		}
		
		/** Getter for compound key */
		public CompoundKey getCompoundKey() {
			return mCompoundKey;
		}
	}

	/**
	 * Specialized BooklistGroup representing an Author group. Includes extra attributes based
	 * on preferences.
	 * 
	 * @author Grunthos
	 */
	public static class BooklistSeriesGroup extends BooklistGroup {
		public boolean allSeries = BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_SHOW_ALL_SERIES, false);
		BooklistSeriesGroup() { kind = ROW_KIND_SERIES; }
	}

	/**
	 * Specialized BooklistGroup representing an Series group. Includes extra attributes based
	 * on preferences.
	 * 
	 * @author Grunthos
	 */
	public static class BooklistAuthorGroup extends BooklistGroup {
		public boolean allAuthors = BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_SHOW_ALL_AUTHORS, false);
		public boolean givenName = BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_DISPLAY_FIRST_THEN_LAST_NAMES, false);
		BooklistAuthorGroup() { kind = ROW_KIND_AUTHOR; }
	}

	
	/** Internal ID of this style. Not persistent across application starts */
	private final int mId;
	/** ID if string representing name of this style. Used for standard system-defined styles */
	public int mStringId;
	/** User-defined name of this style. Used for user-defined styles */
	public String mName;

	/**
	 * Constructor for system-defined styles.
	 * 
	 * @param stringId
	 */
	BooklistStyle(int stringId) {
		synchronized(mBooklistStylesIdCounter) {
			mId = ++mBooklistStylesIdCounter;
		}
		mStringId = stringId;
		mName = null;
	}

	/**
	 * Constructor for user-defined styles.
	 * 
	 * @param name
	 */
	BooklistStyle(String name) {
		synchronized(mBooklistStylesIdCounter) {
			mId = ++mBooklistStylesIdCounter;
		}
		mName = name;
		mStringId = 0;
	}
	/**
	 * Accessor.
	 *
	 * @return
	 */
	public int getId() {
		return mId;
	}
	/**
	 * Accessor. Returns system name or user-defined name based on kind of style this object defines.
	 *
	 * @return
	 */
	public String getName() {
		if (mStringId != 0)
			return BookCatalogueApp.getResourceString(mStringId);
		else
			return mName;
	}
	/**
	 * Add a group to this style below any already added groups.
	 * 
	 * @param kind		Kind of group to add.
	 * 
	 * @return 	Newly created group.
	 */
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

	/**
	 * Returns true if this style is user-defined.
	 *
	 * @return
	 */
	public boolean isUserDefined() {
		return (mStringId == 0);
	}
}

