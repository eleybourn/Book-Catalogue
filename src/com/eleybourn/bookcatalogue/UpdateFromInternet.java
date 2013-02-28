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

import java.util.LinkedHashMap;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.UpdateFromInternet.FieldUsages.Usages;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

public class UpdateFromInternet extends ActivityWithTasks {

	private long mUpdateSenderId = 0;
	private SharedPreferences mPrefs = null;

	/**
	 * Class to manage a collection of fields and the rules for importing them.
	 * Inherits from LinkedHashMap to guarantee iteration order.
	 * 
	 * @author Philip Warner
	 */
	static public class FieldUsages extends LinkedHashMap<String,FieldUsage>  {
		private static final long serialVersionUID = 1L;

		static public enum Usages { COPY_IF_BLANK, ADD_EXTRA, OVERWRITE };

		public FieldUsage put(FieldUsage usage) {
			this.put(usage.fieldName, usage);
			return usage;
		}

	}
	private FieldUsages mFieldUsages = new FieldUsages();

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			Utils.showLtAlertIfNecessary(this, false, "update_from_internet");

			setContentView(R.layout.update_from_internet);
			mPrefs = getSharedPreferences("bookCatalogue", android.content.Context.MODE_PRIVATE);
			setupFields();
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	public class FieldUsage {
		final String fieldName;
		final int stringId;
		Usages usage;
		boolean selected;
		final boolean canAppend;
		FieldUsage(String name, int id, Usages usage, boolean canAppend) {
			this.fieldName = name;
			this.stringId = id;
			this.usage = usage;
			this.selected = true;
			this.canAppend = canAppend;
		}
	}

