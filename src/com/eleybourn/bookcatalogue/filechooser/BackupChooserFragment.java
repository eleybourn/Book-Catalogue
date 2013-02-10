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
package com.eleybourn.bookcatalogue.filechooser;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupInfo;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupReader;
import com.eleybourn.bookcatalogue.filechooser.BackupChooserFragment.BackupFileDetails;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Fragment to display a list of dirs and archive files.
 * 
 * @author pjw
 */
public class BackupChooserFragment extends FileChooserFragment<BackupFileDetails> {
	/** Pattern to match archive file names */
	private static Pattern mBackupFilePattern = Pattern.compile(".bcbk$", Pattern.CASE_INSENSITIVE);

	/** Icon to use for Backup files */
	protected Drawable mBackupIcon = BookCatalogueApp.context.getResources().getDrawable(R.drawable.ic_archive);

	/** Create a new chooser fragment */
	public static BackupChooserFragment newInstance(File root, String fileName) {
		String path;
		if (root.isDirectory()) {
			path = root.getAbsolutePath();
		} else {
			path = root.getParent();
		}
		
		// Build the fragment and save the details
		BackupChooserFragment frag = new BackupChooserFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOT_PATH, path);
        args.putString(ARG_FILE_NAME, fileName);
        frag.setArguments(args);
        
        return frag;
	}

	/**
	 * Implementation of FileDetails that record data about backup files.
	 * 
	 * @author pjw
	 */
	public static class BackupFileDetails implements FileDetails {
		File file;
		BackupInfo info;
		@Override
		public File getFile() {
			return file;
		}
	}

	/**
	 * Return the layout we use
	 */
	protected int getItemLayout() {
		return R.layout.directory_browser_item_backup;
	}

	/**
	 * Filter files for directories we can write to ad archive files
	 */
	@Override
	public FileFilter getFileFilter() {
		return new FileFilter() {
			@Override
			public boolean accept(File f) {
				return (f.isDirectory() && f.canWrite() ) || (f.isFile() && mBackupFilePattern.matcher(f.getName()).find());
			}
		};
	}

	/**
	 * Get details for each backup file in the list
	 */
	protected ArrayList<BackupFileDetails> onRebuild(File[] files) {
		ArrayList<BackupFileDetails> dirs = new ArrayList<BackupFileDetails>();

		if (files != null) {
			for(File f: files) {
				BackupFileDetails fd = new BackupFileDetails();
				fd.file = f;
				dirs.add(fd);
				if (f.getName().toUpperCase().endsWith(".BCBK")) {
					try {
						BackupReader reader = BackupManager.readBackup(f);
						fd.info = reader.getInfo();
						reader.close();
					} catch (IOException e) {
						Logger.logError(e);
					}
				}
			}
		}
		return dirs;
	}

	/** Default locale for string conversions */
	private Locale mLocale = Locale.getDefault();
	
	/**
	 * Build the view we display
	 */
	protected void onSetupView(BackupFileDetails fileDetails, int position, View target) {
		File file = fileDetails.file;
		
		// Set the basic data
		TextView name = (TextView)target.findViewById(R.id.name);
		name.setText(file.getName());
		TextView date = (TextView)target.findViewById(R.id.date);
		ImageView image = (ImageView)target.findViewById(R.id.icon);
		TextView details = (TextView)target.findViewById(R.id.details);
		
		// For directories, hide the extra data
		if (file.isDirectory()) {
			date.setVisibility(View.GONE);
			details.setVisibility(View.GONE);
			image.setImageDrawable(mFolderIcon);
		} else {
			// Display date and backup details
			image.setImageDrawable(mBackupIcon);
			date.setVisibility(View.VISIBLE);
			if (fileDetails.info != null) {
				BackupInfo info = fileDetails.info;
				details.setVisibility(View.VISIBLE);
				details.setText(info.getBookCount() + " books");	
				date.setText(Utils.formatFileSize(file.length()) + ",  " + DateFormat.getDateTimeInstance().format(info.getCreateDate()));
			} else {
				date.setText(Utils.formatFileSize(file.length()) + ",  " + DateFormat.getDateTimeInstance().format(new Date(file.lastModified())));
				details.setVisibility(View.GONE);
			}
		}

	}
}
