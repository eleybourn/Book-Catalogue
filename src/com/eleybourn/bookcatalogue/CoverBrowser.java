package com.eleybourn.bookcatalogue;

import java.io.File;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.LibraryThingManager.ImageSizes;

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

public class CoverBrowser {
	private android.util.DisplayMetrics mMetrics;
	private ImageFetcher<ImageInfo> mImageFetcher = null;
	private OnImageSelectedListener mOnImageSelectedListener;
	private String mIsbn;
	private Context mContext;
	private final int mPreviewSize;
	private ArrayList<String> mEditions;
	private final FileManager mFileManager;
	private Dialog mDialog = null;
	private CoverImageAdapter mAdapter = null;
	
	CoverBrowser(Context context, android.util.DisplayMetrics metrics, String isbn, OnImageSelectedListener onImageSelectedListener) {
		mIsbn = isbn;
		mContext = context;
		mMetrics = metrics;
		mOnImageSelectedListener = onImageSelectedListener;

		// Calculate some image sizes to display
		mPreviewSize = Math.max(mMetrics.widthPixels, mMetrics.heightPixels)/5;

		// Get some editions
		// TODO: the list of editions should be expanded somehow
		ArrayList<String> editions;

		// Create an object to manage the downloaded files
		mFileManager = new FileManager();

		try {
			editions = LibraryThingManager.searchEditions(mIsbn);			
		} catch (Exception e) {
			editions = null;
			Toast.makeText(mContext, R.string.no_isbn_no_editions, Toast.LENGTH_LONG).show();
		}
		mEditions = editions;
	}

	public interface OnImageSelectedListener {
		void onImageSelected(String fileSpec);
	}

	private class ImageInfo {
		Bundle 		b;
		ImageView 	v;
		ImageInfo() {
			b = new Bundle();
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
			mImageFetcher = new ImageFetcher<ImageInfo>();

		// Setup the basic dialog
		mDialog = new Dialog(mContext);
		mDialog.setContentView(R.layout.select_edition_cover);
		mDialog.setTitle(R.string.select_cover);
		
		// The swicther will be used to display larger versions; needed for onItemClick().
		final ImageSwitcher switcher = (ImageSwitcher) mDialog.findViewById(R.id.switcher);

		// Setup the Gallery.
		final Gallery gallery = (Gallery) mDialog.findViewById(R.id.gallery);
		//gallery.setHorizontalScrollBarEnabled(true);
		gallery.setMinimumWidth(mMetrics.widthPixels);
		gallery.setMinimumHeight(mPreviewSize);
		gallery.setSpacing(mPreviewSize/10);

		final ImageFetcher.RequestHandler<ImageInfo> largeRequestHandler = new ImageFetcher.RequestHandler<ImageInfo>() {
			@Override
			public void get(ImageInfo context) {
				int position = context.b.getInt("position");
				String isbn = mEditions.get(position);
				Log.i("BC", "LARGE: requesting " + isbn + " at position " + position);
	            context.b.putString("filespec", mFileManager.download(isbn, ImageSizes.LARGE));
			}
			@Override
			public void done(ImageInfo context) {
				int position = context.b.getInt("position");
				String isbn = mEditions.get(position);
				String filespec = context.b.getString("filespec");
				Log.i("BC", "LARGE: Found " + isbn + " and position " + position + "->" + filespec);
				File file = new File(filespec);
	            Drawable d = new BitmapDrawable(Utils.fetchFileIntoImageView(file, null, mPreviewSize*4, mPreviewSize*4, true));
	            switcher.setImageDrawable(d);
	            switcher.setTag(file.getAbsolutePath());
				Log.i("BC", "LARGE: Set " + isbn + " and position " + position + "->" + filespec);
			}};
		final ImageFetcher.RequestHandler<ImageInfo> thumbRequestHandler = new ImageFetcher.RequestHandler<ImageInfo>() {
			@Override
			public void get(ImageInfo context) {
				int position = context.b.getInt("position");
				String isbn = mEditions.get(position);
				Log.i("BC", "SMALL: requesting " + isbn + " at position " + position);
				context.b.putString("filespec", mFileManager.download(isbn, ImageSizes.SMALL));
			}
			@Override
			public void done(ImageInfo context) {
				int position = context.b.getInt("position");
				String isbn = mEditions.get(position);
				String filespec = context.b.getString("filespec");
				Log.i("BC", "SMALL: Found " + isbn + " and position " + position + "->" + filespec);
				File file = new File(filespec);
			    file.deleteOnExit();
			    //CoverImageAdapter cia = (CoverImageAdapter) gallery.getAdapter();
			    //cia.notifyDataSetChanged();
			    Utils.fetchFileIntoImageView(file, context.v, mPreviewSize, mPreviewSize, true);
				Log.i("BC", "SMALL: Set " + isbn + " and position " + position + "->" + filespec);
			}};

		// Use our custom adapter to load images
		mAdapter = new CoverImageAdapter(thumbRequestHandler);
		gallery.setAdapter(mAdapter);

		gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	        	ImageInfo info = new ImageInfo();
	        	info.b.putInt("position", position);
	        	mImageFetcher.request(info, largeRequestHandler);
	        }
	    });

		mDialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				mFileManager.purge();
				mImageFetcher.finish();
				mImageFetcher = null;
			}});

		switcher.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Object newSpec = switcher.getTag();
				if (newSpec != null) {
					if (mOnImageSelectedListener != null)
						mOnImageSelectedListener.onImageSelected((String)newSpec);
				}
				mDialog.dismiss();
			}});
		
		
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

	private class FileManager {
		private Bundle mFiles = new Bundle();

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

	public class CoverImageAdapter extends BaseAdapter {
	    private ImageFetcher.RequestHandler<ImageInfo> mThumbRequestHandler;
	    private int mGalleryItemBackground;

	    public CoverImageAdapter(ImageFetcher.RequestHandler<ImageInfo> handler) {
	        mThumbRequestHandler = handler;
	        TypedArray a = mContext.obtainStyledAttributes(R.styleable.HelloGallery);
	        mGalleryItemBackground = a.getResourceId(
	                R.styleable.HelloGallery_android_galleryItemBackground, 0);
	        a.recycle();
	    }
			
		public Object getItem(int position) {
		    return position;
		}
		
		public long getItemId(int position) {
		    return position;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
		    ImageView i;
		    if (convertView == null)
			    i = new ImageView(mContext);
		    else 
		    	i = (ImageView)convertView;

		    i.setScaleType(ImageView.ScaleType.FIT_XY);
		    //i.setAdjustViewBounds(true);
		    i.setMaxHeight(mPreviewSize);
		    i.setMaxWidth(mPreviewSize);
		    //i.setMinimumHeight(mPreviewSize);
		    //i.setMinimumWidth(mPreviewSize);
	        i.setBackgroundResource(mGalleryItemBackground);

		    File f = mFileManager.getFile(mEditions.get(position), ImageSizes.SMALL);
		    if (f == null) {
			    ImageInfo info = new ImageInfo();
			    info.v = i;
			    info.b.putInt("position", position);
			    mImageFetcher.request(info, mThumbRequestHandler);
			    i.setImageResource(android.R.drawable.ic_menu_help);
		    } else {
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
