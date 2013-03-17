/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License V3
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

import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOK;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_DAY_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_DAY_READ;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_GENRE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOANED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_READ;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_PUBLISHER;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_READ_AND_UNREAD;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_SERIES;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_READ;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map.Entry;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.CompoundKey;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;

/**
 * Class representing a single level in the booklist hierarchy.
 * 
 * @author Philip Warner
 */
public class BooklistGroup implements Serializable {
	private static final long serialVersionUID = 1012206875683862714L;

	/**
	 * Static definitions of the kinds of rows that can be displayed and summarized.
	 * Adding new row types needs to involve changes to:
	 * 
	 *	- BooklistBuilder (to build the correct SQL)
	 *	- BooksMultitypeListHandler (to know what to do with the new type)
	 * 
	 * @author Philip Warner
	 */
	public static final class RowKinds {
		public static final int ROW_KIND_BOOK = 0;				// Supported
		public static final int ROW_KIND_AUTHOR = 1;			// Supported
		public static final int ROW_KIND_SERIES = 2;			// Supported
		public static final int ROW_KIND_GENRE = 3;				// Supported
		public static final int ROW_KIND_PUBLISHER = 4;			// Supported
		public static final int ROW_KIND_READ_AND_UNREAD = 5;	// Supported
		public static final int ROW_KIND_LOANED = 6;			// Supported
		public static final int ROW_KIND_YEAR_PUBLISHED = 7;	// Supported
		public static final int ROW_KIND_MONTH_PUBLISHED = 8;	// Supported
		public static final int ROW_KIND_TITLE_LETTER = 9;		// Supported
		public static final int ROW_KIND_YEAR_ADDED = 10;		// Supported
		public static final int ROW_KIND_MONTH_ADDED = 11;		// Supported
		public static final int ROW_KIND_DAY_ADDED = 12;		// Supported
		public static final int ROW_KIND_FORMAT = 13;			// Supported
		public static final int ROW_KIND_YEAR_READ = 14;		// Supported
		public static final int ROW_KIND_MONTH_READ = 15;		// Supported
		public static final int ROW_KIND_DAY_READ = 16;			// Supported
		public static final int ROW_KIND_LOCATION = 17;			// Supported
		public static final int ROW_KIND_LANGUAGE = 18;			// Supported
		// NEWKIND: Add new kinds here
		public static final int ROW_KIND_MAX = 18; 				// **** NOTE **** ALWAYS update after adding a row kind...				
	}
	
	private static final Hashtable<Integer, String> mRowKindNames = new Hashtable<Integer, String>();
	static {
		mRowKindNames.put(ROW_KIND_AUTHOR, BookCatalogueApp.getResourceString(R.string.author));
		mRowKindNames.put(ROW_KIND_SERIES, BookCatalogueApp.getResourceString(R.string.series));
		mRowKindNames.put(ROW_KIND_GENRE, BookCatalogueApp.getResourceString(R.string.genre));
		mRowKindNames.put(ROW_KIND_PUBLISHER, BookCatalogueApp.getResourceString(R.string.publisher));
		mRowKindNames.put(ROW_KIND_READ_AND_UNREAD, BookCatalogueApp.getResourceString(R.string.read_amp_unread));
		mRowKindNames.put(ROW_KIND_LOANED, BookCatalogueApp.getResourceString(R.string.loaned));
		mRowKindNames.put(ROW_KIND_YEAR_PUBLISHED, BookCatalogueApp.getResourceString(R.string.publication_year));
		mRowKindNames.put(ROW_KIND_MONTH_PUBLISHED, BookCatalogueApp.getResourceString(R.string.publication_month));
		mRowKindNames.put(ROW_KIND_TITLE_LETTER, BookCatalogueApp.getResourceString(R.string.sort_title_first_letter));
		mRowKindNames.put(ROW_KIND_YEAR_ADDED, BookCatalogueApp.getResourceString(R.string.added_year));
		mRowKindNames.put(ROW_KIND_MONTH_ADDED, BookCatalogueApp.getResourceString(R.string.added_month));
		mRowKindNames.put(ROW_KIND_DAY_ADDED, BookCatalogueApp.getResourceString(R.string.added_day));
		mRowKindNames.put(ROW_KIND_FORMAT, BookCatalogueApp.getResourceString(R.string.format));
		mRowKindNames.put(ROW_KIND_YEAR_READ, BookCatalogueApp.getResourceString(R.string.read_year));
		mRowKindNames.put(ROW_KIND_MONTH_READ, BookCatalogueApp.getResourceString(R.string.read_month));
		mRowKindNames.put(ROW_KIND_DAY_READ, BookCatalogueApp.getResourceString(R.string.read_day));
		mRowKindNames.put(ROW_KIND_LOCATION, BookCatalogueApp.getResourceString(R.string.location));
		mRowKindNames.put(ROW_KIND_LANGUAGE, BookCatalogueApp.getResourceString(R.string.language));
		// NEWKIND: Add new kinds here
		mRowKindNames.put(ROW_KIND_BOOK, BookCatalogueApp.getResourceString(R.string.book));		
	}

