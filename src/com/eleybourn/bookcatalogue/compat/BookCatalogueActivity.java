package com.eleybourn.bookcatalogue.compat;

import android.Manifest.permission;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.text.Html;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment.OnMessageDialogResultListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils.FileCopyStatus;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Class introduced to reduce the future pain when we remove sherlock (once we no longer
 * support Android 2.x), and potentially to make it easier to support two versions.
 * <p>
 * This activity inherits from SherlockFragmentActivity which is just a subclass of
 * the compatibility library FragmentActivity which should be fairly compatible with
 * Activity in API 11+.
 * <p>
 *
 * @author pjw
 */
public abstract class BookCatalogueActivity extends AppCompatActivity implements OnMessageDialogResultListener {
    private static final int PERMISSIONS_RESULT = 666;
    private static final String ARG_TREE_URI = "TREE";
    private static final int ACTIVITY_IMPORT_OLD_FILES = -666;
    private static final int ACTIVITY_REALLY_IMPORT_OLD_FILES = -667;
    private static final String LEGACY_NAME = "bookCatalogue";
    public static final RequiredPermission[] mScannerPermissions = new RequiredPermission[]{
            new RequiredPermission(permission.CAMERA, R.string.perm_camera),
    };
    protected static RequiredPermission[] mMinimumPermissions;
    protected static final RequiredPermission[] mInternetPermissions = new RequiredPermission[]{
            new RequiredPermission(permission.INTERNET, R.string.perm_internet),
            new RequiredPermission(permission.ACCESS_NETWORK_STATE, R.string.perm_network_state),
            new RequiredPermission(permission.ACCESS_WIFI_STATE, R.string.perm_network_state),
    };

    static {
        // We don't actually need read/write for most purposes now.
        if (VERSION.SDK_INT <= 29) {
            mMinimumPermissions = new RequiredPermission[]{
                    new RequiredPermission(permission.READ_EXTERNAL_STORAGE, R.string.perm_read_ext),
                    new RequiredPermission(permission.WRITE_EXTERNAL_STORAGE, R.string.perm_write_ext),
            };
        } else {
            mMinimumPermissions = new RequiredPermission[0];
        }
    }

    protected BackupExportManager mBackupExportManager;
    protected BackupImportManager mBackupImportManager;
    protected DocumentFile mBackupFile;
    /**
     * Last locale used so; cached so we can check if it has genuinely changed
     */
    private Locale mLastLocale = BookCatalogueApp.getPreferredLocale();
    // --------------------------
    // OLD FILES STUFF
    //
    private ActivityResultLauncher<Uri> mOldFilesTreeLauncher = null;

