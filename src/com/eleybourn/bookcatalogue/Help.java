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
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class Help extends Activity {
	public Resources res;

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.help);
			res = getResources();
			
			ScrollView container = (ScrollView) findViewById(R.id.help_container);
			int Rid = 0;
			if (Rid > 0) {
				LinearLayout ll = (LinearLayout) findViewById(Rid);
				container.requestChildFocus(ll, ll);
			}
			
			TextView nutshell = (TextView) findViewById(R.id.nutshell_link);
			nutshell.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					String link = res.getString(R.string.help_text_nutshell2);
					Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
					startActivity(loadweb); 
					return;
				}
			});
			
			TextView zxing = (TextView) findViewById(R.id.barcode_link);
			zxing.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.zxing.client.android")); 
					startActivity(marketIntent);
					return;
				}
			});
			
			TextView isbnwiki = (TextView) findViewById(R.id.barcode_link2);
			isbnwiki.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					String link = res.getString(R.string.help_text_addbooksbarcode4a);
					Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
					startActivity(loadweb); 
					return;
				}
			});
			
			/* Donation Link */
			ImageView donate = (ImageView) findViewById(R.id.donate_url);
			donate.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=94US4N9MQGDMY&lc=AU&currency_code=AUD&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted"));
					startActivity(loadweb); 
					return;
				}
			});
			
			/* Donation Link */
			TextView amazon = (TextView) findViewById(R.id.amazon_url);
			amazon.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.amazon.com/registry/wishlist/27ISBYRXBGXO3/"));
					startActivity(loadweb); 
					return;
				}
			});
		} catch (Exception e) {
			//Log.e("Book Catalogue", "Unknown Exception - BC onCreate - " + e.getMessage() );
		}
	}

}