	/**
	 * Return a list of all defined row kinds.
	 *
	 * @return
	 */
	public static int[] getRowKinds() {
		int[] kinds = new int[mRowKindNames.size()];
		int pos = 0;
		for(Entry<Integer,String> e: mRowKindNames.entrySet()) {
			kinds[pos++] = e.getKey();
		}
		return kinds;
	}

	/**
	 * Return a list of BooklistGroups, one for each defined row kind
	 * 
	 * @return
	 */
	public static ArrayList<BooklistGroup> getAllGroups() {
		ArrayList<BooklistGroup> list = new ArrayList<BooklistGroup>();

		for(Entry<Integer, String> e : mRowKindNames.entrySet()) {
			final int kind = e.getKey();
			if (kind != ROW_KIND_BOOK)
				list.add(newGroup(kind));
		}
		return list;
	}
	
	/**
	 * Create a new BooklistGroup of the specified kind, creating any more specific subclasses as necessary.
	 * 
	 * @param kind		Kind of group to create
	 * 
	 * @return
	 */
	public static BooklistGroup newGroup(int kind) {
		BooklistGroup g;
		switch(kind) {
		case ROW_KIND_AUTHOR:
			g = new BooklistGroup.BooklistAuthorGroup();
			break;
		case ROW_KIND_SERIES:
			g = new BooklistGroup.BooklistSeriesGroup();
			break;
		default:
			g = new BooklistGroup(kind);
			break;			
		}
		return g;
	}
	
	/**
	 * Specialized BooklistGroup representing an Author group. Includes extra attributes based
	 * on preferences.
	 * 
	 * @author Philip Warner
	 */
	public static class BooklistSeriesGroup extends BooklistGroup /* implements Parcelable */ {
		private static final long serialVersionUID = 9023218506278704155L;
		/** Show book under each series it appears in? */
		public transient BooleanListProperty mAllSeries;

		/** mAllSeries Parameter values and descriptions */
		private static ItemEntries<Boolean> mAllSeriesItems = new ItemEntries<Boolean>();
		static {
			String kind = BookCatalogueApp.getResourceString(R.string.series);
			mAllSeriesItems.add(null, R.string.use_default_setting);
			mAllSeriesItems.add(false, BookCatalogueApp.getResourceString(R.string.show_book_under_primary_thing, kind));
			mAllSeriesItems.add(true, BookCatalogueApp.getResourceString(R.string.show_book_under_each_thing, kind));
		}

		private void initProperties() {
			mAllSeries = new BooleanListProperty(mAllSeriesItems, "AllSeries", PropertyGroup.GRP_SERIES, R.string.books_in_multiple_series, null, BookCataloguePreferences.PREF_SHOW_ALL_SERIES, false);
			mAllSeries.setHint(R.string.hint_series_book_may_appear_more_than_once);
		}

		/**
		 * Custom serialization support.
		 */
		private void writeObject(ObjectOutputStream out) throws IOException {
			out.defaultWriteObject();
			// We use read/write Object so that NULL values are preserved
			out.writeObject(mAllSeries.get());
		}

		/**
		 * Pseudo-constructor for custom serialization support.
		 * We need to set the name resource ID for the properties since these may change across versions.
		 */
		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
			in.defaultReadObject();
			initProperties();
			// We use read/write Object so that NULL values are preserved
			mAllSeries.set((Boolean)in.readObject());
		}

		BooklistSeriesGroup() { 
			super(ROW_KIND_SERIES);
			initProperties();
			mAllSeries.set((Boolean)null);
		}

		public boolean getAllSeries() {
			return mAllSeries.getResolvedValue();
		}

