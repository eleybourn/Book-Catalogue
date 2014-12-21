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
import java.util.ArrayList;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.PathChangedListener;
import com.eleybourn.bookcatalogue.filechooser.FileLister.FileListerListener;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;

/**
 * Base class for an Activity to perform file browsing functions consistent with
 * an Open/Save-As chooser.
 * 
 * @author pjw
 */
public abstract class FileChooser extends BookCatalogueActivity implements
		SimpleTaskQueueProgressFragment.OnAllTasksFinishedListener, SimpleTaskQueueProgressFragment.OnTaskFinishedListener,
		FileLister.FileListerListener, PathChangedListener {

	/** Flag indicating nature of this activity */
	private boolean mIsSaveDialog = false;
	/**
	 * Key for member of EXTRAS that specifies the mode of operation of this
	 * dialog
	 */
	public static final String EXTRA_MODE = "mode";
	/**
	 * Value for member of EXTRAS that specifies the mode of operation of this
	 * dialog
	 */
	public static final String EXTRA_MODE_SAVE_AS = "saveAs";
	/**
	 * Value for member of EXTRAS that specifies the mode of operation of this
	 * dialog
	 */
	public static final String EXTRA_MODE_OPEN = "open";
	/** File name for fragment we display */
	public static final String EXTRA_FILE_NAME = "fileName";

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean isSaveDialog() {
		return mIsSaveDialog;
	}

	/** Create the fragment we display */
	protected abstract FileChooserFragment getChooserFragment();

	/**
	 * Initialize this activity
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.file_chooser_base);

		// Determine the dialog type
		Bundle extras = getIntent().getExtras();
		if (extras == null) {
			mIsSaveDialog = false;
		} else {
			String mode = extras.getString(EXTRA_MODE);
			if (mode == null)
				mIsSaveDialog = false;
			else
				mIsSaveDialog = mode.equals(EXTRA_MODE_SAVE_AS);
		}

		// Get and display the fragment
		FragmentManager fragmentManager = getSupportFragmentManager();
		if (findViewById(R.id.browser_fragment) != null && fragmentManager.findFragmentById(R.id.browser_fragment) == null) {
			// Create the browser
			FileChooserFragment frag = getChooserFragment();
			// frag.setArguments(getIntent().getExtras());
			getSupportFragmentManager().beginTransaction().replace(R.id.browser_fragment, frag).commit();
		}

		// Handle 'Cancel' button
		findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		// Handle Open/Save button
		Button confirm = (Button) findViewById(R.id.confirm);

		if (mIsSaveDialog) {
			confirm.setText(R.string.save);
			confirm.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					handleSave();
				}
			});

		} else {
			confirm.setText(R.string.open);
			confirm.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					handleOpen();
				}
			});
		}

	}

	/**
	 * Implemented by subclass to handle a click on the 'Open' button
	 * 
	 * @param file
	 *            Selected file
	 */
	protected abstract void onOpen(File file);

	/**
	 * Implemented by subclass to handle a click on the 'Save' button
	 * 
	 * @param file
	 *            Selected file
	 */
	protected abstract void onSave(File file);

	/**
	 * Local handler for 'Open'. Perform basic validation, and pass on.
	 */
	private void handleOpen() {
		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.browser_fragment);
		if (frag instanceof FileChooserFragment) {
			FileChooserFragment bf = (FileChooserFragment) frag;
			File file = bf.getSelectedFile();
			if (file == null || !file.exists() || !file.isFile()) {
				Toast.makeText(this, R.string.please_select_an_existing_file, Toast.LENGTH_LONG).show();
				return;
			}
			onOpen(file);
		}
	}

	/**
	 * Local handler for 'Save'. Perform basic validation, and pass on.
	 */
	private void handleSave() {
		Fragment frag = getSupportFragmentManager().findFragmentById(R.id.browser_fragment);
		if (frag instanceof FileChooserFragment) {
			FileChooserFragment bf = (FileChooserFragment) frag;
			File file = bf.getSelectedFile();
			if (file == null || (file.exists() && !file.isFile()) ) {
				Toast.makeText(this, R.string.please_select_a_non_directory, Toast.LENGTH_LONG).show();
				return;
			}
			onSave(file);
		}
	}

	/**
	 * Called by lister fragment to pass on the list of files.
	 */
	@Override
	public void onGotFileList(File root, ArrayList<FileDetails> list) {
		FragmentManager fragmentManager = getSupportFragmentManager();
		Fragment frag = fragmentManager.findFragmentById(R.id.browser_fragment);
		if (frag != null && frag instanceof FileListerListener) {
			((FileListerListener) frag).onGotFileList(root, list);
		}
	}

	/**
	 * Get an object for building an list of files in background.
	 * @param root
	 * @return
	 */
	public abstract FileLister getFileLister(File root);
	
	/**
	 * Rebuild the file list in background; gather whatever data is necessary to
	 * ensure fast building of views in the UI thread.
	 * 
	 * @param root
	 */
	public void onPathChanged(File root) {
		if (root == null || !root.isDirectory())
			return;

		// Create the background task
		FileLister lister = getFileLister(root);

		// Start the task
		SimpleTaskQueueProgressFragment.runTaskWithProgress(this, R.string.searching_directory_ellipsis, lister, true, 0);

	}
}
