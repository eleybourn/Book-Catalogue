package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Base class for editing a list of objects.
 * 
 * @author Grunthos
 *
 * @param <T>
 */
abstract public class EditObjectList<T> extends ListActivity {

	// List
	protected ArrayList<T> mList = null;
	// Adapter used to manage list
	protected ArrayAdapter<T> mAdapter;

	// DB connection
	protected CatalogueDBAdapter mDbHelper;

	// The resource ID for the base view
	private int mBaseViewId;
	// The resource ID for the row view
	private int mRowViewId;

	/**
	 * Called from onCreate to initialize the list.
	 * 
	 * @param savedInstanceState	As passed to onCreate
	 */
	abstract protected void onInitList(Bundle savedInstanceState);
	/**
	 * Called when user clicks the 'Save' button (if present).
	 * 
	 * @param i		A newly created Intent to store output if necessary.
	 * 
	 * @return		True if activity should exit, false to abort exit.
	 */
	abstract protected boolean onSave(Intent i);
	/**
	 * Called when user presses 'Cancel' button if present.
	 * 
	 * @return		True if activity should exit, false to abort exit.
	 */
	abstract protected boolean onCancel();
	/**
	 * Call to set up the row view.
	 * 
	 * @param target	The target row view object
	 * @param object	The object (or type T) from which to draw values.
	 */
	abstract protected void onSetupView(View target, T object);

	/**
	 * Constructor
	 * 
	 * @param baseViewId	Resource id of base view
	 * @param rowViewId		Resource id of row view
	 */
	EditObjectList(int baseViewId, int rowViewId) {
		mBaseViewId = baseViewId;
		mRowViewId = rowViewId;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			// Setup the DB
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();

			// Set the view
			setContentView(mBaseViewId);

			// Add handlers for 'Save' and 'Cancel'
			this.findViewById(R.id.confirm).setOnClickListener(mSaveListener);
			this.findViewById(R.id.cancel).setOnClickListener(mCancelListener);

			// Ask the subclass to setup the list
			onInitList(savedInstanceState);

			// Set up list handling
	        this.mAdapter = new ListAdapter(this, mRowViewId, mList);
	        setListAdapter(this.mAdapter);

	        // Look for title and title_label
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				String s = extras.getString("title_label");
				setTextOrHideView(R.id.title_label, s);
				s = extras.getString("title");
				setTextOrHideView(R.id.title, s);
			}

		} catch (Exception e) {
			Log.e("BookCatalogue.EditObjectList.onCreate","Failed to initialize", e);
		}
	}

	/**
	 * Utility routine to set a TextView to a string, or hide it on failure.
	 * 
	 * @param id	View ID
	 * @param s		String to set
	 */
	private void setTextOrHideView(int id, String s) {
		View v = null;
		try {
			v = this.findViewById(id);
			if (s != null && s.length() > 0) {
				((TextView)v).setText(s);
				return;			
			}
		} catch (Exception e) {
		};		
		if (v != null)
			v.setVisibility(View.GONE);		
	}
	
	/**
	 * Handle 'Save'
	 */
	private OnClickListener mSaveListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent i = new Intent();
			if (onSave(i)) {
				setResult(RESULT_OK, i);
				finish();
			}
		}
	};

	/**
	 * Handle 'Cancel'
	 */
	private OnClickListener mCancelListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (onCancel())
				finish();
		}
	};

	/**
	 * Handle deletion of a row
	 */
	private OnClickListener mRowDeleteListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Log.i("BC","Delete");
			View pv = (View) v.getParent();
            TextView pt = (TextView) pv.findViewById(R.id.row_position);
            int pos = Integer.parseInt(pt.getText().toString()) - 1;
            mList.remove(pos);
            mAdapter.notifyDataSetChanged();
		}
	};

	/**
	 * Handle moving a row UP
	 */
	private OnClickListener mRowUpListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Log.i("BC","Move UP");
			View pv = (View) v.getParent();
            TextView pt = (TextView) pv.findViewById(R.id.row_position);
            int pos = Integer.parseInt(pt.getText().toString()) - 1;
            if (pos == 0)
            	return;
            T old = mList.get(pos-1);
            mList.set(pos-1, mList.get(pos));
            mList.set(pos, old);
            mAdapter.notifyDataSetChanged();
		}
		
	};

	/**
	 * Handle moving a row DOWN
	 */
	private OnClickListener mRowDownListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Log.i("BC","Move DOWN");
			View pv = (View) v.getParent();
            TextView pt = (TextView) pv.findViewById(R.id.row_position);
            int pos = Integer.parseInt(pt.getText().toString()) - 1;
            if (pos == (mList.size()-1) )
            	return;
            T old = mList.get(pos);
            mList.set(pos, mList.get(pos+1));
            mList.set(pos+1, old);
            mAdapter.notifyDataSetChanged();
		}
		
	};

	/**
	 * Adapter to manage the rows.
	 * 
	 * @author Grunthos
	 */
	final class ListAdapter extends ArrayAdapter<T> {

		// Flag fields to (slightly) optimize lookups and prevent looking for 
		// fields that are not there.
		private boolean mCheckedFields = false;
		private boolean mHasPosition = false;
		private boolean mHasUp = false;
		private boolean mHasDown = false;
		private boolean mHasDelete = false;

        public ListAdapter(Context context, int textViewResourceId, ArrayList<T> items) {
                super(context, textViewResourceId, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
        	// Get the view; if not defined, load it.
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(mRowViewId, null);
            }
            // Get the object, if not null, do some processing
            T o = mList.get(position);
            if (o != null) {
            	// Try to set position value
            	if (mHasPosition || !mCheckedFields) {
	                TextView pt = (TextView) v.findViewById(R.id.row_position);
	                if(pt != null){
	                	mHasPosition = true;
	                	pt.setText(Long.toString(position+1));
	                }
            	}

            	// Try to set the UP handler
            	if (mHasUp || !mCheckedFields) {
                    ImageView up = (ImageView) v.findViewById(R.id.row_up);
                    if (up != null) {
                    	up.setOnClickListener(mRowUpListener);
                    	mHasUp = true;
                    }
            	}

            	// Try to set the DOWN handler
            	if (mHasDown || !mCheckedFields) {
                    ImageView dn = (ImageView) v.findViewById(R.id.row_down);
                    if (dn != null) {
                    	dn.setOnClickListener(mRowDownListener);
                    	mHasDown = true;
                    }
            	}

            	// Try to set the DELETE handler
            	if (mHasDelete || !mCheckedFields) {
                	ImageView del = (ImageView) v.findViewById(R.id.row_delete);
                    if (del != null) {
        	    		del.setImageResource(android.R.drawable.ic_delete);
                    	del.setOnClickListener(mRowDeleteListener);   
                    	mHasDelete = true;
                    }            		
            	}

            	// Ask the subclass to set other fields.
            	try {
                    onSetupView(v, o);            		
            	} catch (Exception e) {
            		Log.e("BookCatalogue.EditObjectList", "onSetupView failed", e);
            	}

                mCheckedFields = true;
            }
            return v;
        }
}}
