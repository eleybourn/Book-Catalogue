/*
 * @copyright 2012 Philip Warner
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

import java.util.Timer;
import java.util.TimerTask;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Catalogue search based on the SQLite FTS engine. Due to the speed of FTS it updates the 
 * number of hits more or less in real time. The user can choose to see a full list at any
 * time.
 * 
 * ENHANCE: Finish or DELETE FTS activity.
 * 
 * @author Philip Warner
 */
public class SearchCatalogue extends BookCatalogueActivity {
	private CatalogueDBAdapter mDbHelper;
	/** Indicates user has changed something since the last search. */
	private boolean mSearchDirty = false;
	/** Timer reset each time the user clicks, in order to detect an idle time */
	private long mIdleStart = 0;
	/** Timer object for background idle searches */
	private Timer mTimer;
	/** Handle inter-thread messages */
	Handler m_handler = new Handler();

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Get the DB and setup the layout.
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();

		setContentView(R.layout.search_catalogue_criteria);
		
		View layout = this.findViewById(R.id.layout_root);
		EditText criteria = (EditText) this.findViewById(R.id.criteria);
		EditText author = (EditText) this.findViewById(R.id.author);
		EditText title = (EditText) this.findViewById(R.id.title);
		Button showResults = (Button) this.findViewById(R.id.search);
		Button ftsRebuild = (Button) this.findViewById(R.id.rebuild);

		// If the user touches anything, it's not idle
		layout.setOnTouchListener(mOnTouchListener);

		// If the user changes any text, it's not idle
		author.addTextChangedListener(mTextWatcher);
		title.addTextChangedListener(mTextWatcher);
		criteria.addTextChangedListener(mTextWatcher);

		// Handle button presses
		showResults.setOnClickListener(mShowResultsListener);
		ftsRebuild.setOnClickListener(mFtsRebuildListener);
	}

	/** start the idle timer */
    public void startIdleTimer()
    {
    	// Synchronize since this is relevant to more than 1 thread.
    	synchronized(SearchCatalogue.this) {
    		if (mTimer != null)
    			return;
            mTimer = new Timer();
        	mIdleStart = System.currentTimeMillis();
    	}
    	//create timer to tick every 200ms
        mTimer.schedule(new SearchUpdateTimer(), 0, 250);        		
    }

    /**
     * Stop the timer.
     */
	private void stopIdleTimer() {
		Timer tmr;
    	// Synchronize since this is relevant to more than 1 thread.
		synchronized(SearchCatalogue.this) {
			tmr = mTimer;
			mTimer = null;
		}
		if (tmr != null)
			tmr.cancel();
	}

	/**
	 * Class to implement a timer task and do a search when necessary, if idle.
	 * 
	 * If a search happens, we stop the idle timer.
	 * 
	 * @author Philip Warner
	 *
	 */
    private class SearchUpdateTimer extends TimerTask {
		@Override
		public void run() {
			boolean doSearch = false;
	    	// Synchronize since this is relevant to more than 1 thread.
			synchronized(SearchCatalogue.this) {
				long timeNow = System.currentTimeMillis();
				boolean idle = (timeNow - mIdleStart) > 1000;
				if (idle) {
					// Stop the timer, it will be restarted if the user changes something
					stopIdleTimer();
					if (mSearchDirty) {
						doSearch = true;
						mSearchDirty = false;
					}
				}
			}
			if (doSearch)
				doSearch();
		}
	};
    
	/**
	 * Handle the 'Search' button.
	 */
	private OnClickListener mShowResultsListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			doSearch();
		}
	};

	/**
	 * Handle the 'FTS Rebuild' button.
	 */
	private OnClickListener mFtsRebuildListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			mDbHelper.rebuildFts();
		}
	};

	/**
	 * Called in the timer thread, this code will run the search then queue the UI
	 * updates to the main thread.
	 */
	private void doSearch() {
		// Get search criteria
		String author = ((EditText) this.findViewById(R.id.author)).getText().toString();
		String title = ((EditText) this.findViewById(R.id.title)).getText().toString();
		String criteria = ((EditText) this.findViewById(R.id.criteria)).getText().toString();
		String tmpMsg;

		// Save time to log how long query takes.
		long t0 = System.currentTimeMillis();

		//BooksCursor c = mDbHelper.fetchAllBooks(""/*order*/, ""/*bookshelf*/,
		//		"(" + CatalogueDBAdapter.KEY_FAMILY_NAME + " like '%" + author + "%' " + CatalogueDBAdapter.COLLATION + " or " + CatalogueDBAdapter.KEY_GIVEN_NAMES + " like '%" + author + "%' " + CatalogueDBAdapter.COLLATION + ")", 
		//		"b." + CatalogueDBAdapter.KEY_TITLE + " like '%" + title + "%' " + CatalogueDBAdapter.COLLATION + ",
		//		""/*searchText*/, ""/*loaned_to*/, ""/*seriesName*/);	

		// Get the cursor
		Cursor c = null;
		try {
			c = mDbHelper.searchFts(author, title, criteria);
			if (c == null) {
				// Null return means searchFts thought parameters were effectively blank
				tmpMsg = "(enter search criteria)";
			} else {
				int count = c.getCount();
				c.close();
				t0 = System.currentTimeMillis() - t0;
				tmpMsg = "(" + count + " books found in " + t0 + "ms)";									
			}				
		} catch (Exception e) {
			tmpMsg = e.getMessage();
		} finally {
			// Cleanup cursor
			try {
				if (c != null)
					c.close();					
			} catch (Exception e) {}
		}
		final String message = tmpMsg;

		// Update the UI in main thread.
		m_handler.post(new Runnable(){
			@Override
			public void run() {
				TextView booksFound = (TextView) SearchCatalogue.this.findViewById(R.id.books_found);
				booksFound.setText(message);		
			}
		});
	}

	/**
	 * Called when a UI element detects the user doing something
	 * 
	 * @param dirty		Indicates the user action made the last search invalid
	 */
	private void userIsActive(boolean dirty) {
		synchronized(SearchCatalogue.this) {
			// Mark search dirty if necessary
			mSearchDirty = mSearchDirty || dirty;
			// Reset the idle timer since the user did something
			mIdleStart = System.currentTimeMillis();
			// If the search is dirty, make sure idle timer is running and update UI
			if (mSearchDirty) {
				TextView booksFound = (TextView) SearchCatalogue.this.findViewById(R.id.books_found);
				booksFound.setText("(waiting for idle)");
				startIdleTimer(); // (if not started)				
			}
		}
	}

	/**
	 * Detect when user touches something, just so we know they are 'busy'.
	 */
	private OnTouchListener mOnTouchListener = new OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			userIsActive(false);
			return false;
		}
	};

	/**
	 * Detect text changes and call userIsActive(...).
	 */
	private TextWatcher mTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable s) {
			userIsActive(true);
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
	};

	/**
	 * When activity pauses, stop timer.
	 */
	@Override
	protected void onPause() {
		super.onPause();
		stopIdleTimer();
	}

	/**
	 * When activity resumes, mark search as dirty
	 */
	@Override
	protected void onResume() {
		super.onResume();
		userIsActive(true);
	}

	/**
	 * Cleanup
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		try {
			if (mDbHelper != null)
				mDbHelper.close();
		} catch (Exception e) {}
		try {
			stopIdleTimer();
		} catch (Exception e) {}
	}
}
