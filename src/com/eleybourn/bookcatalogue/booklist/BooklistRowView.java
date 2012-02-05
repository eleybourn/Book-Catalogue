package com.eleybourn.bookcatalogue.booklist;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.FieldVisibility;
import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;

/**
 * TODO: Document!
 * 
 * @author Grunthos
 */
public class BooklistRowView {
	private static Integer mBooklistRowViewIdCounter = 0;
	private final BooklistCursor mCursor;
	private final BooklistBuilder mBuilder;
	private final int mMaxThumbnailWidth;
	private final int mMaxThumbnailHeight;
	private final long mId;

	private final boolean mShowBookshelves;
	private final boolean mShowLocation;
	private final boolean mShowPublisher;
	private final boolean mShowThumbnails;
	
	public BooklistRowView(BooklistCursor c, BooklistBuilder builder) {
		synchronized(mBooklistRowViewIdCounter) {
			mId = ++mBooklistRowViewIdCounter;
		}

		mCursor = c;
		mBuilder = builder;

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

	public long getId() {
		return mId;
	}

	public int getMaxThumbnailHeight() {
		return mMaxThumbnailHeight;			
	}
	public int getMaxThumbnailWidth() {
		return mMaxThumbnailWidth;
	}
	public boolean getShowBookshelves() {
		return mShowBookshelves;
	}
	public boolean getShowLocation() {
		return mShowLocation;
	}
	public boolean getShowPublisher() {
		return mShowPublisher;
	}
	public boolean getShowThumbnails() {
		return mShowThumbnails;
	}
	public boolean hasSeries() {
		return hasColumn(CatalogueDBAdapter.KEY_SERIES_NUM);			
	}

	public int getColumnIndex(String columnName) {
		return mCursor.getColumnIndex(columnName);
	}
	public String getString(int columnIndex) {
		return mCursor.getString(columnIndex);
	}

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
	
	private int mLevel2Col = -2;
	public String getLevel2Data() {
		if (mLevel2Col < 0) {
			final String name = mBuilder.getDisplayDomain(2).name;
			mLevel2Col = mCursor.getColumnIndex(name);
			if (mLevel2Col < 0)
				throw new RuntimeException("Column " + name + " not present in cursor");
		}
		return mCursor.getString(mLevel2Col);			
	}
	
	public boolean hasColumn(String name) {
		return mCursor.getColumnIndex(name) >= 0;
	}
	
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
	
	private int mBookIdCol = -2;
	public long getBookId() {
		if (mBookIdCol < 0) {
			mBookIdCol = mCursor.getColumnIndex(DOM_BOOK.name);
			if (mBookIdCol < 0)
				throw new RuntimeException("Column " + DOM_BOOK + " not present in cursor");
		}
		return mCursor.getLong(mBookIdCol);
	}
	private int mSeriesIdCol = -2;
	public long getSeriesId() {
		if (mSeriesIdCol < 0) {
			mSeriesIdCol = mCursor.getColumnIndex(DOM_SERIES_ID.name);
			if (mBookIdCol < 0)
				throw new RuntimeException("Column " + DOM_SERIES_ID + " not present in cursor");
		}
		return mCursor.getLong(mSeriesIdCol);
	}
	private int mAuthorIdCol = -2;
	public long getAuthorId() {
		if (mAuthorIdCol < 0) {
			mAuthorIdCol = mCursor.getColumnIndex(DOM_AUTHOR_ID.name);
			if (mAuthorIdCol < 0)
				throw new RuntimeException("Column " + DOM_AUTHOR_ID + " not present in cursor");
		}
		return mCursor.getLong(mAuthorIdCol);
	}
	private int mKindCol = -2;
	public int getKind() {
		if (mKindCol < 0) {
			mKindCol = mCursor.getColumnIndex(DOM_KIND.name);
			if (mKindCol < 0)
				throw new RuntimeException("Column " + DOM_KIND + " not present in cursor");
		}
		return mCursor.getInt(mKindCol);
	}
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
	private int mPubMonthCol = -2;
	public String getPublicationMonth() {
		if (mPubMonthCol < 0) {
			mPubMonthCol = mCursor.getColumnIndex(DOM_PUBLICATION_MONTH.name);
			if (mPubMonthCol < 0)
				throw new RuntimeException("Column " + DOM_PUBLICATION_MONTH + " not present in cursor");
		}
		//System.out.println("BooklistRowView(" + mId + ") title at " + mTitleCol + " in cursor " + mCursor.getId());
		return mCursor.getString(mPubMonthCol);
	}
	private int mAuthorCol = -2;
	public String getAuthorName() {
		if (mAuthorCol < 0) {
			mAuthorCol = mCursor.getColumnIndex(DOM_AUTHOR_FORMATTED.name);
			if (mAuthorCol < 0)
				throw new RuntimeException("Column " + DOM_AUTHOR_FORMATTED + " not present in cursor");
		}
		return mCursor.getString(mAuthorCol);
	}
	private int mLevelCol = -2;
	public int getLevel() {
		if (mLevelCol < 0) {
			mLevelCol = mCursor.getColumnIndex(DOM_LEVEL.name);
			if (mLevelCol < 0)
				throw new RuntimeException("Column " + DOM_LEVEL + " not present in cursor");
		}
		return mCursor.getInt(mLevelCol);
	}
	private int mGenreCol = -2;
	public String getGenre() {
		if (mGenreCol < 0) {
			mGenreCol = mCursor.getColumnIndex(DOM_GENRE.name);
			if (mGenreCol < 0)
				throw new RuntimeException("Column " + DOM_GENRE + " not present in cursor");
		}
		return mCursor.getString(mGenreCol);
	}
	private int mTitleLetterCol = -2;
	public String getTitleLetter() {
		if (mTitleLetterCol < 0) {
			mTitleLetterCol = mCursor.getColumnIndex(DOM_TITLE_LETTER.name);
			if (mTitleLetterCol < 0)
				throw new RuntimeException("Column " + DOM_TITLE_LETTER.name + " not present in cursor");
		}
		return mCursor.getString(mTitleLetterCol);
	}

	private int mSeriesNameCol = -2;
	public String getSeriesName() {
		if (mSeriesNameCol < 0) {
			mSeriesNameCol = mCursor.getColumnIndex(DOM_SERIES_NAME.name);
			if (mSeriesNameCol < 0)
				throw new RuntimeException("Column " + DOM_SERIES_NAME + " not present in cursor");
		}
		return mCursor.getString(mSeriesNameCol);
	}
	private int mSeriesNumberCol = -2;
	public String getSeriesNumber() {
		if (mSeriesNumberCol < 0) {
			mSeriesNumberCol = mCursor.getColumnIndex(DOM_SERIES_NUM.name);
			if (mSeriesNumberCol < 0)
				throw new RuntimeException("Column " + DOM_SERIES_NUM + " not present in cursor");
		}
		return mCursor.getString(mSeriesNumberCol);
	}
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
