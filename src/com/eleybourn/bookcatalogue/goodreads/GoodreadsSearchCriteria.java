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

package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity to handle searching goodreads for books that did not automatically convert. These
 * are typically books with no ISBN.
 * 
 * The search criteria is setup to contain the book author, title and ISBN. The user can edit
 * these and search goodreads, then review the results.
 * 
 * @author Philip Warner
 */
public class GoodreadsSearchCriteria extends Activity {
	public static final String EXTRA_BOOK_ID = "bookId";

	private CatalogueDBAdapter mDbHelper;
	private long mBookId = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Setup DB and layout.
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		setContentView(R.layout.goodreads_search_criteria);

		// Initial value; try to build from passed book, if available.
		String criteria = "";

		Bundle extras = this.getIntent().getExtras();

		// Look for a book ID
		if (extras != null && extras.containsKey(EXTRA_BOOK_ID)) {
			mBookId = extras.getLong(EXTRA_BOOK_ID);
		}

		// If we have a book, fill in criteria AND try a search
		if (mBookId != 0) {
			setViewVisibility(R.id.original_details, true);
			final BooksCursor c = mDbHelper.fetchBookById(mBookId);
			final BooksRowView book = c.getRowView();
			try 
			{
				if (!c.moveToFirst()) {
					Toast.makeText(this, getString(R.string.book_no_longer_exists), Toast.LENGTH_LONG).show();
					finish();
					return;					
				}
				{
					String s = book.getPrimaryAuthorName();
					setViewText(R.id.author, s);
					criteria += s + " ";
				}
				{
					String s = book.getTitle();
					setViewText(R.id.title, s);
					criteria += s + " ";
				}
				{
					String s = book.getIsbn();
					setViewText(R.id.isbn, s);
					criteria += s + " ";
				}
			} finally {
				c.close();
			}
			criteria = criteria.trim();

			setViewText(R.id.search_text, criteria.trim());
			doSearch();
		} else {
			setViewVisibility(R.id.original_details, false);			
		}

		setClickListener(R.id.search, new OnClickListener() {
			@Override
			public void onClick(View v) {
				doSearch();
			}});
		
	}

	/**
	 * Set the visibility of the passed view.
	 */
	private void setViewVisibility(int id, boolean visible) {
		int flag;
		if (visible) {
			flag = View.VISIBLE;
		} else {
			flag = View.GONE;
		}
		this.findViewById(id).setVisibility(flag);
	}

	/**
	 * Set the text of the passed view
	 */
	private void setViewText(int id, String s) {
		((TextView)this.findViewById(id)).setText(s);
	}
	
	/**
	 * Get the text of the passed view
	 */
	private String getViewText(int id) {
		return ((TextView)this.findViewById(id)).getText().toString();
	}

	/**
	 * Set the OnClickListener for the passed view
	 */
	private void setClickListener(int id, OnClickListener listener) {
		((View)this.findViewById(id)).setOnClickListener(listener);
	}


	/**
	 * Start the search results activity.
	 */
	private void doSearch() {
		String criteria = getViewText(R.id.search_text).trim();

		if (criteria.equals("")) {
			Toast.makeText(this, getString(R.string.please_enter_search_criteria), Toast.LENGTH_LONG).show();
			return;
		}

		Intent i = new Intent(this, GoodreadsSearchResults.class);
		i.putExtra(GoodreadsSearchResults.SEARCH_CRITERIA, criteria);
		this.startActivity(i);
	}

	/**
	 * Cleanup
	 */
	@Override 
	public void onDestroy() {
		super.onDestroy();
		if (mDbHelper != null)
			mDbHelper.close();
	}
}
