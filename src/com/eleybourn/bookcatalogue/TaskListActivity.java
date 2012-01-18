package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import net.philipwarner.taskqueue.BindableItem;
import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.QueueManager;
import net.philipwarner.taskqueue.Task;
import net.philipwarner.taskqueue.TasksCursor;
import net.philipwarner.taskqueue.Listeners.TaskActions;
import net.philipwarner.taskqueue.Listeners.OnTaskChangeListener;
import net.philipwarner.taskqueue.TasksCursor.TaskCursorSubtype;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;

/**
 * Activity to display the available QueueManager Task object subclasses to the user.
 * 
 * @author Grunthos
 */
public class TaskListActivity extends net.philipwarner.taskqueue.BindableItemListActivity {
	private CatalogueDBAdapter m_db = null;
	private TasksCursor m_cursor;

	/**
	 * Constructor. Give superclass the list view ID.
	 */
	public TaskListActivity() {
		super(R.layout.task_list);
	}

	@Override 
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);			
			// Get a DB adapter
			m_db = new CatalogueDBAdapter(this);
			m_db.open();
			//When any Event is added/changed/deleted, update the list. Lazy, yes.
			BookCatalogueApp.getQueueManager().registerTaskListener(m_OnTaskChangeListener);		

			// Bind the 'cleanup' button.
			{
				View v = this.findViewById(R.id.cleanup);
				v.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						QueueManager.getQueueManager().cleanupOldTasks(7);
					}});				
			}

		} catch (Exception e) {
			Logger.logError(e);
		}
		
	}

	/**
	 * Listener to handle Event add/change/delete.
	 */
	private OnTaskChangeListener m_OnTaskChangeListener = new  OnTaskChangeListener() {
		@Override
		public void onTaskChange(Task task, TaskActions action) {
			TaskListActivity.this.refreshData();
		}};

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
		Task task = (Task) v.getTag(R.id.TAG_TASK);
		ArrayList<ContextDialogItem> items = new ArrayList<ContextDialogItem>();

		task.addContextMenuItems(this, parent, v, position, id, items, m_db);

		if (items.size() > 0) {
			showContextDialogue(this.getString(R.string.select_an_action), items);
		}
	};

	/**
	 * Return the number of task types we might return. 50 is just paranoia.
	 * TODO: Keep checking this value!
	 */
	@Override
	public int getBindableItemTypeCount() {
		return 50;
	}

	/**
	 * Pass binding off to the task object.
	 */
	@Override
	public void bindViewToItem(Context context, View view, BindableItemSQLiteCursor cursor, BindableItem bindable) {
		view.setTag(R.id.TAG_TASK, bindable);
		bindable.bindView(view, context, cursor, m_db);
	}

	/**
	 * Get a cursor returning the tasks we are interested in (in this case all tasks)
	 */
	@Override
	protected BindableItemSQLiteCursor getBindableItemCursor(Bundle savedInstanceState) {
		m_cursor = QueueManager.getQueueManager().getTasks(TaskCursorSubtype.all);
		return m_cursor;
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
			if (m_db != null)
				m_db.close();
		} catch (Exception e) {/* Ignore */}
		try {
			BookCatalogueApp.getQueueManager().unregisterTaskListener(m_OnTaskChangeListener);					
		} catch (Exception e) {/* Ignore */}
	} 

}
