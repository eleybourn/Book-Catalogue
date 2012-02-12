package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.Utils;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.RowKinds;

/**
 * RowView object for the BooklistCursor.
 * 
 * Implements methods to perform common tasks on the 'current' row of the cursor.
 * 
 * @author Grunthos
 */
public class BooklistRowView {
	/** ID counter */
	private static Integer mBooklistRowViewIdCounter = 0;
	/** Underlying cursor */
	private final BooklistCursor mCursor;
	/** Underlying builder object */
	private final BooklistBuilder mBuilder;
	/** Max size of thumbnails based on preferences at object creation time */
	private final int mMaxThumbnailWidth;
	/** Max size of thumbnails based on preferences at object creation time */
	private final int mMaxThumbnailHeight;
	/** Internal ID for this RowView */
	private final long mId;

	/** Preference setting cached when object was created */
	private final boolean mShowBookshelves;
	/** Preference setting cached when object was created */
	private final boolean mShowLocation;
	/** Preference setting cached when object was created */
	private final boolean mShowPublisher;
	/** Preference setting cached when object was created */
	private final boolean mShowThumbnails;
	
	/**
	 * Constructor
	 * 
	 * @param c			Underlying Cursor
	 * @param builder	Underlying Builder
	 */
	public BooklistRowView(BooklistCursor c, BooklistBuilder builder) {
		// Allocate ID
		synchronized(mBooklistRowViewIdCounter) {
			mId = ++mBooklistRowViewIdCounter;
		}

		// Save underlying objects.
		mCursor = c;
		mBuilder = builder;

		// Cache preferences
		if (BookCatalogueApp.getAppPreferences().getBoolean(BookCataloguePreferences.PREF_LARGE_THUMBNAILS, false)) {
			mMaxThumbnailWidth = 120;
			mMaxThumbnailHeight = 120;
		} else {
			mMaxThumbnailWidth = 60;
			mMaxThumbnailHeight = 60;
		}
		mShowBookshelves = BookCatalogueApp.getAppPreferences().getBoolean(BooklistPreferences.PREF_SHOW_BOOKSHELVES, false);
		mShowThumbnails = BookCatalogueApp.getAppPreferences().getBoolean(BooklistPreferences.PREF_SHOW_THUMBNAILS, false);
		mShowLocation = BookCatalogueApp.getAppPreferences().getBoolean(BooklistPreferences.PREF_SHOW_LOCATION, false);
		mShowPublisher = BookCatalogueApp.getAppPreferences().getBoolean(BooklistPreferences.PREF_SHOW_PUBLISHER, false);
		
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public long getId() {
		return mId;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public int getMaxThumbnailHeight() {
		return mMaxThumbnailHeight;			
	}
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public int getMaxThumbnailWidth() {
		return mMaxThumbnailWidth;
	}
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean getShowBookshelves() {
		return mShowBookshelves;
	}
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean getShowLocation() {
		return mShowLocation;
	}
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean getShowPublisher() {
		return mShowPublisher;
	}
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean getShowThumbnails() {
		return mShowThumbnails;
	}

	/**
	 * Checks if list displays series numbers anywhere.
	 * 
	 * @return
	 */
	public boolean hasSeries() {
		return hasColumn(CatalogueDBAdapter.KEY_SERIES_NUM);			
	}

	/**
	 * Query underlying cursor for column index.
	 * 
	 * @param columnName
	 * @return
	 */
	public int getColumnIndex(String columnName) {
		return mCursor.getColumnIndex(columnName);
	}
	/**
	 * Get string from underlying cursor given a column index.
	 * 
	 * @param columnIndex
	 * @return
	 */
	public String getString(int columnIndex) {
		return mCursor.getString(columnIndex);
	}

	/**
	 * Get the text associated with the highest level group for the current item.
	 */
	private int mLevel1Col = -2;
	public String getLevel1Data() {
		if (mLevel1Col < 0) {
			final String name = mBuilder.getDisplayDomain(1).name;
			mLevel1Col = mCursor.getColumnIndex(name);
			if (mLevel1Col < 0)
				throw new RuntimeException("Column " + name + " not present in cursor");
		}
		return mCursor.getString(mLevel1Col);			
	}
	
	/**
	 * Get the text associated with the second-highest level group for the current item.
	 */
	private int mLevel2Col = -2;
	public String getLevel2Data() {
		if (mBuilder.getStyle().size() < 2)
			return null;
	
		if (mLevel2Col < 0) {
			final String name = mBuilder.getDisplayDomain(2).name;
			mLevel2Col = mCursor.getColumnIndex(name);
			if (mLevel2Col < 0)
				throw new RuntimeException("Column " + name + " not present in cursor");
		}
		String s = mCursor.getString(mLevel2Col);

		switch(mBuilder.getStyle().get(1).kind) {
		case RowKinds.ROW_KIND_MONTH_ADDED:
		case RowKinds.ROW_KIND_MONTH_PUBLISHED:
			try {
				int i = Integer.parseInt(s);
				// If valid, get the name
				if (i > 0 && i <= 12) {
					// Create static formatter if necessary
					s = Utils.getMonthName(i);
				}				
			} catch (Exception e) {
			}
			break;
		default:
			break;
		}
		return s;				
	}

	/**
	 * Check if a given column is present in underlying cursor.
	 * 
	 * @param name
	 * @return
	 */
	public boolean hasColumn(String name) {
		return mCursor.getColumnIndex(name) >= 0;
	}

	/**
	 * Get the 'absolute position' for the current row. This is a value
	 * generated by the builder object.
	 */
	private int mAbsPosCol = -2;
	public int getAbsolutePosition() {
		if (mAbsPosCol < 0) {
			final String name = DOM_ABSOLUTE_POSITION.name;
			mAbsPosCol = mCursor.getColumnIndex(name);
			if (mAbsPosCol < 0)
				throw new RuntimeException("Column " + name + " not present in cursor");
		}
		return mCursor.getInt(mAbsPosCol);			
	}

	/**
	 * Convenience function to retrieve column value.
	 */
	private int mBookIdCol = -2;
	public long getBookId() {
		if (mBookIdCol < 0) {
			mBookIdCol = mCursor.getColumnIndex(DOM_BOOK.name);
			if (mBookIdCol < 0)
				throw new RuntimeException("Column " + DOM_BOOK + " not present in cursor");
		}
		return mCursor.getLong(mBookIdCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mSeriesIdCol = -2;
	public long getSeriesId() {
		if (mSeriesIdCol < 0) {
			mSeriesIdCol = mCursor.getColumnIndex(DOM_SERIES_ID.name);
			if (mBookIdCol < 0)
				throw new RuntimeException("Column " + DOM_SERIES_ID + " not present in cursor");
		}
		return mCursor.getLong(mSeriesIdCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mAuthorIdCol = -2;
	public long getAuthorId() {
		if (mAuthorIdCol < 0) {
			mAuthorIdCol = mCursor.getColumnIndex(DOM_AUTHOR_ID.name);
			if (mAuthorIdCol < 0)
				throw new RuntimeException("Column " + DOM_AUTHOR_ID + " not present in cursor");
		}
		return mCursor.getLong(mAuthorIdCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mKindCol = -2;
	public int getKind() {
		if (mKindCol < 0) {
			mKindCol = mCursor.getColumnIndex(DOM_KIND.name);
			if (mKindCol < 0)
				throw new RuntimeException("Column " + DOM_KIND + " not present in cursor");
		}
		return mCursor.getInt(mKindCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mTitleCol = -2;
	public String getTitle() {
		if (mTitleCol < 0) {
			mTitleCol = mCursor.getColumnIndex(DOM_TITLE.name);
			if (mTitleCol < 0)
				throw new RuntimeException("Column " + DOM_TITLE + " not present in cursor");
		}
		//System.out.println("BooklistRowView(" + mId + ") title at " + mTitleCol + " in cursor " + mCursor.getId());
		return mCursor.getString(mTitleCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mPubMonthCol = -2;
	public String getPublicationMonth() {
		if (mPubMonthCol < 0) {
			mPubMonthCol = mCursor.getColumnIndex(DOM_PUBLICATION_MONTH.name);
			if (mPubMonthCol < 0)
				throw new RuntimeException("Column " + DOM_PUBLICATION_MONTH + " not present in cursor");
		}
		return mCursor.getString(mPubMonthCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mAuthorCol = -2;
	public String getAuthorName() {
		if (mAuthorCol < 0) {
			mAuthorCol = mCursor.getColumnIndex(DOM_AUTHOR_FORMATTED.name);
			if (mAuthorCol < 0)
				throw new RuntimeException("Column " + DOM_AUTHOR_FORMATTED + " not present in cursor");
		}
		return mCursor.getString(mAuthorCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mLevelCol = -2;
	public int getLevel() {
		if (mLevelCol < 0) {
			mLevelCol = mCursor.getColumnIndex(DOM_LEVEL.name);
			if (mLevelCol < 0)
				throw new RuntimeException("Column " + DOM_LEVEL + " not present in cursor");
		}
		return mCursor.getInt(mLevelCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mGenreCol = -2;
	public String getGenre() {
		if (mGenreCol < 0) {
			mGenreCol = mCursor.getColumnIndex(DOM_GENRE.name);
			if (mGenreCol < 0)
				throw new RuntimeException("Column " + DOM_GENRE + " not present in cursor");
		}
		return mCursor.getString(mGenreCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mTitleLetterCol = -2;
	public String getTitleLetter() {
		if (mTitleLetterCol < 0) {
			mTitleLetterCol = mCursor.getColumnIndex(DOM_TITLE_LETTER.name);
			if (mTitleLetterCol < 0)
				throw new RuntimeException("Column " + DOM_TITLE_LETTER.name + " not present in cursor");
		}
		return mCursor.getString(mTitleLetterCol);
	}

	/**
	 * Convenience function to retrieve column value.
	 */
	private int mSeriesNameCol = -2;
	public String getSeriesName() {
		if (mSeriesNameCol < 0) {
			mSeriesNameCol = mCursor.getColumnIndex(DOM_SERIES_NAME.name);
			if (mSeriesNameCol < 0)
				throw new RuntimeException("Column " + DOM_SERIES_NAME + " not present in cursor");
		}
		return mCursor.getString(mSeriesNameCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mSeriesNumberCol = -2;
	public String getSeriesNumber() {
		if (mSeriesNumberCol < 0) {
			mSeriesNumberCol = mCursor.getColumnIndex(DOM_SERIES_NUM.name);
			if (mSeriesNumberCol < 0)
				throw new RuntimeException("Column " + DOM_SERIES_NUM + " not present in cursor");
		}
		return mCursor.getString(mSeriesNumberCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mReadCol = -2;
	public boolean getRead() {
		if (mReadCol < 0) {
			mReadCol = mCursor.getColumnIndex(DOM_READ.name);
			if (mReadCol < 0)
				throw new RuntimeException("Column " + DOM_READ + " not present in cursor");
		}
		return mCursor.getLong(mReadCol) == 1;
	}
}
