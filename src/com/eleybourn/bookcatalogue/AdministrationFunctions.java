/*
 * @copyright 2010 Evan Leybourn
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment.OnMessageDialogResultListener;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument;
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument;
import androidx.documentfile.provider.DocumentFile;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class AdministrationFunctions extends ActivityWithTasks
		implements OnMessageDialogResultListener,
				   OnImportTypeSelectionDialogResultListener,
				   OnExportTypeSelectionDialogResultListener
{
	private CatalogueDBAdapter mDbHelper;
	private boolean finish_after = false;
	private boolean mExportOnStartup = false;

	public static final String DOAUTO = "do_auto";

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		registerOldFilesTreeCopyLauncher();
		registerBackupExportPickerLauncher(ID.DIALOG_OPEN_IMPORT_TYPE);
		registerBackupImportPickerLauncher();
		try {
			super.onCreate(savedInstanceState);
			setTitle(R.string.administration_label);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			setContentView(R.layout.administration_functions);
			Bundle extras = getIntent().getExtras();
			if (extras != null && extras.containsKey(DOAUTO)) {
				try {
					if (extras.getString(DOAUTO,"").equals("export")) {
						finish_after = true;
						mExportOnStartup = true;
					} else {
						throw new RuntimeException("Unsupported DOAUTO option");
					}
				} catch (NullPointerException e) {
					Logger.logError(e);
				}				
			}
			setupAdmin();
			Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * This function builds the Administration page in 4 sections. 
	 * 1. The button to goto the manage bookshelves activity
	 * 2. The button to export the database
	 * 3. The button to import the exported file into the database
	 * 4. The application version and link details
	 * 5. The link to paypal for donation
	 */
	public void setupAdmin() {
		/* Bookshelf Link */
		View bookshelf = findViewById(R.id.bookshelf_label);
		// Make line flash when clicked.
		bookshelf.setBackgroundResource(android.R.drawable.list_selector_background);
		bookshelf.setOnClickListener(v -> manageBookselves());
		
		/* Manage Fields Link */
		View fields = findViewById(R.id.fields_label);
		// Make line flash when clicked.
		fields.setBackgroundResource(android.R.drawable.list_selector_background);
		fields.setOnClickListener(v -> manageFields());
		
		/* Export Link */
		View export = findViewById(R.id.export_label);
		// Make line flash when clicked.
		export.setBackgroundResource(android.R.drawable.list_selector_background);
		export.setOnClickListener(v -> launchCsvExportPicker());
		
		/* Import Link */
		View imports = findViewById(R.id.import_label);
		// Make line flash when clicked.
		imports.setBackgroundResource(android.R.drawable.list_selector_background);
		imports.setOnClickListener(
				v -> {
					// Verify - this can be a dangerous operation
					AlertDialog alertDialog = new AlertDialog.Builder(AdministrationFunctions.this).setMessage(R.string.import_alert).create();
					alertDialog.setTitle(R.string.import_data);
					alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
					alertDialog.setButton(
							AlertDialog.BUTTON_POSITIVE,
							AdministrationFunctions.this.getResources().getString(R.string.ok),
							(dialog, which) -> {
								launchCsvImportPicker();
								//Toast.makeText(pthis, importUpdated + " Existing, " + importCreated + " Created", Toast.LENGTH_LONG).show();
							});
					alertDialog.setButton(
							AlertDialog.BUTTON_NEGATIVE,
							AdministrationFunctions.this.getResources().getString(R.string.cancel),
							(dialog, which) -> {
								//do nothing
							});
					alertDialog.show();
				});

		///* Goodreads SYNC Link */
		//{
		//	View v = findViewById(R.id.sync_with_goodreads_label);
		//	// Make line flash when clicked.
		//	v.setBackgroundResource(android.R.drawable.list_selector_background);
		//	v.setOnClickListener(v1 -> GoodreadsUtils.importAllFromGoodreads(AdministrationFunctions.this, true));
		//}
		//
		///* Goodreads IMPORT Link */
		//{
		//	View v = findViewById(R.id.import_all_from_goodreads_label);
		//	// Make line flash when clicked.
		//	v.setBackgroundResource(android.R.drawable.list_selector_background);
		//	v.setOnClickListener(v12 -> GoodreadsUtils.importAllFromGoodreads(AdministrationFunctions.this, false));
		//}
		//
		///* Goodreads EXPORT Link */
		//{
		//	View v = findViewById(R.id.send_books_to_goodreads_label);
		//	// Make line flash when clicked.
		//	v.setBackgroundResource(android.R.drawable.list_selector_background);
		//	v.setOnClickListener(v13-> GoodreadsUtils.sendBooksToGoodreads(AdministrationFunctions.this));
		//}
		//
		///* LibraryThing auth Link */
		//View ltAuth = findViewById(R.id.librarything_auth);
		//// Make line flash when clicked.
		//ltAuth.setBackgroundResource(android.R.drawable.list_selector_background);
		//ltAuth.setOnClickListener(v -> {
		//	Intent i = new Intent(AdministrationFunctions.this, AdministrationLibraryThing.class);
		//	startActivity(i);
		//});
		//
		///* Goodreads auth Link */
		//View grAuth = findViewById(R.id.goodreads_auth);
		//// Make line flash when clicked.
		//grAuth.setBackgroundResource(android.R.drawable.list_selector_background);
		//grAuth.setOnClickListener(v -> {
		//	Intent i = new Intent(AdministrationFunctions.this, GoodreadsRegister.class);
		//	startActivity(i);
		//});

		/* Other Prefs Link */
		View otherPrefs = findViewById(R.id.other_prefs_label);
		// Make line flash when clicked.
		otherPrefs.setBackgroundResource(android.R.drawable.list_selector_background);
		otherPrefs.setOnClickListener(v -> {
			Intent i = new Intent(AdministrationFunctions.this, OtherPreferences.class);
			startActivity(i);
		});

		/* Book List Preferences Link */
		View blPrefs = findViewById(R.id.booklist_preferences_label);
		// Make line flash when clicked.
		blPrefs.setBackgroundResource(android.R.drawable.list_selector_background);
		blPrefs.setOnClickListener(v -> BookCatalogueApp.startPreferencesActivity(AdministrationFunctions.this));
		
		// Edit Book list styles
		{
			View lbl = findViewById(R.id.edit_styles_label);
			// Make line flash when clicked.
			lbl.setBackgroundResource(android.R.drawable.list_selector_background);
			lbl.setOnClickListener(v -> BooklistStyles.startEditActivity(AdministrationFunctions.this));
		}
		
		{
			/* Update Fields Link */
			View thumb = findViewById(R.id.thumb_label);
			// Make line flash when clicked.
			thumb.setBackgroundResource(android.R.drawable.list_selector_background);
			thumb.setOnClickListener(v -> updateThumbnails());
		}

		{
			// Debug ONLY!
			/* Backup Link */
			View backup = findViewById(R.id.backup_label);
			// Make line flash when clicked.
			backup.setBackgroundResource(android.R.drawable.list_selector_background);
			backup.setOnClickListener(v -> {
				mDbHelper.backupDbFile();
				Toast.makeText(AdministrationFunctions.this, R.string.backup_success, Toast.LENGTH_LONG).show();
			});

		}

		{
			/* Import old files */
			View imp = findViewById(R.id.import_old_files_label);
			// Make line flash when clicked.
			imp.setBackgroundResource(android.R.drawable.list_selector_background);
			imp.setOnClickListener(v -> startImportOldFiles());
		}

		//{
		//	/* Tasks setup Link */
		//	View tasks = findViewById(R.id.background_tasks_label);
		//	// Make line flash when clicked.
		//	tasks.setBackgroundResource(android.R.drawable.list_selector_background);
		//	tasks.setOnClickListener(v -> showBackgroundTasks());
		//}

		{
			/* Reset Hints Link */
			View hints = findViewById(R.id.reset_hints_label);
			// Make line flash when clicked.
			hints.setBackgroundResource(android.R.drawable.list_selector_background);
			hints.setOnClickListener(v -> {
				HintManager.resetHints();
				Toast.makeText(AdministrationFunctions.this, R.string.hints_have_been_reset, Toast.LENGTH_LONG).show();
			});
		}

		// Erase cover cache
		{
			View erase = findViewById(R.id.erase_cover_cache_label);
			// Make line flash when clicked.
			erase.setBackgroundResource(android.R.drawable.list_selector_background);
			erase.setOnClickListener(v -> {
				Utils utils = new Utils();
				try {
					utils.eraseCoverCache();
				} finally {
					utils.close();
				}
			});
		}
		{
			/* Backup Catalogue Link */
			View backup = findViewById(R.id.backup_catalogue_label);
			// Make line flash when clicked.
			backup.setBackgroundResource(android.R.drawable.list_selector_background);
			backup.setOnClickListener(v -> launchBackupExport());
		}
		{
			/* Restore Catalogue Link */
			View restore = findViewById(R.id.restore_catalogue_label);
			// Make line flash when clicked.
			restore.setBackgroundResource(android.R.drawable.list_selector_background);
			restore.setOnClickListener(v -> launchBackupImport());
		}
	}

	///**
	// * Show the activity that displays all Event objects created by the QueueManager.
	// */
	//private void showEvents() {
	//	Intent i = new Intent(this, GoodreadsExportFailuresActivity.class);
	//	startActivity(i);
	//}

	/**
	 * Load the Bookshelf Activity
	 */
	private void manageBookselves() {
		Intent i = new Intent(this, Bookshelf.class);
		startActivity(i);
	}
	
	/**
	 * Load the Manage Field Visibility Activity
	 */
	private void manageFields() {
		Intent i = new Intent(this, FieldVisibility.class);
		startActivity(i);
	}
	

	/**
	 * Export all data to a CSV file
	 */
	private void exportData(DocumentFile file) {
		ExportThread thread = new ExportThread(getTaskManager(), file);
		thread.start();
	}

	/**
	 * Import all data from the passed CSV file spec
	 */
	private void importData(DocumentFile f) {
		ImportThread thread;
		try {
			thread = new ImportThread(getTaskManager(), f);
		} catch (IOException e) {
			Logger.logError(e);
			Toast.makeText(this, getString(R.string.problem_starting_import_arg, e.getMessage()), Toast.LENGTH_LONG).show();
			return;
		}
		thread.start();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	} 

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
		//if (mExportOnStartup)
		//	exportData();
	}

	/**
	 * Called when any background task completes
	 */
	@Override
	public void onTaskEnded(ManagedTask task) {
		// If it's an export, then handle it
		if (task instanceof ExportThread) {
			onExportFinished((ExportThread)task);
		}
	}

	public void onExportFinished(final ExportThread task) {
		if (task.isCancelled()) {
			if (finish_after)
				finish();
			return;
		}
		AlertDialog alertDialog = new AlertDialog.Builder(AdministrationFunctions.this).create();
		alertDialog.setTitle(R.string.email_export);
		alertDialog.setIcon(android.R.drawable.ic_menu_send);
		alertDialog.setButton(
				DialogInterface.BUTTON_NEGATIVE,
				getResources().getString(R.string.ok),
				(dialog, which) -> {
					// setup the mail message
					final Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
					emailIntent.setType("plain/text");
					//emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, context.getString(R.string.debug_email).split(";"));
					String subject = "[" + getString(R.string.app_name) + "] " + getString(R.string.export_to_csv);
					emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
					//emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.debug_body));
					//has to be an ArrayList
					ArrayList<Uri> uris = new ArrayList<>();
					// Find all files of interest to send
					try {
						uris.add(task.getFile().getUri());
						// Send it, if there are any files to send.
						emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
						startActivity(Intent.createChooser(emailIntent, "Send mail..."));
					} catch (NullPointerException e) {
						Logger.logError(e);
						Toast.makeText(AdministrationFunctions.this, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
					}

					dialog.dismiss();
				});
		alertDialog.setButton(
				DialogInterface.BUTTON_POSITIVE,
				getResources().getString(R.string.cancel),
				(dialog, which) -> {
					//do nothing
					dialog.dismiss();
				});

		alertDialog.setOnDismissListener(
				dialog -> {
					if (finish_after)
						finish();
				});

		if (!isFinishing()) {
			try {
				//
				// Catch errors resulting from 'back' being pressed multiple times so that the activity is destroyed
				// before the dialog can be shown.
				// See http://code.google.com/p/android/issues/detail?id=3953
				//
				alertDialog.show();				
			} catch (Exception e) {
				Logger.logError(e);
			}
		}
	}

	/**
	 * Update all (non-existent) thumbnails
	 * <p>
	 * There is a current limitation that restricts the search to only books
	 * with an ISBN
	 */
	private void updateThumbnails() {
		Intent i = new Intent(this, UpdateFromInternet.class);
		startActivity(i);
	}

	///**
	// * Start the activity that shows the basic details of background tasks.
	// */
	//private void showBackgroundTasks() {
	//	Intent i = new Intent(this, TaskListActivity.class);
	//	startActivity(i);
	//}

	private final ActivityResultLauncher<String> mCsvExportPickerLauncher = registerForActivityResult(
			new CreateDocument("*/*"),
			result -> {
				if (result != null) {
					DocumentFile f = DocumentFile.fromSingleUri(AdministrationFunctions.this, result);
					if (f != null) {
						mBackupFile = f;
						exportData(f);
					}
				}
			}
	);

	ActivityResultLauncher<String[]> mCsvImportPickerLauncher = registerForActivityResult(
			new OpenDocument(),
			result -> {
				if (result != null) {
					DocumentFile f = DocumentFile.fromSingleUri(AdministrationFunctions.this, result);
					if (f != null) {
						mBackupFile = f;
						importData(f);
					}
				}
			}
	);

	/**
	 * Call the input picker.
	 */
	private void launchCsvImportPicker() {
		mCsvImportPickerLauncher.launch(new String[] {"*/*"});
	}

	/**
	 * Call the output picker.
	 */
	private void launchCsvExportPicker() {
		mCsvExportPickerLauncher.launch("Export.csv");
	}

	@Override
	public void onImportTypeSelectionDialogResult(int dialogId, ImportTypeSelectionDialogFragment dialog, int rowId, DocumentFile file) {
		mBackupImportManager.onImportTypeSelectionDialogResult(dialogId, dialog, rowId, file);
	}

	/**
	 * Pass on the event to the relevant handler.
	 * @param dialogId	As passed to us
	 * @param dialog	As passed to us
	 * @param settings	As passed to us
	 */
	@Override
	public void onExportTypeSelectionDialogResult(int dialogId, BookCatalogueDialogFragment dialog, ExportSettings settings) {
		mBackupExportManager.onExportTypeSelectionDialogResult(dialogId, dialog, settings);
	}

	/**
	 * Pass on the event to the relevant handler.
	 * @param dialogId	As passed to us
	 * @param dialog	As passed to us
	 * @param button	As passed to us
	 */
	@Override
	public void onMessageDialogResult(int dialogId, MessageDialogFragment dialog, int button) {
		// Do nothing. We just need this so we can display message dialogs.
		super.onMessageDialogResult(dialogId, dialog, button);
	}
}
