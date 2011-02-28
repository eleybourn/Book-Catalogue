/*
 * @copyright 2011 Philip Warner
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

 package com.eleybourn.bookcatalogue;

import java.io.File;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.LibraryThingManager.ImageSizes;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewSwitcher.ViewFactory;

/**
 * Class to display and manage a cover image browser in a dialog.
 *
 * @author Grunthos
 */
public class CoverBrowser {
	// used in setting images sizes
	private android.util.DisplayMetrics mMetrics;
	// Task queue for images
	private SimpleTaskQueue mImageFetcher = null;
	// Handler when an image is finally selected.
	private OnImageSelectedListener mOnImageSelectedListener;
	// ISBN of book to lookup
	private String mIsbn;
	// Calling context
	private Context mContext;
	// Calculated size for preview images
	private final int mPreviewSize;
	// List of all editions for the given ISBN
	private ArrayList<String> mEditions;
	// Object to ensure files are cleaned up.
	private FileManager mFileManager;
	// The Dialog
	private Dialog mDialog = null;
	// Adapted to queue/display images
	private CoverImageAdapter mAdapter = null;

	/**
	 * Interface called when image is selected.
	 * 
	 * @author Grunthos
	 */
	public interface OnImageSelectedListener {
		void onImageSelected(String fileSpec);
	}

