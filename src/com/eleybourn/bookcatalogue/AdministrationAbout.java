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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class AdministrationAbout extends Activity {

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.administration_about);
			setupAdmin();
		} catch (Exception e) {
			Logger.logError(e);
		}
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
		TextView contact = (TextView) findViewById(R.id.contact);
		contact.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					Intent msg = new Intent(Intent.ACTION_SEND);
					msg.setType("text/plain");
					msg.putExtra(Intent.EXTRA_EMAIL, new String[]{res.getString(R.string.contact)});
					String subject = "[" + res.getString(R.string.app_name) + "] ";
					msg.putExtra(Intent.EXTRA_SUBJECT, subject);
					AdministrationAbout.this.startActivity(Intent.createChooser(msg, "Send email..."));
					//startActivity(msg);
				} catch (ActivityNotFoundException e) {
					Logger.logError(e);
				}
				return;
			}
		});
	}

}