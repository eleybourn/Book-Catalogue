package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

public interface DataAccessor {
	public Object get(DataManager data, Datum datum, Bundle rawData);
	public void set(DataManager data, Datum datum, Bundle rawData, Object value);
	public boolean isPresent(DataManager data, Datum datum, Bundle rawData);
}