	/**
	 * Constructor
	 * 
	 * @param context					Calling context
	 * @param metrics					Display metrics uses in sizing images
	 * @param isbn						ISBN of book
	 * @param onImageSelectedListener	Handler to call when book selected
	 */
	CoverBrowser(Context context, android.util.DisplayMetrics metrics, String isbn, OnImageSelectedListener onImageSelectedListener) {
		mIsbn = isbn;
		mContext = context;
		mMetrics = metrics;
		mOnImageSelectedListener = onImageSelectedListener;

		// Calculate some image sizes to display
		mPreviewSize = Math.max(mMetrics.widthPixels, mMetrics.heightPixels)/5;

		// Create an object to manage the downloaded files
		mFileManager = new FileManager();

		// Get some editions
		// TODO: the list of editions should be expanded to somehow include Amazon and Google. As well
		// as the alternate user-contributed images from LibraryThing. The latter are often the best 
		// source but at present could only be obtained by HTML scraping.
		try {
			mEditions = LibraryThingManager.searchEditions(mIsbn);			
		} catch (Exception e) {
			mEditions = null;
			Toast.makeText(mContext, R.string.no_isbn_no_editions, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Close and abort everything
	 */
	public void dismiss() {
		shutdown();
	}

	/** 
	 * Close down everything
	 */
	private void shutdown() {
		if (mDialog != null) {
			// Dismiss will call shutdown();
			mDialog.dismiss();
			mDialog = null;
		}
		if (mImageFetcher != null) {
			mImageFetcher.finish();
			mImageFetcher = null;
		}
		if (mFileManager != null) {
			mFileManager.purge();
			mFileManager = null;
		}
	}
	/**
	 * SimpleTask to fetch a thumbnail image and apply it to an ImageView
	 * 
	 * @author Grunthos
	 */
	private class GetThumbnailTask implements SimpleTask {
		ImageView 	v;
		int position;
		String isbn;
		String fileSpec;

		/**
		 * Constructor
		 * 
		 * @param position	Position on requested cover.
		 * @param v			View to update
		 */
		GetThumbnailTask(int position, ImageView v){
			this.position = position;
			this.v = v;
			isbn = mEditions.get(position);
		}

		@Override
		public void run() {
			// Start the download
			//Log.i("BC", "SMALL: requesting " + isbn + " at position " + position);
			fileSpec = mFileManager.download(isbn, ImageSizes.SMALL);
		}
		@Override
		public void finished() {
			// Load the file and apply to view
			//Log.i("BC", "SMALL: Found " + isbn + " and position " + position + "->" + fileSpec);
			File file = new File(fileSpec);
		    file.deleteOnExit();
		    //CoverImageAdapter cia = (CoverImageAdapter) gallery.getAdapter();
		    //cia.notifyDataSetChanged();
		    Utils.fetchFileIntoImageView(file, v, mPreviewSize, mPreviewSize, true);
			Log.i("BC", "SMALL: Set " + isbn + " and position " + position + "->" + fileSpec);
		}
	}

	/**
	 * SimpleTask to download an image and apply it to the ImageSwitcher.
	 * 
	 * @author Grunthos
	 */
	private class GetFullImageTask implements SimpleTask {
		// Switcher to use
		private ImageSwitcher 	switcher;
		// Position of edition
		private int position;
		// ISBN
		private String isbn;
		// Resulting file spec
		private String fileSpec;

		/**
		 * Constrcutor
		 * 
		 * @param position		Position f ISBN
		 * @param switcher		ImageSwicther to update
		 */
		GetFullImageTask(int position, ImageSwitcher switcher){
			this.position = position;
			this.switcher = switcher;
			// Get the ISBN
			isbn = mEditions.get(position);
		}
		@Override
		public void run() {
			// Download the file
			//Log.i("BC", "LARGE: requesting " + isbn + " at position " + position);
			fileSpec = mFileManager.download(isbn, ImageSizes.LARGE);
		}
		@Override
		public void finished() {
			// Update the ImageSwitcher
			//Log.i("BC", "LARGE: Found " + isbn + " and position " + position + "->" + fileSpec);
			File file = new File(fileSpec);
            Drawable d = new BitmapDrawable(Utils.fetchFileIntoImageView(file, null, mPreviewSize*4, mPreviewSize*4, true));
            switcher.setImageDrawable(d);
            switcher.setTag(file.getAbsolutePath());
			//Log.i("BC", "LARGE: Set " + isbn + " and position " + position + "->" + fileSpec);
		}
	}

	/**
	 * Show the user a selection of other covers and allow selection of a replacement.
	 */
	public void showEditionCovers() {

		if (mEditions == null) 
			throw new RuntimeException("No editions available");

		// Setup the background fetcher
		if (mImageFetcher == null)
			mImageFetcher = new SimpleTaskQueue();

		// Setup the basic dialog
		mDialog = new Dialog(mContext);
		mDialog.setContentView(R.layout.select_edition_cover);
		mDialog.setTitle(R.string.select_cover);
		
		// The switcher will be used to display larger versions; needed for onItemClick().
		final ImageSwitcher switcher = (ImageSwitcher) mDialog.findViewById(R.id.switcher);

		// Setup the Gallery.
		final Gallery gallery = (Gallery) mDialog.findViewById(R.id.gallery);
		//gallery.setHorizontalScrollBarEnabled(true);
		gallery.setMinimumWidth(mMetrics.widthPixels);
		gallery.setMinimumHeight(mPreviewSize);
		gallery.setSpacing(mPreviewSize/10);


		// Use our custom adapter to load images
		mAdapter = new CoverImageAdapter();
		gallery.setAdapter(mAdapter);

		// When the gallery is clicked, load the switcher
		gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	        	GetFullImageTask task = new GetFullImageTask(position, switcher);
	        	mImageFetcher.request(task);
	        }
	    });

		// When the dialog is closed, delete the files and terminated the SimpleTaskQueue.
		mDialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				shutdown();
			}});

		// When the large image is clicked, send it back to the caller and terminate.
		switcher.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Object newSpec = switcher.getTag();
				if (newSpec != null) {
					if (mOnImageSelectedListener != null)
						mOnImageSelectedListener.onImageSelected((String)newSpec);
				}
				if (mDialog != null)
					mDialog.dismiss();
			}});
		

		// Required object. Just create an ImageView
		switcher.setFactory(new ViewFactory() {
			@Override
			public View makeView() {
		        ImageView i = new ImageView(mContext);
		        i.setBackgroundColor(0xFF000000);
		        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
		        i.setLayoutParams(new ImageSwitcher.LayoutParams(ImageSwitcher.LayoutParams.WRAP_CONTENT,
		        		ImageSwitcher.LayoutParams.WRAP_CONTENT));
			    i.setImageResource(android.R.drawable.ic_menu_help);
		        return i;
			}});

		mDialog.show();

	}

	/**
	 * Simple utility class to (try) to cleanup and prevent files from accumulating.
	 * 
	 * @author Grunthos
	 */
	private class FileManager {
		private Bundle mFiles = new Bundle();

		/**
		 * Download a file if not present and keep a record of it.
		 * 
		 * @param isbn	ISBN of file
		 * @param size	Size of image required.
		 * @return
		 */
		public String download(String isbn, ImageSizes size) {
		    String filespec;
		    String key = isbn + "_" + size;
		    boolean isPresent;
		    synchronized(mFiles) {
		    	isPresent = mFiles.containsKey(key);
		    }
		    
		    if (!isPresent) {
		    	Log.i("BC"," Downloading " + isbn + "(" + size + ")");
		    	filespec = LibraryThingManager.getCoverImage(isbn, null, size);
		    	Log.i("BC"," Downloaded " + isbn + "(" + size + ")->" + filespec);
		    	synchronized(mFiles) {
				    mFiles.putString(key, filespec);		    		
		    	}
		    } else {
		    	synchronized(mFiles) {
			    	filespec = mFiles.getString(key);		    		
		    	}
		    }
			return filespec;
		}

		// Get the requested file, if available, otherwise return null.
		public File getFile(String isbn, ImageSizes size) {
		    String filespec;
		    String key = isbn + "_" + size;
		    boolean isPresent;
		    synchronized(mFiles) {
		    	isPresent = mFiles.containsKey(key);
		    }
		    
		    if (!isPresent)
		    	return null;
	    	synchronized(mFiles) {
		    	filespec = mFiles.getString(key);		    		
	    	}
			return new File(filespec);
		}

		/**
		 * Clean up all files.
		 */
		public void purge() {
			try {
				for(String k : mFiles.keySet()) {
					String filespec = mFiles.getString(k);
					File file = new File(filespec);
					if (file.exists())
						file.delete();
				}				
				mFiles.clear();
			} catch (Exception e) {
				Log.e("BC", " Error purging files", e);
			}
		}
	}

	/**
	 * ImageAdapter for gallery. Queues image requests.
	 * 
	 * @author Grunthos
	 */
	public class CoverImageAdapter extends BaseAdapter {
	    private int mGalleryItemBackground;

	    /**
	     * Constructor
	     */
	    public CoverImageAdapter() {
	    	// Setup the background
	        TypedArray a = mContext.obtainStyledAttributes(R.styleable.CoverGallery);
	        mGalleryItemBackground = a.getResourceId(
	                R.styleable.CoverGallery_android_galleryItemBackground, 0);
	        a.recycle();
	    }
		
	    @Override
		public Object getItem(int position) {
		    return position;
		}
		
	    @Override
		public long getItemId(int position) {
		    return position;
		}
		
	    @Override
		public View getView(int position, View convertView, ViewGroup parent) {
	    	// Create or reuse view
		    ImageView i;
		    if (convertView == null)
			    i = new ImageView(mContext);
		    else 
		    	i = (ImageView)convertView;

		    // Initialize the view
		    i.setScaleType(ImageView.ScaleType.FIT_XY);
		    //i.setAdjustViewBounds(true);
		    i.setMaxHeight(mPreviewSize);
		    i.setMaxWidth(mPreviewSize);
	        i.setBackgroundResource(mGalleryItemBackground);

	        // See if file is present
		    File f = mFileManager.getFile(mEditions.get(position), ImageSizes.SMALL);
		    if (f == null) {
		    	// Not present; request it and use a placeholder.
		    	GetThumbnailTask task = new GetThumbnailTask(position, i);
			    mImageFetcher.request(task);
			    i.setImageResource(android.R.drawable.ic_menu_help);
		    } else {
		    	// Present, so use it.
			    Utils.fetchFileIntoImageView(f, i, mPreviewSize, mPreviewSize, true);
		    }

		    return i;
		}

		@Override
		public int getCount() {
			return mEditions.size();
		}

	}

}
