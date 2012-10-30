package com.eleybourn.bookcatalogue;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import android.net.ParseException;
import android.os.Bundle;

public class AmazonManager {
	/**
	 * 
	 * This searches the amazon REST site based on a specific isbn. It proxies through lgsolutions.com.au
	 * due to amazon not support mobile devices
	 * 
	 * @param mIsbn The ISBN to search for
	 * @return The book array
	 */
	static public void searchAmazon(String mIsbn, String mAuthor, String mTitle, Bundle bookData, boolean fetchThumbnail) {

		//replace spaces with %20
		mAuthor = mAuthor.replace(" ", "%20");
		//try {
		//	mAuthor = URLEncoder.encode(mAuthor, "utf-8");
		//} catch (UnsupportedEncodingException e1) {
		//	// Just use raw author...
		//}

		mTitle = mTitle.replace(" ", "%20");
		//try {
		//	mTitle = URLEncoder.encode(mTitle, "utf-8");
		//} catch (UnsupportedEncodingException e1) {
		//	// Just use raw title...
		//} 
		
		String path = "http://theagiledirector.com/getRest_v3.php";
		if (mIsbn.equals("")) {
			path += "?author=" + mAuthor + "&title=" + mTitle;
		} else {
			path += "?isbn=" + mIsbn;
		}
		URL url;
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchAmazonHandler handler = new SearchAmazonHandler(bookData, fetchThumbnail);

		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			// We can't Toast anything here, so let exceptions fall through.
			parser.parse(Utils.getInputStream(url), handler);
		} catch (MalformedURLException e) {
			Logger.logError(e);
		} catch (ParserConfigurationException e) {
			Logger.logError(e);
		} catch (ParseException e) {
			Logger.logError(e);
		} catch (SAXException e) {
			Logger.logError(e);
		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}
}
