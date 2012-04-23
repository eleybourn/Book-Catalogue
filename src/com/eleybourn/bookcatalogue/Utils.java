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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.eleybourn.bookcatalogue.database.CoversDbHelper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.Toast;

public class Utils {
	// External DB for cover thumbnails
	private boolean mCoversDbCreateFail = false;
	/** Database is non-static member so we don't make it linger longer than necessary */
	private CoversDbHelper mCoversDb = null;

	// Used for formatting dates for sql; everything is assumed to be UTC, or converted to UTC since 
	// UTC is the default SQLite TZ. 
	static TimeZone tzUtc = TimeZone.getTimeZone("UTC");

	// Used for date parsing and display
	private static SimpleDateFormat mDateFullHMSSqlSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static { mDateFullHMSSqlSdf.setTimeZone(tzUtc); }
	private static SimpleDateFormat mDateFullHMSqlSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	static { mDateFullHMSqlSdf.setTimeZone(tzUtc); }
	private static SimpleDateFormat mDateSqlSdf = new SimpleDateFormat("yyyy-MM-dd");
	static { mDateSqlSdf.setTimeZone(tzUtc); }

	private static SimpleDateFormat mDate1HMSSdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
	private static SimpleDateFormat mDate1HMSdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
	private static SimpleDateFormat mDate1Sdf = new SimpleDateFormat("dd-MMM-yyyy");
	private static SimpleDateFormat mDate2HMSSdf = new SimpleDateFormat("dd-MMM-yy HH:mm:ss");
	private static SimpleDateFormat mDate2HMSdf = new SimpleDateFormat("dd-MMM-yy HH:mm");
	private static SimpleDateFormat mDate2Sdf = new SimpleDateFormat("dd-MMM-yy");
	private static SimpleDateFormat mDateUSHMSSdf = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
	private static SimpleDateFormat mDateUSHMSdf = new SimpleDateFormat("MM-dd-yyyy HH:mm");
	private static SimpleDateFormat mDateUSSdf = new SimpleDateFormat("MM-dd-yyyy");
	private static SimpleDateFormat mDateEngHMSSdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
	private static SimpleDateFormat mDateEngHMSdf = new SimpleDateFormat("dd-MM-yyyy HH:mm");
	private static SimpleDateFormat mDateEngSdf = new SimpleDateFormat("dd-MM-yyyy");
	private static DateFormat mDateDispSdf = DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
	//private static DateFormat mDateTimeDispSdf = DateFormat.getDateInstance(java.text.DateFormat.FULL);
	// Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
	private static final SimpleDateFormat mLongUnixHMSSdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy");
	private static final SimpleDateFormat mLongUnixHMSdf = new SimpleDateFormat("EEE MMM dd HH:mm ZZZZ yyyy");
	private static final SimpleDateFormat mLongUnixSdf = new SimpleDateFormat("EEE MMM dd ZZZZ yyyy");

	/** List of all formats, keep the ones with timezone info near the start */
	private static final SimpleDateFormat[] mParseDateFormats = new SimpleDateFormat[] {
			mLongUnixHMSSdf,
			mLongUnixHMSdf,
			mLongUnixSdf,
			mDateFullHMSSqlSdf,
			mDateFullHMSqlSdf,
			mDateSqlSdf,
			mDate1HMSSdf,
			mDate1HMSdf,
			mDate1Sdf,
			mDate2HMSSdf,
			mDate2HMSdf,
			mDate2Sdf,
			mDateUSSdf,
			mDateEngHMSSdf,
			mDateEngHMSdf,
			mDateEngSdf,
			};

	public static final String APP_NAME = "Book Catalogue";
	public static final boolean USE_LT = true;
	public static final boolean USE_BARCODE = true;
	//public static final String APP_NAME = "DVD Catalogue";
	//public static final String LOCATION = "dvdCatalogue";
	//public static final String DATABASE_NAME = "dvd_catalogue";
	//public static final boolean USE_LT = false;
	//public static final boolean USE_BARCODE = false;
	//public static final String APP_NAME = "CD Catalogue";
	//public static final String LOCATION = "cdCatalogue";
	//public static final String DATABASE_NAME = "cd_catalogue";
	//public static final boolean USE_LT = true;
	//public static final boolean USE_BARCODE = false;

	public static String toSqlDateOnly(Date d) {
		return mDateSqlSdf.format(d);
	}
	public static String toSqlDateTime(Date d) {
		return mDateFullHMSSqlSdf.format(d);
	}
	public static String toPrettyDate(Date d) {
		return mDateDispSdf.format(d);		
	}
	public static String toPrettyDateTime(Date d) {
		return DateFormat.getDateTimeInstance().format(d);		
	}

	public static Date parseDate(String s) {
		Date d;
		for ( SimpleDateFormat sdf : mParseDateFormats ) {
			try {
				// Parse as SQL/ANSI date
				d = sdf.parse(s);
				return d;
			} catch (Exception e) {
				// Ignore 
			}			
		}
		// All SDFs failed, try one more...
		try {
			java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
			d = df.parse(s);
			return d;
		} catch (Exception e) {
			// Ignore 
		}			
		return null;
	}

