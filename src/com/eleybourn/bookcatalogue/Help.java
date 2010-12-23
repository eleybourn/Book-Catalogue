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
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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
			
			WebView wv = (WebView) findViewById(R.id.help_webview);
			wv.getSettings().setJavaScriptEnabled(true);
			wv.setWebViewClient(new HelpClient());
			wv.loadUrl("https://github.com/eleybourn/Book-Catalogue/wiki/Help");
			//wv.loadUrl("https://www.google.com");
		} catch (Exception e) {
			Log.e("Book Catalogue", "Unknown Exception - Help onCreate - " + e.getMessage() );
		}
	}
	
	private class HelpClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}
		
		//@Override
		//public void onRecievedSslError(WebView view, SslErrorHandler handler, SslError error) {
		//	Log.e("BC", "SSL ERROR " + error);
		//	handler.proceed();
		//}
	}

}