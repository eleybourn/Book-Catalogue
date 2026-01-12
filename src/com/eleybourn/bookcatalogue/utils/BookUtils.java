/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.eleybourn.bookcatalogue.BookCatalogueAPI;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.BookEdit;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.io.File;
import java.lang.ref.WeakReference;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK_UUID;

/**
 * Class to implement common Book functions
 * 
 * @author pjw
 */
public class BookUtils {
	private static StaticApiListener mApiListener;

	/**
	 * Open a new book editing activity with fields copied from saved book.
	 * Saved book (original of duplicating) is defined by its row _id in database.
	 * @param rowId The id of the book to copy fields
	 */
	public static void duplicateBook(Activity activity, CatalogueDBAdapter db, Long rowId){
		if (rowId == null || rowId == 0) {
			Toast.makeText(activity, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
		}
		Intent i = new Intent(activity, BookEdit.class);
		BookData thisBook = new BookData(rowId);

		File currThumb = CatalogueDBAdapter.fetchThumbnailByUuid(thisBook.getString(DOM_BOOK_UUID.name));
		File tmpThumb = CatalogueDBAdapter.getTempThumbnail();
		if (currThumb.exists()) {
			try {
				Utils.copyFile(currThumb, tmpThumb);
			} catch (Exception e) {
				Logger.logError(e);
				throw new RuntimeException(e);
			}
		}

		// Get the raw data and remove the key info
		Bundle book = thisBook.getRawData();
		book.remove(CatalogueDBAdapter.KEY_BOOK);
		book.remove(DOM_BOOK_UUID.name);

		i.putExtra("bookData", book);
		activity.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY);
	}
	
	/**
	 * Delete book by its database row _id and close current activity. 
	 * @param rowId The database id of the book for deleting
	 */
	public static void deleteBook(Context context, final CatalogueDBAdapter dbHelper, Long rowId, final Runnable runnable){
		mApiListener = new StaticApiListener(context);
		if (rowId == null || rowId == 0) {
			Toast.makeText(context, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
			return;
		}
		int res = StandardDialogs.deleteBookAlert(context, dbHelper, rowId, new Runnable() {
			@Override
			public void run() {
				dbHelper.purgeAuthors();
				dbHelper.purgeSeries();
				new BookCatalogueAPI(BookCatalogueAPI.REQUEST_DELETE_BOOK, rowId, mApiListener);
				if (runnable != null)
					runnable.run();
			}});
		if (res != 0) {
			Toast.makeText(context, res, Toast.LENGTH_LONG).show();
		}
	}
	
	/**
	 * Perform sharing of book by its database rowId. Create chooser with matched 
	 * apps for sharing some text like the next:<br>
	 * <b>"I'm reading " + title + " by " + author + series + " " + ratingString</b>
	 * @param rowId The database id of the book for deleting
	 */
	public static void shareBook(Context context, final CatalogueDBAdapter dbHelper, Long rowId){
		if (rowId == null || rowId == 0) {
			Toast.makeText(context, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
			return;
		}
		
		Cursor thisBook = dbHelper.fetchBookById(rowId);
		thisBook.moveToFirst();
		String title = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_TITLE));
		double rating = thisBook.getDouble(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_RATING));
		String ratingString = "";
		String author = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED_GIVEN_FIRST));
		String series = thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_SERIES_FORMATTED));
		File image = CatalogueDBAdapter.fetchThumbnailByUuid(dbHelper.getBookUuid(rowId));

		if (series.length() > 0) {
			series = " (" + series + ")";
		}
		//remove trailing 0's
		if (rating > 0) {
			int ratingTmp = (int)rating;
			double decimal = rating - ratingTmp;
			if (decimal > 0) {
				ratingString = rating + "/5";
			} else {
				ratingString = ratingTmp + "/5";
			}
		}
		
		if (ratingString.length() > 0){
			ratingString = "(" + ratingString + ")";
		}

		/*
		 * There's a problem with the facebook app in android, so despite it being shown on the list
		 * it will not post any text unless the user types it.
		 */
		Intent share = new Intent(Intent.ACTION_SEND); 
		//TODO Externalize hardcoded text below to allow translating and simple editing
		String text = "I'm reading " + title + " by " + author + series + " " + ratingString;
		share.putExtra(Intent.EXTRA_TEXT, text);
		share.putExtra(Intent.EXTRA_TITLE, title);
		share.putExtra(Intent.EXTRA_SUBJECT, title);
		Uri u = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", image);
		share.putExtra(Intent.EXTRA_STREAM, u);
        share.setType("text/plain");
        
        context.startActivity(Intent.createChooser(share, "Share"));
	}

	// Define the listener as a static inner class
	private static class StaticApiListener implements BookCatalogueAPI.ApiListener {
		private final WeakReference<Context> contextReference;

		StaticApiListener(Context context) {
			// Use a WeakReference to avoid memory leaks
			this.contextReference = new WeakReference<>(context);
		}

		@Override
		public void onApiProgress(String request, int current, int total) {
			Context context = contextReference.get();
			// Only update UI if the activity is still alive
			if (context == null) {
				return;
			}
			Log.d("BookUtils", "API Progress for " + request + ": " + current + "/" + total);
		}

		@Override
		public void onApiComplete(String request, String message) {
			Context context = contextReference.get();
			if (context == null) {
				return;
			}
			Log.d("BookUtils", "API Complete for " + request + ": " + message);
		}

		@Override
		public void onApiError(String request, String error) {
			Context context = contextReference.get();
			if (context == null) {
				return;
			}
			Log.e("BookUtils", "API Error for " + request + ": " + error);
		}
	}

}
