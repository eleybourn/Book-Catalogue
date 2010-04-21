package com.eleybourn.bookcatalogue;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class BookISBNSearch extends Activity {

	private EditText mIsbnText;
    private CatalogueDBAdapter mDbHelper;
    
    private static String ID = "id";
    private static String TOTALRESULTS = "totalResults";
    private static String AUTHOR = "creator";
    private static String TITLE = "title";
    private static String ISBN = "identifier";
    private static String ENTRY = "entry";
    private static String DATE_PUBLISHED = "date";
    private static String PUBLISHER = "publisher";
    private static String PAGES = "format";
    private static String THUMBNAIL = "link";

    public String author;
    public String title;
    public String isbn;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
        	//ISBN has been passed by another component
        	isbn = extras.getString("isbn");
        	String[] book = search(isbn);
    		createBook(book);
            setResult(RESULT_OK);
            finish();
        } else {
	        mDbHelper = new CatalogueDBAdapter(this);
	        mDbHelper.open();
	        
	        setContentView(R.layout.isbn_search);
	       
	        mIsbnText = (EditText) findViewById(R.id.isbn);
	        Button confirmButton = (Button) findViewById(R.id.search);
	        confirmButton.setOnClickListener(new View.OnClickListener() {
	            public void onClick(View view) {
	                String mIsbn = mIsbnText.getText().toString();
	            	String[] book = search(mIsbn);
	        		createBook(book);
	                setResult(RESULT_OK);
	                finish();
	            }
	        });
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
    }
    
    private String[] search(String mIsbn) {
        String path = "http://books.google.com/books/feeds/volumes?q=ISBN";
        URL url;
    	//String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num};
    	String[] book = {"", "", mIsbn, "", "", "0",  "", "", "", "", ""};
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		GoogleBooksHandler handler = new GoogleBooksHandler();
		GoogleBooksEntryHandler entryHandler = new GoogleBooksEntryHandler();

		try {
			url = new URL(path+mIsbn);
			parser = factory.newSAXParser();
			int count = 0;
			try {
				parser.parse(getInputStream(url), handler);
				count = handler.getCount();
			} catch (RuntimeException e) {
	        	Toast.makeText(this, R.string.unable_to_connect, Toast.LENGTH_LONG).show();
			}
			if (count > 0) {
				String id = handler.getId();
				url = new URL(id);
				parser = factory.newSAXParser();
				try {
					parser.parse(getInputStream(url), entryHandler);
					book = entryHandler.getBook();
				} catch (RuntimeException e) {
		        	Toast.makeText(this, R.string.unable_to_connect, Toast.LENGTH_LONG).show();
				}
			} else {
            	Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_LONG).show();
			}
			return book;
		} catch (MalformedURLException e) {
			Log.e("Book Catalogue", "Malformed URL " + e.getMessage());
		} catch (ParserConfigurationException e) {
			Log.e("Book Catalogue", "SAX Parsing Error " + e.getMessage());
		} catch (SAXException e) {
			Log.e("Book Catalogue", "SAX Exception " + e.getMessage());
		} catch (IOException e) {
			Log.e("Book Catalogue", "SAX IO Exception " + e.getMessage());
		}
		return null;
    }
    

    protected InputStream getInputStream(URL url) {
        try {
            return url.openConnection().getInputStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
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
    public class GoogleBooksHandler extends DefaultHandler {
        private StringBuilder builder;
        public String id = "";
        public int count = 0;
        private boolean entry = false;
        
        public String getId(){
            return id;
        }
        
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
            }
            if (entry == true && id == "") {
	            if (localName.equalsIgnoreCase(ID)){
	            	id = builder.toString();
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
    
    public String properCase(String inputString) {
    	StringBuilder ff = new StringBuilder(); 
    	int wordnum = 0;

    	for(String f: inputString.split(" ")) {
			if(ff.length() > 0) { 
				ff.append(" "); 
			} 
    		wordnum++;
    		String word = f.toLowerCase();

    		if (word.substring(0,1).matches("[\"\\(\\./\\\\,]")) {
    			wordnum = 1;
    			ff.append(word.substring(0,1));
    			word = word.substring(1,word.length());
    		}

			/* Do not convert 1st char to uppercase in the following situations */
    		if (wordnum > 1 && word.matches("a|to|at|the|in|and|is|von")) {
    			ff.append(word);
    			continue;
    		} 
    		try {
	    		if (word.substring(0,2).equals("mc")) {
					ff.append(word.substring(0,1).toUpperCase());
					ff.append(word.substring(1,2));
					ff.append(word.substring(2,3).toUpperCase());
					ff.append(word.substring(3,word.length()));
					continue;
	    		}
    		} catch (StringIndexOutOfBoundsException e) {
    			// do nothing and continue;
    		}

    		try {
    			if (word.substring(0,3).equals("mac")) {
					ff.append(word.substring(0,1).toUpperCase());
					ff.append(word.substring(1,3));
					ff.append(word.substring(3,4).toUpperCase());
					ff.append(word.substring(4,word.length()));
					continue;
	    		}
    		} catch (StringIndexOutOfBoundsException e) {
    			// do nothing and continue;
    		}

    		try {
    			ff.append(word.substring(0,1).toUpperCase());
    			ff.append(word.substring(1,word.length()));
    		} catch (StringIndexOutOfBoundsException e) {
    			ff.append(word);
    		}
    	}

    	/* output */ 
    	String outputString = ff.toString();
    	return outputString;
    }

    public class GoogleBooksEntryHandler extends DefaultHandler {
        private StringBuilder builder;
        public String title = "";
        public String author = "";
        public String isbn = "";
        public String publisher = "";
        public String date_published = "";
        public String rating = "0";
        public String bookshelf = "";
        public String read = "false";
        public String series = "";
        public String pages = "0";
        public String thumbnail = "";
        public String series_num = "";
        
        public String[] getBook(){
        	String[] book = {author, title, isbn, publisher, date_published, rating,  bookshelf, read, series, pages, series_num};
            return book;
        }
        
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            builder.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            super.endElement(uri, localName, name);
            if (localName.equalsIgnoreCase(TITLE)){
            	if (title == "") {
            		title = properCase(builder.toString());
            	}
            } else if (localName.equalsIgnoreCase(ISBN)){
            	String tmp = builder.toString(); 
            	if (tmp.indexOf("ISBN:") == 0) {
            		tmp = tmp.substring(5); 
            		if (isbn == "" || tmp.length() > isbn.length()) {
            			isbn = tmp;
            		}
            	}
            } else if (localName.equalsIgnoreCase(AUTHOR)){
            	if (author == "") {
            		author = properCase(builder.toString());
            	}
            } else if (localName.equalsIgnoreCase(PUBLISHER)){
            	if (publisher == "") {
            		publisher = properCase(builder.toString());
            	}
            } else if (localName.equalsIgnoreCase(DATE_PUBLISHED)){
            	if (date_published == "") {
            		date_published = builder.toString();
            	}
            } else if (localName.equalsIgnoreCase(PAGES)){
            	String tmp = builder.toString();
            	int index = tmp.indexOf(" pages");
            	if (index > -1) {
            		tmp = tmp.substring(0, index).trim(); 
           			pages = tmp;
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
            if (localName.equalsIgnoreCase(THUMBNAIL)){
            	if (attributes.getValue("", "rel").equals("http://schemas.google.com/books/2008/thumbnail")) {
            		thumbnail = attributes.getValue("", "href");

            	    URL u;
					try {
						u = new URL(thumbnail);
					} catch (MalformedURLException e) {
						Log.e("Book Catalogue", "Malformed URL");
						return;
					}
            	    HttpURLConnection c;
            	    InputStream in = null;
					try {
						c = (HttpURLConnection) u.openConnection();
	            	    c.setRequestMethod("GET");
	            	    c.setDoOutput(true);
	            	    c.connect();
	            	    in = c.getInputStream();
					} catch (IOException e) {
						Log.e("Book Catalogue", "Thumbnail cannot be read");
						return;
					}
					
            	    FileOutputStream f = null;
					try {
						f = new FileOutputStream(Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/tmp.jpg");
					} catch (FileNotFoundException e) {
						Log.e("Book Catalogue", "Thumbnail cannot be written");
						return;
					}

            	    try {
                	    byte[] buffer = new byte[1024];
                	    int len1 = 0;
						while ( (len1 = in.read(buffer)) > 0 ) {
						     f.write(buffer,0, len1);
						}
	            	    f.close();
					} catch (IOException e) {
						Log.e("Book Catalogue", "Error writing thumbnail");
						return;
					}
            	}
            }
        }
    }
	
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void createBook(String[] book) {
        Intent i = new Intent(this, BookEdit.class);
        i.putExtra("book", book);
        startActivity(i);
        
    }

}
