package com.eleybourn.bookcatalogue;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Activity for representing only read-only details of the book.
 * @author n.silin
 */
public class BookDetails extends Activity {

	private CatalogueDBAdapter mDbHelper;

	private long mRowId = -1;
	
	
	// Target size of a thumbnail in edit dialog and zoom dialog (bbox dim)
	private static final int MAX_EDIT_THUMBNAIL_SIZE = 256;
	private Integer mThumbEditSize;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.book_details);

		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);

		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();

		if (getIntent().hasExtra(CatalogueDBAdapter.KEY_ROWID)) {
			mRowId = getIntent().getLongExtra(CatalogueDBAdapter.KEY_ROWID, -1);
		}

		// See how big the display is and use that to set bitmap sizes
		android.util.DisplayMetrics mMetrics = new android.util.DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
		// Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension
		mThumbEditSize = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels) / 3);

		initViews();
		Tracker.exitOnCreate(this);
	}

	private void initViews() {
		initThumbnail();
		populateFields(mRowId);
	}

	private void initThumbnail() {
		ImageView iv = (ImageView) findViewById(R.id.row_img);
		Utils.fetchFileIntoImageView(getCoverFile(), iv, mThumbEditSize, mThumbEditSize, true);
	}

	/**
	 * Get the File object for the cover of the book we are editing. If the boo
	 * is new, return the standard temp file.
	 */
	private File getCoverFile() {
		if (mRowId < 0) {
			return CatalogueDBAdapter.getTempThumbnail();
		} else {
			return CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(mRowId));
		}
	}
	
	/**
	 * This function will populate the forms elements in three different ways
	 * 1. If a valid rowId exists it will populate the fields from the database
	 * 2. If fields have been passed from another activity (e.g. ISBNSearch) it will populate the fields from the bundle
	 * 3. It will leave the fields blank for new books.
	 */
	private void populateFields(long rowId) {
		if (rowId > 0) {
			BooksCursor book = mDbHelper.fetchBookById(rowId);
			Cursor bookshelves = null;
			try {
				if (book != null) {
					book.moveToFirst();
				}
				
				BooksRowView rowView = book.getRowView();
				String author = rowView.getPrimaryAuthorName();
				ArrayList<Author> authorList = mDbHelper.getBookAuthorList(rowId);
				Log.i("Details", authorList.toString());
				if(author != null){
					((TextView) findViewById(R.id.author)).setText(author);
				}
				// Set any field that has a 'column' non blank.
//				mFields.setFromCursor(book);
//				
//				getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + mFields.getField(R.id.title).getValue().toString());
//				
//				//Display the selected bookshelves
//				Field bookshelfTextFe = mFields.getField(R.id.bookshelf_text);
//				bookshelves = mDbHelper.fetchAllBookshelvesByBook(mRowId);
//				String bookshelves_text = "";
//				String bookshelves_list = "";
//				while (bookshelves.moveToNext()) {
//					String name = bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF));
//					String encoded_name = Utils.encodeListItem(name, BOOKSHELF_SEPERATOR);
//					if (bookshelves_text.equals("")) {
//						bookshelves_text = name;
//						bookshelves_list = encoded_name;
//					} else {
//						bookshelves_text += ", " + name;
//						bookshelves_list += BOOKSHELF_SEPERATOR + encoded_name;
//					}
//				}
//				bookshelfTextFe.setValue(bookshelves_text);
//				bookshelfTextFe.setTag(bookshelves_list);
//
//				Integer anthNo = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
//				mFields.getField(R.id.anthology).setValue(anthNo.toString());
//				
//				ImageView iv = (ImageView) findViewById(R.id.row_img);
//				Utils.fetchFileIntoImageView(getCoverFile(), iv, mThumbEditSize, mThumbEditSize, true );				
			} finally {	
				if (book != null)
					book.close();
				if (bookshelves != null)
					bookshelves.close();
			}
			
//			mAuthorList = mDbHelper.getBookAuthorList(mRowId);
//			mSeriesList = mDbHelper.getBookSeriesList(mRowId);
			
		} 
//		else if (extras != null) {
//			getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " + this.getResources().getString(R.string.menu_insert));
//			// From the ISBN Search (add)
//			try {
//				if (extras.containsKey("book")) {
//					throw new RuntimeException("[book] array passed in Intent");
//				} else {
//					Bundle values = (Bundle)extras.getParcelable("bookData");
//					Iterator<Fields.Field> i = mFields.iterator();
//					while(i.hasNext()) {
//						Fields.Field f = i.next();
//						if (!f.column.equals("") && values.containsKey(f.column)) {
//							try {
//								f.setValue(values.getString(f.column));								
//							} catch (Exception e) {
//								String msg = "Populate field " + f.column + " failed: " + e.getMessage();
//								Logger.logError(e, msg);
//							}
//						}
//					}
//
//					initDefaultShelf();
//
//					// Author/Series
//					mAuthorList = Utils.getAuthorsFromBundle(values);
//					mSeriesList = Utils.getSeriesFromBundle(values);
//				}
//				
//			} catch (NullPointerException e) {
//				Logger.logError(e);
//			}
//			setCoverImage();
//		} 
		
//		fixupAuthorList();
//		fixupSeriesList();

	}
}
