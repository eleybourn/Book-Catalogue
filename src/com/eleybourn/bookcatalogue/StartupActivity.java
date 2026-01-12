/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDoneException;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.booklist.AdminLibraryPreferences;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.compat.BookCatalogueDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment.OnMessageDialogResultListener;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Single Activity to be the 'Main' activity for the app. I does app-startup stuff which is initially
 * to start the 'real' main activity.
 * <p>
 * Note that calling the desired main activity first resulted in MainMenu's 'singleInstance' property
 * NOT being honoured. So we call MainMenu anyway, but set a flag in the Intent to indicate this is
 * a startup. This approach mostly works, but results in an apparent misordering of the activity
 * stack, which we can live with for now.
 * </p>
 *
 * @author Philip Warner
 */
public class StartupActivity
        extends BookCatalogueActivity
        implements OnMessageDialogResultListener,
        OnExportTypeSelectionDialogResultListener {
    private static final String TAG = "StartupActivity";
    /**
     * Flag to indicate FTS rebuild is required at startup
     */
    private static final String PREF_FTS_REBUILD_REQUIRED = TAG + ".FtsRebuildRequired";
    private static final String PREF_AUTHOR_SERIES_FIXUP_REQUIRED = TAG + ".FAuthorSeriesFixupRequired";

    private static final String STATE_OPENED = "state_opened";
    /**
     * Number of times the app has been started
     */
    private static final String PREF_START_COUNT = "Startup.StartCount";

    /**
     * Number of app startups between offers to backup
     */
    private static final int BACKUP_PROMPT_WAIT = 5;

    /**
     * Number of app startups between displaying the Amazon hint
     */
    private static final int AMAZON_PROMPT_WAIT = 7;
    public static WeakReference<StartupActivity> mStartupActivity = null;
    /**
     * Indicates the upgrade message has been shown
     */
    private static boolean mUpgradeMessageShown = false;
    /**
     * Flag set to true on first call
     */
    private static boolean mIsReallyStartup = true;
    /**
     * Flag indicating a StartupActivity has been created in this session
     */
    private static boolean mHasBeenCalled = false;
    /**
     * Flag indicating Amazon hint could be shown
     */
    private static boolean mShowAmazonHint = false;
    private static boolean mNeedMoveFiles = false;
    /**
     * Handler to post runnables to UI thread
     */
    private final Handler mHandler = new Handler();
    /**
     * Queue for executing startup tasks, if any
     */
    private SimpleTaskQueue mTaskQueue = null;
    /**
     * Progress Dialog for startup tasks
     */
    private Dialog mProgress = null;
    /**
     * Flag indicating THIS instance was really the startup instance
     */
    private boolean mWasReallyStartup = false;
    /**
     * Flag indicating an export is required after startup
     */
    private boolean mExportRequired = false;
    /**
     * UI thread
     */
    private Thread mUiThread;

    public static boolean isFileMoveRequired() {
        return mNeedMoveFiles;
    }

    public static void setFileMoveRequired(boolean required) {
        mNeedMoveFiles = required;
    }

    /**
     * Set the flag to indicate an FTS rebuild is required
     */
    public static void scheduleFtsRebuild() {
        BookCatalogueApp.getAppPreferences().setBoolean(PREF_FTS_REBUILD_REQUIRED, true);
    }

    /**
     * Set the flag to indicate an FTS rebuild is required
     */
    public static void scheduleAuthorSeriesFixup() {
        BookCatalogueApp.getAppPreferences().setBoolean(PREF_AUTHOR_SERIES_FIXUP_REQUIRED, true);
    }

    /**
     * Kludge to get a reference to the currently running StartupActivity, if defined.
     *
     * @return        Reference or null.
     */
    public static StartupActivity getActiveActivity() {
        if (mStartupActivity != null)
            return mStartupActivity.get();
        else
            return null;
    }

    public static boolean hasBeenCalled() {
        return mHasBeenCalled;
    }

    public static boolean getShowAmazonHint() {
        return mShowAmazonHint;
    }

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerBackupExportPickerLauncher(ID.DIALOG_OPEN_IMPORT_TYPE);
        super.onCreate(savedInstanceState);
        setTitle("");

        System.out.println("Startup isTaskRoot() = " + isTaskRoot());

        mHasBeenCalled = true;
        mUiThread = Thread.currentThread();

        // Create a progress dialog; we may not use it...but we need it to be created in the UI thread.
        mProgress = new Dialog(this);
        mProgress.setContentView(R.layout.progress_dialog);
        mProgress.setTitle(R.string.book_catalogue_startup);
        mProgress.setCancelable(true);
        mProgress.setOnCancelListener(dialog -> {
            // Cancelling the list cancels the activity.
            StartupActivity.this.finish();
        });
        updateProgress(R.string.starting_up);

        mWasReallyStartup = mIsReallyStartup;

        // If it's a real application startup...cleanup old stuff
        if (mWasReallyStartup) {
            mStartupActivity = new WeakReference<>(this);

            updateProgress("Starting");

            SimpleTaskQueue q = getQueue();

            // Get last version installed (may be zero for none).
            final int lastVersion = UpgradeMessageManager.getLastUpgradeVersion();

            // Determine if the last install was an upgrade or new version
            final boolean wasUpgrade = lastVersion > 0;

            // Update file structures for old versions
            if (lastVersion < 200 && wasUpgrade) {
                mNeedMoveFiles = true;
            }

            // Are we currently warning the user about missing files? If so, we should re-check: They may have fixed it.
            boolean currentlyWarning = HintManager.shouldBeShown(R.string.hint_missing_covers);
            // Do recheck OR if we've upgraded from a prior version
            if (currentlyWarning || (lastVersion <= 206 && wasUpgrade)) {
                BookCataloguePreferences ap = BookCatalogueApp.getAppPreferences();
                // If we're currently warning the user OR we have not yet checked, make a check.
                if (currentlyWarning || !ap.hasCheckedForMissingCovers()) {
                    // Check 10 random books for covers; if < 90% present, there may be a problem.
                    q.enqueue(new CheckCoversTask());
                }
            }

            // Always enqueue it; it will get a DB and check if required...
            q.enqueue(new RebuildFtsTask());
            q.enqueue(new AnalyzeDbTask());
            // Remove old logs
            Logger.clearLog();
            // Clear the flag
            mIsReallyStartup = false;

            // ENHANCE: add checks for new Events/crashes
        }

        // If no tasks were queued, then move on to stage 2. Otherwise, the completed
        // tasks will cause stage 2 to start.
        if (mTaskQueue == null)
            stage2Startup();
//		getSupportFragmentManager().beginTransaction()
//					   .replace(R.id., list)
//					   .addToBackStack(null)
//					   .commit();
    }

    /**
     * Update the progress dialog, if it has not been dismissed.
     *
     * @param stringId Message to display
     */
    public void updateProgress(final int stringId) {
        updateProgress(getString(stringId));
    }

    /**
     * Update the progress dialog, if it has not been dismissed.
     *
     * @param message Message to display
     */
    public void updateProgress(final String message) {
        // If mProgress is null, it has been dismissed. Don't update.
        if (mProgress == null) {
            return;
        }

        // If we are in the UI thread, update the progress.
        if (Thread.currentThread().equals(mUiThread)) {
            // There is a small chance that this message could be set to display *after* the activity is finished,
            // so we check and we also trap, log and ignore errors.
            // See http://code.google.com/p/android/issues/detail?id=3953
            if (!isFinishing()) {
                try {
                    LinearLayout spinnerLayout = (LinearLayout) mProgress.findViewById(R.id.spinner_layout);
                    LinearLayout horizontalLayout = (LinearLayout) mProgress.findViewById(R.id.horizontal_layout);
                    spinnerLayout.setVisibility(View.VISIBLE);
                    horizontalLayout.setVisibility(View.GONE);
                    TextView messageView = (TextView) mProgress.findViewById(R.id.spinner_message);
                    messageView.setText(message);
                    if (!mProgress.isShowing())
                        mProgress.show();
                } catch (Exception e) {
                    Logger.logError(e);
                }
            }
        } else {
            // If we are NOT in the UI thread, queue it to the UI thread.
            mHandler.post(() -> updateProgress(message));
        }
    }

    /**
     * Called in the UI thread when any startup task completes. If no more tasks, start stage 2.
     * Because it is in the UI thread, it is not possible for this code to be called until after
     * onCreate() completes, so a race condition is not possible. Equally well, tasks should only
     * be queued in onCreate().
     */
    private void taskCompleted(SimpleTask task) {
        System.out.println("Task Completed: " + task.getClass().getSimpleName());
        if (!mTaskQueue.hasActiveTasks()) {
            System.out.println("Task Completed - no more");
            stage2Startup();
        }
    }

    /**
     * Get (or create) the task queue.
     *
     * @return Return (possibly creating) the task queue
     */
    private SimpleTaskQueue getQueue() {
        if (mTaskQueue == null) {
            mTaskQueue = new SimpleTaskQueue("startup-tasks", 1);
            // Listen for task completions
            mTaskQueue.setTaskFinishListener((task, e) -> taskCompleted(task));
        }
        return mTaskQueue;
    }

    /**
     * Called in UI thread after last startup task completes, or if there are no tasks to queue.
     */
    private void stage2Startup() {
        // Remove the weak reference. Only used by db onUpgrade.
        mStartupActivity.clear();
        // Get rid of the progress dialog
        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }

        // Display upgrade message if necessary, otherwise go on to stage 3
        if (mUpgradeMessageShown || UpgradeMessageManager.getUpgradeMessage().equals("")) {
            stage3Startup();
        } else {
            upgradePopup(UpgradeMessageManager.getUpgradeMessage());
        }
    }

    /**
     * Start the main book list
     */
    private void doMyBooks() {
        Intent i = new Intent(this, Library.class);
        if (mWasReallyStartup)
            i.putExtra("startup", true);
        // XXX: This is nasty, now we use fragments, StartupActivity shoud be a FragmenActivity and load the right fragment
        // then we could do away with the whole isRoot/willBeRoot thing
        i.putExtra("willBeTaskRoot", isTaskRoot());
        startActivity(i);
    }

    /**
     * Start the main menu
     */
    private void doMainMenu() {
        Intent i = new Intent(this, MainMenu.class);
        if (mWasReallyStartup)
            i.putExtra("startup", true);
        i.putExtra("willBeTaskRoot", isTaskRoot());
        startActivity(i);
    }

    public void stage3Startup() {
        BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
        int opened = prefs.getInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
        int startCount = prefs.getInt(PREF_START_COUNT, 0) + 1;

        Editor ed = prefs.edit();
        if (opened == 0) {
            ed.putInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
        } else {
            ed.putInt(STATE_OPENED, opened - 1);
        }
        ed.putInt(PREF_START_COUNT, startCount);
        ed.commit();

        if ((startCount % AMAZON_PROMPT_WAIT) == 0) {
            mShowAmazonHint = true;
        }
        mExportRequired = false;

        //	if (opened == 0) {
        //		AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(R.string.backup_request).create();
        //		alertDialog.setCanceledOnTouchOutside(false);
        //		alertDialog.setTitle(R.string.backup_title);
        //		alertDialog.setIcon(R.drawable.ic_menu_info);
        //		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.button_cancel),
        //							  (dialog, which) -> dialog.dismiss());
        //		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.button_ok),
        //							  (dialog, which) -> {
        //								  mExportRequired = true;
        //								  dialog.dismiss();
        //							  });
        //		alertDialog.setOnCancelListener(DialogInterface::dismiss);
        //		alertDialog.setOnDismissListener(dialog -> {
        //			if (mExportRequired) {
        //				launchBackupExport();
        //			} else {
        //				stage4Startup();
        //			}
        //		});
        //		alertDialog.show();
        //	} else {
        stage4Startup();
        //}
    }

    /**
     * Start whatever activity the user expects
     */
    private void stage4Startup() {
        BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

        // Handle startup specially.
        // Check if we really want to start this activity.
        if (prefs.getStartInMyBook()) {
            doMyBooks();
        } else {
            doMainMenu();
        }

        // We are done
        finish();
    }

    /**
     * This will display a popup with a provided message to the user. This will be
     * mostly used for upgrade notifications
     *
     * @param message The message to display in the popup
     */
    public void upgradePopup(String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY)).create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setTitle(R.string.upgrade_title);
        alertDialog.setIcon(R.drawable.ic_menu_info);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.button_ok), (dialog, which) -> alertDialog.dismiss());
        alertDialog.setOnCancelListener(dialog -> alertDialog.dismiss());
        alertDialog.setOnDismissListener(dialog -> {
            UpgradeMessageManager.setMessageAcknowledged();
            stage3Startup();
        });
        alertDialog.show();
        mUpgradeMessageShown = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTaskQueue != null)
            mTaskQueue.finish();
    }

    /**
     * Pass on the event to the relevant handler.
     *
     * @param dialogId As passed to us
     * @param dialog   As passed to us
     * @param settings As passed to us
     */
    @Override
    public void onExportTypeSelectionDialogResult(int dialogId, BookCatalogueDialogFragment dialog, ExportSettings settings) {
        mBackupExportManager.onExportTypeSelectionDialogResult(dialogId, dialog, settings);
    }

    /**
     * Pass on the event to the relevant handler.
     *
     * @param dialogId As passed to us
     * @param dialog   As passed to us
     * @param button   As passed to us
     */
    @Override
    public void onMessageDialogResult(int dialogId, MessageDialogFragment dialog, int button) {
        // Do nothing. We just need this so we can display message dialogs.
        if (dialogId == ID.MSG_ID_BACKUP_EXPORT_COMPLETE) {
            stage4Startup();
        } else {
            super.onMessageDialogResult(dialogId, dialog, button);
        }
    }

    /**
     * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
     *
     * @author Philip Warner
     *
     */
    public class RebuildFtsTask implements SimpleTask {

        @Override
        public void run(SimpleTaskContext taskContext) {
            // Get a DB to make sure the FTS rebuild flag is set appropriately
            CatalogueDBAdapter db = taskContext.getDb();
            BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
            if (prefs.getBoolean(PREF_FTS_REBUILD_REQUIRED, false)) {
                updateProgress(getString(R.string.rebuilding_search_index));
                db.rebuildFts();
                prefs.setBoolean(PREF_FTS_REBUILD_REQUIRED, false);
            }
        }

        @Override
        public void onFinish(Exception e) {
        }

    }

    public class AnalyzeDbTask implements SimpleTask {

        @Override
        public void run(SimpleTaskContext taskContext) {
            CatalogueDBAdapter db = taskContext.getDb();
            BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

            updateProgress(getString(R.string.optimizing_databases));
            // Analyze DB
            db.analyzeDb();
            if (AdminLibraryPreferences.isThumbnailCacheEnabled()) {
                // Analyze the covers DB
                Utils utils = taskContext.getUtils();
                utils.analyzeCovers();
            }

            if (prefs.getBoolean(PREF_AUTHOR_SERIES_FIXUP_REQUIRED, false)) {
                db.fixupAuthorsAndSeries();
                prefs.setBoolean(PREF_AUTHOR_SERIES_FIXUP_REQUIRED, false);
            }
        }

        @Override
        public void onFinish(Exception e) {
        }

    }

    public class CheckCoversTask implements SimpleTask {
        BookCataloguePreferences mPrefs = BookCatalogueApp.getAppPreferences();
        boolean mNeedsWarning = false;

        @Override
        public void run(SimpleTaskContext taskContext) {
            CatalogueDBAdapter db = taskContext.getDb();

            updateProgress(getString(R.string.loading));

            // Get the first and last ID's we randomly select a few books and look for covers.
            final long[] ids = db.getFirstAndLastBookRowId();
            final long range = ids[1] - ids[0];
            int cnt = 0;
            int found = 0;
            // Loop and look for random covers; it's possible that if a lot of books have been deleted then
            // the random ID might rarely find a real book, so we look 1000 times (until we have actually got
            // 20 real books).
            for (int i = 0; (i < 1000 && cnt < 20); i++) {
                // Random ID and get UUID
                long id = (long) (Math.random() * range + ids[0]);
                try {
                    String uuid = db.getBookUuid(id);
                    // If there is a book with a valid UUID, then check
                    if (uuid != null && !("".equals(uuid))) {
                        cnt++;
                        File f = CatalogueDBAdapter.fetchThumbnailByUuid(uuid);
                        if (f.exists()) {
                            found++;
                        }
                    }
                } catch (SQLiteDoneException ignore) {
                    // Ignore missing book
                }
            }
            // If we found < 90%, warn the user.
            if (cnt > 10 && found < (cnt * 0.9)) {
                mNeedsWarning = true;
            }
        }

        @Override
        public void onFinish(Exception e) {
            if (e == null) {
                // Remember we have done the check once.
                mPrefs.setCheckedForMissingCovers(true);
                // Display the warning if necessary
                HintManager.setShouldBeShown(R.string.hint_missing_covers, mNeedsWarning);
            }
        }
    }
}
