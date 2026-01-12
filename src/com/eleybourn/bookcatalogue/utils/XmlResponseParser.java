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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.eleybourn.bookcatalogue.utils.XmlFilter.ElementContext;

/**
 * Base class for parsing the output any web request that returns an XML response. NOTE: this does
 * not include general web page parsing since they often do not conform to XML formatting standards.
 * This class is used with the XmlFilter class to call user-defined code at specific points in
 * an XML file.
 * 
 * @author Philip Warner
 */
public class XmlResponseParser extends DefaultHandler {
	/** Temporary storage for inter-tag text */
    final StringBuilder m_builder = new StringBuilder();
	/** Stack of parsed tags giving context to the XML parser */
    final ArrayList<ElementContext> m_parents = new ArrayList<>();

	/**
	 * Constructor. Requires a filter tree.
	 * 
	 * @param rootFilter		Filter tree to use
	 */
	public XmlResponseParser(XmlFilter rootFilter) {
		// Build the root context and add to hierarchy.
		ElementContext ctx = new ElementContext(null, null, null, null, null);
		ctx.filter = rootFilter;
		m_parents.add(ctx);
	}

	/**
	 * Gather inter-tag text
	 */
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		super.characters(ch, start, length);
		m_builder.append(ch, start, length);
	}

	/**
	 * Handle a new tag.
	 */
	@Override
	public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);

		// Get the current context (ie. the enclosing tag)
		ElementContext currElement = m_parents.get(m_parents.size()-1);
		// Get the active filter for the outer context, if present
		XmlFilter currFilter = currElement.filter;
		// Create a new context for this new tag saving the current inter-tag text for later
		ElementContext ctx = new ElementContext(uri, localName, name, attributes, m_builder.toString());

		// If there is an active filter, then see if the new tag is of any interest
		if (currFilter != null) {
			// Check for interest in new tag
			XmlFilter filter = currElement.filter.getSubFilter(ctx);
			// If new tag has a filter, store it in the new context object
			ctx.filter = filter;
			// If we got a filter, tell it a tag is now starting.
			if (filter != null) 
				filter.processStart(ctx);
		}
		// Add the new tag to the context hierarchy and reset 
		m_parents.add(ctx);
		// Reset the inter-tag text storage.
		m_builder.setLength(0);
	}

	/**
	 * Handle the end of the current tag
	 */
	@Override
	public void endElement(String uri, String localName, String name) throws SAXException {
		super.endElement(uri, localName, name);

		// Get out current context from the hierarchy and pop from stack
		ElementContext thisElement = m_parents.remove(m_parents.size()-1);
		// Minor paranoia. Make sure name matches. Total waste of time, right?
		if (!thisElement.localName.equals(localName)) {
			throw new RuntimeException("End element '" + localName + "' does not match start element '" + thisElement.localName + "'");
		}
		
		// Save the text that appeared inside this tag (but not inside inner tags)
		thisElement.body = m_builder.toString();

		// If there is an active filter in this context, then tell it the tag is finished.
		if (thisElement.filter != null) {
			thisElement.filter.processEnd(thisElement);				
		}

		// Reset the inter-tag text and and append the previously saved 'pre-text'.
		m_builder.setLength(0);
		m_builder.append(thisElement.preText);
	}		
}
