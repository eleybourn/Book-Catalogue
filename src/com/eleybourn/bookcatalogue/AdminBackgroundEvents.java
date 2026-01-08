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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.HintManager.HintOwner;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.google.android.material.appbar.MaterialToolbar;

import net.philipwarner.taskqueue.BindableItem;
import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.Event;
import net.philipwarner.taskqueue.Listeners.OnEventChangeListener;
import net.philipwarner.taskqueue.QueueManager;

import java.util.ArrayList;

/**
 * Activity to display all Events in the QueueManager.
 *
 * @author Philip Warner
 */
public class AdminBackgroundEvents extends BindableItemListActivity {
    /**
     * Key to store optional task ID hen activity is started
     */
    public static final String KEY_TASK_ID = "AdminBackgroundEvents.TaskId";
    /**
     * DB connection
     */
    private CatalogueDBAdapter m_db = null;
    private BindableItemSQLiteCursor m_cursor;
    /**
     * Listener to handle Event add/change/delete.
     */
    private final OnEventChangeListener m_OnEventChangeListener = (event, action) -> AdminBackgroundEvents.this.refreshData();
    /**
     * Task ID, if provided in intent
     */
    private long mTaskId = 0;

    /**
     * Constructor. Tell superclass the resource for the list.
     *
     */
    public AdminBackgroundEvents() {
        super(R.layout.admin_background_events);
    }

    /**
     * Get the unique ID associated with this activity. Used in activity results.
     */
    public static int getActivityId() {
        return UniqueId.ACTIVITY_GOODREADS_EXPORT_FAILURES;
    }

    /**
     * Utility routine to start this activity on behalf of the passed activity.
     */
    public static void start(Activity from, long taskId) {
        Intent i = new Intent(from, AdminBackgroundEvents.class);
        i.putExtra(KEY_TASK_ID, taskId);
        from.startActivityForResult(i, getActivityId());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Get a DB adapter
        m_db = new CatalogueDBAdapter(this);
        m_db.open();

        Intent i = getIntent();
        if (i != null && i.hasExtra(KEY_TASK_ID)) {
            mTaskId = i.getLongExtra(KEY_TASK_ID, 0);
        } else {
            mTaskId = 0;
        }

        // Once the basic criteria have been setup, call the parent
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_background_events);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        //When any Event is added/changed/deleted, update the list. Lazy, yes.
        BookCatalogueApp.getQueueManager().registerEventListener(m_OnEventChangeListener);
        // Update the header.
        updateHeader();

        // Handle the 'cleanup' button.
        {
            View v = this.findViewById(R.id.cleanup);
            v.setOnClickListener(v1 -> QueueManager.getQueueManager().cleanupOldEvents(7));
        }

        this.setTitle(R.string.task_errors);

        if (savedInstanceState == null)
            HintManager.displayHint(this, R.string.hint_background_task_events, null, null);

    }

    /**
     * Update the header to reflect current cursor size.
     */
    protected void updateHeader() {
        TextView head = this.findViewById(R.id.events_found);
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
    public void onListItemClick(final AdapterView<?> parent, final View v, final int position, final long id) {
        // get the event object
        final Event event = ViewTagger.getTag(v, R.id.TAG_EVENT);

        // If it owns a hint, display it
        if (event instanceof HintOwner) {
            HintOwner h = (HintOwner) event;
            // Show the hint if necessary; fall through to the runnable
            HintManager.displayHint(this, h.getHint(), null, () -> doContextMenu(parent, v, position, id));
        } else {
            // Just display context menu
            doContextMenu(parent, v, position, id);
        }
    }

    private void doContextMenu(final AdapterView<?> parent, final View v, final int position, final long id) {
        final Event event = ViewTagger.getTag(v, R.id.TAG_EVENT);
        final ArrayList<ContextDialogItem> items = new ArrayList<>();

        assert event != null;
        event.addContextMenuItems(this, parent, v, position, id, items, m_db);

        if (!items.isEmpty()) {
            showContextDialogue("Select an Action", items);
        }
    }

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
        if (mTaskId == 0)
            m_cursor = BookCatalogueApp.getQueueManager().getAllEvents();
        else
            m_cursor = BookCatalogueApp.getQueueManager().getTaskEvents(mTaskId);

        return m_cursor;
    }

}
