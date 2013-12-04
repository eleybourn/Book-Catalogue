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

import com.eleybourn.bookcatalogue.backup.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Tets module. DEBUG ONLY!
 * 
 * @author pjw
 */
public class Backuptest {
	public static void testBackupTar() {
		File f = new File(StorageUtils.getSharedStoragePath() + "/backup.tar");
		try {
			performBackupTar(f);
		} catch (IOException e) {
			Logger.logError(e);
		}
	}
	public static void testRestoreTar() {
		File f = new File(StorageUtils.getSharedStoragePath() + "/backup.tar");
		try {
			performRestoreTar(f);
		} catch (IOException e) {
			Logger.logError(e);
		}
	}

	public static void performBackupTar(File file) throws IOException {
		System.out.println("Starting " + file.getAbsolutePath());
		TarBackupContainer bkp = new TarBackupContainer(file);
		BackupWriter wrt = bkp.newWriter();

		wrt.backup(new BackupWriterListener() {
			private long mMax;
			private String mMessage = "";
			private boolean mIsCancelled = false;
			private int mPosition = 0;

			@Override
			public void setMax(int max) {
				mMax = max;
			}

			@Override
			public void step(String message, int delta) {
				if (message != null)
					mMessage = message;
				mPosition += delta;
				System.out.println("BKP: " + mMessage + " " + mPosition + " of " + mMax);
			}

			@Override
			public boolean isCancelled() {
				return mIsCancelled;
			}}, 0);
		System.out.println("Finished " + file.getAbsolutePath() + ", size = " + file.length());
	}

	public static void performRestoreTar(File file) throws IOException {
		System.out.println("Starting " + file.getAbsolutePath());
		
		TarBackupContainer bkp = new TarBackupContainer(file);
		// Each format should provide a validator of some kind
		if (!bkp.isValid())
			throw new IOException("Not a valid backup file");
		BackupReader rdr = bkp.newReader();

		rdr.restore(new BackupReaderListener() {
			private long mMax;
			private String mMessage = "";
			private boolean mIsCancelled = false;
			private int mPosition = 0;

			@Override
			public void setMax(int max) {
				mMax = max;
			}

			@Override
			public void step(String message, int delta) {
				if (message != null)
					mMessage = message;
				mPosition += delta;
				System.out.println("RST: " + mMessage + " " + mPosition + " of " + mMax);
			}

			@Override
			public boolean isCancelled() {
				return mIsCancelled;
			}}, Importer.IMPORT_ALL);

		System.out.println("Finished " + file.getAbsolutePath() + ", size = " + file.length());
	}


}
