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

package com.eleybourn.bookcatalogue.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
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

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.GetThumbnailTask;
import com.eleybourn.bookcatalogue.LibraryThingManager;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.ThumbnailCacheWriterTask;
import com.eleybourn.bookcatalogue.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;


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
	static DateFormat mDateDispSdf = DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
	private static SimpleDateFormat mLocalDateSqlSdf = new SimpleDateFormat("yyyy-MM-dd");
	static { mLocalDateSqlSdf.setTimeZone(Calendar.getInstance().getTimeZone()); }

	private static final ArrayList<SimpleDateFormat> mParseDateFormats = new ArrayList<SimpleDateFormat>();
	static {
		final boolean isEnglish = (Locale.getDefault().getLanguage() == Locale.ENGLISH.getLanguage());
		addParseDateFormat(!isEnglish, "dd-MMM-yyyy HH:mm:ss");
		addParseDateFormat(!isEnglish, "dd-MMM-yyyy HH:mm");
		addParseDateFormat(!isEnglish, "dd-MMM-yyyy");
		
		addParseDateFormat(!isEnglish, "dd-MMM-yy HH:mm:ss");
		addParseDateFormat(!isEnglish, "dd-MMM-yy HH:mm");
		addParseDateFormat(!isEnglish, "dd-MMM-yy");

		addParseDateFormat(false, "MM-dd-yyyy HH:mm:ss");
		addParseDateFormat(false, "MM-dd-yyyy HH:mm");
		addParseDateFormat(false, "MM-dd-yyyy");

		addParseDateFormat(false, "dd-MM-yyyy HH:mm:ss");
		addParseDateFormat(false, "dd-MM-yyyy HH:mm");
		addParseDateFormat(false, "dd-MM-yyyy");

		// Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
		addParseDateFormat(!isEnglish, "EEE MMM dd HH:mm:ss ZZZZ yyyy");
		addParseDateFormat(!isEnglish, "EEE MMM dd HH:mm ZZZZ yyyy");
		addParseDateFormat(!isEnglish, "EEE MMM dd ZZZZ yyyy");

		mParseDateFormats.add(mDateFullHMSSqlSdf);
		mParseDateFormats.add(mDateFullHMSqlSdf);
		mParseDateFormats.add(mDateSqlSdf);
	}

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

	/**
	 * Add a format to the parser list; if nedEnglish is set, also add the localized english version
	 * 
	 * @param needEnglish
	 * @param format
	 */
	private static void addParseDateFormat(boolean needEnglish, String format) {
		mParseDateFormats.add(new SimpleDateFormat(format));
		if (needEnglish)
			mParseDateFormats.add(new SimpleDateFormat(format, Locale.ENGLISH));
	}
	
	public static String toLocalSqlDateOnly(Date d) {
		return mLocalDateSqlSdf.format(d);
	}
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

	/**
	 * Attempt to parse a date string based on a range of possible formats.
	 * 
	 * @param s		String to parse
	 * @return		Resulting date if parsed, otherwise null
	 */
	public static Date parseDate(String s) {
		Date d;
		// First try to parse using strict rules
		d = parseDate(s, false);
		// If we got a date, exit
		if (d != null)
			return d;
		// OK, be lenient
		return parseDate(s, true);
	}

	/**
	 * Attempt to parse a date string based on a range of possible formats; allow
	 * for caller to specify if the parsing should be strict or lenient.
	 * 
	 * @param s				String to parse
	 * @param lenient		True if parsing should be lenient
	 * 
	 * @return				Resulting date if parsed, otherwise null
	 */
	private static Date parseDate(String s, boolean lenient) {
		Date d;
		for ( SimpleDateFormat sdf : mParseDateFormats ) {
			try {
				sdf.setLenient(lenient);
				d = sdf.parse(s);
				return d;
			} catch (Exception e) {
				// Ignore 
			}			
		}
		// All SDFs failed, try locale-specific...
		try {
			java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
			df.setLenient(lenient);
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
		public ArrayList<T> decodeList(String s, char delim, boolean allowBlank) {
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
	 * Given a URL, get an image and save to a file, optionally appending a suffic to the file.
	 * 
	 * @param urlText			Image file URL
	 * @param filenameSuffix	Suffix to add
	 *
	 * @return	Downloaded filespec
	 */
	static public String saveThumbnailFromUrl(String urlText, String filenameSuffix) {
		// Get the URL
		URL u;
		try {
			u = new URL(urlText);
		} catch (MalformedURLException e) {
			Logger.logError(e);
			return "";
		}
		// Turn the URL into an InputStream
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

		// Get the output file
		File file = CatalogueDBAdapter.getTempThumbnail(filenameSuffix);
		// Save to file
		saveInputToFile(in, file);
		// Return new file path
		return file.getAbsolutePath();
	}

	/**
	 * Given a InputStream, save it to a file.
	 * 
	 * @param in		InputStream to read
	 * @param out		File to save
	 * @return			true if successful
	 */
	static public boolean saveInputToFile(InputStream in, File out) {
		File temp = null;
		boolean isOk = false;

		try {
			// Get a temp file to avoid overwriting output unless copy works
			temp = File.createTempFile("temp_", null, StorageUtils.getSharedStorage());
			FileOutputStream f = new FileOutputStream(temp);

			// Copy from input to temp file
			byte[] buffer = new byte[65536];
			int len1 = 0;
			while ( (len1 = in.read(buffer)) > 0 ) {
				f.write(buffer,0, len1);
			}
			f.close();
			// All OK, so rename to real output file
			temp.renameTo(out);
			isOk = true;
		} catch (FileNotFoundException e) {
			Logger.logError(e);
		} catch (IOException e) {
			Logger.logError(e);
		} finally {
			// Delete temp file if it still exists
			if (temp != null && temp.exists())
				try { temp.delete(); } catch (Exception e) {};
		}
		return isOk;
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

	private static class ConnectionInfo {
		URLConnection conn = null;
		StatefulBufferedInputStream is = null;
	}
	
	public static class StatefulBufferedInputStream extends BufferedInputStream {
		private boolean mIsClosed = false;

		public StatefulBufferedInputStream(InputStream in) {
			super(in);
		}
		public StatefulBufferedInputStream(InputStream in, int i) {
			super(in, i);
		}

		@Override
		public void close() throws IOException {
			try {
				super.close();				
			} finally {
				mIsClosed = true;				
			}
		}
		
		public boolean isClosed() {
			return mIsClosed;
		}
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
					/*
					 * This is quite nasty; there seems to be a bug with URL.openConnection
					 *
					 * It CAN be reduced by doing the following:
					 * 
					 *     ((HttpURLConnection)conn).setRequestMethod("GET");
					 *     
					 * but I worry about future-proofing and the assumption that URL.openConnection
					 * will always return a HttpURLConnection. OFC, it probably will...until it doesn't.
					 * 
					 * Using HttpClient and HttpGet explicitly seems to bypass the casting
					 * problem but still does not allow the timeouts to work, or only works intermittently.
					 * 
					 * Finally, there is another problem with faild timeouts:
					 * 
					 *     http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
					 * 
					 * So...we are forced to use a background thread to kill it.
					 */
					
					// If at some stage in the future the casting code breaks...use the Apache one.
					//final HttpClient client = new DefaultHttpClient();
					//final HttpParams httpParameters = client.getParams();
					//
					//HttpConnectionParams.setConnectionTimeout(httpParameters, 30 * 1000);
					//HttpConnectionParams.setSoTimeout        (httpParameters, 30 * 1000);
					//
					//final HttpGet conn = new HttpGet(url.toString());
					//
					//HttpResponse response = client.execute(conn);
					//InputStream is = response.getEntity().getContent();
					//return new BufferedInputStream(is);

					final ConnectionInfo connInfo = new ConnectionInfo();

					connInfo.conn = url.openConnection();
					connInfo.conn.setUseCaches(false);
					connInfo.conn.setDoInput(true);
					connInfo.conn.setDoOutput(false);

					if (connInfo.conn instanceof HttpURLConnection)
						((HttpURLConnection)connInfo.conn).setRequestMethod("GET");

					connInfo.conn.setConnectTimeout(30000);
					connInfo.conn.setReadTimeout(30000);

					Terminator.enqueue(new Runnable() {
						@Override
						public void run() {
							if (connInfo.is != null) {
								if (!connInfo.is.isClosed()) {
									try {
										connInfo.is.close();
										((HttpURLConnection)connInfo.conn).disconnect();
									} catch (IOException e) {
										Logger.logError(e);
									}									
								}
							} else {
								((HttpURLConnection)connInfo.conn).disconnect();								
							}

						}}, 30000);
					connInfo.is = new StatefulBufferedInputStream(connInfo.conn.getInputStream());

					return connInfo.is;

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
	public static String join(String[] sa, String delim) {
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
	public static <T extends ItemWithIdFixup> boolean pruneList(CatalogueDBAdapter db, ArrayList<T> list) {
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
		return toDelete.size() > 0;
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
	public static boolean pruneSeriesList(ArrayList<Series> list) {
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

		return (toDelete.size() > 0);

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
	 * Utility routine to delete all cached covers of a specified book
	 */
	public void deleteCachedBookCovers(String hash) {
		CoversDbHelper coversDb = getCoversDb();
		if (coversDb != null) {
			coversDb.deleteBookCover(hash);
		}
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
		int idealSampleSize = (int)android.util.FloatMath.ceil(1/ratio); // This is the sample size we want to use
		// Get the nearest *bigger* power of 2.
		int samplePow2 = (int)Math.pow(2, Math.ceil(Math.log(idealSampleSize)/Math.log(2)));
		
		try {
			if (exact) {
				// Create one bigger than needed and scale it; this is an attempt to improve quality.
				opt.inSampleSize = samplePow2 / 2;
				if (opt.inSampleSize < 1)
					opt.inSampleSize = 1;
				Bitmap tmpBm = BitmapFactory.decodeFile( filename, opt );
				if (tmpBm == null) {
					// We ran out of memory, most likely
					// TODO: Need a way to try loading images after GC(), or something. Otherwise, covers in cover browser wil stay blank.
					Logger.logError(new RuntimeException("Unexpectedly failed to decode bitmap; memory exhausted?"));
					return null;
				}
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
	
	/**
	 * Set the passed Activity background based on user preferences
	 */
	public static void initBackground(int bgResource, Activity a, boolean bright) {
		initBackground(bgResource, a.findViewById(R.id.root), bright);
	}
	public static void initBackground(int bgResource, SherlockFragment f, boolean bright) {
		initBackground(bgResource, f.getView().findViewById(R.id.root), bright);
	}
	/**
	 * Set the passed Activity background based on user preferences
	 */
	public static void initBackground(int bgResource, Activity a, int rootId, boolean bright) {
		initBackground(bgResource, a.findViewById(rootId), bright);
	}
	
	public static void initBackground(int bgResource, View root, boolean bright) {
		try {
			final int backgroundColor = BookCatalogueApp.context.getResources().getColor(R.color.background_grey);

			if (BookCatalogueApp.isBackgroundImageDisabled()) {
				root.setBackgroundColor(backgroundColor);
				if (root instanceof ListView) {
					setCacheColorHintSafely((ListView)root, backgroundColor);				
				}
			} else {
				if (root instanceof ListView) {
					ListView lv = ((ListView)root);
					setCacheColorHintSafely(lv, 0x00000000);				
				}
				//Drawable d = cleanupTiledBackground(a.getResources().getDrawable(bgResource));
				Drawable d = makeTiledBackground(bright);

				root.setBackgroundDrawable(d);
			}
			root.invalidate();
		} catch (Exception e) {
			// This is a purely cosmetic function; just log the error
			Logger.logError(e, "Error setting background");
		}
	}
	
	/**
 	 * Reuse of bitmaps in tiled backgrounds is a known cause of problems:
     *		http://stackoverflow.com/questions/4077487/background-image-not-repeating-in-android-layout
	 * So we avoid reusing them.
	 * 
	 * This seems to have become further messed up in 4.1 so now, we just created them manually. No references,
	 * but the old cleanup method (see below for cleanupTiledBackground()) no longer works. Since it effectively
	 * un-cached the background, we just create it here.
	 * 
	 * The main problem with this approach is that the style is defined in code rather than XML.
	 *
	 * @param a			Activity context
	 * @param bright	Flag indicating if background should be 'bright'
	 * 
	 * @return			Background Drawable
	 */
	public static Drawable makeTiledBackground(boolean bright) {
		// Storage for the layers
		Drawable[] drawables = new Drawable[2];
		// Get the BG image, put in tiled drawable
		Bitmap b = BitmapFactory.decodeResource(BookCatalogueApp.context.getResources(), R.drawable.books_bg);
		BitmapDrawable bmD = new BitmapDrawable(BookCatalogueApp.context.getResources(), b);
		bmD.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
		// Add to layers
		drawables[0] = bmD;

		// Set up the gradient colours based on 'bright' setting
		int[] colours = new int[3];
		if (bright) {
			colours[0] = Color.argb(224, 0, 0, 0);
			colours[1] = Color.argb(208, 0, 0, 0);
			colours[2] = Color.argb(48, 0, 0, 0);			
		} else {
			colours[0] = Color.argb(255, 0, 0, 0);
			colours[1] = Color.argb(208, 0, 0, 0);
			colours[2] = Color.argb(160, 0, 0, 0);
		}

		// Create a gradient and add to layers
		GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colours);
		drawables[1] = gd;

		// Make the layers and we are done.
		LayerDrawable ll = new LayerDrawable(drawables);
		ll.setDither(true);
		
		return ll;
	}

	///**
	// * Reuse of bitmaps in tiled backgrounds is a known cause of problems:
	// *		http://stackoverflow.com/questions/4077487/background-image-not-repeating-in-android-layout
	// * So we avoid reusing them
	// *
	// * @param d		Drawable background that may be a BitmapDrawable or a layered drawablewhose first 
	// * 				layer is a tiled bitmap
	// * 
	// * @return		Modified Drawable
	// */
	//private static Drawable cleanupTiledBackground(Drawable d) {
	//	if (d instanceof LayerDrawable) {
	//		System.out.println("BG: BG is layered");
	//		LayerDrawable ld = (LayerDrawable)d;
	//		Drawable l = ld.getDrawable(0);
	//		if (l instanceof BitmapDrawable) {
	//			d.mutate();
	//			l.mutate();
	//			System.out.println("BG: Layer0 is BMP");
	//			BitmapDrawable bmp = (BitmapDrawable) l;
	//			bmp.mutate(); // make sure that we aren't sharing state anymore
	//			//bmp.setTileModeXY(TileMode.CLAMP, TileMode.CLAMP);			
	//			bmp.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
	//		} else {
	//			System.out.println("BG: Layer0 is " + l.getClass().getSimpleName() + " (ignored)");				
	//		}
	//	} else if (d instanceof BitmapDrawable) {
	//		System.out.println("BG: Drawable is BMP");
	//		BitmapDrawable bmp = (BitmapDrawable) d;
	//		bmp.mutate(); // make sure that we aren't sharing state anymore
	//		//bmp.setTileModeXY(TileMode.CLAMP, TileMode.CLAMP);			
	//		bmp.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);			
	//	}
	//	return d;
	//}

	/**
	 * Call setCacheColorHint on a listview and trap IndexOutOfBoundsException. 
	 * 
	 * There is a bug in Android 2.2-2.3 (approx) that causes this call to throw 
	 * exceptions *sometimes* (circumstances unclear):
	 * 
	 *     http://code.google.com/p/android/issues/detail?id=9775
	 * 
	 * Ideally this code should use reflection to set it, or check android versions.
	 * 
	 * @param lv		ListView to set
	 * @param hint		Colour hint
	 */
	public static void setCacheColorHintSafely(ListView lv, int hint) {
		try {
			lv.setCacheColorHint(hint);
		} catch (IndexOutOfBoundsException e) {
			// Ignore
			System.out.println("Android Bug avoided");
		}
	}
	
	/**
	 * Format the passed bundle in a way that is convenient for display
	 * 
	 * @param b		Bundle to format
	 * 
	 * @return		Formatted string
	 */
	public static String bundleToString(Bundle b) {
		StringBuilder sb = new StringBuilder();
		for(String k: b.keySet()) {
			sb.append(k);
			sb.append("->");
			try {
				sb.append(b.get(k).toString());
			} catch (Exception e) {
				sb.append("<<Unknown>>");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
	private interface INextView {
		int getNext(View v);
		void setNext(View v, int id);
	}

	/**
	 * Ensure that next up/down/left/right View is visible for all sub-views of the 
	 * passed view.
	 * 
	 * @param root
	 */
	public static void fixFocusSettings(View root) {
		final INextView getDown = new INextView() {
			@Override public int getNext(View v) { return v.getNextFocusDownId(); }
			@Override public void setNext(View v, int id) { v.setNextFocusDownId(id); }
		};
		final INextView getUp = new INextView() {
			@Override public int getNext(View v) { return v.getNextFocusUpId(); }
			@Override public void setNext(View v, int id) { v.setNextFocusUpId(id); }
		};
		final INextView getLeft = new INextView() {
			@Override public int getNext(View v) { return v.getNextFocusLeftId(); }
			@Override public void setNext(View v, int id) { v.setNextFocusLeftId(id); }
		};
		final INextView getRight = new INextView() {
			@Override public int getNext(View v) { return v.getNextFocusRightId(); }
			@Override public void setNext(View v, int id) { v.setNextFocusRightId(id); }
		};

		Hashtable<Integer,View> vh = getViews(root);

		for(Entry<Integer, View> ve: vh.entrySet()) {
			final View v = ve.getValue();
			if (v.getVisibility() == View.VISIBLE) {
				fixNextView(vh, v, getDown);
				fixNextView(vh, v, getUp);
				fixNextView(vh, v, getLeft);
				fixNextView(vh, v, getRight);
			}
		}
	}

	/**
	 * Passed a collection of views, a specific View and an INextView, ensure that the 
	 * currently set 'next' view is actually a visible view, updating it if necessary.
	 * 
	 * @param vh		Collection of all views
	 * @param v			View to check
	 * @param getter	Methods to get/set 'next' view
	 */
	private static void fixNextView(Hashtable<Integer,View> vh, View v, INextView getter) {
		int nextId = getter.getNext(v);
		if (nextId != View.NO_ID) {
			int actualNextId = getNextView(vh, nextId, getter);
			if (actualNextId != nextId)
				getter.setNext(v, actualNextId);
		}
	}

	/**
	 * Passed a collection of views, a specific view and an INextView object find the 
	 * first VISIBLE object returned by INextView when called recursively.
	 * 
	 * @param vh		Collection of all views
	 * @param nextId	ID of 'next' view to get
	 * @param getter	Interface to lookup 'next' ID given a view
	 * 
	 * @return			ID if first visible 'next' view
	 */
	private static int getNextView(Hashtable<Integer,View> vh, int nextId, INextView getter) {
		final View v = vh.get(nextId);
		if (v == null)
			return View.NO_ID;

		if (v.getVisibility() == View.VISIBLE)
			return nextId;
		
		return getNextView(vh, getter.getNext(v), getter);
	}

	/**
	 * Passed a parent View return a collection of all child views that have IDs.
	 * 
	 * @param v		Parent View
	 *
	 * @return	Hashtable of descendants with ID != NO_ID
	 */
	private static Hashtable<Integer,View> getViews(View v) {
		Hashtable<Integer,View> vh = new Hashtable<Integer,View>();
		getViews(v, vh);
		return vh;
	}
	
	/**
	 * Passed a parent view, add it and all children view (if any) to the passed collection
	 * 
	 * @param p		Parent View
	 * @param vh	Collection
	 */
	private static void getViews(View p, Hashtable<Integer,View> vh) {
		// Get the view ID and add it to collection if not already present.
		final int id = p.getId();
		if (id != View.NO_ID && !vh.containsKey(id)) {
			vh.put(id, p);
		}
		// If it's a ViewGroup, then process children recursively.
		if (p instanceof ViewGroup) {
			final ViewGroup g = (ViewGroup)p;
			final int nChildren = g.getChildCount();
			for(int i = 0; i < nChildren; i++) {
				getViews(g.getChildAt(i), vh);
			}
		}

		
	}
	
	/**
	 * Debug utility to dump an entire view hierarchy to the output.
	 * 
	 * @param depth
	 * @param v
	 */
	//public static void dumpViewTree(int depth, View v) {
	//	for(int i = 0; i < depth*4; i++)
	//		System.out.print(" ");
	//	System.out.print(v.getClass().getName() + " (" + v.getId() + ")" + (v.getId() == R.id.descriptionLabelzzz? "DESC! ->" : " ->"));
	//	if (v instanceof TextView) {
	//		String s = ((TextView)v).getText().toString();
	//		System.out.println(s.substring(0, Math.min(s.length(), 20)));
	//	} else {
	//		System.out.println();
	//	}
	//	if (v instanceof ViewGroup) {
	//		ViewGroup g = (ViewGroup)v;
	//		for(int i = 0; i < g.getChildCount(); i++) {
	//			dumpViewTree(depth+1, g.getChildAt(i));
	//		}
	//	}
	//}
	
	/**
	 * Passed date components build a (partial) SQL format date string.
	 * 
	 * @param year
	 * @param month
	 * @param day
	 * 
	 * @return		Formatted date, eg. '2011-11-01' or '2011-11'
	 */
	public static String buildPartialDate(Integer year, Integer month, Integer day) {
		String value;
		if (year == null) {
			value = "";
		} else {
			value = String.format("%04d", year);
			if (month != null && month > 0) {
				String mm = month.toString();
				if (mm.length() == 1) {
					mm = "0" + mm;
				}

				value += "-" + mm;

				if (day != null && day > 0) {
					String dd = day.toString();
					if (dd.length() == 1) {
						dd = "0" + dd;
					}
					value += "-" + dd;
				}
			}
		}
		return value;
	}

	/**
	 * Set the relevant fields in a BigDateDialog
	 * 
	 * @param dialog		Dialog to set
	 * @param current		Current value (may be null)
	 * @param listener		Listener to be called on dialg completion.
	 */
	public static void prepareDateDialogFragment(PartialDatePickerFragment dialog, Object current) {
		String dateString = current == null ? "" : current.toString();
		// get the current date
		Integer yyyy = null;
		Integer mm = null;
		Integer dd = null;
		try {
			String[] dateAndTime = dateString.split(" ");
			String[] date = dateAndTime[0].split("-");
			yyyy = Integer.parseInt(date[0]);
			mm = Integer.parseInt(date[1]);
			dd = Integer.parseInt(date[2]);				
		} catch (Exception e) {
			//do nothing
		}
		dialog.setDate(yyyy, mm, dd);
	}

//	/**
//	 * Build a new BigDateDialog and return it.
//	 * 
//	 * @param context
//	 * @param titleId
//	 * @param listener
//	 * @return
//	 */
//	public static PartialDatePicker buildDateDialog(Context context, int titleId, PartialDatePicker.OnDateSetListener listener) {
//		PartialDatePicker dialog = new PartialDatePicker(context);
//		dialog.setTitle(titleId);
//		dialog.setOnDateSetListener(listener);
//		return dialog;
//	}

	/**
	 * Utility routine to get an author list from the intent extras
	 * 
	 * @param i		Intent with author list
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	public static ArrayList<Author> getAuthorsFromBundle(Bundle b) {
		return (ArrayList<Author>) b.getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
	}

	/**
	 * Utility routine to get a series list from the intent extras
	 * 
	 * @param i		Intent with series list
	 * @return		List of series
	 */
	@SuppressWarnings("unchecked")
	public
	static ArrayList<Series> getSeriesFromBundle(Bundle b) {
		return (ArrayList<Series>) b.getSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY);
	}
	
	/**
	 * Utility routine to get the list from the passed bundle. Added to reduce lint warnings...
	 * 
	 * @param b		Bundle containig list
	 */
	@SuppressWarnings("unchecked")
	public static <T> ArrayList<T> getListFromBundle(Bundle b, String key) {
		return (ArrayList<T>) b.getSerializable(key);
	}

	/** 
	 * Saved copy of the MD5 hash of the public key that signed this app, or a useful
	 * text message if an error or other problem occurred.
	 */
	private static String mSignedBy = null;

	/**
	 * Return the MD5 hash of the public key that signed this app, or a useful 
	 * text message if an error or other problem occurred.
	 */
	public static String signedBy(Context context) {
		// Get value if no cached value exists
		if (mSignedBy == null) {
			try {
		    	// Get app info
		        PackageManager manager = context.getPackageManager(); 
		        PackageInfo appInfo = manager.getPackageInfo( context.getPackageName(), PackageManager.GET_SIGNATURES);
		        // Each sig is a PK of the signer:
		        //     https://groups.google.com/forum/?fromgroups=#!topic/android-developers/fPtdt6zDzns
		        for(Signature sig: appInfo.signatures) {
			        if (sig != null) {
	                    final MessageDigest sha1 = MessageDigest.getInstance("MD5");
	                    final byte[] publicKey = sha1.digest(sig.toByteArray());
	                    // Turn the hex bytes into a more traditional MD5 string representation.
	                    final StringBuffer hexString = new StringBuffer();
	                    boolean first = true;
	                    for (int i = 0; i < publicKey.length; i++)
	                    {
	                        if (!first) {
	                        	hexString.append(":");
	                        } else {
	                        	first = false;
	                        }
	                        String byteString = Integer.toHexString(0xFF & publicKey[i]);
	                        if (byteString.length() == 1) 
	                        	hexString.append("0");
	                        hexString.append(byteString);
	                    }
	                    String fingerprint = hexString.toString();

	                    // Append as needed (theoretically could have more than one sig */
	                    if (mSignedBy == null)
	                    	mSignedBy = fingerprint;
	                    else
	                    	mSignedBy += "/" + fingerprint;
			        }
		        }

		    } catch (NameNotFoundException e) {
				// Default if package not found...kind of unlikely
				mSignedBy = "NOPACKAGE";

		    } catch (Exception e) {
				// Default if we die
				mSignedBy = e.getMessage();
		    }			
		}
		return mSignedBy;
	}

	/**
	 * Utility function to convert string to boolean
	 * 
	 * @param s		String to convert
	 * @param emptyIsFalse TODO
	 * 
	 * @return		Boolean value
	 */
	public static boolean stringToBoolean(String s, boolean emptyIsFalse) {
		boolean v;
		if (s == null || s.equals(""))
			if (emptyIsFalse) {
				v = false;
			} else {
				throw new RuntimeException("Not a valid boolean value");						
			}
		else if (s.equals("1"))
			v = true;
		else if (s.equals("0"))
			v = false;
		else {
			s = s.trim().toLowerCase();
			if (s.equals("t"))
				v = true;
			else if (s.equals("f"))
				v = false;
			else if (s.equals("true"))
				v = true;
			else if (s.equals("false"))
				v = false;
			else if (s.equals("y"))
				v = true;
			else if (s.equals("n"))
				v = false;
			else if (s.equals("yes"))
				v = true;
			else if (s.equals("no"))
				v = false;
			else {
				try {
					Integer i = Integer.parseInt(s);
					return i != 0;
				} catch (Exception e) {
					throw new RuntimeException("Not a valid boolean value");						
				}
			}
		}
		return v;
	}

	public static boolean objectToBoolean(Object o) {
		if (o instanceof Boolean) {
			return (Boolean)o;
		}
		if (o instanceof Integer || o instanceof Long) {
			return (Long)o != 0;
		}
		try {
			return (Boolean)o;
		} catch (ClassCastException e) {
			return stringToBoolean(o.toString(), true);
		}
	}

	public static void openAmazonSearchPage(Activity context, String author, String series) {
		
		try {
			AmazonUtils.openLink(context, author, series);
		} catch(Exception ae) {
			// An Amazon error should not crash the app
			Logger.logError(ae, "Unable to call the Amazon API");
			Toast.makeText(context, R.string.unexpected_error, Toast.LENGTH_LONG).show();
			// This code works, but Amazon have a nasty tendency to cancel Associate IDs...
			//String baseUrl = "http://www.amazon.com/gp/search?index=books&tag=philipwarneri-20&tracking_id=philipwarner-20";
			//String extra = AmazonUtils.buildSearchArgs(author, series);
			//if (extra != null && !extra.trim().equals("")) {
			//	Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + extra));
			//	context.startActivity(loadweb); 			
			//}			
		}
		return;
	}
	
	/**
	 * Linkify partial HTML. Linkify methods remove all spans before building links, this
	 * method preserves them.
	 * 
	 * See: http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml
	 * 
	 * @param html			Partial HTML
	 * @param linkifyMask	Linkify mask to use in Linkify.addLinks
	 * 
	 * @return				Spannable with all links
	 */
	public static Spannable linkifyHtml(String html, int linkifyMask) {
		// Get the spannable HTML
	    Spanned text = Html.fromHtml(html);
	    // Save the span details for later restoration
	    URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

	    // Build an empty spannable then add the links
	    SpannableString buffer = new SpannableString(text);
	    Linkify.addLinks(buffer, linkifyMask);

	    // Add back the HTML spannables
	    for (URLSpan span : currentSpans) {
	        int end = text.getSpanEnd(span);
	        int start = text.getSpanStart(span);
	        buffer.setSpan(span, start, end, 0);
	    }
	    return buffer;
	}

}

