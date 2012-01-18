package com.eleybourn.bookcatalogue;

import java.util.Hashtable;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

/**
 * Cursor implementation for book-related queries. The cursor wraps common
 * column lookups and reduces code clutter when accessing common columns.
 * 
 * The cursor also simulates a 'selected' flag for each book based on a 
 * hashmap of book IDs.
 * 
 * @author Grunthos
 *
 */
public class BooksCursor extends SQLiteCursor {

	/** Hasmap of selected book IDs */
	private Hashtable<Long,Boolean> m_selections = new Hashtable<Long,Boolean>();

	/**
	 * Constructor
	 */
	public BooksCursor(SQLiteDatabase db, SQLiteCursorDriver driver,
			String editTable, SQLiteQuery query) {
		super(db, driver, editTable, query);
	}

	private int mIdCol = -2;
	public long getId() {
		if (mIdCol < 0) {
			mIdCol = this.getColumnIndex(CatalogueDBAdapter.KEY_ROWID);
			if (mIdCol < 0)
				throw new RuntimeException("ID column not in result set");
		}
		return this.getLong(mIdCol);
	}

	private int mIsbnCol = -2;
	public String getIsbn() {
		if (mIsbnCol < 0) {
			mIsbnCol = this.getColumnIndex(CatalogueDBAdapter.KEY_ISBN);
			if (mIsbnCol < 0)
				throw new RuntimeException("ISBN column not in result set");
		}
		return this.getString(mIsbnCol);
	}

	private int mPrimaryAuthorCol = -2;
	public String getPrimaryAuthorName() {
		if (mPrimaryAuthorCol < 0) {
			mPrimaryAuthorCol = this.getColumnIndex(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);
			if (mPrimaryAuthorCol < 0)
				throw new RuntimeException("Primary author column not in result set");
		}
		return this.getString(mPrimaryAuthorCol);
	}

	private int mTitleCol = -2;
	public String getTitle() {
		if (mTitleCol < 0) {
			mTitleCol = this.getColumnIndex(CatalogueDBAdapter.KEY_TITLE);
			if (mTitleCol < 0)
				throw new RuntimeException("Title column not in result set");
		}
		return this.getString(mTitleCol);
	}

	private int mDescriptionCol = -2;
	public String getDescription() {
		if (mDescriptionCol < 0) {
			mDescriptionCol = this.getColumnIndex(CatalogueDBAdapter.KEY_ISBN);
			if (mDescriptionCol < 0)
				throw new RuntimeException("Description column not in result set");
		}
		return this.getString(mDescriptionCol);
	}

	private int mNotesCol = -2;
	public String getNotes() {
		if (mNotesCol < 0) {
			mNotesCol = this.getColumnIndex(CatalogueDBAdapter.KEY_ISBN);
			if (mNotesCol < 0)
				throw new RuntimeException("Notes column not in result set");
		}
		return this.getString(mNotesCol);
	}


	private int mReadCol = -2;
	public int getRead() {
		if (mReadCol < 0) {
			mReadCol = this.getColumnIndex(CatalogueDBAdapter.KEY_READ);
			if (mTitleCol < 0)
				throw new RuntimeException("READ column not in result set");
		}
		return this.getInt(mReadCol);
	}

	private int mPublisherCol = -2;
	public String getPublisher() {
		if (mPublisherCol < 0) {
			mPublisherCol = this.getColumnIndex(CatalogueDBAdapter.KEY_PUBLISHER);
			if (mPublisherCol < 0)
				throw new RuntimeException("PUBLISHER column not in result set");
		}
		return this.getString(mPublisherCol);
	}

	private int mSeriesCol = -2;
	public String getSeries() {
		if (mSeriesCol < 0) {
			mSeriesCol = this.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_NAME);
			if (mSeriesCol < 0)
				throw new RuntimeException("SERIES column not in result set");
		}
		return this.getString(mSeriesCol);
	}

	/**
	 * Fake attribute to handle multi-select ListViews. if we ever do them.
	 * 
	 * @return	Flag indicating if current row has been marked as 'selected'.
	 */
	public boolean getIsSelected() {
		Long id = getId();
		if (m_selections.containsKey(id)) {
			return m_selections.get(id);			
		} else {
			return false;
		}
	}

	public void setIsSelected(boolean selected) {
		m_selections.put(getId(), selected);
	}
}
