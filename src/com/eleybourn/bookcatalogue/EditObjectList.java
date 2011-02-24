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

import java.util.ArrayList;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Base class for editing a list of objects. The inheritor must specify a view id
 * and a row view id to the constructor of this class. Each view can have the
 * following sub-view IDs present which will be automatically handled. Optional
 * IDs are noted:
 * 
 * Main View:
 * 	- cancel
 *  - confirm
 *  - add (OPTIONAL)
 *  
 * Row View (must have layout ID set to android:id="@+id/row"):
 *  - position (OPTIONAL)
 *  - up (OPTIONAL)
 *  - down (OPTIONAL)
 *  - delete (OPTIONAL)
 * 
 * The row view is tagged using TAG_POSITION, defined in strings.xml, to save the rows position for
 * use when moving the row up/down or deleting it.
 *
 * Abstract methods are defined for specific tasks (Add, Save, Load etc). While would 
 * be tempting to add local implementations the java generic model seems to prevent this.
 * 
 * This Activity uses TouchListView from CommonsWare which is in turn based on Android code
 * for TouchIntercptor which was (reputedly) removed in Android 2.2. 
 * 
 * For this code to work, the  main view must contain:
 * - a TouchListView with id = @+id/android:list
 * - the TouchListView must have the following attributes:
 * 		tlv:grabber="@+id/<SOME ID FOR AN IMAGE>" (eg. "@+id/grabber")
 *		tlv:remove_mode="none"
 *		tlv:normal_height="64dip" ---- or some simlar value
 * 
 * Each row view must have:
 * - an ID of @+id/row
 * - an ImageView with an ID of "@+id/<SOME ID FOR AN IMAGE>" (eg. "@+id/grabber")
 * - (OPTIONAL) a subview with an ID of "@+id/row_details"; when clicked, this will result 
 *   in the onRowClick event.
 * 
 * @author Grunthos
 *
 * @param <T>
 */
abstract public class EditObjectList<T extends Parcelable> extends ListActivity {

	// List
	protected ArrayList<T> mList = null;
	// Adapter used to manage list
	protected ArrayAdapter<T> mAdapter;

	// DB connection
	protected CatalogueDBAdapter mDbHelper;

	protected String mBookTitle;
	protected String mBookTitleLabel;

	// The key to use in the Bundle to get the array
	private String mKey;
	// The resource ID for the base view
	private int mBaseViewId;
	// The resource ID for the row view
	private int mRowViewId;

	// Row ID... mainly used (if list is from a book) to know if book is new.
	protected Long mRowId = null;

	/**
	 * Called when user clicks the 'Add' button (if present).
	 * 
	 * @param v		The view that was clicked ('add' button).
	 * 
	 * @return		True if activity should exit, false to abort exit.
	 */
	abstract protected void onAdd(View v);

	/**
	 * Call to set up the row view.
	 * 
	 * @param target	The target row view object
	 * @param object	The object (or type T) from which to draw values.
	 */
	abstract protected void onSetupView(View target, T object);

	/**
	 * Called when an otherwise inactive part of the row is clicked.
	 * 
	 * @param target	The view clicked
	 * @param object	The object associated with this row
	 */
	abstract protected void onRowClick(View target, T object);
	
	/**
	 * Called when user clicks the 'Save' button (if present). Primary task is
	 * to return a boolean indicating it is OK to continue.
	 * 
	 * Can be overridden to perform other checks.
	 * 
	 * @param i		A newly created Intent to store output if necessary.
	 * 
	 * @return		True if activity should exit, false to abort exit.
	 */
	protected boolean onSave(Intent i) { return true; };
 
	/**
	 * Called when user presses 'Cancel' button if present. Primary task is
	 * return a boolean indicating it is OK to continue.
	 * 
	 * Can be overridden to perform other checks.
	 * 
	 * @return		True if activity should exit, false to abort exit.
	 */
	protected boolean onCancel() { return true;};

