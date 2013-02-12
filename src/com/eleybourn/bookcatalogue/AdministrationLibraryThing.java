/*
 * @copyright 2012 Philip Warner
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

import java.io.File;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * 
 * This is the Administration page. It contains details about LibraryThing links
 * and how to register for a developer key. At a later data we could also include
 * the user key for maintaining user-specific LibraryThing data.
 * 
 * @author Philip Warner
 */
public class AdministrationLibraryThing extends BookCatalogueActivity {

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.administration_librarything);
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

	public void setupAdmin() {
		/* LT Reg Link */
		TextView register = (TextView) findViewById(R.id.register_url);
		register.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.librarything.com/"));
				startActivity(loadweb); 
				return;
			}
		});
		
		/* DevKey Link */
		TextView devkeyLink = (TextView) findViewById(R.id.devkey_url);
		devkeyLink.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.librarything.com/services/keys.php"));
				startActivity(loadweb); 
				return;
			}
		});

		EditText devkeyView = (EditText) findViewById(R.id.devkey);
		SharedPreferences prefs = getSharedPreferences("bookCatalogue", android.content.Context.MODE_PRIVATE);
		devkeyView.setText(prefs.getString(LibraryThingManager.LT_DEVKEY_PREF_NAME, ""));
		
		/* Save Button */
		Button btn = (Button) findViewById(R.id.confirm);
		btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText devkeyView = (EditText) findViewById(R.id.devkey);
				String devkey = devkeyView.getText().toString();
				SharedPreferences prefs = getSharedPreferences("bookCatalogue", android.content.Context.MODE_PRIVATE);
				SharedPreferences.Editor ed = prefs.edit();
				ed.putString(LibraryThingManager.LT_DEVKEY_PREF_NAME, devkey);
				ed.commit();
				
				if (devkey.length() > 0) {
					//TEST Library Thing
					Bundle tmp = new Bundle(); 
					LibraryThingManager ltm = new LibraryThingManager(AdministrationLibraryThing.this);
					String filename = ltm.getCoverImage("0451451783", tmp, LibraryThingManager.ImageSizes.SMALL);
					File filetmp = new File(filename);
					long length = filetmp.length();
					if (length < 100) {
						Toast.makeText(AdministrationLibraryThing.this, R.string.incorrect_key, Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(AdministrationLibraryThing.this, R.string.correct_key, Toast.LENGTH_LONG).show();
					}
					filetmp.delete();
				}
				return;
			}
		});

		/* Reset Button */
		Button resetBtn = (Button) findViewById(R.id.reset_messages);
		resetBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences prefs = getSharedPreferences("bookCatalogue", android.content.Context.MODE_PRIVATE);
				SharedPreferences.Editor ed = prefs.edit();
				for( String key : prefs.getAll().keySet()) {
					if (key.toLowerCase().startsWith(LibraryThingManager.LT_HIDE_ALERT_PREF_NAME.toLowerCase())) 
						ed.remove(key);
				}
				ed.commit();
				return;
			}
		});
		
	}

}
