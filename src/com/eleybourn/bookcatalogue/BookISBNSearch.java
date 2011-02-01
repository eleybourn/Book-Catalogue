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
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;

/**
 * This class is called by the BookCatalogue activity and will search the interwebs for 
 * book details based on either a typed in or scanned ISBN.
 * 
 * It currently only searches Google Books, but Amazon will be coming soon.  
 */
public class BookISBNSearch extends Activity {
	private static final int CREATE_BOOK = 0;
	public static final String BY = "by";
	
	private EditText mIsbnText;
	private EditText mTitleText;
	private AutoCompleteTextView mAuthorText;
	private Button mConfirmButton;
	private CatalogueDBAdapter mDbHelper;
	private android.app.ProgressDialog mProgress = null;
	private SearchForBookTask mSearchTask = null;

	public String author;
	public String title;
	public String isbn;

	private static final int ACTIVITY_SCAN=4;

	/*
	 *  Mode this activity is in; MANUAL = data entry, SCAN = data from scanner.
	 *  For SCAN, it loops repeatedly starting the scanner.
	 */
	private static final int MODE_MANUAL = 1;
	private static final int MODE_SCAN = 2;
	private int mMode;
	
	// Flag to indicate the Activity should not 'finish()' because 
	// an alert is being displayed. The Alter will call finish().
	private boolean mDisplayingAlert = false;

	// The intent used to start the scanner.
	private Intent mScannerIntent = null;
	// The last Intent returned as a result of creating a book.
	private Intent mLastBookIntent = null;

	// Used by AsyncTask to get the ProgressDialog
	public android.app.ProgressDialog getProgress() {
		return mProgress;
	}

	// Dismiss the ProgressDialog and clear pointer.
	public void dismissProgress() {
		if (mProgress != null && mProgress.isShowing()) {
			mProgress.dismiss();
			mProgress = null;
		}
	}

	/*
	 * AsyncTask to lookup and process an ISBN. Doe in background so that 
	 * progress can be reported and to prevent locking up the android.
	 */
	public class SearchForBookTask extends android.os.AsyncTask<WeakReference<BookISBNSearch>, String,String[]> {

		/*
		 * Support for checking if task has finished in case the process finished while a screen rotation was happening.
		 * We don't want the Activity to rebuild the ProgressDialog if this task has done it's main work.
		 */
		public boolean isFinished() { return (this.getStatus() == Status.FINISHED); };

		/* 
		 * Keep a WEAK reference to the parent activity. Keeping a strong
		 * one could cause the GC problems since the parent keeps a pointer
		 * to this task.
		 */
		protected WeakReference<BookISBNSearch> mParent = null;

		public void setParent(WeakReference<BookISBNSearch> parent) {
			mParent = parent;
		}

		protected String[] doInBackground(WeakReference<BookISBNSearch>... activity) {
			mParent = activity[0];

			/* Format the output 
			 * String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, 
			 * series, pages, series_num, list_price, anthology, location, read_start, read_end, audiobook, 
			 * signed, description, genre};
			 */
			
			// List which fields are not sourced externally (ie. are local-only). In the case of
			// 'series' we try to derive it from the title.
			boolean[]  localOnly = { false /* author */, false /* title */, false /* isbn */, 
									false /* publisher */, false /* date_published */, true /* rating */,
									true /* bookshelf */, true /* read */, true /* series */, false /* pages */,
									true /* series_num */, false /* list_price */, false /* anthology */,
									true /* location */, true /* read_start */, true /* read_end */,
									false /* audiobook */, true /* signed */, false /* description */,
									false /* genre */ };

			String[] book = {mParent.get().author, mParent.get().title, mParent.get().isbn, "", "", "0",  "", "", "", "", "", "", "0", "", "", "", "", "0", "", ""};
			String[] bookAmazon = {mParent.get().author, mParent.get().title, mParent.get().isbn, "", "", "0",  "", "", "", "", "", "", "0", "", "", "", "", "0", "", ""};
			try {
				if (mParent != null)
					publishProgress(mParent.get().getResources().getString(R.string.searching_google_books));
				book = searchGoogle(isbn, author, title);
				if (mParent != null)
					publishProgress(mParent.get().getResources().getString(R.string.searching_amazon_books));

				/* Since Amazon only fills in blanks...check for externally sourced blanks so that 
				 * this goes faster.
				 * 
				 * Note 1: This may not be an optimization worth the effort since it seems that some
				 * fields are frequently blank (eg. list price at google).
				 * Note 2: This probably is a good optimization to use when more sources are added.
				 */
				boolean hasBlank = false;

				/* Fill blank fields as required */
				for (int i = 0; i<book.length; i++) {
					if (!localOnly[i] && (book[i] == "" || book[i] == "0") ) {
						hasBlank = true;
						break;
					}
				}

				if (hasBlank) {
					bookAmazon = searchAmazon(isbn, author, title);
					//Look for series in Title. e.g. Red Phoenix (Dark Heavens Trilogy)
					book[8] = findSeries(book[1]);
					bookAmazon[8] = findSeries(bookAmazon[1]);

					/* 
					 * Fill blank fields as required.
					 * 
					 * Note: We should probably verify that we found the same book. At least compare
					 * the ISBNs (if both non-blank) or something. Not sure.
					 */
					for (int i = 0; i<book.length; i++) {
						if (book[i] == "" || book[i] == "0") {
							book[i] = bookAmazon[i];
						}
					}
				}

				return book;

			} catch (Exception e) {
				Toast.makeText(mParent.get(), R.string.search_fail, Toast.LENGTH_LONG).show();
				return book;
			}
		}

