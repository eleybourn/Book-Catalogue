/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Prior to API 14 there was no way to get TextView to capitalize all text. So this class 
 * overrides TextView to crudely capitalize everything.
 * ENHANCE: Support SmallCaps by building styled spans based on words.
 * 
 * @author pjw
 */
public class CapitalizedTextView extends TextView {// implements ViewTreeObserver.OnPreDrawListener {

	/**
	 * Constructor
	 */
    public CapitalizedTextView(Context context) {
        super(context);
    }

	/**
	 * Constructor
	 */
    public CapitalizedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

	/**
	 * Constructor
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