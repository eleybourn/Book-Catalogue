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

import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.database.SerializationUtils;
import com.eleybourn.bookcatalogue.database.SerializationUtils.DeserializationException;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Collection of system-defined and user-defined Book List styles.
 * 
 * @author Philip Warner
 */
public class BooklistStyles implements Iterable<BooklistStyle> {
	/** Internal storage for defined styles represented by this object */
	private ArrayList<BooklistStyle> mList = new ArrayList<BooklistStyle>();
	private HashSet<String> mPreferredStyleNames;
	private ArrayList<String> mPreferredStyleList;

	public static final String TAG = "BooklistStyles";
	public static final String PREF_MENU_PREFIX = TAG + ".Menu";
	public static final String PREF_MENU_ITEMS = TAG + ".Menu.Items";
	public static final String PREF_USER_STYLE_PREFIX = TAG + ".Styles.";
	public static final String PREF_USER_STYLE_COUNT = PREF_USER_STYLE_PREFIX + "Count";
	public static final String PREF_USER_STYLE_LAST_ID = PREF_USER_STYLE_PREFIX + "LastId";

	/**
	 * Constructor
	 */
	public BooklistStyles() {
		mPreferredStyleNames = new HashSet<String>();
		mPreferredStyleList = new ArrayList<String>();
		getPreferredStyleNames(mPreferredStyleNames, mPreferredStyleList);
	}

	/**
	 * Fill in the passed objects with the canonical names of the preferred styles
	 * from user preferences.
	 * 
	 * @param hash		Hashtable of names
	 * @param list
	 */
	private static void getPreferredStyleNames(HashSet<String> hash, ArrayList<String> list) {

		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		String itemStr = prefs.getString(PREF_MENU_ITEMS, null);
		if (itemStr != null && !itemStr.equals("")) {
			list = Utils.decodeList(itemStr, '|');
			for(int i = 0; i < list.size(); i++) {
				String name = list.get(i);
				if (name != null && !name.equals("") && !hash.contains(name))
					hash.add(name);
			}
		}

	}

	/**
	 * Static method to get all defined styles, including user-defined styles (the latter is 
	 * not supported yet).
	 * 
	 * NOTE: Do NOT call this in static initialization of application. This method requires the 
	 * 		 application context to be present.
	 * 
	 * @return		BooklistStyles object
	 */
	//public static final int BUILTIN_AUTHOR_SERIES = 1;
	private static void getBuiltinStyles(BooklistStyles styles) {
		// First build the stock ones
		BooklistStyle style;

		// Author/Series
		style = new BooklistStyle(R.string.sort_author_series);
		styles.add(style);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		//// Author(first)/Series
		//style = new BooklistStyle(R.string.sort_first_author_series);
		//styles.add(style);
		//a = new BooklistAuthorGroup();
		//a.setAllAuthors(false);
		//style.addGroup(a);
		//style.addGroup(ROW_KIND_SERIES);

		// Unread
		style = new BooklistStyle(R.string.sort_unread);
		styles.add(style);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);
		style.setReadUnreadAll(BooklistStyle.FILTER_UNREAD);

		// Compact
		style = new BooklistStyle(R.string.compact);
		styles.add(style);
		style.addGroup(ROW_KIND_AUTHOR);
		style.setCondensed(true);
		style.setShowThumbnails(false);

		// Title
		style = new BooklistStyle(R.string.sort_title_first_letter);
		styles.add(style);
		style.addGroup(RowKinds.ROW_KIND_TITLE_LETTER);

		// Series
		style = new BooklistStyle(R.string.sort_series);
		styles.add(style);
		style.addGroup(RowKinds.ROW_KIND_SERIES);

		// Genre
		style = new BooklistStyle(R.string.sort_genre);
		styles.add(style);
		style.addGroup(ROW_KIND_GENRE);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		// Loaned
		style = new BooklistStyle(R.string.sort_loaned);
		styles.add(style);
		style.addGroup(ROW_KIND_LOANED);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		// Read & Unread
		style = new BooklistStyle(R.string.sort_read_and_unread);
		styles.add(style);
		style.addGroup(ROW_KIND_READ_AND_UNREAD);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		// Publication date
		style = new BooklistStyle(R.string.sort_publication_date);
		styles.add(style);
		style.addGroup(ROW_KIND_YEAR_PUBLISHED);
		style.addGroup(ROW_KIND_MONTH_PUBLISHED);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		// Added date
		style = new BooklistStyle(R.string.sort_added_date);
		styles.add(style);
		style.addGroup(ROW_KIND_YEAR_ADDED);
		style.addGroup(ROW_KIND_MONTH_ADDED);
		style.addGroup(ROW_KIND_DAY_ADDED);
		style.addGroup(ROW_KIND_AUTHOR);

