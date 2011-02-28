/*
 * @copyright 2011 Philip Warner
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.os.Bundle;

/**
 * Handle all aspects of searching (and ultimately synchronizing with) LibraryThing.
 * 
 * The basic URLs are:
 *
 * Details via ISBN: http://www.librarything.com/services/rest/1.1/?method=librarything.ck.getwork&apikey=<DEVKEY>&isbn=<ISBN>
 * Covers via ISBN: http://covers.librarything.com/devkey/<DEVKEY>/large/isbn/<ISBN>
 *
 * TODO: extend the use of LibraryThing:
 * - Lookup title using keywords: http://www.librarything.com/api/thingTitle/hand oberon
 * - consider scraping html for covers: http://www.librarything.com/work/18998/covers
 * 
 * @author Grunthos
 *
 */
public class LibraryThingManager {

	// 
	Bundle mBookData = null;
	
	// Words in XML
	public static String ID = "id";
	public static String AUTHOR = "author";
	public static String RESPONSE = "response";
	public static String FIELD = "field";
	public static String ISBN = "isbn";
	public static String ITEM = "item";
	public static String FACT = "fact";
	public static String CANONICAL_TITLE = "canonicaltitle";
	public static String SERIES = "series";
	public static String PLACES = "placesmentioned";
	public static String CHARACTERS = "characternames";

	public static String COVER_URL_LARGE = "http://covers.librarything.com/devkey/%1$s/large/isbn/%2$s";
	public static String COVER_URL_MEDIUM = "http://covers.librarything.com/devkey/%1$s/medium/isbn/%2$s";
	public static String COVER_URL_SMALL = "http://covers.librarything.com/devkey/%1$s/small/isbn/%2$s";
	public static String DETAIL_URL = "http://www.librarything.com/services/rest/1.1/?method=librarything.ck.getwork&apikey=%1$s&isbn=%2$s";
	public static String EDITIONS_URL = "http://www.librarything.com/api/thingISBN/%s";

	// Field types we are interested in.
	private enum FieldTypes{ NONE, AUTHOR, TITLE, SERIES, PLACES, CHARACTERS, OTHER };

	// Sizes of thumbnails
	public enum ImageSizes { SMALL, MEDIUM, LARGE };

	LibraryThingManager(Bundle bookData) {
		mBookData = bookData;
	}

