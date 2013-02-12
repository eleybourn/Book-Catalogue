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
import java.io.FileFilter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * FileChooser activity to choose an archive file to open/save
 * 
 * @author pjw
 */
public class BackupChooser extends FileChooser {
	/** The backup file that will be created (if saving) */
	private File mBackupFile = null;
	/** Used when saving state */
	private final static String STATE_BACKUP_FILE = "BackupFileSpec";
	
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
		BackupManager.restoreCatalogue(this, file, 1);		
	}

	/**
	 * If a file was selected, save the archive.
	 */
	@Override
	public void onSave(File file) {
		mBackupFile = BackupManager.backupCatalogue(this, file, 1);
	}

	@Override
	public void onTaskFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled, FragmentTask task) {
		// Is it a task we care about?
		if (taskId == 1) {
			if (!success || cancelled) {
				// Just return; user may want to try again
				return;
			}
			// If it was a backup, show a helpful message
			if (mBackupFile != null && mBackupFile.exists()) {
				String msg = getString(R.string.archive_complete_details, mBackupFile.getParent(), mBackupFile.getName(), Utils.formatFileSize(mBackupFile.length()));
				AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(msg).create();
				alertDialog.setTitle(R.string.backup_to_archive);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						finish();
						return;
					}
				}); 
				alertDialog.show();				
			} else {
				// Was not a bckup, but it's done, so exit
				finish();				
			}
		}
	}

	@Override
	public void onAllTasksFinished(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled) {
		// Nothing to do here; we really only care when backup tasks finish, and there's only ever one task
	}

}
