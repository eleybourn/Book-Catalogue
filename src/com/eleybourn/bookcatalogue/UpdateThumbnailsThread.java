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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;
import com.eleybourn.bookcatalogue.UpdateFromInternet.FieldUsage;
import com.eleybourn.bookcatalogue.UpdateFromInternet.FieldUsages;
import com.eleybourn.bookcatalogue.UpdateFromInternet.FieldUsages.Usages;
import com.eleybourn.bookcatalogue.Utils.ItemWithIdFixup;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

/**
 * Class to update all thumbnails (and some other data) in a background thread.
 *
 * @author Grunthos
 */
public class UpdateThumbnailsThread extends ManagedTask implements SearchManager.SearchResultHandler {
	private FieldUsages mFieldUsages;

	// Lock help by pop and by push when an item was added to an empty stack.
	private final ReentrantLock mSearchLock = new ReentrantLock();
	// Signal for available items
	private final Condition mSearchDone = mSearchLock.newCondition();

	public static String filePath = Utils.EXTERNAL_FILE_PATH;
	public static String fileName = filePath + "/export.csv";
	public static String UTF8 = "utf8";
	public static int BUFFER_SIZE = 8192;
	private String mFinalMessage;
	// Shortcut value derived from mFieldUsages
	private boolean mFetchingThumbnails;

	// Data related to current row being processed
	// - Original row data
	private Bundle mOrigData = null;
	// - current book ID
	private long mCurrId = 0;

	// Active search manager
	private SearchManager mSearchManager = null;

	// DB connection
	protected CatalogueDBAdapter mDbHelper;

	public interface LookupHandler extends ManagedTask.TaskHandler {
		void onFinish();
	}

	/**
	 * Constructor.
	 * 
	 * @param ctx				Context to use for constructing progressdialog
	 * @param fieldHash			Hastable containing entries for fields to update
	 * @param overwrite			Whether to overwrite details
	 * @param books				Cursor to scan
	 * @param lookupHandler		Interface object to handle events in this thread.
	 * 
	 */
	public UpdateThumbnailsThread(TaskManager manager, FieldUsages fieldUsages, LookupHandler lookupHandler) {
		super(manager, lookupHandler);
		mDbHelper = new CatalogueDBAdapter(manager.getContext());
		mDbHelper.open();

		mFieldUsages = fieldUsages;
		mSearchManager = new SearchManager(mManager, this);
		mFetchingThumbnails = (mFieldUsages.containsKey(CatalogueDBAdapter.KEY_THUMBNAIL) && mFieldUsages.get("thumbnail").selected);
	}

