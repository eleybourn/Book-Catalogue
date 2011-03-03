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

import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Class to update all thumbnails (and some other data) in a background thread.
 *
 * @author Grunthos
 */
public class UpdateThumbnailsThread extends ManagedTask implements SearchManager.SearchResultHandler {
	private boolean mOverwrite = false;
	private Hashtable<String,Boolean> mFieldHash;

	// Lock help by pop and by push when an item was added to an empty stack.
	private final ReentrantLock mSearchLock = new ReentrantLock();
	// Signal for available items
	private final Condition mSearchDone = mSearchLock.newCondition();

	public static String filePath = Utils.EXTERNAL_FILE_PATH;
	public static String fileName = filePath + "/export.csv";
	public static String UTF8 = "utf8";
	public static int BUFFER_SIZE = 8192;
	private String mFinalMessage;

	private long mCurrId = 0;

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
	public UpdateThumbnailsThread(TaskManager manager, Hashtable<String,Boolean> fieldHash, boolean overwrite, LookupHandler lookupHandler) {
		super(manager, lookupHandler);
		mDbHelper = new CatalogueDBAdapter(manager.getContext());
		mDbHelper.open();

		mFieldHash = fieldHash;
		mOverwrite = overwrite;
		mSearchManager = new SearchManager(mManager, this);
	}

	@Override
	public void onRun() {
		int counter = 0;

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
				Bundle origData = new Bundle();
				for(int i = 0; i < books.getColumnCount(); i++) {
					origData.putString(books.getColumnName(i), books.getString(i));
				}

				counter++;
				// delete any tmp thumbnails //
				try {
					File delthumb = CatalogueDBAdapter.fetchThumbnail(0);
					delthumb.delete();
				} catch (Exception e) {
					// do nothing - this is the expected behaviour 
				}

				mCurrId = Utils.getAsLong(origData, CatalogueDBAdapter.KEY_ROWID);
				String isbn = origData.getString(CatalogueDBAdapter.KEY_ISBN);
				String author = origData.getString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
				String title = origData.getString(CatalogueDBAdapter.KEY_TITLE);

				if (isbn.equals("") && (author.equals("") || title.equals(""))) {
					mManager.doProgress(this, String.format(getString(R.string.skip_title), title), counter);
				} else {
					mSearchManager.search(author, title, isbn);
					if (title.length() > 0)
						mManager.doProgress(title);
					else
						mManager.doProgress(isbn);
					mManager.doProgress(this, null, counter);
				}
				
				// Wait for the search to complete.
				Log.i("BC", "Waiting search done " + mCurrId);
				mSearchLock.lock();
				try {
					mSearchDone.await();
				} finally {
					mSearchLock.unlock();					
				}
				Log.i("BC", "Got search done " + mCurrId + " in " + Thread.currentThread().toString());

			}

		} catch (Exception e) {
			Logger.logError(e);
		} finally {
			if (books != null && !books.isClosed())
				books.close();
			mManager.doProgress(null);
		}
		mFinalMessage = String.format(getString(R.string.num_books_searched), "" + counter);
		if (isCancelled()) 
			mFinalMessage = String.format(getString(R.string.cancelled_info), mFinalMessage);
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
	public void onFinish(Bundle bookData, boolean cancelled) {
		// Set cancelled flag
		if (cancelled) {
			cancelTask();
		} else if (bookData == null) {
			mManager.doToast("Unable to find book details");
		}

		// Save the local ID
		long rowId = mCurrId;

		// Dispatch to UI thread so we can fire the lock...can't do from same thread.
		mMessageHandler.post(new Runnable() {
			@Override
			public void run() {
				doSearchDone();
			}});

		if (!isCancelled() && bookData != null) {
			// Filter the data
			ArrayList<String> toRemove = new ArrayList<String>();
			for(String key : bookData.keySet()) {
				if (!mFieldHash.containsKey(key))
					toRemove.add(key);
			}
			for(String key : toRemove) {
				bookData.remove(key);				
			}
			// Update
			if (bookData.size() > 0)
				mDbHelper.updateBook(rowId, bookData, true);
		}
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
}
