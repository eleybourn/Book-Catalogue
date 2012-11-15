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
import java.util.ArrayList;
import java.util.HashSet;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.AsinUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SoundManager;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * This class is called by the BookCatalogue activity and will search the interwebs for
 * book details based on either a typed in or scanned ISBN.
 *
 * It currently only searches Google Books, but Amazon will be coming soon.
 */
public class BookISBNSearch extends ActivityWithTasks {
	//private static final int CREATE_BOOK = 0;
	public static final String BY = "by";

//	private static Integer mIdCounter = 0;
//	private final int mId;
//
//	{
//		synchronized(mIdCounter) {
//			mId = ++mIdCounter;
//		}
//	}

	private boolean mScannerStarted = false;

	private EditText mIsbnText;
	private EditText mTitleText;
	private AutoCompleteTextView mAuthorText;
	private ArrayAdapter<String> mAuthorAdapter = null;

	private Button mConfirmButton;
	private CatalogueDBAdapter mDbHelper;

	private String mAuthor;
	private String mTitle;
	private String mIsbn;

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
	long mSearchManagerId = 0;

	// A list of author names we have already searched for in this session
	ArrayList<String> mAuthorNames = new ArrayList<String>();

	/**
	 * Called when the activity is first created. This function will search the interwebs for
	 * book details based on either a typed in or scanned ISBN.
	 *
	 * @param savedInstanceState The saved bundle (from pausing). Can be null.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		try {
			super.onCreate(savedInstanceState);

			if (savedInstanceState != null)
				mSearchManagerId = savedInstanceState.getLong("SearchManagerId");

			//System.out.println("BookISBNSearch OnCreate SIS=" + (savedInstanceState == null? "N" : "Y"));

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

			mIsbn = extras.getString("isbn");
			String by = extras.getString(BY);

			if (savedInstanceState != null) {
				if (savedInstanceState.containsKey("mScannerStarted"))
					mScannerStarted = savedInstanceState.getBoolean("mScannerStarted");
				else {
					//System.out.println(mId + " OnCreate mScannerStarted NOT PRESENT");
				}
			}

			// BUG NOTE 1:
			//
			// There is a bizarre bug that seems to only affect some users in which this activity
			// is called AFTER the user has finished and the passed Intent has neither a ISBN nor a
			// "BY" in the Extras. Following all the code that starts this activity suggests that 
			// the activity is ALWAYS started with the intent data. The problems always occur AFTER
			// adding a book, which confirms that the activity has been started correctly.
			// 
			// In order to avoid this problem, we just check for nulls and finish(). THIS IS NOT A FIX
			// it is a MESSY WORK-AROUND.
			//
			// TODO: Find out why BookISBNSearch gets restarted with no data
			//
			// So...we save the extras in savedInstanceState, and look for it when missing
			//
			if (mIsbn == null && (by == null || by.equals("") ) ) {
				Logger.logError(new RuntimeException("Empty args for BookISBNSearch"));
				if (savedInstanceState != null) {
					if (mIsbn == null && savedInstanceState.containsKey("isbn")) 
						mIsbn = savedInstanceState.getString("isbn");
					if ( (by == null || by.equals("") ) && savedInstanceState.containsKey(BY)) 
						by = savedInstanceState.getString(BY);
				}
				// If they are still null, we can't proceed.
				if (mIsbn == null && (by == null || by.equals("") ) ) {
					finish();
					return;
				}
			}

			// Default to MANUAL
			mMode = MODE_MANUAL;

			if (mIsbn != null) {
				//System.out.println(mId + " OnCreate got ISBN");
				//ISBN has been passed by another component
				setContentView(R.layout.isbn_search);
				mIsbnText = (EditText) findViewById(R.id.isbn);
				mIsbnText.setText(mIsbn);
				go(mIsbn, "", "");
			} else if (by.equals("isbn")) {
				// System.out.println(mId + " OnCreate BY ISBN");
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
				final CheckBox allowAsinCb = (CheckBox) BookISBNSearch.this.findViewById(R.id.asinCheckbox);
				allowAsinCb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						if (isChecked) {
							mIsbnText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_CLASS_TEXT);
							getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
						} else {
							mIsbnText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_CLASS_TEXT);
							getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
						}
					}});

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
				// System.out.println(mId + " OnCreate BY NAME");
				setContentView(R.layout.name_search);

				this.initAuthorList();

				mTitleText = (EditText) findViewById(R.id.title);
				mConfirmButton = (Button) findViewById(R.id.search);

				mConfirmButton.setOnClickListener(new View.OnClickListener() {
					public void onClick(View view) {
						String mAuthor = mAuthorText.getText().toString();
						String mTitle = mTitleText.getText().toString();

						ArrayAdapter<String> adapter = mAuthorAdapter;
						if (adapter.getPosition(mAuthor) < 0){
							// Based on code from filipeximenes we also need to update the adapter here in
							// case no author or book is added, but we still want to see 'recent' entries.
							if (!mAuthor.trim().equals("")) {
								boolean found = false;
								for(String s: mAuthorNames) {
									if (s.equalsIgnoreCase(mAuthor)) {
										found = true;
										break;
									}
								}

								if (!found) {
									// Keep a list of names as typed to use when we recreate list
									mAuthorNames.add(mAuthor);
									// Add to adapter, in case search produces no results
									adapter.add(mAuthor);							
								}
							}
						}

						go("", mAuthor, mTitle);

					}
				});
			} else if (by.equals("scan")) {
				// System.out.println(mId + " OnCreate BY SCAN");
				// Use the scanner to get ISBNs
				mMode = MODE_SCAN;
				setContentView(R.layout.isbn_scan);
				mIsbnText = (EditText) findViewById(R.id.isbn);

				/**
				 * Use the zxing barcode scanner to search for a isbn
				 * Prompt users to install the application if they do not have it installed.
				 */
				try {
					if (true) 
						throw new java.lang.SecurityException();
					// Start the scanner IF this is a real 'first time' call.
					if (savedInstanceState == null) {
						startScannerActivity();
					} else {
						// It's a saved state, so see if we have an ISBN
						if (savedInstanceState.containsKey("isbn")) {
							go(savedInstanceState.getString("isbn"),"","");
						}
					}
				} catch (java.lang.SecurityException e) {
					AlertDialog alertDialog = new AlertDialog.Builder(BookISBNSearch.this).setMessage(R.string.bad_scanner).create();
					alertDialog.setTitle(R.string.install_scan_title);
					alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
					alertDialog.setButton2("ZXing", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.zxing.client.android"));
							startActivity(marketIntent);
							finish();
						}
					});
					alertDialog.setButton("Cancel", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							//do nothing
							finish();
						}
					});
					// Prevent the activity result from closing this activity.
					mDisplayingAlert = true;
					alertDialog.show();
					return;					
				} catch (ActivityNotFoundException e) {
					// Verify - this can be a dangerous operation
					AlertDialog alertDialog = new AlertDialog.Builder(BookISBNSearch.this).setMessage(R.string.install_scan).create();
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
		} finally {
			Tracker.exitOnCreate(this);			
		}
	}

	/*
	 * Handle character insertion at cursor position in EditText
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
		//System.out.println(mId + " GO: isbn=" + isbn + ", author=" + author + ", title=" + title);

		// Save the details because we will do some async processing or an alert
		mIsbn = isbn;
		mAuthor = author;
		mTitle = title;

		// If the book already exists, do not continue
		try {
			if (isbn != null && !isbn.equals("")) {

				// If the layout has an 'Allow ASIN' checkbox, see if it is checked.
				final CheckBox allowAsinCb = (CheckBox) BookISBNSearch.this.findViewById(R.id.asinCheckbox);
				final boolean allowAsin = allowAsinCb != null ? allowAsinCb.isChecked() : false;

				if (!IsbnUtils.isValid(isbn) && (!allowAsin || !AsinUtils.isValid(isbn) ) ) {
					int msg;
					if (allowAsin) {
						msg = R.string.x_is_not_a_valid_isbn_or_asin;
					} else {
						msg = R.string.x_is_not_a_valid_isbn;
					}
					Toast.makeText(this, getString(msg, isbn), Toast.LENGTH_LONG).show();
					if (mMode == MODE_SCAN) {
						// Optionally beep if scan failed.
						SoundManager.beepLow();
						// reset the now-discarded details
						mIsbn = "";
						mAuthor = "";
						mTitle = "";
						startScannerActivity();
					}
					return;
				} else {
					final long existingId = mDbHelper.getIdFromIsbn(isbn);
					if (existingId > 0) {
						// Verify - this can be a dangerous operation
						AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(R.string.duplicate_book_message).create();
						alertDialog.setTitle(R.string.duplicate_book_title);
						alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
						alertDialog.setButton2(this.getResources().getString(R.string.add), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								doSearchBook();
								return;
							}
						});
						alertDialog.setButton3(this.getResources().getString(R.string.edit_book), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								BookEdit.editBook(BookISBNSearch.this, existingId, BookEdit.TAB_EDIT);
							}
						});
						alertDialog.setButton(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								//do nothing
								if (mMode == MODE_SCAN) {
									// reset the now-discarded details
									mIsbn = "";
									mAuthor = "";
									mTitle = "";
									startScannerActivity();
								}
								return;
							}
						});
						alertDialog.show();
						return;
					}
				}
			}
		} catch (Exception e) {
			Logger.logError(e);
		}

		if (mSearchManagerId == 0)
			doSearchBook();

	}

	private void doSearchBook() {
		// System.out.println(mId + " doSearchBook");
		/* Delete any hanging around temporary thumbs */
		try {
			File thumb = CatalogueDBAdapter.getTempThumbnail();
			thumb.delete();
		} catch (Exception e) {
			// do nothing - this is the expected behaviour
		}

		if ( (mAuthor != null && !mAuthor.equals("")) || (mTitle != null && !mTitle.equals("")) || (mIsbn != null && !mIsbn.equals("")) ) {
			//System.out.println(mId + " doSearchBook searching");
			/* Get the book */
			try {
				// Start the lookup in background.
				//mTaskManager.doProgress("Searching");
				SearchManager sm = new SearchManager(getTaskManager(), mSearchHandler);
				mSearchManagerId = sm.getSenderId();
				Tracker.handleEvent(this, "Searching" + mSearchManagerId, Tracker.States.Running);

				this.getTaskManager().doProgress(getString(R.string.searching_elipsis));
				sm.search(mAuthor, mTitle, mIsbn, true, SearchManager.SEARCH_ALL);
				// reset the details so we don't restart the search unnecessarily
				mAuthor = "";
				mTitle = "";
				mIsbn = "";
			} catch (Exception e) {
				Logger.logError(e);
				Toast.makeText(this, R.string.search_fail, Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		} else {
			// System.out.println(mId + " doSearchBook no criteria");
			if (mMode == MODE_SCAN)
				startScannerActivity();
			return;
		}
	}

	private SearchManager.SearchListener mSearchHandler = new SearchManager.SearchListener() {
		@Override
		public boolean onSearchFinished(Bundle bookData, boolean cancelled) {
			return BookISBNSearch.this.onSearchFinished(bookData, cancelled);
		}
	};

	private boolean onSearchFinished(Bundle bookData, boolean cancelled) {
		Tracker.handleEvent(this, "onSearchFinished" + mSearchManagerId, Tracker.States.Running);
		try {
			//System.out.println(mId + " onSearchFinished");
			if (cancelled || bookData == null) {
				if (mMode == MODE_SCAN)
					startScannerActivity();
			} else {
				getTaskManager().doProgress(getString(R.string.adding_book_elipsis));
				createBook(bookData);
				// Clear the data entry fields ready for the next one
				clearFields();
			}
			return true;
		} finally {
			// Clean up
			mSearchManagerId = 0;
			// Make sure the base message will be empty.
			this.getTaskManager().doProgress(null);
		}
	}

	@Override
	protected void onPause() {
		Tracker.enterOnPause(this);
		super.onPause();
		if (mSearchManagerId != 0)
			SearchManager.getMessageSwitch().removeListener(mSearchManagerId, mSearchHandler);
		Tracker.exitOnPause(this);
	}

	@Override
	protected void onResume() {
		Tracker.enterOnResume(this);
		super.onResume();
		if (mSearchManagerId != 0)
			SearchManager.getMessageSwitch().addListener(mSearchManagerId, mSearchHandler, true);
		Tracker.exitOnResume(this);
	}

	@Override
	protected void onDestroy() {
		Tracker.enterOnDestroy(this);
		super.onDestroy();
		mDbHelper.close();
		Tracker.exitOnDestroy(this);
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
		startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
		//dismissProgress();
	}

	/**
	 * This is a straight passthrough
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		//System.out.println("BookISBNSearch onActivityResult " + resultCode);
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case UniqueId.ACTIVITY_SCAN:
			mScannerStarted = false;
			try {
				if (resultCode == RESULT_OK) {
					// Scanner returned an ISBN...process it.
					String contents = intent.getStringExtra("SCAN_RESULT");
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
		case UniqueId.ACTIVITY_EDIT_BOOK:
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

		// No matter what the activity was, rebuild the author list in case a new author was added.
		initAuthorList();

	}

	private void initAuthorList() {
		// Get the author field, if present
		mAuthorText = (AutoCompleteTextView) findViewById(R.id.author);
		if (mAuthorText != null) {
			// Get all known authors and build a hash of the names
			final ArrayList<String> authors = mDbHelper.getAllAuthors();
			final HashSet<String> uniqueNames =  new HashSet<String>();
			for(String s: authors)
				uniqueNames.add(s.toUpperCase());

			// Add the names the user has already tried (to handle errors and mistakes) 
			for(String s: mAuthorNames) {
				if (!uniqueNames.contains(s.toUpperCase()))
					authors.add(s);
			}
			
			// Now get an adapter based on the combined names
			mAuthorAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, authors);

			// Set it
			mAuthorText.setAdapter(mAuthorAdapter);				
		}
	}

	/*
	 * Start scanner activity.
	 */
	private void startScannerActivity() {
		//System.out.println(mId + " startScannerActivity");

		if (mScannerIntent == null) {
			mScannerIntent = new Intent("com.google.zxing.client.android.SCAN");
			//intent.putExtra("SCAN_MODE", "EAN_13");
		}
		if (!mScannerStarted) {
			//System.out.println(mId + " startScannerActivity STARTING");
			mScannerStarted = true;
			startActivityForResult(mScannerIntent, UniqueId.ACTIVITY_SCAN);
		} else {
			//System.out.println(mId + " startScannerActivity SKIPPED");
		}
	}

	/**
	 * Ensure the TaskManager is restored.
	 */
	@Override
	protected void onRestoreInstanceState(Bundle inState) {
		//System.out.println(mId + " onRestoreInstanceState");

		mSearchManagerId = inState.getLong("SearchManagerId");

		// Now do 'standard' stuff
		mLastBookIntent = (Intent) inState.getParcelable("LastBookIntent");

		// Call the super method only after we have the searchManager set up
		super.onRestoreInstanceState(inState);
	}

	@Override
	protected void onSaveInstanceState(Bundle inState) {
		super.onSaveInstanceState(inState);

		// Saving intent data is a kludge due to an apparent Android bug in some
		// handsets. Search for "BUG NOTE 1" in this source file for a discussion
		Bundle b = getIntent().getExtras();
		if (b != null) {
			if (b.containsKey("isbn"))
				inState.putString("isbn", b.getString("isbn"));
			if (b.containsKey(BY))
				inState.putString(BY, b.getString(BY));
		}

		inState.putParcelable("LastBookIntent", mLastBookIntent);
		// Save the current search details as this may be called as a result of a rotate during an alert dialog.
		inState.putString("author", mAuthor);
		inState.putString("isbn", mIsbn);
		inState.putString("title", mTitle);
		inState.putBoolean("mScannerStarted", mScannerStarted);
		if (mSearchManagerId != 0)
			inState.putLong("SearchManagerId", mSearchManagerId);
	}
}
