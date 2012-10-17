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

import java.util.ArrayList;
import java.util.Date;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.ViewTagger;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.QueueManager;
import net.philipwarner.taskqueue.RunnableTask;
import net.philipwarner.taskqueue.Task;
import net.philipwarner.taskqueue.TasksCursor;

/**
 * Base class for tasks in BookCatalogue. This builds and populates simple
 * View objects to display the task.
 * 
 * @author Philip Warner
 */
public abstract class GenericTask extends RunnableTask {
	private static final long serialVersionUID = -5985866222873741455L;

	public GenericTask(String description) {
		super(description);
	}

	/**
	 * Holder class record to maintain task views.
	 * 
	 * @author Philip Warner
	 */
	public class TaskHolder {
		Task task;
		long rowId;
		TextView description;
		TextView state;
		TextView retry_info;
		TextView error;
		TextView job_info;
		CheckBox checkbox;
		Button retry;
	}

	/**
	 * Create a new View
	 */
	@Override
	public View newListItemView(LayoutInflater inflater, Context context, BindableItemSQLiteCursor cursor, ViewGroup parent) {
		View view = inflater.inflate(R.layout.task_info, parent, false);
		ViewTagger.setTag(view, R.id.TAG_TASK, this);
		TaskHolder holder = new TaskHolder();
		holder.task = this;
		holder.rowId = cursor.getId();

		holder.description = (TextView)view.findViewById(R.id.description);
		holder.state = (TextView)view.findViewById(R.id.state);
		holder.retry_info = (TextView)view.findViewById(R.id.retry_info);
		holder.error = (TextView)view.findViewById(R.id.error);
		holder.job_info = (TextView)view.findViewById(R.id.job_info);
		holder.checkbox = (CheckBox)view.findViewById(R.id.checked);
		holder.retry = (Button)view.findViewById(R.id.retry);

		ViewTagger.setTag(view, R.id.TAG_TASK_HOLDER, holder);

		ViewTagger.setTag(holder.checkbox, R.id.TAG_BOOK_EVENT_HOLDER, holder);

		return view;
	}

	/**
	 * Bin task details to passed View
	 */
	@Override
	public boolean bindView(View view, Context context, BindableItemSQLiteCursor bindableCursor, Object appInfo) {
		TaskHolder holder = (TaskHolder)ViewTagger.getTag(view, R.id.TAG_TASK_HOLDER);
		TasksCursor cursor = (TasksCursor)bindableCursor;

		// Update task info binding
		holder.task = this;
		holder.rowId = cursor.getId();

		holder.description.setText(this.getDescription());
		String statusCode = cursor.getStatusCode();
		String statusText = "";
		if (statusCode.equalsIgnoreCase("S")) {
			statusText = context.getString(R.string.completed);
			holder.retry_info.setVisibility(View.GONE);
			holder.retry.setVisibility(View.GONE);
		} else if (statusCode.equalsIgnoreCase("F")) {
			statusText = context.getString(R.string.failed);
			holder.retry_info.setVisibility(View.GONE);
			holder.retry.setVisibility(View.VISIBLE);
		} else if (statusCode.equalsIgnoreCase("Q")) {
			statusText = context.getString(R.string.queued);
			holder.retry_info.setVisibility(View.VISIBLE);
			holder.retry_info.setText(context.getString(R.string.retry_x_of_y_next_at_z, 
							this.getRetries(), this.getRetryLimit(), cursor.getRetryDate().toLocaleString()));

			holder.retry.setVisibility(View.GONE);
		} else {
			holder.retry_info.setVisibility(View.GONE);
			statusText = context.getString(R.string.unknown);
			holder.retry.setVisibility(View.GONE);
		}

		statusText += " (" + cursor.getNoteCount() + " events recorded)";
		holder.state.setText(statusText);

		Exception e = this.getException();
		if (e != null) {
			holder.error.setVisibility(View.VISIBLE);
			holder.error.setText(BookCatalogueApp.getResourceString(R.string.last_error_e, e.getMessage()));
		} else {
			holder.error.setVisibility(View.GONE);
		}
		//"Job ID 123, Queued at 20 Jul 2012 17:50:23 GMT"
		Date qd = cursor.getQueuedDate();
		holder.job_info.setText("Task ID " + this.getId() + ", Queued at " + qd.toLocaleString());
		//view.requestLayout();
		return true;
	}

	/**
	 * Add context menu items:
	 * - Allow task deletion
	 */
	@Override
	public void addContextMenuItems(Context ctx, AdapterView<?> parent, View v,
			int position, final long id, ArrayList<ContextDialogItem> items,
			Object appInfo) {

		items.add( new ContextDialogItem(ctx.getString(R.string.delete_task), new Runnable(){
			@Override
			public void run() {
				QueueManager.getQueueManager().deleteTask(id);
			}})
		);
	}

}
