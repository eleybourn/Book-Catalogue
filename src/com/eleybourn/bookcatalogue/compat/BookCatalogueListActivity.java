package com.eleybourn.bookcatalogue.compat;

import android.database.DataSetObserver;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
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
public abstract class BookCatalogueListActivity extends AppCompatActivity { //} SherlockListActivity {
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
        	lv.setOnItemClickListener((adapterView, view, i, l) -> onListItemClick(lv, view, i, l));
		}
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		//noinspection SwitchStatementWithTooFewBranches
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
		ListView lv = unwatchList();
		if (lv != null) {
			lv.setAdapter(adapter);
			adapter.registerDataSetObserver(myListWatcher);
			updateEmptyViewState();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unwatchList();
	}

	private ListView unwatchList() {
		ListView lv = getListView();
		if (lv != null) {
			ListAdapter old = lv.getAdapter();
			if (old != null) {
				old.unregisterDataSetObserver(myListWatcher);
			}
		}
		return lv;
	}

	private void updateEmptyViewState() {
		View v = findViewById(R.id.empty);
		if (v != null) {
			ListView lv = getListView();
			if (lv != null) {
				ListAdapter a = lv.getAdapter();
				if (a.getCount() == 0) {
					v.setVisibility(View.VISIBLE);
				} else {
					v.setVisibility(View.GONE);
				}
			}
		}
	}

	private DataSetObserver myListWatcher = new DataSetObserver() {
		@Override
		public void onChanged() {
			super.onChanged();
			updateEmptyViewState();
		}
	};

}