		@Override
		public void getStyleProperties(Properties list) {
			super.getStyleProperties(list);
			list.add(mAllSeries);
		}
	}

	/**
	 * Specialized BooklistGroup representing an Series group. Includes extra attributes based
	 * on preferences.
	 * 
	 * @author Philip Warner
	 */
	public static class BooklistAuthorGroup extends BooklistGroup {
		private static final long serialVersionUID = -1984868877792780113L;

		/** Support for 'Show Given Name' property */
		public transient BooleanListProperty mGivenName;
		private static ItemEntries<Boolean> mGivenNameFirstItems = new ItemEntries<Boolean>();
		static {
			mGivenNameFirstItems.add(null, R.string.use_default_setting);
			mGivenNameFirstItems.add(false, R.string.family_name_first_eg);
			mGivenNameFirstItems.add(true, R.string.given_name_first_eg);
		}

		/** Support for 'Show All Authors of Book' property */
		public transient BooleanListProperty mAllAuthors;		
		private static ItemEntries<Boolean> mAllAuthorsItems = new ItemEntries<Boolean>();
		static {
			String kind = BookCatalogueApp.getResourceString(R.string.author);
			mAllAuthorsItems.add(null, R.string.use_default_setting);
			mAllAuthorsItems.add(false, BookCatalogueApp.getResourceString(R.string.show_book_under_primary_thing, kind));
			mAllAuthorsItems.add(true, BookCatalogueApp.getResourceString(R.string.show_book_under_each_thing, kind));
		}

		/**
		 * Create the properties objects; these are transient, so not created by deserialization, and need to 
		 * be created in constructors as well.
		 */
		private void initProperties() {
			mAllAuthors = new BooleanListProperty(mAllAuthorsItems, "AllAuthors", PropertyGroup.GRP_AUTHOR, R.string.books_with_multiple_authors, BookCataloguePreferences.PREF_SHOW_ALL_AUTHORS);
			mAllAuthors.setHint(R.string.hint_authors_book_may_appear_more_than_once);
			mGivenName = new BooleanListProperty(mGivenNameFirstItems, "GivenName", PropertyGroup.GRP_AUTHOR, R.string.format_of_author_names, BookCataloguePreferences.PREF_DISPLAY_FIRST_THEN_LAST_NAMES);
		}

		/**
		 * Custom serialization support.
		 */
		private void writeObject(ObjectOutputStream out) throws IOException {
			out.defaultWriteObject();
			// We use read/write Object so that NULL values are preserved
			out.writeObject(mAllAuthors.get());
			out.writeObject(mGivenName.get());
		}

		/**
		 * Pseudo-constructor for custom serialization support.
		 * We need to set the name resource ID for the properties since these may change across versions.
		 */
		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
			in.defaultReadObject();
			initProperties();
			// We use read/write Object so that NULL values are preserved
			mAllAuthors.set((Boolean)in.readObject());
			mGivenName.set((Boolean)in.readObject());
		}

		BooklistAuthorGroup() { 
			super(ROW_KIND_AUTHOR); 
			initProperties();
			mAllAuthors.set((Boolean)null);
			mGivenName.set((Boolean)null);
		}

		/**
		 * Accessor
		 * 
		 * @return
		 */
		public boolean getAllAuthors() {
			return mAllAuthors.getResolvedValue();
		}

		/**
		 * Accessor
		 * 
		 * @return
		 */
		public void setAllAuthors(Boolean allAuthors) {
			mAllAuthors.set(allAuthors);
		}

		/**
		 * Accessor
		 * 
		 * @return
		 */
		public boolean getGivenName() {
			return mGivenName.getResolvedValue();
		}
		/**
		 * Accessor
		 * 
		 * @return
		 */
		public void setGivenName(Boolean giveName) {
			mGivenName.set(giveName);
		}

		/**
		 * Get the Property objects that this group will contribute to a Style.
		 */
		@Override
		public void getStyleProperties(Properties list) {
			super.getStyleProperties(list);

			list.add(mAllAuthors);
			list.add(mGivenName);
		}		

	}
	
	/** The Row Kind of this group */
	public int kind;

	/** The domains represented by this group. Set at runtime by builder based on current group and outer groups */
	public transient ArrayList<DomainDefinition> groupDomains;
	/** The domain used to display this group. Set at runtime by builder based on internal logic of builder */
	public transient DomainDefinition displayDomain;
	/** Compound key of this group. Set at runtime by builder based on current group and outer groups */
	private transient CompoundKey mCompoundKey;

	BooklistGroup(int kind) {
		this.kind = kind;
	}
	
	/** Setter for compound key */
	public void setKeyComponents(String prefix, DomainDefinition...domains) {
		mCompoundKey = new CompoundKey(prefix, domains);
	}
	
	/** Getter for compound key */
	public CompoundKey getCompoundKey() {
		return mCompoundKey;
	}

	public String getName() {
		return mRowKindNames.get(kind);
	}
	
	public void getStyleProperties(Properties list) {
	}

	/**
	 * Custom serialization support.
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
	}

	/**
	 * Pseudo-constructor for custom serialization support.
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}
}

