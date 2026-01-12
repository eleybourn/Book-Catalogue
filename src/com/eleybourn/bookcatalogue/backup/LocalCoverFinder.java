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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Class to find covers for an importer when the import is reading from a local directory.
 * 
 * @author pjw
 */
public class LocalCoverFinder implements Importer.CoverFinder {
	/** The root path to search for files */
	private final String mSrc;
	private final String mDst;
	private final boolean mIsForeign;
	private final String mCoversPath;
	private final CatalogueDBAdapter mDbHelper;

	public LocalCoverFinder(String srcPath, String dstPath) {
		mSrc = srcPath;
		mDst = dstPath;
		mIsForeign = !mSrc.equals(mDst);
		mCoversPath = StorageUtils.getBCCovers().getAbsolutePath();

		mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
		mDbHelper.open();
	}
	
	public void copyOrRenameCoverFile(String srcUuid, long srcId, long dstId) throws IOException {
		if (srcUuid != null && !srcUuid.isEmpty()) {
			// Only copy UUID files if they are foreign...since they already exists, otherwise.
			if (mIsForeign)
				copyCoverImageIfMissing(srcUuid);							
		} else {
			if (srcId != 0) {
				// This will be a rename or a copy
				if (mIsForeign)
					copyCoverImageIfMissing(srcId, dstId);
				else
					renameCoverImageIfMissing(srcId, dstId);																
			}
		}

	}

	private File findExternalCover(String name) {
		// Find the original, if present.
		File orig = new File(mSrc + "/" + name + ".jpg");
		if (!orig.exists()) {
			orig = new File(mSrc + "/" + name + ".png");
		}

		// Nothing to copy?
		if (!orig.exists())
			return null;
		else
			return orig;
		
	}

	/**
	 * Find the current cover file (or new file) based on the passed source and UUID.
	 * 
	 * @param orig		Original file to be copied/renamed if no existing file.
	 * @param newUuid	UUID of file
	 * 
	 * @return			Existing file (if length > 0), or new file object
	 */
	private File getNewCoverFile(File orig, String newUuid) {
		File newFile;
		// Check for ANY current image; delete empty ones and retry
		newFile = CatalogueDBAdapter.fetchThumbnailByUuid(newUuid);
		while (newFile.exists()) {
			if (newFile.length() > 0)
				return newFile;
			else
				newFile.delete();
			newFile = CatalogueDBAdapter.fetchThumbnailByUuid(newUuid);
		}
		
		// Get the new path based on the input file type.
		if (orig.getAbsolutePath().toLowerCase().endsWith(".png")) 
			newFile = new File(mCoversPath + "/" + newUuid + ".png");
		else
			newFile = new File(mCoversPath + "/" + newUuid + ".jpg");

		return newFile;
	}
	/**
	 * Copy a specified source file into the default cover location for a new file.
	 * DO NO OVERWRITE EXISTING FILES.
	 */
	private void copyFileToCoverImageIfMissing(File orig, String newUuid) throws IOException {
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		// Check for ANY current image
		File newFile = getNewCoverFile(orig, newUuid);
		if (newFile.exists())
			return;

		// Copy it.
		InputStream in = null;
		OutputStream out = null;
		try {
			// Open in & out
			in = new FileInputStream(orig);
			out = new FileOutputStream(newFile);
			// Get a buffer
			byte[] buffer = new byte[8192];
			int nRead = 0;
			// Copy
			while( (nRead = in.read(buffer)) > 0){
			    out.write(buffer, 0, nRead);
			}
			// Close both. We close them here so exceptions are signalled
			in.close();
			in = null;
			out.close();
			out = null;
		} finally {
			// If not already closed, close.
			try {
				if (in != null)
					in.close();
			} catch (Exception ignored) {}
            try {
				if (out != null)
					out.close();
			} catch (Exception ignored) {}
        }
	}

	/**
	 * Rename/move a specified source file into the default cover location for a new file.
	 * DO NO OVERWRITE EXISTING FILES.
	 */
	private void renameFileToCoverImageIfMissing(File orig, String newUuid) throws IOException {
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		// Check for ANY current image
		File newFile = getNewCoverFile(orig, newUuid);
		if (newFile.exists())
			return;

		orig.renameTo(newFile);
	}

	/**
	 * Copy the ID-based cover from its current location to the correct location in shared 
	 * storage, if it exists.
	 * 
	 * @param externalId		The file ID in external media
	 * @param newId				The new file ID
	 */
	private void renameCoverImageIfMissing(long externalId, long newId) throws IOException {
		File orig = findExternalCover(Long.toString(externalId));
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		String newUuid = mDbHelper.getBookUuid(newId);

		renameFileToCoverImageIfMissing(orig, newUuid);
	}

	/**
	 * Copy the ID-based cover from its current location to the correct location in shared 
	 * storage, if it exists.
	 * 
	 * @param externalId		The file ID in external media
	 * @param newId				The new file ID
	 */
	private void copyCoverImageIfMissing(long externalId, long newId) throws IOException {
		File orig = findExternalCover(Long.toString(externalId));
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		String newUuid = mDbHelper.getBookUuid(newId);

		copyFileToCoverImageIfMissing(orig, newUuid);
	}

	/**
	 * Copy the UUID-based cover from its current location to the correct location in shared 
	 * storage, if it exists.
	 */
	private void copyCoverImageIfMissing(String uuid) throws IOException {
		File orig = findExternalCover(uuid);
		// Nothing to copy?
		if (orig == null || !orig.exists())
			return;

		copyFileToCoverImageIfMissing(orig, uuid);
	}

}
