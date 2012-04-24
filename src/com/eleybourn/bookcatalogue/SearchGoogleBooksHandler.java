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

/* 
 * An XML handler for the Google Books return 
 * 
 * An example response looks like;
 * <?xml version='1.0' encoding='UTF-8'?>
 * <feed xmlns='http://www.w3.org/2005/Atom' xmlns:openSearch='http://a9.com/-/spec/opensearchrss/1.0/' xmlns:gbs='http://schemas.google.com/books/2008' xmlns:dc='http://purl.org/dc/terms' xmlns:batch='http://schemas.google.com/gdata/batch' xmlns:gd='http://schemas.google.com/g/2005'>
 * 		<id>http://www.google.com/books/feeds/volumes</id>
 * 		<updated>2010-02-28T03:28:09.000Z</updated>
 * 		<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 * 		<title type='text'>Search results for ISBN9780006483830</title>
 * 		<link rel='alternate' type='text/html' href='http://www.google.com'/>
 * 		<link rel='http://schemas.google.com/g/2005#feed' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes'/>
 * 		<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes?q=ISBN9780006483830'/>
 * 		<author>
 * 			<name>Google Books Search</name>
 * 			<uri>http://www.google.com</uri>
 * 		</author>
 * 		<generator version='beta'>Google Book Search data API</generator>
 * 		<openSearch:totalResults>1</openSearch:totalResults>
 * 		<openSearch:startIndex>1</openSearch:startIndex>
 * 		<openSearch:itemsPerPage>1</openSearch:itemsPerPage>
 * 		<entry>
 * 			<id>http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ</id>
 * 			<updated>2010-02-28T03:28:09.000Z</updated>
 * 			<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 * 			<title type='text'>The trigger</title>
 * 			<link rel='http://schemas.google.com/books/2008/info' type='text/html' href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;dq=ISBN9780006483830&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 * 			<link rel='http://schemas.google.com/books/2008/preview' type='text/html' href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;dq=ISBN9780006483830&amp;ie=ISO-8859-1&amp;cd=1&amp;source=gbs_gdata'/>
 * 			<link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml' href='http://www.google.com/books/feeds/users/me/volumes'/>
 * 			<link rel='alternate' type='text/html' href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;dq=ISBN9780006483830&amp;ie=ISO-8859-1'/>
 * 			<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ'/>
 * 			<gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 * 			<gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 * 			<gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 * 			<dc:creator>Arthur Charles Clarke</dc:creator>
 * 			<dc:creator>Michael P. Kube-McDowell</dc:creator>
 * 			<dc:date>2000-01-01</dc:date>
 * 			<dc:format>550 pages</dc:format>
 * 			<dc:format>book</dc:format>
 * 			<dc:identifier>A4NDPgAACAAJ</dc:identifier>
 * 			<dc:identifier>ISBN:0006483836</dc:identifier>
 * 			<dc:identifier>ISBN:9780006483830</dc:identifier>
 * 			<dc:subject>Fiction</dc:subject>
 * 			<dc:title>The trigger</dc:title>
 * 		</entry>
 * </feed>
 * 
 * <?xml version='1.0' encoding='UTF-8'?>
 * <feed xmlns='http://www.w3.org/2005/Atom' xmlns:openSearch='http://a9.com/-/spec/opensearchrss/1.0/' xmlns:gbs='http://schemas.google.com/books/2008' xmlns:dc='http://purl.org/dc/terms' xmlns:batch='http://schemas.google.com/gdata/batch' xmlns:gd='http://schemas.google.com/g/2005'>
 * 		<id>http://www.google.com/books/feeds/volumes</id>
 * 		<updated>2010-03-01T07:27:49.000Z</updated>
 * 		<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 * 		<title type='text'>Search results for ISBN9780307450340</title>
 * 		<link rel='alternate' type='text/html' href='http://www.google.com'/>
 * 		<link rel='http://schemas.google.com/g/2005#feed' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes'/>
 * 		<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes?q=ISBN9780307450340'/>
 * 		<author>
 * 			<name>Google Books Search</name>
 * 			<uri>http://www.google.com</uri>
 *		</author>
 *		<generator version='beta'>Google Book Search data API</generator>
 *		<openSearch:totalResults>1</openSearch:totalResults>
 *		<openSearch:startIndex>1</openSearch:startIndex>
 *		<openSearch:itemsPerPage>1</openSearch:itemsPerPage>
 *		<entry>
 *			<id>http://www.google.com/books/feeds/volumes/lf2EMetoLugC</id>
 *			<updated>2010-03-01T07:27:49.000Z</updated>
 *			<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/books/2008#volume'/>
 *			<title type='text'>The Geeks' Guide to World Domination</title>
 *			<link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown' href='http://bks3.books.google.com/books?id=lf2EMetoLugC&amp;printsec=frontcover&amp;img=1&amp;zoom=5&amp;sig=ACfU3U1hcfy_NvWZbH46OzWwmQQCDV46lA&amp;source=gbs_gdata'/>
 *			<link rel='http://schemas.google.com/books/2008/info' type='text/html' href='http://books.google.com/books?id=lf2EMetoLugC&amp;dq=ISBN9780307450340&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 *			<link rel='http://schemas.google.com/books/2008/preview' type='text/html' href='http://books.google.com/books?id=lf2EMetoLugC&amp;dq=ISBN9780307450340&amp;ie=ISO-8859-1&amp;cd=1&amp;source=gbs_gdata'/>
 *			<link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml' href='http://www.google.com/books/feeds/users/me/volumes'/>
 *			<link rel='alternate' type='text/html' href='http://books.google.com/books?id=lf2EMetoLugC&amp;dq=ISBN9780307450340&amp;ie=ISO-8859-1'/>
 *			<link rel='self' type='application/atom+xml' href='http://www.google.com/books/feeds/volumes/lf2EMetoLugC'/>
 *			<gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 *			<gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *			<gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 *			<dc:creator>Garth Sundem</dc:creator>
 *			<dc:date>2009-03-10</dc:date>
 *			<dc:description>And here, for you pathetic nongeeks, is the last chance to save yourselves: Love thisbook, live this book, and you too can join us in the experience of total ...</dc:description>
 *			<dc:format>245 pages</dc:format>
 *			<dc:format>book</dc:format>
 *			<dc:identifier>lf2EMetoLugC</dc:identifier>
 *			<dc:identifier>ISBN:0307450341</dc:identifier>
 *			<dc:identifier>ISBN:9780307450340</dc:identifier>
 *			<dc:publisher>Three Rivers Pr</dc:publisher>
 *			<dc:subject>Humor</dc:subject>
 *			<dc:title>The Geeks' Guide to World Domination</dc:title>
 *			<dc:title>Be Afraid, Beautiful People</dc:title>
 *		</entry>
 * </feed>
 * 
 */
