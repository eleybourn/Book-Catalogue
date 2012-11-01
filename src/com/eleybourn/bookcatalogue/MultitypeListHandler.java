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

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Interface for handling the View-related tasks in a multi-type ListView.
 * 
 * @author Philip Warner
 */
public interface MultitypeListHandler {

	/**
	 * Abstract base class for 'holder' objects in a multi-type list view.
	 * 
	 * @author Philip Warner
	 *
	 * @param <T>	Row context passed to each method. Typically a RowView. Could be a cursor
	 * 				or any other object capable of representing the data in the current row.
	 */
	public static abstract class MultitypeHolder<T> {		
		/**
		 * Setup a new holder for row type based on the passed rowContext. This holder will be
		 * associated with a reusable view that will always be used for rows of the current
		 * kind. We avoid having to call findViewById() by doing it once at creation time.
		 */
		public abstract void map(T rowContext, View v);

		/**
		 * Use the passed rowContext to fill in the actual details for the current row.
		 * 
		 * @param rowContext
		 * @param v
		 * @param level
		 */
		public abstract void set(T rowContext, View v, int level);

		/**
		 * Use  the passed rowContext to determine the kind of View that is required and return a new
		 * view.
		 * 
		 * @param rowContext
		 * @param inflater
		 * @param parent
		 * @param level
		 * @return
		 */
		public abstract View newView(T rowContext, LayoutInflater inflater, ViewGroup parent, int level);
	}

	/**
	 * Return the view type that will be used for any row of the type represented by
	 * the current cursor position.
	 * 
	 * @param cursor	Cursor position at representative row.
	 * 
	 * @return		view type
	 */
	public int getItemViewType(Cursor cursor);

	/**
	 * Get the total number of view types that can be returned.
	 * 
	 * @return
	 */
	public int getViewTypeCount();
	
	/**
	 * Create a new view and fill it in with details pointed to by the current cursor. The 
	 * convertView parameter (if not null) points to a reusable view of the right type.
	 * 
	 * @param cursor		Cursor, positioned at current row
	 * @param inflater		Inflater to use in case a new view resource must be expanded
	 * @param convertView	Pointer to reusable bew of correct type (may be null)
	 * @param parent		Parent view group
	 * 	
	 * @return				Filled-in view to use.
	 */
	public View getView(Cursor cursor, LayoutInflater inflater, View convertView, ViewGroup parent);
	
	/**
	 * Get the text to display in FastScroller for row at current cursor position
	 *
	 * @param cursor	Cursor, correctly positioned.
	 * 
	 * @return		text to display
	 */
	public String[] getSectionText(Cursor cursor);
}
