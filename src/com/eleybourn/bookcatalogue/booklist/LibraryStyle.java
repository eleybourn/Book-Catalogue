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

import androidx.annotation.NonNull;

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
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.database.SerializationUtils;
import com.eleybourn.bookcatalogue.database.SerializationUtils.DeserializationException;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.StringProperty;

/**
 * Represents a specific style of book list (eg. authors/series). Individual LibraryGroup objects
 * are added to a style in order to describe the resulting list style.
 * ENHANCE: Allow for style-based overrides of things currently stored in preferences
 * 			This should include thumbnail presence/size, book-in-each-series etc. as well as font sizes.
 * How to add a new Group:
 * - add it to RowKinds. Update ROW_KIND_MAX.
 * - add new domain to DatabaseDefinitions (if necessary)
 * - modify LibraryBuilder.build() to add the necessary grouped/sorted domains
 * - modify LibraryMultitypeHandler; if it is just a string field, then use a GenericHolder. Otherwise add a new holder.
 *   Need to at least modify LibraryMultitypeHandler.newHolder().
 * 
 * @author Philip Warner
 */
public class LibraryStyle implements Iterable<LibraryGroup>, Serializable {
	private static final long serialVersionUID = 6615877148246388549L;
	private static final long realSerialVersion = 4;

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

    public static final String SFX_SHOW_BOOKSHELVES = "ShowBookshelves";
	public static final String SFX_SHOW_LOCATION = "ShowLocation";
	public static final String SFX_SHOW_PUBLISHER = "ShowPublisher";
	public static final String SFX_SHOW_AUTHOR = "ShowAuthor";
	public static final String SFX_SHOW_THUMBNAILS = "ShowThumbnails";
	public static final String SFX_LARGE_THUMBNAILS = "LargeThumbnails";
	public static final String SFX_CONDENSED = "Condensed";
	public static final String SFX_SHOW_HEADER_INFO = "ShowHeaderInfo";

	/** Prefix for all prefs */
	public static final String TAG = "BookList";

	/** Show list of bookshelves for each book */
	public static final String PREF_SHOW_EXTRAS_PREFIX = TAG + ".";

	/** Show header info in list */
	public static final String PREF_SHOW_HEADER_INFO = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_SHOW_HEADER_INFO;
	/** Show list of bookshelves for each book */
	public static final String PREF_CONDENSED_TEXT = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_CONDENSED;
	/** Show list of bookshelves for each book */
	public static final String PREF_SHOW_BOOKSHELVES = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_SHOW_BOOKSHELVES;
	/** Show location for each book */
	public static final String PREF_SHOW_LOCATION = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_SHOW_LOCATION;
	/** Show author for each book */
	public static final String PREF_SHOW_AUTHOR = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_SHOW_AUTHOR;
	/** Show publisher for each book */
	public static final String PREF_SHOW_PUBLISHER = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_SHOW_PUBLISHER;
	/** Show thumbnail image for each book */
	public static final String PREF_SHOW_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_SHOW_THUMBNAILS;
	/** Show large thumbnail if thumbnails are shown */
	public static final String PREF_LARGE_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + LibraryStyle.SFX_LARGE_THUMBNAILS;

	/** ID if string representing name of this style. Used for standard system-defined styles */
	private int mNameStringId;

	/** User-defined name of this style. Used for user-defined styles */
	@SuppressWarnings("unused")
    private String mName; // TODO: Legacy field designed for backward serialization compatibility
	private transient StringProperty mNameProperty;

