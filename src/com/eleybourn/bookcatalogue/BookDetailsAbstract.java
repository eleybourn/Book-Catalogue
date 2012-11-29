package com.eleybourn.bookcatalogue;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.app.Dialog;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Abstract class for creating activities containing book details. 
 * Here we define common method for all childs: database and background initializing,
 * initializing fields and display metrics and other common tasks.
 * @author n.silin
 */
public abstract class BookDetailsAbstract extends Activity {

	public static final Character BOOKSHELF_SEPERATOR = ',';
	
	// Target size of a thumbnail in edit dialog and zoom dialog (bbox dim)
	protected static final int MAX_EDIT_THUMBNAIL_SIZE = 256;
	protected static final int MAX_ZOOM_THUMBNAIL_SIZE=1024;
	
	/**
	 * Database row id of the selected book.
	 */
	protected Long mRowId;
	/**
	 * Fields containing book information
	 */
	protected Fields mFields = null;
	protected CatalogueDBAdapter mDbHelper;
	
	protected android.util.DisplayMetrics mMetrics;
	/** Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension */
	protected Integer mThumbEditSize;
	/** Zoom size is minimum of MAX_ZOOM_THUMBNAIL_SIZE and largest screen dimension. */
	protected Integer mThumbZoomSize;
	
	protected ArrayList<Author> mAuthorList = null;
	protected ArrayList<Series> mSeriesList = null;
	
	@Override
	/* Note that you should use setContentView() method in descendant before
	 * running this. */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// See how big the display is and use that to set bitmap sizes
		setDisplayMetrics();
		initThumbSizes();
		
		initFields();
		
