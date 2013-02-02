package com.eleybourn.bookcatalogue.datamanager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import android.database.Cursor;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.utils.Utils;

public class DataManager {
	// Generic validators; if field-specific defaults are needed, create a new one.
	protected static DataValidator integerValidator = new IntegerValidator("0");
	protected static DataValidator nonBlankValidator = new NonBlankValidator();
	protected static DataValidator blankOrIntegerValidator = new OrValidator(new BlankValidator(),
			new IntegerValidator("0"));
	protected static DataValidator blankOrFloatValidator = new OrValidator(new BlankValidator(),
			new FloatValidator("0.00"));
	// FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(),
	// new Fields.DateValidator());

	protected final Bundle mBundle = new Bundle();
	private final DatumHash mData = new DatumHash();
	
	// The last validator exception caught by this object
	private ArrayList<ValidatorException> mValidationExceptions = new ArrayList<ValidatorException>();
	// A list of cross-validators to apply if all fields pass simple validation.
	private ArrayList<DataCrossValidator> mCrossValidators = new ArrayList<DataCrossValidator>();

	public DataManager clear() {
		mBundle.clear();
		mData.clear();
		mValidationExceptions.clear();
		mCrossValidators.clear();
		return this;
	}

	private static class DatumHash extends Hashtable<String,Datum> {
		private static final long serialVersionUID = -650159534364183779L;

		public Datum addOrGet(String key) {
			return this.get(key);
//			Datum datum = this.get(key);
//			if (datum == null) {
//				datum = new Datum(key, null, true);
//				this.put(key, datum);
//			}
//			return datum;
		}
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
//	private static class Validators {
//		private Hashtable<String, DataValidator> mValidators = new Hashtable<String, DataValidator>();
//
//		public Validators add(String key, DataValidator validator) {
//			if (mValidators.containsKey(key))
//				throw new RuntimeException("Key '" + key + "' already present in validators");
//			return put(key, validator);
//		}
//
//		public Validators put(String key, DataValidator validator) {
//			mValidators.put(key, validator);
//			return this;
//		}
//	}
//
//	private static class DataAccessors {
//		private Hashtable<String, DataAccessor> mAccessors = new Hashtable<String, DataAccessor>();
//
//		public DataAccessors add(String key, DataAccessor accessor) {
//			if (mAccessors.containsKey(key))
//				throw new RuntimeException("Key '" + key + "' already present in DataAccessors");
//			return put(key, accessor);
//		}
//
//		public DataAccessors put(String key, DataAccessor accessor) {
//			mAccessors.put(key, accessor);
//			return this;
//		}
//		
//		public DataAccessor get(String key) {
//			return mAccessors.get(key);
//		}
//	}

	public DataManager addValidator(String key, DataValidator validator) {
		mData.addOrGet(key).setValidator(validator);
		return this;
	}

	public DataManager addAccessor(String key, DataAccessor accessor) {
		mData.addOrGet(key).setAccessor(accessor);
		return this;
	}

	public Object get(String key) {
		return get(mData.get(key));
	}
	public Object get(Datum datum) {
		return datum.get(this, mBundle);
	}
	
	public boolean getBoolean(String key) {
		return mData.get(key).getBoolean(this, mBundle);
	}
	public DataManager putBoolean(String key, boolean value) {
		mData.addOrGet(key).putBoolean(this, mBundle, value);
		return this;
	}
	public DataManager putBoolean(Datum datum, boolean value) {
		datum.putBoolean(this, mBundle, value);
		return this;
	}

	public double getDouble(String key) {
		return mData.get(key).getDouble(this, mBundle);
	}
	public DataManager putDouble(String key, double value) {
		mData.addOrGet(key).putDouble(this, mBundle, value);
		return this;
	}
	public DataManager putDouble(Datum datum, double value) {
		datum.putDouble(this, mBundle, value);
		return this;
	}

	public float getFloat(String key) {
		return mData.get(key).getFloat(this, mBundle);
	}
	public DataManager putFloat(String key, float value) {
		mData.addOrGet(key).putFloat(this, mBundle, value);
		return this;
	}
	public DataManager putFloat(Datum datum, float value) {
		datum.putFloat(this, mBundle, value);
		return this;
	}

