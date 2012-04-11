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

package com.eleybourn.bookcatalogue.properties;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;

import android.view.LayoutInflater;
import android.view.View;

/**
 * Base class for generic properties.
 * 
 * @author Philip Warner
 */
public abstract class Property {
	/**
	 * Counter used to generate unique View IDs. Needed to prevent some fields being overwritten when
	 * screen is rotated (if thay all have the same ID).
	 * 
	 * ENHANCE: allow topological sort of parameters to allow arbitrary grouping and sorting.
	 * 
	 * NOTE ABOUT SERIALIZATION
	 * 
	 * It is very tempting to make these serializable, but fraught with danger. Specifically, these 
	 * objects contain resource IDs and, as far as I can tell, resource IDs can change across versions.
	 * This means that any serialized version would only be useful for in-process data passing. But this
	 * can be accomplished by custom serialization in the referencing object much more easily.
	 */
	private static Integer mViewIdCounter = 0;

	/**
	 * Unique 'name' of this property.
	 */
	protected final String mUniqueId;
	/** PropertyGroup in which this property should reside. Display-purposes only */
	protected transient PropertyGroup mGroup;
	/** Resource ID for name of this property */
	protected transient int mNameResourceId;
	private transient String mName = null;
	/** Property weight (for sorting). Most will remain set at 0. */
	private int mWeight = 0;
	/** Hint associated with this property. Subclasses need t ouse, where appropriate */
	private int mHint = 0;

	/**
	 * Exception used by validation code.
	 * 
	 * @author Philip Warner
	 */
	public static class ValidationException extends RuntimeException {
		private static final long serialVersionUID = -1086124703257379812L;
		public ValidationException(String message) {
			super(message);
		}
	}
	/**
	 * Increment and return the view counter
	 * 
	 * @return
	 */
	public static int nextViewId() {
		return ++mViewIdCounter;
	}

	/**
	 * Get the string name of this property
	 * 
	 * @return
	 */
	public String getName() {
		if (mName == null)
			mName = BookCatalogueApp.getResourceString(mNameResourceId);
		return mName;
	}

	/**
	 * Accessor
	 * 
	 * @param weight
	 */
	public void setWeight(int weight) {
		mWeight = weight;
	}
	/**
	 * Accessor
	 */
	public int getWeight() {
		return mWeight;
	}

	/**
	 * Interface used to help setting one property based on another property value.
	 * eg. there are multiple 'Boolean' properties, and *maybe* one day there will be
	 * a use for type conversions.
	 * 
	 * @author Philip Warner
	 */
	public interface BooleanValue {
		public Boolean get();
	}
	/**
	 * Interface used to help setting one property based on another property value.
	 * eg. there are multiple 'Boolean' properties, and *maybe* one day there will be
	 * a use for type conversions.
	 * 
	 * @author Philip Warner
	 */
	public interface StringValue {
		public String get();
	}
	/**
	 * Interface used to help setting one property based on another property value.
	 * eg. there are multiple 'Boolean' properties, and *maybe* one day there will be
	 * a use for type conversions.
	 * 
	 * @author Philip Warner
	 */
	public interface IntegerValue {
		public Integer get();
	}

	/** 
	 * Constructor
	 * 
	 * @param uniqueId			Unique name for this property (ideally, unique for entire app)
	 * @param group				PropertyGroup in which this property belongs
	 * @param nameResourceId	Resource ID for name of this property
	 */
	public Property(String uniqueId, PropertyGroup group, int nameResourceId) {
		mUniqueId = uniqueId;
		mGroup = group;
		mNameResourceId = nameResourceId;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public String getUniqueName() {
		return mUniqueId;
	}
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public PropertyGroup getGroup() {
		return mGroup;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	int getNameResourceId() {
		return mNameResourceId;
	}
	/**
	 * Accessor
	 */
	void setNameResourceId(int id) {
		mNameResourceId = id;
	}

	/**
	 * Accessor
	 */
	public boolean hasHint() {
		return mHint != 0;
	}
	/**
	 * Accessor
	 */
	public int getHint() {
		return mHint;
	}
	/**
	 * Accessor
	 */
	public void setHint(int hint) {
		mHint = hint;
	}

	/**
	 * Default validation method. Override to provide validation.
	 */
	public void validate() {
	}

	/** Children must implement set(Property) */
	public abstract void set(Property p);
	/** Children must method to return an editor for this object */
	public abstract View getView(LayoutInflater inflater);
}
