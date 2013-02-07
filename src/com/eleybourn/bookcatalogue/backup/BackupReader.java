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

import java.io.IOException;

import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;

/**
 * Public interface for any backup archive reader.
 * 
 * @author pjw
 */
public interface BackupReader {
	
	/**
	 * Interface for processes doing a restore operation; allows for progress indications
	 * 
	 * @author pjw
	 */
	public interface BackupReaderListener {
		/** Set the end point for the progress */
		void setMax(int max);
		/** Advance progress by 'delta' */
		void step(String message, int delta);
		/** Check if operation is cancelled */
		boolean isCancelled();
	}

	/**
	 * Perform a restore of the database; a convenience method to loop through 
	 * all entities in the backup and restore them based on the entity type.
	 * 
	 * See BackupReaderAbstract for a default implementation.
	 * 
	 * @param listener		Listener to receive progress information.
	 * 
	 * @throws IOException
	 */
	void restore(BackupReaderListener listener) throws IOException;
	
	/**
	 * Read the next ReaderEntity from the backup.
	 * 
	 * Currently, backup files are read sequentially.
	 * 
	 * @return	The next entity, or null if at end
	 * 
	 * @throws IOException
	 */
	public ReaderEntity nextEntity() throws IOException;
	
	/**
	 * Close the reader
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException;
	
	/**
	 * Get the associated BackupContainer
	 * 
	 * @return
	 */
	BackupContainer getContainer();
	
	/**
	 * Get the INFO object read from the backup
	 * 
	 * @return
	 */	
	public BackupInfo getInfo();

}