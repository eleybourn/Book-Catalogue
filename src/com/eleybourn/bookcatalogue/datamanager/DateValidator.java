package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.R.string;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Validator to apply a default value and validate as a Date
 * 
 * @author Philip Warner
 *
 */
public class DateValidator extends DefaultFieldValidator {

	DateValidator() {
		super();
	}
	DateValidator(String defaultValue) {
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
			java.util.Date d = Utils.parseDate(data.getString(datum));
			data.putString(datum, Utils.toSqlDateTime(d));
		} catch (Exception e) {
			throw new ValidatorException(R.string.vldt_date_expected, new Object[]{datum.getKey()});					
		}
		return;
	}
}