	// List of groups
	private final ArrayList<LibraryGroup> mGroups;
	// Row id of database row from which this object comes
	private long mRowId = 0;
	// Extra details to show on book rows
	private transient BooleanProperty mExtraShowThumbnails;
	// Extra details to show on book rows
	private transient BooleanProperty mExtraLargeThumbnails;
	// Extra details to show on book rows
	private transient BooleanProperty mExtraShowBookshelves;
	// Extra details to show on book rows
	private transient BooleanProperty mExtraShowLocation;
	// Extra details to show on book rows
	private transient BooleanProperty mExtraShowPublisher;
	// Extra details to show on book rows
	private transient BooleanProperty mExtraShowAuthor;
	// Extra details to show on book rows
	private transient IntegerListProperty mExtraReadUnreadAll;
	// Show list using smaller text
	private transient BooleanListProperty mCondensed; 
	// Show list header info
	private transient IntegerListProperty mShowHeaderInfo; 
			
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
        final String prefix;
		/** List of domains in key */
        final DomainDefinition[] domains;
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
	private static final ItemEntries<Integer> mReadFilterListItems = new ItemEntries<>();
	static {
		mReadFilterListItems.add(FILTER_UNREAD, R.string.option_select_unread_only);
		mReadFilterListItems.add(FILTER_READ, R.string.option_select_read_only);
		mReadFilterListItems.add(FILTER_READ_AND_UNREAD, R.string.option_all_books);
	}

	/** Support for 'Condensed' property */
	private static final ItemEntries<Boolean> mCondensedListItems = new ItemEntries<>();
	static {
		mCondensedListItems.add(null, R.string.option_use_default_setting);
		mCondensedListItems.add(false, R.string.option_normal);
		mCondensedListItems.add(true, R.string.option_smaller);
	}

	public static final Integer SUMMARY_HIDE = 0;
	public static final Integer SUMMARY_SHOW_COUNT = 1;
	public static final Integer SUMMARY_SHOW_LEVEL_1 = 2;
	public static final Integer SUMMARY_SHOW_LEVEL_2 = 4;
	public static final Integer SUMMARY_SHOW_LEVEL_1_AND_COUNT = SUMMARY_SHOW_COUNT ^ SUMMARY_SHOW_LEVEL_1;
	public static final Integer SUMMARY_SHOW_ALL = 0xff;
	
