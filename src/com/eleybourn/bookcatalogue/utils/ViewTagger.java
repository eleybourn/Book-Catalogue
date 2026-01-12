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

package com.eleybourn.bookcatalogue.utils;

import android.util.SparseArray;
import android.view.View;

/**
 * Using View.setTag(int, Object) causes a memory leak if the tag refers, by a strong reference chain,
 * to the view itself (ie. it uses the 'Holder' pattern). This bug is documented here:
 * 		<a href="http://code.google.com/p/android/issues/detail?id=18273">...</a>
 * It seems that an 'interesting' design choice was made to use the view itself as a weak key to the into
 * another collection, which then causes the views to never be GC'd.
 * The work-around is to *not* use strong refs, or use setTag(Object). But we use multiple tags.
 * So this class implements setTag(int, Object) in a non-leaky fashion and is designed to be stored
 * in the tag of a view.
 *
 * @author Philip Warner
 */
public class ViewTagger {
	/** Stores the basic tag referred to without an ID */
	public Object mBareTag = null;
	public SparseArray<Object> mTags = null;

	/**
	 * Internal static method to get (and optionally create) a ViewTagger object
	 * on tha passed view.
	 * 
	 * @param v				View with tag
	 * @param autoCreate	Indicates if tagger should be created if not present
	 * 
	 * @return				ViewTagger object
	 */
	private static ViewTagger getTagger(View v, boolean autoCreate) {
		// See if we have one already
		Object o = v.getTag();
		ViewTagger tagger = null;
		if (o == null) {
			// Create if requested
			if (autoCreate) {
				tagger = new ViewTagger();
				v.setTag(tagger);
			}
		} else {
			// Make sure it's a valid object type
			if ( ! (o instanceof ViewTagger) ) 
				throw new RuntimeException("View already has a tag that is not a ViewTagger");
			tagger = (ViewTagger) o;
		}
		return tagger;
	}

	/**
	 * Static method to get the bare tag from the view.
	 * 
	 * @param v		View from which to retrieve tag
	 */
	public static Object getTag(View v) {
		ViewTagger tagger = getTagger(v, false);
		if (tagger == null)
			return null;

		return tagger.get();
	}
	/**
	 * Static method to get the tag matching the ID from the view
	 * 
	 * @param v		View from which to retrieve tag
	 * @param key	Key of required tag
	 * 
	 * @return		Object with specified tag
	 */
	@SuppressWarnings("unchecked")
	public static<T> T getTag(View v, int key) {
		ViewTagger tagger = getTagger(v, false);
		if (tagger == null)
			return null;

		return (T)tagger.get(key);
	}

	/**
	 * Static method to set the bare tag on the view
	 *
	 * @param v		View from which to retrieve tag
	 * @param value	Object to store at specified tag
	 */
	public static void setTag(View v, Object value) {
		getTagger(v, true).set(value);
	}
	/**
	 * Static method to set the tag matching the ID on the view
	 * 
	 * @param v			View from which to retrieve tag
	 * @param key		Key of tag to store
	 * @param value		Object to store at specified tag
	 */
	public static void setTag(View v, int key, Object value) {
		getTagger(v, true).set(key, value);
	}

	/**
	 * Instance method to set the bare tag
	 * 
	 * @param value		Value of id-less tag
	 */
	public void set(Object value) {
		mBareTag = value;
	}
	/**
	 * Instance method to set the specified tag value
	 * 
	 * @param key		Key of new tag
	 * @param value		Object to store at specified tag
	 */
	public void set(int key, Object value) {
		synchronized(this) {
			if (mTags == null)
				mTags = new SparseArray<>();
			mTags.put(key, value);
		}
	}
	
	/**
	 * Instance method to get the bare tag
	 * 
	 * @return		The bare tag object
	 */
	public Object get() {
		return mBareTag;
	}
	
	/**
	 * Instance method to get the specified tag
	 * 
	 * @param key	Key of object to retrieve
	 * 
	 * @return		Object at specified key
	 */
	public Object get(int key) {
		synchronized(this) {
			if (mTags == null)
				return null;
			return mTags.get(key);
		}
	}
}