		// Called in UI thread; update the ProgressDialog
		protected void onProgressUpdate(String... progress) {
			if (mParent != null && mParent.get() != null && mParent.get().getProgress() != null)
				mParent.get().getProgress().setMessage(progress[0]);
		}

		// Called in UI thread; perform appriate next step
	    protected void onPostExecute(String[] result) {
	    	// If book is not found, just return to dialog.
			if (result[0] == "" && result[1] == "") {
				if (mParent != null && mParent.get() != null)
			    	mParent.get().dismissProgress();

				Toast.makeText(mParent.get(), R.string.book_not_found, Toast.LENGTH_LONG).show();
				// Leave the ISBN text unchanged in case they need to edit it.
				if (mMode == MODE_SCAN)
					startScannerActivity();
			} else {
				if (mParent != null && mParent.get() != null && mParent.get().getProgress() != null) 
					mParent.get().getProgress().setMessage("Adding book...");
				result[0] = properCase(result[0]); // author
				result[1] = properCase(result[1]); // title
				result[3] = properCase(result[3]); // publisher
				result[4] = convertDate(result[4]); // date_published
				result[8] = properCase(result[8]); // series
				createBook(result);
				// Clear the data entry fields ready for the next one
				clearFields();
			}
			// Clear ref to parent
			if (mParent != null) {
				mParent.clear();
				mParent = null;
			}
			// Clear reference to this task.
			mSearchTask = null;
	    }
	 }

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

		isbn = extras.getString("isbn");
		String by = extras.getString(BY);

		// Default to MANUAL
		mMode = MODE_MANUAL;

