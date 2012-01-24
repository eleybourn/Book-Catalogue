package com.eleybourn.bookcatalogue;

import android.database.AbstractCursor;
import android.database.sqlite.SQLiteCursor;

/**
 * Cursor object to copy all data from another cursor and save it locally so that the other cursor can
 * be closed. Tests have shown that for SQLiteBooksCursors, copying the entire data set uses less memory
 * than keeping the cursor open. It also uses less SQLite resources, which become important in an 
 * ExpandableListView with 800+ group headings.
 * 
 * TODO: Ideally this object should have the means to rebuild the original cursor. Options include:
 * 
 * - packing it with a Runnable(). OK, but not great because it wont survive serialization
 * - *somehow* retrieving the SQL *AND* parameters/values used to build the original cursor
 * 
 * @author Grunthos
 */
public class CursorSnapshotCursor extends AbstractCursor {

	/** Names of all columns */
	private final String[] mColumnNames;
	/** Values of each column */
	private final ColumnValue[][] mRows;
	/** Pointer to currently active row of this cursor */
	private ColumnValue[] mCurrentRow = null;

	/** Special value to use for 'null' column values */
	private static final NullValue mNullValue = new NullValue();

	/**
	 * We store values retrieved from an SQLite cursor in objects based on the 
	 * actual data type from SQLite. These objects then implement appropriate methods
	 * to respond to the various get*() methods.
	 * 
	 * @author Grunthos
	 */
	private static abstract class ColumnValue {
		public abstract long getLong();
		public abstract double getDouble();
		public abstract String getString();
		public abstract byte[] getBlob();
		public float getFloat() { return (float)getDouble(); }
		public int getInt() { return (int)getLong(); }
		public short getShort() { return (short)getLong(); }
	}

	/**
	 * Class representing a NULL column value.
	 * 
	 * @author Grunthos
	 */
	private static class NullValue extends ColumnValue {
		@Override
		public final long getLong() { throw new RuntimeException("Null value can not be converted to long"); }
		@Override
		public final double getDouble() { throw new RuntimeException("Null value can not be converted to double"); }
		@Override
		public final String getString() { return null; }
		@Override
		public final byte[] getBlob() { throw new RuntimeException("Null value can not be converted to blob"); }		
	}

	/**
	 * Class representing a Long column value
	 * 
	 * @author Grunthos
	 */
	private static class LongValue extends ColumnValue {
		final long mValue;
		LongValue(long value) {
			mValue = value;
		}
		@Override
		public final long getLong() { return mValue; }
		@Override
		public final double getDouble() { return mValue; }
		@Override
		public final String getString() { return Long.toString(mValue); }
		@Override
		public final byte[] getBlob() { throw new RuntimeException("Long value can not be converted to blob"); }		
	}

	/**
	 * Class representing a String column value
	 * 
	 * @author Grunthos
	 */
	private static class StringValue extends ColumnValue {
		final String mValue;
		StringValue(String value) {
			mValue = value;
		}
		@Override
		public final long getLong() { return Long.parseLong(mValue); }
		@Override
		public final double getDouble() { return Double.parseDouble(mValue); }
		@Override
		public final String getString() { return mValue; }
		@Override
		public final byte[] getBlob() { throw new RuntimeException("String value can not be converted to blob"); }		
	}

	/**
	 * Class representing a Double column value
	 * 
	 * @author Grunthos
	 */
	private static class DoubleValue extends ColumnValue {
		final double mValue;
		DoubleValue(double value) {
			mValue = value;
		}
		@Override
		public final long getLong() { return (long)mValue; }
		@Override
		public final double getDouble() { return mValue; }
		@Override
		public final String getString() { return Double.toString(mValue); }
		@Override
		public final byte[] getBlob() { throw new RuntimeException("Double value can not be converted to blob"); }		
	}

	/**
	 * Class representing a Blob column value
	 * 
	 * @author Grunthos
	 */
	private static class BlobValue extends ColumnValue {
		final byte[] mValue;
		BlobValue(byte[] value) {
			mValue = value;
		}
		@Override
		public final long getLong() { throw new RuntimeException("Blob value can not be converted to blob"); }
		@Override
		public final double getDouble() { throw new RuntimeException("Blob value can not be converted to blob"); }
		@Override
		public final String getString() { throw new RuntimeException("Blob value can not be converted to blob"); }
		@Override
		public final byte[] getBlob() { return mValue; }		
	}

	/**
	 * Constructor. Currently only copies SQLiteCursor objects; but they are the resource
	 * intensive ones.
	 * 
	 * @param source
	 */
	public CursorSnapshotCursor( SQLiteCursor source ) {
		// Copy the column names
		final int colCount = source.getColumnCount();
		mColumnNames = new String[colCount];
		for(int i = 0; i < colCount; i++) {
			mColumnNames[i] = source.getColumnName(i);
		}
		// Create the storage for the column values
		mRows = new ColumnValue[source.getCount()][colCount];

		// Save current cursor position
		int savedPosition = source.getPosition();

		// Loop through entire cursor, creating ColumnValue objects for each column of each row.
		int numRows = 0;
		if (source.moveToFirst() )
			do {
				final ColumnValue[] row = mRows[numRows++];
				for(int i = 0; i < colCount; i++) {
					if (source.isString(i)) {
						row[i] = new StringValue(source.getString(i));					
					} else if (source.isLong(i)) {
						row[i] = new LongValue(source.getLong(i));
					} else if (source.isFloat(i)) {
						row[i] = new DoubleValue(source.getDouble(i));
					} else if (source.isBlob(i)) {
						row[i] = new BlobValue(source.getBlob(i));
					} else if (source.isNull(i)) {
						row[i] = mNullValue;
					} else {
						row[i] = new StringValue(source.getString(i));
					}
				}
			} while (source.moveToNext());
		// Restore cursor position
		source.moveToPosition(savedPosition);
	}

	/**
	 * Update mCurrentRow when position changes.
	 */
	@Override
	public boolean onMove(final int oldPosition, final int newPosition) {
		if (newPosition < 0 || newPosition >= mRows.length) {
			mCurrentRow = null;
			return false;
		}

		if (newPosition < 0)
			mCurrentRow = null;
		else
			mCurrentRow = mRows[newPosition];
		return true;
	}

	@Override
	public final String[] getColumnNames() {
		return mColumnNames;
	}

	/**
	 * Get number of rows in this cursor
	 */
	@Override
	public final int getCount() {
		return mRows.length;
	}

	/**
	 * Use ColumnValue to get specified column as a double
	 */
	@Override
	public final double getDouble(int column) {
		return mCurrentRow[column].getDouble();
	}

	/**
	 * Use ColumnValue to get specified column as a float
	 */
	@Override
	public final float getFloat(int column) {
		return (Float)mCurrentRow[column].getFloat();
	}

	/**
	 * Use ColumnValue to get specified column as an int
	 */
	@Override
	public final int getInt(int column) {
		return mCurrentRow[column].getInt();
	}

	/**
	 * Use ColumnValue to get specified column as a long
	 */
	@Override
	public final long getLong(int column) {
		return mCurrentRow[column].getLong();
	}

	/**
	 * Use ColumnValue to get specified column as a short
	 */
	@Override
	public final short getShort(int column) {
		return mCurrentRow[column].getShort();
	}

	/**
	 * Use ColumnValue to get specified column as a String
	 */
	@Override
	public final String getString(int column) {
		return mCurrentRow[column].getString();
	}

	/**
	 * Check if ColumnValue is the special nullValue object
	 */
	@Override
	public final boolean isNull(int column) {
		return mCurrentRow[column].equals(mNullValue);
	}
}
