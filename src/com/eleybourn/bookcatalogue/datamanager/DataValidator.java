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