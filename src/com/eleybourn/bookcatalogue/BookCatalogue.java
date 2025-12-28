package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;

/**
 * This is a placeholder class to deal with the surprising number of old shortcuts that
 * have not been updated from version 3.x.
 * This activity just forwards to the StartupActivity.
 * In retrospect, this should have been done in the first place, but since we now
 * have users with shortcuts that point to 'StartupActivity', it is too late to fix.
 * 
 * @author Philip Warner
 */
public class BookCatalogue extends BookCatalogueActivity {

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent i = new Intent(this, StartupActivity.class);
		startActivity(i);
		finish();
	}

}
