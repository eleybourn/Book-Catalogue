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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.database.SerializationUtils;
import com.eleybourn.bookcatalogue.database.SerializationUtils.DeserializationException;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.StringProperty;

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
 * @author Philip Warner
 */
public class BooklistStyle implements Iterable<BooklistGroup>, Serializable {
	private static final long serialVersionUID = 6615877148246388549L;
	private static final long realSerialVersion = 2;

	/** Extra book data to show at lowest level */
	public static final int EXTRAS_BOOKSHELVES = 1;
	/** Extra book data to show at lowest level */
	public static final int EXTRAS_LOCATION = 2;
	/** Extra book data to show at lowest level */
	public static final int EXTRAS_PUBLISHER = 4;
	/** Extra book data to show at lowest level */
	public static final int EXTRAS_AUTHOR = 8;
	/** Extra book data to show at lowest level */
	public static final int EXTRAS_THUMBNAIL = 16;
	/** Extra book data to show at lowest level */
	public static final int EXTRAS_THUMBNAIL_LARGE = 32;
	/** Extra book data to show at lowest level */
	public static final int EXTRAS_ALL = EXTRAS_BOOKSHELVES|EXTRAS_LOCATION|EXTRAS_PUBLISHER|EXTRAS_AUTHOR|EXTRAS_THUMBNAIL|EXTRAS_THUMBNAIL_LARGE;

	public static final String SFX_SHOW_BOOKSHELVES = "ShowBookshelves";
	public static final String SFX_SHOW_LOCATION = "ShowLocation";
	public static final String SFX_SHOW_PUBLISHER = "ShowPublisher";
	public static final String SFX_SHOW_AUTHOR = "ShowAuthor";
	public static final String SFX_SHOW_THUMBNAILS = "ShowThumbnails";
	public static final String SFX_LARGE_THUMBNAILS = "LargeThumbnails";
	public static final String SFX_CONDENSED = "Condensed";

	/** Prefix for all prefs */
	public static final String TAG = "BookList";

	/** Show list of bookshelves for each book */
	public static final String PREF_SHOW_EXTRAS_PREFIX = TAG + ".";

	/** Show list of bookshelves for each book */
	public static final String PREF_CONDENSED_TEXT = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_CONDENSED;	
	/** Show list of bookshelves for each book */
	public static final String PREF_SHOW_BOOKSHELVES = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_BOOKSHELVES;
	/** Show location for each book */
	public static final String PREF_SHOW_LOCATION = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_LOCATION;
	/** Show author for each book */
	public static final String PREF_SHOW_AUTHOR = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_AUTHOR;
	/** Show publisher for each book */
	public static final String PREF_SHOW_PUBLISHER = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_PUBLISHER;
	/** Show thumbnail image for each book */
	public static final String PREF_SHOW_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_THUMBNAILS;
	/** Show large thumbnail if thumbnails are shown */
	public static final String PREF_LARGE_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_LARGE_THUMBNAILS;

	/** ID if string representing name of this style. Used for standard system-defined styles */
	private int mNameStringId;

	/** User-defined name of this style. Used for user-defined styles */
	private String mName; // TODO: Legacy field designed forbackward serialization compatibility
	private transient StringProperty mNameProperty;

	/** Extra fields to show at the book level */
	//private int mExtras = 0;
	/** List of groups */
	private final ArrayList<BooklistGroup> mGroups;
	/** Row id of database row from which this object comes */
	private long mRowId = 0;
	/** Extra details to show on book rows */
	private transient BooleanProperty mXtraShowThumbnails;
	/** Extra details to show on book rows */
	private transient BooleanProperty mXtraLargeThumbnails;
	/** Extra details to show on book rows */
	private transient BooleanProperty mXtraShowBookshelves;
	/** Extra details to show on book rows */
	private transient BooleanProperty mXtraShowLocation;
	/** Extra details to show on book rows */
	private transient BooleanProperty mXtraShowPublisher;
	/** Extra details to show on book rows */
	private transient BooleanProperty mXtraShowAuthor;
	/** Extra details to show on book rows */
	private transient IntegerListProperty mXtraReadUnreadAll; 
	/** Show list using smaller text */
	private transient BooleanListProperty mCondensed; 
			
	/**
	 * Flag indicating this style was in the 'preferred' set when it was added to its Styles collection 
	 * The value is not dynamically checked. 
	 */
	private boolean mIsPreferred;
	
