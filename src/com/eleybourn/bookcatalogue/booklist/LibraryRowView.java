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

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ABSOLUTE_POSITION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_GENRE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_KIND;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LEVEL;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_TITLE_LETTER;
import android.content.res.Resources;
import android.database.Cursor;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * RowView object for the BooklistCursor.
 * Implements methods to perform common tasks on the 'current' row of the cursor.
 * 
 * @author Philip Warner
 */
public class LibraryRowView {
	/** ID counter */
    private static final Object sBooklistRowViewIdLock = new Object();
	private static Integer mBooklistRowViewIdCounter = 0;
	/** Underlying cursor */
	private final Cursor mCursor;
	/** Underlying builder object */
	private final LibraryBuilder mBuilder;
	/** Max size of thumbnails based on preferences at object creation time */
	private final int mMaxThumbnailWidth;
	/** Max size of thumbnails based on preferences at object creation time */
	private final int mMaxThumbnailHeight;
	/** Internal ID for this RowView */
	private final long mId;

	/**
	 * Constructor
	 * 
	 * @param c			Underlying Cursor
	 * @param builder	Underlying Builder
	 */
	public LibraryRowView(BooklistCursor c, LibraryBuilder builder) {
		// Allocate ID
		synchronized(sBooklistRowViewIdLock) {
			mId = ++mBooklistRowViewIdCounter;
		}

		// Save underlying objects.
		mCursor = c;
		mBuilder = builder;

		final int extras = mBuilder.getStyle().getExtras();

		// Get thumbnail size
		int maxSize = computeThumbnailSize(extras);
		mMaxThumbnailWidth = maxSize;
		mMaxThumbnailHeight = maxSize;
	}

	/**
	 * Constructor
	 * 
	 * @param c			Underlying Cursor
	 * @param builder	Underlying Builder
	 */
	public LibraryRowView(BooklistPseudoCursor c, LibraryBuilder builder) {
		// Allocate ID
		synchronized(sBooklistRowViewIdLock) {
			mId = ++mBooklistRowViewIdCounter;
		}

		// Save underlying objects.
		mCursor = c;

		mBuilder = builder;

		final int extras = mBuilder.getStyle().getExtras();

		// Get thumbnail size
		int maxSize = computeThumbnailSize(extras);
		mMaxThumbnailWidth = maxSize;
		mMaxThumbnailHeight = maxSize;
	}

