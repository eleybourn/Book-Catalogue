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
import java.io.FileInputStream;
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
import java.nio.channels.FileChannel;
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
import java.util.Objects;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
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

import androidx.core.text.HtmlCompat;

import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.GetThumbnailTask;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.ThumbnailCacheWriterTask;
import com.eleybourn.bookcatalogue.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;


public class Utils {
	// External DB for cover thumbnails
	private boolean mCoversDbCreateFail = false;
	/** Database is non-static member so we don't make it linger longer than necessary */
	private CoversDbHelper mCoversDb = null;

	// Used for formatting dates for sql; everything is assumed to be UTC, or converted to UTC since 
	// UTC is the default SQLite TZ. 
	static TimeZone tzUtc = TimeZone.getTimeZone("UTC");

	// Used for date parsing and display
	private static final SimpleDateFormat mDateFullHMSSqlSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static { mDateFullHMSSqlSdf.setTimeZone(tzUtc); }
	private static final SimpleDateFormat mDateFullHMSqlSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	static { mDateFullHMSqlSdf.setTimeZone(tzUtc); }
	private static final SimpleDateFormat mDateSqlSdf = new SimpleDateFormat("yyyy-MM-dd");
	static { mDateSqlSdf.setTimeZone(tzUtc); }
	static DateFormat mDateDisplaySdf = DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
	private static final SimpleDateFormat mLocalDateSqlSdf = new SimpleDateFormat("yyyy-MM-dd");
	static { mLocalDateSqlSdf.setTimeZone(Calendar.getInstance().getTimeZone()); }

	private static final ArrayList<SimpleDateFormat> mParseDateFormats = new ArrayList<>();
	static {
		final boolean isEnglish = (Locale.getDefault().getLanguage().equals(Locale.ENGLISH.getLanguage()));
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

    public static final boolean USE_LT = true;
	public static final boolean USE_BARCODE = true;

	/**
	 * Add a format to the parser list; if nedEnglish is set, also add the localized english version
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
		mDateDisplaySdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return mDateDisplaySdf.format(d);
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
		d = parseDate(s, false, false);
		// If we got a date, exit
		if (d != null)
			return d;
		// OK, be lenient
		return parseDate(s, true, false);
	}

	/**
	 * Attempt to parse a publication date string based on a range of possible formats.
	 *
	 * @param s			String to parse
	 * @param forceUtc 	True if forcing UTC format
	 * @return			Resulting date if parsed, otherwise null
	 */
	public static Date parseDate(String s, boolean forceUtc) {
		Date d;
		// First try to parse using strict rules
		d = parseDate(s, false, forceUtc);
		// If we got a date, exit
		if (d != null)
			return d;
		// OK, be lenient
		return parseDate(s, true, forceUtc);
	}

	/**
	 * Attempt to parse a date string based on a range of possible formats; allow
	 * for caller to specify if the parsing should be strict or lenient.
	 * 
	 * @param s				String to parse
	 * @param lenient		True if parsing should be lenient
	 * @param forceUtc 		True if forcing UTC format
	 * @return				Resulting date if parsed, otherwise null
	 */
	private static Date parseDate(String s, boolean lenient, boolean forceUtc) {
		Date d;
		for ( SimpleDateFormat sdf : mParseDateFormats ) {
			try {
				sdf.setLenient(lenient);
				if(forceUtc) {
					sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
				}
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
			mAuthorUtils = new ArrayUtils<>(Author::new);
		}
		return mAuthorUtils;
	}

	static public ArrayUtils<Series> getSeriesUtils() {
		if (mSeriesUtils == null) {
			mSeriesUtils = new ArrayUtils<>(Series::new);
		}
		return mSeriesUtils;
	}

	/**
	 * Encode a string by 'escaping' all instances of: '|', '\', \r, \n. The
	 * escape char is '\'.
	 * This is used to build text lists separated by the passed delimiter.
	 * 
	 * @param s			String to convert
	 * @param delimiter		The list delimiter to encode (if found).
	 * 
	 * @return		Converted string
	 */
	public static String encodeListItem(String s, char delimiter) {
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
		    	if (c == delimiter)
		    		ns.append("\\");
		    	ns.append(c);
		    }
		}
		return ns.toString();
	}

