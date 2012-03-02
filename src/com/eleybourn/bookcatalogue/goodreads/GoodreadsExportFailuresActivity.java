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

package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.R;
import java.util.ArrayList;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.ViewTagger;

import net.philipwarner.taskqueue.BindableItem;
import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.Event;
import net.philipwarner.taskqueue.QueueManager;
import net.philipwarner.taskqueue.Listeners.EventActions;
import net.philipwarner.taskqueue.Listeners.OnEventChangeListener;

/**
 * Activity to display all Events in the QueueManager.
 * 
 * TODO: Decide if this should be renamed, and consider adding event selection methods.
 * 
 * @author Philip Warner
 */
public class GoodreadsExportFailuresActivity extends  net.philipwarner.taskqueue.BindableItemListActivity 
{
	private CatalogueDBAdapter m_db = null;
	private BindableItemSQLiteCursor m_cursor;

	/**
	 * Constructor. Tell superclass the resource for the list.
	 * 
	 */
	public GoodreadsExportFailuresActivity() {
		super(com.eleybourn.bookcatalogue.R.layout.event_list);
	}

	@Override 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Get a DB adapter
		m_db = new CatalogueDBAdapter(this);
		m_db.open();

		//When any Event is added/changed/deleted, update the list. Lazy, yes.
		BookCatalogueApp.getQueueManager().registerEventListener(m_OnEventChangeListener);		
		// Update the header.
		updateHeader();

		// Handle the 'cleanup' button.
		{
			View v = this.findViewById(R.id.cleanup);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					QueueManager.getQueueManager().cleanupOldEvents(7);
				}});
		}
	
		this.setTitle(R.string.task_errors);

	}

	/**
	 * Listener to handle Event add/change/delete.
	 */
	private OnEventChangeListener m_OnEventChangeListener = new  OnEventChangeListener() {
		@Override
		public void onEventChange(Event event, EventActions action) {
			GoodreadsExportFailuresActivity.this.refreshData();
		}};

	/**
	 * Update the header to reflect current cursor size.
	 */
	protected void updateHeader() {
		TextView head = (TextView) this.findViewById(com.eleybourn.bookcatalogue.R.id.events_found);
		head.setText(m_cursor.getCount() + " Events found");		
	}

	/**
	 * Refresh data; some other activity may have changed relevant data (eg. a book)
	 */
	@Override
	protected void onResume() {
		super.onResume();
		refreshData();
	} 

	/**
	 * Build a context menu dialogue when an item is clicked.
	 */
	@Override
	public void onListItemClick(AdapterView<?> parent, final View v, final int position, final long id) {
		Event event = (Event) ViewTagger.getTag(v, R.id.TAG_EVENT);
		ArrayList<ContextDialogItem> items = new ArrayList<ContextDialogItem>();

		event.addContextMenuItems(this, parent, v, position, id, items, m_db);

		if (items.size() > 0) {
			showContextDialogue("Select an Action", items);
		}
	};

	/**
	 * Capture calls to refreshData() so we can update the header.
	 */
	@Override
	protected void refreshData() {
		super.refreshData();
		updateHeader();
	}

	/**
	 * Cleanup
	 */
	@Override
	protected void onDestroy() {
		try {
			super.onDestroy();
		} catch (Exception e) {/* Ignore */}
		try {
			if (m_cursor != null) {
				m_cursor.close();
				m_cursor = null;
			}
		} catch (Exception e) {/* Ignore */}
		try {
			if (m_db != null)
				m_db.close();
		} catch (Exception e) {/* Ignore */}
		try {
			BookCatalogueApp.getQueueManager().unregisterEventListener(m_OnEventChangeListener);					
		} catch (Exception e) {/* Ignore */}
	}

	/**
	 * Paranoid overestimate of the number of event types we use.
	 */
	@Override
	public int getBindableItemTypeCount() {
		return 50;
	}

	/**
	 * Let the Event bind itself.
	 */
	@Override
	public void bindViewToItem(Context context, View view, BindableItemSQLiteCursor cursor, BindableItem bindable) {
		ViewTagger.setTag(view, R.id.TAG_EVENT, bindable);
		bindable.bindView(view, context, cursor, m_db);
	}

	/**
	 * Get the EventsCursor relevant to this Activity
	 */
	@Override
	protected BindableItemSQLiteCursor getBindableItemCursor(Bundle savedInstanceState) {
		m_cursor = BookCatalogueApp.getQueueManager().getAllEvents();
		return m_cursor;
	}
	
}
