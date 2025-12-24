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
package com.eleybourn.bookcatalogue.datamanager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to manage a version of a set of related data.
 * 
 * @author pjw
 *
 */
public class DataManager {
	// Generic validators; if field-specific defaults are needed, create a new one.
	protected static DataValidator integerValidator = new IntegerValidator("0");
	protected static DataValidator nonBlankValidator = new NonBlankValidator();
	protected static DataValidator blankOrIntegerValidator = new OrValidator(new BlankValidator(),
			new IntegerValidator("0"));
	protected static DataValidator blankOrFloatValidator = new OrValidator(new BlankValidator(),
			new FloatValidator("0.00"));

	/** Raw data storage */
	protected final Bundle mBundle = new Bundle();
	/** Storage for the data-related code */
	private final DatumHash mData = new DatumHash();
	
	/** The last validator exception caught by this object */
	private final ArrayList<ValidatorException> mValidationExceptions = new ArrayList<>();
	/** A list of cross-validators to apply if all fields pass simple validation. */
	private ArrayList<DataCrossValidator> mCrossValidators = new ArrayList<DataCrossValidator>();

	/**
	 * Erase everything in this instance
	 * 
	 * @return	self, for chaining
	 */
	public DataManager clear() {
		mBundle.clear();
		mData.clear();
		mValidationExceptions.clear();
		mCrossValidators.clear();
		return this;
	}

	/**
	 * Class to manage the collection of Datum objects for this DataManager
	 *
	 * @author pjw
	 */
	private static class DatumHash extends Hashtable<String,Datum> {
		private static final long serialVersionUID = -650159534364183779L;

		/**
		 * Get the specified Datum, and create a stub if not present
		 */
		@Override 
		public Datum get(Object key) {
			Datum datum = super.get(key);
			if (datum == null) {
				datum = new Datum(key.toString(), null, true);
				this.put(key.toString(), datum);
			}
			return datum;			
		}
	}

	/**
     * Add a validator for the specified Datum
     *
     * @param key       Key to the Datum
     * @param validator Validator
     */
	public void addValidator(String key, DataValidator validator) {
		mData.get(key).setValidator(validator);
    }

	/**
     * Add an Accessor for the specified Datum
     *
     * @param key      Key to the Datum
     * @param accessor Accessor
     */
	public void addAccessor(String key, DataAccessor accessor) {
		mData.get(key).setAccessor(accessor);
    }

	/**
	 * Get the data object specified by the passed key
	 * 
	 * @param key	Key of data object
	 * 
	 * @return		Data object
	 */
	public Object get(String key) {
		return get(mData.get(key));
	}
	/**
	 * Get the data object specified by the passed Datum
	 * 
	 * @param datum	Datum
	 * 
	 * @return		Data object
	 */
	public Object get(Datum datum) {
		return datum.get(this, mBundle);
	}
	
	/** Retrieve a boolean value */
	public boolean getBoolean(String key) {
		return mData.get(key).getBoolean(this, mBundle);
	}
	/** Store a boolean value */
	public DataManager putBoolean(String key, boolean value) {
		mData.get(key).putBoolean(this, mBundle, value);
		return this;
	}
	/** Store a boolean value */
	public DataManager putBoolean(Datum datum, boolean value) {
		datum.putBoolean(this, mBundle, value);
		return this;
	}

	/** Get a double value */
	public double getDouble(String key) {
		return mData.get(key).getDouble(this, mBundle);
	}
	/**
     * Store a double value
     */
	public void putDouble(String key, double value) {
		mData.get(key).putDouble(this, mBundle, value);
    }
	/** Store a double value */
	public DataManager putDouble(Datum datum, double value) {
		datum.putDouble(this, mBundle, value);
		return this;
	}