	/**
	 * Encode a list of strings by 'escaping' all instances of: delimiter, '\', \r, \n. The
	 * escape char is '\'.
	 * This is used to build text lists separated by 'delimiter'.
	 * 
	 * @param sa	String array to convert
	 * @return		Converted string
	 */
	@SuppressWarnings("unused")
    static String encodeList(ArrayList<String> sa, char delimiter) {
		StringBuilder ns = new StringBuilder();
		Iterator<String> si = sa.iterator();
		if (si.hasNext()) {
			ns.append(encodeListItem(si.next(), delimiter));
			while (si.hasNext()) {
				ns.append(delimiter);
				ns.append(encodeListItem(si.next(), delimiter));
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
		 * Encode a list of strings by 'escaping' all instances of: delimiter, '\', \r, \n. The
		 * escape char is '\'.
		 * This is used to build text lists separated by 'delimiter'.
		 * 
		 * @param sa	String array to convert
		 * @return		Converted string
		 */
		public String encodeList(ArrayList<T> sa, char delimiter) {
			Iterator<T> si = sa.iterator();
			return encodeList(si, delimiter);
		}

		private String encodeList(Iterator<T> si, char delimiter) {
			StringBuilder ns = new StringBuilder();
			if (si.hasNext()) {
				ns.append(encodeListItem(si.next().toString(), delimiter));
				while (si.hasNext()) {
					ns.append(delimiter);
					ns.append(encodeListItem(si.next().toString(), delimiter));
				}
			}
			return ns.toString();
		}
		
		/**
		 * Decode a text list separated by '|' and encoded by encodeListItem.
		 */
		public ArrayList<T> decodeList(String s, char delimiter, boolean allowBlank) {
			StringBuilder ns = new StringBuilder();
			ArrayList<T> list = new ArrayList<>();
			if (s == null)
				return list;

			boolean inEsc = false;
			for (int i = 0; i < s.length(); i++){
			    char c = s.charAt(i);
			    if (inEsc) {
			    	switch(c) {
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
                    if (c == '\\') {
                        inEsc = true;
                    } else {
                        if (c == delimiter) {
                            String source = ns.toString();
                            if (allowBlank || !source.isEmpty())
                                list.add(get(source));
                            ns.setLength(0);
                        } else {
                            ns.append(c);
                        }
                    }
			    }
			}
			// It's important to send back even an empty item.
    		String source = ns.toString();
    		if (allowBlank || !source.isEmpty())
		    	list.add(get(source));
			return list;
		}
	}

	/**
	 * Decode a text list separated by '|' and encoded by encodeListItem.
	 */
	public static ArrayList<String> decodeList(String s, char delimiter) {
		StringBuilder ns = new StringBuilder();
		ArrayList<String> list = new java.util.ArrayList<>();
		boolean inEsc = false;
		for (int i = 0; i < s.length(); i++){
		    char c = s.charAt(i);
		    if (inEsc) {
		    	switch(c) {
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
                if (c == '\\') {
                    inEsc = true;
                } else {
                    if (c == delimiter) {
                        list.add(ns.toString());
                        ns.setLength(0);
                    } else {
                        ns.append(c);
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
		if (!values.containsKey(key) || Objects.requireNonNull(values.getString(key)).isEmpty()) {
			values.putString(key, s);
		} else {
			String curr = values.getString(key);
			values.putString(key, curr + "|" + s);
		}
	}

	/**
	 * Given a URL, get an image and save to a file, optionally appending a suffix to the file.
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
            HttpGet httpRequest = new HttpGet(u.toURI());
            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response = httpclient.execute(httpRequest);

            HttpEntity entity = response.getEntity();
            BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
            in = bufHttpEntity.getContent();

            // Get the output file
            File file = CatalogueDBAdapter.getTempThumbnail(filenameSuffix);
            // Save to file
            saveInputToFile(in, file);
            // Return new file path
            return file.getAbsolutePath();

        } catch (IOException | URISyntaxException e) {
			Logger.logError(e);
			return "";
        } finally {
            // Ensure the InputStream is always closed.
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Log error on close, but don't re-throw as we are cleaning up.
                    Logger.logError(e, "Failed to close input stream in saveThumbnailFromUrl");
                }
            }
        }
	}

	/**
	 * Given a InputStream, save it to a file.
	 * 
	 * @param in		InputStream to read
	 * @param out		File to save
	 * @return			true if successful
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
    static public boolean saveInputToFile(InputStream in, File out) {
		File temp = null;
		boolean isOk = false;

		try {
			// Get a temp file to avoid overwriting output unless copy works; put in same dir so rename works.
			temp = File.createTempFile("temp_", null, out.getParentFile());
			FileOutputStream f = new FileOutputStream(temp);

			// Copy from input to temp file
			byte[] buffer = new byte[65536];
			int len1;
			while ( (len1 = in.read(buffer)) >= 0 ) {
				f.write(buffer,0, len1);
			}
			f.close();
			// All OK, so rename to real output file
			temp.renameTo(out);
			isOk = true;
		} catch (IOException e) {
			Logger.logError(e);
		} finally {
			// Delete temp file if it still exists
			if (temp != null && temp.exists())
				try { temp.delete(); } catch (Exception ignored) {}
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
	@SuppressWarnings("unused")
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
		InputStream in;
		try {
			c = (HttpURLConnection) u.openConnection();
			c.setConnectTimeout(30000);
			c.setReadTimeout(30000);
			c.setRequestMethod("GET");
			c.setDoInput(true);
			c.setUseCaches(false);
			c.connect();
			in = c.getInputStream();
			if ( c.getResponseCode() >= 300) {
				Logger.logError(new RuntimeException("URL lookup failed: " + c.getResponseCode() + " "  + c.getResponseMessage() + ", URL: " + u));
				return null;
			}
		} catch (IOException e) {
			Logger.logError(e);
			return null;
		}

		// Save the output to a byte output stream
		ByteArrayOutputStream f = new ByteArrayOutputStream();
		try {
			byte[] buffer = new byte[65536];
			int len1;
			while ( (len1 = in.read(buffer)) >= 0 ) {
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
		@SuppressWarnings("unused")
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
	 */
	static public InputStream getInputStream(URL url) throws UnknownHostException {
		
		synchronized (url) {
			
			int retries = 3;
			while (true) {
				try {
					final ConnectionInfo connInfo = new ConnectionInfo();

					connInfo.conn = url.openConnection();
					connInfo.conn.setUseCaches(false);
					connInfo.conn.setDoInput(true);
					connInfo.conn.setDoOutput(false);

					HttpURLConnection c;
					if (connInfo.conn instanceof HttpURLConnection) {
						c = (HttpURLConnection) connInfo.conn;
						c.setRequestMethod("GET");
					} else {
						c = null;
					}

					connInfo.conn.setConnectTimeout(30000);
					connInfo.conn.setReadTimeout(30000);

					Terminator.enqueue(() -> {
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

                    }, 30000);
					connInfo.is = new StatefulBufferedInputStream(connInfo.conn.getInputStream());

					if ( c != null && c.getResponseCode() >= 300) {
						Logger.logError(new RuntimeException("URL lookup failed: " + c.getResponseCode() + " "  + c.getResponseMessage() + ", URL: " + url));
						return null;
					}

					return connInfo.is;

				} catch (java.net.UnknownHostException e) {
					Logger.logError(e);
					retries--;
					if (retries-- == 0)
						throw e;
					try { Thread.sleep(500); } catch(Exception ignored) {}
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
            for (NetworkInfo networkInfo : info) {
                if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                    return true;
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
	@SuppressWarnings("ResultOfMethodCallIgnored")
    static public void cleanupThumbnails(Bundle result) {
    	if (result.containsKey("__thumbnail")) {
    		long best = -1;
    		int bestFile = -1;

    		// Parse the list
    		ArrayList<String> files = Utils.decodeList(Objects.requireNonNull(result.getString("__thumbnail")), '|');

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
		    	    	long size = (long) opt.outHeight * opt.outWidth;
		    	    	if (size > best) {
		    	    		best = size;
		    	    		bestFile = i;
		    	    	}
		    	    }	    		
	    		}
    		}

			// Defensive:
			// We rename the best file first in case there are multiple files with same name.

			// Get the best file (if present) and rename it.
			if (bestFile >= 0) {
				File file = new File(files.get(bestFile));
				file.renameTo(CatalogueDBAdapter.getTempThumbnail());
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
    		// Finally, cleanup the data
    		result.remove("__thumbnail");
    		result.putBoolean(CatalogueDBAdapter.KEY_THUMBNAIL, true);
    	}
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
				return (s != null && !s.isEmpty());
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}
	/**
	 * Join the passed array of strings, with 'delimiter' between them.
	 * 
	 * @param sa		Array of strings to join
	 * @param delimiter		Delimiter to place between entries
	 * 
	 * @return			The joined strings
	 */
	public static String join(String[] sa, String delimiter) {
		// Simple case, return empty string
		if (sa.length == 0)
			return "";

		// Initialize with first
		StringBuilder buf = new StringBuilder(sa[0]);

		if (sa.length > 1) {
			// If more than one, loop appending delimiter then string.
			for(int i = 1; i < sa.length; i++) {
				buf.append(delimiter);
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
	@SuppressWarnings("unused")
    public static String getAsString(Bundle b, String key) {
		Object o = b.get(key);
        assert o != null;
        return o.toString();
	}

	public interface ItemWithIdFixup {
		long fixupId(CatalogueDBAdapter db);
		long getId();
		boolean isUniqueById();
	}

	/**
	 * Passed a list of Objects, remove duplicates based on the toString result.
	 * ENHANCE Add author_aliases table to allow further pruning (eg. Joe Haldeman == Joe W Haldeman).
	 * ENHANCE Add series_aliases table to allow further pruning (eg. 'Amber Series' <==> 'Amber').
	 * 
	 * @param db		Database connection to lookup IDs
	 * @param list		List to clean up
	 */
	public static <T extends ItemWithIdFixup> boolean pruneList(CatalogueDBAdapter db, ArrayList<T> list) {
		Hashtable<String,Boolean> names = new Hashtable<>();
		Hashtable<Long,Boolean> ids = new Hashtable<>();

		// We have to go forwards through the list because 'first item' is important,
		// but we also can't delete things as we traverse if we are going forward. So
		// we build a list of items to delete.
		ArrayList<Integer> toDelete = new ArrayList<>();

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
		return !toDelete.isEmpty();
	}

	/**
	 * Remove series from the list where the names are the same, but one entry has a null or empty position.
	 * eg. the following list should be processed as indicated:
	 * fred(5)
	 * fred <-- delete
	 * bill <-- delete
	 * bill <-- delete
	 * bill(1)
	 */
	public static boolean pruneSeriesList(ArrayList<Series> list) {
		ArrayList<Series> toDelete = new ArrayList<>();
		Hashtable<String, Series> index = new Hashtable<>();

		for(Series s: list) {
			final boolean emptyNum = s.num == null || s.num.trim().isEmpty();
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
                    assert orig != null;
                    if (orig.num == null || orig.num.trim().isEmpty()) {
						// Replace with this one, and mark orig for delete
						index.put(lcName, s);
						toDelete.add(orig);
					} else {
						// Both have numbers. See if they are the same.
						if (s.num.trim().equalsIgnoreCase(orig.num.trim())) {
							// Same exact series, delete this one
							toDelete.add(s);
						}
					}
				}
			}
		}
		
		for (Series s: toDelete) 
			list.remove(s);			

		return (!toDelete.isEmpty());

	}
	/**
	 * Convert a array of objects to a string.
     * 
	 * @param a		Array
	 * @return		Resulting string
	 */
	@SuppressWarnings("unused")
    public static <T> String ArrayToString(ArrayList<T> a) {
		StringBuilder details = new StringBuilder();

		for (T i : a) {
			if (details.length() > 0)
				details.append("|");
			details.append(Utils.encodeListItem(i.toString(), '|'));			
		}
		return details.toString();
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
		} catch (ParserConfigurationException | java.io.IOException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception ignored) {}
            Logger.logError(e, s);
		} catch (SAXException e) {
			String s = e.getMessage(); // "unknown";
			try { s = e.getMessage(); } catch (Exception ignored) {}
            Logger.logError(e, s);
		}
	}

	/**
	 * Shrinks the image in the passed file to the specified dimensions, and places the image
	 * in the passed view. The bitmap is returned.
	 */
	public static Bitmap fetchFileIntoImageView(File file, ImageView destView, int maxWidth, int maxHeight, boolean exact) {

		Bitmap bm;					// resultant Bitmap (which we will return) 

		// Get the file, if it exists. Otherwise set 'help' icon and exit.
		if (!file.exists()) {
			if (destView != null)
				destView.setImageResource(R.drawable.ic_menu_help);
			return null;
		}

		bm = shrinkFileIntoImageView(destView, file.getPath(), maxWidth, maxHeight, exact);

		return bm;
	}

	/**
	 * Construct the cache ID for a given thumbnail spec.
	 * NOTE: Any changes to the resulting name MUST be reflect in CoversDbHelper.eraseCachedBookCover()
	 */
	public static String getCoverCacheId(final String hash, final int maxWidth, final int maxHeight) {
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
				}
            if (bytes != null) {
				try {
					bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				} catch (Exception ignored) {
                }
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
	 * <p> 
	 * If a cached image is used a background task is still started to check the file date vs the cache date. If the
	 * cached image date is < the file, it is rebuilt.
	 * 
	 * @param destView			View to populate
	 * @param maxWidth			Max width of resulting image
	 * @param maxHeight			Max height of resulting image
	 * @param exact				Whether to fit dimensions exactly
	 * @param hash				ID of book to retrieve.
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
		}

		if (bm != null)
			return bm;

		// Check the file exists. Otherwise set 'help' icon and exit.
		//if (!coverFile.exists()) {
		//	if (destView != null)
		//		destView.setImageResource(R.drawable.ic_menu_help);
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
	 * Shrinks the passed image file spec into the specified dimensions, and returns the bitmap. If the view 
	 * is non-null, the image is also placed in the view.
	 */
	private static Bitmap shrinkFileIntoImageView(ImageView destView, String filename, int maxWidth, int maxHeight, boolean exact) {
		Bitmap bm;

		// Read the file to get file size
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inJustDecodeBounds = true;
		BitmapFactory.decodeFile( filename, opt );

		// If no size info, or a single pixel, assume file bad and set the 'alert' icon
		if ( opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight== 1 && opt.outWidth == 1) ) {
			if (destView != null)
				destView.setImageResource(R.drawable.ic_alert_warning);
			return null;
		}

		// Next time we don't just want the bounds, we want the file
		opt.inJustDecodeBounds = false;
		
		// Work out how to scale the file to fit in required bounding box
		float widthRatio = (float)maxWidth / opt.outWidth; 
		float heightRatio = (float)maxHeight / opt.outHeight;
		
		// Work out scale so that it fit exactly
		float ratio = Math.min(widthRatio, heightRatio);
		
		// Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
		// specify, so we just work with powers of 2.
		int idealSampleSize = (int)Math.ceil(1/ratio); // This is the sample size we want to use
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

	@SuppressWarnings("unused")
    public static int lookupHost(String hostname) {
	    InetAddress inetAddress;
	    try {
	        inetAddress = InetAddress.getByName(hostname);
	    } catch (UnknownHostException e) {
	        return -1;
	    }
	    byte[] addressBytes;
	    int address;
	    addressBytes = inetAddress.getAddress();
	    address = ((addressBytes[3] & 0xff) << 24)
	            | ((addressBytes[2] & 0xff) << 16)
	            | ((addressBytes[1] & 0xff) << 8)
	            |  (addressBytes[0] & 0xff);
	    return address;
	}
	
	/**
	 * Format the given string using the passed parameters.
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
	public void eraseCachedBookCover(String uuid) {
		CoversDbHelper db = getCoversDb();
		if (db != null) {
            db.eraseCachedBookCover(uuid);
        }
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
     * Call setCacheColorHint on a listview and trap IndexOutOfBoundsException. 
     * There is a bug in Android 2.2-2.3 (approx) that causes this call to throw 
     * exceptions *sometimes* (circumstances unclear):
     *     <a href="http://code.google.com/p/android/issues/detail?id=9775">...</a>
     * Ideally this code should use reflection to set it, or check android versions.
     *
     * @param lv        ListView to set
     * @param hint        Colour hint
     */
	@SuppressWarnings("unused")
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
				sb.append(Objects.requireNonNull(b.get(k)));
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
		Hashtable<Integer,View> vh = new Hashtable<>();
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
	 * Passed date components build a (partial) SQL format date string.
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

    /**
	 * Utility routine to get an author list from the intent extras
	 * 
	 * @param b		Bundle to read
	 * @return		List of authors
	 */
	@SuppressWarnings("unchecked")
	public static ArrayList<Author> getAuthorsFromBundle(Bundle b) {
		return (ArrayList<Author>) b.getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
	}

	/**
	 * Utility routine to get a series list from the intent extras
	 * 
	 * @param b		Bundle with series list
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
	 * @param b		Bundle containing list
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
                assert appInfo.signatures != null;
                for(Signature sig: appInfo.signatures) {
			        if (sig != null) {
	                    final MessageDigest sha1 = MessageDigest.getInstance("MD5");
	                    final byte[] publicKey = sha1.digest(sig.toByteArray());
	                    // Turn the hex bytes into a more traditional MD5 string representation.
	                    final StringBuilder hexString = new StringBuilder();
	                    boolean first = true;
                        for (byte b : publicKey) {
                            if (!first) {
                                hexString.append(":");
                            } else {
                                first = false;
                            }
                            String byteString = Integer.toHexString(0xFF & b);
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
				mSignedBy = "NO_PACKAGE";

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
		if (s == null || s.isEmpty())
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
            switch (s) {
                case "t":
                case "true":
                case "y":
                case "yes":
                    v = true;
                    break;
                case "f":
                case "false":
                case "n":
                case "no":
                    v = false;
                    break;
                default:
                    try {
                        int i = Integer.parseInt(s);
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
            assert o instanceof Long;
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
			Toast.makeText(context, R.string.alert_unexpected_error, Toast.LENGTH_LONG).show();
		}
    }
	
	/**
     * Linkify partial HTML. Linkify methods remove all spans before building links, this
     * method preserves them.
     * See: <a href="http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml">...</a>
     *
     * @param html            Partial HTML
     * @param linkifyMask    Linkify mask to use in Linkify.addLinks
     *
     * @return                Spannable with all links
     */
	public static Spannable linkifyHtml(String html, int linkifyMask) {
		// Get the spannable HTML
	    Spanned text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY);
	    // Save the span details for later restoration
	    URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

	    // Build an empty spannable then add the links
	    SpannableString buffer = new SpannableString(text);
		//
	    // If chrome is disabled, or out of date, or otherwise broken, Linkify.addLinks may produce
		// nasty errors. It's not critical, so we ignore and just return the buffer.
		//
		// NOTE: this crash only occurs if linkifying ADDRESSES (perhaps via ALL) on Android version
		// {@link android.os.Build.VERSION_CODES#O_MR1} or earlier. See:
		//    https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/text/util/Linkify.java
		//
	    try {
			Linkify.addLinks(buffer, linkifyMask);

			// Add back the HTML spannable
			for (URLSpan span : currentSpans) {
				int end = text.getSpanEnd(span);
				int start = text.getSpanStart(span);
				buffer.setSpan(span, start, end, 0);
			}
		} catch (Exception e) {
	    	Logger.logError(e, "Linkify failure, non-critical");
		}
	    return buffer;
	}

	public static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst);
             FileChannel inChannel = fis.getChannel();
             FileChannel outChannel = fos.getChannel()) {

            // The transfer operation remains the same.
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
	}


}

