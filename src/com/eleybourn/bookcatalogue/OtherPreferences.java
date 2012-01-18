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
 * Activity to display the 'Other Preferences' dialog and maintain the preferences.
 * 
 * @author Grunthos
 */
public class OtherPreferences extends Activity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.other_preferences);
			setupViews();
		} catch (Exception e) {
			Logger.logError(e);
		}
		
	}

	/**
	 * Display current preferences and set handlers to catch changes.
	 */
	private void setupViews() {
		final BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		{
			CheckBox v = (CheckBox)this.findViewById(R.id.startup_my_books_checkbox);
			v.setChecked(prefs.getStartInMyBook());
			v.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					prefs.setStartInMyBook(isChecked);
				}});
		}
		{
			// When the parent view is clicked, change the checkbox.
			View v = this.findViewById(R.id.startup_in_my_books_label);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					CheckBox cb = (CheckBox)v.findViewById(R.id.startup_my_books_checkbox);
					cb.setChecked(!prefs.getStartInMyBook());
				}});
		}
		
	}
	
}
