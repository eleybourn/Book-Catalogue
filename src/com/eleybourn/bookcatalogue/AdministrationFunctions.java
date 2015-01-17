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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogFileItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.filechooser.BackupChooser;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsRegister;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class AdministrationFunctions extends ActivityWithTasks {
	private static final int ACTIVITY_BOOKSHELF=1;
	private static final int ACTIVITY_FIELD_VISIBILITY=2;
	private CatalogueDBAdapter mDbHelper;
	private boolean finish_after = false;
	private boolean mExportOnStartup = false;

	public static final String DOAUTO = "do_auto";

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setTitle(R.string.administration_label);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			setContentView(R.layout.administration_functions);
			Bundle extras = getIntent().getExtras();
			if (extras != null && extras.containsKey(DOAUTO)) {
				try {
					if (extras.getString(DOAUTO).equals("export")) {
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
		bookshelf.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				manageBookselves();
				return;
			}
		});
		
		/* Manage Fields Link */
		View fields = findViewById(R.id.fields_label);
		// Make line flash when clicked.
		fields.setBackgroundResource(android.R.drawable.list_selector_background);
		fields.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				manageFields();
				return;
			}
		});
		
		/* Export Link */
		View export = findViewById(R.id.export_label);
		// Make line flash when clicked.
		export.setBackgroundResource(android.R.drawable.list_selector_background);
		export.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				exportData();
				return;
			}
		});
		
		/* Import Link */
		View imports = findViewById(R.id.import_label);
		// Make line flash when clicked.
		imports.setBackgroundResource(android.R.drawable.list_selector_background);
		imports.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Verify - this can be a dangerous operation
				AlertDialog alertDialog = new AlertDialog.Builder(AdministrationFunctions.this).setMessage(R.string.import_alert).create();
				alertDialog.setTitle(R.string.import_data);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, AdministrationFunctions.this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						importData();
						//Toast.makeText(pthis, importUpdated + " Existing, " + importCreated + " Created", Toast.LENGTH_LONG).show();
						return;
					}
				}); 
				alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, AdministrationFunctions.this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
						return;
					}
				}); 
				alertDialog.show();
				return;
			}
		});

		/* Goodreads SYNC Link */
		{
			View v = findViewById(R.id.sync_with_goodreads_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					GoodreadsUtils.importAllFromGoodreads(AdministrationFunctions.this, true);
					return;
				}
			});
		}

		/* Goodreads IMPORT Link */
		{
			View v = findViewById(R.id.import_all_from_goodreads_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					GoodreadsUtils.importAllFromGoodreads(AdministrationFunctions.this, false);
					return;
				}
			});
		}

		/* Goodreads EXPORT Link */
		{
			View v = findViewById(R.id.send_books_to_goodreads_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					GoodreadsUtils.sendBooksToGoodreads(AdministrationFunctions.this);
					return;
				}
			});
		}

		/* LibraryThing auth Link */
		View ltAuth = findViewById(R.id.librarything_auth);
		// Make line flash when clicked.
		ltAuth.setBackgroundResource(android.R.drawable.list_selector_background);
		ltAuth.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(AdministrationFunctions.this, AdministrationLibraryThing.class);
				startActivity(i);
				return;
			}
		});

		/* Goodreads auth Link */
		View grAuth = findViewById(R.id.goodreads_auth);
		// Make line flash when clicked.
		grAuth.setBackgroundResource(android.R.drawable.list_selector_background);
		grAuth.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(AdministrationFunctions.this, GoodreadsRegister.class);
				startActivity(i);
				return;
			}
		});

		/* Other Prefs Link */
		View otherPrefs = findViewById(R.id.other_prefs_label);
		// Make line flash when clicked.
		otherPrefs.setBackgroundResource(android.R.drawable.list_selector_background);
		otherPrefs.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(AdministrationFunctions.this, OtherPreferences.class);
				startActivity(i);
				return;
			}
		});

		/* Book List Preferences Link */
		View blPrefs = findViewById(R.id.booklist_preferences_label);
		// Make line flash when clicked.
		blPrefs.setBackgroundResource(android.R.drawable.list_selector_background);
		blPrefs.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				BookCatalogueApp.startPreferencesActivity(AdministrationFunctions.this);
				return;
			}
		});
		
		// Edit Book list styles
		{
			View v = findViewById(R.id.edit_styles_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					BooklistStyles.startEditActivity(AdministrationFunctions.this);
				}
			});
		}
		
		{
			/* Update Fields Link */
			View thumb = findViewById(R.id.thumb_label);
			// Make line flash when clicked.
			thumb.setBackgroundResource(android.R.drawable.list_selector_background);
			thumb.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					updateThumbnails();
					return;
				}
			});
		}

		{
			// Debug ONLY!
			/* Backup Link */
			View backup = findViewById(R.id.backup_label);
			// Make line flash when clicked.
			backup.setBackgroundResource(android.R.drawable.list_selector_background);
			backup.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mDbHelper.backupDbFile();
					Toast.makeText(AdministrationFunctions.this, R.string.backup_success, Toast.LENGTH_LONG).show();
					return;
				}
			});

		}

		{
			/* Tasks setup Link */
			View v = findViewById(R.id.background_tasks_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					showBackgroundTasks();
					return;
				}
			});
		}

		{
			/* Reset Hints Link */
			View hints = findViewById(R.id.reset_hints_label);
			// Make line flash when clicked.
			hints.setBackgroundResource(android.R.drawable.list_selector_background);
			hints.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					HintManager.resetHints();
					Toast.makeText(AdministrationFunctions.this, R.string.hints_have_been_reset, Toast.LENGTH_LONG).show();
					return;
				}
			});
		}

		// Erase cover cache
		{
			View v = findViewById(R.id.erase_cover_cache_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Utils utils = new Utils();
					try {
						utils.eraseCoverCache();
					} finally {
						utils.close();
					}
					return;
				}
			});
		}
		{
			/* Backup Catalogue Link */
			View v = findViewById(R.id.backup_catalogue_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					backupCatalogue(AdministrationFunctions.this);
					return;
				}
			});
		}
		{
			/* Restore Catalogue Link */
			View v = findViewById(R.id.restore_catalogue_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					restoreCatalogue();
					return;
				}
			});
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
		startActivityForResult(i, ACTIVITY_BOOKSHELF);
	}
	
	/**
	 * Load the Manage Field Visibility Activity
	 */
	private void manageFields() {
		Intent i = new Intent(this, FieldVisibility.class);
		startActivityForResult(i, ACTIVITY_FIELD_VISIBILITY);
	}
	

	/**
	 * Export all data to a CSV file
	 * 
	 * return void
	 */
	public void exportData() {
		ExportThread thread = new ExportThread(getTaskManager());
		thread.start();
	}

	/**
	 * Import all data from somewhere on shared storage; ask user to disambiguate if necessary
	 * 
	 * return void
	 */
	private void importData() {
		// Find all possible files (CSV in bookCatalogue directory)
		ArrayList<File> files = StorageUtils.findExportFiles();
		// If none, exit with message
		if (files == null || files.size() == 0) {
			Toast.makeText(this, R.string.no_export_files_found, Toast.LENGTH_LONG).show();
			return;
		} else {
			if (files.size() == 1) {
				// If only 1, just use it
				importData(files.get(0).getAbsolutePath());
			} else {
				// If more than one, ask user which file
				// ENHANCE: Consider asking about importing cover images.
				StandardDialogs.selectFileDialog(getLayoutInflater(), getString(R.string.more_than_one_export_file_blah), files, new SimpleDialogOnClickListener() {
					@Override
					public void onClick(SimpleDialogItem item) {
						SimpleDialogFileItem fileItem = (SimpleDialogFileItem) item;
						importData(fileItem.getFile().getAbsolutePath());
					}});
			}				
		}
	}

	/**
	 * Import all data from the passed CSV file spec
	 * 
	 * return void
	 * @throws IOException 
	 */
	private void importData(String filespec) {
		ImportThread thread;
		try {
			thread = new ImportThread(getTaskManager(), filespec);
		} catch (IOException e) {
			Logger.logError(e);
			Toast.makeText(this, getString(R.string.problem_starting_import_arg, e.getMessage()), Toast.LENGTH_LONG).show();
			return;
		}
		thread.start();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ACTIVITY_BOOKSHELF:
		case ACTIVITY_FIELD_VISIBILITY:
			//do nothing (yet)
			break;
		}
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
		if (mExportOnStartup)
			exportData();
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

	public void onExportFinished(ExportThread task) {
		if (task.isCancelled()) {
			if (finish_after)
				finish();
			return;
		}

		AlertDialog alertDialog = new AlertDialog.Builder(AdministrationFunctions.this).create();
		alertDialog.setTitle(R.string.email_export);
		alertDialog.setIcon(android.R.drawable.ic_menu_send);
		alertDialog.setButton2(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// setup the mail message
				final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
				emailIntent.setType("plain/text");
				//emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, context.getString(R.string.debug_email).split(";"));
				String subject = "[" + getString(R.string.app_name) + "] " + getString(R.string.export_to_csv);
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
				//emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.debug_body));
				//has to be an ArrayList
				ArrayList<Uri> uris = new ArrayList<Uri>();
				// Find all files of interest to send
				try {
					File fileIn = new File(StorageUtils.getSharedStoragePath() + "/" + "export.csv");
					Uri u = Uri.fromFile(fileIn);
					uris.add(u);
					// Send it, if there are any files to send.
					emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
					startActivity(Intent.createChooser(emailIntent, "Send mail..."));        	
				} catch (NullPointerException e) {
					Logger.logError(e);
					Toast.makeText(AdministrationFunctions.this, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
				}

				dialog.dismiss();
			}
		}); 
		alertDialog.setButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				//do nothing
				dialog.dismiss();
			}
		}); 

		alertDialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				if (finish_after)
					finish();
			}});

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
	 * 
	 * There is a current limitation that restricts the search to only books
	 * with an ISBN
	 */
	private void updateThumbnails() {
		Intent i = new Intent(this, UpdateFromInternet.class);
		startActivity(i);
	}

	/**
	 * Start the activity that shows the basic details of background tasks.
	 */
	private void showBackgroundTasks() {
		Intent i = new Intent(this, TaskListActivity.class);
		startActivity(i);
	}

	/**
	 * Start the archiving activity
	 */
	public static void backupCatalogue(Activity a) {
		Intent i = new Intent(a, BackupChooser.class);
		i.putExtra(BackupChooser.EXTRA_MODE, BackupChooser.EXTRA_MODE_SAVE_AS);
		a.startActivity(i);
	}
	
	/**
	 * Start the restore activity
	 */
	private void restoreCatalogue() {
		Intent i = new Intent(this, BackupChooser.class);
		i.putExtra(BackupChooser.EXTRA_MODE, BackupChooser.EXTRA_MODE_OPEN);
		startActivity(i);
	}
}