	/** 
	 * Represents a collection of domains that make a unique key for a given group.
	 * 
	 * @author Philip Warner
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

	public static final int FILTER_READ = 1;
	public static final int FILTER_UNREAD = 2;
	public static final int FILTER_READ_AND_UNREAD = 3;

	// ENHANCE: Add filters based on 'loaned', 'anthology' and (maybe) duplicate books
	
	/** Support for 'READ' filter */
	private static ItemEntries<Integer> mReadFilterListItems = new ItemEntries<Integer>();
	static {
		mReadFilterListItems.add(FILTER_UNREAD, R.string.select_unread_only);
		mReadFilterListItems.add(FILTER_READ, R.string.select_read_only);
		mReadFilterListItems.add(FILTER_READ_AND_UNREAD, R.string.all_books);
	}

	/** Support for 'Condensed' property */
	private static ItemEntries<Boolean> mCondensedListItems = new ItemEntries<Boolean>();
	static {
		mCondensedListItems.add(null, R.string.use_default_setting);
		mCondensedListItems.add(false, R.string.normal);
		mCondensedListItems.add(true, R.string.smaller);
	}

	/**
	 * Constructor for system-defined styles.
	 * 
	 * @param stringId
	 */
	BooklistStyle(int stringId) {
		mNameStringId = stringId;
		mGroups = new ArrayList<BooklistGroup>();
		initProperties();
		mNameProperty.set((String)null);
	}

	/**
	 * Constructor for user-defined styles.
	 * 
	 * @param name
	 */
	BooklistStyle(String name) {
		initProperties();
		mNameStringId = 0;
		mGroups = new ArrayList<BooklistGroup>();
		mNameProperty.set(name);
	}

	public int getReadFilter() {
		return mXtraReadUnreadAll.getResolvedValue();
	}

	/**
	 * Accessor for flag indicating style is among preferred styles.
	 * 
	 * @return
	 */
	public boolean isPreferred() {
		return mIsPreferred;
	}
	/**
	 * Accessor for flag indicating style is among preferred styles.
	 * 
	 * @return
	 */
	public void setPreferred(boolean isPreferred) {
		mIsPreferred = isPreferred;
	}
	
	/**
	 * Accessor. Returns system name or user-defined name based on kind of style this object defines.
	 *
	 * @return
	 */
	public String getDisplayName() {
		String s = mNameProperty.getResolvedValue();
		if (!s.equals(""))
			return s;
		else
			return BookCatalogueApp.getResourceString(mNameStringId);
	}
	/**
	 * Accessor. Sets user-defined name.
	 *
	 * @return
	 */
	public void setName(String name) {
		mNameProperty.set(name);
		mNameStringId = 0;
	}

	/**
	 * Accessor. Returns a standarised form of the style name.
	 *
	 * @return
	 */
	public String getCanonicalName() {
		if (isUserDefined())
			return getRowId() + "-u";
		else {
			String name = getDisplayName().trim().toLowerCase();
			return name + "-s";
		}
	}

	public void addGroup(BooklistGroup group) {
		mGroups.add(group);
	}

	/**
	 * Add a group to this style below any already added groups.
	 * 
	 * @param kind		Kind of group to add.
	 * 
	 * @return 	Newly created group.
	 */
	public BooklistGroup addGroup(int kind) {
		BooklistGroup g = BooklistGroup.newGroup(kind);
		addGroup(g);
		return g;
	}

	/**
	 * Remove a group from this style.
	 * 
	 * @param kind		Kind of group to add.
	 * 
	 * @return 	Newly created group.
	 */
	public BooklistGroup removeGroup(int kind) {
		BooklistGroup toRemove = null;
		for(BooklistGroup g: mGroups) {
			if (g.kind == kind) {
				toRemove = g;
				break;
			}
		}
		if (toRemove != null)
			mGroups.remove(toRemove);

		return toRemove;
	}


	/**
	 * Returns true if this style is user-defined.
	 *
	 * @return
	 */
	// RELEASE Verify isUserDefined() is correct
	public boolean isUserDefined() {
		return (mNameStringId == 0 || mRowId != 0);
	}

