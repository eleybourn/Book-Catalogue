/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import android.database.Cursor;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Represents the underlying data for a book.
 * 
 * @author pjw
 */
public class BookData extends DataManager {
	
	/** Row ID for book */
	private long mRowId;

	/** Key for special field */
	private static final String KEY_BOOKSHELF_LIST = "+BookshelfList";
	/** Key for special field */
	private static final String KEY_BOOKSHELF_TEXT = "+BookshelfText";
	/** Key for special field */
	public static final String KEY_ANTHOLOGY = "+IsAnthology";

	/** Constructor */
	public BookData() {
		this(0L, null);
	}

	/** Constructor */
	public BookData(Long rowId) {
		this(rowId, null);
	}

	/**
	 * Constructor
	 * 
	 * @param src		Bundle with book data (may be null)
	 */
	public BookData(Bundle src) {
		this(0L, src);
	}

	/**
	 * Constructor
	 * 
	 * @param rowId		ID of book (may be 0 for new)
	 * @param src		Bundle with book data (may be null)
	 */
	public BookData(Long rowId, Bundle src) {
		// Save the row, if possible
		if (rowId == null) {
			mRowId = 0;
		} else {
			mRowId = rowId;
		}
		// Load from bundle or database
		if (src != null) {
			putAll(src);			
		} else if (mRowId > 0) {
			loadFromDb();
		}
		// Create special validators
		initValidators();
	}

	/**
	 * Erase everything in this instance and reset the special handlers
 	 * 
	 * @return	self, for chaining
	 */
	@Override
	public DataManager clear() {
		super.clear();
		// Create special validators
		initValidators();
		return this;
	}
	
	/** Accessor */
	public void setBookshelfList(String encodedList) {
		putString(KEY_BOOKSHELF_LIST, encodedList);
	}
	/** Accessor */
	public String getBookshelfList() {
		return getString(KEY_BOOKSHELF_LIST);
	}
	/** Accessor. Return a formatted list of books. */
	public String getBookshelfText() {
		String list = getBookshelfList();
		ArrayList<String> items = Utils.decodeList(list, BookAbstract.BOOKSHELF_SEPARATOR);
		if (items.isEmpty())
			return "";

		StringBuilder text = new StringBuilder(items.get(0));
		for(int i = 1; i < items.size(); i++) {
			text.append(", ");
			text.append(items.get(i));
		}
		return text.toString();
	}