	private static ArrayUtils<Author> mAuthorUtils = null;
	private static ArrayUtils<Series> mSeriesUtils = null;

	static public ArrayUtils<Author> getAuthorUtils() {
		if (mAuthorUtils == null) {
			mAuthorUtils = new ArrayUtils<Author>(new Utils.Factory<Author>(){
				@Override
				public Author get(String source) {
					return new Author(source);
				}});			
		}
		return mAuthorUtils;
	}

	static public ArrayUtils<Series> getSeriesUtils() {
		if (mSeriesUtils == null) {
			mSeriesUtils = new ArrayUtils<Series>(new Utils.Factory<Series>(){
				@Override
				public Series get(String source) {
					return new Series(source);
				}});
		}
		return mSeriesUtils;
	}

	/**
	 * Encode a string by 'escaping' all instances of: '|', '\', \r, \n. The
	 * escape char is '\'.
	 * 
	 * This is used to build text lists separated by the passed delimiter.
	 * 
	 * @param s			String to convert
	 * @param delim		The list delimiter to encode (if found).
	 * 
	 * @return		Converted string
	 */
	public static String encodeListItem(String s, char delim) {
		StringBuilder ns = new StringBuilder();
		for (int i = 0; i < s.length(); i++){
		    char c = s.charAt(i);        
		    switch (c) {
		    case '\\':
		    	ns.append("\\\\");
		    	break;
		    case '\r':
		    	ns.append("\\r");
		    	break;
		    case '\n':
		    	ns.append("\\n");
		    	break;
		    default:
		    	if (c == delim)
		    		ns.append("\\");
		    	ns.append(c);
		    }
		}
		return ns.toString();
	}

	/**
	 * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n. The
	 * escape char is '\'.
	 * 
	 * This is used to build text lists separated by 'delim'.
	 * 
	 * @param s		String to convert
	 * @return		Converted string
	 */
	static String encodeList(ArrayList<String> sa, char delim) {
		StringBuilder ns = new StringBuilder();
		Iterator<String> si = sa.iterator();
		if (si.hasNext()) {
			ns.append(encodeListItem(si.next(), delim));
			while (si.hasNext()) {
				ns.append(delim);
				ns.append(encodeListItem(si.next(), delim));
			}
		}
		return ns.toString();
	}

	public interface Factory<T> {
		T get(String source);
	}

	static public class ArrayUtils<T> {

		Factory<T> mFactory;

		ArrayUtils(Factory<T> factory) {
			mFactory = factory;
		}

		private T get(String source) {
			return mFactory.get(source);
		}
		/**
		 * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n. The
		 * escape char is '\'.
		 * 
		 * This is used to build text lists separated by 'delim'.
		 * 
		 * @param s		String to convert
		 * @return		Converted string
		 */
		public String encodeList(ArrayList<T> sa, char delim) {
			Iterator<T> si = sa.iterator();
			return encodeList(si, delim);
		}

		private String encodeList(Iterator<T> si, char delim) {
			StringBuilder ns = new StringBuilder();
			if (si.hasNext()) {
				ns.append(encodeListItem(si.next().toString(), delim));
				while (si.hasNext()) {
					ns.append(delim);
					ns.append(encodeListItem(si.next().toString(), delim));
				}
			}
			return ns.toString();
		}
		
		/**
		 * Decode a text list separated by '|' and encoded by encodeListItem.
		 * 
		 * @param s		String representing the list
		 * @return		Array of strings resulting from list
		 */
		ArrayList<T> decodeList(String s, char delim, boolean allowBlank) {
			StringBuilder ns = new StringBuilder();
			ArrayList<T> list = new ArrayList<T>();
			if (s == null)
				return list;

			boolean inEsc = false;
			for (int i = 0; i < s.length(); i++){
			    char c = s.charAt(i);
			    if (inEsc) {
			    	switch(c) {
				    case '\\':
			    		ns.append(c);
				    	break;		    	
				    case 'r':
			    		ns.append('\r');
				    	break;		    	
				    case 't':
			    		ns.append('\t');
				    	break;		    	
				    case 'n':
			    		ns.append('\n');
				    	break;		    	
				    default:
				    	ns.append(c);
				    	break;
			    	}
		    		inEsc = false;
			    } else {
				    switch (c) {
				    case '\\':
			    		inEsc = true;
				    	break;
				    default:
				    	if (c == delim) {
				    		String source = ns.toString();
				    		if (allowBlank || source.length() > 0)
						    	list.add(get(source));
					    	ns.setLength(0);
					    	break;
				    	} else {
					    	ns.append(c);
					    	break;
				    	}
				    }
			    }
			}
			// It's important to send back even an empty item.
    		String source = ns.toString();
    		if (allowBlank || source.length() > 0)
		    	list.add(get(source));
			return list;
		}
	}

