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

//import android.R;
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookEdit extends TabActivity {
	public static final String TAB = "tab";
	public static final int TAB_EDIT = 0;
	public int currentTab = 0;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabhost);
		
		Resources res = getResources(); // Resource object to get Drawables
		TabHost tabHost = getTabHost();  // The activity TabHost
		TabHost.TabSpec spec;  // Resusable TabSpec for each tab
		Intent intent;  // Reusable Intent for each tab
		
		//get the passed parameters
		Bundle extras = getIntent().getExtras();
		currentTab = extras != null ? extras.getInt(BookEdit.TAB) : 0;
		
		// Create an Intent to launch an Activity for the tab (to be reused)
		intent = new Intent().setClass(this, BookEditFields.class);
		intent.putExtras(extras);
		// Initialise a TabSpec for each tab and add it to the TabHost
		spec = tabHost.newTabSpec("artists").setIndicator("Edit Book", res.getDrawable(R.drawable.ic_tab_bookedit)).setContent(intent);
		tabHost.addTab(spec);

		// Do the same for the other tabs
		intent = new Intent().setClass(this, BookEditNotes.class);
		intent.putExtras(extras);
		spec = tabHost.newTabSpec("albums").setIndicator("Albums", res.getDrawable(R.drawable.ic_tab_bookedit)).setContent(intent);
		tabHost.addTab(spec);

		intent = new Intent().setClass(this, BookEditLoaned.class);
		intent.putExtras(extras);
		spec = tabHost.newTabSpec("songs").setIndicator("Songs", res.getDrawable(R.drawable.ic_tab_bookedit)).setContent(intent);
		tabHost.addTab(spec);

		tabHost.setCurrentTab(currentTab);
	}
}
