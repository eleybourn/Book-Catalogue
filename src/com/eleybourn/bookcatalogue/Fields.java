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

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.ValidatorException;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * This is the class that manages data and views for an activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 * <p>
 * Features is provides are:
 * <ul>
 * <li> handling of visibility via preferences
 * <li> handling of 'group' visibility via the 'group' property of a field.
 * <li> understanding of kinds of views (setting a checkbox value to 'true' will work as 
 *      expected as will setting the value of a Spinner). As new view types are added, it
 *      will be necessary to add new FieldAccessor implementations.
 * <li> Custom data accessors and formatters to provide application-specific data rules.
 * <li> validation: calling validate will call user-defined or predefined validation routines and
 *      return success or failure. The text of any exceptions will be available after the call.
 * <li> simplified loading of data from a cursor.
 * <li> simplified extraction of data to a ContentValues collection.
 * </ul>
 * <p>
 * Formatters and Accessors
 * <p>
 * It is up to each accessor to decide what to do with any formatters defined for a field.
 * The fields themselves have extract() and format() methods that will apply the formatter
 * functions (if present) or just pass the value through.
 * <p>
 * On a set(), the accessor should call format() function then apply the value
 * <p>
 * On a get() the accessor should retrieve the value and apply the extract() function.
 * <p>
 * The use of a formatter typically results in all values being converted to strings so
 * they should be avoided for most non-string data.
 * <p>
 * Data Flow
 * <p>
 * Data flows to and from a view as follows:
 * IN  (with formatter): (Cursor or other source) -> format() (via accessor) -> transform (in accessor) -> View
 * IN  ( no formatter ): (Cursor or other source) -> transform (in accessor) -> View
 * OUT (with formatter): (Cursor or other source) -> transform (in accessor) -> extract (via accessor) -> validator -> (ContentValues or Object)
 * OUT ( no formatter ): (Cursor or other source) -> transform (in accessor) -> validator -> (ContentValues or Object)
 * <p>
 * Usage Note:
 * <p>
 * 1. Which Views to Add?
 * <p>
 * It is not necessary to add every control to the 'Fields' collection, but as a general rule
 * any control that displays data from a database, or related derived data, or labels for such
 * data should be added.
 * <p>
 * Typical controls NOT added, are 'Save' and 'Cancel' buttons, or other controls whose
 * interactions are purely functional.
 * <p>
 * 2. Handlers?
 * <p>
 * The add() method of Fields returns a new Field object which exposes the 'view' member; this
 * can be used to perform view-specific tasks like setting onClick() handlers.
 *
 * @author Philip Warner
 *
 */