	/**
	 * Decode a text list separated by '|' and encoded by encodeListItem.
	 * 
	 * @param s		String representing the list
	 * @return		Array of strings resulting from list
	 */
	public static ArrayList<String> decodeList(String s, char delim) {
		StringBuilder ns = new StringBuilder();
		ArrayList<String> list = new java.util.ArrayList<String>();
		boolean inEsc = false;
		for (int i = 0; i < s.length(); i++){
		    char c = s.charAt(i);
		    if (inEsc) {
		    	switch(c) {
			    case '\\':
		    		ns.append(c);
			    	break;		    	
			    case 'r':
		    		ns.append('\r');
			    	break;		    	
			    case 't':
		    		ns.append('\t');
			    	break;		    	
			    case 'n':
		    		ns.append('\n');
			    	break;		    	
			    default:
			    	ns.append(c);
			    	break;
		    	}
	    		inEsc = false;
		    } else {
			    switch (c) {
			    case '\\':
		    		inEsc = true;
			    	break;
			    default:
			    	if (c == delim) {
				    	list.add(ns.toString());
				    	ns.setLength(0);
				    	break;
			    	} else {
				    	ns.append(c);
				    	break;
			    	}
			    }
		    }
		}
		// It's important to send back even an empty item.
    	list.add(ns.toString());
		return list;
	}

	/**
	 * Add the current text data to the collection if not present, otherwise 
	 * append the data as a list.
	 * 
	 * @param key	Key for data to add
	 */
	static public void appendOrAdd(Bundle values, String key, String value) {
		String s = Utils.encodeListItem(value, '|');
		if (!values.containsKey(key) || values.getString(key).length() == 0) {
			values.putString(key, s);
		} else {
			String curr = values.getString(key);
			values.putString(key, curr + "|" + s);
		}
	}
	
	/**
	 * Append a flag indicating how to display the search results. 
	 * 
	 * @param 	value	Text without flag
	 * 
	 * @return	Text with appended flag	
	 */	
	static public String appendListFlag(String value) {
		return value + "|" + CatalogueDBAdapter.SHOW_SEARCH_RESULTS_IN_LIST;
	}		
	
	/**
	 * Remove from given text a flag indicating how to display the search results. 
	 * 
	 * @param 	value	Text with flag
	 * 
	 * @return	Text without flag	
	 */		
	static public String removeListFlag(String value) {
		if(value.contains("|"))
			return value.substring(0, value.lastIndexOf("|"));
		return value;
	}
	
	/**
	 * Return value of a flag indicating how to display the search results. 
	 * 
	 * @param 	value	Text with flag
	 * 
	 * @return	True, if search results should be shown in list, otherwise return false
	 */		
	static public boolean getListFlag(String value) {
		if(value.contains("|")){
			if(value.substring(value.lastIndexOf("|")+1, value.length()).equals(CatalogueDBAdapter.SHOW_SEARCH_RESULTS_IN_LIST))
				return true;
		}
		return false;
	}	

	/**
	 * Given a URL, get an image and save to a file, optionally appending a suffic to the file.
	 * 
	 * @param urlText			Image file URL
	 * @param filenameSuffix	Suffix to add
	 *
	 * @return	Downloaded filespec
	 */
	static public String saveThumbnailFromUrl(String urlText, String filenameSuffix) {
		URL u;
		try {
			u = new URL(urlText);
		} catch (MalformedURLException e) {
			Logger.logError(e);
			return "";
		}
		HttpURLConnection c;
		InputStream in = null;
		try {
            HttpGet httpRequest = null;

			httpRequest = new HttpGet(u.toURI());

            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response = (HttpResponse) httpclient.execute(httpRequest);

            HttpEntity entity = response.getEntity();
            BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
            in = bufHttpEntity.getContent();

            // The defaut URL fetcher does not cope well with pages that have not content
            // header (including goodreads images!). So use the more advanced one.
			//c = (HttpURLConnection) u.openConnection();
			//c.setConnectTimeout(30000);
			//c.setRequestMethod("GET");
			//c.setDoOutput(true);
			//c.connect();
			//in = c.getInputStream();
		} catch (IOException e) {
			Logger.logError(e);
			return "";
		} catch (URISyntaxException e) {
			Logger.logError(e);
			return "";
		}

		String filename = "";
		FileOutputStream f = null;
		try {
			File file = CatalogueDBAdapter.getTempThumbnail(filenameSuffix);
			filename = file.getAbsolutePath();
			f = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			Logger.logError(e);
			return "";
		}
		
		try {
			byte[] buffer = new byte[1024];
			int len1 = 0;
			while ( (len1 = in.read(buffer)) > 0 ) {
				f.write(buffer,0, len1);
			}
			f.close();
		} catch (IOException e) {
			Logger.logError(e);
			return "";
		}
		return filename;
	}

	/**
	 * Given a URL, get an image and return as a bitmap.
	 * 
	 * @param urlText			Image file URL
	 *
	 * @return	Downloaded bitmap
	 */
	static public Bitmap getBitmapFromUrl(String urlText) {
		return getBitmapFromBytes( getBytesFromUrl(urlText) );
	}

