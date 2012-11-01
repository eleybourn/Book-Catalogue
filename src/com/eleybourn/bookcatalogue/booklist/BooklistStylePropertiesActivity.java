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

package com.eleybourn.bookcatalogue.booklist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property.ValidationException;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.StringProperty;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Edit the properties associated with a passed style
 * 
 * @author Philip Warner
 */
public class BooklistStylePropertiesActivity extends Activity {
	/** Parameter used to pass data to this activity */
	public static final String KEY_STYLE = "BooklistStyleProperties.Style";
	/** Parameter used to pass data to this activity */
	public static final String KEY_SAVE_TO_DATABASE = "BooklistStyleProperties.SaveToDb";

	/** Database connection, if used */
	private CatalogueDBAdapter mDb = null;

	/** Flag indicating style should be saved to the database on exit */
	private boolean mSaveToDb = true;
	/** Style we are editing */
	private BooklistStyle mStyle;
	/** Properties object constructed from current style */
	private Properties mProperties;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set the view and handle the save/cancel buttons.
		this.setContentView(R.layout.booklist_style_properties);

		Button save = (Button) findViewById(R.id.confirm);
		save.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				handleSave();
			}});
		Button cancel = (Button) findViewById(R.id.cancel);
		cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}});

		// Get the intent and get the style and other details
		Intent i = this.getIntent();
		mStyle = (BooklistStyle) i.getSerializableExtra(KEY_STYLE);

		if (i.hasExtra(KEY_SAVE_TO_DATABASE))
			mSaveToDb = i.getBooleanExtra(KEY_SAVE_TO_DATABASE, true);

		// Display all the style properties
		displayProperties();

		// Make the title
		String title;
		if (mStyle.getDisplayName().equals(""))
			title = getString(R.string.new_style);
		else if (mStyle.getRowId() == 0)
			title = getString(R.string.clone_style_colon_name, mStyle.getDisplayName());
		else
			title = getString(R.string.edit_style_colon_name, mStyle.getDisplayName());

		this.setTitle(title);

		// Display hint if required
		if (savedInstanceState == null)
			HintManager.displayHint(this, R.string.hint_booklist_style_properties, null);

		Utils.initBackground(R.drawable.bc_background_gradient_dim, this);
	}

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this);		
	}

	/**
	 * Setup the style properties views based on the current style
	 */
	private void displayProperties() {
		ViewGroup vg = (ViewGroup) this.findViewById(R.id.body);
		vg.removeAllViews();

		mProperties = mStyle.getProperties();
		mProperties.add(new GroupsProperty());
		mProperties.buildView(this.getLayoutInflater(), vg);		
	}

	/**
	 * Implementation of a 'Property' that has a button which will start the activity
	 * for editing style groups.
	 * 
	 * @author Philip Warner
	 */
	private class GroupsProperty extends StringProperty {

		/**
		 * Constructor
		 */
		public GroupsProperty() {
			super("StyleGroups", PropertyGroup.GRP_GENERAL, R.string.groupings);
		}

		/**
		 * Get the property 'value': just a list of the groups.
		 */
		@Override
		public String get() {
			return mStyle.getGroupListDisplayNames();
		}
		/**
		 * Can not be 'set'. Will be edited vi the button->activity.
		 */
		@Override
		public GroupsProperty set(String value) {
			throw new RuntimeException("Attempt to set read-only property string");
		}

		/**
		 * Setup the view
		 */
		@Override
		public View getView(LayoutInflater inflater) {
			View v = inflater.inflate(R.layout.property_value_string_button, null);
			ViewTagger.setTag(v, R.id.TAG_PROPERTY, this);
			final TextView name = (TextView)v.findViewById(R.id.name);
			final TextView value = (TextView)v.findViewById(R.id.value);
			final Button btn = (Button)v.findViewById(R.id.edit_button);
			name.setText(getName());
			value.setHint(getName());
			value.setText(get());

			btn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startGroupsActivity();
				}
			});
			return v;
		}
	}

	/**
	 * Start editing the groups.
	 */
	public void startGroupsActivity() {
		Intent i = new Intent(this, BooklistStyleGroupsActivity.class);
		i.putExtra(BooklistStyleGroupsActivity.KEY_STYLE, mStyle);
		i.putExtra(BooklistStyleGroupsActivity.KEY_SAVE_TO_DATABASE, false);
		startActivityForResult(i, UniqueId.ACTIVITY_BOOKLIST_STYLE_GROUPS);		
	}

	/**
	 * Called when 'save' button is clicked.
	 */
	private void handleSave() {
		boolean ok = true;
		try {
			mProperties.validate();
		} catch (ValidationException e) {
			ok = false;
			Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
		}
		if (ok) {
			if (mSaveToDb)
				mStyle.saveToDb(getDb());
			Intent i = new Intent();
			i.putExtra(KEY_STYLE, mStyle);
			setResult(RESULT_OK, i);
			finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case UniqueId.ACTIVITY_BOOKLIST_STYLE_GROUPS:
			// When groups have been edited, copy them to this style.
			if (intent != null && intent.hasExtra(BooklistStyleGroupsActivity.KEY_STYLE)) {
				BooklistStyle editedStyle = null;
				try {
					editedStyle = (BooklistStyle) intent.getSerializableExtra(BooklistStyleGroupsActivity.KEY_STYLE);
				} catch (Exception e) {
					Logger.logError(e);
				}
				if (editedStyle != null) {
					mStyle.setGroups(editedStyle);
					displayProperties();
				}				
			}
			break;
		}
	}

	/**
	 * Get/create database as required.
	 * 
	 * @return
	 */
	private CatalogueDBAdapter getDb() {
		if (mDb == null)
			mDb = new CatalogueDBAdapter(this);
		mDb.open();
		return mDb;
	}

	/**
	 * Cleanup.
	 */
	@Override 
	protected void onDestroy() {
		super.onDestroy();
		if (mDb != null)
			mDb.close();
	}
}
