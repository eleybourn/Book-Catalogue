package com.eleybourn.bookcatalogue.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.cropper.CropImageViewTouchBase;

/**
 * ImageView that supports pinch-to-zoom and panning.
 * Extends CropImageViewTouchBase to reuse matrix-based zooming logic.
 */
public class ZoomableImageView extends CropImageViewTouchBase {

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mScaleDetector.setQuickScaleEnabled(true);
        mGestureDetector = new GestureDetector(context, new GestureListener());
        
        // Ensure we are using the matrix scale type for our zooming logic to work
        setScaleType(ImageView.ScaleType.MATRIX);
        
        // Ensure the view can receive focus and clicks
        setFocusable(true);
        setClickable(true);
    }

    @Override
    protected float maxZoom() {
        // Ensure we can zoom in at least 4x from the base fitted size
        return Math.max(super.maxZoom(), 4.0f);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Pass the event to the scale detector
        boolean handled = mScaleDetector.onTouchEvent(event);
        
        // If we are scaling, don't let the gesture detector handle the event (e.g. for panning)
        if (!mScaleDetector.isInProgress()) {
            handled |= mGestureDetector.onTouchEvent(event);
        }
        
        // We always return true to indicate we've handled the event and want to keep receiving them
        // This is important for gestures to work correctly.
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float currentScale = getScale();
            float targetScale = currentScale * scaleFactor;
            
            // Limit zoom
            if (targetScale < 1.0f) {
                targetScale = 1.0f;
            }
            if (targetScale > mMaxZoom) {
                targetScale = mMaxZoom;
            }
            
            zoomTo(targetScale, detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            return true; // Must return true to detect further gestures like scroll
        }

        @Override
        public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            // Only pan if we are zoomed in
            if (getScale() > 1.0f) {
                panBy(-distanceX, -distanceY);
                center(true, true);
                return true;
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            if (getScale() > 1.0f) {
                // Zoom out to original fit
                zoomTo(1.0f, e.getX(), e.getY(), 200);
            } else {
                // Zoom in to a reasonable level
                float targetScale = Math.min(3.0f, mMaxZoom);
                zoomTo(targetScale, e.getX(), e.getY(), 200);
            }
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            // Trigger the View's click listener (used for dismissing the dialog)
            performClick();
            return true;
        }
    }
}
