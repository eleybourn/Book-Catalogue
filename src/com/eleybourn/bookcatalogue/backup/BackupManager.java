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
package com.eleybourn.bookcatalogue.backup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import net.philipwarner.taskqueue.QueueManager;


import com.eleybourn.bookcatalogue.BcQueueManager;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.goodreads.ImportAllTask;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;

/**
 * Class for public static methods relating to backup/restore
 * 
 * @author pjw
 */
public class BackupManager {

	/**
	 * Create a BackupReader for the specified file.
	 * 
	 * @param file	File to read
	 * 
	 * @return	a new reader
	 * 
	 * @throws IOException (inaccessible, invalid other other errors)
	 */
	public static BackupReader readBackup(File file) throws IOException {
		if (!file.exists())
			throw new java.io.FileNotFoundException("Attempt to open non-existent backup file");
		
		// We only support one backup format; so we use that. In future we would need to 
		// explore the file to determine which format to use
		TarBackupContainer bkp = new TarBackupContainer(file);
		// Each format should provide a validator of some kind
		if (!bkp.isValid())
			throw new IOException("Not a valid backup file");

		return bkp.newReader();
	}

	/**
	 * Open/create a new backup
	 * 
	 * @param file			File to use (will overwrite)
	 * 
	 * @return				a new writer
	 * 
	 * @throws IOException
	 */
	public static BackupWriter writeBackup(File file) throws IOException {
		//if (file.exists())
		//	throw new java.io.FileNotFoundException("Attempt to open non-existent backup file");
		
		// We only support one backup format; so we use that. In future we would need to 
		// explore the file to determine which format to use
		TarBackupContainer bkp = new TarBackupContainer(file);

		return bkp.newWriter();
	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
	 * 
	 * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static void backupCatalogue(final BookCatalogueActivity context) {

		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				try {
					File file = new File(StorageUtils.getSharedStoragePath() + "/bookCatalogue.bcbk");
					System.out.println("Starting " + file.getAbsolutePath());
					BackupWriter wrt = BackupManager.writeBackup(file);
					wrt.backup(new BackupWriterListener() {
						@Override
						public void setMax(int max) {
							fragment.setMax(max);
						}

						@Override
						public void step(String message, int delta) {
							fragment.step(message, delta);
						}

						@Override
						public boolean isCancelled() {
							return fragment.isCancelled();
						}});
					System.out.println("Finished " + file.getAbsolutePath() + ", size = " + file.length());					
				} catch (Exception e) {
					Logger.logError(e);
				}
			}

		};
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.backing_up_ellipsis, task, false);
		frag.setNumberFormat(null);
	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
	 * 
	 * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static void restoreCatalogue(final BookCatalogueActivity context) {

		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				File file = new File(StorageUtils.getSharedStoragePath() + "/bookCatalogue.bcbk");
				try {
					System.out.println("Starting " + file.getAbsolutePath());
					BackupReader rdr = BackupManager.readBackup(file);
					rdr.restore(new BackupReaderListener() {
						@Override
						public void setMax(int max) {
							fragment.setMax(max);
						}

						@Override
						public void step(String message, int delta) {
							fragment.step(message, delta);
						}

						@Override
						public boolean isCancelled() {
							return fragment.isCancelled();
						}});
				} catch (Exception e) {
					Logger.logError(e);
				}
				System.out.println("Finished " + file.getAbsolutePath() + ", size = " + file.length());
			}
		};
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.restoring_ellipsis, task, false);
		frag.setNumberFormat(null);
	}
}
