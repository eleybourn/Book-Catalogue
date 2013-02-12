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

package com.eleybourn.bookcatalogue.goodreads.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;

import org.xml.sax.Attributes;

/**
 * A class to help parsing Sax Xml output. For goodreads XML output, 90% of the XML can be 
 * thrown away but we do need to ensure we get the tags from the right context. The XmlFilter
 * objects build a tree of filters and XmlHandler objects that make this process more manageable.
 * 
 * See SearchBooksApiHandler for an example of usage.
 * 
 * @author Philip Warner
 */
public class XmlFilter {
	/** The tag for this specific filter */
	String mTagName = null;
	/** A hashtable to ensure that there are no more than one sub-filter per tag at a given level */
	Hashtable<String, XmlFilter> mSubFilterHash = new Hashtable<String, XmlFilter>();
	/** List of sub-filters for this filter */
	ArrayList<XmlFilter> mSubFilters = new ArrayList<XmlFilter>();
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

	/** Interface definition for filter handlers */
	public interface XmlHandlerExt<T> {
		void process(ElementContext context, T arg);
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
	 * 
	 * @param context
	 * @return
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
	 * 
	 * @param context
	 */
	public void processStart(ElementContext context) {
		if (mStartAction != null) {
			context.userArg = mStartArg;
			mStartAction.process(context);			
		}
	}
	/**
	 * Called when associated tag is finished.
	 * 
	 * @param context
	 */
	public void processEnd(ElementContext context) {
		if (mEndAction != null) {
			context.userArg = mEndArg;
			mEndAction.process(context);			
		}
	}
	/**
	 * Get the tag that this filter will match
	 * 
	 * @return
	 */
	public String getTagName() {
		return mTagName;
	}
	/**
	 * Set the action to perform when the tag associated with this filter is finished.
	 * 
	 * @param endAction		XmlHandler to call
	 * 
	 * @return		This XmlFilter, to allow chaining
	 */
	public XmlFilter setEndAction(XmlHandler endAction) {
		return setEndAction(endAction, null);
	}
	public XmlFilter setEndAction(XmlHandler endAction, Object userArg) {
		if (mEndAction != null)
			throw new RuntimeException("End Action already set");			
		mEndAction = endAction;
		mEndArg = userArg;
		return this;
	}

	/**
	 * Set the action to perform when the tag associated with this filter is started.
	 * 
	 * @param startAction		XmlHandler to call
	 * 
	 * @return		This XmlFilter, to allow chaining
	 */
	public XmlFilter setStartAction(XmlHandler startAction) {
		return setStartAction(startAction, null);
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
		if (filters.length <= 0)
			return null;
		return buildFilter(root, 0, Arrays.asList(filters).iterator());
	}
	/**
	 * Static method to add a filter to a passed tree and return the matching XmlFilter
	 * 
	 * @param root		Root XmlFilter object.
	 * @param filters	Names of tags to add to tree, if not present. 
	 * 
	 * @return			The filter matching the final tag name passed.
	 */
	public static XmlFilter buildFilter(XmlFilter root, ArrayList<String> filters ) {
		if (filters.size() <= 0)
			return null;
		return buildFilter(root, 0, filters.iterator());
	}
	/**
	 * Internal implementation of method to add a filter to a passed tree and return the matching XmlFilter.
	 * This is called recursively to process the filter list.
	 * 
	 * @param root		Root XmlFilter object.
	 * @param depth		Recursion depth
	 * @param filters	Names of tags to add to tree, if not present. 
	 *
	 * @return			The filter matching the final tag name passed.
	 */
	private static XmlFilter buildFilter(XmlFilter root, int depth, Iterator<String> iter ) {
		//if (!root.matches(filters[depth]))
		//	throw new RuntimeException("Filter at depth=" + depth + " does not match first filter parameter");
		final String curr = iter.next();
		XmlFilter sub = root.getSubFilter(curr);
		if (sub == null) {
			sub = new XmlFilter(curr);
			root.addFilter(sub);
		}
		if (!iter.hasNext()) {
			// At end
			return sub;
		} else {
			// We are still finding leaf
			return buildFilter( sub, depth+1, iter );			
		}
	}
	
}