	/**
	 * Add a FieldUsage if the specified field has not been hidden by the user.
	 * 
	 * @param field		Field name to use in FieldUsages
	 * @param visField	Field name to check for visibility. If null, use field.
	 * @param stringId	ID of field label string
	 * @param usage		Usage to apply.
	 */
	private void addIfVisible(String field, String visField, int stringId, Usages usage, boolean canAppend) {
		if (visField == null || visField.trim().length() == 0)
			visField = field;
		if (mPrefs.getBoolean(FieldVisibility.prefix + visField, true))
			mFieldUsages.put(new FieldUsage(field, stringId, usage, canAppend));		
	}
	/**
	 * This function builds the manage field visibility by adding onClick events
	 * to each field checkbox
	 */
	public void setupFields() {
		addIfVisible(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, CatalogueDBAdapter.KEY_AUTHOR_ID, R.string.author, Usages.ADD_EXTRA, true);
		addIfVisible(CatalogueDBAdapter.KEY_TITLE, null, R.string.title, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_ISBN, null, R.string.isbn, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_THUMBNAIL, null, R.string.thumbnail, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_SERIES_ARRAY, CatalogueDBAdapter.KEY_SERIES_NAME, R.string.series, Usages.ADD_EXTRA, true);
		addIfVisible(CatalogueDBAdapter.KEY_PUBLISHER, null, R.string.publisher, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_DATE_PUBLISHED, null, R.string.date_published, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_PAGES, null, R.string.pages, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_LIST_PRICE, null, R.string.list_price, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_FORMAT, null, R.string.format, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_DESCRIPTION, null, R.string.description, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_GENRE, null, R.string.genre, Usages.COPY_IF_BLANK, false);
		addIfVisible(CatalogueDBAdapter.KEY_LANGUAGE, null, R.string.language, Usages.COPY_IF_BLANK, false);

		// Display the list of fields
		LinearLayout parent = (LinearLayout) findViewById(R.id.manage_fields_scrollview);
		for(FieldUsage usage : mFieldUsages.values()) {
			//Create the LinearLayout to hold each row
			LinearLayout ll = new LinearLayout(this);
			ll.setPadding(5, 0, 0, 0);
			
			//Create the checkbox
			CheckBox cb = new CheckBox(this);
			cb.setChecked(usage.selected);
			ViewTagger.setTag(cb, usage);
			cb.setId(R.id.fieldCheckbox);
			//add override capability
			cb.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					CheckBox thiscb = (CheckBox) v;
					if (thiscb.isChecked() == false && thiscb.getText().toString().contains(getResources().getString(R.string.usage_copy_if_blank))) {
						FieldUsage usage = (FieldUsage) ViewTagger.getTag(thiscb);
						if (usage.canAppend) {
							String extra = getResources().getString(R.string.usage_add_extra);
							String text = getResources().getString(usage.stringId);
							thiscb.setText(text + " (" + extra + ")");
							thiscb.setChecked(true); //reset to checked
							usage.usage = FieldUsages.Usages.ADD_EXTRA;
							ViewTagger.setTag(thiscb, usage);
						} else {
							String extra = getResources().getString(R.string.usage_overwrite);
							String text = getResources().getString(usage.stringId);
							thiscb.setText(text + " (" + extra + ")");
							thiscb.setChecked(true); //reset to checked
							usage.usage = FieldUsages.Usages.OVERWRITE;
							ViewTagger.setTag(thiscb, usage);
						}
					} else if (thiscb.getText().toString().contains(getResources().getString(R.string.usage_add_extra))) {
						FieldUsage usage = (FieldUsage) ViewTagger.getTag(thiscb);
						String extra = getResources().getString(R.string.usage_overwrite);
						String text = getResources().getString(usage.stringId);
						thiscb.setText(text + " (" + extra + ")");
						thiscb.setChecked(true); //reset to checked
						usage.usage = FieldUsages.Usages.OVERWRITE;
						ViewTagger.setTag(thiscb, usage);
					} else if (thiscb.getText().toString().contains(getResources().getString(R.string.usage_overwrite))) {
						FieldUsage usage = (FieldUsage) ViewTagger.getTag(thiscb);
						String extra = getResources().getString(R.string.usage_copy_if_blank);
						String text = getResources().getString(usage.stringId);
						thiscb.setText(text + " (" + extra + ")");
						usage.usage = FieldUsages.Usages.COPY_IF_BLANK;
						ViewTagger.setTag(thiscb, usage);
					}
				}
			});
			//ll.addView(cb);

			//Create the checkBox label (or textView)
			//TextView tv = new TextView(this);
			cb.setTextAppearance(this, android.R.attr.textAppearanceLarge);
			String text = getResources().getString(usage.stringId);
			String extra;
			switch(usage.usage) {
			case ADD_EXTRA:
				extra = getResources().getString(R.string.usage_add_extra);
				break;
			case COPY_IF_BLANK:
				extra = getResources().getString(R.string.usage_copy_if_blank);
				break;
			case OVERWRITE:
				extra = getResources().getString(R.string.usage_overwrite);
				break;
			default:
				throw new RuntimeException("Unknown Usage");
			}
			cb.setText(text + " (" + extra + ")");
			//tv.setPadding(0, 5, 0, 0);
			ll.addView(cb);
			
			//Add the LinearLayout to the parent
			parent.addView(ll);			
		}

		Button confirmBtn = (Button) findViewById(R.id.confirm);
		confirmBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Get the selections the user made
				if (readUserSelections() == 0) {
					Toast.makeText(UpdateFromInternet.this, R.string.select_min_1_field, Toast.LENGTH_LONG).show();
					return;
				}
				
				// If they have selected thumbnails, check if they want to download ALL.
				boolean thumbnail_check = false;
				try {
					thumbnail_check = mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL).selected;
				} catch (NullPointerException e) {
					Logger.logError(e);
				}
				if (thumbnail_check) {
					// Verify - this can be a dangerous operation
					AlertDialog alertDialog = new AlertDialog.Builder(UpdateFromInternet.this).setMessage(R.string.overwrite_thumbnail).create();
					alertDialog.setTitle(R.string.update_fields);
					alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
					alertDialog.setButton(UpdateFromInternet.this.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL).usage = Usages.OVERWRITE;
							startUpdate();
							return;
						}
					}); 
					alertDialog.setButton2(UpdateFromInternet.this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							//do nothing
							return;
						}
					});
					alertDialog.setButton3(UpdateFromInternet.this.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL).usage = Usages.COPY_IF_BLANK;
							startUpdate();
							return;
						}
					}); 
					alertDialog.show();					
				} else {
					startUpdate();					
				}
				return;
			}
		});

		Button cancelBtn = (Button) findViewById(R.id.cancel);
		cancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
	}

	private int readUserSelections() {
		LinearLayout parent = (LinearLayout) findViewById(R.id.manage_fields_scrollview);
		int nChildren = parent.getChildCount();
		int nSelected = 0;
		for (int i = 0; i<nChildren; i++) {
			View v = parent.getChildAt(i);
			CheckBox cb = (CheckBox) v.findViewById(R.id.fieldCheckbox);
			if (cb != null) {
				FieldUsage usage = (FieldUsage) ViewTagger.getTag(cb);
				usage.selected = cb.isChecked();
				if (usage.selected)
					nSelected++;
			}
		}	
		return nSelected;
	}

	private void startUpdate() {
		UpdateThumbnailsThread t = new UpdateThumbnailsThread(getTaskManager(), mFieldUsages, mThumbnailsHandler);
		mUpdateSenderId = t.getSenderId();
		UpdateThumbnailsThread.getMessageSwitch().addListener(mUpdateSenderId, mThumbnailsHandler, false);
		t.start();	
	}

	final ManagedTask.TaskListener mThumbnailsHandler = new ManagedTask.TaskListener() {
		@Override
		public void onTaskFinished(ManagedTask t) {
			mUpdateSenderId = 0;
			finish();
		}
	};

	@Override
	protected void onPause() {
		Tracker.enterOnPause(this);
		super.onPause();
		if (mUpdateSenderId != 0)
			UpdateThumbnailsThread.getMessageSwitch().removeListener(mUpdateSenderId, mThumbnailsHandler);
		Tracker.exitOnPause(this);
	}

	@Override
	protected void onResume() {
		Tracker.enterOnResume(this);
		super.onResume();
		if (mUpdateSenderId != 0)
			UpdateThumbnailsThread.getMessageSwitch().addListener(mUpdateSenderId, mThumbnailsHandler, true);
		Tracker.exitOnResume(this);
	}

}
