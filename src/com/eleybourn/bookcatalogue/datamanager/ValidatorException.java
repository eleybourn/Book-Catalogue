package com.eleybourn.bookcatalogue.datamanager;

/**
 * Exception class for all validation errors. String ID and args are stored
 * for later retrieval.
 * 
 * @author Philip Warner
 *
 */
public class ValidatorException extends RuntimeException {
	// Java likes this
	public static final long serialVersionUID = 1L;
	// String ID of resource string
	private int mStringId;
	// Args to pass to format function
	private Object[] mArgs;
	// Constructor
	public ValidatorException(int stringId, Object[] args) {
		mStringId = stringId;
		mArgs = args;
	}
	public String getFormattedMessage(android.content.res.Resources res) {
		String s = res.getString(mStringId);
		return String.format(s, mArgs);
	}
}