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
package com.eleybourn.bookcatalogue.dialogs;

import java.util.Calendar;

import android.app.AlertDialog;
import android.content.Context;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;

/**
 * Dialog class to allow for selection of partial dates from 0AD to 9999AD.
 * The constructors and interface are now protected because this really should
 * only be called as part of the fragment version.
 *
 * @author pjw
 */
public class PartialDatePicker extends AlertDialog {
    /**
     * Calling context
     */
    private final Context mContext;

    /**
     * Currently displayed year; null if empty/invalid
     */
    private Integer mYear;
    /**
     * Currently displayed month; null if empty/invalid
     */
    private Integer mMonth;
    /**
     * Currently displayed day; null if empty/invalid
     */
    private Integer mDay;

    /**
     * Listener to be called when date is set or dialog cancelled
     */
    private OnDateSetListener mListener;

    /**
     * Local ref to month spinner
     */
    private final Spinner mMonthSpinner;
    /**
     * Local ref to day spinner
     */
    private final Spinner mDaySpinner;
    /**
     * Local ref to day spinner adapter
     */
    private final ArrayAdapter<String> mDayAdapter;
    /**
     * Local ref to year text view
     */
    private final EditText mYearView;

    /**
     * Listener to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    protected interface OnDateSetListener {
        void onDateSet(PartialDatePicker dialog, Integer year, Integer month, Integer day);

        void onCancel(PartialDatePicker dialog);
    }

    /**
     * Constructor
     *
     * @param context Calling context
     */
    protected PartialDatePicker(Context context) {
        this(context, null, null, null);
    }

    /**
     * Constructor
     *
     * @param context Calling context
     * @param year    Starting year
     * @param month   Starting month
     * @param day     Starting day
     */
    protected PartialDatePicker(Context context, Integer year, Integer month, Integer day) {
        super(context);

        mContext = context;

        mYear = year;
        mMonth = month;
        mDay = day;

        // Get the layout
        LayoutInflater inf = this.getLayoutInflater();
        View root = inf.inflate(R.layout.dialog_date_picker, null);

        // Ensure components match current locale order
        reorderPickers(root);

        // Set the view
        setView(root);

        // Get UI components for later use
        mYearView = root.findViewById(R.id.field_year);
        mMonthSpinner = root.findViewById(R.id.field_month);
        mDaySpinner = root.findViewById(R.id.field_day);

        // Create month spinner adapter
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mMonthSpinner.setAdapter(monthAdapter);

        // Create day spinner adapter
        mDayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item);
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
        // Set the day to 1...so avoid wrap on short months (default to current date)
        cal.set(Calendar.DAY_OF_MONTH, 1);
        // Add all month named (abbreviated)
        for (int i = 0; i < 12; i++) {
            cal.set(Calendar.MONTH, i);
            monthAdapter.add(String.format("%tb", cal));
        }

        // Handle selections from the MONTH spinner
        mMonthSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

