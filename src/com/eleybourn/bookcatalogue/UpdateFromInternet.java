package com.eleybourn.bookcatalogue;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;

public class UpdateFromInternet extends ActivityWithTasks {
	
	private String[] mFields;

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.update_from_internet);
			setupFields();
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * This function builds the manage field visibility by adding onClick events
	 * to each field checkbox
	 */
	public void setupFields() {
		// The fields to show/hide
		mFields = new String[] {
				CatalogueDBAdapter.KEY_AUTHOR_ARRAY, 
				CatalogueDBAdapter.KEY_TITLE, 
				"thumbnail", 
				CatalogueDBAdapter.KEY_SERIES_ARRAY, 
				CatalogueDBAdapter.KEY_PUBLISHER, 
				CatalogueDBAdapter.KEY_DATE_PUBLISHED,  
				CatalogueDBAdapter.KEY_PAGES, 
				CatalogueDBAdapter.KEY_LIST_PRICE, 
				CatalogueDBAdapter.KEY_FORMAT, 
				CatalogueDBAdapter.KEY_DESCRIPTION, 
				CatalogueDBAdapter.KEY_GENRE};
		int[] fieldRs = {
				R.string.author, 
				R.string.title, 
				R.string.thumbnail, 
				R.string.series,
				R.string.publisher, 
				R.string.date_published, 
				R.string.pages, 
				R.string.list_price,
				R.string.format, 
				R.string.description, 
				R.string.genre};
		
		// Display the list of fields
		LinearLayout parent = (LinearLayout) findViewById(R.id.manage_fields_scrollview);
		for (int i = 0; i < mFields.length; i++) {
			//Create the LinearLayout to hold each row
			LinearLayout ll = new LinearLayout(this);
			ll.setPadding(5, 0, 0, 0);
			
			//Create the checkbox
			CheckBox cb = new CheckBox(this);
			cb.setChecked(true);
			cb.setTag((Integer)i);
			cb.setId(R.id.fieldCheckbox);
			ll.addView(cb);

			//Create the checkBox label (or textView)
			TextView tv = new TextView(this);
			tv.setTextAppearance(this, android.R.attr.textAppearanceLarge);
			tv.setText(fieldRs[i]);
			tv.setPadding(0, 5, 0, 0);
			ll.addView(tv);
			
			//Add the LinearLayout to the parent
			parent.addView(ll);			
		}

		Button confirmBtn = (Button) findViewById(R.id.confirm);
		confirmBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Verify - this can be a dangerous operation
				AlertDialog alertDialog = new AlertDialog.Builder(UpdateFromInternet.this).setMessage(R.string.overwrite_thumbnail).create();
				alertDialog.setTitle(R.string.update_fields);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(UpdateFromInternet.this.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						startUpdate(true);
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
						startUpdate(false);
						return;
					}
				}); 
				alertDialog.show();
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

	private void startUpdate(boolean overwrite) {
		LinearLayout parent = (LinearLayout) findViewById(R.id.manage_fields_scrollview);
		int nChildren = parent.getChildCount();
		Hashtable<String,Boolean> fieldHash = new Hashtable<String,Boolean>();
		for (int i = 0; i<nChildren; i++) {
			View v = parent.getChildAt(i);
			CheckBox cb = (CheckBox) v.findViewById(R.id.fieldCheckbox);
			if (cb != null) {
				int offset = (Integer) cb.getTag();
				if (cb.isChecked()) {
					fieldHash.put(mFields[offset], true);
				}				
			}
		}

		if (fieldHash.keySet().size() == 0) {
			Toast.makeText(this, R.string.select_min_1_field, Toast.LENGTH_LONG).show();
			return;
		}

		UpdateThumbnailsThread thread = new UpdateThumbnailsThread(mTaskManager, fieldHash, overwrite, mThumbnailsHandler);
		thread.start();	
	}

	final UpdateThumbnailsThread.LookupHandler mThumbnailsHandler = new UpdateThumbnailsThread.LookupHandler() {
		@Override
		public void onFinish() {
		}
	};

	@Override
	TaskHandler getTaskHandler(ManagedTask t) {
		return mThumbnailsHandler;
	}

}
