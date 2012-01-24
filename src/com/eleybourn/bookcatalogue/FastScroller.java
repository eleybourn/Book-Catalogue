package com.eleybourn.bookcatalogue;

/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * This is a substantially modified version of the Android 2.3 FastScroller.
 * 
 * The original did not work correctly with ExpandableListViews; the thumb would work
 * for only a small portion of fully expanded views and exhibited odd behaviour with 
 * view with a small number of groups but large children.
 * 
 * The underlying approach to scrolling with a summary in the original version was
 * also flawed: it translated a thumb position of 50% to mean that the middle summaryGroup
 * should be visible. While this may seem sensible, it is contrary to reasonable expectations with
 * scrollable lists: a thumb at 50% in any scrollable list should result in the list being at
 * the mid-point. With an expandableListView, this needs to take into account the total
 * number of items (groups and children), NOT just the summary groups. Doing what the original
 * implementaion did is not only counter-intuitive, but also makes the thumb unusable in the case of 
 * n groups, where one of those n has O(n) children, and is expanded. In this case, the entire set
 * of children will move through the screen based on the same finger movement as moving between 
 * two unexpanded groups. In the more general case it can be characterised as uneven scrolling
 * if sections have widely varying sizes.
 * 
 * Finally, the original would fail to correctly place the overlay if setFastScrollEnabled was 
 * called after the Activity had been fuly drawn: this is because the only place that set the
 * overlay position was in the onSizeChanged event.
 * 
 * Combine this with the desire to display more than a single letter in the overlay,
 * and a rewrite was more or less essential.
 * 
 * The solution is: 
 * 
 * - modify init() to fake an onSizeChanged event
 * - modify onSizeChanged() to make the overlay 75% of total width;
 * - modify draw() to handle arbitrary text (ellipsize if necessary)
 * - modify scrollTo() to just deal with list contents and not try to do any fancy
 *   calculations about group position.
 *   
 * Because the original was in the android package, it had access to classes that we do not
 * have access to, so in some cases we now check if mList is an ExpandableListView rather than
 * checking if the adapter is an ExpandableListConnector.
 * 
 * *********************************************************
 * 
 * NOTE: any class implementing a SectionIndexer for this object MUST return flattened
 * positions in calls to getPositionForSection(), and will be passed flattened positions in 
 * calls to getSectionForPosition().
 * 
 * *********************************************************
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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;

/**
 * Helper class for AbsListView to draw and control the Fast Scroll thumb
 */
class FastScroller {
   
    // Minimum number of pages to justify showing a fast scroll thumb
    private static int MIN_PAGES = 4;
    // Scroll thumb not showing
    private static final int STATE_NONE = 0;
    // Not implemented yet - fade-in transition
    private static final int STATE_ENTER = 1;
    // Scroll thumb visible and moving along with the scrollbar
    private static final int STATE_VISIBLE = 2;
    // Scroll thumb being dragged by user
    private static final int STATE_DRAGGING = 3;
    // Scroll thumb fading out due to inactivity timeout
    private static final int STATE_EXIT = 4;
    
    private Drawable mThumbDrawable;
    private Drawable mOverlayDrawable;

    private int mThumbH;
    private int mThumbW;
    private int mThumbY;

    private RectF mOverlayPos;
    private int mOverlaySize = 104;

    private AbsListView mList;
    private boolean mScrollCompleted;
    private int mVisibleItem;
    private TextPaint mPaint;
    private int mListOffset;
    private int mItemCount = -1;
    private boolean mLongList;
    
    private Object [] mSections;
    private String mSectionText;
    private boolean mDrawOverlay;
    private ScrollFade mScrollFade;
    
    private int mState;
    
    private Handler mHandler = new Handler();
    
    private BaseAdapter mListAdapter;
    private SectionIndexer mSectionIndexer;

    private boolean mChangedBounds;
    
    public FastScroller(Context context, AbsListView listView) {
        mList = listView;
        init(context);
    }

    public void setState(int state) {
    	
    	System.out.println("State: " + state);
        switch (state) {
            case STATE_NONE:
                mHandler.removeCallbacks(mScrollFade);
                mList.invalidate();
                break;
            case STATE_VISIBLE:
                if (mState != STATE_VISIBLE) { // Optimization
                    resetThumbPos();
                }
                // Fall through
            case STATE_DRAGGING:
                mHandler.removeCallbacks(mScrollFade);
                break;
            case STATE_EXIT:
                int viewWidth = mList.getWidth();
                mList.invalidate(viewWidth - mThumbW, mThumbY, viewWidth, mThumbY + mThumbH);
                break;
        }
        mState = state;
    }
    
    public int getState() {
        return mState;
    }
    
    private void resetThumbPos() {
        final int viewWidth = mList.getWidth();
        // Bounds are always top right. Y coordinate get's translated during draw
        mThumbDrawable.setBounds(viewWidth - mThumbW, 0, viewWidth, mThumbH);
        mThumbDrawable.setAlpha(ScrollFade.ALPHA_MAX);
    }
    