	/**
	 * Return the thumbnail size in DP.
	 * 
	 * @param extras	Flags for style
	 * 
	 * @return	Requested thumbnail size
	 */
	private int computeThumbnailSize(int extras) {
		int maxSize;

		if ( (extras & BooklistStyle.EXTRAS_THUMBNAIL_LARGE) != 0) {
			maxSize = 90;
		} else {
			maxSize = 60;
		}

		DisplayMetrics metrics = BookCatalogueApp.context.getResources().getDisplayMetrics();
		maxSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, maxSize, metrics));		
		return maxSize;
	}

	/**
	 * Get Utils from underlying cursor
	 */
	public Utils getUtils() {
		return ((BooklistSupportProvider)mCursor).getUtils();
	}

	/**
	 * Accessor
	 */
	public BooklistStyle getStyle() {
		return mBuilder.getStyle();
	}

	/**
	 * Accessor
	 */
	public long getId() {
		return mId;
	}

	/**
	 * Accessor
	 */
	public int getMaxThumbnailHeight() {
		return mMaxThumbnailHeight;			
	}
	/**
	 * Accessor
	 */
	public int getMaxThumbnailWidth() {
		return mMaxThumbnailWidth;
	}

	/**
	 * Checks if list displays series numbers anywhere.
	 */
	public boolean hasSeries() {
		return hasColumn(CatalogueDBAdapter.KEY_SERIES_NUM);			
	}

	/**
	 * Query underlying cursor for column index.
	 */
	public int getColumnIndex(String columnName) {
		return mCursor.getColumnIndex(columnName);
	}
	/**
	 * Get string from underlying cursor given a column index.
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
		return formatRowGroup(0, mCursor.getString(mLevel1Col));
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
		return formatRowGroup(1, mCursor.getString(mLevel2Col));
	}

	/**
	 * Perform any special formatting for a row group.
	 * 
	 * @param level		Level of the row group
	 * @param s			Source value
	 * @return			Formatted string
	 */
	private String formatRowGroup(int level, String s) {
		switch(mBuilder.getStyle().getGroupAt(level).kind) {
		case RowKinds.ROW_KIND_MONTH_ADDED:
		case RowKinds.ROW_KIND_MONTH_PUBLISHED:
		case RowKinds.ROW_KIND_MONTH_READ:
		case RowKinds.ROW_KIND_UPDATE_MONTH:
			try {
				int i = Integer.parseInt(s);
				// If valid, get the name
				if (i > 0 && i <= 12) {
					// Create static formatter if necessary
					s = Utils.getMonthName(i);
				}				
			} catch (Exception ignored) {
			}
			break;

		case RowKinds.ROW_KIND_RATING:
			try {
				int i = Integer.parseInt(s);
				// If valid, get the name
				if (i >= 0 && i <= 5) {
					Resources r = BookCatalogueApp.context.getResources();
					s = r.getQuantityString(R.plurals.n_stars, i, i);
				}				
			} catch (Exception ignored) {
			}
			break;
			
		default:
			break;
		}
		return s;
	}

	/**
	 * Check if a given column is present in underlying cursor.
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
	private int mBookUuidCol = -2;
	public String getBookUuid() {
		if (mBookUuidCol < 0) {
			mBookUuidCol = mCursor.getColumnIndex(DOM_BOOK_UUID.name);
			if (mBookUuidCol < 0)
				throw new RuntimeException("Column " + DOM_BOOK_UUID + " not present in cursor");
		}
		return mCursor.getString(mBookUuidCol);
	}

	/**
	 * Convenience function to retrieve column value.
	 */
	private int mSeriesIdCol = -2;
	public long getSeriesId() {
		if (mSeriesIdCol < 0) {
			mSeriesIdCol = mCursor.getColumnIndex(DOM_SERIES_ID.name);
			if (mSeriesIdCol < 0)
				throw new RuntimeException("Column " + DOM_SERIES_ID + " not present in cursor");
		}
		return mCursor.getLong(mSeriesIdCol);
	}

	public boolean hasSeriesId() {
		if (mSeriesIdCol >= 0)
			return true;
		mSeriesIdCol = mCursor.getColumnIndex(DOM_SERIES_ID.name);
		return (mSeriesIdCol >= 0);
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

	public boolean hasAuthorId() {
		if (mAuthorIdCol >= 0)
			return true;
		mAuthorIdCol = mCursor.getColumnIndex(DOM_AUTHOR_ID.name);
		return (mAuthorIdCol >= 0);
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
		//System.out.println("LibraryRowView(" + mId + ") title at " + mTitleCol + " in cursor " + mCursor.getId());
		return mCursor.getString(mTitleCol);
	}
	/**
	 * Convenience function to retrieve column value.
	 */
	private int mPubMonthCol = -2;
	@SuppressWarnings("unused")
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
	@SuppressWarnings("unused")
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
	private int mFormatCol = -2;
	public String getFormat() {
		if (mFormatCol < 0) {
			mFormatCol = mCursor.getColumnIndex(DOM_FORMAT.name);
			if (mFormatCol < 0)
				throw new RuntimeException("Column " + DOM_FORMAT + " not present in cursor");
		}
		return mCursor.getString(mFormatCol);
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
	@SuppressWarnings("unused")
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


	/**
	 * Function used for long click local menu in order to propose, or not, the possibility to mark as read.
	 * @return true if and only if this book has the read status.
	 */
	public boolean isRead() {
		//
		int index =mCursor.getColumnIndex(CatalogueDBAdapter.KEY_READ);
		if (index>=0) {
			return 0!=mCursor.getInt(index);
		}
		return false;
	}
}