	/** Support for 'Show List Header Info' property */
	private static final ItemEntries<Integer> mShowHeaderInfoListItems = new ItemEntries<>();
	static {
		mShowHeaderInfoListItems.add(null, R.string.option_use_default_setting);
		mShowHeaderInfoListItems.add(SUMMARY_HIDE, R.string.option_hide_summary_details);
		mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT, R.string.option_show_book_count);
		mShowHeaderInfoListItems.add(SUMMARY_SHOW_LEVEL_1_AND_COUNT, R.string.option_show_first_level_and_book_count);
		mShowHeaderInfoListItems.add(SUMMARY_SHOW_ALL, R.string.option_show_all_summary_details);
	}

	/**
	 * Constructor for system-defined styles.
	 */
	LibraryStyle(int stringId) {
		mNameStringId = stringId;
		mGroups = new ArrayList<>();
		initProperties();
		mNameProperty.set((String)null);
	}

	/**
	 * Constructor for user-defined styles.
	 */
	LibraryStyle(String name) {
		initProperties();
		mNameStringId = 0;
		mGroups = new ArrayList<>();
		mNameProperty.set(name);
	}

	public int getReadFilter() {
		return mExtraReadUnreadAll.getResolvedValue();
	}

	/**
	 * Accessor for flag indicating style is among preferred styles.
	 */
	public boolean isPreferred() {
		return mIsPreferred;
	}
	/**
	 * Accessor for flag indicating style is among preferred styles.
	 */
	public void setPreferred(boolean isPreferred) {
		mIsPreferred = isPreferred;
	}
	
	/**
	 * Accessor. Returns system name or user-defined name based on kind of style this object defines.
	 */
	public String getDisplayName() {
		String s = mNameProperty.getResolvedValue();
		if (!s.isEmpty())
			return s;
		else
			return BookCatalogueApp.getResourceString(mNameStringId);
	}
	/**
	 * Accessor. Sets user-defined name.
	 */
	public void setName(String name) {
		mNameProperty.set(name);
		mNameStringId = 0;
	}

	/**
	 * Accessor. Returns a standardised form of the style name. This name is unique.
	 */
	public String getCanonicalName() {
		if (isUserDefined())
			return getRowId() + "-u";
		else {
			String name = getDisplayName().trim().toLowerCase();
			return name + "-s";
		}
	}

	public void addGroup(LibraryGroup group) {
		mGroups.add(group);
	}

	/**
     * Add a group to this style below any already added groups.
     *
     * @param kind Kind of group to add.
     */
	public void addGroup(int kind) {
		LibraryGroup g = LibraryGroup.newGroup(kind);
		addGroup(g);
    }

	/**
     * Remove a group from this style.
     *
     * @param kind Kind of group to add.
     */
	public void removeGroup(int kind) {
		LibraryGroup toRemove = null;
		for(LibraryGroup g: mGroups) {
			if (g.kind == kind) {
				toRemove = g;
				break;
			}
		}
		if (toRemove != null)
			mGroups.remove(toRemove);

    }


	/**
	 * Returns true if this style is user-defined.
	 */
	public boolean isUserDefined() {
		return (mNameStringId == 0 || mRowId != 0);
	}

	private void initProperties() {
		mExtraShowThumbnails = new BooleanProperty("XThumbnails", PropertyGroup.GRP_THUMBNAILS, R.string.preference_show_thumbnails,
										PREF_SHOW_THUMBNAILS, true);
		mExtraShowThumbnails.setWeight(-100);
		mExtraLargeThumbnails = new BooleanProperty("XLargeThumbnails", PropertyGroup.GRP_THUMBNAILS, R.string.preference_prefer_large_thumbnails,
										PREF_LARGE_THUMBNAILS, false);
		mExtraLargeThumbnails.setWeight(-99);

		mExtraShowBookshelves = new BooleanProperty("XBookshelves", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.label_bookshelves,
										PREF_SHOW_BOOKSHELVES, false);
		mExtraShowLocation = new BooleanProperty("XLocation", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.label_location,
										PREF_SHOW_LOCATION, false);
		mExtraShowPublisher = new BooleanProperty("XPublisher", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.label_publisher,
										PREF_SHOW_PUBLISHER, false);
		mExtraShowAuthor = new BooleanProperty("XAuthor", PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.label_author,
										PREF_SHOW_AUTHOR, false);
		mExtraReadUnreadAll = new IntegerListProperty(mReadFilterListItems, "XReadUnreadAll", PropertyGroup.GRP_EXTRA_FILTERS, R.string.select_based_on_read_status, FILTER_READ_AND_UNREAD);

		mNameProperty = new StringProperty("StyleName", PropertyGroup.GRP_GENERAL, R.string.option_name);
		mNameProperty.setRequireNonBlank(true);
		// Put it at top of its group
		mNameProperty.setWeight(-100);

		mCondensed = new BooleanListProperty(mCondensedListItems, PREF_CONDENSED_TEXT, PropertyGroup.GRP_GENERAL, R.string.preference_size_of_library_items,
				null, PREF_CONDENSED_TEXT, false);
		mShowHeaderInfo = new IntegerListProperty(mShowHeaderInfoListItems, PREF_SHOW_HEADER_INFO, PropertyGroup.GRP_GENERAL, R.string.preference_summary_details_in_header,
				null, PREF_SHOW_HEADER_INFO, SUMMARY_SHOW_ALL);
	}

	/**
	 * Get all of the properties of this Style and its groups.
	 */
	public Properties getProperties() {
		Properties props = new Properties();

		props.add(mExtraShowThumbnails);
		props.add(mExtraLargeThumbnails);
		props.add(mExtraShowBookshelves);
		props.add(mExtraShowLocation);
		props.add(mExtraShowPublisher);
		props.add(mExtraShowAuthor);
		props.add(mExtraReadUnreadAll);
		props.add(mCondensed);
		props.add(mNameProperty);
		props.add(mShowHeaderInfo);

		for(LibraryGroup g: mGroups) {
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
	public void setGroups(LibraryStyle fromStyle) {
		Properties newProps = new Properties();

		// Save the current groups
		Hashtable<Integer, LibraryGroup> oldGroups = new Hashtable<>();
		for(LibraryGroup g: this) {
			oldGroups.put(g.kind, g);
		}
		// Clear the current groups, and rebuild, reusing old values where possible
		mGroups.clear();
		for(LibraryGroup g: fromStyle) {
			LibraryGroup saved = oldGroups.get(g.kind);
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

		if ( mExtraShowThumbnails.getResolvedValue() )
			extras |= EXTRAS_THUMBNAIL;

		if ( mExtraLargeThumbnails.getResolvedValue() )
			extras |= EXTRAS_THUMBNAIL_LARGE;
			
		if ( mExtraShowBookshelves.getResolvedValue() )
			extras |= EXTRAS_BOOKSHELVES;

		if ( mExtraShowLocation.getResolvedValue() )
			extras |= EXTRAS_LOCATION;
			
		if ( mExtraShowPublisher.getResolvedValue() )
			extras |= EXTRAS_PUBLISHER;
			
		if ( mExtraShowAuthor.getResolvedValue() )
			extras |= EXTRAS_AUTHOR;

		return extras;
	}

	/**
	 * Check if ths style has the specified group
	 */
	public boolean hasKind(int kind) {
		for (LibraryGroup g : mGroups) {
			if (g.kind == kind)
				return true;
		}
		return false;
	}
	
	/**
	 * Get the group at the passed index.
	 */
	public LibraryGroup getGroupAt(int index) {
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
	 */
	public long getRowId() {
		return mRowId;
	}
	
	/**
	 * Accessor for underlying database row id, set by query that retrieves the object.
	 */
	public void setRowId(long rowId) {
		mRowId = rowId;
	}

	/**
     * Iterable support
     */
	@NonNull
    @Override
	public Iterator<LibraryGroup> iterator() {
		return mGroups.iterator();
	}

	/**
	 * Custom serialization support.
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		out.writeObject((Long)realSerialVersion);
		out.writeObject(mExtraShowThumbnails.get());
		out.writeObject(mExtraLargeThumbnails.get());
		out.writeObject(mExtraShowBookshelves.get());
		out.writeObject(mExtraShowLocation.get());
		out.writeObject(mExtraShowPublisher.get());
		out.writeObject(mExtraShowAuthor.get());
		out.writeObject(mExtraReadUnreadAll.get());
		out.writeObject(mCondensed.get());
		out.writeObject(mNameProperty.get());
		out.writeObject(mShowHeaderInfo.get());
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
		}
		mExtraShowThumbnails.set((Boolean)o);
		mExtraLargeThumbnails.set((Boolean)in.readObject());
		mExtraShowBookshelves.set((Boolean)in.readObject());
		mExtraShowLocation.set((Boolean)in.readObject());
		mExtraShowPublisher.set((Boolean)in.readObject());
		mExtraShowAuthor.set((Boolean)in.readObject());
		mExtraReadUnreadAll.set((Integer)in.readObject());
		if (version > 0)
			mCondensed.set((Boolean)in.readObject());
		if (version > 1)
			mNameProperty.set((String)in.readObject());	
		else
			mNameProperty.set(mName);
		// Added mShowHeaderInfo with version 3
		if (version > 2) {
			// Changed it from Boolean to Integer in version 4
			if (version == 3) {
				Boolean isSet = (Boolean)in.readObject();
				if (isSet == null) {
					mShowHeaderInfo.set( (Integer)null );
				} else {
					mShowHeaderInfo.set( isSet ? SUMMARY_SHOW_ALL : SUMMARY_HIDE);
				}
			} else {
				mShowHeaderInfo.set((Integer)in.readObject());
			}			
		}
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

    public void setShowThumbnails(boolean show) {
		mExtraShowThumbnails.set(show);
	}

    public void setReadUnreadAll(Integer readUnreadAll) {
		mExtraReadUnreadAll.set(readUnreadAll);
	}
	
	/**
	 * Accessor
	 */
	public int getShowHeaderInfo() {
		return mShowHeaderInfo.getResolvedValue();
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
		for(LibraryGroup g: this) {
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
	public LibraryStyle getClone() throws DeserializationException {
        return SerializationUtils.cloneObject(this);
	}

	/**
	 * Accessor to allow setting of Extras value directly.
	 */
	public void setShowAuthor(boolean show) {
		mExtraShowAuthor.set(show);
	}
}

