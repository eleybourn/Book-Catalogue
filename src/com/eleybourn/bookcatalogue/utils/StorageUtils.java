package com.eleybourn.bookcatalogue.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
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

	private static final String EXPORT_FILE_BASE_NAME = "bookCatalogue";
	private static final String DATABASE_NAME = "book_catalogue";

	private static final String OLD_FILE_PATH = Environment.getExternalStorageDirectory() + "/bookCatalogue";
	private static final String BC_SHARED_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Book Catalogue/";
	private static final String BC_BACKUPS_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Book Catalogue/Backups/";
	//private static final String BC_COVERS_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Book Catalogue/Covers";
	private static final String ERRORLOG_FILE = BC_SHARED_PATH + "/error.log";

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
	public static final File getBCBackups() {
		File dir = new File(StorageUtils.BC_BACKUPS_PATH);
		dir.mkdirs();
		return dir;
	}

	/**
	 * Accessor
	 *
	 * @return
	 */
	public static final File getBCCache() {
		return BookCatalogueApp.context.getExternalCacheDir();
	}

	/**
	 * Accessor
	 *
	 * @return
	 */
	public static File getBCCovers() {
		File dir = new File(BookCatalogueApp.context.getExternalFilesDir(null), "covers");
		dir.mkdirs();
		return dir;
	}

	/**
	 * Accessor
	 *
	 * @return
	 */
	public static File getBCData() {
		return BookCatalogueApp.context.getFilesDir();
	}


	public static final File getBCShared() {
		File dir = new File(StorageUtils.BC_SHARED_PATH);
		dir.mkdirs();
		return dir;
	}

	/**
	 * Accessor
	 *
	 * @return
	 */
	public static final String getBcBackupsPath() {
		File dir = getBCBackups();
		dir.mkdirs();
		return dir.getAbsolutePath();
	}

	/**
	 * Accessor
	 *
	 * @return
	 */
	public static final String getBCCoversPath() {
		File dir = getBCCovers();
		dir.mkdirs();
		return dir.getAbsolutePath();
	}

	/**
	 * Accessor
	 *
	 * @return
	 */
	public static final String getBCSharedPath() {
		File dir = getBCShared();
		dir.mkdirs();
		return dir.getAbsolutePath();
	}

	/**
	 * Backup database file
	 * @throws Exception 
	 */
	public static void backupDbFile(SQLiteDatabase db, String suffix) {
		try {
			final String fileName = EXPORT_FILE_BASE_NAME + suffix;
			java.io.InputStream dbOrig = new java.io.FileInputStream(db.getPath());
			File dir = getBCBackups();
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

	///**
	// * Scan all mount points for '/bookCatalogue' directory and collect a list
	// * of all CSV files.
	// *
	// * @return
	// */
	//public static ArrayList<File> findExportFiles() {
	//	//StringBuilder info = new StringBuilder();
	//
	//	ArrayList<File> files = new ArrayList<File>();
	//	Pattern mountPointPat = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");
	//	BufferedReader in = null;
	//	// Make a filter for files ending in .csv
	//	FilenameFilter csvFilter = new FilenameFilter() {
	//		@Override
	//		public boolean accept(File dir, String filename) {
	//			final String fl = filename.toLowerCase();
	//			return (fl.endsWith(".csv"));
	//			//ENHANCE: Allow for other files? Backups? || fl.endsWith(".csv.bak"));
	//		}
	//	};
	//
	//	ArrayList<File> dirs = new ArrayList<File>();
	//
	//	//info.append("Getting mounted file systems\n");
	//	// Scan all mounted file systems
	//	try {
	//		in = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")),1024);
	//		String line = "";
	//		while ((line = in.readLine()) != null) {
	//			//info.append("   checking " + line + "\n");
	//			Matcher m = mountPointPat.matcher(line);
	//			// Get the mount point
	//			if (m.find()) {
	//				// See if it has a bookCatalogue directory
	//				File dir = new File(m.group(1).toString() + "/bookCatalogue");
	//				//info.append("       matched " + dir.getAbsolutePath() + "\n");
	//				dirs.add(dir);
	//			} else {
	//				//info.append("       NO match\n");
	//			}
	//		}
	//	} catch (IOException e) {
	//		Logger.logError(e, "Failed to open/scan/read /proc/mounts");
	//	} finally {
	//		if (in != null)
	//			try {
	//				in.close();
	//			} catch (Exception e) {};
	//	}
	//
	//	// Sometimes (Android 6?) the /proc/mount search seems to fail, so we revert to environment vars
	//	//info.append("Found " + dirs.size() + " directories\n");
	//	try {
	//		for(File f: new File[] {getBCBackups(), getBCShared()}) {
	//			if (f != null && f.exists()) {
	//				dirs.add(f);
	//			}
	//		}
	//	} catch (Exception e) {
	//		Logger.logError(e, "Failed to get external storage from environment variables");
	//	}
	//
	//	HashSet<String> paths = new HashSet<String>();
	//
	//	//info.append("Looking for files in directories\n");
	//	for(File dir: dirs) {
	//		try {
	//			if (dir.exists()) {
	//				// Scan for csv files
	//				File[] csvFiles = dir.listFiles(csvFilter);
	//				if (csvFiles != null) {
	//					//info.append("    found " + csvFiles.length + " in " + dir.getAbsolutePath() + "\n");
	//					for (File f : csvFiles) {
	//						System.out.println("Found: " + f.getAbsolutePath());
	//						final String cp = f.getCanonicalPath();
	//						if (paths.contains(cp)) {
	//							//info.append("        already present as " + cp + "\n");
	//						} else {
	//							files.add(f);
	//							paths.add(cp);
	//							//info.append("        added as " + cp + "\n");
	//						}
	//					}
	//				} else {
	//					//info.append("    null returned by listFiles() in " + dir.getAbsolutePath() + "\n");
	//				}
	//			} else {
	//				//info.append("    " + dir.getAbsolutePath() + " does not exist\n");
	//			}
	//		} catch (Exception e) {
	//			Logger.logError(e, "Failed to read directory " + dir.getAbsolutePath());
	//		}
	//	}
	//
	//	//Logger.logError(new RuntimeException("INFO"), info.toString());
	//
	//	// Sort descending based on modified date
	//	Collections.sort(files, new FileDateComparator(-1));
	//	return files;
	//}

	/**
	 * Check if the sdcard is writable
	 * 
	 * @return	success or failure
	 */
	static public boolean isSharedWritable() {
		/* Test write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
					getBCShared() + "/.nomedia"), UTF8), BUFFER_SIZE);
			out.write("");
			out.close();
			return true;
		} catch (IOException e) {
			return false;
		}		
	}

	private static String[] mPurgeableFilePrefixes = new String[]{StorageUtils.EXPORT_FILE_BASE_NAME + "DbUpgrade", StorageUtils.EXPORT_FILE_BASE_NAME + "DbExport", "error.log", "tmp"};
	private static String[] mDebugFilePrefixes = new String[]{StorageUtils.EXPORT_FILE_BASE_NAME + "DbUpgrade", StorageUtils.EXPORT_FILE_BASE_NAME + "DbExport", "error.log", "export.csv"};

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
		String tmpName = StorageUtils.EXPORT_FILE_BASE_NAME + "DbExport-tmp.db";
		dbHelper.backupDbFile(tmpName);
		File dbFile = new File(StorageUtils.getBCCache() + "/" + tmpName);
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
		ArrayList<File> files = new ArrayList<>();
		
		// Find all files of interest to send
		for(File dir: new File[] {getBCShared(), getBCBackups()}) {
			try {
				for (String name : dir.list()) {
					boolean send = false;
					for(String prefix : mDebugFilePrefixes)
						if (name.startsWith(prefix)) {
							send = true;
							break;
						}
					if (send)
						files.add(new File(dir, name));
				}

				// Build the attachment list
				for (File fileIn : files)
				{
					if (fileIn.exists() && fileIn.length() > 0) {
						//Uri u = Uri.fromFile(fileIn);
						Uri u = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider",
														   fileIn);
						uris.add(u);
					}
				}

				// We used to only send it if there are any files to send, but later versions added
				// useful debugging info. So now we always send.
				emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
				context.startActivity(Intent.createChooser(emailIntent, "Send mail..."));

			} catch (NullPointerException e) {
				Logger.logError(e);
				Toast.makeText(context, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
			}
		}
	}

	/**
	 * Cleanup any purgeable files.
	 */
	public static void cleanupFiles() {
		if (StorageUtils.isSharedWritable()) {
			for(File dir: new File[] {getBCShared(), getBCBackups()}) {
				for (String name : dir.list()) {
					boolean purge = false;
					for (String prefix : mPurgeableFilePrefixes)
						if (name.startsWith(prefix)) {
							purge = true;
							break;
						}
					if (purge)
						try {
							File file = new File(dir, name);
							file.delete();
						} catch (Exception e) {
						}
				}
			}
		}
	}

	/**
	 * Get the total size of purgeable files.
	 * @return	size, in bytes
	 */
	public static long cleanupFilesTotalSize() {
		if (!StorageUtils.isSharedWritable())
			return 0;

		long totalSize = 0;
		for(File dir: new File[] {getBCShared(), getBCBackups()}) {
			for (String name : dir.list()) {
				boolean purge = false;
				for (String prefix : mPurgeableFilePrefixes)
					if (name.startsWith(prefix)) {
						purge = true;
						break;
					}
				if (purge)
					try {
						File file = new File(dir, name);
						totalSize += file.length();
					} catch (Exception e) {
					}
			}
		}
        return totalSize;
	}

	public static class FileCopyStatus {
		public int total = 0;
		public int processed = 0;
		public int not_in_db = 0;
		public int duplicates = 0;
	}
	/**
	 * Old BC stored files in the 'shared storage' root in a directory called "bookCatalogue". As
	 * of Android Q theses are more conformant with the Android standard. Specifically:
	 *
	 * - Covers go into the 'Media/Book Catalogue" folder
	 * - Exports and backups go to the "Documents/Book Catalogue" folder
	 * - The 'covers' DB goes into the standard database location.
	 *
	 * This procedure ensures files from an old instance are moved to the correct locations.
	 */
	public static FileCopyStatus moveOldFilesToQLocations(DocumentFile old, SimpleTaskQueueProgressFragment fragment) {
		FileCopyStatus result = new FileCopyStatus();
		fragment.onProgress("Listing files...", 0);
		DocumentFile[] files = old.listFiles();

		result.total = files.length;
		fragment.setMax(files.length);

		fragment.onProgress("Copying files...", 0);
		ContentResolver resolver = BookCatalogueApp.context.getContentResolver();
		File backupDir = getBCBackups();
		File cacheDir = getBCCache();
		File coverDir = getBCCovers();
		File sharedDir = getBCShared();
		File toDir;
		String copyingMsg = BookCatalogueApp.getResourceString(R.string.copying_files);
		CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.context);
		db.open();
		for(DocumentFile file: files) {
			if (fragment.isCancelled()) {
				break;
			}
			result.processed++;
			fragment.onProgress(copyingMsg, result.processed);

			String name = file.getName().toLowerCase();
			boolean image_not_in_db = false; // Only set to true for image files.
			if (name.startsWith("tmp")) {
				toDir = cacheDir;
			} else if (name.endsWith(".png") || name.endsWith(".jpg") ) {
				toDir = coverDir;
				// Check if related file exists in database.
				String uuid = name.substring(0, name.length()-4);
				long book = db.getBookIdFromUuid(uuid);
				if (book == 0) {
					image_not_in_db = true;
				}
			} else if (name.endsWith(".bcbk") || name.endsWith(".csv") || name.endsWith(".db")) {
				toDir = backupDir;
			} else {
				toDir = sharedDir;
			}
			if (!image_not_in_db) {
				File dst = new File(toDir, file.getName());
				try {
					if (!dst.exists()) {
						InputStream in = resolver.openInputStream(file.getUri());
						if (in != null) {
							Utils.saveInputToFile(in, dst);
							in.close();
							// TODO: Decide if this is a good idea! --
							// file.delete();
						}
					} else {
						result.duplicates++;
					}
				} catch (Exception e) {
					Logger.logError(e, "Failed to copy old file");
				}
			} else {
				result.not_in_db++;
			}
		}
		return result;
	}

//	public static Uri getOldFilePathUri() {
//		File old = new File(OLD_FILE_PATH);
//		return Uri.fromFile(old);
//	}

	public static ArrayList<File> getExistingOldPaths() {
		ArrayList<File> list = new ArrayList<>();
		for(File dir: new File[]
				{
					Environment.getExternalStoragePublicDirectory(""),
					Environment.getExternalStorageDirectory(),
					Environment.getRootDirectory(),
				}) {
			File old = new File(dir,"bookCatalogue");
			if (old.exists()) {
				list.add(old);
			}
		}
		return list;
	}
}
