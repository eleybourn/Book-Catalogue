package com.eleybourn.bookcatalogue;

import android.content.Context;

import androidx.documentfile.provider.DocumentFile;

import com.eleybourn.bookcatalogue.backup.CsvImporter;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.utils.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Class to handle import in a separate thread.
 *
 * @author Philip Warner
 */
public class ImportThread extends ManagedTask {
    private final DocumentFile mFile;
    private final Context mContext;
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
    private CatalogueDBAdapter mDbHelper;

    public ImportThread(Context context, TaskManager manager, DocumentFile file) throws IOException {
        super(manager);
        mContext = context;
        mFile = file;
        // Changed getCanonicalPath to getAbsolutePath based on this bug in Android 2.1:
        //     http://code.google.com/p/android/issues/detail?id=4961
        //mCoversPath = StorageUtils.getBCCovers().getAbsolutePath();

        mDbHelper = new CatalogueDBAdapter(mContext);
        mDbHelper.open();
    }

    @Override
    protected void onThreadFinish() {
        cleanup();
    }

    @Override
    protected void onRun() {
        // Initialize
        //ArrayList<String> export = readFile(mFileSpec);

        CsvImporter importer = new CsvImporter();

        InputStream in = null;
        try {
            in = mContext.getContentResolver().openInputStream(mFile.getUri());
            importer.importBooks(mContext, in, mImportListener, Importer.IMPORT_ALL);
            if (isCancelled()) {
                doToast(getString(R.string.alert_cancelled));
            } else {
                doToast(getString(R.string.description_import_complete));
            }
        } catch (IOException e) {
            doToast(BookCatalogueApp.getRes().getString(R.string.alert_import_failed_is_location_correct));
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

    public static class ImportException extends RuntimeException {
        private static final long serialVersionUID = 1660687786319003483L;

        public ImportException(String s) {
            super(s);
        }
    }
}
