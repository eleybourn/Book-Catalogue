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

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Implement a generic list-of-values property.
 * 
 * @author Philip Warner
 *
 * @param <T>		Base type of list items
 */
public abstract class ListProperty<T extends Object> extends ValuePropertyWithGlobalDefault<T> {
	/** List of valid values */
	protected ItemEntries<T> mList = null;

	/** Accessor */
	public ItemEntries<T> getListItems() { return mList; }

    /** Accessor */
	public void setListItems(ItemEntries<T> list) { mList= list; }

    public ListProperty(ItemEntries<T> list, String uniqueId, PropertyGroup group, int nameResourceId, T value, String defaultPref, T defaultValue) {
		super(uniqueId, group, nameResourceId, value, defaultPref, defaultValue);
		mList = list;
	}
	public ListProperty(ItemEntries<T> list, String uniqueId, PropertyGroup group, int nameResourceId, T value, T defaultValue) {
		super(uniqueId, group, nameResourceId, value, null, defaultValue);
		mList = list;
	}

	/**
	 * Return the default list editor view with associated event handlers.
	 */
	@Override
	public View getView(final LayoutInflater inflater) {
		View v = inflater.inflate(R.layout.property_value_list, null);
		ViewTagger.setTag(v, R.id.TAG_PROPERTY, this);
		// Display the list of values when clicked.
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				handleClick(v, inflater);
			}
		});

		// Set the name
		TextView text = v.findViewById(R.id.field_name);
		text.setText(getName());
		
		// Try to find the list item that corresponds to the current stored value.
		ItemEntry<T> val = null;
		for(ItemEntry<T> e: getListItems() ) {
			if (e.value == null) {
				if (get() == null) {
					val = e;
				}
			} else {
				if (get() != null && get().equals(e.value)) {
					val = e;					
				}
			}
		}
		// Display current value
		setValueInView(v, val);

		return v;
	}

	private void handleClick(final View base, final LayoutInflater inflater) {
		final ItemEntries<T> items = getListItems();
		if (this.hasHint()) {
			HintManager.displayHint(base.getContext(), this.getHint(), null, new Runnable(){
				@Override
				public void run() {
					displayList(base, inflater, items);
				}});
		} else {
			displayList(base, inflater, items);
		}
	}

	/**
	 * Class to represent all items in a list-of-values property
	 * 
	 * @author Philip Warner
	 *
	 * @param <T>		Type of underlying list item
	 */
	public static class ItemEntry<T extends Object> {
		/** Actual value */
		T value;
		/** Test description of the meaning of that value */
		int textId;
        Object[] textArgs;

		/** Constructor. Instantiates string. */
		public ItemEntry(T value, int resourceId, Object... args) {
			this.value = value;
			this.textId = resourceId; //BookCatalogueApp.getResourceString(resourceId);
            this.textArgs = args;
		}
//		/** Constructor */
//		public ItemEntry(T value, String text) {
//			this.value = value;
//			this.text = text;
//		}

        /** Accessor */
        public String getString() {
            return BookCatalogueApp.getResourceString(textId, textArgs);
        }

        /** Accessor */
        public T getValue() {
            return value;
        }

        /** Accessor */
        public void setString(int value, Object... args) {
            textId = value;
            textArgs = args;
        }

        @Override
		public String toString() {
			return getString();
		}
	}

	/**
	 * Class to represent a collection of list entries for a list-of-values property
	 * 
	 * @author Philip Warner
	 *
	 * @param <T>		Underlying list item data type.
	 */
	public static class ItemEntries<T> implements Iterable<ItemEntry<T>> {
		ArrayList<ItemEntry<T>> mList = new ArrayList<ItemEntry<T>>();

		/**
		 * Utility to make adding items easier.
		 * 
		 * @param value		Underlying value
		 * @param stringId	String ID of description
		 * @return
		 */
		public ItemEntries<T> add(T value, int stringId, Object... args) {
			mList.add(new ItemEntry<T>(value, stringId, args));
			return this;
		}
//		/**
//		 * Utility to make adding items easier.
//		 *
//		 * @param value		Underlying value
//		 * @param stringId	Description
//		 * @return
//		 */
//		public ItemEntries<T> add(T value, int string) {
//			mList.add(new ItemEntry<T>(value, string));
//			return this;
//		}
		/** Iterator access */
		@Override
		public Iterator<ItemEntry<T>> iterator() {
			return mList.iterator();
		}
	}
	
	/**
	 * Holder class for list items 
	 * 
	 * @author Philip Warner
	 *
	 * @param <T>
	 */
	private static class Holder<T extends Object> {
		ItemEntry<T> item;
		View baseView;
		public Holder(ItemEntry<T> item, View baseView) {
			this.item = item;
			this.baseView = baseView;
		}
	}

	/**
	 * Set the 'value' field in the passed view to match the passed item.
	 * 
	 * @param baseView
	 * @param item
	 */
	private void setValueInView(View baseView, ItemEntry<T> item) {
		TextView text = baseView.findViewById(R.id.value);

		if (item == null) {
			text.setText("");		
		} else {
			if (isDefault(item.value))
				text.setTypeface(null, Typeface.NORMAL);
			else
				text.setTypeface(null, Typeface.BOLD);

			text.setText(item.getString());					
		}
	}

	/**
	 * Called to display a list of values for this property.
	 * 
	 * @param base		Specific view that was clicked
	 * @param inflater	LayoutInflater
	 * @param items		All list items
	 * @return
	 */
	private boolean displayList(final View base, final LayoutInflater inflater, ItemEntries<T> items) {

		// Get the view and the radio group
		View root = inflater.inflate(R.layout.property_value_list_list, null);
		RadioGroup grp = root.findViewById(R.id.values);

		// Get the current value
		T curr = get();

		final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext()).setView(root).create();

		// Create a listener that responds to any click on the list
		OnClickListener l = new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				Holder<T> h = ViewTagger.getTag(v, R.id.TAG_HOLDER);
				set(h.item.value);
				setValueInView(h.baseView, h.item);
			}
		};

		// Add each entry to the list
		for(ItemEntry<T> e: items) {
			// If we are looking at global-only values, NULL is invalid
			if (e.value != null || !isGlobal()) {
				// Check if this value is the currently selected value
				boolean selected = false;
				if (e.value == null && curr == null)
					selected = true;
				else if (curr != null && curr.equals(e.value))
					selected = true;
				// Make the view for this item
				View v = inflater.inflate(R.layout.property_value_list_item, null);
				TextView name = v.findViewById(R.id.field_name);
				RadioButton sel = v.findViewById(R.id.selector);
				//Set the various values
				sel.setChecked(selected);
				name.setText(e.getString());
				// Listen for clicks
				sel.setOnClickListener(l);
				v.setOnClickListener(l);
				// Set the tacks used by the listeners
				ViewTagger.setTag(v, R.id.TAG_HOLDER, new Holder<T>(e, base));
				ViewTagger.setTag(sel, R.id.TAG_HOLDER, new Holder<T>(e, base));
				// Add it to the group
				grp.addView(v);		
			}
		}

		// Display dialog
		dialog.show();
		return true;
	}
}

