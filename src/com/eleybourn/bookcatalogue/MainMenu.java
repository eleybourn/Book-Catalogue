package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.AlertDialogUtils.AlertDialogItem;
import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

/**
 * Implement the 'Main Menu' for BookCatalogue. This is one of two possible start screens.
 * 
 * - 'My Books' -> preferred bookshelf view 
 * - 'Add Book' -> Add Method Dialog 
 * - 'Loan/Return/Edit Book'
 * - 'Search' 
 * - 'Admin & Preferences'
 * - Help
 * - Export/Import/Sync
 * 
 * @author Grunthos
 * 
 */
public class MainMenu extends Activity {
	private CatalogueDBAdapter mDbHelper;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Get the preferences and extras.
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		Bundle extras = this.getIntent().getExtras();

		// Handle startup specially.
		if (extras != null && extras.containsKey("startup") && extras.getBoolean("startup")) {
			// Check if we really want to start this activity.
			if (prefs.getStartInMyBook()) {
				doMyBooks();
				finish();
				return;
			}
		}

		// If we get here, we're meant to be in this activity.
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		setContentView(R.layout.main_menu);

		// Setup handlers for items. It's just a menu after all.
		setOnClickListener(R.id.my_books_label, mMyBooksHandler);
		setOnClickListener(R.id.add_book_label, mAddBookHandler);
		setOnClickListener(R.id.loan_label, mLoanBookHandler);
		setOnClickListener(R.id.search_label, mSearchHandler);
		setOnClickListener(R.id.administration_label, mAdminHandler);
		setOnClickListener(R.id.about_label, mAboutHandler);
		setOnClickListener(R.id.help_label, mHelpHandler);
		setOnClickListener(R.id.donate_label, mDonateHandler);
	}

	/**
	 * Add Book Menu Handler
	 */
	private OnClickListener mAddBookHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			ArrayList<AlertDialogItem> items = new ArrayList<AlertDialogItem>();
			items.add( new AlertDialogItem(getString(R.string.scan_barcode), mCreateBookScan) );
			items.add( new AlertDialogItem(getString(R.string.enter_barcode), mCreateBookIsbn) );
			items.add( new AlertDialogItem(getString(R.string.search_internet), mCreateBookName) );
			items.add( new AlertDialogItem(getString(R.string.add_manually), mCreateBookManually) );
			AlertDialogUtils.showContextDialogue(MainMenu.this, getString(R.string.menu_insert), items);
		}
	};

	/**
	 * Loan Book Menu Handler
	 */
	private OnClickListener mLoanBookHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			//Intent i = new Intent(this, BookCatalogue.class);
			//i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			//startActivity(i);
		}
	};

	/**
	 * Search Menu Handler
	 */
	private OnClickListener mSearchHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, SearchCatalogue.class);
			//i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}
	};

	/**
	 * Admin Menu Handler
	 */
	private OnClickListener mAdminHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, AdministrationFunctions.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);			
		}
	};

	/**
	 * About Menu Handler
	 */
	private OnClickListener mAboutHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, AdministrationAbout.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}
	};

	/**
	 * Help Menu Handler
	 */
	private OnClickListener mHelpHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, Help.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);			
		}
	};

	/**
	 * Donate Menu Handler
	 */
	private OnClickListener mDonateHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, AdministrationDonate.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}
	};

	/**
	 * Utility routine to set the OnClickListener for a given view item.
	 * 
	 * @param id		Sub-View ID
	 * @param l			Listener
	 */
	private void setOnClickListener(int id, OnClickListener l) {
		View v = this.findViewById(id);
		v.setOnClickListener(l);
		v.setBackgroundResource(android.R.drawable.list_selector_background);
	}

	/**
	 * 'My Books' menu item.
	 */
	private OnClickListener mMyBooksHandler = new OnClickListener() {
		@Override public void onClick(View v) { doMyBooks(); }
	};
	/**
	 * Method to start the book catalogue activity. Can be called from onCreate as well
	 * as from a menu item.
	 */
	private void doMyBooks() {
		Intent i = new Intent(this, BookCatalogue.class);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
	}

	/**
	 * Add Book Sub-Menu: Load the BookEdit Activity
	 */
	private Runnable mCreateBookManually = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookEdit.class);
			startActivity(i);
		}
	};
	
	/**
	 * Add Book Sub-Menu: Load the Search by ISBN Activity
	 */
	private Runnable mCreateBookIsbn = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
			i.putExtra(BookISBNSearch.BY, "isbn");
			startActivity(i);
		}
	};
	
	/**
	 * Add Book Sub-Menu: Load the Search by ISBN Activity
	 */
	private Runnable mCreateBookName = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
			i.putExtra(BookISBNSearch.BY, "name");
			startActivity(i);
		}
	};

	/**
	 * Add Book Sub-Menu: Load the Search by ISBN Activity to begin scanning.
	 */
	private Runnable mCreateBookScan = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
			i.putExtra(BookISBNSearch.BY, "scan");
			startActivity(i);
		}
	};

	/**
	 * Cleanup!
	 */
	@Override 
	public void onDestroy() {
		super.onDestroy();
		if (mDbHelper != null)
			mDbHelper.close();
	}
}
