package com.eleybourn.bookcatalogue.datamanager;

import com.eleybourn.bookcatalogue.R;

/**
 * Validator to apply a default String value to empty fields.
 * 
 * @author Philip Warner
 *
 */
public class DefaultFieldValidator implements DataValidator {
	protected String mDefault;
	/**
	 * Allow for no default value.
	 */
	DefaultFieldValidator() {
		this("");
	}
	/**
	 * Constructor with default value
	 * @param defaultValue Default to apply
	 */
	DefaultFieldValidator(String defaultValue) {
		mDefault = defaultValue;
	}

	@Override
	public void validate(DataManager data, Datum datum, boolean crossValidating) {
		if (! datum.isVisible()) {
			// No validation required for invisible fields
			return;
		}
		Object value = data.get(datum);
		// Default validator does not cross-validate
		if (crossValidating)
			return;

		try {
			if (value.toString().trim().equals("")) {
				data.putString(datum, mDefault);
			}
			return;
		} catch (Exception e) {
			throw new ValidatorException(R.string.vldt_unable_to_get_value, new Object[]{datum.getKey()});
		}
	}
}