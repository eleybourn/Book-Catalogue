package com.eleybourn.bookcatalogue.amazon;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import com.amazon.device.associates.AssociatesAPI;
import com.amazon.device.associates.LinkService;
import com.amazon.device.associates.NotInitializedException;
import com.amazon.device.associates.OpenRetailPageRequest;
import com.amazon.device.associates.OpenSearchPageRequest;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Wrappers for Amazon API
 * 
 * NOTE: The project must include a class, AmazonAppKey in this folder, which contains a
 * single static String, KEY, containing the app key granted by Amazon. For testing purposes
 * this KEY can be junk.
 * 
 * @author pjw
 *
 */
public class AmazonUtils {

	public static String AMAZON_TAG = "bookcatalogue-20";

	public static void openLink(Activity context, String author, String series) {
		WebView wv = new WebView(context);

		if (author == null)
			author = "";
		if (series == null)
			series = "";

		try {
			LinkService linkService = AssociatesAPI.getLinkService();
			try {
				String baseUrl = "http://www.amazon.com/gp/search?index=books";
				String extra = AmazonUtils.buildSearchArgs(author, series);
				if (extra != null && !extra.trim().equals("")) {
					linkService.overrideLinkInvocation(wv, baseUrl + extra);
//					Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + extra));
//					context.startActivity(loadweb); 			
				}
			} catch(Exception ew) {
				OpenSearchPageRequest request = new OpenSearchPageRequest("books", author + " " + series);
				linkService.openRetailPage(request);
			}
		} catch (NotInitializedException e) {
			e.printStackTrace();
		}
	}
	
	public static String buildSearchArgs(String author, String series) {
		// This code works, but Amazon have a nasty tendency to cancel Associate IDs...
		//String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
		String extra = "";
		// http://www.amazon.com/gp/search?index=books&field-author=steven+a.+mckay&field-keywords=the+forest+lord
		if (author != null && !author.trim().equals("")) {
			author.replaceAll("\\.,+"," ");
			author.replaceAll(" *","+");
			try {
				extra += "&field-author=" + URLEncoder.encode(author, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Logger.logError(e, "Unable to add author to URL");
				return null;
			}
		}
		if (series != null && !series.trim().equals("")) {
			series.replaceAll("\\.,+"," ");
			series.replaceAll(" *","+");
			try {
				extra += "&field-keywords=" + URLEncoder.encode(series, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Logger.logError(e, "Unable to add series to URL");
				return null;
			}
		}
		return extra;
		//if (extra != null && !extra.trim().equals("")) {
		//	Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + extra));
		//	context.startActivity(loadweb); 			
		//}			
		
	}
	
}
