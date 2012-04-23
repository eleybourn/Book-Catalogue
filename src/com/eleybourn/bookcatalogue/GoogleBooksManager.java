package com.eleybourn.bookcatalogue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.os.Bundle;

// ENHANCE: Get editions via: http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300

public class GoogleBooksManager {

	static public void searchGoogle(String mIsbn, String author, String title, Bundle bookData, boolean fetchThumbnail) {
		//replace spaces with %20
		
		boolean showResultsInList = false;
		
		if(Utils.getListFlag(author)){
			showResultsInList = true;
			author = Utils.removeListFlag(author);
		}			
		
		author = author.replace(" ", "%20");
		title = title.replace(" ", "%20");

		String path = "http://books.google.com/books/feeds/volumes";
		if (mIsbn.equals("")) {
			path += "?q=" + "intitle:"+title+"+inauthor:"+author+"";
		} else {
			path += "?q=ISBN" + mIsbn;
		}
		URL url;

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
		SearchGoogleBooksEntryHandler entryHandler;
		Bundle tmp = new Bundle();
		if(!showResultsInList){
			entryHandler = new SearchGoogleBooksEntryHandler(bookData, fetchThumbnail);
		}else{
			entryHandler = new SearchGoogleBooksEntryHandler(tmp, fetchThumbnail);
		}		
	
		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			int count = 0;
			// We can't Toast anything from here; it no longer runs in UI thread. So let the caller deal 
			// with any exceptions.
			parser.parse(Utils.getInputStream(url), handler);
			count = handler.getCount();
			if (count > 0) {
				if(!showResultsInList){
					String id = handler.getId()[0];
					url = new URL(id);
					parser = factory.newSAXParser();
					parser.parse(Utils.getInputStream(url), entryHandler);
				}else{
					ArrayList<Book> books = new ArrayList<Book>();					
					String[] ids = handler.getId();
					int counter = 0;
					for(String id : ids){						
						if(id != null && id.length() > 0){
							url = new URL(id);
							parser = factory.newSAXParser();
							parser.parse(Utils.getInputStream(url), entryHandler);
							Book book = new Book(tmp);
							if(book.getTHUMBNAIL() != null && book.getTHUMBNAIL().length() > 0){
								book.setTHUMBNAIL(Utils.renameFile(book.getTHUMBNAIL(), "_GB"+Integer.toString(counter++)));							
							}							
							books.add(book);
							tmp.clear();
							bookData.putSerializable(CatalogueDBAdapter.KEY_BOOKLIST, books);
						}
					}				
				}
			}
			return;
		} catch (MalformedURLException e) {
			Logger.logError(e);
		} catch (ParserConfigurationException e) {
			Logger.logError(e);
		} catch (SAXException e) {
			Logger.logError(e);
		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}
	
}
