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
			
			TextView webinstructions = (TextView) findViewById(R.id.helpinstructions);
			webinstructions.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.helppage)));
					startActivity(loadweb); 
					return;
				}
			});
			
			TextView webpage = (TextView) findViewById(R.id.helppage);
			webpage.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.helppage)));
					startActivity(loadweb); 
					return;
				}
			});
			
		} catch (Exception e) {
			//Log.e("Book Catalogue", "Unknown Exception - Help onCreate - " + e.getMessage() );
		}
	}

}