	/**
	 * Search LibaryThing for an ISBN using the Web API.
	 * 
	 * @param isbn		ISBN to lookup
	 * @param bookData	COllection to save results in
	 * 
	 * A typical (and thorough) LibraryThing ISBN response looks like (with formatting added):
	 * 
	 * <?xml version="1.0" encoding="UTF-8"?>
	 * <response stat="ok">
	 *   <ltml xmlns="http://www.librarything.com/" version="1.1">
	 *     <item id="5196084" type="work">
	 *       <author id="28" authorcode="asimovisaac">Isaac Asimov</author>
	 *       <url>http://www.librarything.com/work/5196084</url>
	 *       <commonknowledge>
	 *         <fieldList>
	 *           <field type="4" name="awards" displayName="Awards and honors">
	 *             <versionList>
	 *               <version id="3324305" archived="0" lang="eng">
	 *                 <date timestamp="1296476301">Mon, 31 Jan 2011 07:18:21 -0500</date>
	 *                 <person id="325052"><name>Cecrow</name><url>http://www.librarything.com/profile/Cecrow</url></person>
	 *                 <factList>
	 *                   <fact>1001 Books You Must Read Before You Die (2006/2008/2010 Edition)</fact>
	 *                   <fact>Astounding/Analog Science Fiction and Fact All-Time Poll (placed 23, 1952)</fact>
	 *                   <fact>Astounding/Analog Science Fiction and Fact All-Time Poll (placed 21, 1956)</fact>
	 *                   <fact>Harenberg Buch der 1000 B�cher (1. Ausgabe)</fact>
	 *                   <fact>501 Must-Read Books (Science Fiction)</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="37" name="movies" displayName="Related movies">
	 *             <versionList>
	 *               <version id="3120269" archived="0" lang="eng">
	 *                 <date timestamp="1292202792">Sun, 12 Dec 2010 20:13:12 -0500</date>
	 *                 <person id="656066">
	 *                   <name>Scottneumann</name>
	 *                   <url>http://www.librarything.com/profile/Scottneumann</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>Robots (1988 | tt0174170)</fact>
	 *                   <fact>I, Robot (2004 | tt0343818)</fact>
	 *                   <fact>The Outer Limits: I Robot (1963 | tt0056777)</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="40" name="publisherseries" displayName="Publisher Series">
	 *             <versionList>
	 *               <version id="2971007" archived="0" lang="eng">
	 *                 <date timestamp="1289497446">Thu, 11 Nov 2010 12:44:06 -0500</date>
	 *                 <person id="3929">
	 *                   <name>sonyagreen</name>
	 *                   <url>http://www.librarything.com/profile/sonyagreen</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>Voyager Classics</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="14" name="description" displayName="Description">
	 *             <versionList>
	 *               <version id="2756634" archived="0" lang="eng">
	 *                 <date timestamp="1281897478">Sun, 15 Aug 2010 14:37:58 -0400</date>
	 *                 <person id="203279">
	 *                   <name>jseger9000</name>
	 *                   <url>http://www.librarything.com/profile/jseger9000</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>&lt;![CDATA[ Contents:&lt;br&gt;&lt;br&gt;Introduction&lt;br&gt;Robbie&lt;br&gt;Runaround&lt
	 *                         ;br&gt;Reason&lt;br&gt;Catch That Rabbit&lt;br&gt;Liar!&lt;br&gt;Little Lost Robot&lt
	 *                         ;br&gt;Escape!&lt;br&gt;Evidence&lt;br&gt;The Evitable Conflict ]]&gt;
	 *                   </fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="23" name="series" displayName="Series">
	 *             <versionList>
	 *               <version id="2742329" archived="0" lang="eng">
	 *                 <date timestamp="1281338643">Mon, 09 Aug 2010 03:24:03 -0400</date>
	 *                 <person id="1162290">
	 *                   <name>larry.auld</name>
	 *                   <url>http://www.librarything.com/profile/larry.auld</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>Isaac Asimov's Robot Series (0.1)</fact>
	 *                   <fact>Robot/Foundation</fact>
	 *                   <fact>Robot/Empire/Foundation - Chronological (book 1)</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="16" name="originalpublicationdate" displayName="Original publication date">
	 *             <versionList>
	 *               <version id="2554955" archived="0" lang="eng">
	 *                 <date timestamp="1275746736">Sat, 05 Jun 2010 10:05:36 -0400</date>
	 *                 <person id="125174">
	 *                   <name>paulhurtley</name>
	 *                   <url>http://www.librarything.com/profile/paulhurtley</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>1950 (Collection)</fact>
	 *                   <fact>1944 (Catch that Rabbit)</fact>
	 *                   <fact>1945 (Escape!)</fact>
	 *                   <fact>1946 (Evidence)</fact>
	 *                   <fact>1950 (The Evitable Conflict)</fact>
	 *                   <fact>1941  (Liar)</fact>
	 *                   <fact>1947  (Little Lost Robot)</fact>
	 *                   <fact>1940  (Robbie)</fact>
	 *                   <fact>1942  (Runaround)</fact>
	 *                   <fact>1941  (Reason)</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="27" name="quotations" displayName="Quotations">
	 *             <versionList>
	 *               <version id="2503597" archived="0" lang="eng">
	 *                 <date timestamp="1274377341">Thu, 20 May 2010 13:42:21 -0400</date>
	 *                 <person id="1797">
	 *                   <name>lorax</name>
	 *                   <url>http://www.librarything.com/profile/lorax</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>&lt;![CDATA[ The Three Laws of Robotics
	 *                     1. A robot may not injure a human being, or, through inaction, allow a human being to come to harm. 
	 *                     2. A robot must obey the orders given it by human beings except where such orders would conflict 
	 *                        with the First Law. 
	 *                     3. A robot must protect its own existence as long as such protection does not conflict with the 
	 *                        First or Second Law.  ]]&gt;
	 *                   </fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="30" name="dedication" displayName="Dedication">
	 *             <versionList>
	 *               <version id="2503596" archived="0" lang="eng">
	 *                 <date timestamp="1274377341">Thu, 20 May 2010 13:42:21 -0400</date>
	 *                 <person id="1797">
	 *                   <name>lorax</name>
	 *                   <url>http://www.librarything.com/profile/lorax</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>&lt;![CDATA[ To John W. Campbell, Jr., who godfathered the robots ]]&gt;</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="26" name="lastwords" displayName="Last words">
	 *             <versionList>
	 *               <version id="2503594" archived="0" lang="eng">
	 *                 <date timestamp="1274377340">Thu, 20 May 2010 13:42:20 -0400</date>
	 *                 <person id="1797">
	 *                   <name>lorax</name>
	 *                   <url>http://www.librarything.com/profile/lorax</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>&lt;![CDATA[ Robbie:&lt;br&gt;"Well," said Mrs. Weston, at last, "I guess he can stay with us until he rusts." ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Runaround:&lt;br&gt;"Space Station," said Donovan, "here I come." ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Reason"&lt;br&gt;He grinned � and went into the ship.  Muller would be here for several weeks � ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Catch That Rabbit:&lt;br&gt;****&lt;br&gt;**** too spoilerish! ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Liar:&lt;br&gt;"Liar!" ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Little Lost Robot:&lt;br&gt;"� His very superiority caught him.  Good-by General" ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Escape:&lt;br&gt;To which Bogert added absently, "Strictly according to the contract, too." ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Evidence:&lt;br&gt;Stephen Byerley chuckled.  "I must reply that that is a somewhat farfetched idea."&lt;br&gt;The door closed behind her. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ The Evitable Conflict:&lt;br&gt;And the fire behind the quartz went out and only a curl of smoke was left to indicate its place. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ &lt;i&gt;She died last month at the age of eighty-two.&lt;/i&gt; ]]&gt;</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="25" name="firstwords" displayName="First words">
	 *             <versionList>
	 *               <version id="2503593" archived="0" lang="eng">
	 *                 <date timestamp="1274377340">Thu, 20 May 2010 13:42:20 -0400</date>
	 *                 <person id="1797">
	 *                   <name>lorax</name>
	 *                   <url>http://www.librarything.com/profile/lorax</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>&lt;![CDATA[ Robbie:&lt;br&gt;"Ninety-eight � ninety-nine � &lt;i&gt;one hundred&lt;/i&gt;." ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Runaround:&lt;br&gt;It was one of Gregory Powell's favorite platitudes that nothing was to 
	 *                         be gained from excitement, so when Mike Donovan came leaping down the stairs toward him, red hair matted 
	 *                         with perspiration, Powell frowned. ]]&gt;
	 *                   </fact>
	 *                   <fact>&lt;![CDATA[ Reason:&lt;br&gt;Half a year later, the boys had changed their minds. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Catch That Rabbit:&lt;br&gt;The vacation was longer than two weeks. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Liar!&lt;br&gt;Alfred Lanning lit his cigar carefully, but the tips of his fingers were trembling slightly. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Little Lost Robot:&lt;br&gt;When I did see Susan Calvin again, it was at the door of her office. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Escape!:&lt;br&gt;When Susan Calvin returned from Hyper Base, Alfred Lanning was waiting for her. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ Evidence:&lt;br&gt;Francis Quinn was a politician of the new school. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ The Evitable Conflict:&lt;br&gt;The Co-ordinator, in his private study, had that medieval curiosity, a fireplace. ]]&gt;</fact>
	 *                   <fact>&lt;![CDATA[ &lt;i&gt;I looked at my notes and I didn't like them.&lt;/i&gt; (Introduction) ]]&gt;</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="21" name="canonicaltitle" displayName="Canonical title">
	 *             <versionList>
	 *               <version id="2503590" archived="0" lang="eng">
	 *                 <date timestamp="1274377338">Thu, 20 May 2010 13:42:18 -0400</date>
	 *                 <person id="1797">
	 *                   <name>lorax</name>
	 *                   <url>http://www.librarything.com/profile/lorax</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>I, Robot</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *           <field type="3" name="characternames" displayName="People/Characters">
	 *             <versionList>
	 *               <version id="2503589" archived="0" lang="eng">
	 *                 <date timestamp="1274377337">Thu, 20 May 2010 13:42:17 -0400</date>
	 *                 <person id="1797">
	 *                   <name>lorax</name>
	 *                   <url>http://www.librarything.com/profile/lorax</url>
	 *                 </person>
	 *               <factList>
	 *                 <fact>Susan Calvin</fact>
	 *                 <fact>Cutie (QT1)</fact>
	 *                 <fact>Gregory Powell</fact>
	 *                 <fact>Mike Donovan</fact>
	 *                 <fact>Robbie (RB-series)</fact>
	 *                 <fact>Mr. Weston</fact>
	 *                 <fact>Gloria Weston</fact>
	 *                 <fact>Mrs. Weston</fact>
	 *                 <fact>SPD-13 (Speedy)</fact>
	 *                 <fact>Speedy (SPD-13)</fact>
	 *                 <fact>QT1 (Cutie)</fact>
	 *                 <fact>The Master</fact>
	 *                 <fact>Prophet of the Master</fact>
	 *                 <fact>Ren� Descartes</fact>
	 *                 <fact>DV-5 (Dave)</fact><fact>Dave (DV-5)</fact>
	 *                 <fact>HRB-34 (Herbie)</fact>
	 *                 <fact>Herbie (HRB-34)</fact>
	 *                 <fact>Gerald Black</fact>
	 *                 <fact>NS-2 (Nestor)</fact>
	 *                 <fact>Nestor (NS-2)</fact>
	 *                 <fact>Peter Bogert</fact>
	 *                 <fact>The Brain (computer)</fact>
	 *                 <fact>Stephen Byerley</fact>
	 *                 <fact>Francis Quinn</fact>
	 *               </factList>
	 *             </version>
	 *           </versionList>
	 *         </field>
	 *         <field type="2" name="placesmentioned" displayName="Important places">
	 *           <versionList>
	 *             <version id="2503588" archived="0" lang="eng">
	 *               <date timestamp="1274377336">Thu, 20 May 2010 13:42:16 -0400</date>
	 *                 <person id="1797">
	 *                   <name>lorax</name>
	 *                   <url>http://www.librarything.com/profile/lorax</url>
	 *                 </person>
	 *                 <factList>
	 *                   <fact>Mercury</fact>
	 *                   <fact>New York, New York, USA</fact>
	 *                   <fact>Roosevelt Building</fact>
	 *                   <fact>U.S. Robots and Mechanical Men factory</fact>
	 *                   <fact>Hyper Base</fact>
	 *                 </factList>
	 *               </version>
	 *             </versionList>
	 *           </field>
	 *         </fieldList>
	 *       </commonknowledge>
	 *     </item>
	 *     <legal>By using this data you agree to the LibraryThing API terms of service.</legal>
	 *   </ltml>
	 * </response>
	 * 
	 * A less well-known work produces rather less data:
	 * 
	 * <?xml version="1.0" encoding="UTF-8"?>
	 * <response stat="ok">
	 *   <ltml xmlns="http://www.librarything.com/" version="1.1">
	 *     <item id="255375" type="work">
	 *       <author id="359458" authorcode="fallonmary">Mary Fallon</author>
	 *       <url>http://www.librarything.com/work/255375</url>
	 *       <commonknowledge/>
	 *     </item>
	 *     <legal>By using this data you agree to the LibraryThing API terms of service.</legal>
	 *   </ltml>
	 * </response>
	 *
	 * but in both cases, in both cases it should be noted that the covers are still available.
	 *
	 */
	public void searchByIsbn(String isbn) {
		// Base path for an ISBN search
		String path = String.format(DETAIL_URL, LibraryThingApiKey.get(), isbn);

		if (isbn.equals(""))
			throw new IllegalArgumentException();

		URL url;

		// Setup the parser
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchLibraryThingEntryHandler entryHandler = new LibraryThingManager.SearchLibraryThingEntryHandler(mBookData);

		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			parser.parse(Utils.getInputStream(url), entryHandler);
			// Dont bother catching general exceptions, they will be caught by the caller.
		} catch (MalformedURLException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			BookCatalogue.logError(e, s);
		} catch (ParserConfigurationException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			BookCatalogue.logError(e, s);
		} catch (SAXException e) {
			String s = e.getMessage(); // "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			BookCatalogue.logError(e, s);
		} catch (java.io.IOException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			BookCatalogue.logError(e, s);
		}

