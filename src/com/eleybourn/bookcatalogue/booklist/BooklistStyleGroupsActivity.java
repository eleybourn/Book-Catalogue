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

import java.io.Serializable;
import java.util.ArrayList;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.BookEditObjectList;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyleGroupsActivity.GroupWrapper;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Activity to edit the groups associated with a style (include/exclude and/or move up/down)
 * 
 * @author Philip Warner
 */
public class BooklistStyleGroupsActivity extends BookEditObjectList<GroupWrapper> {
	// Preferences setup
	public static final String KEY_STYLE = "StyleEditor.Style";
	public static final String KEY_SAVE_TO_DATABASE = "StyleEditor.SaveToDb";
	private static final String KEY_GROUPS = "StyleEditor.Groups";

    /** Copy of the style we are editing */
	private LibraryStyle mStyle;
	/** Copy of flag passed by calling activity to indicate changes made here should be saved on exit */
	private boolean mSaveToDb = true;

	/** Database connection, if used */
	private CatalogueDBAdapter mDb = null;
	
	/**
	 * Constructor
	 */
	public BooklistStyleGroupsActivity() {
		super(KEY_GROUPS, R.layout.admin_booklist_style_group_list, R.layout.row_edit_booklist_style_groups);
	}

	/**
	 * We build a list of GroupWrappers which is passed to the underlying class for editing.
	 * The wrapper includes extra details needed by this activity.
	 * 
	 * @author Philip Warner
	 */
	public static class GroupWrapper implements Serializable {
		private static final long serialVersionUID = 3108094089675884238L;
		/** The actual group */
        final LibraryGroup group;
		/** Whether this groups is present in the style */
		boolean present;
		/** Constructor */
		public GroupWrapper(LibraryGroup group, boolean present) {
			this.group = group;
			this.present = present;
		}
	}

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			// Get the intent and get the style and other settings
			Intent i = this.getIntent();
			mStyle = (LibraryStyle) i.getSerializableExtra(KEY_STYLE);

			if (i.hasExtra(KEY_SAVE_TO_DATABASE))
				mSaveToDb = i.getBooleanExtra(KEY_SAVE_TO_DATABASE, true);

            // Indicated this activity was called without an existing style
            boolean mIsNew = (mStyle == null);
			if (mIsNew)
				mStyle = new LibraryStyle("");

			// Build an array list with the groups from the style, and record that they are present in mGroups.
			ArrayList<GroupWrapper> groups = new ArrayList<>();
			for(LibraryGroup g: mStyle) {
				groups.add(new GroupWrapper(g, true));
			}

			// Get all other groups and add any missing ones to the list
			ArrayList<LibraryGroup> allGroups = LibraryGroup.getAllGroups();
			for(LibraryGroup g: allGroups) {
				if (!mStyle.hasKind(g.kind))
					groups.add(new GroupWrapper(g, false));
			}

			// Store the full list in the intent
			i.putExtra(KEY_GROUPS, groups);

			// Init the subclass now it has the array it expects
			super.onCreate(savedInstanceState);

			if (mIsNew)
				this.setTitle(getString(R.string.title_add_style));
			else if (mStyle.getRowId() == 0)
				this.setTitle(getString(R.string.title_clone_style_colon_name, mStyle.getDisplayName()));
			else
				this.setTitle(getString(R.string.title_edit_style_colon_name, mStyle.getDisplayName()));

			if (savedInstanceState == null)
				HintManager.displayHint(this, R.string.hint_library_style_groups, null, null);

		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	@Override
	protected void onAdd(View v) {
		throw new RuntimeException("Unexpected call to 'onAdd'");
	}

	/**
	 * Holder pattern for each row.
	 * 
	 * @author Philip Warner
	 */
	private static class Holder {
		GroupWrapper wrapper;
		TextView name;
		ImageView present;
	}

	/**
	 * Set up the view for a passed wrapper.
	 */
	@Override
	protected void onSetupView(View target, GroupWrapper wrapper) {
		// Get a Holder
		Holder h;
		h = ViewTagger.getTag(target, R.id.TAG_HOLDER);
		if (h == null) {
			// New view, so build the Holder
			h = new Holder();
			h.name = target.findViewById(R.id.field_name);
			h.present = target.findViewById(R.id.row_present);
			// Tag the parts that need it
			ViewTagger.setTag(target, R.id.TAG_HOLDER, h);
			ViewTagger.setTag(h.present, R.id.TAG_HOLDER, h);
			// Make it flash
			//target.setBackgroundResource(android.R.drawable.list_selector_background);

			// Handle a click on the tick/cross
			h.present.setOnClickListener(v -> {
                Holder h1 = ViewTagger.getTag(v, R.id.TAG_HOLDER);
                assert h1 != null;
                h1.wrapper.present = !h1.wrapper.present;
                if (h1.wrapper.present) {
                    h1.present.setImageResource(R.drawable.ic_menu_checkmark_checked);
                } else {
                    h1.present.setImageResource(R.drawable.ic_menu_checkmark_unchecked);
                }
            });
		}
		// Setup the variant fields in the holder
		h.wrapper = wrapper;
		h.name.setText(wrapper.group.getName());

		// Set the correct image (tick/cross)
		if (h.wrapper.present) {
			h.present.setImageResource(R.drawable.ic_menu_checkmark_checked);
		} else {
    		h.present.setImageResource(R.drawable.ic_menu_checkmark_unchecked);
		}
	}

	/**
	 * Cleanup
	 */
	@Override 
	protected void onDestroy() {
		super.onDestroy();
		if (mDb != null)
			mDb.close();
	}

	/**
	 * Get/create the database connection as necessary.
     */
	private CatalogueDBAdapter getDb() {
		if (mDb == null)
			mDb = new CatalogueDBAdapter(this);
		mDb.open();
		return mDb;
	}

	/**
	 * Save the style in the resulting Intent
	 */
	@Override
	protected boolean onSave(Intent intent) {
		// Save the properties of this style
		Properties props = mStyle.getProperties();
		// Loop through ALL groups
		for(GroupWrapper wrapper: mList) {
			// Remove it from style
			mStyle.removeGroup(wrapper.group.kind);
			// Add it back, if required. 
			// Add then move ensures order will also match 
			if (wrapper.present) {
				mStyle.addGroup(wrapper.group);
			}
		}
		// Apply any saved properties.
		mStyle.setProperties(props);

		// Store in resulting Intent
		intent.putExtra(KEY_STYLE, mStyle);

		// Save to DB if necessary
		if (mSaveToDb)
			mStyle.saveToDb(getDb());

		return true;
	}

	/**
	 * Required. Do nothing.
	 */
	@Override
	protected void onRowClick(View target, int position, GroupWrapper object) {
	}

}
