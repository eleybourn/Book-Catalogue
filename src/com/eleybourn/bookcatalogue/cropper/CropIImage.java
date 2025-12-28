/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.io.InputStream;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * The interface of all images used in gallery.
 */
public interface CropIImage {
	int THUMBNAIL_TARGET_SIZE = 320;
	int MINI_THUMB_TARGET_SIZE = 96;
	int UNCONSTRAINED = -1;

	/** Get the image list which contains this image. */
    CropIImageList getContainer();

	/** Get the bitmap for the full size image. */
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels);

	Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels, boolean rotateAsNeeded);

	Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels, boolean rotateAsNeeded, boolean useNative);

	int getDegreesRotated();

	boolean ROTATE_AS_NEEDED = true;
	boolean NO_ROTATE = false;
	boolean USE_NATIVE = true;
	boolean NO_NATIVE = false;

	/** Get the input stream associated with a given full size image. */
    InputStream fullSizeImageData();

	long fullSizeImageId();

	Uri fullSizeImageUri();

	/** Get the path of the (full size) image data. */
    String getDataPath();

	// Get/Set the title of the image
    void setTitle(String name);

	String getTitle();

	// Get metadata of the image
    long getDateTaken();

	String getMimeType();

	int getWidth();

	int getHeight();

	String getDisplayName();

	// Get property of the image
    boolean isReadonly();

	boolean isDrm();

	// Get the bitmap/uri of the medium thumbnail
    Bitmap thumbBitmap(boolean rotateAsNeeded);

	Uri thumbUri();

	// Get the bitmap of the mini thumbnail.
    Bitmap miniThumbBitmap();

	// Rotate the image
    boolean rotateImageBy(int degrees);

}