	/**
	 * Constructor
	 * 
	 * @param baseViewId	Resource id of base view
	 * @param rowViewId		Resource id of row view
	 */
	EditObjectList(String key, int baseViewId, int rowViewId) {
		mKey = key;
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

			// Add handlers for 'Save', 'Cancel' and 'Add'
			setupListener(R.id.confirm, mSaveListener);
			setupListener(R.id.cancel, mCancelListener);
			setupListener(R.id.add, mAddListener);

			// Ask the subclass to setup the list; we need this before 
			// building the adapter.
			if (savedInstanceState != null && savedInstanceState.containsKey(mKey)) {
				mList = savedInstanceState.getParcelableArrayList(mKey);
			}

			if (mList == null) {
				/* Get any information from the extras bundle */
				Bundle extras = getIntent().getExtras();
				if (extras != null) {
					mList = extras.getParcelableArrayList(mKey);
				}
				if (mList == null) {
					throw new RuntimeException("Unable to find list key '" + mKey + "' in passed data");		
				}
			}		

			// Set up list handling
	        this.mAdapter = new ListAdapter(this, mRowViewId, mList);
	        setListAdapter(this.mAdapter);

	        // Look for title and title_label
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				mRowId = extras.getLong(CatalogueDBAdapter.KEY_ROWID);
				mBookTitleLabel = extras.getString("title_label");
				mBookTitle = extras.getString("title");
				setTextOrHideView(R.id.title_label, mBookTitleLabel);
				setTextOrHideView(R.id.title, mBookTitle);
			}

			
			TouchListView tlv=(TouchListView)getListView();
			tlv.setDropListener(mDropListener);
			//tlv.setRemoveListener(onRemove);

		} catch (Exception e) {
			Log.e("BookCatalogue.EditObjectList.onCreate","Failed to initialize", e);
		}
	}

	private TouchListView.DropListener mDropListener=new TouchListView.DropListener() {
		@Override
		public void drop(int from, int to) {
				Log.i("BC", "Drop " + from + "->"+to);
				T item=mAdapter.getItem(from);				
				mAdapter.remove(item);
				mAdapter.insert(item, to);
		}
	};

	/**
	 * Utility routine to setup a listener for the specified view id
	 * 
	 * @param id	Resource ID
	 * @param l		Listener
	 * 
	 * @return		true if resource present, false if not
	 */
	private boolean setupListener(int id, OnClickListener l) {
		View v = this.findViewById(id);
		if (v == null)
			return false;
		v.setOnClickListener(l);
		return true;
	}

	/**
	 * Utility routine to set a TextView to a string, or hide it on failure.
	 * 
	 * @param id	View ID
	 * @param s		String to set
	 */
	protected void setTextOrHideView(View v, int id, String s) {
		if (v != null && v.getId() != id)
			v = v.findViewById(id);
		setTextOrHideView(v,s);
	}
	
	protected void setTextOrHideView(View v, String s) {
		try {
			if (s != null && s.length() > 0) {
				((TextView)v).setText(s);
				return;			
			}
		} catch (Exception e) {
		};		
		// If we get here, something went wrong.
		if (v != null)
			v.setVisibility(View.GONE);		
	}
	
	protected void setTextOrHideView(int id, String s) {
		setTextOrHideView(this.findViewById(id), id, s);
	}
	
	/**
	 * Handle 'Save'
	 */
	private OnClickListener mSaveListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent i = new Intent();
			i.putParcelableArrayListExtra(mKey, mList);
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
	 * Handle 'Add'
	 */
	private OnClickListener mAddListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			onAdd(v);
		}		
	};

	/**
	 * Find the first ancestor that has the ID R.id.row. This 
	 * will be the complete row View. Use the TAG on that to get
	 * the physical row number.
	 * 
	 * @param v		View to search from
	 * 
	 * @return		The row view.
	 */
	private Integer getViewRow(View v) {
		View pv = v;
		while(pv.getId() != R.id.row) {
			ViewParent p = pv.getParent();
			if (!(p instanceof View))
				throw new RuntimeException("Could not find row view in view ancestors");
			pv = (View) p;
		}
		return (Integer) pv.getTag(R.id.TAG_POSITION);
	}

	/**
	 * Handle deletion of a row
	 */
	private OnClickListener mRowDeleteListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			int pos = getViewRow(v);
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
			int pos = getViewRow(v);
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
			int pos = getViewRow(v);
            if (pos == (mList.size()-1) )
            	return;
            T old = mList.get(pos);
            mList.set(pos, mList.get(pos+1));
            mList.set(pos+1, old);
            mAdapter.notifyDataSetChanged();
		}
		
	};

	/**
	 * Handle moving a row DOWN
	 */
	private OnClickListener mRowClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			int pos = getViewRow(v);
			onRowClick(v, mList.get(pos));
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
            
            // Save this views position
            v.setTag(R.id.TAG_POSITION, new Integer(position));

            {
            	// Giving the whole row ad onClickListener seems to interfere
            	// with drag/drop.
            	View details = v.findViewById(R.id.row_details);
            	if (details != null)
                    details.setOnClickListener(mRowClickListener);
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
	}

	/**
	 * Ensure that the list is saved.
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {    	
    	super.onSaveInstanceState(outState);
    	// save list
    	outState.putParcelableArrayList(mKey, mList);
    }
}
