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
import android.widget.Button;
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
	private CatalogueDBAdapter mDbHelper;

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			// Needed for sending debug info...
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
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
			
			Button sendInfo = (Button) findViewById(R.id.send_info);
			sendInfo.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Utils.sendDebugInfo(Help.this, mDbHelper);
				}
			});
			
			setupCleanupButton();
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	private void setupCleanupButton() {
		try {
			Button cleanupBtn = (Button) findViewById(R.id.cleanup_button);
			TextView cleanupTxt = (TextView) findViewById(R.id.cleanup_text);

			cleanupBtn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Utils.cleanupFiles();
					setupCleanupButton();
				}
			});


			float space = Utils.cleanupFilesTotalSize();
			if (space == 0) {
				cleanupBtn.setVisibility(View.GONE);
				cleanupTxt.setVisibility(View.GONE);
			} else {
				cleanupBtn.setVisibility(View.VISIBLE);
				cleanupTxt.setVisibility(View.VISIBLE);
				String fmt = getString(R.string.cleanup_files_text);
				String sizeFmt;
				String msg;
				if (space < 3072) { // Show 'bytes' if < 3k
					sizeFmt = getString(R.string.bytes);
				} else if (space < 250 * 1024) { // Show Kb if less than 250kB
					sizeFmt = getString(R.string.kilobytes);
					space = space / 1024;
				} else { // Show MB otherwise...
					sizeFmt = getString(R.string.megabytes);
					space = space / (1024 * 1024);
				}
				msg = String.format(fmt, String.format(sizeFmt,space));
				cleanupTxt.setText(msg);

			}			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		setupCleanupButton();		
	}

}