		// Author/Publication date
		style = new BooklistStyle(R.string.sort_author_year);
		styles.add(style);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_YEAR_PUBLISHED);
		style.addGroup(ROW_KIND_SERIES);

		// Format
		style = new BooklistStyle(R.string.format);
		styles.add(style);
		style.addGroup(ROW_KIND_FORMAT);

		// Read date
		style = new BooklistStyle(R.string.sort_read_date);
		styles.add(style);
		style.addGroup(ROW_KIND_YEAR_READ);
		style.addGroup(ROW_KIND_MONTH_READ);
		style.addGroup(ROW_KIND_AUTHOR);

		// Location
		style = new BooklistStyle(R.string.location);
		styles.add(style);
		style.addGroup(ROW_KIND_LOCATION);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		// Location
		style = new BooklistStyle(R.string.language);
		styles.add(style);
		style.addGroup(ROW_KIND_LANGUAGE);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		// NEWKIND: Add new kinds to this list so the user sees them (Optional)

	}

	/**
	 * Static method to get all user-defined styles from the passed database.
	 * 
	 * @return		BooklistStyles object
	 */
	private static void getUserStyles(CatalogueDBAdapter db, BooklistStyles styles) {
		BooklistStyle style;
		Cursor c = db.getBooklistStyles();
		try {
			// Get the columns we want
			int idCol = c.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			int blobCol = c.getColumnIndex(DatabaseDefinitions.DOM_STYLE.name);
			// Loop over all rows
			while (c.moveToNext()) {
				long id = c.getLong(idCol);
				byte[] blob = c.getBlob(blobCol);
				try {
					style = SerializationUtils.deserializeObject(blob);
				} catch (DeserializationException e) {
					// Not much we can do; just delete it. Really should only happen in development.
					db.deleteBooklistStyle(id);
					style = null;
				}
				if (style != null) {
					style.setRowId(id);
					styles.add(style);
				}
			}
		} finally {
			if (c != null)
				c.close();
		}
	}

	/**
	 * Internal implementation which is passed a collection of styles, and returns
	 * the ordered set of preferred styles.
	 * 
	 * @param allStyles
	 * 
	 * @return
	 */
	private static BooklistStyles filterPreferredStyles(BooklistStyles allStyles) {
		BooklistStyles styles = new BooklistStyles();

		// Get the user preference
		String itemStr = BookCatalogueApp.getAppPreferences().getString(PREF_MENU_ITEMS, null);
		if (itemStr != null && !itemStr.equals("")) {
			// Break it up and process in order
			ArrayList<String> list = Utils.decodeList(itemStr, '|');
			if (list != null) {
				for(String n: list) {
					// Add any exiting style that is preferred
					BooklistStyle s = allStyles.findCanonical(n);
					if (s != null)
						styles.add(s);
				}
			}
		}

		// If none found, return all. Otherwise return the ones we found.
		if (styles.size() > 0)
			return styles;
		else {
			return allStyles;
		}		
	}

	/**
	 * Get the preferred styles using system and user-defined styles.
	 * 
	 * @param db
	 * @return
	 */
	public static BooklistStyles getPreferredStyles(CatalogueDBAdapter db) {
		BooklistStyles allStyles = new BooklistStyles();

		// Get all styles: user & builtin
		getUserStyles(db, allStyles);
		getBuiltinStyles(allStyles);

		// Return filtered list
		return filterPreferredStyles(allStyles);
	}

	/** 
	 * Return all styles, with the preferred styles move to front of list.
	 * 
	 * @param db
	 * @return
	 */
	public static BooklistStyles getAllStyles(CatalogueDBAdapter db) {
		BooklistStyles allStyles = new BooklistStyles();

		// Get all styles and preferred styles.
		getUserStyles(db, allStyles);
		getBuiltinStyles(allStyles);

		BooklistStyles styles = filterPreferredStyles(allStyles);

		// Add missing styles to the end of the list
		if (styles != allStyles) {
			for(BooklistStyle s: allStyles)
				if (!styles.contains(s))
					styles.add(s);
		}

		return styles;
	}

	/**
	 * Return the number of styles in this collection
	 * 
	 * @return
	 */
	public int size() {
		return mList.size();
	}

	/**
	 * Utility to check if this collection contains a specific style INSTANCE.
	 * 
	 * @param style
	 * @return
	 */
	private boolean contains(BooklistStyle style) {
		return mList.contains(style);
	}

	/**
	 * Add a style to this list
	 * 
	 * @param style
	 */
	public void add(BooklistStyle style) {
		style.setPreferred(mPreferredStyleNames.contains(style.getCanonicalName()));
		mList.add(style);
	}
	
	/**
	 * Find a style based on the passed name.
	 * 
	 * @param name
	 * 
	 * @return		Named style, or null
	 */
	public BooklistStyle findCanonical(String name) {
		for(BooklistStyle style: mList) {
			if (style.getCanonicalName().equalsIgnoreCase(name))
				return style;
		}
		return null;
	}
	
	/**
	 * Return the i'th style in the list
	 * 
	 * @param i
	 * 
	 * @return
	 */
	public BooklistStyle get(int i) {
		return mList.get(i);
	}

	/**
	 * Return an iterator for the list of styles.
	 * 
	 * @return
	 */
	public Iterator<BooklistStyle> iterator() {
		return mList.iterator();
	}

	/**
	 * Save the preferred style menu list.
	 * 
	 * @return
	 */
	public static boolean SaveMenuOrder(ArrayList<BooklistStyle> list) {
		String items = "";
		for(int i = 0; i < list.size(); i++) {
			BooklistStyle s = list.get(i);
			if (s.isPreferred()) {
				if (!items.equals(""))
					items += "|";
				items += Utils.encodeListItem(s.getCanonicalName(), '|');				
			}
		}
		Editor e = BookCatalogueApp.getAppPreferences().edit();
		e.putString(PREF_MENU_ITEMS, items);
		e.commit();
		return true;
	};

	/**
	 * Start the activity to edit this style.
	 *
	 * @param a
	 */
	public static void startEditActivity(Activity a) {
		Intent i = new Intent(a, BooklistStylesActivity.class);
		a.startActivityForResult(i, UniqueId.ACTIVITY_BOOKLIST_STYLES);			
	}
}

