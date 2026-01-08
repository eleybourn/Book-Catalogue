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

import java.util.ArrayList;

import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.LegacyEvent;
import net.philipwarner.taskqueue.LegacyTask;
import net.philipwarner.taskqueue.QueueManager;
import android.content.Context;
import android.view.View;
import android.widget.AdapterView;

/**
 * BookCatalogue implementation of QueueManager.
 * This just implements the application-specific (and localized) versions of basic
 * QueueManager objects.
 * 
 * @author Philip Warner
 */
public class BcQueueManager extends QueueManager {

	public BcQueueManager() {
		super(BookCatalogueApp.context);
		initializeQueue(QUEUE_MAIN);
		initializeQueue(QUEUE_SMALL_JOBS);
	}

	/**
	 * Create the queue we need, if they do not already exist.
	 * main: long-running tasks, or tasks that can just wait
	 * small_jobs: trivial background tasks that will only take a few seconds.
	 */
	public BcQueueManager(Context context) {
		super(context);
		initializeQueue(QUEUE_MAIN);
		initializeQueue(QUEUE_SMALL_JOBS);
	}

	public static final String QUEUE_MAIN = "main";
	public static final String QUEUE_SMALL_JOBS = "small_jobs";

	public static final long CAT_LEGACY = 1;

    /**
	 * Return a localized LegacyEvent for the passed blob.
	 * This method is used when deserialization fails, most likely as a result of changes
	 * to the underlying serialized class.
	 */
	@Override
	public LegacyEvent newLegacyEvent(byte[] original) {
		return new BcLegacyEvent(original);
	}

	/**
	 * The only reason that this class has to be implemented in the client application is
	 * so that the call to addContextMenuItems(...) can return a LOCALIZED context menu.
	 * 
	 * @author Philip Warner
	 *
	 */
	public static class BcLegacyEvent extends LegacyEvent {
		private static final long serialVersionUID = 1992740024689009867L;

		public BcLegacyEvent(byte[] original) {
			super(original, "Legacy Event");
		}

		@Override
		public void addContextMenuItems(Context ctx, AdapterView<?> parent,
				View v, int position, long id,
				ArrayList<ContextDialogItem> items,
				Object appInfo) {

			items.add(new ContextDialogItem(ctx.getString(R.string.delete_event), () -> QueueManager.getQueueManager().deleteEvent(BcLegacyEvent.this.getId())));

		}		
	}

	/**
	 * The only reason that this class has to be implemented in the client application is
	 * so that the call to addContextMenuItems(...) can return a LOCALIZED context menu.
	 * 
	 * @author Philip Warner
	 *
	 */
	public static class BcLegacyTask extends LegacyTask {
		private static final long serialVersionUID = 164669981603757736L;

		public BcLegacyTask(byte[] original, String description) {
			super(original, description);
		}

		@Override
		public void addContextMenuItems(Context ctx, AdapterView<?> parent,
				View v, int position, long id,
				ArrayList<ContextDialogItem> items,
				Object appInfo) {

			items.add(new ContextDialogItem(ctx.getString(R.string.delete_task), () -> QueueManager.getQueueManager().deleteTask(BcLegacyTask.this.getId())));

		}

		@Override
		public String getDescription() {
			return BookCatalogueApp.getResourceString(R.string.unrecognized_task);
		}

		@Override
		public long getCategory() {
			return CAT_LEGACY;
		}
	}

	/**
	 * Return a localized current LegacyTask object.
	 */
	@Override
	public LegacyTask newLegacyTask(byte[] original) {
		return new BcLegacyTask(original, BookCatalogueApp.context.getResources().getString(R.string.legacy_task));
	}

	@Override
	public Context getApplicationContext() {
		return BookCatalogueApp.context;
	}
}
