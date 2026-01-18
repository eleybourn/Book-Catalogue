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

import android.content.Context;
import android.content.SharedPreferences;

import com.eleybourn.bookcatalogue.booklist.LibraryStyle;

/**
 * Public interface for any backup archive reader.
 * 
 * @author pjw
 */
public interface BackupWriter {
	
	/**
	 * Interface for processes doing a backup operation; allows for progress indications
	 * and saves some useful context
	 * 
	 * @author pjw
	 */
    interface BackupWriterListener {
		/** Set the end point for the progress */
		void setMax(int max);
		/** Advance progress by 'delta' */
		void step(String message, int delta);
		/** Check if operation is cancelled */
		boolean isCancelled();
		/** Save the total books exported */
		void setTotalBooks(int books);
		/** Retrieve the total books */
		int getTotalBooks();
	}

	/**
	 * Perform a restore of the database; a convenience method to loop through 
	 * all entities in the backup and restore them based on the entity type.
	 * See BackupWriterAbstract for a default implementation.
	 */
	void backup(Context context, BackupWriterListener listener, final int backupFlags, final Date since) throws IOException;
	/** Get the containing archive */
	BackupContainer getContainer();
	/** Write an info block to the archive */
    void putInfo(BackupInfo info) throws IOException;
	/** Write an export file to the archive */
    void putBooks(File books) throws IOException;
	/** Store a cover file */
    void putCoverFile(File source) throws IOException;
	/** Store a Booklist Style */
    void putBooklistStyle(LibraryStyle style) throws IOException;
	/** Store a SharedPreferences */
    void putPreferences(SharedPreferences prefs) throws IOException;
	/** Close the writer */
    void close() throws IOException;
}