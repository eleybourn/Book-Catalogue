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

import android.app.Activity;
import android.content.Context;
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

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.eleybourn.bookcatalogue.BookCatalogue;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueFragment;
import com.eleybourn.bookcatalogue.filechooser.FileLister.FileListerListener;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter.ViewProvider;

/**
 * Fragment to display a simple directory/file browser.
 * 
 * @author pjw
 *
 * @param <T>		Class for file details, used in showing list.
 */
public class FileChooserFragment extends BookCatalogueFragment implements FileListerListener {
	private File mRootPath;
	protected static final String ARG_ROOT_PATH = "rootPath";
	protected static final String ARG_FILE_NAME = "fileName";
	protected static final String ARG_LIST = "list";
	// Create an empty one in case we are rotated before generated.
	protected ArrayList<FileDetails> mList = new ArrayList<FileDetails>();

	/**
	 * Interface that the containing Activity must implement. Called when user changes path.
	 *
	 * @author pjw
	 */
	public interface PathChangedListener {
		public void onPathChanged(File root);
	}

	/** Create a new chooser fragment */
	public static FileChooserFragment newInstance(File root, String fileName) {
		String path;
		// Turn the passed File into a directory
		if (root.isDirectory()) {
			path = root.getAbsolutePath();
		} else {
			path = root.getParent();
		}
		
		// Build the fragment and save the details
		FileChooserFragment frag = new FileChooserFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOT_PATH, path);
        args.putString(ARG_FILE_NAME, fileName);
        frag.setArguments(args);

        return frag;
	}

	/** Interface for details of files in current directory */
	public interface FileDetails extends ViewProvider, Parcelable {
		/** Get the underlying File object */
		File getFile();
		/** Called to fill in the defails of this object in the View provided by the ViewProvider implementation */
		public void onSetupView(Context context, int position, View target);
	}

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);

		checkInstance(a, PathChangedListener.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		return inflater.inflate(R.layout.file_chooser, container, false);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		MenuItem home = menu.add(0, R.id.MENU_CHOOSER_HOME, 0, R.string.home);
		home.setIcon(R.drawable.ic_home_dark);
		home.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {

			case R.id.MENU_CHOOSER_HOME:

				handleHome();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Handle the 'up' item; go to the next directory up
		final View root = getView();
		((ImageView) root.findViewById(R.id.up)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				handleUp();
			}
		});

		// If it's new, just build from scratch, otherwise, get the saved directory and list
		if (savedInstanceState == null) {
			mRootPath = new File(getArguments().getString(ARG_ROOT_PATH));
			String fileName = getArguments().getString(ARG_FILE_NAME);
			EditText et = (EditText) getView().findViewById(R.id.file_name);
			et.setText(fileName);
			((TextView) getView().findViewById(R.id.path)).setText(mRootPath.getAbsolutePath());
			tellActivityPathChanged();
		} else {
			mRootPath = new File(savedInstanceState.getString(ARG_ROOT_PATH));
			ArrayList<FileDetails> list = savedInstanceState.getParcelableArrayList(ARG_LIST);
			this.onGotFileList(mRootPath, list);
		}
	}

	/**
	 * Convenience method to tell our activity the path has changed.
	 */
	private void tellActivityPathChanged() {	
		((PathChangedListener)getActivity()).onPathChanged(mRootPath);
	}

	private void handleHome() {
		String homeDir = StorageUtils.getSharedStoragePath();
		if (homeDir.isEmpty()) {
			Toast.makeText(getActivity(), R.string.no_home_directory_found, Toast.LENGTH_LONG).show();
			return;
		}
		File tmp = new File(homeDir);
		if (!tmp.exists()) {
			Toast.makeText(getActivity(), R.string.no_home_directory_found, Toast.LENGTH_LONG).show();
			return;
		}
		mRootPath = tmp;
		tellActivityPathChanged();
	}

	/**
	 * Handle the 'Up' action
	 */
	private void handleUp() {
		String parent = mRootPath.getParent();
		if (parent == null) {
			Toast.makeText(getActivity(), R.string.no_parent_directory_found, Toast.LENGTH_LONG).show();
			return;
		}
		// Be a little paranoid and don't go to a parent we can't get back from
		File tmp = new File(parent);
		Boolean ok = tmp.exists();
		if (ok) {
			String[] list = tmp.list();
			ok = list != null && list.length > 0;
		}
		if (!ok) {
			Toast.makeText(getActivity(), R.string.no_parent_directory_found, Toast.LENGTH_LONG).show();
			return;
		}

		mRootPath = tmp;
		tellActivityPathChanged();
	}

	/**
	 * Save our root path and list
	 */
	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putString(ARG_ROOT_PATH, mRootPath.getAbsolutePath());
		state.putParcelableArrayList(ARG_LIST, mList);
	}

	/**
	 * List Adapter for FileDetails objects
	 * 
	 * @author pjw
	 */
	public class DirectoryAdapter extends SimpleListAdapter<FileDetails> {
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
		public DirectoryAdapter(Context context, int rowViewId, ArrayList<FileDetails> items) {
			super(context, rowViewId, items);
		}

		@Override
		protected void onSetupView(FileDetails fileDetails, int position, View target) {
			fileDetails.onSetupView(getActivity(), position, target);
		}

		@Override
		protected void onRowClick(FileDetails fileDetails, int position, View v) {
			if (fileDetails != null) {
				if (fileDetails.getFile().isDirectory()) {
					mRootPath = fileDetails.getFile();
					tellActivityPathChanged();
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

	/**
	 * Display the list
	 * 
	 * @param root		Root directory
	 * @param dirs		List of FileDetials
	 */
	@Override
	public void onGotFileList(File root, ArrayList<FileDetails> list) {
		mRootPath = root;
		((TextView) getView().findViewById(R.id.path)).setText(mRootPath.getAbsolutePath());

		// Setup and display the list
		mList = list;
		// We pass 0 as view ID since each item can provide the view id
		DirectoryAdapter adapter = new DirectoryAdapter(getActivity(), 0, mList);
		ListView lv = ((ListView) getView().findViewById(android.R.id.list));
		lv.setAdapter(adapter);
	}

}
