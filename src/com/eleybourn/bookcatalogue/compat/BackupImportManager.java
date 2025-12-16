package com.eleybourn.bookcatalogue.compat;

import android.widget.Toast;

import com.eleybourn.bookcatalogue.ID;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupManager.BackupListener;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentManager;

public class BackupImportManager
		implements OnImportTypeSelectionDialogResultListener,
				   BackupListener
{
	ActivityResultLauncher<String[]> mBackupImportPickerLauncher;
	private DocumentFile mBackupFile = null;
	private ExportSettings mSettings = null;

	public BackupImportManager(BookCatalogueActivity activity) {
		register(activity);
	}

	private void register(BookCatalogueActivity activity) {

		if (!(activity instanceof OnImportTypeSelectionDialogResultListener)) {
			throw new RuntimeException(this.getClass()
										   .getSimpleName() + " must implement OnImportTypeSelectionDialogResultListener");
		}
		final FragmentManager fm = activity.getSupportFragmentManager();

		mBackupImportPickerLauncher = activity.registerForActivityResult(
				new OpenDocument(),
				result -> {
					if (result != null) {
						DocumentFile f = DocumentFile.fromSingleUri(activity, result);
						if (f != null) {
							TarBackupContainer bkp = new TarBackupContainer(f);
							// Each format should provide a validator of some kind
							if (!bkp.isValid()) {
								Toast.makeText(activity,
											   R.string.invalid_backup_file,
											   Toast.LENGTH_LONG).show();
								start();
								return;
							}
							mBackupFile = f;
							ImportTypeSelectionDialogFragment frag = ImportTypeSelectionDialogFragment.newInstance(ID.DIALOG_OPEN_IMPORT_TYPE, f);
							frag.show(activity.getSupportFragmentManager(), null);
						}
					}
				}
		);
	}

	public void start() {
		mBackupImportPickerLauncher.launch(new String[] {"*/*"});
	}

	@Override
	public void onImportTypeSelectionDialogResult(int dialogId, ImportTypeSelectionDialogFragment dialog, int rowId, DocumentFile file) {
		BookCatalogueActivity activity = (BookCatalogueActivity) dialog.requireActivity();
		if (rowId == R.id.all_books_row) {
			BackupManager.restoreCatalogue(activity, file, ID.TASK_ID_OPEN, Importer.IMPORT_ALL, this);
		} else if (rowId == R.id.new_and_changed_books_row) {
			BackupManager.restoreCatalogue(activity, file, ID.TASK_ID_OPEN, Importer.IMPORT_NEW_OR_UPDATED, this);
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
			msg = activity.getString(R.string.import_failed)
					+ " " + activity.getString(R.string.please_check_sd_readable)
					+ "\n\n" + activity.getString(R.string.if_the_problem_persists);

		} else {
			msg = activity.getString(R.string.import_complete);
		}
		MessageDialogFragment frag = MessageDialogFragment.newInstance(
				ID.MSG_ID_BACKUP_IMPORT_COMPLETE,
				R.string.label_import_from_archive,
				msg,
				R.string.ok, 0, 0);
		frag.show(activity.getSupportFragmentManager(), null);
	}
}
