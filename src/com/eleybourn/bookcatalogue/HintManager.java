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

package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences.Editor;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * Class to manage the display of 'hints' withing the application. Each hint dialog has 
 * a 'Do not show again' option, that results in an update to the preferences which 
 * are checked by this code.
 * 
 * To add a new hint, create a string resource and add it to mHints. Then, to display the
 * hint, simply call HintManager.displayHint(a, stringId).
 * 
 * @author Philip Warner
 */
public class HintManager {
	/** Preferences prefix */
	private final static String TAG = "HintManager";
	/** Preferences prefix for hints */
	private final static String PREF_HINT = TAG + ".Hint.";

	/** All hints managed by this class */
	private static final Hints mHints = new Hints()
		.add("BOOKLIST_STYLES_EDITOR", R.string.hint_booklist_styles_editor)
		.add("BOOKLIST_STYLE_GROUPS", R.string.hint_booklist_style_groups)
		.add("BOOKLIST_STYLE_PROPERTIES", R.string.hint_booklist_style_properties)
		.add("BOOKLIST_GLOBAL_PROPERTIES", R.string.hint_booklist_global_properties)
		.add("BOOKLIST_MULTI_AUTHORS", R.string.hint_authors_book_may_appear_more_than_once)
		.add("BOOKLIST_MULTI_SERIES", R.string.hint_series_book_may_appear_more_than_once)
		.add("BACKGROUND_TASKS", R.string.hint_background_tasks)
		.add("BACKGROUND_TASK_EVENTS", R.string.hint_background_task_events)
		.add("STARTUP_SCREEN", R.string.hint_startup_screen)
		.add("explain_goodreads_no_isbn", R.string.explain_goodreads_no_isbn)
		.add("explain_goodreads_no_match", R.string.explain_goodreads_no_match)
		.add("hint_booklist_style_menu", R.string.hint_booklist_style_menu)
		.add("hint_show_search_results_in_list", R.string.hint_show_search_results_in_list)
		;

	public static interface HintOwner {
		public int getHint();
	}
	
	/** Reset all hints to that they will be displayed again */
	public static void resetHints() {
		Enumeration<Hint> hints = mHints.getHints();
		while(hints.hasMoreElements()) {
			hints.nextElement().setVisible(true);
		}
	}

	/** Display the passed hint, if the user has not disabled it */
	public static boolean displayHint(Context context, int stringId, final Runnable postRun) {
		// Get the hint and return if it has been disabled.
		final Hint h = mHints.getHint(stringId);
		if (!h.isVisible()) {
			if (postRun != null)
				postRun.run();
			return false;			
		}

		// Build the hint dialog
		final Dialog dialog = new Dialog(context);
		dialog.setContentView(R.layout.hint_dialogue);
		
		// Get the various Views
		final TextView msg = (TextView)dialog.findViewById(R.id.hint);
		final CheckBox cb = (CheckBox)dialog.findViewById(R.id.hide_hint_checkbox); // new CheckBox(context);
		final Button ok = (Button)dialog.findViewById(R.id.confirm);

		// Setup the views
		msg.setText(stringId);
		dialog.setTitle(R.string.hint);

		// Handle the 'OK' click
		ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				// Disable hint if checkbox checked
				if (cb.isChecked()) {
					h.setVisible(false);
				}
			}
		});

		dialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				if (postRun != null)
					postRun.run();
			}
		});

		dialog.show();		
		return true;
	}
	
	/**
	 * Class to represent a collection of all defined hints
	 * 
	 * @author Philip Warner
	 */
	private static class Hints {
		/** USed to lookup hint based on string ID */
		private Hashtable<Integer, Hint> mHintsById = new Hashtable<Integer, Hint>();
		/** Used to prevent two hints having the same preference name */
		private Hashtable<String, Hint> mHintsByKey = new Hashtable<String, Hint>();

		/**
		 * Add a hint to the collection
		 * 
		 * @param key			Unique preference suffix for this hint
		 * @param stringId		String ID to display
		 * 
		 * @return				Hints, for chaining
		 */
		public Hints add(String key, int stringId) {
			Hint h = new Hint(key, stringId);
			mHintsById.put(stringId, h);
			mHintsByKey.put(key.trim().toLowerCase(), h);
			return this;
		}

		/**
		 * Return the hint based on string ID
		 * @param stringId
		 * @return
		 */
		public Hint getHint(int stringId) {
			Hint h = mHintsById.get(stringId);
			if (h == null)
				throw new RuntimeException("Hint not found for ID " + stringId);
			return h;
		}

		/**
		 * Get an enumeration of all hints.
		 * 
		 * @return
		 */
		public Enumeration<Hint> getHints() {
			return mHintsById.elements();
		}

	}
	
	/**
	 * Class to represent a single Hint.
	 * 
	 * @author Philip Warner
	 */
	private static class Hint {
		/** Preferences key suffix specific to this hint */
		public final String key;
		/** String to display for this hint */
		public final int stringId;

		/**
		 * Constructor
		 * 
		 * @param key			Preferences key suffix specific to this hint
		 * @param stringId		String to display for this hint
		 */
		private Hint(String key, int stringId) {
			this.key = key;
			this.stringId = stringId;
		}
		
		/**
		 * Get the preference name for this hint
		 *
		 * @return		Fully qualified preference name
		 */
		public String getFullPrefName() {
			return PREF_HINT + key;
		}

		/**
		 * Set the preference to indicate if this hint should be shown again
		 * 
		 * @param visible	Flag indicating future visibility
		 */
		public void setVisible(boolean visible) {
			BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
			Editor ed = prefs.edit();
			String name = getFullPrefName();
			ed.putBoolean(name, visible);
			ed.commit();
		}
		/**
		 * Check if this hint should be shown
		 * 
		 * @return
		 */
		public boolean isVisible() {
			BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
			return prefs.getBoolean(getFullPrefName(), true);
		}
	}
}
