package com.eleybourn.bookcatalogue.compat;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.R;

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
public class BookCatalogueListActivity extends AppCompatActivity { //} SherlockListActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
        	bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
    		bar.setDisplayHomeAsUpEnabled(! (this.isTaskRoot() || getIntent().getBooleanExtra("willBeTaskRoot", false) ) );
        }

        final ListView lv = getListView();
        if (lv != null) {
        	lv.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
					onListItemClick(lv, view, i, l);
				}
			});
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

	public ListView getListView() {
		return findViewById(R.id.list);
	}

	protected void onListItemClick(ListView l, View v, int position, long id) {
	}

	public void setListAdapter(ListAdapter adapter) {
		getListView().setAdapter(adapter);
	}

}