	/**
	 * Given byte array that represents an image (jpg, png etc), return as a bitmap.
	 * 
	 * @param bytes			Raw byte data
	 *
	 * @return	bitmap
	 */
	static public Bitmap getBitmapFromBytes(byte[] bytes) {
		if (bytes == null || bytes.length == 0)
			return null;

		BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,options);
        String s = "Array " + bytes.length + " bytes, bitmap " + bitmap.getHeight() + "x" + bitmap.getWidth();
        System.out.println(s);
        return bitmap;
	}

	/**
	 * Given a URL, get an image and return as a byte array.
	 * 
	 * @param urlText			Image file URL
	 *
	 * @return	Downloaded byte[]
	 */
	static public byte[] getBytesFromUrl(String urlText) {
		// Get the URL
		URL u;
		try {
			u = new URL(urlText);
		} catch (MalformedURLException e) {
			Logger.logError(e);
			return null;
		}
		// Request it from the network
		HttpURLConnection c;
		InputStream in = null;
		try {
			c = (HttpURLConnection) u.openConnection();
			c.setConnectTimeout(30000);
			c.setRequestMethod("GET");
			c.setDoOutput(true);
			c.connect();
			in = c.getInputStream();
		} catch (IOException e) {
			Logger.logError(e);
			return null;
		}

		// Save the output to a byte output stream
		ByteArrayOutputStream f = new ByteArrayOutputStream();
		try {
			byte[] buffer = new byte[1024];
			int len1 = 0;
			while ( (len1 = in.read(buffer)) > 0 ) {
				f.write(buffer,0, len1);
			}
			f.close();
		} catch (IOException e) {
			Logger.logError(e);
			return null;
		}
		// Return it as a byte[]
		return f.toByteArray();
	}

	/**
	 * Utility routine to get the data from a URL. Makes sure timeout is set to avoid application
	 * stalling.
	 * 
	 * @param url		URL to retrieve
	 * @return
	 * @throws UnknownHostException 
	 */
	static public InputStream getInputStream(URL url) throws UnknownHostException {
		
		synchronized (url) {
			
			int retries = 3;
			while (true) {
				try {
					java.net.URLConnection conn = url.openConnection();
					conn.setConnectTimeout(30000);
					return conn.getInputStream();
				} catch (java.net.UnknownHostException e) {
					Logger.logError(e);
					retries--;
					if (retries-- == 0)
						throw e;
					try { Thread.sleep(500); } catch(Exception junk) {};
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException(e);
				}			
			}
		}
	}
	
	/*
	 *@return boolean return true if the application can access the internet
	 */
	public static boolean isNetworkAvailable(Context context) {
		ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity != null) {
			NetworkInfo[] info = connectivity.getAllNetworkInfo();
			if (info != null) {
				for (int i = 0; i < info.length; i++) {
					if (info[i].getState() == NetworkInfo.State.CONNECTED) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * If there is a '__thumbnails' key, pick the largest image, rename it
	 * and delete the others. Finally, remove the key.
	 * 
	 * @param result	Book data
	 */
	static public void cleanupThumbnails(Bundle result) {
    	if (result.containsKey("__thumbnail")) {
    		long best = -1;
    		int bestFile = -1;

    		// Parse the list
    		ArrayList<String> files = Utils.decodeList(result.getString("__thumbnail"), '|');

    		// Just read the image files to get file size
    		BitmapFactory.Options opt = new BitmapFactory.Options();
    		opt.inJustDecodeBounds = true;

    		// Scan, finding biggest
    		for(int i = 0; i < files.size(); i++) {
    			String filespec = files.get(i);
	    		File file = new File(filespec);
	    		if (file.exists()) {
		    	    BitmapFactory.decodeFile( filespec, opt );
		    	    // If no size info, assume file bad and skip
		    	    if ( opt.outHeight > 0 && opt.outWidth > 0 ) {
		    	    	long size = opt.outHeight * opt.outWidth;
		    	    	if (size > best) {
		    	    		best = size;
		    	    		bestFile = i;
		    	    	}
		    	    }	    		
	    		}
    		}

    		// Delete all but the best one. Note there *may* be no best one,
    		// so all would be deleted. We do this first in case the list 
    		// contains a file with the same name as the target of our
    		// rename.
    		for(int i = 0; i < files.size(); i++) {
    			if (i != bestFile) {
		    		File file = new File(files.get(i));
		    		file.delete();
    			}
    		}
    		// Get the best file (if present) and rename it.
			if (bestFile >= 0) {
	    		File file = new File(files.get(bestFile));
	    		file.renameTo(CatalogueDBAdapter.getTempThumbnail());
			}
    		// Finally, cleanup the data
    		result.remove("__thumbnail");
    		result.putBoolean(CatalogueDBAdapter.KEY_THUMBNAIL, true);
    	}
	}
	
	/**
	 * Make temp thumbnail from file from given path.
	 * 
	 * @param path	Path to thumbnail
	 */	
	static public void makeTempThumbnail(String path) {		
		File file = new File(path);   			
		File f = new File(path+"_copy");	    			
		copyFile(file, f);	    			
		f.renameTo(CatalogueDBAdapter.getTempThumbnail());
	}	
	
	/**
	 * Delete actual temp thumbnail.
	 * 
	 */		
    static public void deleteTempThumbnail(){
    	File f = CatalogueDBAdapter.getTempThumbnail();
    	f.delete();
    }
    
	/**
	 * Delete given thumbnail.
	 * 
	 * @param path	Path to thumbnail
	 */		
    static public void deleteThumbnail(String path){
    	File f = new File(path);
    	if(f.exists())
    		f.delete();
    }    
	
	/**
	 * Make copy of source file.
	 * 
	 * @param f1	Source
	 * @param f2	Target
	 */	    
	private static void copyFile(File f1, File f2){
		try{			
			InputStream in = new FileInputStream(f1);
			OutputStream out = new FileOutputStream(f2);	
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0){
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
		}
		catch(Exception ex){
			//nothing
		}
	}	

	/**
	 * Convert text at specified key to proper case.
	 * 
	 * @param values
	 * @param key
	 */
	public static void doProperCase(Bundle values, String key) {
		if (!values.containsKey(key))
			return;
		values.putString(key, properCase(values.getString(key)));
	}

	public static String properCase(String inputString) {
		StringBuilder ff = new StringBuilder(); 
		String outputString;
		int wordnum = 0;

		try {
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
				if (wordnum > 1 && word.matches("a|to|at|the|in|and|is|von|de|le")) {
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
			outputString = ff.toString();
		} catch (StringIndexOutOfBoundsException e) {
			//empty string - do nothing
			outputString = inputString;
		}
		return outputString;
	}

	/**
	 * Check if passed bundle contains a non-blank string at key k.
	 * 
	 * @param b			Bundle to check
	 * @param key		Key to check for
	 * @return			Present/absent
	 */
	public static boolean isNonBlankString(Bundle b, String key) {
		try {
			if (b.containsKey(key)) {
				String s = b.getString(key);
				return (s != null && s.length() > 0);
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}
	/**
	 * Join the passed array of strings, with 'delim' between them.
	 * 
	 * @param sa		Array of strings to join
	 * @param delim		Delimiter to place between entries
	 * 
	 * @return			The joined strings
	 */
	static String join(String[] sa, String delim) {
		// Simple case, return empty string
		if (sa.length <= 0)
			return "";

		// Initialize with first
		StringBuilder buf = new StringBuilder(sa[0]);

		if (sa.length > 1) {
			// If more than one, loop appending delim then string.
			for(int i = 1; i < sa.length; i++) {
				buf.append(delim);
				buf.append(sa[i]);
			}
		}
		// Return result
		return buf.toString();
	}

	/**
	 * Get a value from a bundle and convert to a long.
	 * 
	 * @param b		Bundle
	 * @param key	Key in bundle
	 * 
	 * @return		Result
	 */
	public static long getAsLong(Bundle b, String key) {
		Object o = b.get(key);
		if (o instanceof Long) {
			return (Long) o;
		} else if (o instanceof String) {
			return Long.parseLong((String)o);
		} else if (o instanceof Integer) {
			return ((Integer)o).longValue();
		} else {
			throw new RuntimeException("Not a long value");
		}
	}

	/**
	 * Get a value from a bundle and convert to a long.
	 * 
	 * @param b		Bundle
	 * @param key	Key in bundle
	 * 
	 * @return		Result
	 */
	public static String getAsString(Bundle b, String key) {
		Object o = b.get(key);
		return o.toString();
	}

	public interface ItemWithIdFixup {
		long fixupId(CatalogueDBAdapter db);
		long getId();
		boolean isUniqueById();
	}

	/**
	 * Passed a list of Objects, remove duplicates based on the toString result.
	 * 
	 * ENHANCE Add author_aliases table to allow further pruning (eg. Joe Haldeman == Jow W Haldeman).
	 * ENHANCE Add series_aliases table to allow further pruning (eg. 'Amber Series' <==> 'Amber').
	 * 
	 * @param db		Database connection to lookup IDs
	 * @param list		List to clean up
	 */
	public static <T extends ItemWithIdFixup> void pruneList(CatalogueDBAdapter db, ArrayList<T> list) {
		Hashtable<String,Boolean> names = new Hashtable<String,Boolean>();
		Hashtable<Long,Boolean> ids = new Hashtable<Long,Boolean>();

		// We have to go forwards through the list because 'first item' is important,
		// but we also can't delete things as we traverse if we are going forward. So
		// we build a list of items to delete.
		ArrayList<Integer> toDelete = new ArrayList<Integer>();

		for(int i = 0; i < list.size(); i++) {
			T item = list.get(i);
			Long id = item.fixupId(db);
			String name = item.toString().trim().toUpperCase();
			
			// Series special case - same name different series number.
			// This means different series positions will have the same ID but will have
			// different names; so ItemWithIdFixup contains the 'isUniqueById()' method.
			if (ids.containsKey(id) && !names.containsKey(name) && !item.isUniqueById()) {
				ids.put(id, true);
				names.put(name, true);
			} else if (names.containsKey(name) || (id != 0 && ids.containsKey(id))) {
				toDelete.add(i);
			} else {
				ids.put(id, true);
				names.put(name, true);
			}
		}
		for(int i = toDelete.size() - 1; i >= 0; i--)
			list.remove(toDelete.get(i).intValue());
	}

	/**
	 * Remove series from the list where the names are the same, but one entry has a null or empty position.
	 * eg. the followig list should be processed as indicated:
	 * 
	 * fred(5)
	 * fred <-- delete
	 * bill <-- delete
	 * bill <-- delete
	 * bill(1)
	 * 
	 * @param list
	 */
	public static void pruneSeriesList(ArrayList<Series> list) {
		ArrayList<Series> toDelete = new ArrayList<Series>();
		Hashtable<String, Series> index = new Hashtable<String, Series> ();

		for(Series s: list) {
			final boolean emptyNum = s.num == null || s.num.trim().equals("");
			final String lcName = s.name.trim().toLowerCase();
			final boolean inNames = index.containsKey(lcName);
			if (!inNames) {
				// Just add and continue
				index.put(lcName, s);
			} else {
				// See if we can purge either
				if (emptyNum) {
					// Always delete series with empty numbers if an equally or more specific one exists
					toDelete.add(s);
				} else {
					// See if the one in 'index' also has a num
					Series orig = index.get(lcName);
					if (orig.num == null || orig.num.trim().equals("")) {
						// Replace with this one, and mark orig for delete
						index.put(lcName, s);
						toDelete.add(orig);
					} else {
						// Both have numbers. See if they are the same.
						if (s.num.trim().toLowerCase().equals(orig.num.trim().toLowerCase())) {
							// Same exact series, delete this one
							toDelete.add(s);
						} else {
							// Nothing to do: this is a different series position							
						}
					}
				}
			}
		}
		
		for (Series s: toDelete) 
			list.remove(s);

	}
	/**
	 * Convert a array of objects to a string.
	 * 
	 * @param <T>
	 * @param a		Array
	 * @return		Resulting string
	 */
	public static <T> String ArrayToString(ArrayList<T> a) {
		String details = "";

		for (T i : a) {
			if (details.length() > 0)
				details += "|";
			details += Utils.encodeListItem(i.toString(), '|');			
		}
		return details;
	}
	
	// TODO: Make sure all URL getters use this if possible.
	static public void parseUrlOutput(String path, SAXParserFactory factory, DefaultHandler handler) {
		SAXParser parser;
		URL url;

		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			parser.parse(Utils.getInputStream(url), handler);
			// Dont bother catching general exceptions, they will be caught by the caller.
		} catch (MalformedURLException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (ParserConfigurationException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (SAXException e) {
			String s = e.getMessage(); // "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (java.io.IOException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		}
	}

	/**
	 * Shrinks the image in the passed file to the specified dimensions, and places the image
	 * in the passed view. The bitmap is returned.
	 * 
	 * @param file
	 * @param destView
	 * @param maxWidth
	 * @param maxHeight
	 * @param exact
	 * 
	 * @return
	 */
	public static Bitmap fetchFileIntoImageView(File file, ImageView destView, int maxWidth, int maxHeight, boolean exact) {

		Bitmap bm = null;					// resultant Bitmap (which we will return) 

		// Get the file, if it exists. Otherwise set 'help' icon and exit.
		if (!file.exists()) {
			if (destView != null)
				destView.setImageResource(android.R.drawable.ic_menu_help);
			return null;
		}

		bm = shrinkFileIntoImageView(destView, file.getPath(), maxWidth, maxHeight, exact);

		return bm;
	}

	/**
	 * Construct the cache ID for a given thumbnail spec.
	 * 
	 * NOTE: Any changes to the resulting name MUST be reflect in CoversDbHelper.eraseCachedBookCover()
	 * 
	 * @param hash
	 * @param maxWidth
	 * @param maxHeight
	 * @return
	 */
	public static final String getCoverCacheId(final String hash, final int maxWidth, final int maxHeight) {
		// NOTE: Any changes to the resulting name MUST be reflect in CoversDbHelper.eraseCachedBookCover()
		return hash + ".thumb." + maxWidth + "x" + maxHeight + ".jpg";
	}

	/**
	 * Called in the UI thread, will return a cached image OR NULL.
	 * 
	 * @param originalFile	File representing original image file
	 * @param destView		View to populate
	 * @param cacheId		ID of the image in the cache
	 * 
	 * @return				Bitmap (if cached) or NULL (if not cached)
	 */
	public Bitmap fetchCachedImageIntoImageView(final File originalFile, final ImageView destView, final String cacheId) {
		Bitmap bm = null;					// resultant Bitmap (which we will return) 

		// Get the db
		CoversDbHelper coversDb = getCoversDb();
		if (coversDb != null) {
			byte[] bytes;
			// Wrap in try/catch. It's possible the SDCard got removed and DB is now inaccessible
			Date expiry;
			if (originalFile == null)
				expiry = new Date(0L);
			else
				expiry = new Date(originalFile.lastModified());

			try { bytes = coversDb.getFile(cacheId, expiry); } 
				catch (Exception e) {
					bytes = null;
				};
			if (bytes != null) {
				try {
					bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				} catch (Exception e) {
					bytes = null;
				};
			}
		}

		if (bm != null) {
			//
			// Remove any tasks that may be getting the image because they may overwrite anything we do.
			// Remember: the view may have been re-purposed and have a different associated task which
			// must be removed from the view and removed from the queue.
			//
			if (destView != null)
				GetThumbnailTask.clearOldTaskFromView( destView );

			// We found it in cache
			if (destView != null)
				destView.setImageBitmap(bm);
			// Return the image
		}
		return bm;
	}

	/**
	 * Called in the UI thread, will either use a cached cover OR start a background task to create and load it.
	 * 
	 * If a cached image is used a background task is still started to check the file date vs the cache date. If the
	 * cached image date is < the file, it is rebuilt.
	 * 
	 * @param destView			View to populate
	 * @param maxWidth			Max width of resulting image
	 * @param maxHeight			Max height of resulting image
	 * @param exact				Whether to fit dimensions exactly
	 * @param bookId			ID of book to retrieve.
	 * @param checkCache		Indicates if cache should be checked for this cover
	 * @param allowBackground	Indicates if request can be put in background task.
	 * 
	 * @return				Bitmap (if cached) or NULL (if done in background)
	 */
	public final Bitmap fetchBookCoverIntoImageView(final ImageView destView, int maxWidth, int maxHeight, final boolean exact, final String hash, final boolean checkCache, final boolean allowBackground) {

		// Get the original file so we can use the modification date, path etc
		File coverFile = CatalogueDBAdapter.fetchThumbnailByUuid(hash);

		Bitmap bm = null;
		boolean cacheWasChecked = false;

		// If we want to check the cache, AND we dont have cache building happening, then check it.
		if (checkCache && !GetThumbnailTask.hasActiveTasks() && !ThumbnailCacheWriterTask.hasActiveTasks()) {
			final String cacheId = getCoverCacheId(hash, maxWidth, maxHeight);
			bm = fetchCachedImageIntoImageView(coverFile, destView, cacheId);
			cacheWasChecked = true;
		} else {
			//System.out.println("Skipping cache check");
		}

		if (bm != null)
			return bm;

		// Check the file exists. Otherwise set 'help' icon and exit.
		//if (!coverFile.exists()) {
		//	if (destView != null)
		//		destView.setImageResource(android.R.drawable.ic_menu_help);
		//	return null;
		//}

		// If we get here, the image is not in the cache but the original exists. See if we can queue it.
		if (allowBackground) {
			destView.setImageBitmap(null);
			GetThumbnailTask.getThumbnail(hash, destView, maxWidth, maxHeight, cacheWasChecked);
			return null;
		}

		//File coverFile = CatalogueDBAdapter.fetchThumbnail(bookId);
		
		// File is not in cache, original exists, we are in the background task (or not allowed to queue request)
		return shrinkFileIntoImageView(destView, coverFile.getPath(), maxWidth, maxHeight, exact);

	}
	
	/**
	 * Rename file from given path - use given suffix.
	 * 
	 * @param file		Original file
	 * @param suffix	Suffix to rename
	 * 
	 * @return			New name of file.
	 */	
	static public String renameFile(String file, String suffix){		
		ArrayList<String> files = Utils.decodeList(file, '|');		
		File f = new File(files.get(files.size() - 1));
		f.renameTo(new File(StorageUtils.getSharedStoragePath() + "/tmp" + suffix + ".jpg"));		
		return StorageUtils.getSharedStoragePath() + "/tmp" + suffix + ".jpg";		
	}	

	/**
	 * Shrinks the passed image file spec into the specificed dimensions, and returns the bitmap. If the view 
	 * is non-null, the image is also placed in the view.
	 * 
	 * @param destView
	 * @param filename
	 * @param maxWidth
	 * @param maxHeight
	 * @param exact
	 * 
	 * @return
	 */
	private static Bitmap shrinkFileIntoImageView(ImageView destView, String filename, int maxWidth, int maxHeight, boolean exact) {
		Bitmap bm = null;

		// Read the file to get file size
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inJustDecodeBounds = true;
		BitmapFactory.decodeFile( filename, opt );

		// If no size info, or a single pixel, assume file bad and set the 'alert' icon
		if ( opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight== 1 && opt.outWidth == 1) ) {
			if (destView != null)
				destView.setImageResource(android.R.drawable.ic_dialog_alert);
			return null;
		}

		// Next time we don't just want the bounds, we want the file
		opt.inJustDecodeBounds = false;
		
		// Work out how to scale the file to fit in required bbox
		float widthRatio = (float)maxWidth / opt.outWidth; 
		float heightRatio = (float)maxHeight / opt.outHeight;
		
		// Work out scale so that it fit exactly
		float ratio = widthRatio < heightRatio ? widthRatio : heightRatio;
		
		// Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
		// specify, so we just work with powers of 2.
		int idealSampleSize = (int)Math.ceil(1/ratio); // This is the sample size we want to use
		// Get the nearest *bigger* power of 2.
		int samplePow2 = (int)Math.ceil(Math.log(idealSampleSize)/Math.log(2));
		
		try {
			if (exact) {
				// Create one bigger than needed and scale it; this is an attempt to improve quality.
				opt.inSampleSize = samplePow2 / 2;
				if (opt.inSampleSize < 1)
					opt.inSampleSize = 1;
				Bitmap tmpBm = BitmapFactory.decodeFile( filename, opt );
				android.graphics.Matrix matrix = new android.graphics.Matrix();
				// Fixup ratio based on new sample size and scale it.
				ratio = ratio / (1.0f / opt.inSampleSize);
				matrix.postScale(ratio, ratio);
				bm = Bitmap.createBitmap(tmpBm, 0, 0, opt.outWidth, opt.outHeight, matrix, true);
				// Recycle if original was not returned
				if (bm != tmpBm) {
					tmpBm.recycle();
					tmpBm = null;
				}
			} else {
				// Use a scale that will make image *no larger than* the desired size
				if (ratio < 1.0f)
					opt.inSampleSize = samplePow2;
				bm = BitmapFactory.decodeFile( filename, opt );
			}
		} catch (OutOfMemoryError e) {
			return null;
		}

		// Set ImageView and return bitmap
		if (destView != null)
			destView.setImageBitmap(bm);

		return bm;		
	}

	public static void showLtAlertIfNecessary(Context context, boolean always, String suffix) {
		if (USE_LT) {
			LibraryThingManager ltm = new LibraryThingManager(context);
			if (!ltm.isAvailable())
				StandardDialogs.needLibraryThingAlert(context, always, suffix);		
		}
	}

	/**
	 * Check if phone has a network connection
	 * 
	 * @return
	 */
	/*
	public static boolean isOnline(Context ctx) {
	    ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo netInfo = cm.getActiveNetworkInfo();
	    if (netInfo != null && netInfo.isConnectedOrConnecting()) {
	        return true;
	    }
	    return false;
	}
	*/

	/**
	 * Check if phone can connect to a specific host.
	 * Does not work....
	 * 
	 * ENHANCE: Find a way to make network host checks possible
	 * 
	 * @return
	 */
	/*
	public static boolean hostIsAvailable(Context ctx, String host) {
		if (!isOnline(ctx))
			return false;
		int addr;
		try {
			addr = lookupHost(host);			
		} catch (Exception e) {
			return false;
		}
	    ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
	    try {
		    return cm.requestRouteToHost(ConnectivityManager., addr);	    	
		} catch (Exception e) {
			return false;
		}
	}
	*/

	public static int lookupHost(String hostname) {
	    InetAddress inetAddress;
	    try {
	        inetAddress = InetAddress.getByName(hostname);
	    } catch (UnknownHostException e) {
	        return -1;
	    }
	    byte[] addrBytes;
	    int addr;
	    addrBytes = inetAddress.getAddress();
	    addr = ((addrBytes[3] & 0xff) << 24)
	            | ((addrBytes[2] & 0xff) << 16)
	            | ((addrBytes[1] & 0xff) << 8)
	            |  (addrBytes[0] & 0xff);
	    return addr;
	}
	
	/**
	 * Format the given string using the passed paraeters.
	 */
	public static String format(Context c, int id, Object...objects) {
		String f = c.getString(id);
		return String.format(f, objects);
	}
	
	/**
	 * Get the 'covers' DB from external storage.
	 */
	public final CoversDbHelper getCoversDb() {
		if (mCoversDb == null) {
			if (mCoversDbCreateFail)
				return null;
			try {
				mCoversDb = new CoversDbHelper();				
			} catch (Exception e) {
				mCoversDbCreateFail = true;
			}
		}
		return mCoversDb;
	}
	
	/**
	 * Cleanup DB connection, if present
	 */
	public void close() {
		if (mCoversDb != null)
			mCoversDb.close();
	}

	/**
	 * Analyze the covers db
	 */
	public void analyzeCovers() {
		CoversDbHelper db = getCoversDb();
		if (db != null)
			db.analyze();
	}

	/**
	 * Erase contents of covers cache
	 */
	public void eraseCoverCache() {
		CoversDbHelper db = getCoversDb();
		if (db != null)
			db.eraseCoverCache();
	}
	
	/**
	 * Erase contents of covers cache
	 */
	public int eraseCachedBookCover(String uuid) {
		CoversDbHelper db = getCoversDb();
		if (db != null)
			return db.eraseCachedBookCover(uuid);
		else 
			return 0;
	}
	
	/** Calendar to construct dates from month numbers */
	private static Calendar mCalendar = null;
	/** Formatter for month names given dates */
	private static SimpleDateFormat mMonthNameFormatter = null;

	public static String getMonthName(int month) {
		if (mMonthNameFormatter == null)
			mMonthNameFormatter = new SimpleDateFormat("MMMM");
		// Create static calendar if necessary
		if (mCalendar == null)
			mCalendar = Calendar.getInstance();
		// Assumes months are integers and in sequence...which everyone seems to assume
		mCalendar.set(Calendar.MONTH, month - 1 + java.util.Calendar.JANUARY);
		return mMonthNameFormatter.format(mCalendar.getTime());
	}

	/**
	 * Format a number of bytes in a human readable form
	 */
	public static String formatFileSize(float space) {
		String sizeFmt;
		String msg;
		if (space < 3072) { // Show 'bytes' if < 3k
			sizeFmt = BookCatalogueApp.getResourceString(R.string.bytes);
		} else if (space < 250 * 1024) { // Show Kb if less than 250kB
			sizeFmt = BookCatalogueApp.getResourceString(R.string.kilobytes);
			space = space / 1024;
		} else { // Show MB otherwise...
			sizeFmt = BookCatalogueApp.getResourceString(R.string.megabytes);
			space = space / (1024 * 1024);
		}
		return String.format(sizeFmt,space);		
	}
}

