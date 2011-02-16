package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class EditSeriesList extends EditObjectList<Series> {

	public EditSeriesList() {
		super(R.layout.edit_series_list, R.layout.row_edit_series_list);
	}

	@Override
	protected boolean onSave(Intent i) {
		i.putExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY, mList);
		return true;
	}

	@Override
	protected boolean onCancel() {
		return true;
	}

	@Override
	protected void onSetupView(View target, Series object) {
        if (object != null) {
	        TextView dt = (TextView) target.findViewById(R.id.row_series);
	        if (dt != null)
	              dt.setText(object.getDisplayName());

	        TextView st = (TextView) target.findViewById(R.id.row_series_sort);
	        if (st != null) {
		        if (object.getDisplayName().equals(object.getSortName())) {
		        	st.setVisibility(View.GONE);
		        } else {
		        	st.setVisibility(View.VISIBLE);
					st.setText(object.getSortName());
	        	}
        	}
			EditText et = (EditText) target.findViewById(R.id.row_series_num);
			if (et != null)
				et.setText(object.num);
        }
	}

	@Override
	protected void onInitList(Bundle savedInstanceState) {
		if (savedInstanceState != null && savedInstanceState.containsKey(CatalogueDBAdapter.KEY_SERIES_ARRAY)) {
			mList = savedInstanceState.getParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY);
		}

		if (mList == null) {
			/* Get any information from the extras bundle */
			Bundle extras = getIntent().getExtras();
			if (extras != null && extras.containsKey(CatalogueDBAdapter.KEY_SERIES_ARRAY)) {
				mList = extras.getParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY);
			} else {
				mList = new ArrayList<Series>();				
			}
		}		
	}

	protected ArrayList<String> getSeriesFromDb() {
		ArrayList<String> series_list = new ArrayList<String>();
		Cursor series_cur = mDbHelper.fetchAllSeries();
		startManagingCursor(series_cur);
		while (series_cur.moveToNext()) {
			String series = series_cur.getString(series_cur.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NAME));
			series_list.add(series);
		}
		series_cur.close();
		return series_list;
	}

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {

			ArrayAdapter<String> series_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, getSeriesFromDb());
			((AutoCompleteTextView)this.findViewById(R.id.series)).setAdapter(series_adapter);
			this.findViewById(R.id.add).setOnClickListener(mAddSeriesListener);

		} catch (Exception e) {
			Log.e("BookCatalogue.EditSeriesList.onCreate","Failed to initialize", e);
		}
	}

	private OnClickListener mAddSeriesListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Log.i("BC","Add");
			AutoCompleteTextView t = ((AutoCompleteTextView)EditSeriesList.this.findViewById(R.id.series));
			String s = t.getText().toString().trim();
			if (s.length() > 0) {
				EditText et = ((EditText)EditSeriesList.this.findViewById(R.id.series_num));
				String n = et.getText().toString();
				if (n == null)
					n = "";
				Series series = new Series(t.getText().toString(), n);
				series.id = mDbHelper.lookupSeriesId(series);
				boolean foundMatch = false;
				for(int i = 0; i < mList.size() && !foundMatch; i++) {
					if (series.id != 0L) {
						if (mList.get(i).id == series.id)
							foundMatch = true;
					} else {
						if (series.name.equals(mList.get(i).name))
							foundMatch = true;
					}
				}
				if (foundMatch) {
					Toast.makeText(EditSeriesList.this, "Series matches exiting series", Toast.LENGTH_LONG).show();						
					return;							
				}
				mList.add(series);
	            mAdapter.notifyDataSetChanged();
			} else {
				Toast.makeText(EditSeriesList.this, "Series is empty", Toast.LENGTH_LONG).show();
			}
		}
	};
}
