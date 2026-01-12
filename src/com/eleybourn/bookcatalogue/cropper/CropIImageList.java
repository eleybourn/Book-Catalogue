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

import java.io.IOException;
import java.util.HashMap;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Parcelable;

//
// ImageList and Image classes have one-to-one correspondence.
// The class hierarchy (* = abstract class):
//
//    IImageList
//    - BaseImageList (*)
//      - VideoList
//      - ImageList
//        - DrmImageList
//      - SingleImageList (contains UriImage)
//    - ImageListUber
//
//    IImage
//    - BaseImage (*)
//      - VideoObject
//      - Image
//        - DrmImage
//    - UriImage
//

/**
 * The interface of all image collections used in gallery.
 */
public interface CropIImageList extends Parcelable {
	HashMap<String, String> getBucketIds();

	void deactivate();

	/**
	 * Returns the count of image objects.
	 * 
	 * @return the number of images
	 */
    int getCount();

	/**
	 * @return true if the count of image objects is zero.
	 */
    boolean isEmpty();

	/**
	 * Returns the image at the ith position.
	 * 
	 * @param i
	 *            the position
	 * @return the image at the ith position
	 */
    CropIImage getImageAt(int i);

	/**
	 * Returns the image with a particular Uri.
     *
	 * @return the image with a particular Uri. null if not found.
	 */
    CropIImage getImageForUri(Uri uri);

    boolean removeImage(CropIImage image);

	/**
	 * Removes the image at the ith position.
	 * 
	 * @param i
	 *            the position
	 */
    boolean removeImageAt(int i);

	int getImageIndex(CropIImage image);

	/**
	 * Generate thumbnail for the image (if it has not been generated.)
	 * 
	 * @param index
	 *            the position of the image
	 */
    void checkThumbnail(int index) throws IOException;

	/**
	 * Opens this list for operation.
	 */
    void open(ContentResolver resolver);

	/**
	 * Closes this list to release resources, no further operation is allowed.
	 */
    void close();
}