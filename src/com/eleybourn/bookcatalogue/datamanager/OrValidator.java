/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.datamanager;

import java.util.Iterator;

import com.eleybourn.bookcatalogue.R;

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