    private void useThumbDrawable(Drawable drawable) {
        mThumbDrawable = drawable;
        mThumbW = mOverlaySize *3/4 ; //64; //mCurrentThumb.getIntrinsicWidth();
        mThumbH = mOverlaySize *3/4; //52; //mCurrentThumb.getIntrinsicHeight();
        mChangedBounds = true;
    }

    private void init(Context context) {
        // Get both the scrollbar states drawables
        final Resources res = context.getResources();
        useThumbDrawable(res.getDrawable( R.drawable.scrollbar_handle_accelerated_anim2));
        
        mOverlayDrawable = res.getDrawable( R.drawable.menu_submenu_background);
        
        mScrollCompleted = true;

        getSections();
        
        mOverlayPos = new RectF();
        mScrollFade = new ScrollFade();
        mPaint = new TextPaint();
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(mOverlaySize / 3);
        TypedArray ta = context.getTheme().obtainStyledAttributes(new int[] { 
                android.R.attr.textColorPrimary });
        ColorStateList textColor = ta.getColorStateList(ta.getIndex(0));
        int textColorNormal = textColor.getDefaultColor();
        mPaint.setColor(textColorNormal);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        
        mState = STATE_NONE;

        // Send a fake onSizeChanged so that overlay position is correct if
        // this is called after Activity is stable
        final int w = mList.getWidth();
        final int h = mList.getHeight();
        onSizeChanged(w,h,w,h);
    }
    
    void stop() {
        setState(STATE_NONE);
        // No need for these any more.
        mOverlayDrawable = null;
        mThumbDrawable = null;
    }
    
    boolean isVisible() {
        return !(mState == STATE_NONE);
    }
    
    public void draw(final Canvas canvas) {
        
        if (mState == STATE_NONE) {
            // No need to draw anything
            return;
        }

        final int y = mThumbY;
        final int viewWidth = mList.getWidth();
        final FastScroller.ScrollFade scrollFade = mScrollFade;

        int alpha = -1;
        if (mState == STATE_EXIT) {
            alpha = scrollFade.getAlpha();
            if (alpha < ScrollFade.ALPHA_MAX / 2) {
                mThumbDrawable.setAlpha(alpha * 2);
            }
            int left = viewWidth - (mThumbW * alpha) / ScrollFade.ALPHA_MAX;
            mThumbDrawable.setBounds(left, 0, viewWidth, mThumbH);
            mChangedBounds = true;
        }

        canvas.translate(0, y);
        mThumbDrawable.draw(canvas);
        canvas.translate(0, -y);

        // If user is dragging the scroll bar, draw the alphabet overlay
        if (mState == STATE_DRAGGING && mDrawOverlay) {
            mOverlayDrawable.draw(canvas);
            final TextPaint paint = mPaint;
            float descent = paint.descent();
            final RectF rectF = mOverlayPos;
            final String text = TextUtils.ellipsize(mSectionText, paint, (mOverlayPos.right - mOverlayPos.left) * 0.8f, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, (int) (rectF.left + rectF.right) / 2,
            					  // Base of text at: (middle) + (half text height) - descent : so it is vertically centred
                    			  (int) (rectF.bottom + rectF.top) / 2 + mOverlaySize / 6 - descent, 
                    			  paint);
        } else if (mState == STATE_EXIT) {
            if (alpha == 0) { // Done with exit
                setState(STATE_NONE);
            } else {
                mList.invalidate(viewWidth - mThumbW, y, viewWidth, y + mThumbH);            
            }
        }
    }

