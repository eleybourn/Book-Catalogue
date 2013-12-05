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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import android.content.SharedPreferences;
import android.database.Cursor;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Basic implementation of format-agnostic BackupWriter methods using 
 * only a limited set of methods from the base interface.
 * 
 * @author pjw
 */
public abstract class BackupWriterAbstract implements BackupWriter {
	private CatalogueDBAdapter mDbHelper;

	/**
	 * Constructor
	 */
	public BackupWriterAbstract() {
		mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
		mDbHelper.open();
	}

	/**
	 * Do a full backup, sending progress to the listener
	 */
	@Override
	public void backup(BackupWriterListener listener, final int backupFlags, Date since) throws IOException {
		
		try {
			listener.setMax((int) (mDbHelper.getBookCount() * 2 + 1));

			// Process each component of the Archive, unless we are cancelled, as in Nikita
			if (!listener.isCancelled())
				writeInfo(listener);
			if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_DETAILS) != 0)
				writeBooks(listener, backupFlags, since);
			if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_COVERS) != 0)
				writeCovers(listener, backupFlags, since);
			if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_PREFERENCES) != 0)
				writePreferences(listener);
			if (!listener.isCancelled() && (backupFlags & Exporter.EXPORT_STYLES) != 0)
				writeStyles(listener);			
		} finally {
			try {
				close();							
			} catch (Exception e) {
				Logger.logError(e, "Failed to close archive");
			}
		}

		System.out.println("Closed writer");
	}

	/**
	 * Generate a bundle containing the INFO block, and send it to the archive
	 * 
	 * @param writer
	 * 
	 * @throws IOException
	 */
	private void writeInfo(BackupWriterListener listener) throws IOException {
		BackupInfo info = BackupInfo.createInfo(getContainer(), mDbHelper, BookCatalogueApp.context);
		putInfo(info);
		listener.step(null, 1);
	}

	/**
	 * Generate a temporary file containing a books export, and send it to the archive
	 * 
	 * NOTE: This implementation is built around the TAR format; it is not a fixed design.
	 * We could for example pass an Exporter to the writer and leave it to decide if a 
	 * temp file or a stream were appropriate. Sadly, tar archives need to know size before
	 * the header can be written.
	 * 
	 * It IS convenient to do it here because we can caputre the progress, but we could also
	 * have writer.putBooks(exporter, listener) as the method.
	 * 
	 * @param writer
	 * 
	 * @throws IOException
	 */
	private void writeBooks(final BackupWriterListener listener, final int backupFlags, final Date since) throws IOException {
		// This is an estimate only; we actually don't know how many covers
		// there are in the backup.
		listener.setMax((int) (mDbHelper.getBookCount() * 2 + 1));

		Exporter.ExportListener exportListener = new Exporter.ExportListener() {
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
				// Ignore
			}
		};

		// Get a temp file and mark for delete
		System.out.println("Getting books");
		File temp = File.createTempFile("bookcat", ".tmp");
		temp.deleteOnExit();
		FileOutputStream output = null;
		try {
			CsvExporter exporter = new CsvExporter();
			output = new FileOutputStream(temp);
			exporter.export(output, exportListener, backupFlags, since);
			output.close();
			System.out.println("Writing Books");
			putBooks(temp);
		} finally {
			if (output != null && output.getChannel().isOpen())
				output.close();
			if (temp.exists())
				temp.delete();
		}
	}

	/**
	 * Write each cover file corresponding to a book to the archive
	 * 
	 * @param writer
	 * 
	 * @throws IOException
	 */
	private void writeCovers(final BackupWriterListener listener, final int backupFlags, final Date since) throws IOException {
		long sinceTime = 0;
		if ( since != null && (backupFlags & Exporter.EXPORT_SINCE) != 0) {
			try {
				sinceTime = since.getTime();
			} catch (Exception e) {
				// Just ignore; backup everything
				Logger.logError(e);
			}
		}

		System.out.println("Writing Images");
		
		int ok = 0;
		int missing = 0;
		int skipped = 0;
		String fmt_noskip = BookCatalogueApp.getResourceString(R.string.covers_progress);
		String fmt_skip = BookCatalogueApp.getResourceString(R.string.covers_progress_incr);

		Cursor c = mDbHelper.getUuidList();
		try {
			final int uuidCol = c.getColumnIndex(DatabaseDefinitions.DOM_BOOK_UUID.toString());
			while(c.moveToNext() && !listener.isCancelled()) {
				File cover = CatalogueDBAdapter.fetchThumbnailByUuid(c.getString(uuidCol));
				if (cover.exists()) {
					if (since == null || sinceTime < cover.lastModified()) {
						putCoverFile(cover);
						ok++;
					} else {
						skipped++;
					}
				} else {
					missing++;
				}
				String message;
				if (skipped == 0) {
					message = String.format(fmt_noskip, ok, missing);
				} else {
					message = String.format(fmt_skip, ok, missing, skipped);
				}
				listener.step(message, 1);
			}			
		} finally {
			if (c != null && !c.isClosed())
				c.close();			
		}
		System.out.println("Wrote " + ok + " Images, "+ missing + " missing, and " + skipped + " skipped");
	}

	/**
	 * Get the preferences and save them
	 * 
	 * @param listener
	 * 
	 * @throws IOException
	 */
	private void writePreferences(final BackupWriterListener listener) throws IOException {
		SharedPreferences prefs = BookCataloguePreferences.getSharedPreferences();
		putPreferences(prefs);
		listener.step(null, 1);
	}

	/**
	 * Save all USER styles
	 * 
	 * @param listener
	 * 
	 * @throws IOException
	 */
	private void writeStyles(final BackupWriterListener listener) throws IOException {
		BooklistStyles styles = BooklistStyles.getAllStyles(mDbHelper);
		for(BooklistStyle style: styles) {
			if (style.isUserDefined()) {
				putBooklistStyle(style);				
			}
		}
		listener.step(null, 1);
	}
	
	/**
	 * Cleanup
	 */
	public void close() throws IOException {
		mDbHelper.close();
	}
}
