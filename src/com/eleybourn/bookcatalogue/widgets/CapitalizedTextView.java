package com.eleybourn.bookcatalogue.widgets;

import android.content.Context; 
import android.util.AttributeSet; 
import android.widget.TextView;

/**
 * Prior to API 14 there was no way to get TextView to capitalize all text. So this class 
 * overrides TextView to crudely capitalize everything.
 * 
 * ENHANCE: Support SmallCaps by building styled spans based on words.
 * 
 * @author pjw
 */
public class CapitalizedTextView extends TextView {// implements ViewTreeObserver.OnPreDrawListener {

	/**
	 * Constructor
	 * 
	 * @param context
	 */
    public CapitalizedTextView(Context context) {
        super(context);
    }

	/**
	 * Constructor
	 * 
	 * @param context
	 */
    public CapitalizedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

	/**
	 * Constructor
	 * 
	 * @param context
	 */
    public CapitalizedTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Override setText to capitalise the scring before passing it to parent class.
     */
    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text.toString().toUpperCase(), type);
    }

}