	/**
	 * Create the list of bookshelves in the underlying data
	 * 
	 * @param db		Database connection
	 * 
	 * @return			The list
	 */
	private String getBookshelfListFromDb(CatalogueDBAdapter db) {
        try (Cursor bookshelves = db.fetchAllBookshelvesByBook(getRowId())) {
            StringBuilder bookshelves_list = new StringBuilder();
            while (bookshelves.moveToNext()) {
                String name = bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF));
                String encoded_name = Utils.encodeListItem(name, BookAbstract.BOOKSHELF_SEPARATOR);
                if (bookshelves_list.length() == 0) {
                    bookshelves_list = new StringBuilder(encoded_name);
                } else {
                    bookshelves_list.append(BookAbstract.BOOKSHELF_SEPARATOR).append(encoded_name);
                }
            }
            return bookshelves_list.toString();
        }
	}

	/** Accessor */
	public long getRowId() {
		return mRowId;
	}

	/**
     * Reload all data from DB
     */
	public void reload() {
		loadFromDb();
    }

	/**
	 * Load the book details from the database
	 */
	private void loadFromDb() {
		long rowId = getRowId();
		// If ID = 0, no details in DB
		if (rowId == 0)
			return;

		// Connect to DB and get cursor for bok details
		CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.context);
		db.open();
		try {
            try (BooksCursor book = db.fetchBookById(getRowId())) {
                // Put all cursor fields in collection
                putAll(book);

                // Get author, series, bookshelf and anthology title lists
                setAuthorList(db.getBookAuthorList(getRowId()));
                setSeriesList(db.getBookSeriesList(getRowId()));
                setBookshelfList(getBookshelfListFromDb(db));
                setAnthologyTitles(db.getBookAnthologyTitleList(getRowId()));

            } catch (Exception e) {
                Logger.logError(e);
            }
			
		} finally {
			db.close();
		}
	}

	/** Special Accessor */
	public ArrayList<Author> getAuthorList() {
		return getAuthors();
	}

	/** Special Accessor */
	public void setAnthologyTitles(ArrayList<AnthologyTitle> list) {
		putSerializable(CatalogueDBAdapter.KEY_ANTHOLOGY_TITLE_ARRAY, list);
	}

	/** Special Accessor */
	public void setAuthorList(ArrayList<Author> list) {
		putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, list);
	}

	/** Special Accessor */
	public ArrayList<Series> getSeriesList() {
		return getSeries(); // mSeriesList;
	}

	/** Special Accessor */
	public void setSeriesList(ArrayList<Series> list) {
		putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, list);
	}

	/**
	 *  Special Accessor. Build a formatted string for author list.
	 */
	public String getAuthorTextShort() {
		String newText;
		ArrayList<Author> list = getAuthorList();
		if (list.isEmpty()) {
			newText = null;
		} else {
			newText = list.get(0).getDisplayName();
			if (list.size() > 1) {
				newText += " " + BookCatalogueApp.context.getResources().getString(R.string.and_others);
			}
		}
		return newText;		
	}
	/**
	 *  Special Accessor. Build a formatted string for series list.
	 */
	public String getSeriesTextShort() {
		String newText;
		ArrayList<Series> list = getSeriesList();
		if (list.isEmpty()) {
			newText = null;
		} else {
			newText = list.get(0).getDisplayName();
			if (list.size() > 1) {
				newText += " " + BookCatalogueApp.context.getResources().getString(R.string.and_others);
			}
		}
		return newText;		
	}
	
	/**
	 * Build any special purpose validators
	 */
	private void initValidators() {
		addValidator(CatalogueDBAdapter.KEY_TITLE, nonBlankValidator);
		addValidator(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK, integerValidator);

		/* Anthology needs special handling, and we use a formatter to do this. If the original
		 * value was 0 or 1, then setting/clearing it here should just set the new value to 0 or 1.
		 * However...if if the original value was 2, then we want setting/clearing to alternate
		 * between 2 and 0, not 1 and 0.
		 * So, despite if being a checkbox, we use an integerValidator and use a special formatter.
		 * We also store it in the tag field so that it is automatically serialized with the
		 * activity. */
		addAccessor(KEY_ANTHOLOGY, new DataAccessor() {
			@Override
			public Object get(DataManager data, Datum datum, Bundle rawData) {
				int mask = data.getInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK);
				return mask != 0 ? "1" : "0";
			}

			@Override
			public void set(DataManager data, Datum datum, Bundle rawData, Object value) {
				int mask = getInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK);
				// Parse the string the CheckBox returns us (0 or 1)
				if (Utils.objectToBoolean(value)) {
					mask |= 1;
				} else {
					mask &= 0xFFFFFFFE;
				}
				putInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK, mask);
				
			}

			@Override
			public boolean isPresent(DataManager data, Datum datum, Bundle rawData) {
				return rawData.containsKey(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK);
			}

		});

		// Make a formatted list of bookshelves
		addAccessor(KEY_BOOKSHELF_TEXT, new DataAccessor() {
			@Override
			public Object get(DataManager data, Datum datum, Bundle rawData) {
				return getBookshelfText();
			}

			@Override
			public void set(DataManager data, Datum datum, Bundle rawData, Object value) {
				throw new RuntimeException("Bookshelf Text can not be set");
			}

			@Override
			public boolean isPresent(DataManager data, Datum datum, Bundle rawData) {
				return !getBookshelfText().isEmpty();
			}
		});

		// Whenever the row ID is written, make sure mRowId is updated.
		addAccessor(CatalogueDBAdapter.KEY_ROW_ID, new DataAccessor() {
			@Override
			public Object get(DataManager data, Datum datum, Bundle rawData) {
				return Datum.objectToLong(rawData.get(datum.getKey()));
			}

			@Override
			public void set(DataManager data, Datum datum, Bundle rawData, Object value) {
				rawData.putLong(datum.getKey(), Datum.objectToLong(value));
				mRowId = rawData.getLong(datum.getKey());
			}

			@Override
			public boolean isPresent(DataManager data, Datum datum, Bundle rawData) {
				return true;
			}
		});
		

		addValidator("list_price", blankOrFloatValidator);
		addValidator(CatalogueDBAdapter.KEY_PAGES, blankOrIntegerValidator);
	}
	
	/**
	 * Utility routine to get an author list from a data manager
	 * 
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<AnthologyTitle> getAnthologyTitles() {
		ArrayList<AnthologyTitle> list = (ArrayList<AnthologyTitle>) getSerializable(CatalogueDBAdapter.KEY_ANTHOLOGY_TITLE_ARRAY);
		if (list == null) {
			list = new ArrayList<>();
		}
		return list;
	}

	/**
	 * Utility routine to get an author list from a data manager
	 * 
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<Author> getAuthors() {
		ArrayList<Author> list = (ArrayList<Author>) getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
		if (list == null) {
			list = new ArrayList<>();
		}
		return list;
	}

	/**
	 * Utility routine to get an author list from a data manager
	 * 
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<Series> getSeries() {
		ArrayList<Series> list = (ArrayList<Series>) getSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY);
		if (list == null) {
			list = new ArrayList<>();
		}
		return list;
	}

	/** Convenience Accessor */
	public boolean isRead() {
		int val = getInt(CatalogueDBAdapter.KEY_READ);
		return val != 0;
	}

	/** Convenience Accessor */
	public boolean isSigned() {
		int val = getInt(CatalogueDBAdapter.KEY_SIGNED);
		return val != 0;
	}

	/** 
	 * Update author details from DB
	 * 
	 * @param db		Database connection
	 */
	public void refreshAuthorList(CatalogueDBAdapter db) {
		ArrayList<Author> list = getAuthorList();
		for(Author a : list) {
			db.refreshAuthor(a);
		}
		setAuthorList(list);
	}

    /**
     * Reload the series list from the database.
     * Call this when the series list might have been changed by another activity (like the Series Editor).
     */
    public void refreshSeriesList(CatalogueDBAdapter db) {
        ArrayList<Series> list = getSeriesList();
        for(Series s : list) {
            db.refreshSeries(s);
        }
        setSeriesList(list);
    }


    /**
	 * Cleanup thumbnails from underlying data
	 */
	public void cleanupThumbnails() {
		Utils.cleanupThumbnails(mBundle);
	}

	/**
	 * Get the underlying raw data.
	 * DO NOT UPDATE THIS! IT SHOULD BE USED FOR READING DATA ONLY.
	 */
	public Bundle getRawData() {
		return mBundle;
	}
}
