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
import java.io.InputStream;
import java.util.Date;

import android.content.SharedPreferences;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.booklist.LibraryStyle;
import com.eleybourn.bookcatalogue.database.SerializationUtils.DeserializationException;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Basic implementation of format-agnostic BackupReader methods using 
 * only a limited set of methods from the base interface.
 * 
 * @author pjw
 */
public abstract class BackupReaderAbstract implements BackupReader {
	private final CatalogueDBAdapter mDbHelper;
	private final File mCoversDir = StorageUtils.getBCCovers();

	/**
	 * Constructor
	 */
	public BackupReaderAbstract() {
		mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
		mDbHelper.open();
	}

	/**
	 * Do a full restore, sending progress to the listener
	 */
	@Override
	public void restore(BackupReaderListener listener, int importFlags) throws IOException {
		// Just a stat for progress
		int coverCount = 0;

		// This is an estimate only; we actually don't know how many covers
		// there are in the backup.
		BackupInfo info = getInfo();
		int maxSteps = info.getBookCount();
		if (info.hasCoverCount())
			maxSteps += info.getCoverCount();
		else 
			maxSteps *= 2;
		maxSteps++;
		listener.setMax(maxSteps);

		// Get first entity (this will be the entity AFTER the INFO entities)
		ReaderEntity entity = nextEntity();
		// While not at end, loop, processing each entry based on type
		while (entity != null && !listener.isCancelled()) {
			switch (entity.getType()) {
			case Books:
				restoreBooks(listener, entity, importFlags);
				break;
			case Cover:
				coverCount++;
				restoreCover(listener, entity, importFlags);
				break;
			case Database:
				break;
			case Preferences:
				restorePreferences(listener, entity);
				break;
			case BooklistStyle:
				restoreStyle(listener, entity);
				break;
			case Info:
				break;
			default:
				throw new RuntimeException("Unknown Entity type: " + entity.getType().toString());
			}
			entity = nextEntity();
		}
		close();

		System.out.println("Restored " + coverCount + " covers");
	}

	/**
	 * Restore the books from the export file.
	 * 
	 * @param listener
	 * @param entity
	 * @throws IOException
	 */
	private void restoreBooks(final BackupReaderListener listener, ReaderEntity entity, int importFlags) throws IOException {
		// Make a listener for the 'export' function that just passes on the progress to out listener
		Importer.OnImporterListener importListener = new Importer.OnImporterListener() {
			private int mLastPos = 0;

			@Override
			public void onProgress(String message, int position) {
				// The progress is sent periodically and has jumps, so we calculate deltas
				listener.step(message, position - mLastPos);
				mLastPos = position;
			}

			@Override
			public boolean isCancelled() {
				return listener.isCancelled();
			}

			@Override
			public void setMax(int max) {
				// Ignore; we know how many books there are
			}
		};

		// Now do the import
		InputStream in = entity.getStream();
		CsvImporter importer = new CsvImporter();
		importer.importBooks(in, importListener, importFlags);
	}

	/**
	 * Restore a cover file.
	 * 
	 * @param listener	Listener
	 * @param cover		Entity containing the cover image
	 * @param flags		Import flags
	 * @throws IOException
	 */
	private void restoreCover(BackupReaderListener listener, ReaderEntity cover, int flags) throws IOException {
		listener.step("Processing Covers...", 1);
		final File curr = new File(mCoversDir + "/" + cover.getName());
		final Date covDate = cover.getDateModified();
		if ( (flags & Importer.IMPORT_NEW_OR_UPDATED) != 0) {			
			if (curr.exists()) {
				Date currFileDate = new Date(curr.lastModified());
				if (currFileDate.compareTo(covDate) >= 0) {
					return;
				}
			}
		}
		cover.saveToDirectory(mCoversDir);
		curr.setLastModified(covDate.getTime());
	}

	/**
	 * Restore the app preferences
	 * 
	 * @param listener
	 * @param entity
	 * @throws IOException
	 */
	private void restorePreferences(BackupReaderListener listener, ReaderEntity entity) throws IOException {
		listener.step("Preferences...", 1);
		SharedPreferences prefs = BookCataloguePreferences.getSharedPreferences();
		entity.getPreferences(prefs);
	}

	/**
	 * Restore a booklist style
	 * 
	 * @param listener
	 * @param entity
	 * @throws IOException
	 */
	private void restoreStyle(BackupReaderListener listener, ReaderEntity entity) throws IOException {
		listener.step("Booklist Styles...", 1);
		LibraryStyle s = null;
		try {
			s = (LibraryStyle) entity.getSerializable();
		} catch (DeserializationException e) {
			Logger.logError(e, "Unable to restore style");
		}
		if (s != null) {
			s.saveToDb(mDbHelper);
		}
	}

	/**
	 * Close the reader
	 */
	public void close() throws IOException {
		mDbHelper.close();
	}

}
