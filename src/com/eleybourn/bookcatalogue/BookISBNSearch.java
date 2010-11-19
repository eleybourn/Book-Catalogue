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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This class is called by the BookCatalogue activity and will search the interwebs for 
 * book details based on either a typed in or scanned ISBN.
 * 
 * It currently only searches Google Books, but Amazon will be coming soon.  
 */
public class BookISBNSearch extends Activity {
	private static final int CREATE_BOOK = 0;
	
	private EditText mIsbnText;
	private TextView mIsbnStatus;
	private Button mConfirmButton;
	private CatalogueDBAdapter mDbHelper;

	public String author;
	public String title;
	public String isbn;
	
	/**
	 * Called when the activity is first created. This function will search the interwebs for 
	 * book details based on either a typed in or scanned ISBN.
	 * 
	 * @param savedInstanceState The saved bundle (from pausing). Can be null.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		
		setContentView(R.layout.isbn_search);
		mIsbnText = (EditText) findViewById(R.id.isbn);
		mIsbnStatus = (TextView) findViewById(R.id.isbn_search_status);
		mConfirmButton = (Button) findViewById(R.id.search);
		
		if (extras != null) {
			//ISBN has been passed by another component
			isbn = extras.getString("isbn");
			mIsbnText.setText(isbn);
			go(isbn);
		} else {
			
			// Set the number buttons
			Button button1 = (Button) findViewById(R.id.isbn_1);
			button1.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("1"); } });
			Button button2 = (Button) findViewById(R.id.isbn_2);
			button2.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("2"); } });
			Button button3 = (Button) findViewById(R.id.isbn_3);
			button3.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("3"); } });
			Button button4 = (Button) findViewById(R.id.isbn_4);
			button4.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("4"); } });
			Button button5 = (Button) findViewById(R.id.isbn_5);
			button5.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("5"); } });
			Button button6 = (Button) findViewById(R.id.isbn_6);
			button6.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("6"); } });
			Button button7 = (Button) findViewById(R.id.isbn_7);
			button7.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("7"); } });
			Button button8 = (Button) findViewById(R.id.isbn_8);
			button8.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("8"); } });
			Button button9 = (Button) findViewById(R.id.isbn_9);
			button9.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("9"); } });
			Button buttonX = (Button) findViewById(R.id.isbn_X);
			buttonX.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("X"); } });
			Button button0 = (Button) findViewById(R.id.isbn_0);
			button0.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { mIsbnText.append("0"); } });
			ImageButton buttonDel = (ImageButton) findViewById(R.id.isbn_del);
			buttonDel.setOnClickListener(new View.OnClickListener() { 
				public void onClick(View view) { 
					try {
						mIsbnText.setText(mIsbnText.getText().toString().substring(0, (mIsbnText.getText().toString().length()-1))); 
					} catch (StringIndexOutOfBoundsException e) {
						//do nothing - empty string
					}
				} 
			});
			
			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					String mIsbn = mIsbnText.getText().toString();
					go(mIsbn);
				}
			});
		}
	}
	
	/* - MAJOR DATABASE ISSUES FOR THIS TO WORK!!!
	protected void checkISBN(final String isbn) {
		// If the book already exists, ask if the user wants to continue
		try {
			if (!isbn.equals("")) {
				Cursor book = mDbHelper.fetchBookByISBN(isbn);
				int rows = book.getCount();
				if (rows != 0) {
					
					AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(R.string.duplicate_alert).create();
					alertDialog.setTitle(R.string.duplicate_title);
					alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
					alertDialog.setButton(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							go(isbn);
							return;
						}
					}); 
					alertDialog.setButton2(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
							return;
						}
					}); 
					alertDialog.show();
				} else {
					go(isbn);
				}
			} else {
				go(isbn);
			}
		} catch (Exception e) {
			//do nothing
		}
	}
	*/
	
	/**
	 * This function takes the isbn and search google books (and soon amazon)
	 * to extract the details of the book. The details will then get sent to the
	 * BookEdit activity
	 * 
	 * @param isbn The ISBN to search
	 */
	protected void go(String isbn) {
		// If the book already exists, do not continue
		mConfirmButton.setEnabled(false);
		try {
			if (!isbn.equals("")) {
				Cursor book = mDbHelper.fetchBookByISBN(isbn);
				int rows = book.getCount();
				if (rows != 0) {
					Toast.makeText(this, R.string.book_exists, Toast.LENGTH_LONG).show();
					finish();
					return;
				}
				book.close(); //close the cursor
			}
		} catch (Exception e) {
			//do nothing
		}
		
		/* Delete any hanging around thumbs */
		try {
			File thumb = CatalogueDBAdapter.fetchThumbnail(0);
			thumb.delete();
		} catch (Exception e) {
			// do nothing - this is the expected behaviour 
		}
		/* Get the book */
		try {
			String[] book;
			String[] bookAmazon;
			
			mIsbnStatus.append(this.getResources().getString(R.string.searching_google_books) + "\n");
			book = searchGoogle(isbn);
			mIsbnStatus.append(this.getResources().getString(R.string.searching_amazon_books) + "\n");
			bookAmazon = searchAmazon(isbn);
			//Look for series in Title. e.g. Red Phoenix (Dark Heavens Trilogy)
			book[8] = findSeries(book[1]);
			bookAmazon[8] = findSeries(bookAmazon[1]);
			
			/* Fill blank fields as required */
			for (int i = 0; i<book.length; i++) {
				if (book[i] == "" || book[i] == "0") {
					book[i] = bookAmazon[i];
				}
			}
			
			/* Format the output 
			 * String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num, list_price, anthology, location, read_start, read_end, audiobook, signed};
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
		} catch (Exception e) {
			Toast.makeText(this, R.string.search_fail, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
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
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}
	
	public String[] searchGoogle(String mIsbn) {
		String path = "http://books.google.com/books/feeds/volumes?q=ISBN";
		URL url;
		//String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num, list_price, anthology, location, read_start, read_end, audiobook, signed, description, genre};
		String[] book = {"", "", mIsbn, "", "", "0",  "", "", "", "", "", "", "0", "", "", "", "", "0", "", ""};
		
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
		} catch (Exception e) {
			//Log.e("Book Catalogue", "SAX IO Exception " + e.getMessage());
		}
		return null;
	}
	
	/**
	 * 
	 * This searches the amazon REST site based on a specific isbn. It proxies through lgsolutions.com.au
	 * due to amazon not support mobile devices
	 * 
	 * @param mIsbn The ISBN to search for
	 * @return The book array
	 */
	public String[] searchAmazon(String mIsbn) {
		//String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num, list_price, anthology, location, read_start, read_end, audiobook, signed, description, genre};
		String[] book = {"", "", mIsbn, "", "", "0",  "", "", "", "", "", "", "0", "", "", "", "", "0", "", ""};
		String signedurl = "http://alphacomplex.org/getRest.php?isbn="+mIsbn;
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
				//Log.e("Book Catalogue", "Handler Exception " + e);
			}
			return book;
		} catch (MalformedURLException e) {
			//Log.e("Book Catalogue", "Malformed URL " + e.getMessage());
		} catch (ParserConfigurationException e) {
			//Log.e("Book Catalogue", "SAX Parsing Error " + e.getMessage());
		} catch (SAXException e) {
			//Log.e("Book Catalogue", "SAX Exception " + e.getMessage());
		} catch (Exception e) {
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
