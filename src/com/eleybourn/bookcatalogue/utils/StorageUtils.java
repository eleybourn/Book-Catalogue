package com.eleybourn.bookcatalogue.utils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 * Class to wrap common storage related functions.
 * 
 * @author Philip Warner
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class StorageUtils {

    private static final String EXPORT_FILE_BASE_NAME = "bookCatalogue";
	private static final String DATABASE_NAME = "book_catalogue";

    private static final String BC_SHARED_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Book Catalogue/";
	private static final String BC_BACKUPS_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Book Catalogue/Backups/";
	//private static final String BC_COVERS_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Book Catalogue/Covers";
	private static final String ERROR_LOG_FILE = BC_SHARED_PATH + "/error.log";

	/**
	 * Accessor
	 */
	public static String getErrorLog() {
		return ERROR_LOG_FILE;
	}

	/**
	 * Accessor
	 */
	public static String getDatabaseName() {
		return DATABASE_NAME;
	}

	/**
	 * Accessor
	 */
	public static File getBCBackups() {
		File dir = new File(StorageUtils.BC_BACKUPS_PATH);
		dir.mkdirs();
		return dir;
	}

	/**
	 * Accessor
	 */
	public static File getBCCache() {
		return BookCatalogueApp.context.getExternalCacheDir();
	}

	/**
	 * Accessor
	 */
	public static File getBCCovers() {
		File dir = new File(BookCatalogueApp.context.getExternalFilesDir(null), "covers");
		dir.mkdirs();
		return dir;
	}

	/**
	 * Accessor
	 */
	public static File getBCData() {
		return BookCatalogueApp.context.getFilesDir();
	}


	private static File getBCShared() {
		File dir = new File(StorageUtils.BC_SHARED_PATH);
		dir.mkdirs();
		return dir;
	}

    /**
	 * Accessor
	 */
    public static String getBCCoversPath() {
		File dir = getBCCovers();
		dir.mkdirs();
		return dir.getAbsolutePath();
	}

	/**
	 * Backup database file
	 */
	public static File backupDbFile(SQLiteDatabase db, String suffix) {
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
			return existing;
		} catch (Exception e) {
			Logger.logError(e);
			return null;
		}
	}

    /**
	 * Check if the shared storage dir is writable
	 * 
	 * @return	success or failure
	 */
	static public boolean isSharedWritable() {
		/* Test write to the BC shared dir */
		try {
			return getBCShared().canWrite();
		} catch (Exception e) {
			return false;
		}		
	}

	private static final String[] mPurgeableFilePrefixes = new String[]{
			StorageUtils.EXPORT_FILE_BASE_NAME + "DbUpgrade",
			StorageUtils.EXPORT_FILE_BASE_NAME + StorageUtils.EXPORT_FILE_BASE_NAME + "DbUpgrade", // Bug in prior version meant duplicated base name!
			StorageUtils.EXPORT_FILE_BASE_NAME + "DbExport",
			StorageUtils.EXPORT_FILE_BASE_NAME + StorageUtils.EXPORT_FILE_BASE_NAME + "DbExport", // Bug in prior version meant duplicated base name!
			"error.log", "tmp"};
	private static final String[] mDebugFilePrefixes = new String[]{StorageUtils.EXPORT_FILE_BASE_NAME + "DbUpgrade", StorageUtils.EXPORT_FILE_BASE_NAME + "DbExport", "error.log", "export.csv"};

	/**
	 * Collect and send com.eleybourn.bookcatalogue.debug info to a support email address. 
	 * THIS SHOULD NOT BE A PUBLICLY AVAILABLE MAILING LIST OR FORUM!
	 */
	public static void sendDebugInfo(Context context, CatalogueDBAdapter dbHelper) {
		// Create a temp DB copy.
		File dbFile = dbHelper.backupDbFile("DbExport-tmp.db");
		if (dbFile != null) {
			dbFile.deleteOnExit();
		}
		// setup the mail message
		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, context.getString(R.string.debug_email).split(";"));
		String subject = "[" + context.getString(R.string.app_name) + "] " + context.getString(R.string.debug_subject);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
		StringBuilder message = new StringBuilder();

        try {
        	// Get app info
            PackageManager manager = context.getPackageManager(); 
			PackageInfo appInfo = manager.getPackageInfo( context.getPackageName(), 0);
			message.append("App: ").append(appInfo.packageName).append("\n");
			message.append("Version: ").append(appInfo.versionName).append(" (").append(appInfo.versionCode).append(")\n");
		} catch (Exception e1) {
			// Not much we can do inside error logger...
		}
        
        
        message.append("SDK: ").append(Build.VERSION.SDK_INT_FULL).append(" (").append(Build.VERSION.SDK_INT).append(" ").append(Build.TAGS).append(")\n");
        message.append("Phone Model: ").append(Build.MODEL).append("\n");
        message.append("Phone Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        message.append("Phone Device: ").append(Build.DEVICE).append("\n");
        message.append("Phone Product: ").append(Build.PRODUCT).append("\n");
        message.append("Phone Brand: ").append(Build.BRAND).append("\n");
        message.append("Phone ID: ").append(Build.ID).append("\n");

        message.append("Signed-By: ").append(Utils.signedBy(context)).append("\n");

		message.append("\nHistory:\n").append(Tracker.getEventsInfo()).append("\n");

		// Scanners installed
		try {
	        message.append("Pref. Scanner: ").append(BookCatalogueApp.getAppPreferences().getInt(ScannerManager.PREF_PREFERRED_SCANNER, -1)).append("\n");
	        String[] scanners = new String[] { ZxingScanner.ACTION, Scan.ACTION, Scan.Pro.ACTION};
	        for(String scanner:  scanners) {
	            message.append("Scanner [").append(scanner).append("]:\n");
	            final Intent mainIntent = new Intent(scanner, null);
	            final List<ResolveInfo> resolved = context.getPackageManager().queryIntentActivities( mainIntent, 0);
	            if (!resolved.isEmpty()) {
		            for(ResolveInfo r: resolved) {
		            	message.append("    ");
		            	// Could be activity or service...
		            	if (r.activityInfo != null) {
		            		message.append(r.activityInfo.packageName);
		            	} else if (r.serviceInfo != null) {
		            		message.append(r.serviceInfo.packageName);
		            	} else {
		            		message.append("UNKNOWN");
		            	}
		                message.append(" (priority ").append(r.priority).append(", preference ").append(r.preferredOrder).append(", match ").append(r.match).append(", default=").append(r.isDefault).append(")\n");
		            }
	            } else {
            		message.append("    No packages found\n");
	            }
	        }			
		} catch (Exception e) {
			// Don't lose the other debug info if scanner data dies for some reason
	        message.append("Scanner failure: ").append(e.getMessage()).append("\n");
		}
		message.append("\n");

        message.append("Details:\n\n").append(context.getString(R.string.debug_body).toUpperCase()).append("\n\n");

		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, message.toString());
		//has to be an ArrayList
		ArrayList<Uri> uris = new ArrayList<>();
		//convert from paths to Android friendly Parcelable Uri's
		ArrayList<File> files = new ArrayList<>();
		
		// Find all files of interest to send
		for(File dir: new File[] {getBCShared(), getBCBackups()}) {
			try {
				for (String name : Objects.requireNonNull(dir.list())) {
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
				Toast.makeText(context, R.string.alert_export_failed_sdcard, Toast.LENGTH_LONG).show();
			}
		}
	}

	/**
	 * Cleanup any purgeable files.
	 */
	public static void cleanupFiles() {
		if (StorageUtils.isSharedWritable()) {
			for(File dir: new File[] {getBCShared(), getBCBackups()}) {
				for (String name : Objects.requireNonNull(dir.list())) {
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
						} catch (Exception ignored) {
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
			for (String name : Objects.requireNonNull(dir.list())) {
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
					} catch (Exception ignored) {
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
	 * - Covers go into the 'Media/Book Catalogue" folder
	 * - Exports and backups go to the "Documents/Book Catalogue" folder
	 * - The 'covers' DB goes into the standard database location.
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

			String name = Objects.requireNonNull(file.getName()).toLowerCase();
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

}
