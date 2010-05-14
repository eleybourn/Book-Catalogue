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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class BookISBNSearch extends Activity {

	private EditText mIsbnText;
	private CatalogueDBAdapter mDbHelper;

	public String author;
	public String title;
	public String isbn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			//ISBN has been passed by another component
			isbn = extras.getString("isbn");
			go(isbn);
		} else {
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
			setContentView(R.layout.isbn_search);
			
			mIsbnText = (EditText) findViewById(R.id.isbn);
			Button confirmButton = (Button) findViewById(R.id.search);
			confirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					String mIsbn = mIsbnText.getText().toString();
					go(mIsbn);
				}
			});
		}
	}
	
	protected void go(String isbn) {
		/* Get the book */
		String[] book = searchGoogle(isbn);
		/* Format the output 
		 * String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num};
		 */
		book[0] = properCase(book[0]); // author
		book[1] = properCase(book[1]); // title
		book[1] = properCase(book[3]); // publisher
		createBook(book);
		setResult(RESULT_OK);
		finish();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	private String[] searchGoogle(String mIsbn) {
		String path = "http://books.google.com/books/feeds/volumes?q=ISBN";
		URL url;
		//String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num};
		String[] book = {"", "", mIsbn, "", "", "0",  "", "", "", "", ""};
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
		SearchGoogleBooksEntryHandler entryHandler = new SearchGoogleBooksEntryHandler();

		try {
			url = new URL(path+mIsbn);
			parser = factory.newSAXParser();
			int count = 0;
			try {
				parser.parse(getInputStream(url), handler);
				count = handler.getCount();
			} catch (RuntimeException e) {
				Toast.makeText(this, R.string.unable_to_connect, Toast.LENGTH_LONG).show();
			}
			if (count > 0) {
				String id = handler.getId();
				url = new URL(id);
				parser = factory.newSAXParser();
				try {
					parser.parse(getInputStream(url), entryHandler);
					book = entryHandler.getBook();
				} catch (RuntimeException e) {
					Toast.makeText(this, R.string.unable_to_connect, Toast.LENGTH_LONG).show();
				}
			} else {
				Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_LONG).show();
			}
			return book;
		} catch (MalformedURLException e) {
			//Log.e("Book Catalogue", "Malformed URL " + e.getMessage());
		} catch (ParserConfigurationException e) {
			//Log.e("Book Catalogue", "SAX Parsing Error " + e.getMessage());
		} catch (SAXException e) {
			//Log.e("Book Catalogue", "SAX Exception " + e.getMessage());
		} catch (IOException e) {
			//Log.e("Book Catalogue", "SAX IO Exception " + e.getMessage());
		}
		return null;
	}
	

	protected InputStream getInputStream(URL url) {
		try {
			return url.openConnection().getInputStream();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public String properCase(String inputString) {
		StringBuilder ff = new StringBuilder(); 
		int wordnum = 0;

		for(String f: inputString.split(" ")) {
			if(ff.length() > 0) { 
				ff.append(" "); 
			} 
			wordnum++;
			String word = f.toLowerCase();

			if (word.substring(0,1).matches("[\"\\(\\./\\\\,]")) {
				wordnum = 1;
				ff.append(word.substring(0,1));
				word = word.substring(1,word.length());
			}

			/* Do not convert 1st char to uppercase in the following situations */
			if (wordnum > 1 && word.matches("a|to|at|the|in|and|is|von|de|le")) {
				ff.append(word);
				continue;
			} 
			try {
				if (word.substring(0,2).equals("mc")) {
					ff.append(word.substring(0,1).toUpperCase());
					ff.append(word.substring(1,2));
					ff.append(word.substring(2,3).toUpperCase());
					ff.append(word.substring(3,word.length()));
					continue;
				}
			} catch (StringIndexOutOfBoundsException e) {
				// do nothing and continue;
			}

			try {
				if (word.substring(0,3).equals("mac")) {
					ff.append(word.substring(0,1).toUpperCase());
					ff.append(word.substring(1,3));
					ff.append(word.substring(3,4).toUpperCase());
					ff.append(word.substring(4,word.length()));
					continue;
				}
			} catch (StringIndexOutOfBoundsException e) {
				// do nothing and continue;
			}

			try {
				ff.append(word.substring(0,1).toUpperCase());
				ff.append(word.substring(1,word.length()));
			} catch (StringIndexOutOfBoundsException e) {
				ff.append(word);
			}
		}

		/* output */ 
		String outputString = ff.toString();
		return outputString;
	}
	
	/*
	 * Load the BookEdit Activity
	 * 
	 * return void
	 */
	private void createBook(String[] book) {
		Intent i = new Intent(this, BookEdit.class);
		i.putExtra("book", book);
		startActivity(i);
	}

}
