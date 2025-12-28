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

/**
 * Interface provided by a backup archiver
 * 
 * @author pjw
 */
public interface BackupContainer {
	/**
	 * Get a BackupReader for the referenced archive.
	 * 
	 * @return a new reader
	 * 
	 * @throws IOException
	 */
    BackupReader newReader() throws IOException;

	/**
	 * Get a BackupWriter for the referenced archive.
	 * 
	 * @return a new writer
	 * 
	 * @throws IOException
	 */
    BackupWriter newWriter() throws IOException;

	/**
	 * Get the version of the underlying archiver
	 * 
	 * @return
	 */
    int getVersion();

	/**
	 * Checks if the current archive looks valid. Does not need to be
	 * exhaustive. Do not call if an archive is being written.
	 * 
	 * @return True if valid
	 */
    boolean isValid();
}
