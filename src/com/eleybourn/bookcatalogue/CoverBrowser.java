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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher.ViewFactory;

import com.eleybourn.bookcatalogue.LibraryThingManager.ImageSizes;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.SimpleTaskQueue.SimpleTaskContext;

/**
 * Class to display and manage a cover image browser in a dialog.
 *
 * ENHANCE: For each ISBN returned by LT, add TWO images and get the second from GoodReads
 * ENHANCE: (Somehow) remove non-existent images from ImageSelector. Probably start with 1 image and GROW it.
 * 
 * @author Philip Warner
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
	// Libary Thing
	private LibraryThingManager mLibraryThing;
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
	/** Indicates a 'shutdown()' has been requested */
	private boolean mShutdown = false;

	/**
	 * Interface called when image is selected.
	 * 
	 * @author Philip Warner
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
		mShutdown = true;

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
	 * @author Philip Warner
	 */
	private class GetEditionsTask implements SimpleTask {
		String isbn;

		/**
		 * Constructor
		 * 
		 * @param position	Position on requested cover.
		 * @param v			View to update
		 */
		GetEditionsTask(String isbn){
			this.isbn = isbn;
		}

		@Override
		public void run(SimpleTaskContext taskContext) {
			// Get some editions
			// ENHANCE: the list of editions should be expanded to somehow include Amazon and Google. As well
			// as the alternate user-contributed images from LibraryThing. The latter are often the best 
			// source but at present could only be obtained by HTML scraping.
			try {
				mEditions = LibraryThingManager.searchEditions(isbn);			
			} catch (Exception e) {
				mEditions = null;
			}
		}
		@Override
		public void onFinish() {
			if (mEditions.size() == 0) {
				Toast.makeText(mContext, R.string.no_editions, Toast.LENGTH_LONG).show();
				shutdown();
				return;
			}
			showDialogDetails();
		}

		/**
		 * Always want the finished() method to be called.
		 */
		@Override
		public boolean requiresOnFinish() {
			return true;
		}
	}

	/**
	 * SimpleTask to fetch a thumbnail image and apply it to an ImageView
	 * 
	 * @author Philip Warner
	 */
	@SuppressWarnings("unused")
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
		public void run(SimpleTaskContext taskContext) {
			// Start the download
			fileSpec = mFileManager.download(isbn, ImageSizes.SMALL);
			File file = new File(fileSpec);
			if (file.length() < 50) {
				fileSpec = mFileManager.download(isbn, ImageSizes.LARGE);
			}
		}
		@Override
		public void onFinish() {
			// Load the file and apply to view
			File file = new File(fileSpec);
			file.deleteOnExit();
			//CoverImageAdapter cia = (CoverImageAdapter) gallery.getAdapter();
			//cia.notifyDataSetChanged();
			Utils.fetchFileIntoImageView(file, v, mPreviewSize, mPreviewSize, true );
		}

		@Override
		public boolean requiresOnFinish() {
			return true;
		}
	}

	/**
	 * SimpleTask to download an image and apply it to the ImageSwitcher.
	 * 
	 * @author Philip Warner
	 */
	@SuppressWarnings("unused")
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
		 * Constructor
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
		public void run(SimpleTaskContext taskContext) {
			// If we are shutdown, just return
			if (mShutdown)
				return;

			// Download the file
			fileSpec = mFileManager.download(isbn, ImageSizes.LARGE);
			File file = new File(fileSpec);
			if (file.length() < 50) {
				fileSpec = mFileManager.download(isbn, ImageSizes.SMALL);
			}
		}
		@Override
		public void onFinish() {
			// Update the ImageSwitcher
			File file = new File(fileSpec);
			TextView msgVw = (TextView)mDialog.findViewById(R.id.switcherStatus);
			if (file.exists() && file.length() > 100) {
				Drawable d = new BitmapDrawable(Utils.fetchFileIntoImageView(file, null, mPreviewSize*4, mPreviewSize*4, true ));
				switcher.setImageDrawable(d);
				ViewTagger.setTag(switcher, file.getAbsolutePath());    			
				msgVw.setVisibility(View.GONE);
				switcher.setVisibility(View.VISIBLE);
			} else {
				msgVw.setVisibility(View.VISIBLE);
				switcher.setVisibility(View.GONE);
				msgVw.setText(R.string.image_not_found);
			}
		}
		@Override
		public boolean requiresOnFinish() {
			return !mShutdown;
		}
	}

	/**
	 * Show the user a selection of other covers and allow selection of a replacement.
	 */
	public void showEditionCovers() {

		mLibraryThing = new LibraryThingManager(mContext);
		if (!mLibraryThing.isAvailable()) {
			StandardDialogs.needLibraryThingAlert(mContext, true, "cover_browser");
			return;
		}

		if (mIsbn == null || mIsbn.trim().length() == 0) {
			Toast.makeText(mContext, R.string.no_isbn_no_editions, Toast.LENGTH_LONG).show();
			shutdown();
			return;
		}

		// Setup the background fetcher
		if (mImageFetcher == null)
			mImageFetcher = new SimpleTaskQueue("cover-browser");

		SimpleTask edTask = new GetEditionsTask(mIsbn);
		mImageFetcher.enqueue(edTask);

		// Setup the basic dialog
		mDialog = new Dialog(mContext);
		mDialog.setContentView(R.layout.select_edition_cover);
		mDialog.setTitle(R.string.finding_editions);

		//TextView msgVw = (TextView)mDialog.findViewById(R.id.status);
		//msgVw.setText(R.string.finding_editions);

		mDialog.show();

	}

	private void showDialogDetails() {
		mDialog.setTitle(R.string.select_cover);

		// The switcher will be used to display larger versions; needed for onItemClick().
		final ImageSwitcher switcher = (ImageSwitcher) mDialog.findViewById(R.id.switcher);

		// Setup the Gallery.
		final Gallery gallery = (Gallery) mDialog.findViewById(R.id.gallery);
		gallery.setVisibility(View.VISIBLE);

		// Show help message
		TextView msgVw = (TextView)mDialog.findViewById(R.id.switcherStatus);
		msgVw.setText(R.string.click_on_thumb);
		msgVw.setVisibility(View.VISIBLE);

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
	    		// Show status message
	    		TextView msgVw = (TextView)mDialog.findViewById(R.id.switcherStatus);
        		switcher.setVisibility(View.GONE);
	    		msgVw.setText(R.string.loading);
        		msgVw.setVisibility(View.VISIBLE);

	    		GetFullImageTask task = new GetFullImageTask(position, switcher);
	        	mImageFetcher.enqueue(task);
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
				Object newSpec = ViewTagger.getTag(switcher);
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

	}

	/**
	 * Simple utility class to (try) to cleanup and prevent files from accumulating.
	 * 
	 * @author Philip Warner
	 */
	private class FileManager {
		private Bundle mFiles = new Bundle();
    	LibraryThingManager mLibraryThing = new LibraryThingManager(mContext);

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

		    // Do some checks on the actual file to see if a re-download may help
		    if (isPresent) {
			    synchronized(mFiles) {
		    		filespec = mFiles.getString(key);			    	
			    }
		    	File f = new File(filespec);
		    	boolean isBad = false;
		    	if (!f.exists()) {
		    		isBad = true;
		    	} else if (f.length() == 0) {
		    		f.delete();
		    		isBad = true;
		    	}
		    	if (isBad) {
		    		mFiles.remove(key);
		    		isPresent = false;		    		
		    	}
		    }

		    if (!isPresent) {
		    	filespec = mLibraryThing.getCoverImage(isbn, null, size);
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
				Logger.logError(e);
			}
		}
	}

	/**
	 * ImageAdapter for gallery. Queues image requests.
	 * 
	 * @author Philip Warner
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

			// If we are shutdown, just return a view
			if (mShutdown)
				return i;

			// Initialize the view
			i.setScaleType(ImageView.ScaleType.FIT_XY);
			//i.setAdjustViewBounds(true);
			i.setMaxHeight(mPreviewSize);
			i.setMaxWidth(mPreviewSize);
			i.setBackgroundResource(mGalleryItemBackground);
			
			// See if file is present
			File f = null;
			try {
				f = mFileManager.getFile(mEditions.get(position), ImageSizes.SMALL);
			} catch (NullPointerException e) {
				//file did not exist. Dealt with later.
			}
			if (f == null) {
				// Not present; request it and use a placeholder.
				GetThumbnailTask task = new GetThumbnailTask(position, i);
				mImageFetcher.enqueue(task);
				i.setImageResource(android.R.drawable.ic_menu_help);
			} else {
				// Present, so use it.
				Utils.fetchFileIntoImageView(f, i, mPreviewSize, mPreviewSize, true );
			}
			
			return i;
		}
		
		@Override
		public int getCount() {
			return mEditions.size();
		}

	}

}