		//Set zooming by default on clicking on image
		findViewById(R.id.row_img).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showZoomedThumb(mRowId);
			}
		});
		
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
		
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open(); //Open here. We will close it in onDestroy method later.
	}
	
	@Override 
	protected void onResume() {
		Tracker.enterOnResume(this);
		super.onResume();
		
		// Fix background
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
	 * Populate Author field by data from {@link #mAuthorList}.
	 * If there is no data shows "Set author" text defined in resources.
	 * <p>
	 * Be sure that you get {@link #mAuthorList}. See {@link #populateFieldsFromDb(Long)}
	 * for example. 
	 */
	protected void populateAuthorListField() {

		String newText;
		if (mAuthorList.size() == 0)
			newText = getResources().getString(R.string.set_authors);
		else {
			newText = mAuthorList.get(0).getDisplayName();
			if (mAuthorList.size() > 1)
				newText += " " + getResources().getString(R.string.and_others);
		}
		mFields.getField(R.id.author).setValue(newText);	
	}
	
	/**
	 * Populate Series field by data from {@link #mSeriesList}.
	 * If there is no data shows "Set series..." text defined in resources.
	 * <p>
	 * Be sure that you get {@link #mSeriesList}. See {@link #populateFieldsFromDb(Long)}
	 * for example. 
	 */
	protected void populateSeriesListField() {

		String newText;
		int size = 0;
		try {
			size = mSeriesList.size();
		} catch (NullPointerException e) {
			size = 0;
		}
		if (size == 0)
			newText = getResources().getString(R.string.set_series);
		else {
			Utils.pruneSeriesList(mSeriesList);
			Utils.pruneList(mDbHelper, mSeriesList);
			newText = mSeriesList.get(0).getDisplayName();
			if (mSeriesList.size() > 1)
				newText += " " + getResources().getString(R.string.and_others);
		}
		mFields.getField(R.id.series).setValue(newText);		
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
	
	/**
	 * Initializes {@link #mThumbEditSize} and {@link #mThumbZoomSize} values according
	 * to screen size and {@link #MAX_EDIT_THUMBNAIL_SIZE}, {@link #MAX_ZOOM_THUMBNAIL_SIZE}
	 * values.<p> 
	 * Be sure that you set {@link #mMetrics} before. See {@link #setDisplayMetrics()}
	 * for it. 
	 */
	private void initThumbSizes() {
		mThumbEditSize = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels) / 3);
		mThumbZoomSize = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels));
	}
	
	/**
	 * Get display metrics and set {@link #mMetrics} with it.
	 */
	private void setDisplayMetrics(){
		mMetrics = new android.util.DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
	}
	
	/**
	 * Populate all fields (See {@link #mFields} ) except of authors and series fields with 
	 * data from database. To set authors and series fields use {@link #populateAuthorListField()}
	 * and {@link #populateSeriesListField()} methods.<br>
	 * Also sets {@link #mAuthorList} and {@link #mSeriesList} values with data from database.
	 * Data defined by its _id in db. 
	 * @param rowId database row id of the selected book.
	 */
	protected void populateFieldsFromDb(Long rowId) {
		// From the database (edit)
		Cursor book = mDbHelper.fetchBookById(rowId);
		try {
			if (book != null) {
				book.moveToFirst();
			}

			populateBookDetailsFields(rowId, book);
			
			String title = mFields.getField(R.id.title).getValue().toString();
			setActivityTitle(title);
		} catch (Exception e) {
			e.printStackTrace();
		} 
		finally {
			if (book != null)
				book.close();
		}
		
		populateBookshelvesField(mFields, rowId);
		
		// Get author and series lists
		mAuthorList = mDbHelper.getBookAuthorList(rowId);
		mSeriesList = mDbHelper.getBookSeriesList(rowId);
	}
	
	/**
	 * Inflates all fields with data from cursor and populates UI fields with it.
	 * Also set thumbnail of the book. 
	 * @param rowId database row _id of the book
	 * @param bookCursor cursor with information of the book
	 */
	protected void populateBookDetailsFields(Long rowId, Cursor bookCursor){
		// Set any field that has a 'column' non blank.
		mFields.setFromCursor(bookCursor);
		
		//Set anthology field
		int columnIndex = bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY);
		Integer anthNo = bookCursor.getInt(columnIndex);
		mFields.getField(R.id.anthology).setValue(anthNo.toString()); // Set checked if anthNo != 0
		
		//Sets book thumbnail
		ImageView iv = (ImageView) findViewById(R.id.row_img);
		Utils.fetchFileIntoImageView(getCoverFile(rowId), iv, mThumbEditSize, mThumbEditSize, true);
	}
	
	/**
	 * Shows zoomed thumbnail in dialog. Closed by click on image area.
	 * @param rowId database row id for getting correct file
	 */
	private void showZoomedThumb(Long rowId) {
		// Create dialog and set layout
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.zoom_thumb_dialog);
		
		// Check if we have a file and/or it is valid
		File thumbFile = getCoverFile(rowId);

		if (thumbFile == null || !thumbFile.exists()) {
			dialog.setTitle(getResources().getString(R.string.cover_not_set));
		} else {

			BitmapFactory.Options opt = new BitmapFactory.Options();
			opt.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), opt);

			// If no size info, assume file bad and return appropriate icon
			if (opt.outHeight <= 0 || opt.outWidth <= 0) {
				dialog.setTitle(getResources().getString(R.string.cover_corrupt));
			} else {
				dialog.setTitle(getResources().getString(R.string.cover_detail));
				ImageView cover = new ImageView(this);
				Utils.fetchFileIntoImageView(thumbFile, cover, mThumbZoomSize, mThumbZoomSize, true);
				cover.setAdjustViewBounds(true);
				cover.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.dismiss();						
					}
				});
				
				LayoutParams lp = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
				dialog.addContentView(cover, lp);
			}
		}
		dialog.show();
	}
	
	/**
	 * Gets all bookshelves for the book from database and populate corresponding 
	 * filed with them.
	 * @param fields Fields containing book information
	 * @param rowId Database row _id of the book
	 * @return true if populated, false otherwise
	 */
	protected boolean populateBookshelvesField(Fields fields, Long rowId){
		Cursor bookshelves = null;
		boolean result = false;
		try {
			// Display the selected bookshelves
			Field bookshelfTextFe = fields.getField(R.id.bookshelf_text);
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
					bookshelves_text += "<br/>" + name;
					bookshelves_list += BOOKSHELF_SEPERATOR + encoded_name;
				}
			}
			bookshelfTextFe.setValue(bookshelves_text);
			bookshelfTextFe.setTag(bookshelves_list);
			if (bookshelves.getCount() > 0) {
				result = true; // One or more bookshelves have been set
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (bookshelves != null) {
				bookshelves.close();
			}
		}
		return result;
	}
	
	/**
	 * Sets title of the parent activity in the next format:<br>
	 * <i>"Application name + : + title"</i>
	 * @param title
	 */
	protected void setActivityTitle(String title) {
		String activityTitle = getResources().getString(R.string.app_name) + ": " + title;
		getParent().setTitle(activityTitle);
	}
}
