package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.R.string;

/**
 * Validator to apply a default value and validate as integer.
 * 
 * @author Philip Warner
 *
 */
public class IntegerValidator extends DefaultFieldValidator {
	IntegerValidator() {
		super();
	}
	IntegerValidator(String defaultValue) {
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

		Object o = null;
		try {
			o = data.get(datum);
			Integer v;
			if (o instanceof Integer) {
				v = (Integer)o;
			} else {
				v = Integer.parseInt(o.toString());
			}
			data.putInt(datum, v);
			return;
		} catch (Exception e) {
			throw new ValidatorException(R.string.vldt_integer_expected, new Object[]{datum.getKey()});
		}
	}
}