package com.eleybourn.bookcatalogue;

import android.content.Context;

import com.eleybourn.bookcatalogue.backup.CsvExporter;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.utils.Logger;

import java.io.IOException;
import java.io.OutputStream;

import androidx.documentfile.provider.DocumentFile;

/**
 * Class to handle export in a separate thread.
 * 
 * @author Philip Warner
 */
public class ExportThread extends ManagedTask {
    // Changed the paths to non-static variable because if this code is called
    // while a phone sync is in progress, they will not be set correctly
    private final Context mContext;
    private static DocumentFile mFile;
	private CatalogueDBAdapter mDbHelper;

	public ExportThread(Context context, TaskManager ctx, DocumentFile file) {
		super(ctx);
        mContext = context;
		mDbHelper = new CatalogueDBAdapter(mContext);
		mDbHelper.open();
		mFile = file;
	}

	public DocumentFile getFile() {
		return mFile;
	}

	@Override
	protected void onThreadFinish() {
		cleanup();
	}
	
	private final Exporter.ExportListener mOnExportListener = new Exporter.ExportListener() {

		@Override
		public void onProgress(String message, int position) {
			if (position > 0) {
				mManager.doProgress(ExportThread.this, message, position);
			} else {
				mManager.doProgress(message);
			}
		}

		@Override
		public boolean isCancelled() {
			return ExportThread.this.isCancelled();
		}

		@Override
		public void setMax(int max) {
			mManager.setMax(ExportThread.this, max);
		}

	};

	@Override
	protected void onRun() {
		// We used to check for writability of shared storage here but since as of v6
		// we have a file selected via standard calls, writability of shared storage is
		// moot: The file could be local or cloud-backed, for example. The only useful
		// test here is whether we can open it for writing.
		try {
			OutputStream out = mContext.getContentResolver().openOutputStream(mFile.getUri());
			CsvExporter exporter = new CsvExporter();
			exporter.export(out, mOnExportListener, Exporter.EXPORT_ALL, null);
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignore) {
				}
			}
		} catch (IOException e) {
			Logger.logError(e);
			mManager.doToast(getString(R.string.alert_export_failed_sdcard));
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
