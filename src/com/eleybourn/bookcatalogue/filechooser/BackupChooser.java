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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Choose an archive file to open/save
 * 
 * @author pjw
 */
public class BackupChooser extends FileChooser {
	private File mBackupFile;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set the correct title
		if (isSaveDialog()) {
			this.setTitle(R.string.backup_to_archive);
		} else {
			this.setTitle(R.string.import_from_archive);			
		}
	}

	/**
	 * Setup the default file name: blank for 'open', date-based for save
	 * 
	 * @return
	 */
	protected String getDefaultFileName() {
    	if (isSaveDialog()) {
    		final String sqlDate = Utils.toLocalSqlDateOnly(new Date());
    		return "BookCatalogue-" + sqlDate.replace(" ", "-").replace(":", "") + ".bcbk";
    	} else {
    		return "";
    	}

	}

	/**
	 * Create the fragment
	 */
	protected FileChooserFragment<?> getNewBrowserFragment() {
		return BackupChooserFragment.newInstance(StorageUtils.getSharedStorage(), getDefaultFileName());
	}

	/**
	 * If a file was selected, restore the archive.
	 */
	protected void onOpen(File file) {
		BackupManager.restoreCatalogue(this, file, 1);		
	}

	/**
	 * If a file was selected, save the archive.
	 */
	protected void onSave(File file) {
		mBackupFile = BackupManager.backupCatalogue(this, file, 1);
	}

	/**
	 * When a specific backup/restore task completes, exit
	 */
	@Override
	public void onTasksComplete(SimpleTaskQueueProgressFragment fragment, int taskId, boolean success, boolean cancelled) {
		if (taskId == 1) {
			if (success && !cancelled && mBackupFile != null && mBackupFile.exists()) {
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
				finish();				
			}
		}
	}

}