		getCoverImage(isbn, mBookData, ImageSizes.LARGE);

		return;
	}

	/**
	 * Parser Handler to collect the book data.
	 * 
	 * @author Grunthos
	 */
	private class SearchLibraryThingEntryHandler extends DefaultHandler  {
		private Bundle mBookData = null;
		private StringBuilder mBuilder = new StringBuilder();

		private FieldTypes mFieldType = FieldTypes.OTHER;
		private String mWorkId;

		SearchLibraryThingEntryHandler(Bundle bookData) {
			mBookData = bookData;
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			super.characters(ch, start, length);
			mBuilder.append(ch, start, length);
		}

		/**
		 * Add the current characters to the book collection if not already present.
		 * 
		 * @param key	Key for data to add
		 */
		private void addIfNotPresent(String key) {
			if (!mBookData.containsKey(key) || mBookData.getString(key).length() == 0) {
				mBookData.putString(key, mBuilder.toString());
			}		
		}

		/**
		 * Add the current text data to the book collection if not present, otherwise 
		 * append the data as a list.
		 * 
		 * @param key	Key for data to add
		 */
		private void appendOrAdd(String key) {
			Utils.appendOrAdd(mBookData, key, mBuilder.toString());
		}

		@Override
		public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
			super.startElement(uri, localName, name, attributes);

			// reset the string. See note in endElement() for a discussion.
			mBuilder.setLength(0);

			if (localName.equalsIgnoreCase(RESPONSE)){
				// Not really much to do; we *could* look for the <err> element, then report it.
				String stat = attributes.getValue("", "stat");
			} else if (localName.equalsIgnoreCase(ITEM)){
				// We don't use it yet, but this contains the Work ID. LibraryThing supports
				// retrieval of other editions etc via the Work ID.
				String type = attributes.getValue("","type");
				if (type != null && type.equalsIgnoreCase("work")) {
					mWorkId = attributes.getValue("", "id");
				}
			} else if (localName.equalsIgnoreCase(FIELD)){
				// FIELDs are the main things we want. Once we are in a fieldm we wait for a FACT; these
				// are read in the endElement() method.
				String fieldName = attributes.getValue("", "name");
				if (fieldName != null) {
					if (fieldName.equalsIgnoreCase(CANONICAL_TITLE)) {
						mFieldType = FieldTypes.TITLE;
					} else if (fieldName.equalsIgnoreCase(SERIES)) {
						mFieldType = FieldTypes.SERIES;
					} else if (fieldName.equalsIgnoreCase(PLACES)) {
						mFieldType = FieldTypes.PLACES;
					} else if (fieldName.equalsIgnoreCase(CHARACTERS)) {
						mFieldType = FieldTypes.CHARACTERS;
					}					
				}
			}
		}

		@Override
		public void endElement(String uri, String localName, String name) throws SAXException {
			super.endElement(uri, localName, name);
			
			if (localName.equalsIgnoreCase(FIELD)){
				// Reset the current field
				mFieldType = FieldTypes.NONE;

			} else if (localName.equalsIgnoreCase(AUTHOR)) {
				// Add the author
				Utils.appendOrAdd(mBookData, CatalogueDBAdapter.KEY_AUTHOR_DETAILS, mBuilder.toString());

			} else if (localName.equalsIgnoreCase(FACT)) {
				// Process the FACT according to the active FIELD type.

				switch (mFieldType) {

				case TITLE:
					addIfNotPresent(CatalogueDBAdapter.KEY_TITLE);
					break;

				case SERIES:
					appendOrAdd(CatalogueDBAdapter.KEY_SERIES_DETAILS);
					break;

				case PLACES:
					appendOrAdd("__places");
					break;

				case CHARACTERS:
					appendOrAdd("__characters");
					break;	
				}
			}
			// Note:
			// Always reset the length. This is not entirely the right thing to do, but works
			// because we always want strings from the lowest level (leaf) XML elements.
			// To be completely correct, we should maintain a stack of builders that are pushed and
			// popped as each startElement/endElement is called. But lets not be pedantic for now.
			mBuilder.setLength(0);
		}

	}

	/**
	 * Get the cover image using the ISBN
	 * 
	 * @param isbn
	 */
	public static String getCoverImageUrl(String isbn, ImageSizes size) {
		String path = COVER_URL_SMALL;

		switch(size) {
		case SMALL:
			path = COVER_URL_SMALL;
			break;
		case MEDIUM:
			path = COVER_URL_MEDIUM;
			break;
		case LARGE:
			path = COVER_URL_LARGE;
			break;
		}
		// Get the 'large' version
		String url = String.format(path, LibraryThingApiKey.get(), isbn);
		return url;
	}
	/**
	 * Get the cover image using the ISBN
	 * 
	 * @param isbn
	 */
	public static String getCoverImage(String isbn, Bundle bookData, ImageSizes size) {
		String url = getCoverImageUrl(isbn, size);
		// Save it with an _LT suffix
		String filename = Utils.saveThumbnailFromUrl(url, "_LT_" + size + "_" + isbn);
		if (filename.length() > 0 && bookData != null)
			Utils.appendOrAdd(bookData, "__thumbnail", filename);
		return filename;
	}

	/**
	 * Search for edition data.
	 *
	 * @param bookData
	 * 
	 */
	public static ArrayList<String> searchEditions(String isbn) {
		// Base path for an ISBN search
		String path = String.format(EDITIONS_URL, isbn);
		if (isbn.equals(""))
			throw new RuntimeException("Can not get editions without an ISBN");

		ArrayList<String> editions = new ArrayList<String>();

		// Setup the parser
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SearchLibraryThingEditionHandler entryHandler = new LibraryThingManager.SearchLibraryThingEditionHandler(editions);

		Utils.parseUrlOutput(path, factory, entryHandler);

		return editions;
	}

	/**
	 * Parser Handler to collect the edition data.
	 * 
	 * Typical request output:
	 * 
	 * <?xml version="1.0" encoding="utf-8"?>
	 * <idlist>
	 *  <isbn>0380014300</isbn>
	 *  <isbn>0839824270</isbn>
	 *  <isbn>0722194390</isbn>
	 *  <isbn>0783884257</isbn>
	 *  ...etc...
	 *  <isbn>2207301907</isbn>
	 * </idlist>
	 * @author Grunthos
	 */
	static private class SearchLibraryThingEditionHandler extends DefaultHandler  {
		private StringBuilder mBuilder = new StringBuilder();
		private ArrayList<String> mEditions = new ArrayList<String>();

		SearchLibraryThingEditionHandler(ArrayList<String> editions) {
			mEditions = editions;
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			super.characters(ch, start, length);
			mBuilder.append(ch, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String name) throws SAXException {
			super.endElement(uri, localName, name);
			
			if (localName.equalsIgnoreCase(ISBN)){
				// Add the isbn
				String isbn = mBuilder.toString();
				mEditions.add(isbn);
			}
			// Note:
			// Always reset the length. This is not entirely the right thing to do, but works
			// because we always want strings from the lowest level (leaf) XML elements.
			// To be completely correct, we should maintain a stack of builders that are pushed and
			// popped as each startElement/endElement is called. But lets not be pedantic for now.
			mBuilder.setLength(0);
		}

	}

}
