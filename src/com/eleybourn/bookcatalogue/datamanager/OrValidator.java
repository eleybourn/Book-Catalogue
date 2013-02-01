package com.eleybourn.bookcatalogue.datamanager;

import java.util.Iterator;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.R.string;

/**
 * 'Meta' Validator to evaluate a list of validators; any one being true is OK.
 * 
 * @author Philip Warner
 *
 */
public class OrValidator extends MetaValidator implements DataValidator {

	// Java likes this
	public static final long serialVersionUID = 1L;

	// Constructors
	OrValidator(DataValidator v1) { super(v1); }
	OrValidator(DataValidator v1, DataValidator v2) { super(v1, v2); }
	OrValidator(DataValidator v1, DataValidator v2, DataValidator v3) { super(v1, v2, v3); }

	@Override
	public void validate(DataManager data, Datum datum, boolean crossValidating) {
		Iterator<DataValidator> i = this.iterator();
		ValidatorException lastException = null;
		while (i.hasNext()) {
			DataValidator v = i.next();
			try {
				v.validate(data, datum, crossValidating);
				return;
			} catch (ValidatorException e) {
				// Do nothing...try next validator, but keep it for later
				lastException = e;
			} catch (Exception e) {
				// Do nothing...try next validator
			}
		}
		if (lastException != null)
			throw lastException;
		else
			throw new ValidatorException(R.string.vldt_failed, new Object[]{datum.getKey()});
	}
}