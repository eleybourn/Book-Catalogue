package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * This subclass of TextView is designed to get aaround a bug in Android 4.1 and 4.1.1, documented here:
 * 
 *     http://code.google.com/p/android/issues/detail?id=34872
 * and here
 *     https://code.google.com/p/android/issues/detail?id=35412
 * 
 * It should have no effect *except* for very specific books in the above-mentioned versions of Android
 * 
 * NOTE:
 * It should only be needed for TextViews that are wrapped and have MetricAffecting spans (eg. URL, bold, emphasis).
 * Currently this is limited to the book description field.
 * 
 * @author pjw
 *
 */
public class SafeSpannedTextView extends TextView {

	public SafeSpannedTextView(Context context) {
		super(context);
	}

	public SafeSpannedTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SafeSpannedTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		try {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		} catch (IndexOutOfBoundsException e) {
			setText(getText().toString());
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}

	@Override
	public void setGravity(int gravity) {
		try {
			super.setGravity(gravity);
		} catch (ArrayIndexOutOfBoundsException e) {
			setText(getText().toString());
			super.setGravity(gravity);
		}
	}

	@Override
	public void setText(CharSequence text, BufferType type) {
		try {
			super.setText(text, type);
		} catch (ArrayIndexOutOfBoundsException e) {
			setText(text.toString());
		}
	}
}
