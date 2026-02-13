package com.eleybourn.bookcatalogue.cropper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.eleybourn.bookcatalogue.utils.Logger;

import java.util.ArrayList;

public class CropImageView extends CropImageViewTouchBase {
    final ArrayList<CropHighlightView> mHighlightViews = new ArrayList<>();
    private final Context mContext;
    CropHighlightView mMotionHighlightView = null;
    float mLastX, mLastY;
    int mMotionEdge;

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mBitmapDisplayed.getBitmap() != null) {
            for (CropHighlightView hv : mHighlightViews) {
                hv.mMatrix.set(getImageMatrix());
                hv.invalidate();
            }
        }
    }

    @Override
    protected void zoomTo(float scale, float centerX, float centerY) {
        super.zoomTo(scale, centerX, centerY);
        for (CropHighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void zoomIn() {
        super.zoomIn();
        for (CropHighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void zoomOut() {
        super.zoomOut();
        for (CropHighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void postTranslate(float deltaX, float deltaY) {
        super.postTranslate(deltaX, deltaY);
        for (int i = 0; i < mHighlightViews.size(); i++) {
            CropHighlightView hv = mHighlightViews.get(i);
            hv.mMatrix.postTranslate(deltaX, deltaY);
            hv.invalidate();
        }
    }

    // According to the event's position, change the focus to the first
    // hitting cropping rectangle.
    private void recomputeFocus(MotionEvent event) {
        for (int i = 0; i < mHighlightViews.size(); i++) {
            CropHighlightView hv = mHighlightViews.get(i);
            hv.setFocus(false);
            hv.invalidate();
        }

        for (int i = 0; i < mHighlightViews.size(); i++) {
            CropHighlightView hv = mHighlightViews.get(i);
            int edge = hv.getHit(event.getX(), event.getY());
            if (edge != CropHighlightView.GROW_NONE) {
                if (!hv.hasFocus()) {
                    hv.setFocus(true);
                    hv.invalidate();
                }
                break;
            }
        }
        invalidate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        CropCropImage cropImage = (CropCropImage) mContext;
        if (cropImage.mSaving) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (cropImage.mWaitingToPick) {
                    recomputeFocus(event);
                } else {
                    for (int i = 0; i < mHighlightViews.size(); i++) {
                        CropHighlightView hv = mHighlightViews.get(i);
                        int edge = hv.getHit(event.getX(), event.getY());
                        if (edge != CropHighlightView.GROW_NONE) {
                            mMotionEdge = edge;
                            mMotionHighlightView = hv;
                            mLastX = event.getX();
                            mLastY = event.getY();
                            mMotionHighlightView
                                    .setMode((edge == CropHighlightView.MOVE) ? CropHighlightView.ModifyMode.Move
                                            : CropHighlightView.ModifyMode.Grow);
                            break;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (cropImage.mWaitingToPick) {
                    for (int i = 0; i < mHighlightViews.size(); i++) {
                        CropHighlightView hv = mHighlightViews.get(i);
                        if (hv.hasFocus()) {
                            cropImage.mCrop = hv;
                            for (int j = 0; j < mHighlightViews.size(); j++) {
                                if (j == i) {
                                    continue;
                                }
                                mHighlightViews.get(j).setHidden(true);
                            }
                            ((CropCropImage) mContext).mWaitingToPick = false;
                            return true;
                        }
                    }
                } else if (mMotionHighlightView != null) {
                    mMotionHighlightView.setMode(CropHighlightView.ModifyMode.None);
                }
                mMotionHighlightView = null;
                break;
            case MotionEvent.ACTION_MOVE:
                if (cropImage.mWaitingToPick) {
                    recomputeFocus(event);
                } else if (mMotionHighlightView != null) {
                    mMotionHighlightView.handleMotion(mMotionEdge, event.getX()
                            - mLastX, event.getY() - mLastY);
                    mLastX = event.getX();
                    mLastY = event.getY();

                    ensureVisible(mMotionHighlightView);
                }
                break;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                center(true, true);
                break;
            case MotionEvent.ACTION_MOVE:
                // if we're not zoomed then there's no point in even allowing
                // the user to move the image around. This call to center puts
                // it back to the normalized location (with false meaning don't
                // animate).
                if (getScale() == 1F) {
                    center(true, true);
                }
                break;
        }

        return true;
    }

    // Pan the displayed image to make sure the cropping rectangle is visible.
    private void ensureVisible(CropHighlightView hv) {
        Rect r = hv.mDrawRect;

        int panDeltaX1 = Math.max(0, mLeft - r.left);
        int panDeltaX2 = Math.min(0, mRight - r.right);

        int panDeltaY1 = Math.max(0, mTop - r.top);
        int panDeltaY2 = Math.min(0, mBottom - r.bottom);

        int panDeltaX = panDeltaX1 != 0 ? panDeltaX1 : panDeltaX2;
        int panDeltaY = panDeltaY1 != 0 ? panDeltaY1 : panDeltaY2;

        if (panDeltaX != 0 || panDeltaY != 0) {
            panBy(panDeltaX, panDeltaY);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
            for (int i = 0; i < mHighlightViews.size(); i++) {
                mHighlightViews.get(i).draw(canvas);
            }
        } catch (RuntimeException e) {
            Logger.logError(e);
        }
    }

    void add(CropHighlightView hv) {
        mHighlightViews.add(hv);
        invalidate();
    }
}