package com.eleybourn.bookcatalogue.compat;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.ID;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupManager.BackupListener;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.Date;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentManager;

public class BackupExportManager
		implements OnExportTypeSelectionDialogResultListener,
				   BackupListener
{
	private ActivityResultLauncher<String> mBackupExportPickerLauncher = null;
	private DocumentFile mBackupFile = null;
	private ExportSettings mSettings = null;

	private final int mId;

	public BackupExportManager(int id, BookCatalogueActivity activity) {
		mId = id;
		register(activity);
	}

	private void register(BookCatalogueActivity activity) {
		//BookCatalogueActivity activity = BookCatalogueActivity.this;

		if (!(activity instanceof OnExportTypeSelectionDialogResultListener)) {
			throw new RuntimeException(this.getClass()
										   .getSimpleName() + " must implement OnExportTypeSelectionDialogResultListener");
		}
		final FragmentManager fm = activity.getSupportFragmentManager();

		mBackupExportPickerLauncher = activity.registerForActivityResult(
				new CreateDocument("*/*"),
				result -> {
					if (result != null) {
						DocumentFile f = DocumentFile.fromSingleUri(activity, result);
						if (f != null) {
							mBackupFile = f;
							ExportTypeSelectionDialogFragment frag = ExportTypeSelectionDialogFragment.newInstance(
									mId,
									f);
							frag.show(fm, null);
						}
					}
				}
		);
	}

	public void start() {
		final String sqlDate = Utils.toLocalSqlDateOnly(new Date());
		final String name = "BookCatalogue-" + sqlDate.replace(" ", "-").replace(":", "") + ".bcbk";
		mBackupExportPickerLauncher.launch(name);
	}

	@Override
	public void onExportTypeSelectionDialogResult(
			int dialogId,
			BookCatalogueDialogFragment dialog,
			ExportSettings settings)
	{
		mSettings = settings;
		BookCatalogueActivity activity = (BookCatalogueActivity) dialog.getActivity();
		if (settings.options == Exporter.EXPORT_ALL) {
			mBackupFile = BackupManager.backupCatalogue(activity,
														settings.file,
														ID.TASK_ID_SAVE,
														Exporter.EXPORT_ALL,
														null,
														this);
		} else if (settings.options != 0) {
			if (settings.dateFrom == null) {
				String lastBackup = BookCatalogueApp.getAppPreferences().getString(
						BookCataloguePreferences.PREF_LAST_BACKUP_DATE, null);
				if (lastBackup != null && !lastBackup.equals("")) {
					try {
						settings.dateFrom = Utils.parseDate(lastBackup);
					} catch (Exception e) {
						// Just ignore; backup everything
						Logger.logError(e);
						settings.dateFrom = null;
					}
				} else {
					settings.dateFrom = null;
				}
			}
			mBackupFile = BackupManager.backupCatalogue(activity,
														settings.file,
														ID.TASK_ID_SAVE,
														settings.options,
														settings.dateFrom,
														this);
		}
	}

	@Override
	public void onFinish(
			SimpleTaskQueueProgressFragment fragment,
			int taskId,
			FragmentTask task)
	{
		BookCatalogueActivity activity = (BookCatalogueActivity) fragment.requireActivity();
		String msg;
		if (fragment.isCancelled()) {
			msg = activity.getString(R.string.cancelled);
		} else if (!fragment.getSuccess()) {
			msg = activity.getString(R.string.backup_failed)
					+ " " + activity.getString(R.string.please_check_sd_writable)
					+ "\n\n" + activity.getString(R.string.if_the_problem_persists);
		} else {
			msg = activity.getString(R.string.archive_complete_details,
									 activity.getString(R.string.selected_thing),
									 mBackupFile.getName(),
									 Utils.formatFileSize(mBackupFile.length()));
		}
		// Show a helpful message
		MessageDialogFragment frag = MessageDialogFragment.newInstance(ID.MSG_ID_BACKUP_EXPORT_COMPLETE,
																	   R.string.label_backup_to_archive,
																	   msg,
																	   R.string.button_ok,
																	   0,
																	   0);
		frag.show(activity.getSupportFragmentManager(), null);
	}
}
