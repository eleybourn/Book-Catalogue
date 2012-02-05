package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * Base class to display simple preference-based optins to the user.
 * 
 * @author Grunthos
 */
public abstract class PreferencesBase extends Activity {
	private int mLayout;

	/** Get the layour of the subclass */
	public abstract int getLayout();
	/** Setup the views in the layout */
	public abstract void setupViews(BookCataloguePreferences prefs);
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			System.out.println("In onCreate in PreferencesBase");
			setContentView(this.getLayout());
			final BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
			setupViews(prefs);
		} catch (Exception e) {
			Logger.logError(e);
		}
		
	}

	/**
	 * Utility routine to setup a checkobox based on a preference.
	 * 
	 * @param prefs		Preferences to use
	 * @param cbId		CheckBox ID from XML file
	 * @param viewId	Containing ViewGroup from XML file (for clicking and highlighting)
	 * @param key		Preferences key associated with this CheckBox
	 */
	protected void addBooleanPreference(final BookCataloguePreferences prefs, final int cbId, int viewId, final String key) {
		// Setup the checkbox
		{
			CheckBox v = (CheckBox)this.findViewById(cbId);
			v.setChecked(prefs.getBoolean(key, false));
			v.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					prefs.setBoolean(key,isChecked);
				}});
		}
		// Allow clicking of entire row.
		{
			View v = this.findViewById(viewId);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					CheckBox cb = (CheckBox)v.findViewById(cbId);
					cb.setChecked(!prefs.getBoolean(key, false));
				}});
		}
	}

	// Add an item that has a creator-define click event
	public void addClickablePref(final BookCataloguePreferences prefs, final int viewId, final OnClickListener listener) {
		/* Erase covers cache Link */
		View v = findViewById(viewId);
		// Make line flash when clicked.
		v.setBackgroundResource(android.R.drawable.list_selector_background);
		v.setOnClickListener(listener);
	}
}