    public static boolean checkPermissions(BookCatalogueActivity activity, boolean finishOnFail, ActivityResultLauncher<String[]> l, RequiredPermission[]... lists) {
        // List of permissions that we need, but don't have
        ArrayList<RequiredPermission> failed = new ArrayList<>();

        for (RequiredPermission[] permissions : lists) {
            for (RequiredPermission req : permissions) {
                if (!BookCatalogueApp.hasPermission(req.permission)) {
                    failed.add(req);
                }
            }
        }

        // If we have them all, exit
        if (failed.isEmpty())
            return true;

        // Now build a message and the list to request
        PackageManager pm = activity.getApplicationContext().getPackageManager();
        StringBuilder message = new StringBuilder();
        final String[] list = new String[failed.size()];
        int pos = 0;

        for (RequiredPermission req : failed) {
            list[pos++] = req.permission;
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, req.permission)) {
                PermissionInfo info = null;
                try {
                    info = pm.getPermissionInfo(req.permission, 0);
                } catch (NameNotFoundException ignored) {
                }
                String name = info == null ? req.permission : info.loadLabel(pm).toString();
                message.append("<p>");
                message.append("<b>");
                message.append(name);
                message.append(":</b>");
                message.append("</p>");
                message.append("<p>");

                message.append(BookCatalogueApp.getRes().getString(req.reason));
                message.append("</p>");
            }
        }

        if (message.length() > 0) {
            // Show reasons, rinse and repeat.
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.perm_required)
                    .setMessage(Html.fromHtml(BookCatalogueApp.getRes().getString(R.string.perm_intro) + message, Html.FROM_HTML_MODE_LEGACY))
                    .setPositiveButton(R.string.button_ok, (dialogInterface, i) -> {
                        if (l != null) {
                            l.launch(list);
                        } else {
                            ActivityCompat.requestPermissions(activity, list, PERMISSIONS_RESULT);
                        }
                    })
                    .setOnCancelListener(dialogInterface -> {
                        if (finishOnFail)
                            activity.finish();
                    })
                    .create()
                    .show();
        } else {
            // Ask for them
            if (l != null) {
                l.launch(list);
            } else {
                ActivityCompat.requestPermissions(activity, list, PERMISSIONS_RESULT);
            }
        }
        return false;
    }

    private static void handleOldFilesTreeCopyResult(DocumentFile f, FragmentManager fm) {
        if (f != null) {
            FragmentTask task = new FragmentTaskAbstract() {
                private boolean mOK = false;
                private FileCopyStatus mResult;

                @Override
                public void run(
                        SimpleTaskQueueProgressFragment fragment,
                        SimpleTaskContext taskContext) {
                    mResult = StorageUtils.moveOldFilesToQLocations(f, fragment);
                    mOK = true;
                }

                @Override
                public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
                    super.onFinish(fragment, exception);
                    if (exception != null) {
                        Logger.logError(exception, "Failed to import files");
                    }
                    fragment.setSuccess(mOK);
                    String msg_dup = BookCatalogueApp.getRes().getString(R.string.old_file_import_status_duplicates, mResult.duplicates);
                    String msg_not_book = BookCatalogueApp.getRes().getString(R.string.old_file_import_status_not_book, mResult.not_in_db);
                    String extra = "";
                    if (mResult.duplicates > 0) {
                        if (mResult.not_in_db > 0) {
                            extra = BookCatalogueApp.getRes().getString(R.string.fragment_a_and_b, msg_dup, msg_not_book);
                        } else {
                            extra = msg_dup;
                        }
                    } else {
                        if (mResult.not_in_db > 0) {
                            extra = msg_not_book;
                        }
                    }
                    if (!extra.isEmpty()) {
                        extra = BookCatalogueApp.getRes().getString(R.string.old_file_import_status_of_those, extra);
                    }
                    int baseId;
                    if (fragment.isCancelled()) {
                        baseId = R.string.old_file_import_cancelled;
                    } else {
                        baseId = R.string.old_file_import_complete;
                    }
                    String msg = BookCatalogueApp.getRes().getString(baseId) + "\n\n" + BookCatalogueApp.getRes().getString(R.string.old_file_import_stats, mResult.processed, mResult.total, extra);
                    MessageDialogFragment frag = MessageDialogFragment.newInstance(0, R.string.label_import_old_files, msg, R.string.button_ok, 0, 0);
                    frag.show(fm, null);
                }
            };
            SimpleTaskQueueProgressFragment.runTaskWithProgress(fm, R.string.copying_files, task, false, 0);
        }
    }

    protected abstract RequiredPermission[] getRequiredPermissions();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // If we are NOT the startup activity AND we need to move old files, then register launcher.
        if (!(this instanceof StartupActivity) && StartupActivity.isFileMoveRequired()) {
            registerOldFilesTreeCopyLauncher();
        }

        super.onCreate(savedInstanceState);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            // Show home, use logo (bigger) and show title
            bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
            // Don't display the 'back' decoration if we are at the top
            boolean atTop = this.isTaskRoot() || getIntent().getBooleanExtra("willBeTaskRoot", false);
            bar.setDisplayHomeAsUpEnabled(!atTop);
            //bar.setHomeAsUpIndicator(R.drawable.ic_launcher4);
            if (atTop)
                bar.setLogo(R.drawable.ic_launcher4);
            else
                bar.setLogo(0);
        }
        checkPermissions();
    }

    private void checkPermissions() {
        checkPermissions(this, true, null, mMinimumPermissions, getRequiredPermissions());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_RESULT) {
            // If request is cancelled, the result arrays are empty.
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
            // permission was granted, yay! Do the
            // contacts-related task you need to do.
            //} else {
            //	// permission denied, boo! Disable the
            //	// functionality that depends on this permission.
            //	this.finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Default handler for home icon
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * When resuming, check if locale has changed and reload activity if so.
     */
    @Override
    protected void onResume() {
        reloadIfLocaleChanged();
        super.onResume();
    }

    /**
     * Reload this activity if locale has changed.
     */
    public void reloadIfLocaleChanged() {
        Locale old = mLastLocale;
        Locale curr = BookCatalogueApp.getPreferredLocale();
        if ((curr != null && !curr.equals(old)) || (curr == null && old != null)) {
            mLastLocale = curr;
            //BookCatalogueApp.setLocale(this);
            Intent intent = getIntent();
            System.out.println("Restarting " + this.getClass().getSimpleName());
            finish();
            startActivity(intent);
        }
    }

    protected void registerOldFilesTreeCopyLauncher() {
        mOldFilesTreeLauncher = registerForActivityResult(
                new OpenDocumentTree(),
                result -> {
                    if (result != null) {
                        DocumentFile t = DocumentFile.fromTreeUri(BookCatalogueActivity.this, result);
                        DocumentFile f = null;
                        if (t != null) {
                            if (LEGACY_NAME.equalsIgnoreCase(t.getName())) {
                                f = t;
                            } else {
                                DocumentFile[] list = t.listFiles();
                                for (DocumentFile i : list) {
                                    if (LEGACY_NAME.equalsIgnoreCase(i.getName())) {
                                        f = i;
                                        break;
                                    }
                                }
                                if (f == null) {
                                    // We don't have the right directory, maybe.
                                    String msg = BookCatalogueApp.context.getString(R.string.selected_dir_wrong_name, LEGACY_NAME);
                                    MessageDialogFragment frag = MessageDialogFragment.newInstance(
                                            ACTIVITY_REALLY_IMPORT_OLD_FILES,
                                            R.string.label_import_old_files,
                                            msg,
                                            R.string.button_ok,
                                            R.string.button_cancel,
                                            0);
                                    frag.requireArguments().putString(ARG_TREE_URI, t.getUri().toString());
                                    frag.show(BookCatalogueActivity.this.getSupportFragmentManager(),
                                            null);
                                    return;
                                }
                            }
                        }
                        handleOldFilesTreeCopyResult(f, getSupportFragmentManager());
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!(this instanceof StartupActivity) && StartupActivity.isFileMoveRequired()) {
            // To avoid re-display on recreate of activity
            StartupActivity.setFileMoveRequired(false);
            String msg = this.getString(
                    R.string.alert_old_files_message,
                    getString(R.string.button_ok),
                    getString(R.string.button_cancel),
                    getString(R.string.title_settings),
                    getString(R.string.label_import_old_files));
            MessageDialogFragment frag = MessageDialogFragment.newInstance(
                    ACTIVITY_IMPORT_OLD_FILES,
                    R.string.label_import_old_files,
                    msg,
                    R.string.button_ok,
                    R.string.button_cancel,
                    0);
            frag.show(getSupportFragmentManager(), null);
        }
    }

    @Override
    public void onMessageDialogResult(int dialogId, MessageDialogFragment dialog, int button) {
        if (dialogId == ACTIVITY_IMPORT_OLD_FILES) {
            if (button == Dialog.BUTTON_POSITIVE) {
                startImportOldFiles();
            }
            dialog.dismiss();
        } else if (dialogId == ACTIVITY_REALLY_IMPORT_OLD_FILES) {
            if (button == Dialog.BUTTON_POSITIVE) {
                Uri uri = Uri.parse(dialog.requireArguments().getString(ARG_TREE_URI));
                DocumentFile f = DocumentFile.fromTreeUri(BookCatalogueActivity.this, uri);
                handleOldFilesTreeCopyResult(f, getSupportFragmentManager());
            } else {
                mOldFilesTreeLauncher.launch(null);
            }
            dialog.dismiss();
        }
    }

    protected void startImportOldFiles() {
        mOldFilesTreeLauncher.launch(null);
    }

    // ========================
    // BACKUP EXPORT/IMPORT MANAGERS
    //
    protected void registerBackupExportPickerLauncher(@SuppressWarnings("SameParameterValue") int id) {
        mBackupExportManager = new BackupExportManager(id, this);
    }

    protected void registerBackupImportPickerLauncher() {
        mBackupImportManager = new BackupImportManager(this);
    }

    /**
     * Call the output picker.
     */
    protected void launchBackupExport() {
        mBackupExportManager.start();
    }

    protected void launchBackupImport() {
        mBackupImportManager.start();
    }

    public static class RequiredPermission {
        final String permission;
        final int reason;

        public RequiredPermission(String permission, int reason) {
            this.permission = permission;
            this.reason = reason;
        }
    }
}
