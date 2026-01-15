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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.Objects;

/* 
 * An XML handler for the Google Books entry return 
 * 
 * <?xml version='1.0' encoding='UTF-8'?>
 * <entry xmlns='http://www.w3.org/2005/Atom' xmlns:gbs='http://schemas.google.com/books/2008' xmlns:dc='http://purl.org/dc/terms' xmlns:batch='http://schemas.google.com/gdata/batch' xmlns:gd='http://schemas.google.com/g/2005'>
 * 		<id>http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ</id>
 * 		<updated>2010-02-28T10:49:24.000Z</updated>
 * 		<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 * 		<title type='text'>The trigger</title>
 * 		<link rel='http://schemas.google.com/books/2008/info' type='text/html' href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 * 		<link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml' href='http://www.google.com/books/feeds/users/me/volumes'/>
 * 		<link rel='alternate' type='text/html' href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;ie=ISO-8859-1'/>
 * 		<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ'/>
 * 		<gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 * 		<gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 * 		<gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 * 		<dc:creator>Arthur Charles Clarke</dc:creator>
 * 		<dc:creator>Michael P. Kube-McDowell</dc:creator>
 * 		<dc:date>2000-01-01</dc:date>
 * 		<dc:format>Dimensions 11.0x18.0x3.6 cm</dc:format>
 * 		<dc:format>550 pages</dc:format>
 * 		<dc:format>book</dc:format>
 * 		<dc:identifier>A4NDPgAACAAJ</dc:identifier>
 * 		<dc:identifier>ISBN:0006483836</dc:identifier>
 * 		<dc:identifier>ISBN:9780006483830</dc:identifier>
 * 		<dc:language>en</dc:language>
 * 		<dc:publisher>Voyager</dc:publisher>
 * 		<dc:subject>Fiction / Science Fiction / General</dc:subject>
 * 		<dc:subject>Fiction / Technological</dc:subject>
 * 		<dc:subject>Fiction / War &amp; Military</dc:subject>
 * 		<dc:title>The trigger</dc:title>
 * </entry>
 * 
 * <?xml version='1.0' encoding='UTF-8'?>
 * <entry xmlns='http://www.w3.org/2005/Atom' xmlns:gbs='http://schemas.google.com/books/2008' xmlns:dc='http://purl.org/dc/terms' xmlns:batch='http://schemas.google.com/gdata/batch' xmlns:gd='http://schemas.google.com/g/2005'>
 * 		<id>http://www.google.com/books/feeds/volumes/lf2EMetoLugC</id>
 * 		<updated>2010-03-01T07:31:23.000Z</updated>
 * 		<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 * 		<title type='text'>The Geeks' Guide to World Domination</title>
 * 		<link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown' href='http://bks3.books.google.com/books?id=lf2EMetoLugC&amp;printsec=frontcover&amp;img=1&amp;zoom=5&amp;sig=ACfU3U1hcfy_NvWZbH46OzWwmQQCDV46lA&amp;source=gbs_gdata'/>
 * 		<link rel='http://schemas.google.com/books/2008/info' type='text/html' href='http://books.google.com/books?id=lf2EMetoLugC&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 * 		<link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml' href='http://www.google.com/books/feeds/users/me/volumes'/>
 * 		<link rel='alternate' type='text/html' href='http://books.google.com/books?id=lf2EMetoLugC&amp;ie=ISO-8859-1'/>
 * 		<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes/lf2EMetoLugC'/>
 * 		<gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 * 		<gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 * 		<gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 * 		<dc:creator>Garth Sundem</dc:creator>
 * 		<dc:date>2009-03-10</dc:date>
 * 		<dc:description>TUNE IN. TURN ON. GEEK OUT.Sorry, beautiful people. These days, from government to business to technology to Hollywood, geeks rule the world. Finally, here’s the book no self-respecting geek can live without–a guide jam-packed with 314.1516 short entries both useful and fun. Science, pop-culture trivia, paper airplanes, and pure geekish nostalgia coexist as happily in these pages as they do in their natural habitat of the geek brain.In short, dear geek, here you’ll find everything you need to achieve nirvana. And here, for you pathetic nongeeks, is the last chance to save yourselves: Love this book, live this book, and you too can join us in the experience of total world domination. • become a sudoku god• brew your own beer• build a laser beam• classify all living things• clone your pet• exorcise demons• find the world’s best corn mazes• grasp the theory of relativity• have sex on Second Life• injure a fish• join the Knights Templar• kick ass with sweet martial-arts moves• learn ludicrous emoticons• master the Ocarina of Time• pimp your cubicle• program a remote control• quote He-Man and Che Guevara• solve fiendish logic puzzles• touch Carl Sagan • unmask Linus Torvalds• visit Beaver Lick, Kentucky• win bar bets• write your name in ElvishJoin us or die, you will.Begun, the Geek Wars have</dc:description>
 * 		<dc:format>Dimensions 13.2x20.1x2.0 cm</dc:format>
 * 		<dc:format>288 pages</dc:format>
 * 		<dc:format>book</dc:format>
 * 		<dc:identifier>lf2EMetoLugC</dc:identifier>
 * 		<dc:identifier>ISBN:0307450341</dc:identifier>
 * 		<dc:identifier>ISBN:9780307450340</dc:identifier>
 * 		<dc:language>en</dc:language>
 * 		<dc:publisher>Three Rivers Press</dc:publisher>
 * 		<dc:subject>Curiosities and wonders/ Humor</dc:subject>
 * 		<dc:subject>Geeks (Computer enthusiasts)/ Humor</dc:subject>
 * 		<dc:subject>Curiosities and wonders</dc:subject>
 * 		<dc:subject>Geeks (Computer enthusiasts)</dc:subject>
 * 		<dc:subject>Humor / Form / Parodies</dc:subject>
 * 		<dc:subject>Humor / General</dc:subject>
 * 		<dc:subject>Humor / General</dc:subject>
 * 		<dc:subject>Humor / Form / Comic Strips &amp; Cartoons</dc:subject>
 * 		<dc:subject>Humor / Form / Essays</dc:subject>
 * 		<dc:subject>Humor / Form / Parodies</dc:subject>
 * 		<dc:subject>Reference / General</dc:subject>
 * 		<dc:subject>Reference / Curiosities &amp; Wonders</dc:subject>
 * 		<dc:subject>Reference / Encyclopedias</dc:subject>
 * 		<dc:title>The Geeks' Guide to World Domination</dc:title>
 * 		<dc:title>Be Afraid, Beautiful People</dc:title>
 * </entry>
 * 
 */
