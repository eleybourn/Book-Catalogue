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

/**
 * Interface for all field-level validators. Each field validator is called twice; once
 * with the crossValidating flag set to false, then, if all validations were successful,
 * they are all called a second time with the flag set to true. This is an alternate
 * method of applying cross-validation.
 * 
 * @author Philip Warner
 */
public interface DataValidator {
	/**
	 * Validation method. Must throw a ValidatorException if validation fails.
	 * 
	 * @param data				The DataManager object containing the Datum being validated
	 * @param datum				The Datum to validate
	 * @param crossValidating	Flag indicating if this is the cross-validation pass.
	 * 
	 * @throws ValidatorException	For any validation failure.
	 */
	void validate(DataManager data, Datum datum, boolean crossValidating);
}