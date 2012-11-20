package com.eleybourn.bookcatalogue;

import java.io.File;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ImageView;

/**
 * 
 * 
 */
public abstract class BookDetails extends Activity /* implements OnRestoreTabInstanceStateListener */{

	public static final Character BOOKSHELF_SEPERATOR = ',';
	
	// Target size of a thumbnail in edit dialog and zoom dialog (bbox dim)
	private static final int MAX_EDIT_THUMBNAIL_SIZE = 256;
	private static final int MAX_ZOOM_THUMBNAIL_SIZE=1024;
	
	protected Long mRowId;
	protected Fields mFields = null;
	protected CatalogueDBAdapter mDbHelper;
	
	protected android.util.DisplayMetrics mMetrics;
	protected Integer mThumbEditSize;
	protected Integer mThumbZoomSize;
	
	@Override
	/* Note that you should use setContentView() method in descendant before
	 * running this. */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// See how big the display is and use that to set bitmap sizes
		getDisplayMetrics();
		initThumbSizes();
		
		initFields();
		
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
		
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
	}
	
	/**
	 * Fix background
	 */
	@Override 
	protected void onResume() {
		Tracker.enterOnResume(this);
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);		
		Tracker.exitOnResume(this);
	}
	
	@Override
	protected void onDestroy() {
		Tracker.enterOnDestroy(this);
		super.onDestroy();
		mDbHelper.close();
		Tracker.exitOnDestroy(this);
	}
	
	/**
	 * Get the File object for the cover of the book we are editing. If the boo
	 * is new, return the standard temp file.
	 */
	protected File getCoverFile(Long rowId) {
		if (rowId == null || rowId == 0) 
			return CatalogueDBAdapter.getTempThumbnail();
		else
			return CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(rowId));			
	}
	
	/**
	 * Set and return {@link #mRowId} value by activity intent extras. Return:<br> 
	 * <b>null</b> if there is no extars, <br> 
	 * <b>0</b> if extras is exist but there is no {@link CatalogueDBAdapter#KEY_ROWID} extra,<br>
	 * <b>rowId</b> if all is OK  
	 */
	protected Long setRowIdByExtras() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		return mRowId;
	}
	
	/**
	 * Add all book fields with corresponding validators.
	 */
	protected void initFields() {
		mFields = new Fields(this);

		// Generic validators; if field-specific defaults are needed, create a new one.
		FieldValidator integerValidator = new Fields.IntegerValidator("0");
		FieldValidator nonBlankValidator = new Fields.NonBlankValidator();
		FieldValidator blankOrIntegerValidator = new Fields.OrValidator(new Fields.BlankValidator(),
				new Fields.IntegerValidator("0"));
		FieldValidator blankOrFloatValidator = new Fields.OrValidator(new Fields.BlankValidator(),
				new Fields.FloatValidator("0.00"));
		// FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(),
		// new Fields.DateValidator());

		/* Title has some post-processing on the text, to move leading 'A', 'The' etc to the end.
		 * While we could do it in a formatter, it it not really a display-oriented function and
		 * is handled in preprocessing in the database layer since it also needs to be applied
		 * to imported record etc. */
		mFields.add(R.id.title, CatalogueDBAdapter.KEY_TITLE, nonBlankValidator);

		/* Anthology needs special handling, and we use a formatter to do this. If the original
		 * value was 0 or 1, then
		 * setting/clearing it here should just set the new value to 0 or 1.
		 * However...if if the original value was 2, then we want setting/clearing to alternate
		 * between 2 and 0, not 1
		 * and 0.
		 * So, despite if being a checkbox, we use an integerValidator and use a special formatter.
		 * We also store it in the tag field so that it is automatically serialized with the
		 * activity. */
		mFields.add(R.id.anthology, CatalogueDBAdapter.KEY_ANTHOLOGY, integerValidator, new Fields.FieldFormatter() {
			public String format(Field f, String s) {
				// Save the original value, if its an integer
				try {
					Integer i = Integer.parseInt(s);
					ViewTagger.setTag(f.getView(), R.id.TAG_ORIGINAL_VALUE, i);
				} catch (Exception e) {
					ViewTagger.setTag(f.getView(), R.id.TAG_ORIGINAL_VALUE, 0);
				}
				// Just pass the string onwards to the accessor.
				return s;
			}

			public String extract(Field f, String s) {
				// Parse the string the CheckBox returns us (0 or 1)
				Integer i = Integer.parseInt(s);
				Integer orig = (Integer) ViewTagger.getTag(f.getView(), R.id.TAG_ORIGINAL_VALUE);
				try {
					if (i != 0 && orig > 0) {
						// If non-zero, and original was non-zero, re-use original
						return orig.toString();
					} else {
						// Just return what we got.
						return s;
					}
				} catch (NullPointerException e) {
					return s;
				}
			}
		});

		mFields.add(R.id.author, "", CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, nonBlankValidator);
		mFields.add(R.id.isbn, CatalogueDBAdapter.KEY_ISBN, null);
		mFields.add(R.id.publisher, CatalogueDBAdapter.KEY_PUBLISHER, null);
		mFields.add(R.id.date_published_button, "", CatalogueDBAdapter.KEY_DATE_PUBLISHED, null);
		mFields.add(R.id.date_published, CatalogueDBAdapter.KEY_DATE_PUBLISHED, CatalogueDBAdapter.KEY_DATE_PUBLISHED,
				null, new Fields.DateFieldFormatter());
		mFields.add(R.id.series, CatalogueDBAdapter.KEY_SERIES_NAME, CatalogueDBAdapter.KEY_SERIES_NAME, null);
		mFields.add(R.id.list_price, "list_price", blankOrFloatValidator);
		mFields.add(R.id.pages, "pages", blankOrIntegerValidator);
		mFields.add(R.id.format, CatalogueDBAdapter.KEY_FORMAT, null);
		mFields.add(R.id.bookshelf, "", null);
		mFields.add(R.id.description, CatalogueDBAdapter.KEY_DESCRIPTION, null);
		mFields.add(R.id.genre, CatalogueDBAdapter.KEY_GENRE, null);
		mFields.add(R.id.row_img, "", "thumbnail", null);
		mFields.add(R.id.format_button, "", CatalogueDBAdapter.KEY_FORMAT, null);
		mFields.add(R.id.bookshelf_text, "bookshelf_text", null).doNoFetch = true; // Output-only
																					// field
	}
	
	private void initThumbSizes() {
		// Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension
		mThumbEditSize = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels) / 3);
		// Zoom size is minimum of MAX_ZOOM_THUMBNAIL_SIZE and largest screen dimension.
		mThumbZoomSize = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels));
	}
	
	private void getDisplayMetrics(){
		mMetrics = new android.util.DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
	}
	
	protected void populateFieldsFromDb(Long rowId) {
		// From the database (edit)
		Cursor book = mDbHelper.fetchBookById(rowId);
		Cursor bookshelves = null;
		try {
			if (book != null) {
				book.moveToFirst();
			}

			// Set any field that has a 'column' non blank.
			mFields.setFromCursor(book);

			getParent().setTitle(
					this.getResources().getString(R.string.app_name) + ": "
							+ mFields.getField(R.id.title).getValue().toString());

			// Display the selected bookshelves
			Field bookshelfTextFe = mFields.getField(R.id.bookshelf_text);
			bookshelves = mDbHelper.fetchAllBookshelvesByBook(rowId);
			String bookshelves_text = "";
			String bookshelves_list = "";
			while (bookshelves.moveToNext()) {
				String name = bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF));
				String encoded_name = Utils.encodeListItem(name, BOOKSHELF_SEPERATOR);
				if (bookshelves_text.equals("")) {
					bookshelves_text = name;
					bookshelves_list = encoded_name;
				} else {
					bookshelves_text += ", " + name;
					bookshelves_list += BOOKSHELF_SEPERATOR + encoded_name;
				}
			}
			bookshelfTextFe.setValue(bookshelves_text);
			bookshelfTextFe.setTag(bookshelves_list);

			Integer anthNo = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
			mFields.getField(R.id.anthology).setValue(anthNo.toString());

			ImageView iv = (ImageView) findViewById(R.id.row_img);
			Utils.fetchFileIntoImageView(getCoverFile(rowId), iv, mThumbEditSize, mThumbEditSize, true);
		} finally {
			if (book != null)
				book.close();
			if (bookshelves != null)
				bookshelves.close();
		}
	}
}
