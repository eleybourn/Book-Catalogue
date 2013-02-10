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
package com.eleybourn.bookcatalogue.widgets;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * ArrayAdapter to manage rows of an arbitrary type with row movement via clicking on predefined 
 * sub-views, if present.
 * 
 * @author Philip Warner
 */
public abstract class SimpleListAdapter<T> extends ArrayAdapter<T> {
	private int mRowViewId;
	private boolean mCheckedFields = false;
	private boolean mHasPosition = false;
	private boolean mHasUp = false;
	private boolean mHasDown = false;
	private boolean mHasDelete = false;

	private ArrayList<T> mItems;
	
	public SimpleListAdapter(Context context, int rowViewId, ArrayList<T> items) {
            super(context, rowViewId, items);
            mRowViewId = rowViewId;
            mItems = items;
    }

	protected void onListChanged() {};
	protected void onRowClick(T object, int position, View v) {};
	protected void onRowDelete(T object, int position, View v) {};
	protected void onRowDown(T object, int position, View v) {};
	protected void onRowUp(T object, int position, View v) {};

	/**
	 * Call to set up the row view.
	 * 
	 * @param target	The target row view object
	 * @param object	The object (or type T) from which to draw values.
	 */
	abstract protected void onSetupView(T object, int position, View target);

	private OnClickListener mRowClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			try {
				int pos = getViewRow(v);
				T item = getItem(pos);
				onRowClick(item, pos, v);
			} catch (Exception e) {
				Logger.logError(e);
			}
		}			
	};

	private OnClickListener mRowDeleteListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (v == null)
				return;

			int pos = getViewRow(v);
			T old = getItem(pos);
			try {
	            onRowDelete(old, pos, v);					
				remove(old);
	            notifyDataSetChanged();
	            onListChanged();
			} catch (Exception e) {
				// TODO: Allow a specific exception to cancel the action
				Logger.logError(e);
			}
		}			
	};

	private OnClickListener mRowDownListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			int pos = getViewRow(v);
            if (pos == (getCount()-1) )
            	return;
            T old = getItem(pos);
            try {
				onRowDown(old, pos, v);

	            mItems.set(pos, getItem(pos+1));
	            mItems.set(pos+1, old);
	            notifyDataSetChanged();
	            onListChanged();
            } catch (Exception e) {
				// TODO: Allow a specific exception to cancel the action
				Logger.logError(e);
            }
		}			
	};

	private OnClickListener mRowUpListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			int pos = getViewRow(v);
            if (pos == 0)
            	return;
            T old = getItem(pos-1);

            try {
				onRowUp(old, pos, v);

	            mItems.set(pos-1, getItem(pos));
	            mItems.set(pos, old);
	            notifyDataSetChanged();
	            onListChanged();
            } catch (Exception e) {
				// TODO: Allow a specific exception to cancel the action
				Logger.logError(e);
            }
            
		}			
	};

	@Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	// Get the view; if not defined, load it.
        View v = convertView;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(mRowViewId, null);
        }
        
        // Save this views position
        ViewTagger.setTag(v, R.id.TAG_POSITION, Integer.valueOf(position));

        {
        	// Giving the whole row an onClickListener seems to interfere
        	// with drag/drop.
        	View details = v.findViewById(R.id.row_details);
        	if (details == null) {
        		details = v.findViewById(R.id.row);
        	}
        	if (details != null) {
                details.setOnClickListener(mRowClickListener);
                details.setFocusable(false);
        	}
        }

        // Get the object, if not null, do some processing
        T o = this.getItem(position);
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
                onSetupView(o, position, v);            		
        	} catch (Exception e) {
        		Logger.logError(e);
        	}
    		v.setBackgroundResource(android.R.drawable.list_selector_background);

            mCheckedFields = true;
        }
        return v;
    }
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

}