/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.R.id;
import com.eleybourn.bookcatalogue.R.layout;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;



/**
 * The activity can crop specific region of interest from an image.
 */
public class CropCropImage extends CropMonitoredActivity {
    private static final String TAG = "CropImage";

    // These are various options can be specified in the intent.
    private Bitmap.CompressFormat mOutputFormat =
	Bitmap.CompressFormat.JPEG; // only used with mSaveUri
    private Uri mSaveUri = null;
    private int mAspectX, mAspectY;
    private boolean mCircleCrop = false;
    private final Handler mHandler = new Handler();

    // These options specifiy the output image size and whether we should
    // scale the output to fit it (or just crop it).
    private int mOutputX, mOutputY;
    private boolean mScale;
    private boolean mScaleUp = true;

    private boolean mDoFaceDetection = true;

    boolean mWaitingToPick; // Whether we are wait the user to pick a face.
    boolean mSaving;  // Whether the "save" button is already clicked.

    private CropImageView mImageView;
    private ContentResolver mContentResolver;

    private Bitmap mBitmap;
    private final CropBitmapManager.ThreadSet mDecodingThreads =
	new CropBitmapManager.ThreadSet();
    CropHighlightView mCrop;

    private CropIImage mImage;

    private String mImagePath;

    @Override
    public void onCreate(Bundle icicle) {
	super.onCreate(icicle);
	mContentResolver = getContentResolver();

	requestWindowFeature(Window.FEATURE_NO_TITLE);
	setContentView(R.layout.cropcropimage);

	mImageView = (CropImageView) findViewById(R.id.image);

	showStorageToast(this);

	Intent intent = getIntent();
	Bundle extras = intent.getExtras();
	if (extras != null) {
	    if (extras.getString("circleCrop") != null) {
		mCircleCrop = true;
		mAspectX = 1;
		mAspectY = 1;
	    }
	    
	    mImagePath = extras.getString("image-path");

	    // Use the "output" parameter if present, otherwise overwrite existing file
	    String imgUri = extras.getString("output");
	    if (imgUri == null) 
	    	imgUri = mImagePath;

	    mSaveUri = getImageUri(imgUri);
	   
	    mBitmap = getBitmap(mImagePath);

	    mAspectX = extras.getInt("aspectX");
	    mAspectY = extras.getInt("aspectY");
	    mOutputX = extras.getInt("outputX");
	    mOutputY = extras.getInt("outputY");
	    mScale = extras.getBoolean("scale", true);
	    mScaleUp = extras.getBoolean("scaleUpIfNeeded", true);
	}



	if (mBitmap == null) {
	    finish();
	    return;
	}

	// Make UI fullscreen.
	getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

	findViewById(R.id.discard).setOnClickListener(
		new View.OnClickListener() {
		    public void onClick(View v) {
			setResult(RESULT_CANCELED);
			finish();
		    }
		});

	findViewById(R.id.save).setOnClickListener(
		new View.OnClickListener() {
		    public void onClick(View v) {
			onSaveClicked();
		    }
		});
	startFaceDetection();
    }
    
    private Uri getImageUri(String path) {
	return Uri.fromFile(new File(path));
    }

    private Bitmap getBitmap(String path) {
	Uri uri = getImageUri(path);
	InputStream in = null;
	try {
	    in = mContentResolver.openInputStream(uri);
	    return BitmapFactory.decodeStream(in);
	} catch (FileNotFoundException e) {
	}
	return null;
    }


    private void startFaceDetection() {
	if (isFinishing()) {
	    return;
	}

	mImageView.setImageBitmapResetBase(mBitmap, true);

	CropUtil.startBackgroundJob(this, null,
		"Please wait\u2026",
		new Runnable() {
	    public void run() {
		final CountDownLatch latch = new CountDownLatch(1);
		final Bitmap b = (mImage != null)
		? mImage.fullSizeBitmap(CropIImage.UNCONSTRAINED,
			1024 * 1024)
			: mBitmap;
		mHandler.post(new Runnable() {
		    public void run() {
			if (b != mBitmap && b != null) {
				// Do not recycle until mBitmap has been set to the new bitmap!
				Bitmap toRecycle = mBitmap;
			    mBitmap = b;
			    mImageView.setImageBitmapResetBase(mBitmap, true);
			    toRecycle.recycle();
			}
			if (mImageView.getScale() == 1F) {
			    mImageView.center(true, true);
			}
			latch.countDown();
		    }
		});
		try {
		    latch.await();
		} catch (InterruptedException e) {
		    throw new RuntimeException(e);
		}
		mRunFaceDetection.run();
	    }
	}, mHandler);
    }