	public int getInt(String key) {
		return mData.get(key).getInt(this, mBundle);
	}
	public DataManager putInt(String key, int value) {
		mData.addOrGet(key).putInt(this, mBundle, value);
		return this;
	}
	public DataManager putInt(Datum datum, int value) {
		datum.putInt(this, mBundle, value);
		return this;
	}

	public long getLong(long key) {
		return mData.get(key).getLong(this, mBundle);
	}
	public DataManager putLong(String key, long value) {
		mData.addOrGet(key).putLong(this, mBundle, value);
		return this;
	}
	public DataManager putLong(Datum datum, long value) {
		datum.putLong(this, mBundle, value);
		return this;
	}

	public String getString(String key) {
		return mData.get(key).getString(this, mBundle);
	}
	public String getString(Datum datum) {
		return datum.getString(this, mBundle);
	}
	public DataManager putString(String key, String value) {
		mData.addOrGet(key).putString(this, mBundle, value);
		return this;
	}
	public DataManager putString(Datum datum, String value) {
		datum.putString(this, mBundle, value);
		return this;
	}

	/**
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

	public void putAll(Cursor cursor) {
		cursor.moveToFirst();

		for(int i = 0; i < cursor.getColumnCount(); i++) {
			final String name = cursor.getColumnName(i);
			final String value = cursor.getString(i);
			putString(name, value);
		}
	}

	public Object getSerializable(String key) {
		return mBundle.getSerializable(key);
	}

	public DataManager putSerializable(String key, Serializable value) {
		mBundle.putSerializable(key, value);
		return this;
	}

	/**
	 * Loop through and apply validators, generating a Bundle collection as a by-product.
	 * The Bundle collection is then used in cross-validation as a second pass, and finally
	 * passed to each defined cross-validator.
	 * 
	 * @param values The Bundle collection to fill
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
		Iterator<DataCrossValidator> i = mCrossValidators.iterator();
		while (i.hasNext()) {
			DataCrossValidator v = i.next();
			try {
				v.validate(this);
			} catch(ValidatorException e) {
				mValidationExceptions.add(e);
				isOk = false;
			}
		}
		return isOk;
	}

	/**
	 * Internal utility routine to perform one loop validating all fields.
	 * 
	 * @param values 			The Bundle to fill in/use.
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

	public boolean containsKey(String key) {
		Datum datum = mData.get(key);
		if (datum.getAccessor() == null) {
			return mBundle.containsKey(key);
		} else {
			return datum.getAccessor().isPresent(this, datum, mBundle);
		}
	}

	public Datum remove(String key) {
		return mData.remove(key);
	}

	public Set<String> keySet() {
		return mData.keySet();
	}

	/**
	 * Retrieve the text message associated with the last validation exception t occur.
	 * 
	 * @return res The resource manager to use when looking up strings.
	 */
	public String getValidationExceptionMessage(android.content.res.Resources res) {
		if (mValidationExceptions.size() == 0)
			return "No error";
		else {
			String message = "";
			Iterator<ValidatorException> i = mValidationExceptions.iterator();
			int cnt = 1;
			if (i.hasNext())
				message = "(" + cnt + ") " + i.next().getFormattedMessage(res);
			while (i.hasNext()) {
				cnt ++;
				message += " (" + cnt + ") " + i.next().getFormattedMessage(res) + "\n";
			}
			return message;
		}
	}

	/**
	 * Format the passed bundle in a way that is convenient for display
	 * 
	 * @param b		Bundle to format
	 * 
	 * @return		Formatted string
	 */
	public String getDataAsString() {
		return Utils.bundleToString(mBundle);
	}
	
	public void appendOrAdd(String key, String value) {
		String s = Utils.encodeListItem(value, '|');
		if (!containsKey(key) || getString(key).length() == 0) {
			putString(key, s);
		} else {
			String curr = getString(key);
			putString(key, curr + "|" + s);
		}
	}
}
