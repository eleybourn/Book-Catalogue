package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.R.string;

/**
 * Validator to apply a default value and validate as Float
 * 
 * @author Philip Warner
 *
 */
public class FloatValidator extends DefaultFieldValidator {
	FloatValidator() {
		super();
	}
	FloatValidator(String defaultValue) {
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
			Float v;
			Object o = data.get(datum);
			if (o instanceof Float) {
				v = (Float)o;
			} else if (o instanceof Double) {
				v = ((Double)o).floatValue();
			} else if (o instanceof Integer) {
				v = ((Integer)o).floatValue();
			} else {
				v = Float.parseFloat(o.toString());					
			}
			data.putFloat(datum, v);
			return;
		} catch (Exception e) {
			throw new ValidatorException(R.string.vldt_real_expected, new Object[]{datum.getKey()});
		}
	}
}