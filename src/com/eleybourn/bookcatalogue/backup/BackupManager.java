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

import android.content.Context;

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
	 * @param file	DocumentFile to read
	 * 
	 * @return	a new reader
	 * 
	 * @throws IOException (inaccessible, invalid other other errors)
	 */
	public static BackupReader readBackup(Context context, DocumentFile file) throws IOException {
		if (!file.exists())
			throw new java.io.FileNotFoundException("Attempt to open non-existent backup file");

		// We only support one backup format; so we use that. In future we would need to
		// explore the file to determine which format to use
		TarBackupContainer bkp = new TarBackupContainer(context, file);
		// Each format should provide a validator of some kind
		if (!bkp.isValid())
			throw new IOException("Not a valid backup file");

		return bkp.newReader();
	}

	public interface BackupListener {
		void onFinish(SimpleTaskQueueProgressFragment fragment, int taskId, FragmentTask task);
	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
     * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static DocumentFile backupCatalogue(final BookCatalogueActivity context, final DocumentFile requestedFile, int taskId, final int backupFlags, final Date since, BackupListener listener) {
		final int flags = backupFlags & Exporter.EXPORT_MASK;
		if (flags == 0)
			throw new RuntimeException("Backup flags must be specified");

		FragmentTask task = new FragmentTaskAbstract() {
			private boolean mBackupOk = false;
			private final String mBackupDate = Utils.toSqlDateTime(new Date());

			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) throws IOException {
				BackupWriter wrt = null;

				try {
					TarBackupContainer bkp = new TarBackupContainer(context, requestedFile);
					wrt = bkp.newWriter(context);

					wrt.backup(fragment.getContext(), new BackupWriterListener() {
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
						if (requestedFile.exists())
                            requestedFile.delete();
					} else {
						mBackupOk = true;
					}
				} catch (Exception e) {
					Logger.logError(e);
					if (requestedFile.exists())
						try {
                            requestedFile.delete();
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
					if (requestedFile.exists())
                        requestedFile.delete();
				}
				fragment.setSuccess(mBackupOk);
				if (mBackupOk) {
					BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
					if ( (backupFlags == Exporter.EXPORT_ALL)) {
						prefs.setString(BookCataloguePreferences.PREF_LAST_BACKUP_DATE, mBackupDate);
					}
					prefs.setString(BookCataloguePreferences.PREF_LAST_BACKUP_FILE, requestedFile.getName());
				}
				if (listener != null) {
					listener.onFinish(fragment, taskId, this);
				}
			}

		};
		SimpleTaskQueueProgressFragment.runTaskWithProgress(context.getSupportFragmentManager(), R.string.backing_up_ellipsis, task, false, taskId);
		return requestedFile;
	}

	/**
	 * Start a foreground task that backs up the entire catalogue.
	 * We use a FragmentTask so that long actions do not occur in the UI thread.
	 */
	public static void restoreCatalogue(final BookCatalogueActivity context, final DocumentFile inputFile, int taskId, final int importFlags, BackupListener listener) {

		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(final SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				try {
					BackupReader rdr = BackupManager.readBackup(context, inputFile);
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
			}

			@Override
			public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
				super.onFinish(fragment, exception);
				listener.onFinish(fragment, taskId, this);
			}
		};
		SimpleTaskQueueProgressFragment.runTaskWithProgress(context.getSupportFragmentManager(),
				R.string.importing_ellipsis, task, false, taskId);
	}
}
