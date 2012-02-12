package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.*;

import java.util.ArrayList;
import java.util.Iterator;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.BooklistAuthorGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds;

/**
 * Collection of system-defined and user-defined Book List styles.
 * 
 * @author Grunthos
 */
public class BooklistStyles {
	/** Internal storage for defined styles represented by this object */
	private ArrayList<BooklistStyle> mList = new ArrayList<BooklistStyle>();

	/**
	 * Static method to get all defined styles, including user-defined styles (the latter is 
	 * not supported yet).
	 * 
	 * NOTE: Do NOT call this in static initialization of application. This method requires the 
	 * 		 application context to be present.
	 * 
	 * @return		BooklistStyles object
	 */
	public static BooklistStyles getDefinedStyles() {
		// First build the stock ones
		BooklistStyles styles = new BooklistStyles();
		BooklistStyle style;
		BooklistAuthorGroup a;

		// Author/Series
		style = new BooklistStyle(R.string.sort_author_series);
		styles.add(style);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_SERIES);

		// Author(first)/Series
		style = new BooklistStyle(R.string.sort_first_author_series);
		styles.add(style);
		a = new BooklistAuthorGroup();
		a.allAuthors = false;
		style.add(a);
		style.addGroup(ROW_KIND_SERIES);

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

		// Unread
		style = new BooklistStyle(R.string.sort_unread);
		styles.add(style);
		style.addGroup(ROW_KIND_UNREAD);
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

		return styles;
	}

	/**
	 * Add a style to this list
	 * 
	 * @param style
	 */
	public void add(BooklistStyle style) {
		mList.add(style);
	}
	
	/**
	 * Find a style based on the passed name.
	 * 
	 * @param name
	 * 
	 * @return		Named style, or null
	 */
	public BooklistStyle find(String name) {
		for(BooklistStyle style: mList) {
			if (style.getName().equalsIgnoreCase(name))
				return style;
		}
		return null;
	}
	
	/**
	 * Find a style based on (transient) internal ID.
	 * 
	 * @param id
	 * 
	 * @return	Matching style, or null
	 */
	public BooklistStyle find(int id) {
		for(BooklistStyle style: mList) {
			if (style.getId() == id)
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
}

