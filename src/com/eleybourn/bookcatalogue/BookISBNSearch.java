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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class BookISBNSearch extends Activity {
	private static final int CREATE_BOOK = 0;
	
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
	
	/**
	 * This function takes the isbn and search google books (and soon amazon)
	 * to extract the details of the book. The details will then get sent to the
	 * BookEdit activity
	 * 
	 * @param isbn The ISBN to search
	 */
	protected void go(String isbn) {
		// If the book already exists, do not continue
		if (!isbn.equals("")) {
			Cursor book = mDbHelper.fetchBookByISBN(isbn);
			int rows = book.getCount();
			if (rows != 0) {
				Toast.makeText(this, R.string.book_exists, Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}
		
		/* Delete any hanging around thumbs */
		try {
			String tmpThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/tmp.jpg";
			File thumb = new File(tmpThumbFilename);
			thumb.delete();
		} catch (Exception e) {
			// do nothing - this is the expected behaviour 
		}
		/* Get the book */
		String[] book;
		//TODO: RE-IMPLEMENT String[] bookAmazon;
		book = searchGoogle(isbn);
		//TODO: RE-IMPLEMENT bookAmazon = searchAmazon(isbn);
		//Look for series in Title. e.g. Red Phoenix (Dark Heavens Trilogy)
		book[8] = findSeries(book[1]);
		//TODO: RE-IMPLEMENT bookAmazon[8] = findSeries(bookAmazon[1]);
		
		/* Fill blank fields as required */
		//TODO: RE-IMPLEMENT for (int i = 0; i<book.length; i++) {
		//TODO: RE-IMPLEMENT 	if (book[i] == "" || book[i] == "0") {
		//TODO: RE-IMPLEMENT 		book[i] = bookAmazon[i];
		//TODO: RE-IMPLEMENT 	}
		//TODO: RE-IMPLEMENT }
		
		/* Format the output 
		 * String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num};
		 */
		if (book[0] == "" && book[1] == "") {
			Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_LONG).show();
		} else {
			book[0] = properCase(book[0]); // author
			book[1] = properCase(book[1]); // title
			book[3] = properCase(book[3]); // publisher
			book[4] = convertDate(book[4]); // date_published
			book[8] = properCase(book[8]); // series
		}
		createBook(book);
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
				Toast.makeText(this, R.string.unable_to_connect_google, Toast.LENGTH_LONG).show();
			}
			if (count > 0) {
				String id = handler.getId();
				url = new URL(id);
				parser = factory.newSAXParser();
				try {
					parser.parse(getInputStream(url), entryHandler);
					book = entryHandler.getBook();
				} catch (RuntimeException e) {
					Toast.makeText(this, R.string.unable_to_connect_google, Toast.LENGTH_LONG).show();
				}
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
	
	private String[] searchAmazon(String mIsbn) {
		//String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num};
		String[] book = {"", "", mIsbn, "", "", "0",  "", "", "", "", ""};

		// Format the timestamp
		Date now = new Date();
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.'000Z'");

		// Create the URL
		String host = "webservices.amazon.com";
		String path = "/onca/xml";
		
		String[] params = {mIsbn, "ItemSearch", "Medium,Images", "Books", "AWSECommerceService", "AKIAIHF2BM6OTOA23JEQ", format.format(now)};
		String[] key_params = {"Keywords", "Operation", "ResponseGroup", "SearchIndex", "Service", "SubscriptionId", "Timestamp"};
		
		String amazon_url = "http://" + host + path + "?";
		//TODO - INSERT SECRET KEY HERE
		String sk = "<INSERT SECRET KEY>";
		String url_params = "";
		String enc_params = "";
		for (int i = 0; i<params.length; i++) {
			if (i != 0) {
				url_params += "&";
				enc_params += "&";
			}
			String param = params[i];
			url_params += key_params[i]+"="+param;
			param = param.replace(",","%2C");
			param = param.replace(":","%3A");
			enc_params += key_params[i]+"="+param;
		}
		
		String signstr = "GET\n" + host + "\n" + path + "\n" + enc_params;
		try {
			byte[] secretyKeyBytes = sk.getBytes("UTF-8");
			SecretKeySpec signingKey = new SecretKeySpec(secretyKeyBytes, "HmacSHA256");
			Mac hmac = Mac.getInstance("HmacSHA256");
			hmac.init(signingKey);
			byte[] rawHmac = hmac.doFinal(signstr.getBytes("UTF-8"));
			signstr = Base64.encodeBytes(rawHmac);
			signstr = URLEncoder.encode(signstr, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
		} catch (NoSuchAlgorithmException e) {
			//Log.e("Book Catalogue", "Invalid Algorithm");
			return book;
		} catch (InvalidKeyException e) {
			//Log.e("Book Catalogue", "Invalid Key");
			return book;
		} catch (UnsupportedEncodingException e) {
			//do nothing
		}

		String signedurl = amazon_url + url_params + "&Signature=" + signstr;

		URL url;
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchAmazonHandler handler = new SearchAmazonHandler();

		try {
			url = new URL(signedurl);
			parser = factory.newSAXParser();
			try {
				parser.parse(getInputStream(url), handler);
				book = handler.getBook();
			} catch (RuntimeException e) {
				Toast.makeText(this, R.string.unable_to_connect_amazon, Toast.LENGTH_LONG).show();
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
	
	public String findSeries(String title) {
		String series = "";
		int last = title.lastIndexOf("(");
		int close = title.lastIndexOf(")");
		if (last > -1 && close > -1 && last < close) {
			series = title.substring((last+1), close);
		}
		return series;
	}
	
	public String convertDate(String date) {
		if (date.length() == 2) {
			//assume yy
			try {
				if (Integer.parseInt(date) < 15) {
					date = "20" + date + "-01-01";
				} else {
					date = "19" + date + "-01-01";
				}
			} catch (Exception e) {
				date = "";
			}
		} else if (date.length() == 4) {
			//assume yyyy
			date = date + "-01-01";
		} else if (date.length() == 6) {
			//assume yyyymm
			date = date.substring(0, 4) + "-" + date.substring(4, 6) + "-01";
		} else if (date.length() == 7) {
			//assume yyyy-mm
			date = date + "-01";
		}
		return date;
	}
	
	public String properCase(String inputString) {
		StringBuilder ff = new StringBuilder(); 
		String outputString;
		int wordnum = 0;

		try {
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
			outputString = ff.toString();
		} catch (StringIndexOutOfBoundsException e) {
			//empty string - do nothing
			outputString = inputString;
		}
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
		startActivityForResult(i, CREATE_BOOK);
	}
	
	/**
	 * This is a straight passthrough
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		setResult(resultCode, intent);
		finish();
	}
}
