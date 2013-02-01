package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.R.string;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Validator to apply a default value and validate as Boolean
 * 
 * @author Philip Warner
 *
 */
public class BooleanValidator extends DefaultFieldValidator {
	BooleanValidator() {
		super();
	}
	BooleanValidator(String defaultValue) {
		super(defaultValue);
	}
	@Override
	public void validate(DataManager data, Datum datum, boolean crossValidating) {
		if (!datum.isVisible()) {
			// No validation required for invisible fields
			return;
		}
		if (crossValidating)
			return;

		// Will throw on failure...
		super.validate(data, datum, crossValidating);

		try {
			Object o = data.get(datum);
			Boolean v;
			if (o instanceof Boolean) {
				v = (Boolean)o;
			} else if (o instanceof Integer) {
				v = (((Integer)o) != 0);
			} else {
				String s = o.toString();
				v = Utils.stringToBoolean(s);
			}
			data.putBoolean(datum, v);
			return;
		} catch (Exception e) {
			throw new ValidatorException(R.string.vldt_boolean_expected, new Object[]{datum.getKey()});
		}
	}
}