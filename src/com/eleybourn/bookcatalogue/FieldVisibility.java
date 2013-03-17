/*
 * @copyright 2010 Evan Leybourn
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

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * 
 * This is the Field Visibility page. It contains a list of all fields and a 
 * checkbox to enable or disable the field on the main edit book screen.
 * 
 * @author Evan Leybourn
 */
public class FieldVisibility extends BookCatalogueActivity {
	public final static String prefix = "field_visibility_";
	
	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.field_visibility);
			setupFields();
			Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);		
	}

	/**
	 * This function builds the manage field visibility by adding onClick events
	 * to each field checkbox
	 */
	public void setupFields() {
		// The fields to show/hide
		String[] fields = {CatalogueDBAdapter.KEY_AUTHOR_ID, CatalogueDBAdapter.KEY_TITLE, "thumbnail", 
				CatalogueDBAdapter.KEY_ISBN, CatalogueDBAdapter.KEY_SERIES_NAME, CatalogueDBAdapter.KEY_SERIES_NUM, 
				CatalogueDBAdapter.KEY_PUBLISHER, CatalogueDBAdapter.KEY_DATE_PUBLISHED, CatalogueDBAdapter.KEY_BOOKSHELF, 
				CatalogueDBAdapter.KEY_PAGES, CatalogueDBAdapter.KEY_LIST_PRICE, CatalogueDBAdapter.KEY_READ, 
				CatalogueDBAdapter.KEY_RATING, CatalogueDBAdapter.KEY_NOTES, CatalogueDBAdapter.KEY_ANTHOLOGY_MASK, 
				CatalogueDBAdapter.KEY_LOCATION, CatalogueDBAdapter.KEY_READ_START, CatalogueDBAdapter.KEY_READ_END, 
				CatalogueDBAdapter.KEY_FORMAT, CatalogueDBAdapter.KEY_SIGNED, CatalogueDBAdapter.KEY_DESCRIPTION, 
				CatalogueDBAdapter.KEY_GENRE, DatabaseDefinitions.DOM_LANGUAGE.name};
		int[] fieldRs = {R.string.author, R.string.title, R.string.thumbnail, R.string.isbn, R.string.series, R.string.series_num, 
				R.string.publisher, R.string.date_published, R.string.bookshelf, R.string.pages, R.string.list_price,
				R.string.read, R.string.rating, R.string.notes, R.string.anthology, R.string.location_of_book, 
				R.string.read_start, R.string.read_end, R.string.format, R.string.signed, R.string.description, 
				R.string.genre, R.string.language};
		boolean[] compulsory = {true, true, false, false, false, false, false, false, 
				true, false, false, false, false, false, false, false, false, false, false, false, false, false, false};
		
		SharedPreferences mPrefs = getSharedPreferences("bookCatalogue", MODE_PRIVATE);
		//SharedPreferences.Editor ed = mPrefs.edit();
		//ed.putString(STATE_BOOKSHELF, bookshelf);
		//ed.commit();
		
		// Display the list of fields
		LinearLayout parent = (LinearLayout) findViewById(R.id.manage_fields_scrollview);
		for (int i = 0; i<fields.length; i++) {
			final String prefs_name = prefix + fields[i];
			//Create the LinearLayout to hold each row
			LinearLayout ll = new LinearLayout(this);
			ll.setPadding(5, 0, 0, 0);

			OnClickListener listener = new OnClickListener() {
				@Override
				public void onClick(View v) {
					SharedPreferences mPrefs = getSharedPreferences("bookCatalogue", MODE_PRIVATE);
					SharedPreferences.Editor ed = mPrefs.edit();
					boolean field_visibility = mPrefs.getBoolean(prefs_name, true);
					ed.putBoolean(prefs_name, !field_visibility);
					ed.commit();
					return;
				}};

			//Create the checkbox
			boolean field_visibility = mPrefs.getBoolean(prefs_name, true);
			CheckBox cb = new CheckBox(this);
			cb.setChecked(field_visibility);
			if (compulsory[i] == true) {
				cb.setEnabled(false);
			} else {
				cb.setOnClickListener(listener);
			}
			ll.addView(cb);
			
			//Create the checkBox label (or textView)
			//TextView tv = new TextView(this);
			cb.setTextAppearance(this, android.R.attr.textAppearanceLarge);
			cb.setText(fieldRs[i]);
			//cb.setPadding(0, 5, 0, 0);
			if (compulsory[i] == true) {
				cb.setTextColor(Color.GRAY);
			//} else {
				//cb.setOnClickListener(listener);
			}
			//ll.addView(tv);
			
			//Add the LinearLayout to the parent
			parent.addView(ll);
			
		}
	}

	public static boolean isVisible(String fieldName) {
		return BookCatalogueApp.getAppPreferences().getBoolean(prefix + fieldName, true);
	}
}