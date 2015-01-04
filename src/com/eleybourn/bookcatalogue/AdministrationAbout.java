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
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class AdministrationAbout extends BookCatalogueActivity {

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.administration_about);
			setupAdmin();
			Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
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
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);		
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
		TextView release = (TextView) findViewById(R.id.version);
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
		TextView webpage = (TextView) findViewById(R.id.webpage);
		webpage.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.webpage)));
				startActivity(loadweb); 
				return;
			}
		});
		TextView sourcecode = (TextView) findViewById(R.id.sourcecode);
		sourcecode.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.sourcecode)));
				startActivity(loadweb); 
				return;
			}
		});
		TextView contact1 = (TextView) findViewById(R.id.contact1);
		contact1.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendContactEmail(R.string.contact1);
				return;
			}
		});
		TextView contact2 = (TextView) findViewById(R.id.contact2);
		contact2.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendContactEmail(R.string.contact2);
				return;
			}
		});
		
		{
			TextView tv = (TextView) findViewById(R.id.amazon_links_info);
			// Setup the linked HTML
			String text = getString(R.string.hint_amazon_links_blurb, 
					getString(R.string.amazon_books_by_author),
					getString(R.string.amazon_books_in_series),
					getString(R.string.amazon_books_by_author_in_series),
					getString(R.string.app_name));
			tv.setText(Utils.linkifyHtml(text, Linkify.ALL));			
			tv.setMovementMethod(LinkMovementMethod.getInstance());
		}
	}

	private void sendContactEmail(int stringId) {
		try {
			Intent msg = new Intent(Intent.ACTION_SEND);
			msg.setType("text/plain");
			msg.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(stringId)});
			String subject = "[" + getString(R.string.app_name) + "] ";
			msg.putExtra(Intent.EXTRA_SUBJECT, subject);
			AdministrationAbout.this.startActivity(Intent.createChooser(msg, "Send email..."));
		} catch (ActivityNotFoundException e) {
			Logger.logError(e);
		}		
	}
}