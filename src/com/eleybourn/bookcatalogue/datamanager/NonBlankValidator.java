package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.R.string;

/**
 * Validator to require a non-blank field
 * 
 * @author Philip Warner
 *
 */
public class NonBlankValidator implements DataValidator {

	@Override
	public void validate(DataManager data, Datum datum, boolean crossValidating) {
		if (!datum.isVisible()) {
			// No validation required for invisible fields
			return;
		}
		if (crossValidating)
			return;

		try {
			String v = data.getString(datum).trim();
			if (v.length() > 0) {
				return;
			} else {
				throw new ValidatorException(R.string.vldt_nonblank_required, new Object[]{datum.getKey()});
			}
		} catch (Exception e) {
			throw new ValidatorException(R.string.vldt_nonblank_required, new Object[]{datum.getKey()});
		}
	}
}