	/** Get a float value */
	public float getFloat(String key) {
		return mData.get(key).getFloat(this, mBundle);
	}
	/** Store a float value */
	public DataManager putFloat(String key, float value) {
		mData.get(key).putFloat(this, mBundle, value);
		return this;
	}
	/** Store a float value */
	public DataManager putFloat(Datum datum, float value) {
		datum.putFloat(this, mBundle, value);
		return this;
	}

	/** Get an int value */
	public int getInt(String key) {
		return mData.get(key).getInt(this, mBundle);
	}
	/** Store an int value */
	public DataManager putInt(String key, int value) {
		mData.get(key).putInt(this, mBundle, value);
		return this;
	}
	/** Store an int value */
	public DataManager putInt(Datum datum, int value) {
		datum.putInt(this, mBundle, value);
		return this;
	}

	/** Get a long value */
	public long getLong(long key) {
		return mData.get(key).getLong(this, mBundle);
	}
	/** Store a long value */
	public DataManager putLong(String key, long value) {
		mData.get(key).putLong(this, mBundle, value);
		return this;
	}
	/** Store a long value */
	public DataManager putLong(Datum datum, long value) {
		datum.putLong(this, mBundle, value);
		return this;
	}

	/** Get a String value */
	public String getString(String key) {
		return mData.get(key).getString(this, mBundle);
	}
	/** Get a String value */
	public String getString(Datum datum) {
		return datum.getString(this, mBundle);
	}
	/** Store a String value */
	public DataManager putString(String key, String value) {
		mData.get(key).putString(this, mBundle, value);
		return this;
	}
	public DataManager putString(Datum datum, String value) {
		datum.putString(this, mBundle, value);
		return this;
	}

	/**
	 * Store all passed values in our collection.
	 * We do the laborious method here to allow Accessors to do their thing.
	 *
	 * @param src
	 * @return
	 */
	public DataManager putAll(Bundle src) {
		for(String key: src.keySet()) {
			Object o = src.get(key);
			if (o instanceof String) {
				putString(key, (String)o);
			} else if (o instanceof Integer) {
				putInt(key, (Integer)o);
			} else if (o instanceof Long) {
				putLong(key, (Long)o);
			} else if (o instanceof Double) {
				putDouble(key, (Double)o);
			} else if (o instanceof Float) {
				putFloat(key, (Float)o);
			} else if (o instanceof Serializable) {
				this.putSerializable(key, (Serializable)o);
			} else {
				// THIS IS NOT IDEAL!
				if (o != null) {
					putString(key, o.toString());					
				} else {
					System.out.println("NULL value for key '" + key + "'");
				}
			}
		}
		return this;
	}

	/**
	 * Store the contents of the passed cursor
	 *
	 * @param cursor
	 */
	public void putAll(Cursor cursor) {
        putAll_Api11(cursor);
    }

    /**
     * Store the contents of the passed cursor
     * For API after 11, store the correct type
     */
	private void putAll_Api11(Cursor cursor) {
		cursor.moveToFirst();

		for(int i = 0; i < cursor.getColumnCount(); i++) {
			final String name = cursor.getColumnName(i);
			switch(cursor.getType(i)) {
			case SQLiteCursor.FIELD_TYPE_STRING:
				putString(name, cursor.getString(i));
				break;
			case SQLiteCursor.FIELD_TYPE_INTEGER:
				putLong(name, cursor.getLong(i));
				break;
			case SQLiteCursor.FIELD_TYPE_FLOAT:
				putDouble(name, cursor.getDouble(i));
				break;
			case SQLiteCursor.FIELD_TYPE_NULL:
				break;
			case SQLiteCursor.FIELD_TYPE_BLOB:
				throw new RuntimeException("Unsupported column type: 'blob'");
			default:
				throw new RuntimeException("Unsupported column type: " + cursor.getType(i));
			}
		}
	}

	/**
	 * Get the serializable object from the collection.
	 * We currently do not use a Datum for special access.
	 * 
	 * @param key	Key of object
	 * 
	 * @return		The data
	 */
	public Object getSerializable(String key) {
		return mData.get(key).getSerializable(this, mBundle);
	}

