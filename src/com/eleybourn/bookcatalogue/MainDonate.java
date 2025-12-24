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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class MainDonate extends BookCatalogueActivity {

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
			setContentView(R.layout.main_donate);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            topAppBar.setTitle(R.string.app_name);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

			setupAdmin();
		} catch (Exception e) {
			Logger.logError(e);
		}
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
		OnClickListener payPalClick = v -> {
            Intent loadWeb = new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/leybourn?locale.x=en_AU&country.x=AU"));
            startActivity(loadWeb);
        };

		/* Donation Link */
		View donate = findViewById(R.id.donateUrl);
		donate.setOnClickListener(payPalClick);
		View donate2 = findViewById(R.id.donateUrlImage);
		donate2.setOnClickListener(payPalClick);

		/* Donation Link */
		View amazon = findViewById(R.id.amazonUrl);
		amazon.setOnClickListener(v -> {
            Intent loadWeb = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.amazon.com/gp/registry/wishlist/2A2E48ONH64HM?tag=bookcatalogue-20"));
            startActivity(loadWeb);
        });
		
	}

}