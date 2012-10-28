package com.eleybourn.bookcatalogue.dialogs;

import java.util.Calendar;

import com.eleybourn.bookcatalogue.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * Dialog class to allow for selection of partial dates from 0AD to 9999AD.
 * 
 * @author pjw
 */
public class PartialDatePicker extends AlertDialog {
	/** Calling context */
	private Context mContext;

	/** Currently displayed year; null if empty/invalid */
	private Integer mYear;
	/** Currently displayed month; null if empty/invalid */
	private Integer mMonth;
	/** Currently displayed day; null if empty/invalid */
	private Integer mDay;

	/** Local ref to month spinner */
	private Spinner mMonthSpinner;
	/** Local ref to day spinner */
	private Spinner mDaySpinner;
	/** Local ref to day spinner adapter */
	private ArrayAdapter<String> mDayAdapter;
	/** Local ref to year text view */
	private EditText mYearView;
	
	/** Listener to be called when date is set or dialog cancelled */
	private OnDateSetListener mListener;

	/**
	 * Listener to receive notifications when dialog is closed by any means.
	 * 
	 * @author pjw
	 */
	public static interface OnDateSetListener {
		public void onDateSet(PartialDatePicker dialog, Integer year, Integer month, Integer day);
		public void onCancel(PartialDatePicker dialog);
	}

	/**
	 * Constructor
	 * 
	 * @param context		Calling context
	 * @param listener		Listener for dialog events
	 */
	public PartialDatePicker(Context context, OnDateSetListener listener) {
		this(context, listener, null, null, null);
	}

	/**
	 * Constructor
	 * 
	 * @param context		Calling context
	 * @param listener		Listener for dialog events
	 * @param year			Starting year
	 * @param month			Starting month
	 * @param day			Starting day
	 */
	public PartialDatePicker(Context context, OnDateSetListener listener, Integer year, Integer month, Integer day) {
		super(context);

		mContext = context;
		mListener= listener;

		mYear = year;
		mMonth = month;
		mDay = day;

		// Get the layout
		LayoutInflater inf = this.getLayoutInflater();
		View root = inf.inflate(R.layout.date_picker, null);

		// Ensure components match current locale order
		reorderPickers(root);
		
		// Set the view
		setView(root);

		// Get UI components for later use
		mYearView = (EditText)root.findViewById(R.id.year);
		mMonthSpinner = (Spinner)root.findViewById(R.id.month);
		mDaySpinner = (Spinner)root.findViewById(R.id.day);

		// Create month spinner adapter
		ArrayAdapter<String> monthAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item);
		monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mMonthSpinner.setAdapter(monthAdapter);

