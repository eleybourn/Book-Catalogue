package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.Hashtable;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import android.database.Cursor;
import android.os.Bundle;

public class BookData extends DataManager {
	
	private long mRowId;

//    private final CatalogueDBAdapter mDb;
//	private ArrayList<Author> mAuthorList = null;
//	private ArrayList<Series> mSeriesList = null;

	private static final String KEY_BOOKSHELF_LIST = "+BookshelfList";
	private static final String KEY_BOOKSHELF_TEXT = "+BookshelfText";
	
	public BookData() {
		this(0L, ((Bundle)null));
	}

	public BookData(Long rowId) {
		this(rowId, ((Bundle)null));
	}

	public BookData(Bundle src) {
		this(0L, src);
	}

	public BookData(Long rowId, Bundle src) {
		if (rowId == null) {
			mRowId = 0;
		} else {
			mRowId = rowId;
		}
		if (src != null) {
			putAll(src);			
		} else if (mRowId > 0) {
			loadFromDb();
		}
		initValidators();
	}

	public void setBookshelfList(String encodedList) {
		putString(KEY_BOOKSHELF_LIST, encodedList);
	}
	public String getBookshelfList() {
		return getString(KEY_BOOKSHELF_LIST);
	}
	public String getBookshelfText() {
		String list = getBookshelfList();
		ArrayList<String> items = Utils.decodeList(list, BookDetailsAbstract.BOOKSHELF_SEPERATOR);
		if (items.size() == 0)
			return "";

		StringBuilder text = new StringBuilder(items.get(0));
		for(int i = 1; i < items.size(); i++) {
			text.append(", ");
			text.append(items.get(i));
		}
		return text.toString();
	}