                                                    @Override
                                                    public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                                                        int pos = mMonthSpinner.getSelectedItemPosition();
                                                        handleMonth(pos);
                                                    }

                                                    @Override
                                                    public void onNothingSelected(AdapterView<?> arg0) {
                                                        handleMonth(null);
                                                    }
                                                }
        );

        // Handle selections from the DAY spinner
        mDaySpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

                                                  @Override
                                                  public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                                                      int pos = mDaySpinner.getSelectedItemPosition();
                                                      handleDay(pos);
                                                  }

                                                  @Override
                                                  public void onNothingSelected(AdapterView<?> arg0) {
                                                      handleDay(null);
                                                  }
                                              }
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
            }
        });


        // Handle YEAR +
        root.findViewById(R.id.button_year_plus).setOnClickListener(v -> {
                    if (mYear != null) {
                        mYearView.setText((++mYear).toString());
                    } else {
                        mYearView.setText(Calendar.getInstance().get(Calendar.YEAR));
                    }
                }
        );

        // Handle YEAR -
        root.findViewById(R.id.button_year_minus).setOnClickListener(v -> {
                    if (mYear != null) {
                        // We can't support negative years yet because of sorting issues and the fact that
                        // the Calendar object bugs out with them. To fix the calendar object interface we
                        // would need to translate -ve years to Epoch settings throughout the app. For now,
                        // not many people have books written before 0AD, so it's a low priority.
                        if (mYear > 0) {
                            mYearView.setText((--mYear).toString());
                        }
                    } else {
                        mYearView.setText(Calendar.getInstance().get(Calendar.YEAR));
                    }
                }
        );

        // Handle MONTH +
        root.findViewById(R.id.button_month_plus).setOnClickListener(v -> {
                    int pos = (mMonthSpinner.getSelectedItemPosition() + 1) % mMonthSpinner.getCount();
                    mMonthSpinner.setSelection(pos);
                }
        );

        // Handle MONTH -
        root.findViewById(R.id.button_month_minus).setOnClickListener(v -> {
                    int pos = (mMonthSpinner.getSelectedItemPosition() - 1 + mMonthSpinner.getCount()) % mMonthSpinner.getCount();
                    mMonthSpinner.setSelection(pos);
                }
        );

        // Handle DAY +
        root.findViewById(R.id.button_day_plus).setOnClickListener(v -> {
                    int pos = (mDaySpinner.getSelectedItemPosition() + 1) % mDaySpinner.getCount();
                    mDaySpinner.setSelection(pos);
                }
        );

        // Handle DAY -
        root.findViewById(R.id.button_day_minus).setOnClickListener(v -> {
                    int pos = (mDaySpinner.getSelectedItemPosition() - 1 + mDaySpinner.getCount()) % mDaySpinner.getCount();
                    mDaySpinner.setSelection(pos);
                }
        );

        // Handle OK
        root.findViewById(R.id.button_ok).setOnClickListener(v -> {
                    // Ensure the date is 'hierarchically valid'; require year, if month is non-null, require month if day non-null
                    if (mDay != null && mDay > 0 && (mMonth == null || mMonth == 0)) {
                        Toast.makeText(mContext, R.string.if_day_is_specified_month_and_year_must_be, Toast.LENGTH_LONG).show();
                    } else if (mMonth != null && mMonth > 0 && mYear == null) {
                        Toast.makeText(mContext, R.string.if_month_is_specified_year_must_be, Toast.LENGTH_LONG).show();
                    } else {
                        if (mListener != null)
                            mListener.onDateSet(PartialDatePicker.this, mYear, mMonth, mDay);
                    }
                }
        );

        // Handle Cancel
        root.findViewById(R.id.button_cancel).setOnClickListener(v -> {
                    if (mListener != null)
                        mListener.onCancel(PartialDatePicker.this);
                }
        );

        // Handle any other form of cancellation
        this.setOnCancelListener(arg0 -> {
            if (mListener != null)
                mListener.onCancel(PartialDatePicker.this);
        });

        // We are all set up!

        // Set the initial date
        setDate(year, month, day);
    }

    /**
     * Set the date to display
     *
     * @param year  Year (or null)
     * @param month Month (or null)
     * @param day   Day (or null)
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
        if (yearVal.isEmpty()) {
            mYearView.requestFocus();
            assert getWindow() != null;
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
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
     * Accessor
     */
    public Integer getYear() {
        return mYear;
    }

    /**
     * Accessor
     */
    public Integer getMonth() {
        return mMonth;
    }

    /**
     * Accessor
     */
    public Integer getDay() {
        return mDay;
    }

    /**
     * Handle changes to the YEAR field.
     */
    private void handleYear() {
        // Try to convert to integer
        String val = mYearView.getText().toString();
        try {
            mYear = Integer.parseInt(val);
        } catch (Exception e) {
            mYear = null;
        }

        // Seems reasonable to disable other spinners if year invalid, but it actually
        // not very friendly when entering data for new books.
        regenDaysOfMonth(null);

        // Handle the result

    }

    /**
     * Handle changes to the MONTH field
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
        }
        regenDaysOfMonth(null);
    }

    /**
     * Handle changes to the DAY spinner
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
                cal.set(Calendar.MONTH, mMonth - 1);
                // Add appropriate days
                totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            } else {
                totalDays = 31;
            }
        }

        // If we have a valid total number of days, then update the list
        // Don't forget we have a '--' in the adapter
        if (mDayAdapter.getCount() <= totalDays) {
            for (int i = mDayAdapter.getCount(); i <= totalDays; i++) {
                mDayAdapter.add(i + "");
            }
        } else {
            for (int i = mDayAdapter.getCount() - 1; i > totalDays; i--) {
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

    private static final char DATE_FORMAT_MONTH = 'M';
    private static final char DATE_FORMAT_DAY = 'd';

    /**
     * Reorder the views in the dialog to suit the current locale.
     *
     * @param root Root view
     */
    private void reorderPickers(View root) {
        char[] order;
        try {
            // This actually throws exception in some versions of Android, specifically when
            // the locale-specific date format has the day name (EEE) in it. So we exit and
            // just use our default order in these cases.
            // See Issue 712.
            order = DateFormat.getDateFormatOrder(mContext);
        } catch (Exception e) {
            return;
        }

        /* Default order is {year, month, date} so if that's the order then
         * do nothing.
         */
        if ((order[0] == DATE_FORMAT_DAY) && (order[1] == DATE_FORMAT_MONTH)) {
            return;
        }

        /* Remove the 3 pickers from their parent and then add them back in the
         * required order.
         */
        LinearLayout parent = root.findViewById(R.id.dateSelector);
        // Get the three views
        View y = root.findViewById(R.id.yearSelector);
        View m = root.findViewById(R.id.monthSelector);
        View d = root.findViewById(R.id.daySelector);
        // Remove them
        parent.removeAllViews();
        // Re-add in the correct order.
        for (char c : order) {
            if (c == DATE_FORMAT_DAY) {
                parent.addView(d);
            } else if (c == DATE_FORMAT_MONTH) {
                parent.addView(m);
            } else {
                parent.addView(y);
            }
        }
    }

    protected void setOnDateSetListener(OnDateSetListener listener) {
        mListener = listener;
    }

}