public class Fields extends ArrayList<Fields.Field> {
	// Used for date parsing
	static java.text.SimpleDateFormat mDateSqlSdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
	static java.text.DateFormat mDateDispSdf = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);

	// Java likes this
	public static final long serialVersionUID = 1L;

	// The activity and preferences related to this object.
	private FieldsContext mContext = null;
	SharedPreferences mPrefs = null;

	public interface AfterFieldChangeListener {
		void afterFieldChange(Field field, String newValue);
	}
	
	private AfterFieldChangeListener mAfterFieldChangeListener = null;
	
	private interface FieldsContext {
		Context getContext();
		View findViewById(int id);
	}
	private class ActivityContext implements FieldsContext {
		private final WeakReference<Activity> mActivity;
		public ActivityContext(Activity a) {
			mActivity = new WeakReference<Activity>(a);
		}
		@Override
		public Context getContext() {
			return mActivity.get();
		}
		@Override
		public View findViewById(int id) {
			return mActivity.get().findViewById(id);
		}
	}
	private class FragmentContext implements FieldsContext {
		private final WeakReference<SherlockFragment> mFragment;
		public FragmentContext(SherlockFragment f) {
			mFragment = new WeakReference<SherlockFragment>(f);
		}
		@Override
		public Context getContext() {
			return mFragment.get().getActivity();
		}

		@Override
		public View findViewById(int id) {
			if (mFragment.get() == null) {
				System.out.println("Fragment is NULL");
				return null;
			}
			View v = mFragment.get().getView();
			if (v == null) {
				System.out.println("View is NULL");
				return null;
			}

			return v.findViewById(id);
		}
		
	}
	/**
	 * Constructor
	 * 
	 * @param a 	The parent activity which contains all Views this object
	 * 				will manage.
	 */
	Fields(android.app.Activity a) {
		super();
		mContext = new ActivityContext(a);
		mPrefs = a.getSharedPreferences("bookCatalogue", android.content.Context.MODE_PRIVATE);
	}

	/**
	 * Constructor
	 * 
	 * @param a 	The parent fragment which contains all Views this object
	 * 				will manage.
	 */
	Fields(SherlockFragment f) {
		super();
		mContext = new FragmentContext(f);
		mPrefs = f.getActivity().getSharedPreferences("bookCatalogue", android.content.Context.MODE_PRIVATE);
	}

	/**
	 * Set the listener for field changes
	 * 
	 * @param listener
	 * @return
	 */
	public AfterFieldChangeListener setAfterFieldChangeListener(AfterFieldChangeListener listener) {
		AfterFieldChangeListener old = mAfterFieldChangeListener;
		mAfterFieldChangeListener = listener;
		return old;
	}

	/**
	 * Utility routine to parse a date. Parses YYYY-MM-DD and DD-MMM-YYYY format.
	 * Could be generalized even further if desired by supporting more formats.  
	 * 
	 * @param s		String to parse
	 * @return		Parsed date
	 * 
	 * @throws ParseException		If parse failed.
	 */
	static Date parseDate(String s) throws ParseException {
		Date d;
		try {
			// Parse as SQL/ANSI date
			d = mDateSqlSdf.parse(s);
		} catch (Exception e) {
			try {
				d = mDateDispSdf.parse(s);				
			} catch (Exception e1) {
				java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
				d = df.parse(s);
			}

		}
		return d;
	}

	// The last validator exception caught by this object
	private ArrayList<ValidatorException> mValidationExceptions = new ArrayList<ValidatorException>();
	// A list of cross-validators to apply if all fields pass simple validation.
	private ArrayList<FieldCrossValidator> mCrossValidators = new ArrayList<FieldCrossValidator>();

	/**
	 * Interface for view-specific accessors. One of these will be implemented for each view type that
	 * is supported.
	 * 
	 * @author Philip Warner
	 *
	 */
	public interface FieldDataAccessor {
		/**
		 * Passed a Field and a Cursor get the column from the cursor and set the view value.
		 * 
		 * @param field		Field which defines the View details
		 * @param c			Cursor with data to load.
		 */
		void set(Field field, Cursor c);

		/**
		 * Passed a Field and a Cursor get the column from the cursor and set the view value.
		 * 
		 * @param field		Field which defines the View details
		 * @param b			Bundle with data to load.
		 */
		void set(Field field, Bundle b);

		/**
		 * Passed a Field and a DataManager get the column from the data manager and set the view value.
		 * 
		 * @param field		Field which defines the View details
		 * @param b			Bundle with data to load.
		 */
		void set(Field field, DataManager data);

		/**
		 * Passed a Field and a String, use the string to set the view value.
		 * 
		 * @param field		Field which defines the View details
		 * @param s			Source string for value to set.
		 */
		void set(Field field, String s);

		/**
		 * Get the the value from the view associated with Field and store a native version
		 * in the passed values collection.
		 * 
		 * @param field		Field associated with the View object
		 * @param values	Collection to save value.
		 */
		void get(Field field, Bundle values);

		/**
		 * Get the the value from the view associated with Field and store a native version
		 * in the passed DataManager.
		 * 
		 * @param field		Field associated with the View object
		 * @param values	Collection to save value.
		 */
		void get(Field field, DataManager values);

		/**
		 * Get the the value from the view associated with Field and return it as am Object.
		 * 
		 * @param 	field	Field associated with the View object
		 * @return 	The most natural value to associate with the View value.
		 */
		Object get(Field field);
	}

	/**
	 * Implementation that stores and retrieves data from a string variable.
	 * Only used when a Field fails to find a layout.
	 * 
	 * @author Philip Warner
	 *
	 */
	static public class StringDataAccessor implements FieldDataAccessor {
		private String mLocalValue = "";
		public void set(Field field, Cursor c) {
			set(field, c.getString(c.getColumnIndex(field.column)));
		}
		public void set(Field field, Bundle b) {
			set(field, b.getString(field.column));
		}
		public void set(Field field, DataManager data) {
			set(field, data.getString(field.column));
		}
		public void set(Field field, String s) {
			mLocalValue = field.format(s);
		}
		public void get(Field field, Bundle values) {
			values.putString(field.column, field.extract(mLocalValue));
		}
		@Override
		public void get(Field field, DataManager values) {
			values.putString(field.column, field.extract(mLocalValue));
		}
		public Object get(Field field) {
			return field.extract(mLocalValue);
		}
	}

	/**
	 * Implementation that stores and retrieves data from a TextView.
	 * This is treated differently to an EditText in that HTML is 
	 * displayed properly.
	 * 
	 * @author Philip Warner
	 *
	 */
	static public class TextViewAccessor implements FieldDataAccessor {
		private boolean mFormatHtml;
		private String mRawValue;

		public TextViewAccessor(boolean formatHtml) {
			mFormatHtml = formatHtml;
		}
		public void set(Field field, Cursor c) {
			set(field, c.getString(c.getColumnIndex(field.column)));
		}
		public void set(Field field, Bundle b) {
			set(field, b.getString(field.column));
		}
		public void set(Field field, DataManager data) {
			set(field, data.getString(field.column));
		}
		public void set(Field field, String s) {
			mRawValue = s;
			TextView v = (TextView) field.getView();
			if (mFormatHtml && s != null) {
				v.setText(Html.fromHtml(field.format(s)));
				v.setFocusable(false);
				v.setTextColor(BookCatalogueApp.context.getResources().getColor(android.R.color.primary_text_dark_nodisable));
			} else {
				v.setText(field.format(s));
			}
		}
		public void get(Field field, Bundle values) {
			//TextView v = (TextView) field.getView();
			//values.putString(field.column, field.extract(v.getText().toString()));
			values.putString(field.column, mRawValue);
		}
		@Override
		public void get(Field field, DataManager values) {
			values.putString(field.column, mRawValue);
		}
		public Object get(Field field) {
			return mRawValue;
			//return field.extract(((TextView) field.getView()).getText().toString());
		}

		/**
		 * Set the TextViewAccessor to support HTML.
		 */
		public void setShowHtml(boolean showHtml) {
			mFormatHtml = showHtml;
		}
	
	}

	/**
	 * Implementation that stores and retrieves data from an EditText.
	 * Just uses for defined formatter and setText() and getText().
	 * 
	 * @author Philip Warner
	 *
	 */
	static public class EditTextAccessor implements FieldDataAccessor {
		private boolean mIsSetting = false;

		public void set(Field field, Cursor c) {
			set(field, c.getString(c.getColumnIndex(field.column)));
		}
		public void set(Field field, Bundle b) {
			set(field, b.getString(field.column));
		}
		public void set(Field field, DataManager data) {
			set(field, data.getString(field.column));
		}
		public void set(Field field, String s) {
			synchronized(this) {
				if (mIsSetting)
					return; // Avoid recursion now we watch text
				mIsSetting = true;				
			}
			try {
				TextView v = (TextView) field.getView();
				String newVal = field.format(s);
				// Despite assurances otherwise, getText() apparently returns null sometimes
				String oldVal = v.getText() == null ? null : v.getText().toString();
				if (newVal == null && oldVal == null)
					return;
				if (newVal != null && oldVal != null && newVal.equals(oldVal))
					return;
				v.setText(newVal);
			} finally {
				mIsSetting = false;				
			}
		}
		public void get(Field field, Bundle values) {
			TextView v = (TextView) field.getView();
			values.putString(field.column, field.extract(v.getText().toString()));
		}
		@Override
		public void get(Field field, DataManager values) {
			try {
				TextView v = (TextView) field.getView();
				if (v == null) {
					throw new RuntimeException("No view for field " + field.column);					
				}
				if (v.getText() == null) {
					throw new RuntimeException("Text is NULL for field " + field.column);					
				}
				values.putString(field.column, field.extract(v.getText().toString()));				
			} catch (Exception e) {
				throw new RuntimeException("Unable to save data", e);
			}
		}
		public Object get(Field field) {
			return field.extract(((TextView) field.getView()).getText().toString());
		}
	}

	/**
	 * CheckBox accessor. Attempt to convert data to/from a boolean.
	 * 
	 * @author Philip Warner
	 *
	 */
	static public class CheckBoxAccessor implements FieldDataAccessor {
		public void set(Field field, Cursor c) {
			set(field, c.getString(c.getColumnIndex(field.column)));
		}
		public void set(Field field, Bundle b) {
			set(field, b.getString(field.column));
		}
		public void set(Field field, DataManager data) {
			set(field, data.getString(field.column));
		}
		public void set(Field field, String s) {
			CheckBox v = (CheckBox) field.getView();
			if (s != null) {
				try {
					s = field.format(s);
					v.setChecked(Utils.stringToBoolean(s, true));
				} catch (Exception e) {
					v.setChecked(false);
				}
			} else {
				v.setChecked(false);
			}
		}
		public void get(Field field, Bundle values) {
			CheckBox v = (CheckBox) field.getView();
			if (field.formatter != null)
				values.putString(field.column, field.extract(v.isChecked() ? "1" : "0"));
			else
				values.putBoolean(field.column, v.isChecked());
		}
		@Override
		public void get(Field field, DataManager values) {
			CheckBox v = (CheckBox) field.getView();
			if (field.formatter != null)
				values.putString(field.column, field.extract(v.isChecked() ? "1" : "0"));
			else
				values.putBoolean(field.column, v.isChecked());
		}
		public Object get(Field field) {
			if (field.formatter != null)
				return field.formatter.extract(field, (((CheckBox)field.getView()).isChecked() ? "1" : "0"));
			else				
				return (Integer)(((CheckBox)field.getView()).isChecked() ? 1 : 0);
		}
	}

	/**
	 * RatingBar accessor. Attempt to convert data to/from a Float.
	 * 
	 * @author Philip Warner
	 *
	 */
	static public class RatingBarAccessor implements FieldDataAccessor {
		public void set(Field field, Cursor c) {
			RatingBar v = (RatingBar) field.getView();
			if (field.formatter != null)
				v.setRating(Float.parseFloat(field.formatter.format(field, c.getString(c.getColumnIndex(field.column)))));
			else
				v.setRating(c.getFloat(c.getColumnIndex(field.column)));
		}
		public void set(Field field, Bundle b) {
			set(field, b.getString(field.column));
		}
		public void set(Field field, DataManager data) {
			set(field, data.getString(field.column));
		}
		public void set(Field field, String s) {
			RatingBar v = (RatingBar) field.getView();
			Float f = 0.0f;
			try {
				s = field.format(s);
				f = Float.parseFloat(s);
			} catch (Exception e) {
			}
			v.setRating(f);
		}
		public void get(Field field, Bundle values) {
			RatingBar v = (RatingBar) field.getView();
			if (field.formatter != null)
				values.putString(field.column, field.extract("" + v.getRating()));
			else
				values.putFloat(field.column, v.getRating());
		}
		public void get(Field field, DataManager values) {
			RatingBar v = (RatingBar) field.getView();
			if (field.formatter != null)
				values.putString(field.column, field.extract("" + v.getRating()));
			else
				values.putFloat(field.column, v.getRating());			
		}

		public Object get(Field field) {
			RatingBar v = (RatingBar) field.getView();
			return v.getRating();
		}
	}

	/**
	 * Spinner accessor. Assumes the Spinner contains a list of Strings and
	 * sets the spinner to the matching item.
	 * 
	 * @author Philip Warner
	 *
	 */
	static public class SpinnerAccessor implements FieldDataAccessor {
		public void set(Field field, Cursor c) {
			set(field, c.getString(c.getColumnIndex(field.column)));
		}
		public void set(Field field, Bundle b) {
			set(field, b.getString(field.column));
		}
		public void set(Field field, DataManager data) {
			set(field, data.getString(field.column));
		}
		public void set(Field field, String s) {
			s = field.format(s);
			Spinner v = (Spinner) field.getView();
			if (v == null)
				return;
			for(int i=0; i < v.getCount(); i++) {
				if (v.getItemAtPosition(i).equals(s)) {
					v.setSelection(i);
					return;
				}
			}
		}
		public void get(Field field, Bundle values) {
			String value;
			Spinner v = (Spinner) field.getView();
			if (v == null)
				value = "";
			else {
				Object selItem = v.getSelectedItem();
				if (selItem != null)
					value = selItem.toString();
				else
					value = "";							
			}
			values.putString(field.column, value);
		}
		public void get(Field field, DataManager values) {
			String value;
			Spinner v = (Spinner) field.getView();
			if (v == null)
				value = "";
			else {
				Object selItem = v.getSelectedItem();
				if (selItem != null)
					value = selItem.toString();
				else
					value = "";							
			}
			values.putString(field.column, value);			
		}
		public Object get(Field field) {
			String value;
			Spinner v = (Spinner) field.getView();
			if (v == null)
				value = "";
			else {
				Object selItem = v.getSelectedItem();
				if (selItem != null)
					value = selItem.toString();
				else
					value = "";							
			}
			return field.extract(value);
		}
	}

	/**
	 * Interface for all field-level validators. Each field validator is called twice; once
	 * with the crossValidating flag set to false, then, if all validations were successful,
	 * they are all called a second time with the flag set to true. This is an alternate
	 * method of applying cross-validation.
	 * 
	 * @author Philip Warner
	 */
	public interface FieldValidator {
		/**
		 * Validation method. Must throw a ValidatorException if validation fails.
		 * 
		 * @param fields			The Fields object containing the Field being validated
		 * @param field				The Field to validate
		 * @param values			A ContentValues collection to store the validated value.
		 * 							On a cross-validation pass this collection will have all 
		 * 							field values set and can be read.
		 * @param crossValidating	Flag indicating if this is the cross-validation pass.
		 * 
		 * @throws ValidatorException	For any validation failure.
		 */
		void validate(Fields fields, Field field, Bundle values, boolean crossValidating);
	}
	
	/**
	 * Interface for all cross-validators; these are applied after all field-level validators
	 * have succeeded.
	 * 
	 * @author Philip Warner
	 *
	 */
	public interface FieldCrossValidator {
		/**
		 * 
		 * @param fields			The Fields object containing the Field being validated
		 * @param values			A Bundle collection with all validated field values.
		 */
		void validate(Fields fields, Bundle values);
	}
	
	/**
	 * Interface definition for Field formatters.
	 *
	 * @author Philip Warner
	 *
	 */
	public interface FieldFormatter {
		/**
		// Format a string for applying to a View
		 * 
		 * @param source	Input value
		 * @return			The formatted value
		 */
		abstract String format(Field f, String source);
		/**
		 * Extract a formatted string from the display version
		 * 
		 * @param source	The value to be back-translated
		 * @return			The extracted value
		 */
		abstract String extract(Field f, String source);
	}

	/**
	 * Formatter for date fields. On failure just return the raw string.
	 * 
	 * @author Philip Warner
	 *
	 */
	static public class DateFieldFormatter implements FieldFormatter {

		/**
		 * Display as a human-friendly date
		 */
		public String format(Field f, String source) {
			try {
				java.util.Date d = parseDate(source);
				return mDateDispSdf.format(d);				
			} catch (Exception e) {
				return source;
			}
		}

		/**
		 * Extract as an SQL date.
		 */
		public String extract(Field f, String source) {
			try {
				java.util.Date d = parseDate(source);
				return mDateSqlSdf.format(d);				
			} catch (Exception e) {
				return source;
			}
		}
	}

	/**
	 * Field definition contains all information and methods necessary to manage display and
	 * extraction of data in a view.
	 * 
	 * @author Philip Warner
	 *
	 */
	public class Field {
		/** Owning collction */
		WeakReference<Fields> mFields;

		/** Layout ID  */
		public int id;
		/** database column name (can be blank) */
		public String column;
		/** Visibility group name. Used in conjunction with preferences to show/hide Views  */
		public String group;
		/** FieldFormatter to use (can be null) */
		public FieldFormatter formatter = null;
		/** Validator to use (can be null) */
		public FieldValidator validator;
		/** Has the field been set to invisible **/
		public boolean visible;
		/** Flag indicating that even though field has a column name, it should NOT be fetched from a 
		 * Cursor. This is usually done for synthetic fields needed when saving the data */
		public boolean doNoFetch = false;

		/** Accessor to use (automatically defined) */
		private FieldDataAccessor mAccessor = null;

		/** Optional field-specific tag object */
		private Object mTag = null;
		
		///** Property used to determine if edits have been made.
		// * 
		// * Set to true in case the view is clicked
		// *
		// * This a good and simple metric to identify if a field was changed despite not being 100% accurate
		// * */ 
		//private boolean mWasClicked = false;

		/**
		 * Constructor.
		 * 
		 * @param fields				Parent object
		 * @param fieldId				Layout ID
		 * @param sourceColumn			Source database column. Can be empty.
		 * @param visibilityGroupName	Visibility group. Can be blank.
		 * @param fieldValidator		Validator. Can be null.
		 * @param fieldFormatter		Formatter. Can be null.
		 */
		Field(Fields fields, int fieldId, String sourceColumn, String visibilityGroupName, FieldValidator fieldValidator, FieldFormatter fieldFormatter) {
			mFields = new WeakReference<Fields>(fields);
			id = fieldId;
			column = sourceColumn;
			group = visibilityGroupName;
			formatter = fieldFormatter;
			validator = fieldValidator;

			/*
			 * Load the layout from the passed Activity based on the ID and set visibility and accessor.
			 */
			FieldsContext c = fields.getContext();
			if (c == null)
				return;

			// Lookup the view
			final View view = c.findViewById(id);

			// Set the appropriate accessor
			if (view == null) {
				mAccessor = new StringDataAccessor();
			} else {
				if (view instanceof Spinner) {
					mAccessor = new SpinnerAccessor();
				} else if (view instanceof CheckBox) {
					mAccessor = new CheckBoxAccessor();
					addTouchSignalsDirty(view);
				} else if (view instanceof EditText) {
					mAccessor = new EditTextAccessor();
					EditText et = (EditText) view;
					et.addTextChangedListener(new TextWatcher() {

						@Override
						public void afterTextChanged(Editable arg0) {
							Field.this.setValue(arg0.toString());
						}

						@Override
						public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
						@Override
						public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}}
					);

				} else if (view instanceof Button) {
					mAccessor = new TextViewAccessor(false);
				} else if (view instanceof TextView) {
					mAccessor = new TextViewAccessor(false);
				} else if (view instanceof ImageView) {
					mAccessor = new TextViewAccessor(false);
				} else if (view instanceof RatingBar) {
					mAccessor = new RatingBarAccessor();
					addTouchSignalsDirty(view);
				} else {
					throw new IllegalArgumentException();
				}
				visible = fields.getPreferences().getBoolean(FieldVisibility.prefix + group, true);
				if (!visible) {
					view.setVisibility(View.GONE);
				}
			}
		}

		public Field setFormatter(FieldFormatter formatter) {
			this.formatter = formatter;
			return this;
		}

		/**
		 * If a text field, set the TextViewAccessor to support HTML.
		 * Call this before loading the field.
		 */
		public Field setShowHtml(boolean showHtml) {
			if (mAccessor instanceof TextViewAccessor) {
				((TextViewAccessor)mAccessor).setShowHtml(showHtml);
			}
			return this;
		}

		/**
		 * Reset one fields visibility based on user preferences
		 */
		private void resetVisibility(FieldsContext c) {
			if (c == null)
				return;
			// Lookup the view
			final View view = c.findViewById(id);
			if (view != null) {
				visible = BookCatalogueApp.getAppPreferences().getBoolean(FieldVisibility.prefix + group, true);
				if (visible) {
					view.setVisibility(View.VISIBLE);					
				} else {
					view.setVisibility(View.GONE);
				}
				
			}
		}
		/**
		 * Add on onTouch listener that signals a 'dirty' event when touched.
		 * 
		 * @param view		The view to watch
		 */
		private void addTouchSignalsDirty(View view) {
			// Touching this is considered a change
			// We need to introduce a better way to handle this.
			view.setOnTouchListener(new View.OnTouchListener(){
			    @Override
			    public boolean onTouch(View v, MotionEvent event) {
			        if (MotionEvent.ACTION_UP == event.getAction()) {
						if (mAfterFieldChangeListener != null) {
							mAfterFieldChangeListener.afterFieldChange(Field.this, null);
						}
			        }
			        return false;
			    }
			});			
		}
		/**
		 * Get the view associated with this Field, if available.
		 * @param id	View ID.
		 * @return		Resulting View, or null.
		 */
		View getView() {
			Fields fs = mFields.get();
			if (fs == null) {
				System.out.println("Fields is NULL");
				return null;
			}
			FieldsContext c = fs.getContext();
			if (c == null) {
				System.out.println("Context is NULL");
				return null;
			}
			return c.findViewById(this.id);
		}

		/**
		 * Return the current value of the tag field.
		 * @return	Current value of tag.
		 */
		public Object getTag() {
			return mTag;
		}

		/**
		 * Set the current value of the tag field.
		 * @return	Current value of tag.
		 */
		public void setTag(Object tag) {
			mTag = tag;
		}

		/**
		 * Return the current value of this field.
		 * @return	Current value in native form.
		 */
		public Object getValue() {
			return mAccessor.get(this);
		}

		/**
		 * Get the current value of this field and put into the Bundle collection.
		 * @return	Current value in native form.
		 */
		public void getValue(Bundle values) {
			mAccessor.get(this, values);
		}

		/**
		 * Get the current value of this field and put into the Bundle collection.
		 * @return	Current value in native form.
		 */
		public void getValue(DataManager data) {
			mAccessor.get(this, data);
		}

		/**
		 * Set the value to the passed string value.
		 * 
		 * @param s		New value
		 */
		public void setValue(String s) {
			mAccessor.set(this, s);
			if (mAfterFieldChangeListener != null) {
				mAfterFieldChangeListener.afterFieldChange(this, s);
			}
		}

		/**
		 * Utility function to call the formatters format() method if present, or just return the raw value.
		 *
		 * @param s		String to format
		 * @return		Formatted value
		 */
		public String format(String s) {
			if (formatter == null)
				return s;
			return formatter.format(this, s);
		}

		/**
		 * Utility function to call the formatters extract() method if present, or just return the raw value.
		 * 
		 * @param s
		 * @return
		 */
		public String extract(String s) {
			if (formatter == null)
				return s;
			return formatter.extract(this, s);
		}

		/**
		 * Set the value of this field from the passed cursor. Useful for getting access to 
		 * raw data values from the database.
		 * 
		 * @param c
		 */
		public void set(Cursor c) {
			if (column.length() > 0 && !doNoFetch) {
				try {
					mAccessor.set(this, c);					
				} catch (android.database.CursorIndexOutOfBoundsException e) {
					throw new RuntimeException("Column '" + this.column + "' not found in cursor",e);
				}
			}
		}

		/**
		 * Set the value of this field from the passed Bundle. Useful for getting access to 
		 * raw data values from a saved data bundle.
		 * 
		 * @param c
		 */
		public void set(Bundle b) {
			if (column.length() > 0 && !doNoFetch) {
				try {
					mAccessor.set(this, b);					
				} catch (android.database.CursorIndexOutOfBoundsException e) {
					throw new RuntimeException("Column '" + this.column + "' not found in cursor",e);
				}
			}
		}

		/**
		 * Set the value of this field from the passed Bundle. Useful for getting access to 
		 * raw data values from a saved data bundle.
		 * 
		 * @param c
		 */
		public void set(DataManager data) {
			if (column.length() > 0 && !doNoFetch) {
				try {
					mAccessor.set(this, data);					
				} catch (android.database.CursorIndexOutOfBoundsException e) {
					throw new RuntimeException("Column '" + this.column + "' not found in data",e);
				}
			}
		}

		//public boolean isEdited(){
		//	return mWasClicked;
		//}
	}
	
	/**
	 * Accessor for related Activity
	 * 
	 * @return Activity for this collection.
	 */
	private FieldsContext getContext() {
		return mContext;
	}

	/**
	 * Accessor for related Preferences
	 * 
	 * @return SharedPreferences for this collection.
	 */
	public SharedPreferences getPreferences() {
		return mPrefs;
	}

	/**
	 * Provides access to the underlying arrays get() method.
	 * 
	 * @param index
	 * @return
	 */
	public Field getItem(int index) {
		return super.get(index);
	}

	/**
	 * Add a field to this collection
	 * 
	 * @param fieldId			Layout ID
	 * @param sourceColumn		Source DB column (can be blank)
	 * @param fieldValidator	Field Validator (can be null)
	 * 
	 * @return					The resulting Field.
	 */
	public Field add(int fieldId, String sourceColumn, FieldValidator fieldValidator) {
		return add(fieldId, sourceColumn, sourceColumn, fieldValidator, null);
	}
	/**
	 * Add a field to this collection
	 * 
	 * @param fieldId			Layout ID
	 * @param sourceColumn		Source DB column (can be blank)
	 * @param fieldValidator	Field Validator (can be null)
	 * @param formatter			Formatter to use
	 * 
	 * @return					The resulting Field.
	 */
	public Field add(int fieldId, String sourceColumn, FieldValidator fieldValidator, FieldFormatter formatter) {
		return add(fieldId, sourceColumn, sourceColumn, fieldValidator, formatter);
	}
	/**
	 * Add a field to this collection
	 * 
	 * @param fieldId			Layout ID
	 * @param sourceColumn		Source DB column (can be blank)
	 * @param visibilityGroup	Group name to determine visibility.
	 * @param fieldValidator	Field Validator (can be null)
	 * 
	 * @return					The resulting Field.
	 */
	public Field add(int fieldId, String sourceColumn, String visibilityGroup, FieldValidator fieldValidator) {
		return add(fieldId, sourceColumn, visibilityGroup, fieldValidator, null);
	}
	/**
	 * Add a field to this collection
	 * 
	 * @param fieldId			Layout ID
	 * @param sourceColumn		Source DB column (can be blank)
	 * @param visibilityGroup	Group name to determine visibility.
	 * @param fieldValidator	Field Validator (can be null)
	 * @param formatter			Formatter to use
	 * 
	 * @return					The resulting Field.
	 */
	public Field add(int fieldId, String sourceColumn, String visibilityGroup, FieldValidator fieldValidator, FieldFormatter formatter) {
		Field fe = new Field(this, fieldId, sourceColumn, visibilityGroup, fieldValidator, formatter);
		this.add(fe);
		return fe;
	}

	/**
	 * Return the Field associated with the passed layout ID
	 * 
	 * @return Associated Field.
	 */
	public Field getField(int id) {
		Iterator<Field> iter = this.iterator();
		while (iter.hasNext()) {
			Field f = iter.next();
			if (f.id == id)
				return f;
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Convenience function: For an AutoCompleteTextView, set the adapter
	 * 
	 * @param fieldId	Layout ID of View
	 * @param adapter	Adapter to use
	 */
	public void setAdapter(int fieldId, ArrayAdapter<String> adapter) {
		Field f = getField(fieldId);
		TextView tv = (TextView)f.getView();
		if (tv instanceof AutoCompleteTextView)
			((AutoCompleteTextView)tv).setAdapter(adapter);
	}

	/**
	 * For a View that supports onClick() (all of them?), set the listener.
	 * 
	 * @param id		Layout ID
	 * @param listener	onClick() listener.
	 */
	void setListener(int id, View.OnClickListener listener) {
		Field f = getField(id);
		View v = f.getView();
		if (v != null) {
			f.getView().setOnClickListener(listener);
		} else {
			v = f.getView();
			throw new RuntimeException("Unable to find view for field id " + id);
		}
	}

	/**
	 * Load all fields from the passed cursor
	 * 
	 * @param c Cursor to load Field objects from.
	 */
	public void setAll(Cursor c) {
		Iterator<Field> fi = this.iterator();
		while(fi.hasNext()) {
			Field fe = fi.next();
			fe.set(c);
		}			
	}

	/**
	 * Load all fields from the passed cursor
	 * 
	 * @param c Cursor to load Field objects from.
	 */
	public void setAll(Bundle b) {
		Iterator<Field> fi = this.iterator();
		while(fi.hasNext()) {
			Field fe = fi.next();
			fe.set(b);
		}			
	}

	/**
	 * Load all fields from the passed datamanager
	 * 
	 * @param c Cursor to load Field objects from.
	 */
	public void setAll(DataManager data) {
		Iterator<Field> fi = this.iterator();
		while(fi.hasNext()) {
			Field fe = fi.next();
			fe.set(data);
		}			
	}

	/**
	 * Save all fields to the passed DataManager (ie. 'get' them *into* the DataManager).
	 * 
	 * @param c Cursor to load Field objects from.
	 */
	public void getAll(DataManager data) {
		Iterator<Field> fi = this.iterator();
		while(fi.hasNext()) {
			Field fe = fi.next();
			if (fe.column != null && !fe.column.equals("")) {
				fe.getValue(data);
			}
		}			
	}

	public void getAll(Bundle b) {
		Iterator<Field> fi = this.iterator();

		while(fi.hasNext()) {
			Field fe = fi.next();
			if (fe.column != null && !fe.column.equals("")) {
				fe.getValue(b);
			}
		}
	}

	/**
	 * Internal utility routine to perform one loop validating all fields.
	 * 
	 * @param values 			The Bundle to fill in/use.
	 * @param crossValidating 	Flag indicating if this is a cross validation pass.
	 */
	private boolean doValidate(Bundle values, boolean crossValidating) {
		Iterator<Field> fi = this.iterator();
		boolean isOk = true;

		while(fi.hasNext()) {
			Field fe = fi.next();
			if (fe.validator != null) {
				try {
					fe.validator.validate(this,fe, values, crossValidating);
				} catch(ValidatorException e) {
					mValidationExceptions.add(e);
					isOk = false;
					// Always save the value...even if invalid. Or at least try to.
					if (!crossValidating)
						try {
							values.putString(fe.column, fe.getValue().toString());							
						} catch (Exception e2) {};
				}
			} else {
				if (!fe.column.equals("") && values != null)
					fe.getValue(values);						
			}
		}
		return isOk;
	}

	/**
	 * Reset all field visibility based on user preferences
	 */
	public void resetVisibility() {
		FieldsContext c = this.getContext();
		Iterator<Field> fi = this.iterator();		
		while(fi.hasNext()) {
			Field fe = fi.next();
			fe.resetVisibility(c);
		}
	}

	/**
	 * Loop through and apply validators, generating a Bundle collection as a by-product.
	 * The Bundle collection is then used in cross-validation as a second pass, and finally
	 * passed to each defined cross-validator.
	 * 
	 * @param values The Bundle collection to fill
	 * 
	 * @return boolean True if all validation passed.
	 */
	public boolean validate(Bundle values) {
		if (values == null)
			throw new NullPointerException();

		boolean isOk = true;
		mValidationExceptions.clear();

		// First, just validate individual fields with the cross-val flag set false
		if (!doValidate(values, false))
			isOk = false;
		
		// Now re-run with cross-val set to true.
		if (!doValidate(values, true))
			isOk = false;

		// Finally run the local cross-validation
		Iterator<FieldCrossValidator> i = mCrossValidators.iterator();
		while (i.hasNext()) {
			FieldCrossValidator v = i.next();
			try {
				v.validate(this,values);
			} catch(ValidatorException e) {
				mValidationExceptions.add(e);
				isOk = false;
			}
		}
		return isOk;
	}

	/**
	 * Retrieve the text message associated with the last validation exception t occur.
	 * 
	 * @return res The resource manager to use when looking up strings.
	 */
	public String getValidationExceptionMessage(android.content.res.Resources res) {
		if (mValidationExceptions.size() == 0)
			return "No error";
		else {
			String message = "";
			Iterator<ValidatorException> i = mValidationExceptions.iterator();
			int cnt = 1;
			if (i.hasNext())
				message = "(" + cnt + ") " + i.next().getFormattedMessage(res);
			while (i.hasNext()) {
				cnt ++;
				message += " (" + cnt + ") " + i.next().getFormattedMessage(res) + "\n";
			}
			return message;
		}
	}

	/**
	 * Append a cross-field validator to the collection. These will be applied after
	 * the field-specific validators have all passed.
	 * 
	 * @param v An instance of FieldCrossValidator to append
	 */
	public void addCrossValidator(FieldCrossValidator v) {
		mCrossValidators.add(v);
	}

	///**
	// * Check if any field has been modified
	// * 
	// * @return	true if a field has been edited (or clicked)
	// */
	//public boolean isEdited(){
	//
	//	for (Field field : this){
	//		if (field.isEdited()){
	//			return true;
	//		}
	//	}
	//
	//	return false;
	//}
}

