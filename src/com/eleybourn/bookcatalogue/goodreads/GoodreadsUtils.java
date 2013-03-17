package com.eleybourn.bookcatalogue.goodreads;

import net.philipwarner.taskqueue.QueueManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.BcQueueManager;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;

public class GoodreadsUtils {
	/**
	 * Show the goodreads options list
	 */
	public static void showGoodreadsOptions(final BookCatalogueActivity activity) {
		LayoutInflater inf = activity.getLayoutInflater();
		View root = inf.inflate(R.layout.goodreads_options_list, null);

		final AlertDialog grDialog = new AlertDialog.Builder(activity).setView(root).create();
		grDialog.setTitle(R.string.select_an_action);
		grDialog.show();
		
		/* Goodreads SYNC Link */
		{
			View v = grDialog.findViewById(R.id.sync_with_goodreads_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					GoodreadsUtils.importAllFromGoodreads(activity, true);
					grDialog.dismiss();
				}
			});
		}

		/* Goodreads IMPORT Link */
		{
			View v = grDialog.findViewById(R.id.import_all_from_goodreads_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					GoodreadsUtils.importAllFromGoodreads(activity, false);
					grDialog.dismiss();
				}
			});
		}

		/* Goodreads EXPORT Link */
		{
			View v = grDialog.findViewById(R.id.send_books_to_goodreads_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					sendBooksToGoodreads(activity);
					grDialog.dismiss();
				}
			});
		}
	}
	
	/**
	 * Start a background task that imports books from goodreads.
	 * 
	 * We use a FragmentTask so that network access does not occur in the UI thread.
	 */
	public static void importAllFromGoodreads(final BookCatalogueActivity context, final boolean isSync) {
		
		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {

				if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_IMPORT_ALL)) {
					fragment.showToast(R.string.requested_task_is_already_queued);
					return;
				}
				if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_EXPORT_ALL)) {
					fragment.showToast(R.string.export_task_is_already_queued);
					return;
				}

				int msg = checkGoodreadsAuth();
				if (msg == -1) {
					fragment.post(new Runnable() {

						@Override
						public void run() {
							StandardDialogs.goodreadsAuthAlert(context);
						}});
					return;
				} else if (msg != 0) {
					fragment.showToast(msg);
					return;
				}

				if (!fragment.isCancelled()) {
					QueueManager.getQueueManager().enqueueTask(new ImportAllTask(isSync), BcQueueManager.QUEUE_MAIN, 0);
					fragment.showToast(R.string.task_has_been_queued_in_background);					
				}
			}
		};
		SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.connecting_to_web_site, task, true, 0);
	}

	/**
	 * Check that goodreads is authorized for this app, and optionally allow user to request auth or more info
	 * 
	 * This does network comms and should not be called in the UI thread.
	 * 
	 * @return	Flag indicating OK
	 */
	private static int checkGoodreadsAuth() {
		// Make sure GR is authorized for this app
		GoodreadsManager grMgr = new GoodreadsManager();

		if (!GoodreadsManager.hasCredentials() || !grMgr.hasValidCredentials()) {
			return -1;
		}

		return 0;		
	}
	
	/**
	 * Check that no other sync-related jobs are queued, and that goodreads is authorized for this app.
	 * 
	 * This does network comms and should not be called in the UI thread.
	 * 
	 * @return	Flag indicating OK
	 */
	private static int checkCanSendToGoodreads() {
		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_EXPORT_ALL)) {
			return R.string.requested_task_is_already_queued;
		}
		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_IMPORT_ALL)) {
			return R.string.import_task_is_already_queued;
		}

		return checkGoodreadsAuth();
	}

	/**
	 * Start a background task that exports all books to goodreads.
	 */
	private static void sendToGoodreads(final FragmentActivity context, final boolean updatesOnly) {
		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				int msg = checkCanSendToGoodreads();
				if (msg == 0) {
					QueueManager.getQueueManager().enqueueTask(new SendAllBooksTask(updatesOnly), BcQueueManager.QUEUE_MAIN, 0);
					msg = R.string.task_has_been_queued_in_background;
				}
				setState(msg);
			}

			@Override
			public void onFinish(final SimpleTaskQueueProgressFragment fragment, Exception exception) {
				final int msg = getState();
				if (msg == -1) {
					fragment.post(new Runnable() {

						@Override
						public void run() {
							StandardDialogs.goodreadsAuthAlert(fragment.getActivity());
						}
					});
					return;
				} else {
					fragment.showToast(msg);
				}

			}
		};
		SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.connecting_to_web_site, task, true, 0);
	}
	
	/**
	 * Ask the user which books to send, then send them.
	 * 
	 * Optionally, display a dialog warning the user that goodreads authentication is required; gives them
	 * the options: 'request now', 'more info' or 'cancel'.
	 */
	public static void sendBooksToGoodreads(final BookCatalogueActivity ctx) {

		FragmentTaskAbstract task = new FragmentTaskAbstract() {
			/**
			 * Just check we can send. If so, onFinish() will be called.
			 */
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				int msg = GoodreadsUtils.checkCanSendToGoodreads();
				setState(msg);
			}

			@Override
			public void onFinish(final SimpleTaskQueueProgressFragment fragment, Exception exception) {
				if (getState() == 0) {
					final FragmentActivity context = fragment.getActivity();
					if (context != null) {
						// Get the title		
						final AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle(R.string.send_books_to_goodreads).setMessage(R.string.send_books_to_goodreads_blurb).create();
		
						alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
						alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getResources().getString(R.string.send_updated), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								alertDialog.dismiss();
								GoodreadsUtils.sendToGoodreads(context, true);
							}
						});
						
						alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getResources().getString(R.string.send_all), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								alertDialog.dismiss();
								GoodreadsUtils.sendToGoodreads(context, false);
							}
						});
		
						alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								alertDialog.dismiss();
							}
						}); 
		
						alertDialog.show();						
					}
				} else if (getState() == -1) {
					fragment.post(new Runnable() {

						@Override
						public void run() {
							StandardDialogs.goodreadsAuthAlert(fragment.getActivity());
						}
					});
					return;
				} else {
					fragment.showToast(getState());
				}
			}
		};
		// Run the task
		SimpleTaskQueueProgressFragment.runTaskWithProgress(ctx, R.string.connecting_to_web_site, task, true, 0);

	}

}
