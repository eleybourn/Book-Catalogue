package com.eleybourn.bookcatalogue.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.scanner.ZxingScanner;
import com.eleybourn.bookcatalogue.scanner.pic2shop.Scan;

/**
 * Class to wrap common storage related funcions.
 * 
 * @author Philip Warner
 */
public class StorageUtils {
	private static String UTF8 = "utf8";
	private static int BUFFER_SIZE = 8192;

	private static final String LOCATION = "bookCatalogue";
	private static final String DATABASE_NAME = "book_catalogue";

	private static final String EXTERNAL_FILE_PATH = Environment.getExternalStorageDirectory() + "/" + LOCATION;
	private static final String ERRORLOG_FILE = EXTERNAL_FILE_PATH + "/error.log";

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public static String getErrorLog() {
		return ERRORLOG_FILE;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public static String getDatabaseName() {
		return DATABASE_NAME;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public static final File getSharedStorage() {
		File dir = new File(StorageUtils.EXTERNAL_FILE_PATH);
		dir.mkdir();
		return dir;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public static final String getSharedStoragePath() {
		File dir = new File(StorageUtils.EXTERNAL_FILE_PATH);
		dir.mkdir();
		return dir.getAbsolutePath();
	}

	/**
	 * Backup database file
	 * @throws Exception 
	 */
	public static void backupDbFile(SQLiteDatabase db, String suffix) {
		try {
			final String fileName = LOCATION + suffix;
			java.io.InputStream dbOrig = new java.io.FileInputStream(db.getPath());
			File dir = getSharedStorage();
			// Path to the external backup
			String fullFilename = dir.getPath() + "/" + fileName;
			//check if it exists
			File existing = new File(fullFilename);
			if (existing.exists()) {
				String backupFilename = dir.getPath() + "/" + fileName + ".bak";
				File backup = new File(backupFilename);
				existing.renameTo(backup);
			}
			java.io.OutputStream dbCopy = new java.io.FileOutputStream(fullFilename);
			
			byte[] buffer = new byte[1024];
			int length;
			while ((length = dbOrig.read(buffer))>0) {
				dbCopy.write(buffer, 0, length);
			}
			
			dbCopy.flush();
			dbCopy.close();
			dbOrig.close();
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * Make sure the external shared directory exists
	 */
	public static void initSharedDirectory() {
		new File(StorageUtils.EXTERNAL_FILE_PATH + "/").mkdirs();
		try {
			new File(StorageUtils.EXTERNAL_FILE_PATH + "/.nomedia").createNewFile();
		} catch (IOException e) {
			Logger.logError(e);
		}		
	}

	/**
	 * Compare two files based on date. Used for sorting file list by date.
	 * 
	 * @author Philip Warner
	 */
	public static class FileDateComparator implements Comparator<File> {
		/** Ascending is >= 0, Descenting is < 0. */
		private int mDirection;
		/**
		 * Constructor
		 */
		public FileDateComparator(int direction) {
			mDirection = direction < 0? -1 : 1;
		}
		/**
		 * Compare based on modified date
		 */
		@Override
		public int compare(File lhs, File rhs) {
			final long l = lhs.lastModified();
			final long r = rhs.lastModified();
			if (l < r)
				return -mDirection;
			else if (l > r)
				return mDirection;
			else
				return 0;
		}		
	}

	/**
	 * Scan all mount points for '/bookCatalogue' directory and collect a list
	 * of all CSV files.
	 * 
	 * @return
	 */
	public static ArrayList<File> findExportFiles() {
		ArrayList<File> files = new ArrayList<File>();
		Pattern mountPointPat = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");
		BufferedReader in = null;
		// Make a filter for files ending in .csv
		FilenameFilter csvFilter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				final String fl = filename.toLowerCase();
				return (fl.endsWith(".csv"));
				//ENHANCE: Allow for other files? Backups? || fl.endsWith(".csv.bak"));
			}
		};

		// Scan all mounted file systems
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")),1024);
			String line = "";
			while ((line = in.readLine()) != null) {
				Matcher m = mountPointPat.matcher(line);
				// Get the mount point
				if (m.find()) {
					// See if it has a bookCatalogue directory
					File dir = new File(m.group(1).toString() + "/bookCatalogue");
					if (dir.exists()) {
						// Scan for csv files
						File[] csvFiles = dir.listFiles(csvFilter);
						if (csvFiles != null) {
							for(File f: csvFiles) {
								System.out.println("Found: " + f.getAbsolutePath());
								files.add(f);								
							}
						}
					}
				}
			}
		} catch (IOException e) {
			return files;
		} finally {
			if (in != null)
				try {
					in.close();					
				} catch (Exception e) {};
		}
		// Sort descending based on modified date
		Collections.sort(files, new FileDateComparator(-1));
		return files;
	}

	/**
	 * Check if the sdcard is writable
	 * 
	 * @return	success or failure
	 */
	static public boolean sdCardWritable() {
		/* Test write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(EXTERNAL_FILE_PATH + "/.nomedia"), UTF8), BUFFER_SIZE);
			out.write("");
			out.close();
			return true;
		} catch (IOException e) {
			return false;
		}		
	}

	private static String[] mPurgeableFilePrefixes = new String[]{StorageUtils.LOCATION + "DbUpgrade", StorageUtils.LOCATION + "DbExport", "error.log", "tmp"};
	private static String[] mDebugFilePrefixes = new String[]{StorageUtils.LOCATION + "DbUpgrade", StorageUtils.LOCATION + "DbExport", "error.log", "export.csv"};

	/**
	 * Collect and send com.eleybourn.bookcatalogue.debug info to a support email address. 
	 * 
	 * THIS SHOULD NOT BE A PUBLICALLY AVAILABLE MAINING LIST OR FORUM!
	 * 
	 * @param context
	 * @param dbHelper
	 */
	public static void sendDebugInfo(Context context, CatalogueDBAdapter dbHelper) {
		// Create a temp DB copy.
		String tmpName = StorageUtils.LOCATION + "DbExport-tmp.db";
		dbHelper.backupDbFile(tmpName);
		File dbFile = new File(StorageUtils.EXTERNAL_FILE_PATH + "/" + tmpName);
		dbFile.deleteOnExit();
		// setup the mail message
		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, context.getString(R.string.debug_email).split(";"));
		String subject = "[" + context.getString(R.string.app_name) + "] " + context.getString(R.string.debug_subject);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
		String message = "";

        try {
        	// Get app info
            PackageManager manager = context.getPackageManager(); 
			PackageInfo appInfo = manager.getPackageInfo( context.getPackageName(), 0);
			message += "App: " + appInfo.packageName + "\n";
			message += "Version: " + appInfo.versionName + " (" + appInfo.versionCode + ")\n";
		} catch (Exception e1) {
			// Not much we can do inside error logger...
		}
        
        
        message += "SDK: " + Build.VERSION.SDK + " (" + Build.VERSION.SDK_INT + " " + Build.TAGS + ")\n";
        message += "Phone Model: " + Build.MODEL + "\n";
        message += "Phone Manufacturer: " + Build.MANUFACTURER + "\n";
        message += "Phone Device: " + Build.DEVICE + "\n";
        message += "Phone Product: " + Build.PRODUCT + "\n";
        message += "Phone Brand: " + Build.BRAND + "\n";
        message += "Phone ID: " + Build.ID + "\n";

        message += "Signed-By: " + Utils.signedBy(context) + "\n";

		message += "\nHistory:\n" + Tracker.getEventsInfo() + "\n";

		// Scanners installed
		try {
	        message += "Pref. Scanner: " + BookCatalogueApp.getAppPreferences().getInt( ScannerManager.PREF_PREFERRED_SCANNER, -1) + "\n";
	        String[] scanners = new String[] { ZxingScanner.ACTION, Scan.ACTION, Scan.Pro.ACTION};
	        for(String scanner:  scanners) {
	            message += "Scanner [" + scanner + "]:\n";
	            final Intent mainIntent = new Intent(scanner, null);
	            final List<ResolveInfo> resolved = context.getPackageManager().queryIntentActivities( mainIntent, 0); 
	            if (resolved.size() > 0) {
		            for(ResolveInfo r: resolved) {
		            	message += "    ";
		            	// Could be activity or service...
		            	if (r.activityInfo != null) {
		            		message += r.activityInfo.packageName;
		            	} else if (r.serviceInfo != null) {
		            		message += r.serviceInfo.packageName;
		            	} else {
		            		message += "UNKNOWN";
		            	}
		                message += " (priority " + r.priority + ", preference " + r.preferredOrder + ", match " + r.match + ", default=" + r.isDefault + ")\n";
		            }
	            } else {
            		message += "    No packages found\n";
	            }
	        }			
		} catch (Exception e) {
			// Don't lose the other debug info if scanner data dies for some reason
	        message += "Scanner failure: " + e.getMessage() + "\n";
		}
		message += "\n";

        message += "Details:\n\n" + context.getString(R.string.debug_body).toUpperCase() + "\n\n";

		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, message);
		//has to be an ArrayList
		ArrayList<Uri> uris = new ArrayList<Uri>();
		//convert from paths to Android friendly Parcelable Uri's
		ArrayList<String> files = new ArrayList<String>();
		
		// Find all files of interest to send
		File dir = new File(StorageUtils.EXTERNAL_FILE_PATH);
		try {
			for (String name : dir.list()) {
				boolean send = false;
				for(String prefix : mDebugFilePrefixes)
					if (name.startsWith(prefix)) {
						send = true;
						break;
					}
				if (send)
					files.add(name);
			}
			
			// Build the attachment list
			for (String file : files)
			{
				File fileIn = new File(StorageUtils.EXTERNAL_FILE_PATH + "/" + file);
				if (fileIn.exists() && fileIn.length() > 0) {
					Uri u = Uri.fromFile(fileIn);
					uris.add(u);
				}
			}
			// Send it, if there are any files to send.
			if (uris.size() == 0) {
				Toast.makeText(context, R.string.no_debug_info, Toast.LENGTH_LONG).show();
			} else {
				emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
				context.startActivity(Intent.createChooser(emailIntent, "Send mail..."));        	
			}
		} catch (NullPointerException e) {
			Logger.logError(e);
			Toast.makeText(context, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Cleanup any purgeable files.
	 */
	public static void cleanupFiles() {
		if (StorageUtils.sdCardWritable()) {
	        File dir = new File(StorageUtils.EXTERNAL_FILE_PATH);
	        for (String name : dir.list()) {
	        	boolean purge = false;
	        	for(String prefix : mPurgeableFilePrefixes)
	        		if (name.startsWith(prefix)) {
	        			purge = true;
	        			break;
	        		}
	        	if (purge)
		        	try {
		        		File file = new File(StorageUtils.EXTERNAL_FILE_PATH + "/" + name);
			        	file.delete();
		        	} catch (Exception e) {        		
		        	}
	        }
		}
	}

	/**
	 * Get the total size of purgeable files.
	 * @return	size, in bytes
	 */
	public static long cleanupFilesTotalSize() {
		if (!StorageUtils.sdCardWritable())
			return 0;

		long totalSize = 0;

		File dir = new File(StorageUtils.EXTERNAL_FILE_PATH);
        for (String name : dir.list()) {
        	boolean purge = false;
        	for(String prefix : mPurgeableFilePrefixes)
        		if (name.startsWith(prefix)) {
        			purge = true;
        			break;
        		}
        	if (purge)
	        	try {
	        		File file = new File(StorageUtils.EXTERNAL_FILE_PATH + "/" + name);
	        		totalSize += file.length();
	        	} catch (Exception e) {        		
	        	}
        }
        return totalSize;
	}
}
