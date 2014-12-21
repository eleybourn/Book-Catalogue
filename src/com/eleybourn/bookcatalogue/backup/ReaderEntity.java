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
import java.io.Serializable;
import java.util.Date;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.database.SerializationUtils.DeserializationException;

/**
 * Interface provided by every entity read from a backup file.
 * 
 * @author pjw
 */
public interface ReaderEntity {
	/** Supported entity types */
	public enum BackupEntityType { Cover, Books, Info, Database, Preferences, BooklistStyle }

	/** Get the original "file name" of the object */
	public String getName();
	/** Get the type of this entity */
	BackupEntityType getType();
	/** get the stream to read the entity */
	InputStream getStream() throws IOException;
	/** Save the data to a directory, using the original file name */
	void saveToDirectory(File dir) throws IOException;
	/** Save the data to a file, using the passed file name & path */
	void saveToFile(File outFile) throws IOException;
	/** Read the data into a bundle */
	public Bundle getBundle() throws IOException;
	/** Read the data into preferences */
	public void getPreferences(SharedPreferences prefs) throws IOException;
	/** Read the data as a Serializable object */
	public Serializable getSerializable() throws IOException, DeserializationException;
	/** Modified date from archive entry */
	public Date getDateModified();
}