	@Override
	public void onRun() throws InterruptedException {
		int counter = 0;

		mManager.setMax(this, 1);
		mManager.doProgress(this, mManager.getString(R.string.starting_search), 0);

		/* Test write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath + "/.nomedia"), UTF8), BUFFER_SIZE);
			out.write("");
			out.close();
		} catch (IOException e) {
			Logger.logError(e);
			mFinalMessage = getString(R.string.thumbnail_failed_sdcard);
			return;
		}
		
		Cursor books = mDbHelper.fetchAllBooks("b." + CatalogueDBAdapter.KEY_ROWID, "All Books", "", "", "", "", "");
		mManager.setMax(this, books.getCount());
		try {
			while (books.moveToNext() && !isCancelled()) {

				// Copy the fields from the cursor
				mOrigData = new Bundle();
				for(int i = 0; i < books.getColumnCount(); i++) {
					mOrigData.putString(books.getColumnName(i), books.getString(i));
				}
				// Get the book ID
				mCurrId = Utils.getAsLong(mOrigData, CatalogueDBAdapter.KEY_ROWID);
				// Get the extra data about the book
				mOrigData.putParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mDbHelper.getBookAuthorList(mCurrId));
				mOrigData.putParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY, mDbHelper.getBookSeriesList(mCurrId));

				counter++;
				if (mFetchingThumbnails) {
					// delete any tmp thumbnails //
					try {
						File delthumb = CatalogueDBAdapter.fetchThumbnail(0);
						delthumb.delete();
					} catch (Exception e) {
						// do nothing - this is the expected behaviour 
					}					
				}

				String isbn = mOrigData.getString(CatalogueDBAdapter.KEY_ISBN);
				String author = mOrigData.getString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
				String title = mOrigData.getString(CatalogueDBAdapter.KEY_TITLE);
				boolean tmpWantThumb = mFetchingThumbnails;
				
				if (tmpWantThumb && mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL).usage == Usages.COPY_IF_BLANK) {
					File file = CatalogueDBAdapter.fetchThumbnail(mCurrId);
					tmpWantThumb = (!file.exists() || file.length() == 0);
				}

				if (isbn.equals("") && (author.equals("") || title.equals(""))) {
					mManager.doProgress(this, String.format(getString(R.string.skip_title), title), counter);
				} else {
					mSearchManager.search(author, title, isbn, tmpWantThumb);
					if (title.length() > 0)
						mManager.doProgress(title);
					else
						mManager.doProgress(isbn);
				}
				mManager.doProgress(this, null, counter);

				// Wait for the search to complete; when the search has completed it uses class-level state
				// data when processing the results. It will signal this lock when it no longer needs any class
				// level state data (eg. mOrigData).
				Log.i("BC", "Waiting search done " + mCurrId);
				mSearchLock.lock();
				try {
					mSearchDone.await();
				} finally {
					mSearchLock.unlock();					
				}
				Log.i("BC", "Got search done " + mCurrId + " in " + Thread.currentThread().toString());

			}
		} finally {
			if (books != null && !books.isClosed())
				books.close();
			mManager.doProgress(null);

			mFinalMessage = String.format(getString(R.string.num_books_searched), "" + counter);
			if (isCancelled()) 
				mFinalMessage = String.format(getString(R.string.cancelled_info), mFinalMessage);
		}
	}

	@Override
	protected boolean onFinish() {
		mManager.doToast(mFinalMessage);
		if (getTaskHandler() != null) {
			((LookupHandler)getTaskHandler()).onFinish();
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onMessage(Message msg) {
	}

	/**
	 * Called in the main thread for this object when a search has completed.
	 *
	 * @param bookData
	 * @param cancelled
	 */
	@Override
	public void onSearchFinished(Bundle bookData, boolean cancelled) {
		// Set cancelled flag if the task was cancelled
		if (cancelled) {
			cancelTask();
		} else if (bookData == null) {
			mManager.doToast("Unable to find book details");
		}

		// Save the local data from the context so we can start a new search
		long rowId = mCurrId;
		Bundle origData = mOrigData;

		// Dispatch to UI thread so we can fire the lock...can't do from same thread.
		mMessageHandler.post(new Runnable() {
			@Override
			public void run() {
				doSearchDone();
			}});

		if (!isCancelled() && bookData != null)
			processSearchResults(rowId, bookData, origData);
	}

	/**
	 * Passed the old & new data, construct the update data and perform the update.
	 * 
	 * @param rowId		Book ID
	 * @param newData	Data gathered from internet
	 * @param origData	Original data
	 */
	private void processSearchResults(long rowId, Bundle newData, Bundle origData) {
		// First, filter the data to remove keys we don't care about
		ArrayList<String> toRemove = new ArrayList<String>();
		for(String key : newData.keySet()) {
			if (!mFieldUsages.containsKey(key) || !mFieldUsages.get(key).selected)
				toRemove.add(key);
		}
		for(String key : toRemove) {
			newData.remove(key);				
		}

		// For each field, process it according the the usage. 
		for(FieldUsage usage : mFieldUsages.values()) {
			if (newData.containsKey(usage.fieldName)) {
				// Handle thumbnail specially
				if (usage.fieldName.equals(CatalogueDBAdapter.KEY_THUMBNAIL)) {
					File downloadedFile = CatalogueDBAdapter.fetchThumbnail(0);
					boolean copyThumb = false;
					if (usage.usage == Usages.COPY_IF_BLANK) {
						File file = CatalogueDBAdapter.fetchThumbnail(rowId);
						copyThumb = (!file.exists() || file.length() == 0);
					} else if (usage.usage == Usages.OVERWRITE) {
						copyThumb = true;
					}
					if (copyThumb) {
						File file = CatalogueDBAdapter.fetchThumbnail(rowId);
						downloadedFile.renameTo(file);
					} else {
						downloadedFile.delete();
					}
				} else {
					switch(usage.usage) {
					case OVERWRITE:
						// Nothing to do; just use new data
						break;
					case COPY_IF_BLANK:
						// Handle special cases
						if (usage.fieldName.equals(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)) {
							if (origData.containsKey(usage.fieldName)) {
								ArrayList<Author> origAuthors = origData.getParcelableArrayList(usage.fieldName);
								if (origAuthors != null && origAuthors.size() > 0)
									newData.remove(usage.fieldName);								
							}
						} else if (usage.fieldName.equals(CatalogueDBAdapter.KEY_SERIES_ARRAY)) {
							if (origData.containsKey(usage.fieldName)) {
								ArrayList<Series> origSeries = origData.getParcelableArrayList(usage.fieldName);
								if (origSeries != null && origSeries.size() > 0)
									newData.remove(usage.fieldName);								
							}
						} else {
							// If the original was non-blank, erase from list
							if (origData.containsKey(usage.fieldName) && origData.getString(usage.fieldName) != null && origData.getString(usage.fieldName).length() > 0 )
								newData.remove(usage.fieldName);
						}
						break;
					case ADD_EXTRA:
						// Handle arrays
						if (usage.fieldName.equals(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)) {
							UpdateThumbnailsThread.<Author>combineArrays(usage.fieldName,origData, newData);
						} else if (usage.fieldName.equals(CatalogueDBAdapter.KEY_SERIES_ARRAY)) {
							UpdateThumbnailsThread.<Series>combineArrays(usage.fieldName,origData, newData);
						} else {
							// No idea how to handle this for non-arrays
							throw new RuntimeException("Illegal usage '" + usage.usage + "' specified for field '" + usage.fieldName + "'");
						}
						break;
					}
					
				}
			}
		}

		// Update
		if (newData.size() > 0)
			mDbHelper.updateBook(rowId, newData, true);
		
	}

	private static<T extends Parcelable> void combineArrays(String key, Bundle origData, Bundle newData) {
		// Each of the lists to combine
		ArrayList<T> origList = null;
		ArrayList<T> newList = null;
		// Get the list from the original, if present. 
		if (origData.containsKey(key)) {
			origList = origData.getParcelableArrayList(key);
		}
		// Otherwise an empty list
		if (origList == null)
			origList = new ArrayList<T>();

		// Get from the new data
		if (newData.containsKey(key)) {
			newList = newData.getParcelableArrayList(key);			
		}
		if (newList == null)
			newList = new ArrayList<T>();
		origList.addAll(newList);
		// Save combined version to the new data
		newData.putParcelableArrayList(key, origList);
	}
	private void doSearchDone() {
		// Let another search begin
		mSearchLock.lock();
		try {
			Log.i("BC", "Signalling search done " + mCurrId + " in " + Thread.currentThread().toString());
			mSearchDone.signal();
		} finally {
			mSearchLock.unlock();			
		}
	}
	
	@Override
	protected void finalize() {
		mDbHelper.close();
	}
	
	public TaskHandler getTaskHandler(ManagedTask t) {
		return mSearchManager.getTaskHandler(t);
	}

}
