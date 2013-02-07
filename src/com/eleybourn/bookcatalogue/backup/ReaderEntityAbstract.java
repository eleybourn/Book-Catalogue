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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.database.SerializationUtils;
import com.eleybourn.bookcatalogue.database.SerializationUtils.DeserializationException;

/**
 * Basic implementation of format-agnostic ReaderEntity methods using 
 * only a limited set of methods from the base interface.
 * 
 * @author pjw
 */
public abstract class ReaderEntityAbstract implements ReaderEntity {
	
	@Override
	public void saveToDirectory(File dir) throws IOException {
		// Make sure it's a directory
		if (!dir.isDirectory())
			throw new RuntimeException("Specified path is not a directory");

		// Build the new File and save
		File output = new File(dir.getAbsoluteFile() + "/" + getName());
		saveToFile(output);
	}

	@Override
	public void saveToFile(File outFile) throws IOException {
		// Open output and copy bytes.
		FileOutputStream out = new FileOutputStream(outFile);
		try {
			byte[] buffer = new byte[TarBackupContainer.BUFFER_SIZE];
			InputStream in = getStream();
			while (true) {
				int cnt = in.read(buffer);
				if (cnt <= 0)
					break;
				out.write(buffer, 0, cnt);
			}			
		} finally {
			if (out != null && out.getChannel().isOpen())
				out.close();
		}
	}

	/**
	 * Read the input as XML and put it into a Bundle
	 */
	public Bundle getBundle() throws IOException {
		BufferedReader in = new BufferedReaderNoClose(new InputStreamReader(getStream(), TarBackupContainer.UTF8), TarBackupContainer.BUFFER_SIZE);
		return BackupUtils.bundleFromXml(in);
	}

	/**
	 * Read the input as XML and put it into a SharedPreferences
	 */
	public void getPreferences(SharedPreferences prefs) throws IOException {
		BufferedReader in = new BufferedReaderNoClose(new InputStreamReader(getStream(), TarBackupContainer.UTF8), TarBackupContainer.BUFFER_SIZE);
		BackupUtils.preferencesFromXml(in, prefs);
	}

	/**
	 * The sax parser closes streams, which is not good on a Tar archive entry
	 * @author pjw
	 *
	 */
	private static class BufferedReaderNoClose extends BufferedReader
    {
        public BufferedReaderNoClose(Reader in, int flags) {
			super(in, flags);
		}

        @Override
        public void close() {
        }
    }

	@Override
	public Serializable getSerializable() throws IOException, DeserializationException {
		// Turn the input into a byte array
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		byte[] buffer = new byte[TarBackupContainer.BUFFER_SIZE];
		
		while (true) {
			int cnt = getStream().read(buffer);
			if (cnt <= 0)
				break;
			byteStream.write(buffer);
		}
		byteStream.close();
		// Deserialize the byte array
		return SerializationUtils.deserializeObject(byteStream.toByteArray());
	}
}
