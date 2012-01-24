package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.AbsListView;
import android.widget.ExpandableListView;

/**
 * Subclass of ExpandableListView that uses a local implementation of FastScroller to bypass
 * the deficiencies in the original Android version. See fastScroller.java for a discussion.
 * 
 * We need to subclass ExpandableListView because we need access to events that are only provided
 * by the subclass.
 * 
 * @author Grunthos
 */
public class FastScrollExpandableListView extends ExpandableListView {

	/** Active scroller, if any */
	FastScroller mScroller = null;
	
	public FastScrollExpandableListView(Context context ) {
		super(context);
	}
	public FastScrollExpandableListView(Context context, AttributeSet attrs ) {
		super(context, attrs);
	}
	public FastScrollExpandableListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	/** OnScrollListener, if used */
	private OnScrollListener mOnScrollListener = null;

	/**
	 * Called to create and start a new FastScroller if none already exists.
	 * 
	 * @param scroller
	 */
	private void initScroller() {
		if (mScroller != null)
			return;

		mScroller = new FastScroller(this.getContext(), this);
		// Scroller needs access to onScroll event.
		if (mScroller != null && mOnScrollListener == null) {
			// Create a listener that just dispatches onScroll to mScroller.
			mOnScrollListener = new OnScrollListener(){
				@Override
				public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
					if (mScroller != null)
						mScroller.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
				}
	
				@Override
				public void onScrollStateChanged(AbsListView view, int scrollState) {
				}};
			// Start listening
			this.setOnScrollListener(mOnScrollListener);
		}
	}

	/**
	 * Pass to scroller if defined, otherwise perform default actions.
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (mScroller != null && mScroller.onInterceptTouchEvent(ev))
			return true;

		return super.onInterceptTouchEvent(ev);
	}
	/**
	 * Pass to scroller if defined, otherwise perform default actions.
	 */
	@Override
	protected void  onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (mScroller != null)
			mScroller.onSizeChanged(w, h, oldw, oldh);
	}
	/**
	 * Pass to scroller if defined, otherwise perform default actions.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (mScroller != null && mScroller.onTouchEvent(ev))
			return true;

		return super.onTouchEvent(ev);
	}

	/**
	 * Send draw() to the scroller as well.
	 */
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (mScroller != null)
			mScroller.draw(canvas);
	}

	/**
	 * Depending on 'enabled', either stop or start the scroller.
	 */
	@Override
	public void setFastScrollEnabled(boolean enabled) {
		if (!enabled) {
			if (mScroller != null) {
				mScroller.stop();
				mScroller = null;
			}
		} else {
			if (mScroller == null) {
				initScroller();
			}			
		}
	}
}