		// Create day spinner adapter
		mDayAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item);
		mDayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mDaySpinner.setAdapter(mDayAdapter);

		// First entry is 'unknown'
		monthAdapter.add("---"); 
		mDayAdapter.add("--"); 

		// Make sure that the spinner can initially take any 'day' value. Otherwise, when a dialog is
		// reconstructed after rotation, the 'day' field will not be restorable by Android.
		regenDaysOfMonth(31);

		// Get a calendar for locale-related info
		Calendar cal = Calendar.getInstance();
		// Add all month named (abbreviated)
		for(int i = 0; i < 12; i++) {
			cal.set(Calendar.MONTH, i);
			monthAdapter.add(String.format("%tb",cal));
		}

		// Handle selections from the MONTH spinner
		mMonthSpinner.setOnItemSelectedListener(new OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				int pos = mMonthSpinner.getSelectedItemPosition();
				handleMonth(pos);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				handleMonth(null);
			}}
		);
		
		// Handle selections from the DAY spinner
		mDaySpinner.setOnItemSelectedListener(new OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				int pos = mDaySpinner.getSelectedItemPosition();
				handleDay(pos);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				handleDay(null);
			}}
		);

		// Handle all changes to the YEAR text
		mYearView.addTextChangedListener(new TextWatcher() {

			@Override
			public void afterTextChanged(Editable s) {
				handleYear();
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}});
		

		// Handle YEAR +
		((Button)root.findViewById(R.id.plus)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mYear != null) {
					mYearView.setText((++mYear).toString());
				} else {
					mYearView.setText(Calendar.getInstance().get(Calendar.YEAR) + "");
				}
			}}
		);

		// Handle YEAR -
		((Button)root.findViewById(R.id.minus)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mYear != null) {
					// We can't support negatvive years yet because of sorting issues and the fact that 
					// the Calendar object bugs out with them. To fix the calendar object interface we 
					// would need to translate -ve years to Epoch settings throughout the app. For now,
					// not many people have books written before 0AD, so it's a low priority.
					if (mYear > 0) {
						mYearView.setText((--mYear).toString());
					}
				} else {
					mYearView.setText(Calendar.getInstance().get(Calendar.YEAR) + "");
				}
			}}
		);

		// Handle MONTH +
		((Button)root.findViewById(R.id.plusMonth)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pos = (mMonthSpinner.getSelectedItemPosition() + 1) % mMonthSpinner.getCount();
				mMonthSpinner.setSelection(pos);
			}}
		);

		// Handle MONTH -
		((Button)root.findViewById(R.id.minusMonth)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pos = (mMonthSpinner.getSelectedItemPosition() - 1 + mMonthSpinner.getCount()) % mMonthSpinner.getCount();
				mMonthSpinner.setSelection(pos);
			}}
		);

		// Handle DAY +
		((Button)root.findViewById(R.id.plusDay)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pos = (mDaySpinner.getSelectedItemPosition() + 1) % mDaySpinner.getCount();
				mDaySpinner.setSelection(pos);
			}}
		);

		// Handle DAY -
		((Button)root.findViewById(R.id.minusDay)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int pos = (mDaySpinner.getSelectedItemPosition() - 1 + mDaySpinner.getCount()) % mDaySpinner.getCount();
				mDaySpinner.setSelection(pos);
			}}
		);

		// Handle OK
		((Button)root.findViewById(R.id.ok)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Ensure the date is 'hierarchically valid'; require year, if month is non-null, require month if day non-null
				if (mDay != null && mDay > 0 && (mMonth == null || mMonth == 0)) {
					Toast.makeText(mContext, R.string.if_day_is_specified_month_and_year_must_be, Toast.LENGTH_LONG).show();
				} else if (mMonth != null && mMonth > 0 && mYear == null) {
					Toast.makeText(mContext, R.string.if_month_is_specified_year_must_be, Toast.LENGTH_LONG).show();					
				} else {
					mListener.onDateSet(PartialDatePicker.this, mYear, mMonth, mDay);					
				}
			}}
		);

		// Handle Cancel
		((Button)root.findViewById(R.id.cancel)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mListener.onCancel(PartialDatePicker.this);				
			}}
		);

		// Handle any other form of cancellation
		this.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface arg0) {
				mListener.onCancel(PartialDatePicker.this);				
			}});

		// We are all set up!
		
		// Set the initial date
		setDate(year, month, day);
	}

	/**
	 * Set the date to display
	 * 
	 * @param year		Year (or null)
	 * @param month		Month (or null)
	 * @param day		Day (or null)
	 */
	public void setDate(Integer year, Integer month, Integer day) {
		mYear = year;
		mMonth = month;
		mDay = day;
		
		String yearVal;
		if (year != null) {
			yearVal = year.toString();
		} else {
			yearVal = "";
		}
		mYearView.setText(yearVal);
		Editable e = mYearView.getEditableText();
		Selection.setSelection(e, e.length(), e.length());
		if (yearVal.equals("")) {
			mYearView.requestFocus();
			getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		}

		if (month == null || month == 0) {
			mMonthSpinner.setSelection(0);
		} else {
			mMonthSpinner.setSelection(month);			
		}

		if (day == null || day == 0) {
			mDaySpinner.setSelection(0);
		} else {
			mDaySpinner.setSelection(day);			
		}

	}

	/**
	 * Handle changes to the YEAR field.
	 */
	private void handleYear() {
		// Try to convert to integer
		String val = mYearView.getText().toString();
		try {
			int year = Integer.parseInt(val);
			mYear = year;
		} catch (Exception e) {
			mYear = null;
		}

		// Seems reasonable to disable other spinners if year invalid, but it actually
		// not very friendly when entering data for new books.
		regenDaysOfMonth(null);

		// Handle the result
		//if (mYear == null) {
		//	mMonthSpinner.setEnabled(false);			
		//	mDaySpinner.setEnabled(false);			
		//} else {
		//	// Enable other spinners as appropriate
		//	mMonthSpinner.setEnabled(true);			
		//	mDaySpinner.setEnabled(mMonthSpinner.getSelectedItemPosition() > 0);
		//	regenDaysOfMonth(null);
		//}

	}

	/**
	 * Handle changes to the MONTH field
	 * @param pos
	 */
	private void handleMonth(Integer pos) {
		// See if we got a valid month
		boolean isMonth = (pos != null && pos > 0);

		// Seems reasonable to disable other spinners if year invalid, but it actually
		// not very friendly when entering data for new books.
		if (!isMonth) {
			// If not, disable DAY spinner; we leave current value intact in case a valid month is set later
			//mDaySpinner.setEnabled(false);
			mMonth = null;
		} else {
			// Set the month and make sure DAY spinner is valid
			mMonth = pos;
			//mDaySpinner.setEnabled(true);
			//regenDaysOfMonth(null);
		}
		regenDaysOfMonth(null);
	}

	/**
	 * Handle changes to the DAY spinner
	 * 
	 * @param pos
	 */
	private void handleDay(Integer pos) {
		boolean isSelected = (pos != null && pos > 0);
		if (!isSelected) {
			mDay = null;
		} else {
			mDay = pos;
		}
	}

	/**
	 * Depending on year/month selected, generate the DAYS spinner values
	 */
	private void regenDaysOfMonth(Integer totalDays) {
		// Save the current day in case the regen alters it
		Integer daySave = mDay;
		//ArrayAdapter<String> days = (ArrayAdapter<String>)mDaySpinner.getAdapter();

		// Make sure we have the 'no-day' value in the dialog
		if (mDayAdapter.getCount() == 0)
			mDayAdapter.add("--");

		// Determine the total days if not passed to us
		if (totalDays == null || totalDays == 0) {
			if (mYear != null && mMonth != null && mMonth > 0) {
				// Get a calendar for the year/month
				Calendar cal = Calendar.getInstance();
				cal.set(Calendar.YEAR, mYear);
				cal.set(Calendar.MONTH, mMonth-1);
				// Add appropriae days
				totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
			} else {
				totalDays = 31;
			}
		}
		
		// If we have a valid total number of days, then update the list
		if (totalDays != null) {
			if (mDayAdapter.getCount() < totalDays) {
				for(int i = mDayAdapter.getCount(); i <= totalDays; i++) {
					mDayAdapter.add(i + "");
				}
			} else {
				for(int i = mDayAdapter.getCount() - 1; i > totalDays; i--) {
					mDayAdapter.remove(i + "");
				}
			}

			// Ensure selected day is valid
			if (daySave == null || daySave == 0) {
				mDaySpinner.setSelection(0);
			} else {
				if (daySave > totalDays)
					daySave = totalDays;
				mDaySpinner.setSelection(daySave);
			}			
		}
	}

	/**
	 * Reorder the views in the dialog to suit the curret locale.
	 * 
	 * @param root	Root view
	 */
    private void reorderPickers(View root) {
        char[] order = DateFormat.getDateFormatOrder(mContext);
        
        /* Default order is month, date, year so if that's the order then
         * do nothing.
         */
        if ((order[0] == DateFormat.YEAR) && (order[1] == DateFormat.MONTH)) {
            return;
        }
        
        /* Remove the 3 pickers from their parent and then add them back in the
         * required order.
         */
        LinearLayout parent = (LinearLayout) root.findViewById(R.id.dateSelector);
        // Get the three views
        View y = root.findViewById(R.id.yearSelector);
        View m = root.findViewById(R.id.monthSelector);
        View d = root.findViewById(R.id.daySelector);
        // Remove them
        parent.removeAllViews();
        // Re-add in the correct order.
        for (char c : order) {
            if (c == DateFormat.DATE) {
                parent.addView(d);
            } else if (c == DateFormat.MONTH) {
                parent.addView(m);
            } else {
                parent.addView (y);
            }
        }
    }
}
