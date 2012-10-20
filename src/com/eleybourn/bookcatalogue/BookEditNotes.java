/*
 * @copyright 2010 Evan Leybourn
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

//import android.R;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookEdit.OnRestoreTabInstanceStateListener;
import com.eleybourn.bookcatalogue.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.Fields.FieldValidator;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePicker;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookEditNotes extends Activity implements OnRestoreTabInstanceStateListener {

	private Fields mFields;
	private boolean mIsDirtyFlg = false;

	private Button mConfirmButton;
	private Button mCancelButton;
	private Long mRowId;
	private CatalogueDBAdapter mDbHelper;

	private static final int READ_START_DIALOG_ID = 1;
	private static final int READ_END_DIALOG_ID = 2;
	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
	}

	/**
	 * Validate the current data in all fields that have validators. Display any errors.
	 *
	 * @param values The values to use
	 *
	 * @return Boolean success or failure.
	 */
	private boolean validate(Bundle values) {
		if (!mFields.validate(values)) {
			Toast.makeText(getParent(), mFields.getValidationExceptionMessage(getResources()), Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	/**
	 * Returns a unique list of all locations in the database
	 *
	 * @return The list
	 */
	protected ArrayList<String> getLocations() {
		ArrayList<String> location_list = new ArrayList<String>();
		Cursor location_cur = mDbHelper.fetchAllLocations();
		try {
			while (location_cur.moveToNext()) {
				String location = location_cur.getString(location_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION));
				try {
					if (location.length() > 2) {
						location_list.add(location);
					}
				} catch (NullPointerException e) {
					// do nothing
				}
			}
		} finally {
			location_cur.close();
		}
		return location_list;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();

			setContentView(R.layout.edit_book_notes);

			if (savedInstanceState != null) {
				setDirty(savedInstanceState.getBoolean("Dirty"));
			}

			mFields = new Fields(this);
			Field f;

			// Generic validators; if field-specific defaults are needed, create a new one.
			FieldValidator booleanValidator = new Fields.BooleanValidator();
			FieldValidator blankOrDateValidator = new Fields.OrValidator(new Fields.BlankValidator(), new Fields.DateValidator());
			FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

			mFields.add(R.id.rating, CatalogueDBAdapter.KEY_RATING, new Fields.FloatValidator());
			mFields.add(R.id.rating_label, "",  CatalogueDBAdapter.KEY_RATING, null);
			mFields.add(R.id.read, CatalogueDBAdapter.KEY_READ, booleanValidator);
			mFields.add(R.id.notes, CatalogueDBAdapter.KEY_NOTES, null);

			ArrayAdapter<String> location_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getLocations());
			mFields.add(R.id.location, CatalogueDBAdapter.KEY_LOCATION, null);
			mFields.setAdapter(R.id.location, location_adapter);

			f = mFields.add(R.id.read_start_button, "",  CatalogueDBAdapter.KEY_READ_START, null);
			f.getView().setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(READ_START_DIALOG_ID);
				}
			});
			mFields.add(R.id.read_start, CatalogueDBAdapter.KEY_READ_START, blankOrDateValidator, dateFormatter);

			f = mFields.add(R.id.read_end_button, "",  CatalogueDBAdapter.KEY_READ_END, null);
			f.getView().setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					showDialog(READ_END_DIALOG_ID);
				}
			});
			f = mFields.add(R.id.read_end, CatalogueDBAdapter.KEY_READ_END, blankOrDateValidator, dateFormatter);

			mFields.add(R.id.signed, CatalogueDBAdapter.KEY_SIGNED,  booleanValidator);

			mFields.addCrossValidator(new Fields.FieldCrossValidator() {
				public void validate(Fields fields, Bundle values) {
					String start = values.getString(CatalogueDBAdapter.KEY_READ_START);
					if (start == null || start.equals(""))
						return;
					String end = values.getString(CatalogueDBAdapter.KEY_READ_END);
					if (end == null || end.equals(""))
						return;
					if (start.compareToIgnoreCase(end) > 0)
						throw new Fields.ValidatorException(R.string.vldt_read_start_after_end,new Object[]{});
				}
			});

			mConfirmButton = (Button) findViewById(R.id.confirm);
			mCancelButton = (Button) findViewById(R.id.cancel);

			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (mRowId == null) {
				getRowId();
			}

			if (savedInstanceState == null) {
				populateFields();
			} else {
				restoreField(savedInstanceState, CatalogueDBAdapter.KEY_READ_START, R.id.read_start);
				restoreField(savedInstanceState, CatalogueDBAdapter.KEY_READ_END, R.id.read_end);
			}

			if (mRowId != null && mRowId > 0)
				mConfirmButton.setText(R.string.confirm_save);

			mConfirmButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					Bundle values = new Bundle();
					if (!validate(values)) {
						//return;
						// Ignore...nothing here is that important...but warn the users.
					}

					saveState(values);
					Intent i = new Intent();
					i.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
					if (getParent() == null) {
						setResult(RESULT_OK, i);
					} else {
						getParent().setResult(RESULT_OK, i);
					}
					finish();
				}
			});

			mCancelButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					Intent i = new Intent();
					i.putExtra(CatalogueDBAdapter.KEY_ROWID, mRowId);
					if (getParent() == null) {
						setResult(RESULT_OK, i);
					} else {
						getParent().setResult(RESULT_OK, i);
					}
					if (isDirty()) {
						StandardDialogs.showConfirmUnsavedEditsDialog(BookEditNotes.this);
					} else {
						finish();
					}
				}
			});
			
			try {
				Utils.fixFocusSettings(findViewById(android.R.id.content));				
			} catch (Exception e) {
				// Log, but ignore. This is a non-critical feature that prevents crashes when the
				// 'next' key is pressed and some views have been hidden.
				Logger.logError(e);
			}

			mFields.setAfterFieldChangeListener(new AfterFieldChangeListener(){
				@Override
				public void afterFieldChange(Field field, String newValue) {
					setDirty(true);
				}});
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * Restore a single field from a saved state
	 *
	 * @param savedInstanceState 	the saved state
	 * @param key					key in state for value to restore
	 * @param fieldId				field to set
	 */
	private void restoreField(Bundle savedInstanceState, String key, int fieldId) {
		final Field fe = mFields.getField(fieldId);
		fe.setValue(savedInstanceState.getString(key));
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case READ_START_DIALOG_ID:
			try {
				return Utils.buildDateDialog(this, R.string.read_start, mReadStartSetListener);
			} catch (Exception e) {
				// use the default date
			}
			break;
		case READ_END_DIALOG_ID:
			try {
				return Utils.buildDateDialog(this, R.string.read_end, mReadEndSetListener);
			} catch (Exception e) {
				// use the default date
			}
			break;
		}
		return null;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case READ_START_DIALOG_ID:
			try {
				String dateString;
				Object o = mFields.getField(R.id.read_start).getValue();
				if (o == null || o.toString().equals("")) {
					dateString = Utils.toSqlDateTime(new Date());
				} else {
					dateString = o.toString();
				}
				Utils.prepareDateDialog((PartialDatePicker)dialog, dateString, mReadStartSetListener);
			} catch (Exception e) {
				// use the default date
			}
			break;
		case READ_END_DIALOG_ID:
			try {
				String dateString;
				Object o = mFields.getField(R.id.read_end).getValue();
				if (o == null || o.toString().equals("")) {
					dateString = Utils.toSqlDateTime(new Date());
				} else {
					dateString = o.toString();
				}
				Utils.prepareDateDialog((PartialDatePicker)dialog, dateString, mReadEndSetListener);
			} catch (Exception e) {
				// use the default date
			}
			break;
		}
	}

	/**
	 * the callback received when the user "sets" the read-start date in the dialog
	 */
	private PartialDatePicker.OnDateSetListener mReadStartSetListener = new PartialDatePicker.OnDateSetListener() {
		@Override
		public void onDateSet(PartialDatePicker dialog, Integer year, Integer month, Integer day) {
			String value = Utils.buildPartialDate(year, month, day);
			mFields.getField(R.id.read_start).setValue(value);
			dismissDialog(READ_START_DIALOG_ID);
		}

		@Override
		public void onCancel(PartialDatePicker dialog) {
			dismissDialog(READ_START_DIALOG_ID);
		}
	};

	/**
	 * the callback received when the user "sets" the read-end date in the dialog
	 */
	private PartialDatePicker.OnDateSetListener mReadEndSetListener = new PartialDatePicker.OnDateSetListener() {
		@Override
		public void onDateSet(PartialDatePicker dialog, Integer year, Integer month, Integer day) {
			String value = Utils.buildPartialDate(year, month, day);
			mFields.getField(R.id.read_end).setValue(value);
			dismissDialog(READ_END_DIALOG_ID);
		}

		@Override
		public void onCancel(PartialDatePicker dialog) {
			dismissDialog(READ_END_DIALOG_ID);
		}
	};

	private void populateFields() {
		if (mRowId == null) {
			getRowId();
		}

		if (mRowId != null && mRowId > 0) {
			// From the database (edit)
			Cursor book = mDbHelper.fetchBookById(mRowId);
			try {
				if (book != null) {
					book.moveToFirst();
				}
				getParent().setTitle(this.getResources().getString(R.string.app_name) + ": " +
							book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)));

				mFields.setFromCursor(book);

			} finally {
				// Tidy up
				if (book != null)
					book.close();
			}
		} else {
			// Manual Add
			//This should never happen
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	/**
	 * If 'back' is pressed, and the user has made changes, ask them if they really want to lose the changes.
	 * 
	 * We don't use onBackPressed because it does not work with API level 4.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && isDirty()) {
			StandardDialogs.showConfirmUnsavedEditsDialog(this);
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);

		// DONT FORGET TO UPDATE onCreate to read these values back.
		outState.putBoolean("Dirty", isDirty());
		// Need to save local data that is not stored in EDITABLE views 
		// ...including special text stored in TextViews and the like (TextViews are not restored automatically)
		putStringSafely(outState, CatalogueDBAdapter.KEY_READ_START, mFields.getField(R.id.read_start).getValue());
		putStringSafely(outState, CatalogueDBAdapter.KEY_READ_END, mFields.getField(R.id.read_end).getValue());
	}

	/**
	 * If the object is null, then don't output. Otherwise, output the result of 'toString()'
	 * 
	 * @param outState		Bundle to store value in
	 * @param key			Key in bundle
	 * @param value			value to store
	 */
	private void putStringSafely(Bundle outState, String key, Object value) {
		if (value != null)
			outState.putString(key, value.toString());
	}

	/**
	 * Mark the data as dirty (or not)
	 */
	public void setDirty(boolean dirty) {
		mIsDirtyFlg = dirty;
	}

	/**
	 * Get the current status of the data in this activity
	 */
	public boolean isDirty() {
		return mIsDirtyFlg;
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	private void saveState(Bundle values) {

		if (mRowId == null || mRowId == 0) {
			//This should never happen
			//long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		} else {
			mDbHelper.updateBook(mRowId, values, true);
		}
		return;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	}

	@Override
	/**
	 * Make sure we are marked as 'dirty' based on saved state after a restore.
	 */
	public void restoreTabInstanceState(Bundle savedInstanceState) {
		setDirty(savedInstanceState.getBoolean("Dirty"));
	}

}