	private void initProperties() {
		mXtraShowThumbnails = new BooleanProperty("XThumbnails", PropertyGroup.GRP_THUMBNAILS, R.string.show_thumbnails, 
										PREF_SHOW_THUMBNAILS, true);
		mXtraShowThumbnails.setWeight(-100);		
		mXtraLargeThumbnails = new BooleanProperty("XLargeThumbnails", PropertyGroup.GRP_THUMBNAILS, R.string.prefer_large_thumbnails, 
										PREF_LARGE_THUMBNAILS, false);
		mXtraLargeThumbnails.setWeight(-99);		

		mXtraShowBookshelves = new BooleanProperty("XBookshelves", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.bookshelves, 
										PREF_SHOW_BOOKSHELVES, false);
		mXtraShowLocation = new BooleanProperty("XLocation", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.location, 
										PREF_SHOW_LOCATION, false);
		mXtraShowPublisher = new BooleanProperty("XPublisher", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.publisher, 
										PREF_SHOW_PUBLISHER, false);
		mXtraShowAuthor = new BooleanProperty("XAuthor", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.author,
										PREF_SHOW_AUTHOR, false);
		mXtraReadUnreadAll = new IntegerListProperty(mReadFilterListItems, "XReadUnreadAll", PropertyGroup.GRP_EXTRA_FILTERS, R.string.select_based_on_read_status, FILTER_READ_AND_UNREAD);

		mNameProperty = new StringProperty("StyleName", PropertyGroup.GRP_GENERAL, R.string.name);
		mNameProperty.setRequireNonBlank(true);
		// Put it at top of its group
		mNameProperty.setWeight(-100);

		mCondensed = new BooleanListProperty(mCondensedListItems, PREF_CONDENSED_TEXT, PropertyGroup.GRP_GENERAL, R.string.size_of_booklist_items,
										null, PREF_CONDENSED_TEXT, false);
	}

	/**
	 * Get all of the properties of this Style and its groups.
	 */
	public Properties getProperties() {
		Properties props = new Properties();

		props.add(mXtraShowThumbnails);
		props.add(mXtraLargeThumbnails);
		props.add(mXtraShowBookshelves);
		props.add(mXtraShowLocation);
		props.add(mXtraShowPublisher);
		props.add(mXtraShowAuthor);
		props.add(mXtraReadUnreadAll);
		props.add(mCondensed);
		props.add(mNameProperty);

		for(BooklistGroup g: mGroups) {
			g.getStyleProperties(props);
		}

		return props;
	}

	/**
	 * Passed a Properties object, update the properties of this style
	 * based on the values of the passed properties.
	 */
	public void setProperties(Properties newProps) {
		Properties props = getProperties();
		for(Property newVal: newProps) {
			Property thisProp = props.get(newVal.getUniqueName());
			if (thisProp != null) {
				thisProp.set(newVal);
			}
		}
	}

	/**
	 * Passed a template style, copy the group structure to this style.
	 */
	public void setGroups(BooklistStyle fromStyle) {
		Properties newProps = new Properties();

		// Save the current groups
		Hashtable<Integer, BooklistGroup> oldGroups = new Hashtable<Integer, BooklistGroup>();
		for(BooklistGroup g: this) {
			oldGroups.put(g.kind, g);
		}
		// Clear the current groups, and rebuild, reusing old values where possible
		mGroups.clear();
		for(BooklistGroup g: fromStyle) {
			BooklistGroup saved = oldGroups.get(g.kind);
			if (saved != null) {
				this.addGroup(saved);
			} else {
				g.getStyleProperties(newProps);
				this.addGroup(g.kind);
			}
		}
		// Copy any properties from new groups.
		this.setProperties(newProps);
	}

	/**
	 * Accessor.
	 */
	public int getExtras() {
		int extras = 0;

		if ( mXtraShowThumbnails.getResolvedValue() )
			extras |= EXTRAS_THUMBNAIL;

		if ( mXtraLargeThumbnails.getResolvedValue() )
			extras |= EXTRAS_THUMBNAIL_LARGE;
			
		if ( mXtraShowBookshelves.getResolvedValue() )
			extras |= EXTRAS_BOOKSHELVES;

		if ( mXtraShowLocation.getResolvedValue() )
			extras |= EXTRAS_LOCATION;
			
		if ( mXtraShowPublisher.getResolvedValue() )
			extras |= EXTRAS_PUBLISHER;
			
		if ( mXtraShowAuthor.getResolvedValue() )
			extras |= EXTRAS_AUTHOR;

		return extras;
	}

