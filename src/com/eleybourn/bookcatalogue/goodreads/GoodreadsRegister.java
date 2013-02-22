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
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

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
				requestAuthorizationInBackground(GoodreadsRegister.this);
				return;
			}
		});

		/* Forget credentials */
		boolean hasCred = GoodreadsManager.hasCredentials();
		View blurb = findViewById(R.id.forget_blurb);
		Button blurb_button = (Button)findViewById(R.id.forget);
		if (hasCred) {
			blurb.setVisibility(View.VISIBLE);
			blurb_button.setVisibility(View.VISIBLE);
			blurb_button.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					GoodreadsManager.forgetCredentials();
				}});
		} else {
			blurb.setVisibility(View.GONE);
			blurb_button.setVisibility(View.GONE);
		}
	}

	/**
	 * Called by button click to start a non-UI-thread task to do the work.
	 */
	public static void requestAuthorizationInBackground(final FragmentActivity activity) {
		FragmentTask task = new FragmentTask() {
			private int mMessage = 0;

			/**
			 * Call the static method to start the web page; this can take a few seconds
			 */
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				mMessage = requestAuthorizationImmediate(activity);
			}

			/**
			 * Display any error message
			 */
			@Override
			public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
				if (mMessage != 0)
					fragment.showToast(mMessage);
			}

		};

		// Get the fragment to display task progress
		SimpleTaskQueueProgressFragment.runTaskWithProgress(activity, R.string.connecting_to_web_site, task, true, 0);
	}

	/**
	 * Static method to request authorization from goodreads.
	 */
	private static int requestAuthorizationImmediate(Context context) {
		GoodreadsManager grMgr = new GoodreadsManager();
		// This next step can take several seconds....
		if (!grMgr.hasValidCredentials()) {
			try {
				grMgr.requestAuthorization(context);
			} catch (NetworkException e) {
				Logger.logError(e, "Error while requesting Goodreads authorization");
				return R.string.goodreads_access_error;
			}
		} else {
			return R.string.authorize_access_already_auth;
		}		
		return 0;
	}
}
