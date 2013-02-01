package com.eleybourn.bookcatalogue.datamanager;

/**
 * Interface for all cross-validators; these are applied after all field-level validators
 * have succeeded.
 * 
 * @author Philip Warner
 *
 */
public interface DataCrossValidator {
	/**
	 * 
	 * @param fields			The Fields object containing the Field being validated
	 * @param values			A Bundle collection with all validated field values.
	 */
	void validate(DataManager data);
}