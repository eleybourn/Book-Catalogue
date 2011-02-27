/*
 * @copyright 2011 Philip Warner
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

import android.os.Bundle;
import android.os.Message;

abstract public class SearchThread extends ManagedTask {
	protected String mAuthor;
	protected String mTitle;
	protected String mIsbn;

	// Accumulated book info.
	protected Bundle mBookData = new Bundle();

	/**
	 * Constructor. Will search according to passed parameters. If an ISBN
	 * is provided that will be used to the exclusion of all others.
	 * 
	 * @param ctx			Context
	 * @param taskHandler	TaskHandler implementation
	 * @param author		Author to search for
	 * @param title			Title to search for
	 * @param isbn			ISBN to search for.
	 */
	public SearchThread(TaskManager manager, TaskHandler taskHandler, String author, String title, String isbn) {
		super(manager, taskHandler);
		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;

		mBookData.putString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, mAuthor);
		mBookData.putString(CatalogueDBAdapter.KEY_TITLE, mTitle);
		mBookData.putString(CatalogueDBAdapter.KEY_ISBN, mIsbn);
	}

	/**
	 * Task handler for thread management; caller MUST implement this to get
	 * search results.
	 * 
	 * @author Grunthos
	 */
	public interface SearchHandler extends ManagedTask.TaskHandler {
		void onFinish(SearchThread t, Bundle bookData, boolean cancelled);
	}

	@Override
	protected boolean onFinish() {
		doProgress("Done",0);
		if (getTaskHandler() != null) {
			((SearchHandler)getTaskHandler()).onFinish(this, mBookData, isCancelled());				
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onMessage(Message msg) {
	}

	/**
	 * Try to extract a series from a book title.
	 * TODO: Consider removing findSeries if LibraryThing proves reliable.
	 * 
	 * @param 	title	Book title to parse
	 * @return
	 */
	public String findSeries(String title) {
		String series = "";
		int last = title.lastIndexOf("(");
		int close = title.lastIndexOf(")");
		if (last > -1 && close > -1 && last < close) {
			series = title.substring((last+1), close);
		}
		return series;
	}

	/**
	 * Look in the data for a title, if present try to get a series name from it.
	 * In any case, clear the title (and save if none saved already) so that the 
	 * next lookup will overwrite with a possibly new title.
	 */
	protected void checkForSeriesName() {
		try {
			if (mBookData.containsKey(CatalogueDBAdapter.KEY_TITLE)) {
				String thisTitle = mBookData.getString(CatalogueDBAdapter.KEY_TITLE);
				String tmpSeries = findSeries(thisTitle);
				if (tmpSeries != null && tmpSeries.length() > 0)
					Utils.appendOrAdd(mBookData, CatalogueDBAdapter.KEY_SERIES_DETAILS, tmpSeries);				
			}							
		} catch (Exception e) {
			BookCatalogue.logError(e);
		};		
	}
	
	protected void showException(int id, Exception e) {
		String s;
		try {s = e.getMessage(); } catch (Exception e2) {s = "Unknown Exception";};
		String msg = String.format(getString(R.string.search_exception), getString(id), s);
		doToast(msg);		
	}
}
