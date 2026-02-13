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

import android.graphics.Bitmap;

/**
 * The interface of all images used in gallery.
 */
public interface CropIImage {
    int UNCONSTRAINED = -1;

	/** Get the image list which contains this image. */
    CropIImageList getContainer();

	/** Get the bitmap for the full size image. */
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels);

    // Get/Set the title of the image
    void setTitle(String name);

	String getTitle();

    int getWidth();

	int getHeight();

	String getDisplayName();

}