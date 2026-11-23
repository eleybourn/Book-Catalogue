package com.eleybourn.bookcatalogue.scanner;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.CameraSource;

/**
 * Custom ViewGroup that holds a SurfaceView and sizes it so the camera preview
 * maintains its exact aspect ratio without distortion/skewing in portrait or landscape.
 */
public class CameraSourcePreview extends ViewGroup {
    private CameraSource mCameraSource;

    public CameraSourcePreview(Context context) {
        super(context);
    }

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraSourcePreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCameraSource(CameraSource cameraSource) {
        mCameraSource = cameraSource;
        requestLayout();
    }

    private boolean isPortrait() {
        int orientation = getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        int childWidth = width;
        int childHeight = height;

        if (mCameraSource != null) {
            Size size = mCameraSource.getPreviewSize();
            if (size != null) {
                int previewWidth = size.getWidth();
                int previewHeight = size.getHeight();

                // In portrait, width and height of the preview frame are swapped
                if (isPortrait()) {
                    int tmp = previewWidth;
                    previewWidth = previewHeight;
                    previewHeight = tmp;
                }

                double targetRatio = (double) previewWidth / previewHeight;
                double layoutRatio = (double) width / height;

                if (layoutRatio < targetRatio) {
                    childWidth = (int) (height * targetRatio);
                    childHeight = height;
                } else {
                    childWidth = width;
                    childHeight = (int) (width / targetRatio);
                }
            }
        }

        int childLeft = (width - childWidth) / 2;
        int childTop = (height - childHeight) / 2;

        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
    }
}
