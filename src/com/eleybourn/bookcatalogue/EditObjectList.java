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

import java.io.Serializable;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

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
 * @author Philip Warner
 *
 * @param <T>
 */
abstract public class EditObjectList<T extends Serializable> extends ListActivity {

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
	abstract protected void onRowClick(View target, int position, T object);
	
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
	protected boolean onSave(Intent intent) { return true; };
 
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
	 * Called when the list had been modified in some way.
	 */
	protected void onListChanged() { };

	/**
	 * Called to get the list if it was not in the intent.
	 */
	protected ArrayList<T> getList() { return null; };

	/**
	 * Constructor
	 * 
	 * @param baseViewId	Resource id of base view
	 * @param rowViewId		Resource id of row view
	 */
	protected EditObjectList(String key, int baseViewId, int rowViewId) {
		mKey = key;
		mBaseViewId = baseViewId;
		mRowViewId = rowViewId;
	}

	/**
	 * Update the current list
	 */
	protected void setList(ArrayList<T> newList) {
		final int savedRow = getListView().getFirstVisiblePosition();
		View v = getListView().getChildAt(0);
		final int savedTop = v == null ? 0 : v.getTop();

		mList = newList;
		// Set up list handling
        this.mAdapter = new ListAdapter(this, mRowViewId, mList);
        setListAdapter(this.mAdapter);

        getListView().post(new Runnable() {
			@Override
			public void run() {
				getListView().setSelectionFromTop(savedRow, savedTop);
			}});
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
			if (savedInstanceState != null && mKey != null && savedInstanceState.containsKey(mKey)) {
				mList = (ArrayList<T>) savedInstanceState.getSerializable(mKey);//.getParcelableArrayList(mKey);
			}

			if (mList == null) {
				/* Get any information from the extras bundle */
				Bundle extras = getIntent().getExtras();
				if (extras != null && mKey != null) {
					mList = (ArrayList<T>) extras.getSerializable(mKey); // .getParcelableArrayList(mKey);
				}
				if (mList == null)
					mList = getList();

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
			Logger.logError(e);
		}
	}

	/**
	 * Handle drop events; also preserves current position.
	 */
	private TouchListView.DropListener mDropListener=new TouchListView.DropListener() {
		@Override
		public void drop(int from, final int to) {
            final ListView lv = getListView();
			// Check if nothing to do; also avoids the nasty case where list size == 1
			if (from == to)
				return;

            final int firstPos = lv.getFirstVisiblePosition();

			T item=mAdapter.getItem(from);				
			mAdapter.remove(item);
			mAdapter.insert(item, to);
            onListChanged();

            int first2 = lv.getFirstVisiblePosition();
            System.out.println(from + " -> " + to + ", first " + firstPos + "(" + first2 + ")");
            final int newFirst = (to > from && from < firstPos) ? (firstPos - 1) : firstPos;

            View firstView = lv.getChildAt(0);
            final int offset = firstView.getTop();
            lv.post(new Runnable() {
				@Override
				public void run() {
					System.out.println("Positioning to " + newFirst + "+{" + offset + "}");
					lv.requestFocusFromTouch();
					lv.setSelectionFromTop(newFirst, offset);
					lv.post(new Runnable() {
						@Override
						public void run() {
							for(int i = 0; ; i++) {
								View c = lv.getChildAt(i);
								if (c == null)
									break;
								if (lv.getPositionForView(c) == to) {
									lv.setSelectionFromTop(to, c.getTop());
									//c.requestFocusFromTouch();
									break;
								}
							}
						}});
				}});

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
		// If view is not present, just exit
		if (v == null)
			return;
		try {
			if (s != null && s.length() > 0) {
				((TextView)v).setText(s);
				return;			
			}
		} catch (Exception e) {
			Logger.logError(e);
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
			i.putExtra(mKey, mList);
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
			onListChanged();
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
		Object o = ViewTagger.getTag(pv, R.id.TAG_POSITION);
		if (o == null)
			throw new RuntimeException("A view with the tag R.id.row was found, but it is not the view for the row");
		return (Integer) o;
	}

	/**
	 * Handle deletion of a row
	 */
	private OnClickListener mRowDeleteListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (v == null)
				return;

			int pos = getViewRow(v);
            mList.remove(pos);
            mAdapter.notifyDataSetChanged();
            onListChanged();
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
            onListChanged();
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
            onListChanged();
		}
		
	};

	/**
	 * Handle moving a row DOWN
	 */
	private OnClickListener mRowClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			int pos = getViewRow(v);
			onRowClick(v, pos, mList.get(pos));
		}
		
	};

	/**
	 * Adapter to manage the rows.
	 * 
	 * @author Philip Warner
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
            ViewTagger.setTag(v, R.id.TAG_POSITION, Integer.valueOf(position));

            {
            	// Giving the whole row ad onClickListener seems to interfere
            	// with drag/drop.
            	View details = v.findViewById(R.id.row_details);
            	if (details != null) {
                    details.setOnClickListener(mRowClickListener);
                    details.setFocusable(false);
            	}
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
            		Logger.logError(e);
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
    	outState.putSerializable(mKey, mList);
    }

	/**
	 * This is totally bizarre. Without this piece of code, under Android 1.6, the
	 * native onRestoreInstanceState() fails to restore custom classes, throwing
	 * a ClassNotFoundException, when the activity is resumed.
	 * 
	 * To test this, remove this line, edit a custom style, and save it. App will
	 * crash in AVD under Android 1.6.
	 * 
	 * It is not entirely clear how this happens but since the Bundle has a classLoader
	 * it is fair to surmise that the code that creates the bundle determines the class
	 * loader to use based (somehow) on the class being called, and if we don't implement
	 * this method, then in Android 1.6, the class is a basic android class NOT and app 
	 * class.
	 */
	@Override
	public void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mDbHelper != null)
			mDbHelper.close();
	}

}
