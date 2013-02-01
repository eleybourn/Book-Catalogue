package com.eleybourn.bookcatalogue.datamanager;

import java.util.ArrayList;

/**
 * Base class for validators that operate on a list of other validators.
 * 
 * @author Philip Warner
 */
abstract public class MetaValidator extends ArrayList<DataValidator> implements DataValidator {
	// Java likes this
	public static final long serialVersionUID = 1L;

	/**
	 * Constructor taking one 'sub' Validator.
	 * 
	 * @param v1	Validator to check
	 */
	MetaValidator(DataValidator v1) {
		this.add(v1);
	}

	/**
	 * Constructor taking two 'sub' Validator.
	 * 
	 * @param v1	Validator to check
	 * @param v2	Validator to check
	 */
	MetaValidator(DataValidator v1, DataValidator v2) {
		this.add(v1);
		this.add(v2);
	}

	/**
	 * Constructor taking three 'sub' Validator.
	 * 
	 * @param v1	Validator to check
	 * @param v2	Validator to check
	 * @param v3	Validator to check
	 */
	MetaValidator(DataValidator v1, DataValidator v2,  DataValidator v3) {
		this.add(v1);
		this.add(v2);
		this.add(v3);
	}
}