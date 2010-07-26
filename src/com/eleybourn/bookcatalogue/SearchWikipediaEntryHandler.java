/*
 * @copyright 2010 Evan Leybourn
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

package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.util.Log;

/** 
 * An XML handler for the Wikipedia entry return 
 */
public class SearchWikipediaEntryHandler extends DefaultHandler {
	private StringBuilder builder;
	private boolean entry1 = false;
	private boolean entry2 = false;
	private boolean entry3 = false;
	private boolean intoc = false;
	private boolean in_parent_ul = false;
	private boolean ready_to_close_parent_ul = false;
	private int div = 0;
	private int entrydiv = 0;
	private String this_title = "";
	public ArrayList<String> titles = new ArrayList<String>();
	
	public static String DIV = "div";
	public static String LIST1 = "ul";
	public static String LIST2 = "ol";
	public static String ENTRY = "li";
	public static String LINK1 = "a"; //optional
	public static String LINK2 = "i"; //optional
	public static String LINK3 = "b"; //optional
	public static String TOC_TABLE = "table";
	
	public ArrayList<String> getList(){
		return titles;
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		super.characters(ch, start, length);
		builder.append(ch, start, length);
	}

	@Override
	public void endElement(String uri, String localName, String name) throws SAXException {
		super.endElement(uri, localName, name);
		// don't do anything if we are in the table of contents
		if (intoc == false) {
			if (localName.equalsIgnoreCase(ENTRY)){
				if (entry1 == true && entry2 == true) {
					Log.e("BC", "inentry false");
					String title = this_title + builder.toString();
					title = title.replace("\"", "").trim();
					Log.e("BC", title);
					if (title != null && title != "") {
						titles.add(title);
					}
					this_title = "";
					entry3 = false;
				}
			} else if (localName.equalsIgnoreCase(LINK1) || localName.equalsIgnoreCase(LINK2) || localName.equalsIgnoreCase(LINK3)){
					if (entry1 == true && entry2 == true && entry3 == true) {
						this_title += builder.toString();
					}
			} else if (localName.equalsIgnoreCase(LIST1) || localName.equalsIgnoreCase(LIST2)){
				if (in_parent_ul == true && ready_to_close_parent_ul == false) {
					// inner ul (if exists)
					in_parent_ul = false;
					entry3 = false;
				} else if (entry1 == true && entry2 == true) {
					Log.e("BC", "inlist false");
					entry1 = false;
					entry2 = false;
					entry3 = false;
					in_parent_ul = false;
				}
			} 
		}
		if (localName.equalsIgnoreCase(DIV)){
			if (entry1 == true && div==entrydiv) {
				Log.e("BC", "indiv false");
				entry1 = false;
				entry2 = false;
				entry3 = false;
			}
			div--;
		}
		if (localName.equalsIgnoreCase(TOC_TABLE)){
			if (intoc == true) {
				Log.e("BC", "intoc false");
				intoc = false;
			}
		}
		builder.setLength(0);
	}

	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		builder = new StringBuilder();
		titles = new ArrayList<String>();
	}
	
	@Override
	public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);
		if (localName.equalsIgnoreCase(DIV)) {
			div++;
			String idName = attributes.getValue("id");
			if (idName != null && idName.equals("bodyContent")) {
				Log.e("BC", "indiv true");
				entrydiv = div;
				entry1 = true;
			}
		}
		if (entry1 == true && localName.equalsIgnoreCase(TOC_TABLE)) {
			String idName = attributes.getValue("id");
			if (idName != null && idName.equals("toc")) {
				Log.e("BC", "intoc true");
				intoc = true;
			}
		}
		if (intoc == false) {
			// This is a parent ul. Not the list ul
			if (entry1 == true && entry2 == true && localName.equalsIgnoreCase(LIST1) || localName.equalsIgnoreCase(LIST2)) {
				// inner ul (if exists)
				Log.e("BC", "inlist x2 true");
				in_parent_ul = true;
				this_title = "";
				ready_to_close_parent_ul = false;
			} else if (entry1 == true && localName.equalsIgnoreCase(LIST1) || localName.equalsIgnoreCase(LIST2)) {
				Log.e("BC", "inlist true");
				entry2 = true;
				ready_to_close_parent_ul = true;
			}
			if (entry1 == true && entry2 == true && localName.equalsIgnoreCase(ENTRY)) {
				Log.e("BC", "inentry true");
				entry3 = true;
			}
		}
	}
}