	/**
	 * Check if ths style has the specified group
	 */
	public boolean hasKind(int kind) {
		for (BooklistGroup g : mGroups) {
			if (g.kind == kind)
				return true;
		}
		return false;
	}
	
	/**
	 * Get the group at the passed index.
	 */
	public BooklistGroup getGroupAt(int index) {
		return mGroups.get(index);
	}

	/**
	 * Get the number of groups in this style
	 */
	public int size() {
		return mGroups.size();
	}
	
	/**
	 * Accessor for underlying database row id, if this object is from a database. 0 if not from database.
	 * 
	 * @return
	 */
	public long getRowId() {
		return mRowId;
	}
	
	/**
	 * Accessor for underlying database row id, set by query that retrieves the object.
	 * 
	 * @return
	 */
	public void setRowId(long rowId) {
		mRowId = rowId;
	}

	/**
     * Iterable support
     */
	@Override
	public Iterator<BooklistGroup> iterator() {
		return mGroups.iterator();
	}

	/**
	 * Custom serialization support.
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		out.writeObject((Long)realSerialVersion);
		out.writeObject(mXtraShowThumbnails.get());			
		out.writeObject(mXtraLargeThumbnails.get());
		out.writeObject(mXtraShowBookshelves.get());
		out.writeObject(mXtraShowLocation.get());
		out.writeObject(mXtraShowPublisher.get());
		out.writeObject(mXtraShowAuthor.get());
		out.writeObject(mXtraReadUnreadAll.get());
		out.writeObject(mCondensed.get());
		out.writeObject(mNameProperty.get());
	}

	/**
	 * Pseudo-constructor for custom serialization support.
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		initProperties();
		Object o = in.readObject();
		long version = 0;
		if (o instanceof Long) {
			// Its the version
			version = ((Long)o);
			// Get the next object
			o = in.readObject();
		} else {
			// Its a pre-version object...just use it
		}
		mXtraShowThumbnails.set((Boolean)o);
		mXtraLargeThumbnails.set((Boolean)in.readObject());
		mXtraShowBookshelves.set((Boolean)in.readObject());
		mXtraShowLocation.set((Boolean)in.readObject());
		mXtraShowPublisher.set((Boolean)in.readObject());
		mXtraShowAuthor.set((Boolean)in.readObject());
		mXtraReadUnreadAll.set((Integer)in.readObject());
		if (version > 0)
			mCondensed.set((Boolean)in.readObject());
		if (version > 1)
			mNameProperty.set((String)in.readObject());	
		else
			mNameProperty.set(mName);
	}
	
	/**
	 * Accessor
	 */
	public boolean isCondensed() {
		return mCondensed.getResolvedValue();
	}
	public void setCondensed(boolean condensed) {
		mCondensed.set(condensed);
	}
	/**
	 * Accessor
	 */
	public boolean showThumbnails() {
		return mXtraShowThumbnails.getResolvedValue();
	}
	public void setShowThumbnails(boolean show) {
		mXtraShowThumbnails.set(show);
	}
	/**
	 * Accessor
	 */
	public Integer getReadUnreadAll() {
		return mXtraReadUnreadAll.getResolvedValue();
	}
	public void setReadUnreadAll(Integer readUnreadAll) {
		mXtraReadUnreadAll.set(readUnreadAll);
	}

	/**
	 * Save this style as a custom user style to the database.
	 * Either updates or creates as necessary
	 */
	public void saveToDb(CatalogueDBAdapter db) {
		if (getRowId() == 0)
			mRowId = db.insertBooklistStyle(this);
		else
			db.updateBooklistStyle(this);
	}

	/**
	 * Delete this style from the database
	 */
	public void deleteFromDb(CatalogueDBAdapter db) {
		if (getRowId() == 0)
			throw new RuntimeException("Style is not stored in the database, can not be deleted");
		db.deleteBooklistStyle(this.getRowId());
	}

	/**
	 * Convenience function to return a list of group names.
	 */
	public String getGroupListDisplayNames() {
		StringBuilder groups = new StringBuilder();
		boolean first = true;
		for(BooklistGroup g: this) {
			if (first) 
				first = false; 
			else
				groups.append(" / ");
			groups.append(g.getName());
		}
		return groups.toString();
	}

	/**
	 * Construct a deep clone of this object.
	 */
	public BooklistStyle getClone() throws DeserializationException {
		BooklistStyle newStyle = SerializationUtils.cloneObject(this);				
		return newStyle;
	}
}

