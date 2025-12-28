/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueListActivity;

import net.philipwarner.taskqueue.BindableItemCursorAdapter;
import net.philipwarner.taskqueue.BindableItemCursorAdapter.BindableItemBinder;
import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;

import java.util.ArrayList;

/**
 * NOTE!!!!!
 * This code is copied from TaskQueue *solely* so that it can inherit from BookCatalogueListActivity instead
 * of Activity. If this code needs changes either update it's cubclasses OR updat the code in TaskQueue
 * and re-copy the file.
 * NOTE!!!!!
 * 
 * @author pjw
 *
 */
public abstract class BindableItemListActivity extends BookCatalogueListActivity implements BindableItemBinder {
	/** The resource ID for the base view */
	private final int mBaseViewId;

	/**
	 * Constructor; this will be called by the subclass to set the resource IDs.
	 * 
	 * @param baseViewId	Resource id of base view
	 */
	public BindableItemListActivity(int baseViewId) {
		mBaseViewId = baseViewId;
	}

	/** Cursor of book IDs */
	protected BindableItemSQLiteCursor mBindableItems;

	/** Adapter for list */
	BindableItemCursorAdapter	m_listAdapter;
	
	/**
	 * Subclass MUST implement to return the cursor that will be used to select TaskNotes to display. This
	 * is called from onCreate().
	 * 
	 * @param savedInstanceState	state info passed to onCreate()
	 *
	 * @return TaskNotesCursor to use
	 */
	protected abstract BindableItemSQLiteCursor getBindableItemCursor(Bundle savedInstanceState);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//try {
			// Set the view
			setContentView(mBaseViewId);

			// Add handlers for 'Save' and 'Cancel'
			//setupListener(R.id.confirm, mSaveListener);
			//setupListener(R.id.cancel, mCancelListener);

			mBindableItems = getBindableItemCursor(savedInstanceState);

			// Set up list handling
			m_listAdapter = new BindableItemCursorAdapter(this, this, mBindableItems);
			//m_listAdapter.setViewBinder(m_viewBinder);

			setListAdapter(m_listAdapter);

			ListView lv = this.findViewById(R.id.list);

			lv.setOnItemClickListener( new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
					BindableItemListActivity.this.onListItemClick(parent, v, position, id);
				}
			});
			lv.setOnItemLongClickListener( new OnItemLongClickListener() {

				@Override
				public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
					return BindableItemListActivity.this.onListItemLongClick(parent, v, position, id);
				}
			});

		//} catch (Exception e) {
		//	Logger.logError(e);
		//}
	}

	protected void refreshData() {
		mBindableItems.requery();
		m_listAdapter.notifyDataSetChanged();
	}

	public void onListItemClick(AdapterView<?> parent, View v, int position, long id) {}

    public boolean onListItemLongClick(AdapterView<?> parent, View v, int position, long id) { return false; }

    //public abstract void bindListItem(View view, Context context, TasksCursor cursor) ;

	/**
	 * Utility routine to display an array of ContextDialogItems in an alert.
	 * 
	 * @param title		Title of Alert
	 * @param items		Items to display
	 */
	protected void showContextDialogue(String title, ArrayList<ContextDialogItem> items) {
		if (items.size() > 0) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(title);

			final ContextDialogItem[] itemArray = new ContextDialogItem[items.size()];
			items.toArray(itemArray);
	
			builder.setItems(itemArray, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
			    	itemArray[item].handler.run();
			    }
			});
			AlertDialog alert = builder.create();	
			alert.show();
		}		
	}


}
