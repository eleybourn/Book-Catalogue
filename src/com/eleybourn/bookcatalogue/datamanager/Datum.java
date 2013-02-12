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

import android.os.Bundle;

import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to manage storage and retrieval of a piece of data from a bundle as well as
 * ancillary details such as visibility.
 * 
 * @author pjw
 */
public class Datum {
	/** True if data should be visible */
	private boolean mIsVisible = true;
	/** Validator for this Datum */
	private DataValidator mValidator = null;
	/** Accessor for this Datum (eg. the datum might be a bit in a mask field, or a composite read-only value */
	private DataAccessor mAccessor = null;
	/** Key of this datum */
	private final String mKey;

	/**
	 * Constructor
	 * 
	 * @param key			Key of this datum
	 * @param validator		Validator for this Datum
	 * @param visible		True if data should be visible
	 */
	public Datum(String key, DataValidator validator, boolean visible) {
		mKey = key;
		mValidator = validator;
		mIsVisible = visible;
	}
	
	/** Accessor */
	public String getKey() {
		return mKey;
	}

	/** Accessor */
	public boolean isVisible() {
		return mIsVisible;
	}
	/** Accessor */
	public Datum setVisible(boolean isVisible) {
		mIsVisible = isVisible;
		return this;
	}

	/** Accessor */
	public DataValidator getValidator() {
		return mValidator;
	}
	/** Accessor */
	public boolean hasValidator() {
		return mValidator != null;
	}
	/**
	 *  Accessor. Protected against being set twice.
	 */
	public Datum setValidator(DataValidator validator) {
		if (mValidator != null && validator != mValidator)
			throw new RuntimeException("Datum '" + mKey + "' already has a validator");
		mValidator = validator;
		return this;
	}

	/** Accessor */
	public DataAccessor getAccessor() {
		return mAccessor;
	}
	/**
	 *  Accessor. Protected against being set twice.
	 */
	public Datum setAccessor(DataAccessor accessor) {
		if (mAccessor != null && accessor != mAccessor)
			throw new RuntimeException("Datum '" + mKey + "' already has an Accessor");
		mAccessor = accessor;
		return this;
	}

	/**
	 * Get the raw Object for this Datum
	 * 
	 * @param data		Parent DataManager
	 * @param bundle	Raw data bundle
	 * 
	 * @return			The object data
	 */
	public Object get(DataManager data, Bundle bundle) {
		if (mAccessor == null) {
			return bundle.get(mKey);
		} else {
			return mAccessor.get(data, this, bundle);
		}
	}

	/**
	 * Retrieve the data from the DataManager, translating and using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			Value of the data
	 */
	public boolean getBoolean(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.getBoolean(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		try {
			return (Boolean) o;
		} catch (ClassCastException e) {
			return Utils.objectToBoolean(o);
		}
	}
	/**
	 * Store the data in the DataManager, using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			This Datum, for chaining
	 */
	public Datum putBoolean(DataManager data, Bundle bundle, boolean value) {
		if (mAccessor == null) {
			bundle.putBoolean(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	/**
	 * Retrieve the data from the DataManager, translating and using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			Value of the data
	 */
	public int getInt(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return (int) objectToLong(o);
	}
	/**
	 * Store the data in the DataManager, using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			This Datum, for chaining
	 */
	public Datum putInt(DataManager data, Bundle bundle, int value) {
		if (mAccessor == null) {
			bundle.putInt(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	/**
	 * Retrieve the data from the DataManager, translating and using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			Value of the data
	 */
	public long getLong(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		
		return objectToLong(o);
	}
	/**
	 * Store the data in the DataManager, using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			This Datum, for chaining
	 */
	public Datum putLong(DataManager data, Bundle bundle, long value) {
		if (mAccessor == null) {
			bundle.putLong(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	/**
	 * Retrieve the data from the DataManager, translating and using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			Value of the data
	 */
	public double getDouble(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return objectToDouble(o);
	}
	/**
	 * Store the data in the DataManager, using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			This Datum, for chaining
	 */
	public Datum putDouble(DataManager data, Bundle bundle, double value) {
		if (mAccessor == null) {
			bundle.putDouble(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	/**
	 * Retrieve the data from the DataManager, translating and using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			Value of the data
	 */
	public float getFloat(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return (float) objectToDouble(o);
	}
	/**
	 * Store the data in the DataManager, using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			This Datum, for chaining
	 */
	public Datum putFloat(DataManager data, Bundle bundle, float value) {
		if (mAccessor == null) {
			bundle.putFloat(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	/**
	 * Retrieve the data from the DataManager, translating and using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			Value of the data
	 */
	public String getString(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return objectToString(o);
	}
	/**
	 * Store the data in the DataManager, using Accessor as necessary.
	 * 
	 * @param data		Parent collection
	 * @param bundle	Raw data
	 * 
	 * @return			This Datum, for chaining
	 */
	public Datum putString(DataManager data, Bundle bundle, String value) {
		if (mAccessor == null) {
			bundle.putString(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	/**
	 * Get the serializable object from the collection.
	 * We currently do not use a Datum for special access.
	 * TODO: Consider how to use an accessor
	 * 
	 * @param data		Parent DataManager
	 * @param bundle	Raw data Bundle
	 * 
	 * @return		The data
	 */
	public Serializable getSerializable(DataManager data, Bundle bundle) {
		if (mAccessor == null) {
			return bundle.getSerializable(mKey);			
		} else {
			throw new RuntimeException("Accessor not supported for serializable objects");
		}
	}

	/**
	 * Set the serializable object in the collection.
	 * We currently do not use a Datum for special access.
	 * TODO: Consider how to use an accessor
	 * 
	 * @param data		Parent DataManager
	 * @param bundle	Raw data Bundle
	 * @param value		The serializable object
	 * 
	 * @return		The data manager for chaining
	 */
	public Datum putSerializable(DataManager data, Bundle bundle, Serializable value) {
		if (mAccessor == null) {
			bundle.putSerializable(mKey, value);			
		} else {
			throw new RuntimeException("Accessor not supported for serializable objects");
		}
		return this;
	}

	/**
	 * Translate the passed object to a String value
	 * 
	 * @param o		Object
	 * 
	 * @return		Resulting value
	 */
	public static String objectToString(Object o) {
		if (o == null)
			return "";
		try {
			return (String) o;
		} catch (ClassCastException e) {
			return o.toString();
		}	
	}

	/**
	 * Translate the passed object to a Long value
	 * 
	 * @param o		Object
	 * 
	 * @return		Resulting value
	 */
	public static long objectToLong(Object o) {
		if (o == null)
			return 0;
		try {
			return (Long) o;
		} catch (ClassCastException e) {
			final String s = o.toString();
			if (s.equals(""))
				return 0;
			else
				return Long.parseLong(s);
		}	
	}
	
	/**
	 * Translate the passed object to a Double value
	 * 
	 * @param o		Object
	 * 
	 * @return		Resulting value
	 */
	public static double objectToDouble(Object o) {
		if (o == null)
			return 0;
		try {
			return (Double) o;
		} catch (ClassCastException e) {
			final String s = o.toString();
			if (s.equals(""))
				return 0;
			else
				return Double.parseDouble(s);
		}	
	}
}
