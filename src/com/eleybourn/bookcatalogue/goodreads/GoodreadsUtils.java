package com.eleybourn.bookcatalogue.goodreads;

import net.philipwarner.taskqueue.QueueManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.AdministrationFunctions;
import com.eleybourn.bookcatalogue.BcQueueManager;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

public class GoodreadsUtils {
	/**
	 * Show the goodreads options list
	 */
	public static void showGoodreadsOptions(final Activity activity) {
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
	 */
	public static void importAllFromGoodreads(Context context, boolean isSync) {

		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_IMPORT_ALL)) {
			Toast.makeText(context, R.string.requested_task_is_already_queued, Toast.LENGTH_LONG).show();
			return;
		}
		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_EXPORT_ALL)) {
			Toast.makeText(context, R.string.export_task_is_already_queued, Toast.LENGTH_LONG).show();
			return;
		}

		if (!checkGoodreadsAuth(context))
			return;

		QueueManager.getQueueManager().enqueueTask(new ImportAllTask(isSync), BcQueueManager.QUEUE_MAIN, 0);
		Toast.makeText(context, R.string.task_has_been_queued_in_background, Toast.LENGTH_LONG).show();
	}

	/**
	 * Check that goodreads is authorized for this app, and optionally allow user to request auth or more info
	 * 
	 * @return	Flag indicating OK
	 */
	private static boolean checkGoodreadsAuth(Context context) {
		// Make sure GR is authorized for this app
		GoodreadsManager grMgr = new GoodreadsManager();

		if (!grMgr.hasCredentials()) {
			StandardDialogs.goodreadsAuthAlert(context);
			return false;
		}

		if (!grMgr.hasValidCredentials()) {
			StandardDialogs.goodreadsAuthAlert(context);
			return false;
		}

		return true;		
	}
	
	/**
	 * Check that no other sync-related jobs are queued, and that goodreads is authorized for this app
	 * 
	 * @return	Flag indicating OK
	 */
	public static boolean checkCanSendToGoodreads(Context context) {
		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_EXPORT_ALL)) {
			Toast.makeText(context, R.string.requested_task_is_already_queued, Toast.LENGTH_LONG).show();
			return false;
		}
		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_GOODREADS_IMPORT_ALL)) {
			Toast.makeText(context, R.string.import_task_is_already_queued, Toast.LENGTH_LONG).show();
			return false;
		}

		return checkGoodreadsAuth(context);
	}

	/**
	 * Start a background task that exports all books to goodreads.
	 */
	public static void sendToGoodreads(Context context, boolean updatesOnly) {

		if (!checkCanSendToGoodreads(context))
			return;

		QueueManager.getQueueManager().enqueueTask(new SendAllBooksTask(updatesOnly), BcQueueManager.QUEUE_MAIN, 0);
		Toast.makeText(context, R.string.task_has_been_queued_in_background, Toast.LENGTH_LONG).show();
	}
	
	/**
	 * Display a dialog warning the user that goodreads authentication is required; gives them
	 * the options: 'request now', 'more info' or 'cancel'.
	 */
	public static void sendBooksToGoodreads(final Context context) {

		if (!GoodreadsUtils.checkCanSendToGoodreads(context))
			return;

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

}