public class SearchGoogleBooksEntryHandler extends DefaultHandler {
	private StringBuilder builder;
	
	private final Bundle mValues;
	private static boolean mFetchThumbnail;

	public static final String ID = "id";
	public static final String TOTALRESULTS = "totalResults";
	public static final String ENTRY = "entry";
	public static final String AUTHOR = "creator";
	public static final String TITLE = "title";
	public static final String ISBN = "identifier";
	public static final String DATE_PUBLISHED = "date";
	public static final String PUBLISHER = "publisher";
	public static final String PAGES = "format";
	public static final String THUMBNAIL = "link";
	public static final String LANGUAGE = "language";
	public static final String GENRE = "subject";
	public static final String DESCRIPTION = "description";
	public static final String THUMBNAIL_KEY = "__thumbnail";

	SearchGoogleBooksEntryHandler(Bundle values, boolean fetchThumbnail) {
		mValues = values;
		mFetchThumbnail = fetchThumbnail;
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		super.characters(ch, start, length);
		builder.append(ch, start, length);
	}

	private void addIfNotPresent(String key) {
		if (!mValues.containsKey(key) || Objects.requireNonNull(mValues.getString(key)).isEmpty()) {
			mValues.putString(key, builder.toString());
		}		
	}

	@Override
	public void endElement(String uri, String localName, String name) throws SAXException {
		super.endElement(uri, localName, name);
		if (localName.equalsIgnoreCase(TITLE)){
			addIfNotPresent(CatalogueDBAdapter.KEY_TITLE);
		} else if (localName.equalsIgnoreCase(ISBN)){
			String tmp = builder.toString(); 
			if (tmp.indexOf("ISBN:") == 0) {
				tmp = tmp.substring(5); 
				if (!mValues.containsKey(CatalogueDBAdapter.KEY_ISBN) || tmp.length() > Objects.requireNonNull(mValues.getString(CatalogueDBAdapter.KEY_ISBN)).length()) {
					mValues.putString(CatalogueDBAdapter.KEY_ISBN, tmp);
				}
			}
		} else if (localName.equalsIgnoreCase(AUTHOR)){
			Utils.appendOrAdd(mValues, CatalogueDBAdapter.KEY_AUTHOR_DETAILS, builder.toString());
		} else if (localName.equalsIgnoreCase(PUBLISHER)){
			addIfNotPresent(CatalogueDBAdapter.KEY_PUBLISHER);
		} else if (localName.equalsIgnoreCase(LANGUAGE)){
			addIfNotPresent(DatabaseDefinitions.DOM_LANGUAGE.name);
		} else if (localName.equalsIgnoreCase(DATE_PUBLISHED)){
			addIfNotPresent(CatalogueDBAdapter.KEY_DATE_PUBLISHED);
		} else if (localName.equalsIgnoreCase(PAGES)){
			String tmp = builder.toString();
			int index = tmp.indexOf(" pages");
			if (index > -1) {
				tmp = tmp.substring(0, index).trim(); 
				mValues.putString(CatalogueDBAdapter.KEY_PAGES, tmp);
			} else {
				index = tmp.indexOf("Dimensions");
				if (index < 0) {
					mValues.putString(CatalogueDBAdapter.KEY_FORMAT, tmp);
				}
			}
		} else if (localName.equalsIgnoreCase(GENRE)){
			mValues.putString(CatalogueDBAdapter.KEY_GENRE, builder.toString());
		} else if (localName.equalsIgnoreCase(DESCRIPTION)){
			addIfNotPresent(CatalogueDBAdapter.KEY_DESCRIPTION);
		}
		builder.setLength(0);
	}

	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		builder = new StringBuilder();
	}

	@Override
	public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);
		if (mFetchThumbnail && localName.equalsIgnoreCase(THUMBNAIL)){
			if (attributes.getValue("", "rel").equals("http://schemas.google.com/books/2008/thumbnail")) {
				String thumbnail = attributes.getValue("", "href");
				String filename = Utils.saveThumbnailFromUrl(thumbnail, "_GB");
				if (!filename.isEmpty())
					Utils.appendOrAdd(mValues, THUMBNAIL_KEY, filename);
			}
		}
	}
}
