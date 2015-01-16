package com.eleybourn.bookcatalogue.compat;

import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.eleybourn.bookcatalogue.BookCatalogueApp;

import java.util.Locale;

/**
 * Class introduced to reduce the future pain when we remove sherlock (once we no longer 
 * support Android 2.x), and potentially to make it easier to support two versions.
 * 
 * This activity inherits from SherlockFragmentActivity which is just a subclass of
 * the compatibility library FragmentActivity which should be fairly compatible with
 * Activity in API 11+.
 * 
 * @author pjw
 */
public class BookCatalogueActivity extends SherlockFragmentActivity {
    /** Last locale used so; cached so we can check if it has genuinely changed */
    private Locale mLastLocale = BookCatalogueApp.getPreferredLocale();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
        	// Show home, use logo (bigger) and show title
        	bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
        	// Don't display the 'back' decoration if we are not at the top
    		bar.setDisplayHomeAsUpEnabled(! (this.isTaskRoot() || getIntent().getBooleanExtra("willBeTaskRoot", false) ) );
        }
    }
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		// Default handler for home icon
        case android.R.id.home:
        	finish();
            return true;

        default:
            return super.onOptionsItemSelected(item);	
		}
		
	}

    @Override
    /**
     * When resuming, check if locale has changed and reload activity if so.
     */
    protected void onResume() {
        reloadIfLocaleChanged();
        super.onResume();
    }

    /**
     * Reload this activity if locale has changed.
     */
    public void reloadIfLocaleChanged() {
        Locale old = mLastLocale;
        Locale curr = BookCatalogueApp.getPreferredLocale();
        if ((curr != null && !curr.equals(old)) || (curr == null && old != null)) {
            mLastLocale = curr;
            BookCatalogueApp.applyPreferredLocaleIfNecessary(this.getResources());
            Intent intent = getIntent();
            System.out.println("Restarting " + this.getClass().getSimpleName());
            finish();
            startActivity(intent);
        }
    }

}
