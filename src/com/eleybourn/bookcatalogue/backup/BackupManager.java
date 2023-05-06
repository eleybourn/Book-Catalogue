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
import java.io.IOException;
import java.util.Date;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;
import com.eleybourn.bookcatalogue.utils.Utils;

import androidx.documentfile.provider.DocumentFile;

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
	public static BackupReader readBackup(DocumentFile file) throws IOException {
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
	 * Esnure the file name extension is what we want
	 */
	//private static DocumentFile cleanupFile(DocumentFile requestedFile) {
	//	if (!requestedFile.getName().toUpperCase().endsWith(".BCBK")) {
	//		requestedFile.renameTo(requestedFile.getName() + ".bcbk");
	//	}
	//	return requestedFile;
	//}

	public interface BackupListener {
		void onFinish(SimpleTaskQueueProgressFragment fragment, int taskId, FragmentTask task);
	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
	 * 
	 * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static DocumentFile backupCatalogue(final BookCatalogueActivity context, final DocumentFile requestedFile, int taskId, final int backupFlags, final Date since, BackupListener listener) {
		final int flags = backupFlags & Exporter.EXPORT_MASK;
		if (flags == 0)
			throw new RuntimeException("Backup flags must be specified");
		//if (flags == (Exporter.EXPORT_ALL | Exporter.EXPORT_NEW_OR_UPDATED) )
		//	throw new RuntimeException("Illegal backup flag combination: ALL and NEW_OR_UPADTED");
		
		final DocumentFile resultingFile = requestedFile; //cleanupFile(requestedFile);
		// We can no longer save to a temp file then delete/rename due to google awfulness.
		////final File tempFile = new File(resultingFile.getAbsolutePath() + ".tmp");
		//final String finalName = resultingFile.getName();
		//resultingFile.renameTo(resultingFile.getName() + ".tmp");
		//final DocumentFile tempFile = resultingFile;

		FragmentTask task = new FragmentTaskAbstract() {
			private boolean mBackupOk = false;
			private String mBackupDate = Utils.toSqlDateTime(new Date());

			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) throws IOException {
				BackupWriter wrt = null;

				try {
					System.out.println("Starting " + resultingFile.getName());
					TarBackupContainer bkp = new TarBackupContainer(resultingFile);
					wrt = bkp.newWriter();

					wrt.backup(new BackupWriterListener() {
						private int mTotalBooks = 0;

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
						}

						@Override
						public void setTotalBooks(int books) {
							mTotalBooks = books;
						}

						@Override
						public int getTotalBooks() {
							return mTotalBooks;
						}}, backupFlags, since);

					if (fragment.isCancelled()) {
						System.out.println("Cancelled " + resultingFile.getName());
						if (resultingFile.exists())
							resultingFile.delete();
					} else {
//						if (resultingFile.exists())
//							resultingFile.delete();
						//tempFile.renameTo(finalName);
						mBackupOk = true;
						System.out.println("Finished " + resultingFile.getName() + ", size = " + resultingFile.length());
					}
				} catch (Exception e) {
					Logger.logError(e);
					if (resultingFile.exists())
						try {
							resultingFile.delete();
						} catch (Exception e2) {
							// Ignore
						}
					throw new RuntimeException("Error during backup", e);
				} finally {
					if (wrt != null) {
						try {
							wrt.close();
						} catch (Exception e2) {
							// Ignore
						}
					}
				}
			}

			@Override
			public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
				super.onFinish(fragment, exception);
				if (exception != null) {
					if (resultingFile.exists())
						resultingFile.delete();
				}
				fragment.setSuccess(mBackupOk);
				if (mBackupOk) {
					BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
					if ( (backupFlags == Exporter.EXPORT_ALL)) {
						prefs.setString(BookCataloguePreferences.PREF_LAST_BACKUP_DATE, mBackupDate);
					}
					prefs.setString(BookCataloguePreferences.PREF_LAST_BACKUP_FILE, resultingFile.getName());
				}
				if (listener != null) {
					listener.onFinish(fragment, taskId, this);
				}
			}

		};
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.runTaskWithProgress(context.getSupportFragmentManager(), R.string.backing_up_ellipsis, task, false, taskId);
		frag.setNumberFormat(null);
		return resultingFile;
	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
	 * 
	 * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static void restoreCatalogue(final BookCatalogueActivity context, final DocumentFile inputFile, int taskId, final int importFlags, BackupListener listener) {

		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				DocumentFile file = inputFile; //new File(StorageUtils.getSharedStoragePath() + "/bookCatalogue.bcbk");
				try {
					System.out.println("Starting " + file.getName());
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
						}}, importFlags);
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Error during restore", e);
				}
				System.out.println("Finished " + file.getName() + ", size = " + file.length());
			}

			@Override
			public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
				super.onFinish(fragment, exception);
				listener.onFinish(fragment, taskId, this);
			}
		};
		SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.runTaskWithProgress(context.getSupportFragmentManager(),
				R.string.importing_ellipsis, task, false, taskId);
		frag.setNumberFormat(null);
	}
}
