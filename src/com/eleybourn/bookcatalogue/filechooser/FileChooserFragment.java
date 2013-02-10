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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueFragment;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

/**
 * Fragment to display a simple directory/file browser.
 * 
 * @author pjw
 *
 * @param <T>		Class for file details, used in showing list.
 */
public abstract class FileChooserFragment<T extends FileDetails> extends BookCatalogueFragment {
	private File mRootPath;
	protected static final String ARG_ROOT_PATH = "rootPath";
	protected static final String ARG_FILE_NAME = "fileName";
	protected static final String ARG_LIST = "list";
	protected ArrayList<T> mList;

	/** Interface for details of files in current directory */
	public interface FileDetails {
		/** Get the uderlying File object */
		File getFile();
	}

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		//checkInstance(a, DirectoryBrowserListener.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.directory_browser, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Hanle the 'up' item; go to the next directory up
		final View root = getView();
		((ImageView) root.findViewById(R.id.up)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				handleUp();
			}
		});

		// If it's new, just buld from scratch, otherwise, get the saved directory and rebuild the list
		if (savedInstanceState == null) {
			mRootPath = new File(getArguments().getString(ARG_ROOT_PATH));
			String fileName = getArguments().getString(ARG_FILE_NAME);
			EditText et = (EditText) getView().findViewById(R.id.file_name);
			et.setText(fileName);
			backgroundRebuild(mRootPath);
		} else {
			mRootPath = new File(savedInstanceState.getString(ARG_ROOT_PATH));
			// List is not Parcelable, so we just rebuild it every time. It's less efficient, but much easier
			//initList(mRootPath, (ArrayList<T>) savedInstanceState.getSerializable(ARG_LIST));
			backgroundRebuild(mRootPath);
		}
	}

	/** Given a list of File objects return a (sub) list of FileDetails objects to display */
	protected abstract ArrayList<T> onRebuild(File[] files);
	/** FileFilter to apply to directory lists */
	protected abstract FileFilter getFileFilter();
	/** Get the layout to use for each row */
	protected abstract int getItemLayout();
	/** Setup a row for one FileDetails object */
	protected abstract void onSetupView(T fileDetails, int position, View target);

	/**
	 * Handle the 'Up' action
	 */
	private void handleUp() {
		String parent = mRootPath.getParent();
		if (parent == null) {
			Toast.makeText(getActivity(), R.string.no_parent_directory_found, Toast.LENGTH_LONG).show();
			return;
		}
		backgroundRebuild(new File(parent));
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putString(ARG_ROOT_PATH, mRootPath.getAbsolutePath());
		// List is not Parcelable, so we just rebuild it every time. It's less efficient, but much easier
		//state.putSerializable(ARG_LIST, mList);
	}

	/**
	 * Compare two FileDetails objects for sorting
	 * 
	 * @author pjw
	 */
	private static class FileDetailsComparator implements Comparator<FileDetails> {
		/**
		 * Perform case-insensitive sorting using defaut locale.
		 */
		@SuppressLint("DefaultLocale")
		public int compare(FileDetails f1, FileDetails f2) {
			return f1.getFile().getName().toUpperCase().compareTo(f2.getFile().getName().toUpperCase());
		}
	}

	private FileDetailsComparator mComparator = new FileDetailsComparator();

	/**
	 * Rebuild the file list in background; gather whatever data is necessary to ensure fast 
	 * building of views in the UI thread.
	 * 
	 * @param root
	 */
	private void backgroundRebuild(final File root) {
		if (root == null || !root.isDirectory())
			return;

		// Set the 'root' path text
		((TextView) getView().findViewById(R.id.path)).setText(root.getAbsolutePath());

		// Create the background task
		FragmentTask task = new FragmentTask() {
			ArrayList<T> dirs;

			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				// Get a file list
				File[] files = root.listFiles(getFileFilter());
				// Filter/fill-in using the subclass
				dirs = onRebuild(files);
				// Sort it
				Collections.sort(dirs, mComparator);
			}

			@Override
			public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
				// Display it in UI thread.
				initList(root, dirs);
			}

		};

		// Star the task
		SimpleTaskQueueProgressFragment.runTaskWithProgress(getActivity(), 0, task, true, 0);

	}

	/**
	 * Display the list
	 * 
	 * @param root		Root directory
	 * @param dirs		List of FileDetials
	 */
	private void initList(File root, ArrayList<T> dirs) {
		mRootPath = root;
		((TextView) getView().findViewById(R.id.path)).setText(mRootPath.getAbsolutePath());

		// Setup and display the list
		mList = dirs;
		DirectoryAdapter adapter = new DirectoryAdapter(getActivity(), getItemLayout(), mList);
		ListView lv = ((ListView) getView().findViewById(android.R.id.list));
		lv.setAdapter(adapter);
	}

	/** Icon to use for Folders */
	protected Drawable mFolderIcon = BookCatalogueApp.context.getResources().getDrawable(R.drawable.ic_closed_folder);

	/**
	 * List Adapter for FileDetails objects
	 * 
	 * @author pjw
	 */
	public class DirectoryAdapter extends SimpleListAdapter<T> {
		boolean series = false;

		/**
		 * 
		 * Pass the parameters directly to the overridden function
		 * 
		 * @param context
		 * @param layout
		 * @param cursor
		 * @param from
		 * @param to
		 */
		public DirectoryAdapter(Context context, int rowViewId, ArrayList<T> items) {
			super(context, rowViewId, items);
		}

		@Override
		protected void onSetupView(T fileDetails, int position, View target) {
			FileChooserFragment.this.onSetupView(fileDetails, position, target);
		}

		@Override
		protected void onRowClick(T fileDetails, int position, View v) {
			if (fileDetails != null) {
				if (fileDetails.getFile().isDirectory()) {
					backgroundRebuild(fileDetails.getFile());
				} else {
					EditText et = (EditText) FileChooserFragment.this.getView().findViewById(R.id.file_name);
					et.setText(fileDetails.getFile().getName());
				}
			}
		};

		@Override
		protected void onListChanged() {
			// Just ignore it. They never change.
		};
	}

	/** 
	 * Accessor
	 * 
	 * @return
	 */
	public File getSelectedFile() {
		EditText et = (EditText) getView().findViewById(R.id.file_name);
		return new File(mRootPath.getAbsolutePath() + "/" + et.getText().toString());
	}

}
