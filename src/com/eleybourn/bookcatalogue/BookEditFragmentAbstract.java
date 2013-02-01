package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.Logger;

import android.app.Activity;
import android.os.Bundle;


public abstract class BookEditFragmentAbstract extends SherlockFragment implements DataEditor {
	protected Fields mFields;
	
	private static final int DELETE_ID = 1;
	private static final int DUPLICATE_ID = 3; //2 is taken by populate in anthology
	private static final int SHARE_ID = 4;
	protected static final int THUMBNAIL_OPTIONS_ID = 5;
	private static final int EDIT_OPTIONS_ID = 6;
	
	public interface BookEditManager {
		//public Fields getFields();
		public void setShowAnthology(boolean showAnthology);
		public void setDirty(boolean isDirty);
		public boolean isDirty();
		public BookData getBookData();
		public void setRowId(Long id);
		public ArrayList<String> getFormats();
		public ArrayList<String> getGenres();
		public ArrayList<String> getPublishers();
	}

	protected BookEditManager mEditManager;
	protected CatalogueDBAdapter mDbHelper;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setHasOptionsMenu(true);	
	}

	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		if (! (a instanceof BookEditManager))
			throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement BookEditManager");
		
		mEditManager = (BookEditManager)a;
		mDbHelper = new CatalogueDBAdapter(a);
		mDbHelper.open();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mFields = new Fields(this);		
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		//menu.clear();
		final Long currRow = mEditManager.getBookData().getRowId();
		if (currRow != null && currRow != 0) {
			MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete);
			delete.setIcon(android.R.drawable.ic_menu_delete);

			MenuItem duplicate = menu.add(0, DUPLICATE_ID, 0, R.string.menu_duplicate);
			duplicate.setIcon(android.R.drawable.ic_menu_add);
		}

		// TODO: Consider allowing Tweets (or other sharing methods) to work on un-added books.
		MenuItem tweet = menu.add(0, SHARE_ID, 0, R.string.menu_share_this);
		tweet.setIcon(R.drawable.ic_menu_twitter);
		tweet.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

		boolean thumbVisible = BookCatalogueApp.getAppPreferences().getBoolean(FieldVisibility.prefix + "thumbnail", true);
//		if (this instanceof BookEditFields) {
//			MenuItem thumbOptions = menu.add(0, THUMBNAIL_OPTIONS_ID, 0, R.string.cover_options_cc_ellipsis);
//			thumbOptions.setIcon(android.R.drawable.ic_menu_camera);			
//			thumbOptions.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//		}
		
		if(this instanceof BookDetailsReadOnly){
			menu.add(0, EDIT_OPTIONS_ID, 0, R.string.edit_book)
				.setIcon(android.R.drawable.ic_menu_edit)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
				;
		}
	}

	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final Long currRow = mEditManager.getBookData().getRowId();

		try {
			switch(item.getItemId()) {
			case THUMBNAIL_OPTIONS_ID:
				if (this instanceof BookEditFields) {
					((BookEditFields)this).showCoverContextMenu();
				}
				break;
			case SHARE_ID:
				BookUtils.shareBook(getActivity(), mDbHelper, currRow);
				return true;
			case DELETE_ID:
				BookUtils.deleteBook(getActivity(), mDbHelper, currRow, new Runnable() {
					@Override
					public void run() {
						getActivity().finish();
					}});
				return true;
			case DUPLICATE_ID:
				BookUtils.duplicateBook(getActivity(), mDbHelper, currRow);
				return true;
			case EDIT_OPTIONS_ID:
				BookEdit.editBook(getActivity(), currRow, BookEdit.TAB_EDIT);
				return true;
			}
		} catch (NullPointerException e) {
			Logger.logError(e);
		}
		return true;
	}
	
	@Override
	public void onPause() {
		super.onPause();
		// This is now done in onPause() since the view may have been deleted when this is called
		onSaveBookDetails(mEditManager.getBookData());	
	}

	abstract protected void onLoadBookDetails(BookData book, boolean setAllDone);
	
	protected void onSaveBookDetails(BookData book) {
		mFields.getAll(book);
	}
	
	@Override
	public void onResume() {
		double t0 = System.currentTimeMillis();

		super.onResume();
		mFields.setAfterFieldChangeListener(null);
		final boolean wasDirty = mEditManager.isDirty();
		BookData book = mEditManager.getBookData();
		onLoadBookDetails(book, false);

		mEditManager.setDirty(wasDirty);

		mFields.setAfterFieldChangeListener(new AfterFieldChangeListener(){
			@Override
			public void afterFieldChange(Field field, String newValue) {
				mEditManager.setDirty(true);
			}});
		System.out.println("BEFA resume: " + (System.currentTimeMillis() - t0));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}
	
	@Override
	public void saveAllEdits(DataManager data) {
		mFields.getAll(mEditManager.getBookData());
	}

	/**
	 * This is 'final' because we want inheritors to implement onLoadBookDetails()
	 */
	@Override
	public final void reloadData(DataManager data) {
		final boolean wasDirty = mEditManager.isDirty();
		onLoadBookDetails(mEditManager.getBookData(), false);
		mEditManager.setDirty(wasDirty);
	}
}
