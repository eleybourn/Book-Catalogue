package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.ROW_KIND_GENRE;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.ROW_KIND_LOANED;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.ROW_KIND_MONTH_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.ROW_KIND_SERIES;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.ROW_KIND_UNREAD;
import static com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds.ROW_KIND_YEAR_PUBLISHED;

import java.util.ArrayList;
import java.util.Iterator;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.BooklistAuthorGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds;

/**
 * TODO: Document!
 * 
 * @author Grunthos
 */
public class BooklistStyles {

	/**
	 * Get all defined styles, including user-defined styles (the latter is not supported yet).
	 * 
	 * NOTE: Do NOT call this in static initialization of application. This method requires the 
	 * 		 application context to be present.
	 * 
	 * @return
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
		style = new BooklistStyle(R.string.sort_title);
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

		// Author/Publication date
		style = new BooklistStyle(R.string.sort_author_year);
		styles.add(style);
		style.addGroup(ROW_KIND_AUTHOR);
		style.addGroup(ROW_KIND_YEAR_PUBLISHED);
		style.addGroup(ROW_KIND_SERIES);

		return styles;
	}

	private ArrayList<BooklistStyle> mList = new ArrayList<BooklistStyle>();
	public void add(BooklistStyle style) {
		mList.add(style);
	}
	public BooklistStyle find(String name) {
		for(BooklistStyle style: mList) {
			if (style.getName().equalsIgnoreCase(name))
				return style;
		}
		return null;
	}
	public BooklistStyle find(int id) {
		for(BooklistStyle style: mList) {
			if (style.getId() == id)
				return style;
		}
		return null;
	}
	public BooklistStyle get(int i) {
		return mList.get(i);
	}
	public Iterator<BooklistStyle> iterator() {
		return mList.iterator();
	}
}