		if (isbn != null) {
			//ISBN has been passed by another component
			setContentView(R.layout.isbn_search);
			mIsbnText = (EditText) findViewById(R.id.isbn);
			mIsbnText.setText(isbn);
			go(isbn, "", "");
		} else if (by.equals("isbn")) {
			setContentView(R.layout.isbn_search);
			mIsbnText = (EditText) findViewById(R.id.isbn);
			mConfirmButton = (Button) findViewById(R.id.search);

			// Not sure this is a great idea; we CAN diable keypad for this item completely.
			//android.view.inputmethod.InputMethodManager imm 
			//	= (android.view.inputmethod.InputMethodManager)
			//	getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
			//imm.hideSoftInputFromWindow(mIsbnText.getWindowToken(), 0);

			// For now, just make sure it's hidden on entry
			getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

			// Set the number buttons
			Button button1 = (Button) findViewById(R.id.isbn_1);
			button1.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("1"); } });
			Button button2 = (Button) findViewById(R.id.isbn_2);
			button2.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("2"); } });
			Button button3 = (Button) findViewById(R.id.isbn_3);
			button3.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("3"); } });
			Button button4 = (Button) findViewById(R.id.isbn_4);
			button4.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("4"); } });
			Button button5 = (Button) findViewById(R.id.isbn_5);
			button5.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("5"); } });
			Button button6 = (Button) findViewById(R.id.isbn_6);
			button6.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("6"); } });
			Button button7 = (Button) findViewById(R.id.isbn_7);
			button7.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("7"); } });
			Button button8 = (Button) findViewById(R.id.isbn_8);
			button8.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("8"); } });
			Button button9 = (Button) findViewById(R.id.isbn_9);
			button9.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("9"); } });
			Button buttonX = (Button) findViewById(R.id.isbn_X);
			buttonX.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("X"); } });
			Button button0 = (Button) findViewById(R.id.isbn_0);
			button0.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { handleIsbnKey("0"); } });
			ImageButton buttonDel = (ImageButton) findViewById(R.id.isbn_del);
			buttonDel.setOnClickListener(new View.OnClickListener() { 
				public void onClick(View view) { 
					try {
						int start = mIsbnText.getSelectionStart();
						int end = mIsbnText.getSelectionEnd();
						if (start < end) {
							// We have a selection. Delete it.
							mIsbnText.getText().replace(start, end, "");
							mIsbnText.setSelection(start, start);
						} else {
							// Delete char before cursor
							if (start > 0) {
								mIsbnText.getText().replace(start-1, start, "");
								mIsbnText.setSelection(start-1, start-1);
							}
						}
					} catch (StringIndexOutOfBoundsException e) {
						//do nothing - empty string
					}
				} 
			});
			
			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					String mIsbn = mIsbnText.getText().toString();
					go(mIsbn, "", "");
				}
			});
		} else if (by.equals("name")) {
			setContentView(R.layout.name_search);
			ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getAuthors());
			mAuthorText = (AutoCompleteTextView) findViewById(R.id.author);
			mAuthorText.setAdapter(author_adapter);
			
			mTitleText = (EditText) findViewById(R.id.title);
			mConfirmButton = (Button) findViewById(R.id.search);

			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					String mAuthor = mAuthorText.getText().toString();
					String mTitle = mTitleText.getText().toString();
					go("", mAuthor, mTitle);
				}
			});
		} else if (by.equals("scan")) {
			// Use the scanner to get ISBNs
			mMode = MODE_SCAN;
			setContentView(R.layout.isbn_scan);
			mIsbnText = (EditText) findViewById(R.id.isbn);

			/**
			 * Use the zxing barcode scanner to search for a isbn
			 * Prompt users to install the application if they do not have it installed.
			 */
			try {
				// Start the scanner IF this is a real 'first time' call.
				if (savedInstanceState == null)
					startScannerActivity();
			} catch (ActivityNotFoundException e) {
				// Verify - this can be a dangerous operation
				BookISBNSearch pthis = this;
				AlertDialog alertDialog = new AlertDialog.Builder(pthis).setMessage(R.string.install_scan).create();
				alertDialog.setTitle(R.string.install_scan_title);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.zxing.client.android")); 
						startActivity(marketIntent);
						finish();
					}
				}); 
				alertDialog.setButton2("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
						finish();
					}
				}); 
				// Prevent the activity result from closing this activity.
				mDisplayingAlert = true;
				alertDialog.show();
				return;
			}
		}
	}

	/*
	 * Handle character inserion at cursor position in EditText
	 */
	private void handleIsbnKey(String key) {
		int start = mIsbnText.getSelectionStart();
		int end = mIsbnText.getSelectionEnd();
		mIsbnText.getText().replace(start, end, key);
		mIsbnText.setSelection(start+1, start+1);
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
	
	protected ArrayList<String> getAuthors() {
		ArrayList<String> author_list = new ArrayList<String>();
		Cursor author_cur = mDbHelper.fetchAllAuthorsIgnoreBooks();
		startManagingCursor(author_cur);
		while (author_cur.moveToNext()) {
			String name = author_cur.getString(author_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
			author_list.add(name);
		}
		author_cur.close();
		return author_list;
	}

	/*
	 * Clear any data-entry fields that have been set.
	 * Used when a book has been successfully added as we want to get ready for another.
	 */
	private void clearFields() {
		if (mIsbnText != null)
			mIsbnText.setText("");
		if (mAuthorText != null)
			mAuthorText.setText("");
		if (mTitleText != null)
			mTitleText.setText("");
	}

	/**
	 * This function takes the isbn and search google books (and soon amazon)
	 * to extract the details of the book. The details will then get sent to the
	 * BookEdit activity
	 * 
	 * @param isbn The ISBN to search
	 */
	protected void go(String isbn, String author, String title) {
		// If the book already exists, do not continue
		try {
			if (!isbn.equals("")) {
				Cursor book = mDbHelper.fetchBookByISBN(isbn);
				int rows = book.getCount();
				book.close(); //close the cursor

				if (rows != 0) {
					Toast.makeText(this, R.string.book_exists, Toast.LENGTH_LONG).show();
					// If the scanner was the input, start it again.
					if (mMode == MODE_SCAN)
						startScannerActivity();
					return;
				}
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
			// Save the details
			this.isbn = isbn;
			this.author = author;
			this.title = title;

			// Show a ProgressDialog
			mProgress = android.app.ProgressDialog.show(this, "Searching...", "Searching internet for book...",true);
			mProgress.setCancelable(false);

			// Start the lookup task
			mSearchTask = new SearchForBookTask();
			WeakReference<BookISBNSearch> ref = new WeakReference<BookISBNSearch>(this);
			mSearchTask.execute(ref,null,null);

		} catch (Exception e) {
			Toast.makeText(this, R.string.search_fail, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Leaving the PD ref around causes some issues. Just clear it.
		// We will recreate later.
		dismissProgress();
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle inState) {
		// Get the AsyncTask
		mSearchTask = (SearchForBookTask) getLastNonConfigurationInstance();
		if (mSearchTask != null && !mSearchTask.isFinished()) {
			// If we had a task, create the progross dialog and reset the pointers.
			mProgress = android.app.ProgressDialog.show(this, "Searching...", "Searching internet for book...",true);
			mSearchTask.setParent(new WeakReference<BookISBNSearch>(this));
		}
		super.onRestoreInstanceState(inState);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		// Save the AsyncTask and remove the local refs.
		SearchForBookTask t = mSearchTask;
		if (mSearchTask != null) {
			mSearchTask.setParent(null);
			mSearchTask = null;
		}
		return t;
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
	
	public String[] searchGoogle(String mIsbn, String mAuthor, String mTitle) {
		//replace spaces with %20
		mAuthor = mAuthor.replace(" ", "%20");
		mTitle = mTitle.replace(" ", "%20");
		
		String path = "http://books.google.com/books/feeds/volumes";
		if (mIsbn.equals("")) {
			path += "?q=" + "intitle:"+mTitle+"+inauthor:"+mAuthor+"";
		} else {
			path += "?q=ISBN" + mIsbn;
		}
		URL url;
		//String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num, list_price, anthology, location, read_start, read_end, audiobook, signed, description, genre};
		String[] book = {mAuthor, mTitle, mIsbn, "", "", "0",  "", "", "", "", "", "", "0", "", "", "", "", "0", "", ""};
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchGoogleBooksHandler handler = new SearchGoogleBooksHandler();
		SearchGoogleBooksEntryHandler entryHandler = new SearchGoogleBooksEntryHandler();
		
		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			int count = 0;
			try {
				parser.parse(getInputStream(url), handler);
				count = handler.getCount();
			} catch (RuntimeException e) {
				Toast.makeText(this, R.string.unable_to_connect_google, Toast.LENGTH_LONG).show();
				//Log.e("BC", e.getMessage());
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
					//Log.e("BC", e.getMessage());
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
		return book;
	}
	
	/**
	 * 
	 * This searches the amazon REST site based on a specific isbn. It proxies through lgsolutions.com.au
	 * due to amazon not support mobile devices
	 * 
	 * @param mIsbn The ISBN to search for
	 * @return The book array
	 */
	public String[] searchAmazon(String mIsbn, String mAuthor, String mTitle) {
		//replace spaces with %20
		mAuthor = mAuthor.replace(" ", "%20");
		mTitle = mTitle.replace(" ", "%20");
		
		//String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num, list_price, anthology, location, read_start, read_end, audiobook, signed, description, genre};
		String[] book = {mAuthor, mTitle, mIsbn, "", "", "0",  "", "", "", "", "", "", "0", "", "", "", "", "0", "", ""};
		
		String path = "http://alphacomplex.org/getRest_v2.php";
		if (mIsbn.equals("")) {
			path += "?author=" + mAuthor + "&title=" + mTitle;
		} else {
			path += "?isbn=" + mIsbn;
		}
		URL url;
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchAmazonHandler handler = new SearchAmazonHandler();
		
		try {
			url = new URL(path);
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
		return book;
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
		dismissProgress();
	}

	/**
	 * This is a straight passthrough
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ACTIVITY_SCAN:
			try {
				if (resultCode == RESULT_OK) {
					// Scanner returned an ISBN...process it.
					String contents = intent.getStringExtra("SCAN_RESULT");
					Toast.makeText(this, R.string.isbn_found, Toast.LENGTH_LONG).show();
					mIsbnText.setText(contents);
					go(contents, "", "");
				} else {
					// Scanner Cancelled/failed. Exit if no dialog present.
					if (mLastBookIntent != null)
						this.setResult(RESULT_OK, mLastBookIntent);
					else
						this.setResult(RESULT_CANCELED, mLastBookIntent);
					if (!mDisplayingAlert)
						finish();
				}
			} catch (NullPointerException e) {
				finish();
			}
			break;
		case CREATE_BOOK:
			if (intent != null)
				mLastBookIntent = intent;

			// Created a book; save the intent and restart scanner if necessary.
			if (mMode == MODE_SCAN)
				startScannerActivity();
			else
				// If the 'Back' button is pressed on a normal activity, set the default result to cancelled by setting it here.
				this.setResult(RESULT_CANCELED, mLastBookIntent);

			break;
		}
	}
	
	/*
	 * Start scanner activity.
	 */
	private void startScannerActivity() {
		if (mScannerIntent == null)
			mScannerIntent = new Intent("com.google.zxing.client.android.SCAN");
		//intent.putExtra("SCAN_MODE", "EAN_13");
		startActivityForResult(mScannerIntent, ACTIVITY_SCAN);		
	}
}
