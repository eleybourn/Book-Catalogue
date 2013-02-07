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
package com.eleybourn.bookcatalogue.backup.tar;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import com.eleybourn.bookcatalogue.backup.BackupContainer;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.backup.BackupWriter;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Class to handle TAR archive storage.
 * 
 * TAR files have some limitations: no application-defined metadata can be stored with the files, the
 * index is at the start, so it helps to know the entity size before it is written, and they usually
 * do not support random access.
 * 
 * So we:
 * 
 * - use "file names" to encode special meaning (eg. "books*.csv" is always an export file).
 * - use intermediate temp files so we can out sizes
 * 
 * @author pjw
 */
public class TarBackupContainer implements BackupContainer {
	/** Used in the storage and identifcation of data store in TAR file */
	public static final String BOOKS_FILE = "books.csv";
	/** Used in the storage and identifcation of data store in TAR file */
	public static final Pattern BOOKS_PATTERN = Pattern.compile("^books_.*\\.csv$", Pattern.CASE_INSENSITIVE);
	/** Used in the storage and identifcation of data store in TAR file */
	public static final String DB_FILE = "snapshot.db";
	/** Used in the storage and identifcation of data store in TAR file */
	public static final String INFO_FILE = "INFO.xml";
	/** Used in the storage and identifcation of data store in TAR file */
	public static final Pattern INFO_PATTERN = Pattern.compile("^INFO_.*\\.xml$", Pattern.CASE_INSENSITIVE);
	/** Used in the storage and identifcation of data store in TAR file */
	public static final String STYLE_PREFIX = "style.blob.";
	/** Used in the storage and identifcation of data store in TAR file */
	public static final Pattern STYLE_PATTERN = Pattern.compile("^" + STYLE_PREFIX + "[0-9]*$", Pattern.CASE_INSENSITIVE);
	/** Used in the storage and identifcation of data store in TAR file */
	public static final String PREFERENCES = "preferences";

	/** Backup file spec */
	public File mFile;
	/** UNICODE stream type for read/write text files */
	public static String UTF8 = "utf8";
	/** Buffer size for buffered streams */
	public static int BUFFER_SIZE = 32768;

	/**
	 * Constructor
	 * 
	 * @param file
	 */
	public TarBackupContainer(File file) {
		mFile = file;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public File getFile() {
		return mFile;
	}

	@Override
	public BackupReader newReader() throws IOException {
		return new TarBackupReader(this);
	}

	@Override
	public BackupWriter newWriter() throws IOException {
		return new TarBackupWriter(this);
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public boolean isValid() {
		// The reader will do basic validation.
		try {
			BackupReader reader = new TarBackupReader(this);
			reader.close();
		} catch (IOException e) {
			Logger.logError(e);
			return false;
		}

		return true;
	}

}