    void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mThumbDrawable != null) {
            mThumbDrawable.setBounds(w - mThumbW, 0, w, mThumbH);
        }
        final RectF pos = mOverlayPos;
        // Original: width was equal to height, controlled by mOverlaySize.
        //    pos.left = (w - mOverlaySize) / 2;
        //    pos.right = pos.left + mOverlaySize;
        //
        // Now, Make it 75% of total available space
        pos.left = (w / 8);
        pos.right = pos.left + w * 3 / 4;
        pos.top = h / 10; // 10% from top
        pos.bottom = pos.top + mOverlaySize;
        if (mOverlayDrawable != null) {
            mOverlayDrawable.setBounds((int) pos.left, (int) pos.top,
                (int) pos.right, (int) pos.bottom);
        }
    }
    
    void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, 
            int totalItemCount) {
        // Are there enough pages to require fast scroll? Recompute only if total count changes
        if (mItemCount != totalItemCount && visibleItemCount > 0) {
            mItemCount = totalItemCount;
            mLongList = mItemCount / visibleItemCount >= MIN_PAGES;
        }
        if (!mLongList) {
            if (mState != STATE_NONE) {
                setState(STATE_NONE);
            }
            return;
        }
        if (totalItemCount - visibleItemCount > 0 && mState != STATE_DRAGGING ) {
            mThumbY = ((mList.getHeight() - mThumbH) * firstVisibleItem) 
                    / (totalItemCount - visibleItemCount);
            if (mChangedBounds) {
                resetThumbPos();
                mChangedBounds = false;
            }
        }
        mScrollCompleted = true;
        if (firstVisibleItem == mVisibleItem) {
            return;
        }
        mVisibleItem = firstVisibleItem;
        if (mState != STATE_DRAGGING) {
            setState(STATE_VISIBLE);
            mHandler.postDelayed(mScrollFade, 1500);
        }
    }

    private void getSections() {
        Adapter adapter = mList.getAdapter();
        mSectionIndexer = null;
        if (adapter instanceof HeaderViewListAdapter) {
            mListOffset = ((HeaderViewListAdapter)adapter).getHeadersCount();
            adapter = ((HeaderViewListAdapter)adapter).getWrappedAdapter();
        }
        if (mList instanceof ExpandableListView) {
            ExpandableListAdapter expAdapter = ((ExpandableListView)mList).getExpandableListAdapter();
            if (expAdapter instanceof SectionIndexer) {
                mSectionIndexer = (SectionIndexer) expAdapter;
                mListAdapter = (BaseAdapter) adapter;
                mSections = mSectionIndexer.getSections();
            }
        } else {
            if (adapter instanceof SectionIndexer) {
                mListAdapter = (BaseAdapter) adapter;
                mSectionIndexer = (SectionIndexer) adapter;
                mSections = mSectionIndexer.getSections();
                
            } else {
                mListAdapter = (BaseAdapter) adapter;
                mSections = new String[] { " " };
            }
        }
    }

	private void scrollTo(float position) {
		int count = mList.getCount();
		mScrollCompleted = false;
		final Object[] sections = mSections;
		int sectionIndex;

		int index = (int) (position * count);
		if (mList instanceof ListView) { // This INCLUDES ExpandableListView
			((ListView) mList).setSelectionFromTop(index + mListOffset, 0);
		} else {
			mList.setSelection(index + mListOffset);
		}
		if (sections != null && sections.length > 1) {
			sectionIndex = mSectionIndexer.getSectionForPosition(index);
		} else
			sectionIndex = -1;

		if (sectionIndex >= 0) {
			String text = mSectionText = sections[sectionIndex].toString();
			mDrawOverlay = (text.length() != 1 || text.charAt(0) != ' ')
					&& sectionIndex < sections.length;
		} else {
			mDrawOverlay = false;
		}
	}

    private void cancelFling() {
        // Cancel the list fling
        MotionEvent cancelFling = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        mList.onTouchEvent(cancelFling);
        cancelFling.recycle();
    }
    
    boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mState > STATE_NONE && ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (ev.getX() > mList.getWidth() - mThumbW && ev.getY() >= mThumbY &&
                    ev.getY() <= mThumbY + mThumbH) {
                setState(STATE_DRAGGING);
                return true;
            }
        }
        return false;
    }

    boolean onTouchEvent(MotionEvent me) {
        if (mState == STATE_NONE) {
            return false;
        }
        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            if (me.getX() > mList.getWidth() - mThumbW
                    && me.getY() >= mThumbY 
                    && me.getY() <= mThumbY + mThumbH) {
                
                setState(STATE_DRAGGING);
                if (mListAdapter == null && mList != null) {
                    getSections();
                }

                cancelFling();
                return true;
            }
        } else if (me.getAction() == MotionEvent.ACTION_UP) {
            if (mState == STATE_DRAGGING) {
                setState(STATE_VISIBLE);
                final Handler handler = mHandler;
                handler.removeCallbacks(mScrollFade);
                handler.postDelayed(mScrollFade, 1000);
                return true;
            }
        } else if (me.getAction() == MotionEvent.ACTION_MOVE) {
            if (mState == STATE_DRAGGING) {
                final int viewHeight = mList.getHeight();
                // Jitter
                int newThumbY = (int) me.getY() - mThumbH + 10;
                if (newThumbY < 0) {
                    newThumbY = 0;
                } else if (newThumbY + mThumbH > viewHeight) {
                    newThumbY = viewHeight - mThumbH;
                }
                if (Math.abs(mThumbY - newThumbY) < 2) {
                    return true;
                }
                mThumbY = newThumbY;
                // If the previous scrollTo is still pending
                if (mScrollCompleted) {
                    scrollTo((float) mThumbY / (viewHeight - mThumbH));
                }
                return true;
            }
        }
        return false;
    }
    
    public class ScrollFade implements Runnable {
        
        long mStartTime;
        long mFadeDuration;
        static final int ALPHA_MAX = 208;
        static final long FADE_DURATION = 200;
        
        void startFade() {
            mFadeDuration = FADE_DURATION;
            mStartTime = SystemClock.uptimeMillis();
            setState(STATE_EXIT);
        }
        
        int getAlpha() {
            if (getState() != STATE_EXIT) {
                return ALPHA_MAX;
            }
            int alpha;
            long now = SystemClock.uptimeMillis();
            if (now > mStartTime + mFadeDuration) {
                alpha = 0;
            } else {
                alpha = (int) (ALPHA_MAX - ((now - mStartTime) * ALPHA_MAX) / mFadeDuration); 
            }
            return alpha;
        }
        
        public void run() {
            if (getState() != STATE_EXIT) {
                startFade();
                return;
            }
            
            if (getAlpha() > 0) {
                mList.invalidate();
            } else {
                setState(STATE_NONE);
            }
        }
    }
}
