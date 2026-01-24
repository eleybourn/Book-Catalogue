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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import net.philipwarner.taskqueue.BindableItemCursorAdapter;
import net.philipwarner.taskqueue.BindableItemCursorAdapter.BindableItemBinder;
import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;

import java.util.ArrayList;

/**
 * NOTE!!!!!
 * This code is copied from TaskQueue *solely* so that it can inherit from BookCatalogueListActivity instead
 * of Activity. If this code needs changes either update it's subclasses OR update the code in TaskQueue
 * and re-copy the file.
 * NOTE!!!!!
 *
 * @author pjw
 *
 */
public abstract class BindableItemListActivity extends AppCompatActivity implements BindableItemBinder {
    /**
     * The resource ID for the base view
     */
    private final int mBaseViewId;
    private final DataSetObserver myListWatcher = new DataSetObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            updateEmptyViewState();
        }
    };
    /**
     * Cursor of book IDs
     */
    protected BindableItemSQLiteCursor mBindableItems;

    /**
     * Adapter for list
     */
    BindableItemCursorAdapter m_listAdapter;

    /**
     * Constructor; this will be called by the subclass to set the resource IDs.
     *
     * @param baseViewId Resource id of base view
     */
    public BindableItemListActivity(int baseViewId) {
        mBaseViewId = baseViewId;
    }

    /**
     * Subclass MUST implement to return the cursor that will be used to select TaskNotes to display. This
     * is called from onCreate().
     *
     * @param savedInstanceState state info passed to onCreate()
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

        mBindableItems = getBindableItemCursor(savedInstanceState);

        // Set up list handling
        m_listAdapter = new BindableItemCursorAdapter(this, this, mBindableItems);
        //m_listAdapter.setViewBinder(m_viewBinder);

        setListAdapter(m_listAdapter);

        ListView lv = this.findViewById(R.id.list);

        lv.setOnItemLongClickListener(BindableItemListActivity.this::onListItemLongClick);

        //} catch (Exception e) {
        //	Logger.logError(e);
        //}
    }

    public void setListAdapter(ListAdapter adapter) {
        ListView lv = unwatchList();
        if (lv != null) {
            lv.setAdapter(adapter);
            adapter.registerDataSetObserver(myListWatcher);
            updateEmptyViewState();
        }
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

    public ListView getListView() {
        return findViewById(R.id.list);
    }

    protected void refreshData() {
        mBindableItems.requery();
        m_listAdapter.notifyDataSetChanged();
    }

    public boolean onListItemLongClick(AdapterView<?> parent, View v, int position, long id) {
        return false;
    }

    /**
     * Utility routine to display an array of ContextDialogItems in an alert.
     *
     * @param title Title of Alert
     * @param items Items to display
     */
    protected void showContextDialogue(String title, ArrayList<ContextDialogItem> items) {
        if (!items.isEmpty()) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(title);

            final ContextDialogItem[] itemArray = new ContextDialogItem[items.size()];
            items.toArray(itemArray);

            builder.setItems(itemArray, (dialog, item) -> itemArray[item].handler.run());
            AlertDialog alert = builder.create();
            alert.show();
        }
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

}
