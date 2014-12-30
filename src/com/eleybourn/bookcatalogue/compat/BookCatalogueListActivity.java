package com.eleybourn.bookcatalogue.compat;

import android.os.Bundle;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;

/**
 * Class introduced to reduce the future pain when we remove sherlock (once we no longer 
 * support Android 2.x), and potentially to make it easier to support two versions.
 * 
 * This activity inherits from SherlockListActivity; there is no matching class in the
 * compatibility library.
 * 
 * It is very tempting to take the code from 'ListActivity' and base this class off of
 * BookCatalogueActivity, but currently there is little value in doing go.
 *
 * @author pjw
 */
public class BookCatalogueListActivity extends SherlockListActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
        	bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
    		bar.setDisplayHomeAsUpEnabled(! (this.isTaskRoot() || getIntent().getBooleanExtra("willBeTaskRoot", false) ) );
        }
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

        case android.R.id.home:
        	finish();
            return true;

        default:
            return super.onOptionsItemSelected(item);
		}
		
	}
}