public class SearchGoogleBooksHandler extends DefaultHandler {
	private StringBuilder builder;
	public String[] id;
	public int count = 0;
	private int counter = 0;
	private boolean entry = false;
	
	public static String ID = "id";
	public static String TOTALRESULTS = "totalResults";
	public static String ENTRY = "entry";
	
	/**
	 * Return the ids of founded books
	 * 
	 * @return The books ids (to be passed to the entry handler)
	 */
	public String[] getId(){
		return id;
	}
	
	/**
	 * How many books were found?
	 * 
	 * @return The number of books found
	 */
	public int getCount(){
		return count;
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		super.characters(ch, start, length);
		builder.append(ch, start, length);
	}
	
	@Override
	public void endElement(String uri, String localName, String name) throws SAXException {
		super.endElement(uri, localName, name);
		if (localName.equalsIgnoreCase(TOTALRESULTS)){
			count = Integer.parseInt(builder.toString());
			if(count <= 10){
				id = new String[count];
			}else{
				id = new String[10];
			}			
		}
		if (localName.equalsIgnoreCase(ENTRY)){
			entry = false;
		}
		if (entry == true) {
			if (localName.equalsIgnoreCase(ID)){
				if(count > 0)
					id[counter++] = builder.toString();
			}
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
		if (localName.equalsIgnoreCase(ENTRY)){
			entry = true;
		}
	}
}
