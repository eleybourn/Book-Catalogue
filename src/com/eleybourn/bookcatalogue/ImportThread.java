package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.backup.CsvImporter;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.utils.Logger;

import java.io.IOException;
import java.io.InputStream;

import androidx.documentfile.provider.DocumentFile;

/**
 * Class to handle import in a separate thread.
 *
 * @author Philip Warner
 */
public class ImportThread extends ManagedTask {
	public static String UTF8 = "utf8";

	private final DocumentFile mFile;
	private CatalogueDBAdapter mDbHelper;
	//private LocalCoverFinder mCoverFinder;
	
	public static class ImportException extends RuntimeException {
		private static final long serialVersionUID = 1660687786319003483L;

		public ImportException(String s) {
			super(s);
		}
	}

	public ImportThread(TaskManager manager, DocumentFile file) throws IOException {
		super(manager);
		mFile = file;
		// Changed getCanonicalPath to getAbsolutePath based on this bug in Android 2.1:
		//     http://code.google.com/p/android/issues/detail?id=4961
		//mCoversPath = StorageUtils.getBCCovers().getAbsolutePath();

		mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
		mDbHelper.open();
	}

	@Override
	protected void onThreadFinish() {
		cleanup();
	}

	private final Importer.OnImporterListener mImportListener = new Importer.OnImporterListener() {

		@Override
		public void onProgress(String message, int position) {
			if (position > 0) {
				mManager.doProgress(ImportThread.this, message, position);
			} else {
				mManager.doProgress(message);
			}
		}

		@Override
		public boolean isCancelled() {
			return ImportThread.this.isCancelled();
		}

		@Override
		public void setMax(int max) {
			mManager.setMax(ImportThread.this, max);
		}
	};

	@Override
	protected void onRun() {
		// Initialize
		//ArrayList<String> export = readFile(mFileSpec);
		
		CsvImporter importer = new CsvImporter();
		
		InputStream in = null;
		try {
			in = BookCatalogueApp.context.getContentResolver().openInputStream(mFile.getUri());
			importer.importBooks(in, mImportListener, Importer.IMPORT_ALL);
			if (isCancelled()) {
				doToast(getString(R.string.cancelled));
			} else {
				doToast(getString(R.string.import_complete));
			}
		} catch (IOException e) {
			doToast(BookCatalogueApp.getResourceString(R.string.import_failed_is_location_correct));
			Logger.logError(e);
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					Logger.logError(e);
				}
		}

	}

	/**
	 * Cleanup any DB connection etc after main task has run.
	 */
	private void cleanup() {
		if (mDbHelper != null) {
			mDbHelper.close();
			mDbHelper = null;
		}		
	}

	@Override
	protected void finalize() throws Throwable {
		cleanup();
		super.finalize();
	}
}
