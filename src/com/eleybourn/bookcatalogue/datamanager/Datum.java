package com.eleybourn.bookcatalogue.datamanager;

import com.eleybourn.bookcatalogue.utils.Utils;

import android.os.Bundle;

public class Datum {
	private boolean mIsVisible = true;
	private DataValidator mValidator = null;
	private DataAccessor mAccessor = null;
	private final String mKey;
	
	public Datum(String key, DataValidator validator, boolean visible) {
		mKey = key;
		mValidator = validator;
		mIsVisible = visible;
	}
	
	public String getKey() {
		return mKey;
	}

	public boolean isVisible() {
		return mIsVisible;
	}
	public Datum setVisible(boolean isVisible) {
		mIsVisible = isVisible;
		return this;
	}

	public DataValidator getValidator() {
		return mValidator;
	}
	public boolean hasValidator() {
		return mValidator != null;
	}
	public Datum setValidator(DataValidator validator) {
		if (mValidator != null && validator != mValidator)
			throw new RuntimeException("Datum '" + mKey + "' already has a validator");
		mValidator = validator;
		return this;
	}

	public DataAccessor getAccessor() {
		return mAccessor;
	}
	public Datum setAccessor(DataAccessor accessor) {
		if (mAccessor != null && accessor != mAccessor)
			throw new RuntimeException("Datum '" + mKey + "' already has an Accessor");
		mAccessor = accessor;
		return this;
	}

	public Object get(DataManager data, Bundle bundle) {
		if (mAccessor == null) {
			return bundle.get(mKey);
		} else {
			return mAccessor.get(data, this, bundle);
		}
	}
//	public Datum put(DataManager data, Bundle bundle, Object value) {
//		if (mAccessor == null) {
//			bundle.put
//			bundle.put(mKey, value);
//		} else {
//			mAccessor.set(data, this, bundle, value);
//		}
//		return this;
//	}

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
			return Utils.stringToBoolean(o.toString());
		}
	}
	public Datum putBoolean(DataManager data, Bundle bundle, boolean value) {
		if (mAccessor == null) {
			bundle.putBoolean(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	public int getInt(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return (int) objectToLong(o);
	}
	public Datum putInt(DataManager data, Bundle bundle, int value) {
		if (mAccessor == null) {
			bundle.putInt(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	public long getLong(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		
		return objectToLong(o);
	}
	public Datum putLong(DataManager data, Bundle bundle, long value) {
		if (mAccessor == null) {
			bundle.putLong(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	public double getDouble(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return objectToDouble(o);
	}
	public Datum putDouble(DataManager data, Bundle bundle, double value) {
		if (mAccessor == null) {
			bundle.putDouble(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	public float getFloat(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return (float) objectToDouble(o);
	}
	public Datum putFloat(DataManager data, Bundle bundle, float value) {
		if (mAccessor == null) {
			bundle.putFloat(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	public String getString(DataManager data, Bundle bundle) {
		Object o;
		if (mAccessor == null) {
			o = bundle.get(mKey);
		} else {
			o = mAccessor.get(data, this, bundle);
		}
		return objectToString(o);
	}
	public Datum putString(DataManager data, Bundle bundle, String value) {
		if (mAccessor == null) {
			bundle.putString(mKey, value);
		} else {
			mAccessor.set(data, this, bundle, value);
		}
		return this;
	}

	public static String objectToString(Object o) {
		if (o == null)
			return "";
		try {
			return (String) o;
		} catch (ClassCastException e) {
			return o.toString();
		}	
	}
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
