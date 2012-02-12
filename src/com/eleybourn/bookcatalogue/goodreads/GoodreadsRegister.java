package com.eleybourn.bookcatalogue.goodreads;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.Logger;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NetworkException;

/**
 * Activity to allow the user to authorize the application to access their goodreads account and
 * to explain goodreads.
 * 
 * @author Grunthos
 *
 */
public class GoodreadsRegister extends Activity {

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.goodreads_register);
			setupViews();
		} catch (Exception e) {
			Logger.logError(e);
		}
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
				// RELEASE: put up a progress dialog...its only one web request, but it can take a few seconds
				GoodreadsManager grMgr = new GoodreadsManager();
				if (!grMgr.hasValidCredentials()) {
					try {
						grMgr.requestAuthorization(GoodreadsRegister.this);
					} catch (NetworkException e) {
						Logger.logError(e, "Error while requesting Goodreads authorization");
						Toast.makeText(GoodreadsRegister.this, R.string.goodreads_access_error, Toast.LENGTH_LONG).show();
					}
				} else {
					Toast.makeText(GoodreadsRegister.this, R.string.authorize_access_already_auth, Toast.LENGTH_LONG).show();
				}
				return;
			}
		});

	}

}
