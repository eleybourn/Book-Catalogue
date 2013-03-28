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
import java.io.OutputStream;

/**
 * Interface definition for ant 'books' importer.
 * 
 * Currently (Feb 2013) there is only one, but there will probably be
 * an XML export/import one day.
 * 
 * @author pjw
 */
public interface Exporter {
	/** Flag value to indicate ALL books should be exported */
	public static final int EXPORT_ALL = 1;
	/** Flag value to indicate new books and books with more recent update_date fields should be exported */
	public static final int EXPORT_NEW_OR_UPDATED = 2;

	/**
	 * Listener interface to get progress messages.
	 * 
	 * @author pjw
	 */
	public interface ExportListener {
		public void setMax(int max);
		public void onProgress(String message, int position);
		public boolean isCancelled();
	}

	/** 
	 * Export function
	 * 
	 * @param outputStream		Stream to send data
	 * @param listener			Progress & cancellation interface
	 * 
	 * @return	true on success
	 * 
	 * @throws IOException
	 */
	public boolean export(OutputStream outputStream, Exporter.ExportListener listener, final int backupFlags) throws IOException;

}
