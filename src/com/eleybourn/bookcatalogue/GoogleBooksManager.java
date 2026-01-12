package com.eleybourn.bookcatalogue;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.net.ParseException;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

// ENHANCE: Get editions via: http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300

public class GoogleBooksManager {

	static public File getThumbnailFromIsbn(String isbn) {
		Bundle b = new Bundle();
		try {
			searchGoogle(isbn, "", "", b, true);
			if (b.containsKey(SearchGoogleBooksEntryHandler.THUMBNAIL_KEY)) {
				File f = new File(b.getString(SearchGoogleBooksEntryHandler.THUMBNAIL_KEY));
				File newName = new File(f.getAbsolutePath() + "_" + isbn);
				f.renameTo(newName);
				return newName;
			} else {
				return null;
			}
		} catch (Exception e) {
			Logger.logError(e, "Error getting thumbnail from Google");
			return null;
		}
	}

	static public void searchGoogle(String mIsbn, String author, String title, Bundle bookData, boolean fetchThumbnail) {
		//replace spaces with %20
		author = author.replace(" ", "%20");
		title = title.replace(" ", "%20");

		String path = "https://books.google.com/books/feeds/volumes";
		if (mIsbn.isEmpty()) {
			path += "?q=" + "intitle%3A"+title+"%2Binauthor%3A"+author;
		} else {
			path += "?q=isbn%3A" + mIsbn;
		}
		URL url;

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
		SearchGoogleBooksEntryHandler entryHandler = new SearchGoogleBooksEntryHandler(bookData, fetchThumbnail);
	
		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			int count = 0;
			// We can't Toast anything from here; it no longer runs in UI thread. So let the caller deal 
			// with any exceptions.
			parser.parse(Utils.getInputStream(url), handler);
			count = handler.getCount();
			if (count > 0) {
				String id = handler.getId();
				url = new URL(id);
				parser = factory.newSAXParser();
				parser.parse(Utils.getInputStream(url), entryHandler);
			}
		} catch (Exception e) {
			Logger.logError(e);
		}
    }
	
}
