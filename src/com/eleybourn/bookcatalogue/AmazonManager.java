package com.eleybourn.bookcatalogue;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.content.ContentValues;
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
	static public void searchAmazon(String mIsbn, String mAuthor, String mTitle, Bundle bookData) {
		//replace spaces with %20
		mAuthor = mAuthor.replace(" ", "%20");
		mTitle = mTitle.replace(" ", "%20");
		
		String path = "http://alphacomplex.org/getRest_v2.php";
		if (mIsbn.equals("")) {
			path += "?author=" + mAuthor + "&title=" + mTitle;
		} else {
			path += "?isbn=" + mIsbn;
		}
		URL url;
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchAmazonHandler handler = new SearchAmazonHandler(bookData);

		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			// We can't Toast anything here, so let exceptions fall through.
			parser.parse(Utils.getInputStream(url), handler);
		} catch (MalformedURLException e) {
			//Log.e("Book Catalogue", "Malformed URL " + e.getMessage());
		} catch (ParserConfigurationException e) {
			//Log.e("Book Catalogue", "SAX Parsing Error " + e.getMessage());
		} catch (SAXException e) {
			//Log.e("Book Catalogue", "SAX Exception " + e.getMessage());
		} catch (Exception e) {
			//Log.e("Book Catalogue", "SAX IO Exception " + e.getMessage());
		}
		return;
	}
}