	private String getBookshelfListFromDb(CatalogueDBAdapter db) {
		Cursor bookshelves = db.fetchAllBookshelvesByBook(getRowId());
		try {
			String bookshelves_list = "";
			while (bookshelves.moveToNext()) {
				String name = bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF));
				String encoded_name = Utils.encodeListItem(name, BookDetailsAbstract.BOOKSHELF_SEPERATOR);
				if (bookshelves_list.equals("")) {
					bookshelves_list = encoded_name;
				} else {
					bookshelves_list += BookDetailsAbstract.BOOKSHELF_SEPERATOR + encoded_name;
				}
			}
			return bookshelves_list;			
		} finally {
			if (bookshelves != null)
				bookshelves.close();
		}
	}

	public long getRowId() {
		return mRowId;
	}

	public BookData reload() {
		loadFromDb();
		return this;
	}

	private void loadFromDb() {
		long rowId = getRowId();
		if (rowId == 0)
			return;

		CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.context);
		db.open();
		try {
			BooksCursor book = db.fetchBookById(getRowId());
			try {
				putAll(book);

				// Get author and series lists
				setAuthorList(db.getBookAuthorList(getRowId()));
				setSeriesList(db.getBookSeriesList(getRowId()));
				setBookshelfList(getBookshelfListFromDb(db));
				setAnthologyTitles(db.getBookAnthologyTitleList(getRowId()));

			} catch (Exception e) {
				Logger.logError(e);
			} finally {
				if (book != null)
					book.close();
			}		
			
		} finally {
			db.close();
		}
	}

	public ArrayList<Author> getAuthorList() {
//		if (mAuthorList == null) {
//			mAuthorList = getAuthors();
//		}
//		return mAuthorList;
		return getAuthors();
	}

	public void setAnthologyTitles(ArrayList<AnthologyTitle> list) {
//		mAuthorList = (ArrayList<Author>) list.clone();
//		putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
		putSerializable(CatalogueDBAdapter.KEY_ANTHOLOGY_TITLE_ARRAY, list);
	}

	public void setAuthorList(ArrayList<Author> list) {
//		mAuthorList = (ArrayList<Author>) list.clone();
//		putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mAuthorList);
		putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, list);
	}

	public ArrayList<Series> getSeriesList() {
//		if (mSeriesList == null) {
//			mSeriesList = getSeries();
//		}
		return getSeries(); // mSeriesList;
	}

	public void setSeriesList(ArrayList<Series> list) {
		//mSeriesList = (ArrayList<Series>) list.clone();
//		putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, mSeriesList);
		putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, list);
	}

	public String getAuthorTextShort() {
		String newText;
		ArrayList<Author> list = getAuthorList();
		if (list.size() == 0) {
			newText = null;
		} else {
			newText = list.get(0).getDisplayName();
			if (list.size() > 1) {
				newText += " " + BookCatalogueApp.context.getResources().getString(R.string.and_others);
			}
		}
		return newText;		
	}
	public String getSeriesTextShort() {
		String newText;
		ArrayList<Series> list = getSeriesList();
		if (list.size() == 0) {
			newText = null;
		} else {
			newText = list.get(0).getDisplayName();
			if (list.size() > 1) {
				newText += " " + BookCatalogueApp.context.getResources().getString(R.string.and_others);
			}
		}
		return newText;		
	}
	
	public static final String KEY_ANTHOLOGY = "+IsAnthology";
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
				Integer mask = data.getInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK);				
				return mask != 0 ? "1" : "0";
			}

			@Override
			public void set(DataManager data, Datum datum, Bundle rawData, Object value) {
				Integer mask = getInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK);				
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
				return !getBookshelfText().equals("");
			}
		});
		
		addAccessor(CatalogueDBAdapter.KEY_ROWID, new DataAccessor() {
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
		

//		addValidator(R.id.author, "", CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, nonBlankValidator);
//		mFields.add(R.id.isbn, CatalogueDBAdapter.KEY_ISBN, null);
//		mFields.add(R.id.publisher, CatalogueDBAdapter.KEY_PUBLISHER, null);
//		mFields.add(R.id.date_published_button, "", CatalogueDBAdapter.KEY_DATE_PUBLISHED, null);
//		mFields.add(R.id.date_published, CatalogueDBAdapter.KEY_DATE_PUBLISHED, CatalogueDBAdapter.KEY_DATE_PUBLISHED,
//				null, new Fields.DateFieldFormatter());
//		mFields.add(R.id.series, CatalogueDBAdapter.KEY_SERIES_NAME, CatalogueDBAdapter.KEY_SERIES_NAME, null);
		addValidator("list_price", blankOrFloatValidator);
		addValidator(CatalogueDBAdapter.KEY_PAGES, blankOrIntegerValidator);
//		mFields.add(R.id.format, CatalogueDBAdapter.KEY_FORMAT, null);
//		mFields.add(R.id.bookshelf, "", null);
//		mFields.add(R.id.description, CatalogueDBAdapter.KEY_DESCRIPTION, null);
//		mFields.add(R.id.genre, CatalogueDBAdapter.KEY_GENRE, null);
		
//		mFields.add(R.id.row_img, "", "thumbnail", null);
//		mFields.getField(R.id.row_img).getView().setOnCreateContextMenuListener(mCreateBookThumbContextMenuListener);
		
//		mFields.add(R.id.format_button, "", CatalogueDBAdapter.KEY_FORMAT, null);
//		mFields.add(R.id.bookshelf_text, "bookshelf_text", null).doNoFetch = true; // Output-only field
		
	}
	
	/**
	 * Utility routine to get an author list from a data manager
	 * 
	 * @param i		Intent with author list
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<AnthologyTitle> getAnthologyTitles() {
		ArrayList<AnthologyTitle> list = (ArrayList<AnthologyTitle>) getSerializable(CatalogueDBAdapter.KEY_ANTHOLOGY_TITLE_ARRAY);
		if (list == null) {
			list = new ArrayList<AnthologyTitle>();
		}
		return list;
	}

	/**
	 * Utility routine to get an author list from a data manager
	 * 
	 * @param i		Intent with author list
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<Author> getAuthors() {
		ArrayList<Author> list = (ArrayList<Author>) getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
		if (list == null) {
			list = new ArrayList<Author>();
		}
		return list;
	}

	/**
	 * Utility routine to get an author list from a data manager
	 * 
	 * @param i		Intent with author list
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	private ArrayList<Series> getSeries() {
		ArrayList<Series> list = (ArrayList<Series>) getSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY);
		if (list == null) {
			list = new ArrayList<Series>();
		}
		return list;
	}

	public boolean isRead() {
		int val = getInt(CatalogueDBAdapter.KEY_READ);
		return val != 0;
	}

	public boolean isSigned() {
		int val = getInt(CatalogueDBAdapter.KEY_SIGNED);
		return val != 0;
	}

	public void refreshAuthorList(CatalogueDBAdapter db) {
		ArrayList<Author> list = getAuthorList();
		for(Author a : list) {
			db.refreshAuthor(a);
		}
		setAuthorList(list);
	}

	public void cleanupThumbnails() {
		Utils.cleanupThumbnails(mBundle);
	}

	public Bundle getRawData() {
		return mBundle;
	}
}
