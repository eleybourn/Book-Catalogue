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

package com.eleybourn.bookcatalogue.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;

import org.xml.sax.Attributes;

/**
 * A class to help parsing Sax Xml output. The XmlFilter
 * objects build a tree of filters and XmlHandler objects
 * that make this process more manageable.
 *
 * @author Philip Warner
 */
public class XmlFilter {
	/** The tag for this specific filter */
	String mTagName;
	/** A hashtable to ensure that there are no more than one sub-filter per tag at a given level */
	Hashtable<String, XmlFilter> mSubFilterHash = new Hashtable<>();
	/** List of sub-filters for this filter */
	ArrayList<XmlFilter> mSubFilters = new ArrayList<>();
	/** Action to perform, if any, when the associated tag is started */
	XmlHandler mStartAction = null;
	/** Optional parameter put in context before action is called */
	Object mStartArg = null;
	
	/** Action to perform, if any, when the associated tag is finished */
	XmlHandler mEndAction = null;
	/** Optional parameter put in context before action is called */
	Object mEndArg = null;

	/** Interface definition for filter handlers */
	public interface XmlHandler {
		void process(ElementContext context);
	}

    /**
	 * Class used to define the context of a specific tag. The 'body' element will only be
	 * filled in the call to the 'processEnd' method.
	 * @author Philip Warner
	 *
	 */
	public static class ElementContext {
		public String uri;
		public String localName;
		public String name;
		public Attributes attributes;
		public String preText;
		public String body;
		public XmlFilter filter;
		public ElementContext(String uri, String localName, String name, Attributes attributes, String preText) {
			this.uri = uri;
			this.localName = localName;
			this.name = name;
			this.attributes = attributes;
			this.preText = preText;
		}
		public Object userArg;
	}

	/**
	 * Constructor
	 * 
	 * @param pattern The tag that this filter handles
	 */
	public XmlFilter(String pattern) {
		mTagName = pattern;
	}

	/**
	 * Check if this filter matches the passed XML tag
	 * 
	 * @param tag	Tag name
	 *
	 * @return		Boolean indicating it matches.
	 */
	public boolean matches(String tag) {
		return mTagName.equalsIgnoreCase(tag);
	}
	
	/**
	 * Find a sub-filter for the passed context.
	 * Currently just used local_name from the context.
	 */
	public XmlFilter getSubFilter(ElementContext context) {
		return getSubFilter(context.localName);
	}
	/**
	 * Find a sub-filter based on the passed tag name.
	 * 
	 * @param name	XML tag name
	 * @return		Matching filter, or NULL
	 */
	private XmlFilter getSubFilter(String name) {
		for(XmlFilter f : mSubFilters) {
			if (f.matches(name))
				return f;
		}
		return null;
	}
	/**
	 * Called when associated tag is started.
	 */
	public void processStart(ElementContext context) {
		if (mStartAction != null) {
			context.userArg = mStartArg;
			mStartAction.process(context);			
		}
	}
	/**
	 * Called when associated tag is finished.
	 */
	public void processEnd(ElementContext context) {
		if (mEndAction != null) {
			context.userArg = mEndArg;
			mEndAction.process(context);			
		}
	}
	/**
	 * Get the tag that this filter will match
	 */
	public String getTagName() {
		return mTagName;
	}

    public void setEndAction(XmlHandler endAction, Object userArg) {
		if (mEndAction != null)
			throw new RuntimeException("End Action already set");			
		mEndAction = endAction;
		mEndArg = userArg;
    }

    public XmlFilter setStartAction(XmlHandler startAction, Object userArg) {
		if (mStartAction != null)
			throw new RuntimeException("Start Action already set");			
		mStartAction = startAction;
		mStartArg = userArg;
		return this;
	}

	/**
	 * Add a filter at this level; ensure it is unique.
	 * 
	 * @param filter	filter to add
	 */
	public void addFilter(XmlFilter filter) {
		String lcPat = filter.getTagName().toLowerCase();
		if (mSubFilterHash.containsKey(lcPat))
			throw new RuntimeException("Filter " + filter.getTagName() + " already exists");
		mSubFilterHash.put(lcPat, filter);
		mSubFilters.add(filter);
	}

	/**
	 * Static method to add a filter to a passed tree and return the matching XmlFilter
	 * 
	 * @param root		Root XmlFilter object.
	 * @param filters	Names of tags to add to tree, if not present. 
	 * 
	 * @return			The filter matching the final tag name passed.
	 */
	public static XmlFilter buildFilter(XmlFilter root, String... filters ) {
		if (filters.length == 0)
			return null;
		return buildFilter(root, 0, Arrays.asList(filters).iterator());
	}

    /**
	 * Internal implementation of method to add a filter to a passed tree and return the matching XmlFilter.
	 * This is called recursively to process the filter list.
	 * 
	 * @param root		Root XmlFilter object.
	 * @param depth		Recursion depth
	 * @param iterator	Names of tags to add to tree, if not present.
	 *
	 * @return			The filter matching the final tag name passed.
	 */
	private static XmlFilter buildFilter(XmlFilter root, int depth, Iterator<String> iterator ) {
        final String curr = iterator.next();
		XmlFilter sub = root.getSubFilter(curr);
		if (sub == null) {
			sub = new XmlFilter(curr);
			root.addFilter(sub);
		}
		if (!iterator.hasNext()) {
			// At end
			return sub;
		} else {
			// We are still finding leaf
			return buildFilter( sub, depth+1, iterator );
		}
	}
	
}
