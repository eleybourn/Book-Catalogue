package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.LegacyEvent;
import net.philipwarner.taskqueue.LegacyTask;
import net.philipwarner.taskqueue.QueueManager;

/**
 * BookCatalogue implementation of QueueManager.
 * 
 * This just implements the application-specific (and localized) versions of basic
 * QueueManager objects.
 * 
 * @author Grunthos
 */
public class BcQueueManager extends QueueManager {
	public static final String QUEUE_MAIN = "main";
	public static final String QUEUE_SMALL_JOBS = "small_jobs";

	/**
	 * Create the queue we need, if they do not already exist.
	 * 
	 * main: long-running tasks, or tasks that can just wait
	 * small_jobs: trivial background tasks that will only take a few seconds.
	 */
	@Override
    public void onCreate() {
		super.onCreate();

		initializeQueue(QUEUE_MAIN);
		initializeQueue(QUEUE_SMALL_JOBS);
	}

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
	 * @author Grunthos
	 *
	 */
	public class BcLegacyEvent extends LegacyEvent {
		private static final long serialVersionUID = 1992740024689009867L;

		public BcLegacyEvent(byte[] original) {
			super(original, "Legacy Event");
		}

		@Override
		public void addContextMenuItems(Context ctx, AdapterView<?> parent,
				View v, int position, long id,
				ArrayList<ContextDialogItem> items,
				Object appInfo) {

			items.add(new ContextDialogItem(ctx.getString(R.string.delete_entry), new Runnable() {
				@Override
				public void run() {
					QueueManager.getQueueManager().deleteEvent(BcLegacyEvent.this.getId());
				}}));

		}		
	}

	/**
	 * The only reason that this class has to be implemented in the client application is
	 * so that the call to addContextMenuItems(...) can return a LOCALIZED context menu.
	 * 
	 * @author Grunthos
	 *
	 */
	public class BcLegacyTask extends LegacyTask {
		private static final long serialVersionUID = 164669981603757736L;

		public BcLegacyTask(byte[] original, String description) {
			super(original, description);
		}

		@Override
		public void addContextMenuItems(Context ctx, AdapterView<?> parent,
				View v, int position, long id,
				ArrayList<ContextDialogItem> items,
				Object appInfo) {

			items.add(new ContextDialogItem(ctx.getString(R.string.delete_entry), new Runnable() {
				@Override
				public void run() {
					QueueManager.getQueueManager().deleteEvent(BcLegacyTask.this.getId());
				}}));

		}

		@Override
		public String getDescription() {
			return BookCatalogueApp.getResourceString(R.string.unrecognized_task);
		}
	}

	/**
	 * Return a localized current LegacyTask object.
	 */
	@Override
	public LegacyTask newLegacyTask(byte[] original) {
		return new BcLegacyTask(original, BookCatalogueApp.context.getResources().getString(R.string.legacy_task));
	}
}
