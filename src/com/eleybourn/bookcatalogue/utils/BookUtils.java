package com.eleybourn.bookcatalogue.utils;

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookEdit;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

public class BookUtils {
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
		Bundle book = new Bundle();
		Cursor thisBook = db.fetchBookById(rowId);
		try {
			thisBook.moveToFirst();
			book.putString(CatalogueDBAdapter.KEY_TITLE, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_TITLE)));
			book.putString(CatalogueDBAdapter.KEY_ISBN, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_ISBN)));
			book.putString(CatalogueDBAdapter.KEY_PUBLISHER, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_PUBLISHER)));
			book.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_DATE_PUBLISHED)));
			book.putString(CatalogueDBAdapter.KEY_RATING, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_RATING)));
			book.putString(CatalogueDBAdapter.KEY_READ, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_READ)));
			book.putString(CatalogueDBAdapter.KEY_PAGES, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_PAGES)));
			book.putString(CatalogueDBAdapter.KEY_NOTES, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_NOTES)));
			book.putString(CatalogueDBAdapter.KEY_LIST_PRICE, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_LIST_PRICE)));
			book.putString(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK)));
			book.putString(CatalogueDBAdapter.KEY_LOCATION, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_LOCATION)));
			book.putString(CatalogueDBAdapter.KEY_READ_START, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_READ_START)));
			book.putString(CatalogueDBAdapter.KEY_READ_END, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_READ_END)));
			book.putString(CatalogueDBAdapter.KEY_FORMAT, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_FORMAT)));
			book.putString(CatalogueDBAdapter.KEY_SIGNED, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_SIGNED)));
			book.putString(CatalogueDBAdapter.KEY_DESCRIPTION, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_DESCRIPTION)));
			book.putString(CatalogueDBAdapter.KEY_GENRE, thisBook.getString(thisBook.getColumnIndex(CatalogueDBAdapter.KEY_GENRE)));
			
			book.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, db.getBookAuthorList(rowId));
			book.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, db.getBookSeriesList(rowId));
			
			i.putExtra("bookData", book);
			activity.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY);
		} catch (CursorIndexOutOfBoundsException e) {
			Toast.makeText(activity, R.string.unknown_error, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		}
	}
	
	/**
	 * Delete book by its database row _id and close current activity. 
	 * @param rowId The database id of the book for deleting
	 */
	public static void deleteBook(Context context, final CatalogueDBAdapter dbHelper, Long rowId, final Runnable runnable){
		if (rowId == null || rowId == 0) {
			Toast.makeText(context, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
			return;
		}
		int res = StandardDialogs.deleteBookAlert(context, dbHelper, rowId, new Runnable() {
			@Override
			public void run() {
				dbHelper.purgeAuthors();
				dbHelper.purgeSeries();
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
			series = " (" + series.replace("#", "%23") + ")";
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
		share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + image.getPath()));
        share.setType("text/plain");
        
        context.startActivity(Intent.createChooser(share, "Share"));
	}


}
