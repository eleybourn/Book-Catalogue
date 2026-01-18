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

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.eleybourn.bookcatalogue.backup.BackupContainer;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupReaderAbstract;
import com.eleybourn.bookcatalogue.backup.ReaderEntity;
import com.eleybourn.bookcatalogue.backup.ReaderEntity.BackupEntityType;

/**
 * Implementation of TAR-specific reader functions
 * 
 * @author pjw
 */
public class TarBackupReader extends BackupReaderAbstract {
	/** Parent container */
	private final TarBackupContainer mContainer;
	/** The data stream for the archive */
	private final TarArchiveInputStream mInput;
	/** Used to allow 'peeking' at the input stream */
	private ReaderEntity mPushedEntity;
	/** The INFO data read from the start of the archive */
	private final BackupInfo mInfo;

	/**
	 * Constructor
	 * 
	 * @param container		Parent
	 */
	public TarBackupReader(Context context, TarBackupContainer container) throws IOException {
        super(context);
        mContainer = container;

		// Open the file and create the archive stream
		InputStream in = container.getInputStream();
		mInput = new TarArchiveInputStream(in);

		// Process the INFO entry. Should be first.
		ReaderEntity info = nextEntity();
		if (info == null || info.getType() != BackupEntityType.Info)
			throw new IOException("Not a valid backup");

		// Save the INFO
		mInfo = new BackupInfo(info.getBundle());

		// Skip any following INFOs. Later versions may store more.
		while(info != null && info.getType() == BackupEntityType.Info) {
			info = nextEntity();
		}
		// Save the 'peeked' entity
		mPushedEntity = info;
	}

	/**
	 * Accessor
	 */
	@Override
	public BackupContainer getContainer() {
		return mContainer;
	}

	/**
	 * Get the next entity (allowing for peeking).
	 */
	@Override
	public ReaderEntity nextEntity() throws IOException {

		if (mPushedEntity != null) {
			ReaderEntity e = mPushedEntity;
			mPushedEntity = null;
			return e;
		}

		TarArchiveEntry entry = mInput.getNextTarEntry();

		if (entry == null) {
			return null;
		}

		// Based on the file name, determine entity type
		BackupEntityType type;
		if (entry.getName().equalsIgnoreCase(TarBackupContainer.BOOKS_FILE)) {
			type = BackupEntityType.Books;
		} else if (TarBackupContainer.BOOKS_PATTERN.matcher(entry.getName()).find()) {
			type = BackupEntityType.Books;
		} else if (entry.getName().equalsIgnoreCase(TarBackupContainer.INFO_FILE)) {
			type = BackupEntityType.Info;
		} else if (TarBackupContainer.INFO_PATTERN.matcher(entry.getName()).find()) {
			type = BackupEntityType.Info;
		} else if (entry.getName().equalsIgnoreCase(TarBackupContainer.DB_FILE)) {
			type = BackupEntityType.Database;
		} else if (TarBackupContainer.STYLE_PATTERN.matcher(entry.getName()).find()) {
			type = BackupEntityType.BooklistStyle;
		} else if (entry.getName().equalsIgnoreCase(TarBackupContainer.PREFERENCES)) {
			type = BackupEntityType.Preferences;
		} else {
			type = BackupEntityType.Cover;			
		}

		// Create entity
		return new TarReaderEntity(this, entry, type);

	}

	/**
	 * Accessor used by TarEntityReader to get access to the stream data
	 */
	protected TarArchiveInputStream getInput() {
		return mInput;
	}

	/**
	 * Accessor
	 */
	@Override
	public BackupInfo getInfo() {
		return mInfo;
	}

	@Override
	public void close() throws IOException {
		super.close();
		mInput.close();
	}
}