	/**
     * Get the serializable object from the collection.
     * We currently do not use a Datum for special access.
     *
     * @param key   Key of object
     * @param value The serializable object
     */
	public void putSerializable(String key, Serializable value) {
		mData.get(key).putSerializable(this, mBundle, value);
    }

	/**
	 * Loop through and apply validators, generating a Bundle collection as a by-product.
	 * The Bundle collection is then used in cross-validation as a second pass, and finally
	 * passed to each defined cross-validator.
	 * 
	 * @return boolean True if all validation passed.
	 */
	public boolean validate() {

		boolean isOk = true;
		mValidationExceptions.clear();

		// First, just validate individual fields with the cross-val flag set false
		if (!doValidate(false))
			isOk = false;
		
		// Now re-run with cross-val set to true.
		if (!doValidate(true))
			isOk = false;

		// Finally run the local cross-validation
        for (DataCrossValidator v : mCrossValidators) {
            try {
                v.validate(this);
            } catch (ValidatorException e) {
                mValidationExceptions.add(e);
                isOk = false;
            }
        }
		return isOk;
	}

	/**
	 * Internal utility routine to perform one loop validating all fields.
	 * 
	 * @param crossValidating 	Flag indicating if this is a cross validation pass.
	 */
	private boolean doValidate(boolean crossValidating) {
		boolean isOk = true;

		for(String key: mData.keySet()) {
			Datum datum = mData.get(key); 
			if (datum.hasValidator()) {
				try {
					datum.getValidator().validate(this, datum, crossValidating);
				} catch(ValidatorException e) {
					mValidationExceptions.add(e);
					isOk = false;
				}
			}
		}
		return isOk;
	}

	/**
	 * Check if the underlying data contains the specified key.
	 * 
	 * @param key The string key
     */
	public boolean containsKey(String key) {
		Datum datum = mData.get(key);
		if (datum.getAccessor() == null) {
			return mBundle.containsKey(key);
		} else {
			return datum.getAccessor().isPresent(this, datum, mBundle);
		}
	}

	/** 
	 * Remove the specified key from this collection
	 * 
	 * @param key		Key of data to remove.
	 *
     */
	public Datum remove(String key) {
		Datum datum = mData.remove(key);
		mBundle.remove(key);
		return datum;
	}

	/**
	 * Get the current set of data
     */
	public Set<String> keySet() {
		return mData.keySet();
	}

	/**
	 * Retrieve the text message associated with the last validation exception t occur.
	 * 
	 * @return res The resource manager to use when looking up strings.
	 */
	public String getValidationExceptionMessage(android.content.res.Resources res) {
		if (mValidationExceptions.isEmpty())
			return "No error";
		else {
			StringBuilder message = new StringBuilder();
			Iterator<ValidatorException> i = mValidationExceptions.iterator();
			int cnt = 1;
			if (i.hasNext())
				message = new StringBuilder("(" + cnt + ") " + i.next().getFormattedMessage(res));
			while (i.hasNext()) {
				cnt ++;
				message.append(" (").append(cnt).append(") ").append(i.next().getFormattedMessage(res)).append("\n");
			}
			return message.toString();
		}
	}

	/**
	 * Format the passed bundle in a way that is convenient for display
	 * 
	 * @return		Formatted string
	 */
	public String getDataAsString() {
		return Utils.bundleToString(mBundle);
	}
	
	/**
	 * Append a string to a list value in this collection
	 *  
	 * @param key The key to the string
	 * @param value The value to append
	 */
	public void appendOrAdd(String key, String value) {
		String s = Utils.encodeListItem(value, '|');
		if (!containsKey(key) || getString(key).isEmpty()) {
			putString(key, s);
		} else {
			String curr = getString(key);
			putString(key, curr + "|" + s);
		}
	}
}
