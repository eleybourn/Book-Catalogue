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
import java.util.Hashtable;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;

/**
 * This class is called by the BookCatalogue activity and will search the interwebs for 
 * book details based on either a typed in or scanned ISBN.
 * 
 * It currently only searches Google Books, but Amazon will be coming soon.  
 */
public class BookISBNSearch extends ActivityWithTasks {
	private static final int CREATE_BOOK = 0;
	public static final String BY = "by";
	
	private EditText mIsbnText;
	private EditText mTitleText;
	private AutoCompleteTextView mAuthorText;
	private Button mConfirmButton;
	private CatalogueDBAdapter mDbHelper;

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

	// Object managing current search.
	SearchManager mSearchManager = null;

	/**
	 * Called when the activity is first created. This function will search the interwebs for 
	 * book details based on either a typed in or scanned ISBN.
	 * 
	 * @param savedInstanceState The saved bundle (from pausing). Can be null.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//do we have a network connection?
		boolean network_available = Utils.isNetworkAvailable(this);
		if (network_available == false) {
			Toast.makeText(this, R.string.no_connection, Toast.LENGTH_LONG).show();
			finish();
		}


		Utils.showLtAlertIfNecessary(this, false, "search");

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
			ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mDbHelper.getAllAuthors());
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
				alertDialog.setButton("Google Goggles", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//TODO
						Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.unveil")); 
						startActivity(marketIntent);
						finish();
					}
				}); 
				alertDialog.setButton3("ZXing", new DialogInterface.OnClickListener() {
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
		// Get instance of Vibrator from current Context
		//NOTE: Removed due to complaints
		//Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		// Vibrate for 50 milliseconds
		//v.vibrate(50);
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
			Logger.logError(e);
		}
	}
	*/
	
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
		// Save the details
		this.isbn = isbn;
		this.author = author;
		this.title = title;
		
		// If the book already exists, do not continue
		try {
			if (!isbn.equals("")) {
				
				if (mDbHelper.checkIsbnExists(isbn)) {
					// Verify - this can be a dangerous operation
					AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(R.string.duplicate_book_message).create();
					alertDialog.setTitle(R.string.duplicate_book_title);
					alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
					alertDialog.setButton(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							doSearchBook();
							return;
						}
					}); 
					alertDialog.setButton2(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							//do nothing
							if (mMode == MODE_SCAN)
								startScannerActivity();
							return;
						}
					}); 
					alertDialog.show();
					return;
				}
			}
		} catch (Exception e) {
			Logger.logError(e);
		}
		
		doSearchBook();
		
	}
	
	private void doSearchBook() {
		/* Delete any hanging around thumbs */
		try {
			File thumb = CatalogueDBAdapter.fetchThumbnail(0);
			thumb.delete();
		} catch (Exception e) {
			// do nothing - this is the expected behaviour 
		}
		
		/* Get the book */
		try {
			// Start the lookup in background.
			//mTaskManager.doProgress("Searching");
			mSearchManager = new SearchManager(mTaskManager, mSearchHandler);
			mSearchManager.search(author, title, isbn, true);

		} catch (Exception e) {
			Logger.logError(e);
			Toast.makeText(this, R.string.search_fail, Toast.LENGTH_LONG).show();
			finish();
			return;
		}		
	}

	private SearchManager.SearchResultHandler mSearchHandler = new SearchManager.SearchResultHandler() {

		@Override
		public void onSearchFinished(Bundle bookData, boolean cancelled) {
			if (cancelled || bookData == null) {
				if (mMode == MODE_SCAN)
					startScannerActivity();
			} else {
				mTaskManager.doProgress("Adding Book...");
				createBook(bookData);
				// Clear the data entry fields ready for the next one
				clearFields();
				// Make sure the message will be empty.
				mTaskManager.doProgress(null);
			}
			// Clean up
			mSearchManager = null;
		}
	};

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
	
	/*
	 * Load the BookEdit Activity
	 * 
	 * return void
	 */
	private void createBook(Bundle book) {
		Intent i = new Intent(this, BookEdit.class);
		i.putExtra("bookData", book);
		startActivityForResult(i, CREATE_BOOK);
		//dismissProgress();
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
				Logger.logError(e);
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

	/**
	 * Ensure the TaskManager is restored.
	 */
	@Override
	protected void onRestoreInstanceState(Bundle inState) {
		mSearchManager = (SearchManager) getLastNonConfigurationInstance("SearchManager");
		if (mSearchManager != null)
			mSearchManager.reconnect( mSearchHandler );
		// Call the super method only after we have the searchManager set up
		super.onRestoreInstanceState(inState);
	}

	/**
	 * Ensure the TaskManager is saved.
	 */
	@Override
	public void onRetainNonConfigurationInstance(Hashtable<String,Object> store) {
		if (mSearchManager != null) {
			store.put("SearchManager", mSearchManager);
			mSearchManager.disconnect();
			mSearchManager = null;
		}
			
	}

	@Override
	TaskHandler getTaskHandler(ManagedTask t) {
		if (mSearchManager == null)
			throw new RuntimeException("Tasks running, but no SearchManager");
		TaskHandler h = mSearchManager.getTaskHandler( t );
		if (h == null)
			throw new RuntimeException("Unable to find handler for task " + t.toString());

		return h;
	}
}
