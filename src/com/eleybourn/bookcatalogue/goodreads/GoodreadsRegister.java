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

package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Activity to allow the user to authorize the application to access their goodreads account and
 * to explain goodreads.
 * 
 * @author Philip Warner
 *
 */
public class GoodreadsRegister extends BookCatalogueActivity {

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.goodreads_register);
			setupViews();
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

	public void setupViews() {
		final Resources res = this.getResources();
		/* GR Reg Link */
		TextView register = (TextView) findViewById(R.id.goodreads_url);
		register.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String url = res.getString(R.string.goodreads_url);
				Uri uri = Uri.parse(url);
				Intent loadweb = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(loadweb); 
				return;
			}
		});
		
		/* Auth button */
		Button devkeyLink = (Button) findViewById(R.id.authorize);
		devkeyLink.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				doRequestAuthorization();
				return;
			}
		});

	}

	/**
	 * Called by button click to call static method.
	 */
	private void doRequestAuthorization() {
		requestAuthorization(this);
	}

	/**
	 * Static method to request authorization from goodreads.
	 */
	public static void requestAuthorization(Context context) {
		// ENHANCE: put up a progress dialog...its only one web request, but it can take a few seconds
		GoodreadsManager grMgr = new GoodreadsManager();
		// This next step can take several seconds....
		if (!grMgr.hasValidCredentials()) {
			try {
				grMgr.requestAuthorization(context);
			} catch (NetworkException e) {
				Logger.logError(e, "Error while requesting Goodreads authorization");
				Toast.makeText(context, R.string.goodreads_access_error, Toast.LENGTH_LONG).show();
			}
		} else {
			Toast.makeText(context, R.string.authorize_access_already_auth, Toast.LENGTH_LONG).show();
		}		
	}
}
