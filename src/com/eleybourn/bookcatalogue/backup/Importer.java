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
import java.io.InputStream;

import com.eleybourn.bookcatalogue.backup.Importer.CoverFinder;

public interface Importer {

	/** Flag value to indicate ALL books should be imported */
	public static final int IMPORT_ALL = 1;
	/** Flag value to indicate new books and books with more recent update_date fields should be imported */
	public static final int IMPORT_NEW_OR_UPDATED = 2;

	/**
	 * Listener interface to get progress messages.
	 * 
	 * @author pjw
	 */
	public interface OnImporterListener {
		public void onProgress(String message, int position);
		public boolean isCancelled();
		public void setMax(int max);
	}

	/**
	 * Interface for finding a cover file on the local device if missing from bookCatalogue directory.
	 * Legacy of the "import from a directory" model.
	 * 
	 * @author pjw
	 */
	public interface CoverFinder {
		public void copyOrRenameCoverFile(String srcUuid, long srcId, long dstId) throws IOException;
	}

	/**
	 * Import function
	 * 
	 * @param importStream		Stream for reading data	
	 * @param coverFinder		(Optional) object to find a file on the local device
	 * @param listener			Progress and cancellation provider
	 * 
	 * @return		true on success
	 * 
	 * @throws IOException
	 */
	public boolean importBooks(InputStream importStream, Importer.CoverFinder coverFinder, Importer.OnImporterListener listener, int importFlags) throws IOException;
}
