/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.filechooser;

import java.io.File;
import java.util.Date;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment.OnMessageDialogResultListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * FileChooser activity to choose an archive file to open/save
 * 
 * @author pjw
 */
public class BackupChooser extends FileChooser implements OnMessageDialogResultListener, OnImportTypeSelectionDialogResultListener, OnExportTypeSelectionDialogResultListener {
	/** The backup file that will be created (if saving) */
	private File mBackupFile = null;
	/** Used when saving state */
	private final static String STATE_BACKUP_FILE = "BackupFileSpec";
	
	private static final int TASK_ID_SAVE = 1;
	private static final int TASK_ID_OPEN = 2;
	private static final int DIALOG_OPEN_IMPORT_TYPE = 1;

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set the correct title
		if (isSaveDialog()) {
			this.setTitle(R.string.backup_to_archive);
		} else {
			this.setTitle(R.string.import_from_archive);			
		}

		if (savedInstanceState != null && savedInstanceState.containsKey(STATE_BACKUP_FILE)) {
			mBackupFile = new File(savedInstanceState.getString(STATE_BACKUP_FILE));
		}
	
	}

	/**
	 * Setup the default file name: blank for 'open', date-based for save
	 * 
	 * @return
	 */
	private String getDefaultFileName() {
    	if (isSaveDialog()) {
    		final String sqlDate = Utils.toLocalSqlDateOnly(new Date());
    		return "BookCatalogue-" + sqlDate.replace(" ", "-").replace(":", "") + ".bcbk";
    	} else {
    		return "";
    	}
	}

	/**
	 * Create the fragment using the last backup for the path, and the default file name (if saving)
	 */
	@Override
	protected FileChooserFragment getChooserFragment() {
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		String lastBackup = prefs.getString(BookCataloguePreferences.PREF_LAST_BACKUP_FILE, StorageUtils.getSharedStoragePath());
		return FileChooserFragment.newInstance(new File(lastBackup), getDefaultFileName());
	}

	/**
	 * Get a task suited to building a list of backup files.
	 */
	@Override
	public FileLister getFileLister(File root) {
		return new BackupLister(root);
	}

	/**
	 * Save the state
	 */
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// We need the backup file, if set
		if (mBackupFile != null) {
			outState.putString(STATE_BACKUP_FILE, mBackupFile.getAbsolutePath());
		}
	}

	/**
	 * If a file was selected, restore the archive.
	 */
	@Override
	public void onOpen(File file) {
		ImportTypeSelectionDialogFragment frag = ImportTypeSelectionDialogFragment.newInstance(DIALOG_OPEN_IMPORT_TYPE, file);
		frag.show(getSupportFragmentManager(), null);
	}

	/**
	 * If a file was selected, save the archive.
	 */
	@Override
	public void onSave(File file) {
		ExportTypeSelectionDialogFragment frag = ExportTypeSelectionDialogFragment.newInstance(DIALOG_OPEN_IMPORT_TYPE, file);
		frag.show(getSupportFragmentManager(), null);
	}

	@Override
	public void onTaskFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled, FragmentTask task) {
		// Is it a task we care about?
		if (taskId == TASK_ID_SAVE) {
			if (!success) {
				String msg = getString(R.string.backup_failed)
						+ " " + getString(R.string.please_check_sd_writable)
						+ "\n\n" + getString(R.string.if_the_problem_persists);

				MessageDialogFragment frag = MessageDialogFragment.newInstance(0, R.string.backup_to_archive, msg, R.string.ok, 0, 0);
				frag.show(getSupportFragmentManager(), null);
				// Just return; user may want to try again
				return;
			}
			if (cancelled) {
				// Just return; user may want to try again
				return;
			}
			// Show a helpful message
			String msg = getString(R.string.archive_complete_details, mBackupFile.getParent(), mBackupFile.getName(), Utils.formatFileSize(mBackupFile.length()));
			MessageDialogFragment frag = MessageDialogFragment.newInstance(TASK_ID_SAVE, R.string.backup_to_archive, msg, R.string.ok, 0, 0);
			frag.show(getSupportFragmentManager(), null);

		} else if (taskId == TASK_ID_OPEN) {
			if (!success) {
				String msg = getString(R.string.import_failed)
						+ " " + getString(R.string.please_check_sd_readable)
						+ "\n\n" + getString(R.string.if_the_problem_persists);

				MessageDialogFragment frag = MessageDialogFragment.newInstance(0, R.string.import_from_archive, msg, R.string.ok, 0, 0);
				frag.show(getSupportFragmentManager(), null);
				// Just return; user may want to try again
				return;
			}
			if (cancelled) {
				// Just return; user may want to try again
				return;
			}

			MessageDialogFragment frag = MessageDialogFragment.newInstance(TASK_ID_OPEN, R.string.import_from_archive, R.string.import_complete, R.string.ok, 0, 0);
			frag.show(getSupportFragmentManager(), null);

		}
	}

	@Override
	public void onAllTasksFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled) {
		// Nothing to do here; we really only care when backup tasks finish, and there's only ever one task
	}

	@Override
	public void onMessageDialogResult(int dialogId, MessageDialogFragment dialog, int button) {
		switch(dialogId) {
		case 0:
			// Do nothing.
			// Our dialogs with ID 0 are only 'FYI' type; 
			break;
		case TASK_ID_OPEN:
		case TASK_ID_SAVE:
			finish();
			break;
		}
	}

	@Override
	public void onImportTypeSelectionDialogResult(int dialogId, ImportTypeSelectionDialogFragment dialog, int rowId, File file) {
		switch(rowId) {
		case 0:
			// Do nothing
			break;
		case R.id.all_books_row:
			BackupManager.restoreCatalogue(this, file, TASK_ID_OPEN, Importer.IMPORT_ALL);
			break;
		case R.id.new_and_changed_books_row:
			BackupManager.restoreCatalogue(this, file, TASK_ID_OPEN, Importer.IMPORT_NEW_OR_UPDATED);
			break;
		}
	}

	@Override
	public void onExportTypeSelectionDialogResult(int dialogId, BookCatalogueDialogFragment dialog, ExportSettings settings) {
		if (settings.options == Exporter.EXPORT_ALL) {
			mBackupFile = BackupManager.backupCatalogue(this, settings.file, TASK_ID_SAVE, Exporter.EXPORT_ALL, null);			
		} else if (settings.options == 0) {
			return;
		} else {
			if (settings.dateFrom == null) {
				String lastBackup = BookCatalogueApp.getAppPreferences().getString(BookCataloguePreferences.PREF_LAST_BACKUP_DATE, null);
				if (lastBackup != null && !lastBackup.equals("")) {
					try {
						settings.dateFrom = Utils.parseDate(lastBackup);
					} catch (Exception e) {
						// Just ignore; backup everything
						Logger.logError(e);
						settings.dateFrom = null;
					}
				} else {
					settings.dateFrom = null;
				}				
			}
			mBackupFile = BackupManager.backupCatalogue(this, settings.file, TASK_ID_SAVE, settings.options, settings.dateFrom);
			
		}
	}

}