    private void onSaveClicked() {
	// TODO this code needs to change to use the decode/crop/encode single
	// step api so that we don't require that the whole (possibly large)
	// bitmap doesn't have to be read into memory
	if (mSaving) return;

	if (mCrop == null) {
	    return;
	}

	mSaving = true;

	Rect r = mCrop.getCropRect();

	int width = r.width();
	int height = r.height();

	// If we are circle cropping, we want alpha channel, which is the
	// third param here.
	Bitmap croppedImage = Bitmap.createBitmap(width, height,
		mCircleCrop
		? Bitmap.Config.ARGB_8888
			: Bitmap.Config.RGB_565);
	{
	    Canvas canvas = new Canvas(croppedImage);
	    Rect dstRect = new Rect(0, 0, width, height);
	    canvas.drawBitmap(mBitmap, r, dstRect, null);
	}

	if (mCircleCrop) {
	    // OK, so what's all this about?
	    // Bitmaps are inherently rectangular but we want to return
	    // something that's basically a circle.  So we fill in the
	    // area around the circle with alpha.  Note the all important
	    // PortDuff.Mode.CLEAR.
	    Canvas c = new Canvas(croppedImage);
	    Path p = new Path();
	    p.addCircle(width / 2F, height / 2F, width / 2F,
		    Path.Direction.CW);
	    c.clipPath(p, Region.Op.DIFFERENCE);
	    c.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
	}

	/* If the output is required to a specific size then scale or fill */
	if (mOutputX != 0 && mOutputY != 0) {
	    if (mScale) {
		/* Scale the image to the required dimensions */
		Bitmap old = croppedImage;
		croppedImage = CropUtil.transform(new Matrix(),
			croppedImage, mOutputX, mOutputY, mScaleUp);
		if (old != croppedImage) {
		    old.recycle();
		}
	    } else {

		/* Don't scale the image crop it to the size requested.
		 * Create an new image with the cropped image in the center and
		 * the extra space filled.
		 */

		// Don't scale the image but instead fill it so it's the
		// required dimension
		Bitmap b = Bitmap.createBitmap(mOutputX, mOutputY,
			Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(b);

		Rect srcRect = mCrop.getCropRect();
		Rect dstRect = new Rect(0, 0, mOutputX, mOutputY);

		int dx = (srcRect.width() - dstRect.width()) / 2;
		int dy = (srcRect.height() - dstRect.height()) / 2;

		/* If the srcRect is too big, use the center part of it. */
		srcRect.inset(Math.max(0, dx), Math.max(0, dy));

		/* If the dstRect is too big, use the center part of it. */
		dstRect.inset(Math.max(0, -dx), Math.max(0, -dy));

		/* Draw the cropped bitmap in the center */
		canvas.drawBitmap(mBitmap, srcRect, dstRect, null);

		/* Set the cropped bitmap as the new bitmap */
		croppedImage.recycle();
		croppedImage = b;
	    }
	}

	// Return the cropped image directly or save it to the specified URI.
	Bundle myExtras = getIntent().getExtras();
	if (myExtras != null && (myExtras.getParcelable("data") != null
		|| myExtras.getBoolean("return-data"))) {
	    Bundle extras = new Bundle();
	    extras.putParcelable("data", croppedImage);
	    setResult(RESULT_OK,
		    (new Intent()).setAction("inline-data").putExtras(extras));
	    finish();
	} else {
	    final Bitmap b = croppedImage;
	    CropUtil.startBackgroundJob(this, null,"Saving image",
		    new Runnable() {
		public void run() {
		    saveOutput(b);
		}
	    }, mHandler);
	}
    }

    private void saveOutput(Bitmap croppedImage) {
	if (mSaveUri != null) {
	    OutputStream outputStream = null;
	    try {
		outputStream = mContentResolver.openOutputStream(mSaveUri);
		if (outputStream != null) {
		    croppedImage.compress(mOutputFormat, 75, outputStream);
		}
	    } catch (IOException ex) {
		// TODO: report error to caller
	    } finally {
		CropUtil.closeSilently(outputStream);
	    }
	    Bundle extras = new Bundle();
	    setResult(RESULT_OK, new Intent(mSaveUri.toString())
	    .putExtras(extras));
	} else {
	    /*Bundle extras = new Bundle();
	    extras.putString("rect", mCrop.getCropRect().toString());

	    File oldPath = new File(mImage.getDataPath());
	    File directory = new File(oldPath.getParent());

	    int x = 0;
	    String fileName = oldPath.getName();
	    fileName = fileName.substring(0, fileName.lastIndexOf("."));

	    // Try file-1.jpg, file-2.jpg, ... until we find a filename which
	    // does not exist yet.
	    while (true) {
		x += 1;
		String candidate = directory.toString()
		+ "/" + fileName + "-" + x + ".jpg";
		boolean exists = (new File(candidate)).exists();
		if (!exists) {
		    break;
		}
	    }

	    try {
		Uri newUri = ImageManager.addImage(
			mContentResolver,
			mImage.getTitle(),
			mImage.getDateTaken(),
			null,    // TODO this null is going to cause us to lose
			// the location (gps).
			0,       // TODO this is going to cause the orientation
			// to reset.
			directory.toString(),
			fileName + "-" + x + ".jpg");

		 Cancelable<Void> cancelable =
                        ImageManager.storeImage(
                        newUri,
                        mContentResolver,
                        0, // TODO fix this orientation
                        croppedImage,
                        null);

                cancelable.get();
		setResult(RESULT_OK, new Intent()
		.setAction(newUri.toString())
		.putExtras(extras));
	    } catch (Exception ex) {
		// basically ignore this or put up
		// some ui saying we failed
		Log.e(TAG, "store image fail, continue anyway", ex);
	    }
	    */
	}
	croppedImage.recycle();
	finish();
    }

    @Override
    protected void onPause() {
    	super.onPause();
    	CropBitmapManager.instance().cancelThreadDecoding(mDecodingThreads);
    	// DO NOT RECYCLE HERE; will leave mBitmap unusable after a resume.
        //mBitmap.recycle();
    }

    @Override
    protected void onDestroy() {
		super.onDestroy();
        if (mBitmap != null && !mBitmap.isRecycled())
        	mBitmap.recycle();
    }


    Runnable mRunFaceDetection = new Runnable() {
	float mScale = 1F;
	Matrix mImageMatrix;
	FaceDetector.Face[] mFaces = new FaceDetector.Face[3];
	int mNumFaces;

	// For each face, we create a HightlightView for it.
	private void handleFace(FaceDetector.Face f) {
	    PointF midPoint = new PointF();

	    int r = ((int) (f.eyesDistance() * mScale)) * 2;
	    f.getMidPoint(midPoint);
	    midPoint.x *= mScale;
	    midPoint.y *= mScale;

	    int midX = (int) midPoint.x;
	    int midY = (int) midPoint.y;

	    CropHighlightView hv = new CropHighlightView(mImageView);

	    int width = mBitmap.getWidth();
	    int height = mBitmap.getHeight();

	    Rect imageRect = new Rect(0, 0, width, height);

	    RectF faceRect = new RectF(midX, midY, midX, midY);
	    faceRect.inset(-r, -r);
	    if (faceRect.left < 0) {
		faceRect.inset(-faceRect.left, -faceRect.left);
	    }

	    if (faceRect.top < 0) {
		faceRect.inset(-faceRect.top, -faceRect.top);
	    }

	    if (faceRect.right > imageRect.right) {
		faceRect.inset(faceRect.right - imageRect.right,
			faceRect.right - imageRect.right);
	    }

	    if (faceRect.bottom > imageRect.bottom) {
		faceRect.inset(faceRect.bottom - imageRect.bottom,
			faceRect.bottom - imageRect.bottom);
	    }

	    hv.setup(mImageMatrix, imageRect, faceRect, mCircleCrop,
		    mAspectX != 0 && mAspectY != 0);

	    mImageView.add(hv);
	}

	// Create a default HightlightView if we found no face in the picture.
	private void makeDefault() {
	    CropHighlightView hv = new CropHighlightView(mImageView);

	    int width = mBitmap.getWidth();
	    int height = mBitmap.getHeight();

	    Rect imageRect = new Rect(0, 0, width, height);

	    // make the default size about 4/5 of the width or height
	    int cropWidth = Math.min(width, height) * 4 / 5;
	    int cropHeight = cropWidth;

	    if (mAspectX != 0 && mAspectY != 0) {
		if (mAspectX > mAspectY) {
		    cropHeight = cropWidth * mAspectY / mAspectX;
		} else {
		    cropWidth = cropHeight * mAspectX / mAspectY;
		}
	    }

	    int x = (width - cropWidth) / 2;
	    int y = (height - cropHeight) / 2;

	    RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
	    hv.setup(mImageMatrix, imageRect, cropRect, mCircleCrop,
		    mAspectX != 0 && mAspectY != 0);
	    mImageView.add(hv);
	}
	
	// Scale the image down for faster face detection.
	private Bitmap prepareBitmap() {
		if (mBitmap == null) {
		return null;
		}
		
		// 256 pixels wide is enough.
		if (mBitmap.getWidth() > 256) {
			mScale = 256.0F / mBitmap.getWidth();
		}
		Matrix matrix = new Matrix();
		matrix.setScale(mScale, mScale);
		Bitmap faceBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
		return faceBitmap;
	}
	
	public void run() {
	    mImageMatrix = mImageView.getImageMatrix();
	    Bitmap faceBitmap = prepareBitmap();

	    mScale = 1.0F / mScale;
	    if (faceBitmap != null && mDoFaceDetection) {
		FaceDetector detector = new FaceDetector(faceBitmap.getWidth(),
			faceBitmap.getHeight(), mFaces.length);
		mNumFaces = detector.findFaces(faceBitmap, mFaces);
	    }

	    if (faceBitmap != null && faceBitmap != mBitmap) {
		faceBitmap.recycle();
	    }

	    mHandler.post(new Runnable() {
		public void run() {
		    mWaitingToPick = mNumFaces > 1;
		    if (mNumFaces > 0) {
			for (int i = 0; i < mNumFaces; i++) {
			    handleFace(mFaces[i]);
			}
		    } else {
			makeDefault();
		    }
		    mImageView.invalidate();
		    if (mImageView.mHighlightViews.size() == 1) {
			mCrop = mImageView.mHighlightViews.get(0);
			mCrop.setFocus(true);
		    }

		    if (mNumFaces > 1) {
			Toast t = Toast.makeText(CropCropImage.this,
				"Multi face crop help",
				Toast.LENGTH_SHORT);
			t.show();
		    }
		}
	    });
	}
    };

    public static final int NO_STORAGE_ERROR = -1;
    public static final int CANNOT_STAT_ERROR = -2;

    public static void showStorageToast(Activity activity) {
	showStorageToast(activity, calculatePicturesRemaining());
    }

    public static void showStorageToast(Activity activity, int remaining) {
	String noStorageText = null;

	if (remaining == NO_STORAGE_ERROR) {
	    String state = Environment.getExternalStorageState();
	    if (state == Environment.MEDIA_CHECKING) {
		noStorageText = "Preparing card";
	    } else {
		noStorageText = "No storage card";
	    }
	} else if (remaining < 1) {
	    noStorageText = "Not enough space";
	}

	if (noStorageText != null) {
	    Toast.makeText(activity, noStorageText, 5000).show();
	}
    }

    public static int calculatePicturesRemaining() {
	try {
	    /*if (!ImageManager.hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {*/
	    String storageDirectory =
		Environment.getExternalStorageDirectory().toString();
	    StatFs stat = new StatFs(storageDirectory);
	    float remaining = ((float) stat.getAvailableBlocks()
		    * (float) stat.getBlockSize()) / 400000F;
	    return (int) remaining;
	    //}
	} catch (Exception ex) {
	    // if we can't stat the filesystem then we don't know how many
	    // pictures are remaining.  it might be zero but just leave it
	    // blank since we really don't know.
	    return CANNOT_STAT_ERROR;
	}
    }



}
