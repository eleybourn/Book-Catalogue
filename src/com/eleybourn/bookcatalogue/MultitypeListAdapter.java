/*
 * @copyright 2012 Philip Warner
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

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.widgets.FastScroller.SectionIndexerV2;

/**
 * Cursor adapter for flattened multi-typed ListViews. Simplifies the implementation of such lists.
 * 
 * Users of this class need to implement MultitypeListHandler to manage the creation and display of 
 * each view.
 * 
 * @author Philip Warner
 */
public class MultitypeListAdapter extends CursorAdapter implements SectionIndexerV2 {

	LayoutInflater mInflater;
	MultitypeListHandler mHandler;

	public MultitypeListAdapter(Context context, Cursor c, MultitypeListHandler handler) {
		super(context, c);
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mHandler = handler;
	}

	/**
	 * NOT USED. Should never be called. Die if it is.
	 */
	@Override
    public void bindView(View view, Context context, Cursor cursor) {
		throw new RuntimeException("EventsCursorAdapter.bindView is unsupported");
	}
	/**
	 * NOT USED. Should never be called. Die if it is.
	 */
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		throw new RuntimeException("EventsCursorAdapter.newView is unsupported");
	}

	@Override 
	public int getItemViewType(final int position) {
		final Cursor cursor = this.getCursor();
		//
		// At least on Android 2.3.4 we see attempts to get item types for cached items beyond the
		// end of empty cursors. This implies a cleanup ordering issue, but has not been confirmed.
		// This code attempts to gather more details of how this error occurs.
		//
		// NOTE: It DOES NOT fix the error; just gathers more debug info
		//
		if (cursor.isClosed()) {
			throw new RuntimeException("Attempt to get type of item on closed cursor (" + cursor.toString() + ")");
		} else if (position >= cursor.getCount()) {
			throw new RuntimeException("Attempt to get type of item beyond end of cursor (" + cursor.toString() + ")");
		} else {
			cursor.moveToPosition(position);
			return mHandler.getItemViewType(cursor);			
		}
	}
	@Override
	public int getViewTypeCount() {
		return mHandler.getViewTypeCount();
	}

	@Override
    public View getView(final int position, final View convertView, final ViewGroup parent)
    {
		Cursor cursor = this.getCursor();
		cursor.moveToPosition(position);

		return mHandler.getView(cursor, mInflater, convertView, parent);
    }

	@Override
	public String[] getSectionTextForPosition(final int position) {
		final Cursor c = getCursor();
		if (position < 0 || position >= c.getCount())
			return null;

		final int savedPos = c.getPosition();
		c.moveToPosition(position);
		final String[] section = mHandler.getSectionText(c);
		c.moveToPosition(savedPos);
		return section;
	}
}
