/*
 * @copyright 2010 Evan Leybourn
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

package com.eleybourn.bookcatalogue;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Objects;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class MainAbout extends BookCatalogueActivity {

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.main_about);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            setSupportActionBar(topAppBar);
            topAppBar.setTitle(R.string.app_name);
            topAppBar.setNavigationOnClickListener((View.OnClickListener) this); {
                // Define your back action here
                getOnBackPressedDispatcher().onBackPressed();
            }

            setupContributors();
			setupAdmin();
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * We build the list of contributors separately from the credits text so that we are not updating
	 * all translations whenever a new translator contributes.
	 */
	private void setupContributors() {
		// The view
		final TextView thanks = findViewById(R.id.thanks);
		// Load the list stored in contributors.xml and join all but the last name with commas.
		final String[] contributorsList = getResources().getStringArray(R.array.contributors_list);
		final StringBuilder contributorsStr = new StringBuilder();
		final int lastName = contributorsList.length-1;
		for(int i = 0; i < lastName; i++) {
			if (i > 0) {
				contributorsStr.append(", ");
			}
			contributorsStr.append(contributorsList[i]);
		}
		// Add the last name so we have "a, b, c and e", then construct and set the full blurb.
		final String fullList = getString(R.string.a_and_b, contributorsStr.toString(), contributorsList[lastName]);
		final String thanksStr = getString(R.string.para_translators, fullList);
		thanks.setText(thanksStr);
	}
	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
	}

	/**
	 * This function builds the Admin page in 4 sections. 
	 * 1. The button to goto the manage bookshelves activity
	 * 2. The button to export the database
	 * 3. The button to import the exported file into the database
	 * 4. The application version and link details
	 * 5. The link to paypal for donation
	 */
	public void setupAdmin() {
		/* Version Number */
		TextView release = findViewById(R.id.version);
		PackageManager manager = this.getPackageManager();
		PackageInfo info;
		try {
			info = manager.getPackageInfo(this.getPackageName(), 0);
			String versionName = info.versionName;
			release.setText(versionName);
		} catch (NameNotFoundException e) {
			Logger.logError(e);
		}
		final Resources res = this.getResources();
		TextView webpage = findViewById(R.id.webpage);
		webpage.setOnClickListener(v -> {
            Intent loadWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.url_webpage)));
            startActivity(loadWeb);
        });
		TextView sourcecode = findViewById(R.id.sourceCode);
		sourcecode.setOnClickListener(v -> {
            Intent loadWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.url_sourcecode)));
            startActivity(loadWeb);
        });
		TextView contact1 = findViewById(R.id.contact1);
		contact1.setOnClickListener(v -> sendContactEmail(R.string.contact1));
		TextView contact2 = findViewById(R.id.contact2);
		contact2.setOnClickListener(v -> sendContactEmail(R.string.contact2));

	}

	private void sendContactEmail(int stringId) {
		try {
			Intent msg = new Intent(Intent.ACTION_SEND);
			msg.setType("text/plain");
			msg.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(stringId)});
			String subject = "[" + getString(R.string.app_name) + "] ";
			msg.putExtra(Intent.EXTRA_SUBJECT, subject);
			MainAbout.this.startActivity(Intent.createChooser(msg, "Send email..."));
		} catch (ActivityNotFoundException e) {
			Logger.logError(e);
		}		
	}
}