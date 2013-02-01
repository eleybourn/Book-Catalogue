package com.eleybourn.bookcatalogue.datamanager;

import java.util.Iterator;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;

/**
 * 'Meta' Validator to evaluate a list of validators; ALL validators must be true.
 * 
 * @author Philip Warner
 *
 */
public class AndValidator extends MetaValidator implements DataValidator {
	// Java likes this
	public static final long serialVersionUID = 1L;

	// Constructors
	AndValidator(DataValidator v1) { super(v1); }
	AndValidator(DataValidator v1, DataValidator v2) { super(v1, v2); }
	AndValidator(DataValidator v1, DataValidator v2, DataValidator v3) { super(v1, v2, v3); }

	@Override
	public void validate(DataManager data, Datum datum, boolean crossValidating) {
		Iterator<DataValidator> i = this.iterator();
		while (i.hasNext()) {
			DataValidator v = i.next();
			// Only set the Bundle for the last in the list
			v.validate(data, datum, crossValidating);
		}
		return;
	}
}