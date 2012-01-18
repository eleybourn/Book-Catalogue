package com.eleybourn.bookcatalogue.goodreads.api;

import java.util.ArrayList;
import java.util.Hashtable;

import org.xml.sax.Attributes;

/**
 * A class to help parsing Sax Xml output. For goodreads XML output, 90% of the XML can be 
 * thrown away but we do need to ensure we get the tags from the right context. The XmlFilter
 * objects build a tree of filters and XmlHandler objects that make this process more manageable.
 * 
 * See SearchBooksApiHandler for an example of usage.
 * 
 * @author Grunthos
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
	/** Action to perform, if any, when the associated tag is finished */
	XmlHandler mEndAction = null;

	/** Interface definition for filter handlers */
	public interface XmlHandler {
		void process(ElementContext context);
	}

	/**
	 * Class used to define the context of a specific tag. The 'body' element will only be
	 * filled in the call to the 'processEnd' method.
	 * @author Grunthos
	 *
	 */
	public static class ElementContext {
		String uri;
		String localName;
		String name;
		Attributes attributes;
		String preText;
		public String body;
		public XmlFilter filter;
		public ElementContext(String uri, String localName, String name, Attributes attributes, String preText) {
			this.uri = uri;
			this.localName = localName;
			this.name = name;
			this.attributes = attributes;
			this.preText = preText;
		}
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
		if (mStartAction != null)
			mStartAction.process(context);
	}
	/**
	 * Called when associated tag is finished.
	 * 
	 * @param context
	 */
	public void processEnd(ElementContext context) {
		if (mEndAction != null)
			mEndAction.process(context);
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
		if (mEndAction != null)
			throw new RuntimeException("End Action already set");			
		mEndAction = endAction;
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
		if (mStartAction != null)
			throw new RuntimeException("Start Action already set");			
		mStartAction = startAction;
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
		return buildFilter(root, 0, filters);
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
	private static XmlFilter buildFilter(XmlFilter root, int depth, String... filters ) {
		//if (!root.matches(filters[depth]))
		//	throw new RuntimeException("Filter at depth=" + depth + " does not match first filter parameter");

		XmlFilter sub = root.getSubFilter(filters[depth]);
		if (sub == null) {
			sub = new XmlFilter(filters[depth]);
			root.addFilter(sub);
		}
		if (depth == (filters.length-1)) {
			// At end
			return sub;
		} else {
			// We are still finding leaf
			return buildFilter( sub, depth+1, filters );			
		}
	}
}
