/*
 * @copyright 2011 Philip Warner
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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.TaskManager.TaskManagerController;
import com.eleybourn.bookcatalogue.TaskManager.TaskManagerListener;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.debug.Tracker.States;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * TODO: Remove this!!!! Fragments makes ActivityWithTasks mostly redundant.
 * <p>
 * Class to used as a base class for any Activity that wants to run one or more threads that
 * use a ProgressDialog.
 * Part of three components that make this easier:
 *  - TaskManager -- handles the management of multiple threads sharing a progressDialog
 *  - ActivityWithTasks -- uses a TaskManager (and communicates with it) to handle progress
 *    messages for threads. Deals with orientation changes in cooperation with TaskManager.
 *  - ManagedTask -- Background task that is managed by TaskManager and uses TaskManager to
 *    do all display activities.
 *
 * @author Philip Warner
 */
abstract public class ActivityWithTasks extends BookCatalogueActivity {
    /**
     * ID of associated TaskManager
     */
    protected long mTaskManagerId = 0;
    /**
     * ProgressDialog for this activity
     */
    protected Dialog mProgressDialog = null;
    /**
     * Associated TaskManager
     */
    private TaskManager mTaskManager = null;
    /**
     * Max value for ProgressDialog
     */
    private int mProgressMax = 0;
    /**
     * Current value for ProgressDialog
     */
    private int mProgressCount = 0;
    /**
     * Message for ProgressDialog
     */
    private String mProgressMessage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Restore mTaskManagerId if present
        if (savedInstanceState != null) {
            mTaskManagerId = savedInstanceState.getLong("TaskManagerId");
        }
    }

    /**
     * Utility routine to get the task manager for his activity
     */
    protected TaskManager getTaskManager() {
        if (mTaskManager == null) {
            if (mTaskManagerId != 0) {
                TaskManagerController c = TaskManager.getMessageSwitch().getController(mTaskManagerId);
                if (c != null) {
                    mTaskManager = c.getManager();
                } else {
                    Logger.logError(new RuntimeException("Have ID, but can not find controller getting TaskManager"));
                }
            } else {
                //Logger.logError(new RuntimeException("Task manager requested, but no ID available"));
            }

            // Create if necessary
            if (mTaskManager == null) {
                TaskManager tm = new TaskManager();
                mTaskManagerId = tm.getSenderId();
                mTaskManager = tm;
            }
        }
        return mTaskManager;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop listening
        if (mTaskManagerId != 0) {
            TaskManager.getMessageSwitch().removeListener(mTaskManagerId, mTaskListener);
            // If it's finishing, the remove all tasks and cleanup
            if (isFinishing()) {
                TaskManager tm = getTaskManager();
                if (tm != null)
                    tm.close();
            }
        }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If we are finishing, we don't care about active tasks.
        if (!this.isFinishing()) {
            // Restore mTaskManager if present
            getTaskManager();

            // Listen
            TaskManager.getMessageSwitch().addListener(mTaskManagerId, mTaskListener, true);
        }
    }

    /**
     * Method to allow subclasses easy access to terminating tasks
     */
    public void onTaskEnded(ManagedTask task) {
    }

    /**
     * Utility routine to standardize checking for desired dialog type.
     *
     * @return    true if dialog should be determinate
     */
    private boolean wantDeterminateProgress() {
        return (mProgressMax > 0);
    }    /**
     * Object to handle all TaskManager events
     */
    private final TaskManagerListener mTaskListener = new TaskManagerListener() {

        @Override
        public void onTaskEnded(TaskManager manager, ManagedTask task) {
            // Just pass this one on
            ActivityWithTasks.this.onTaskEnded(task);
        }

        @Override
        public void onProgress(int count, int max, String message) {
            // RELEASE: Remove these lines!
            String dbgMsg = count + "/" + max + ", '" + message.replace("\n", "\\n") + "'";
            Tracker.handleEvent(ActivityWithTasks.this, "SearchProgress " + dbgMsg, States.Running);
            System.out.println("PRG: " + dbgMsg);

            // Save the details
            mProgressCount = count;
            mProgressMax = max;
            mProgressMessage = message;

            // If empty, close any dialog
            if ((mProgressMessage == null || mProgressMessage.trim().isEmpty()) && mProgressMax == mProgressCount) {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
            } else {
                updateProgress();
            }

        }

        /**
         * Display a Toast message
         */
        @Override
        public void onToast(String message) {
            Toast.makeText(ActivityWithTasks.this, message, Toast.LENGTH_LONG).show();
        }

        /**
         * TaskManager is finishing...cleanup.
         */
        @Override
        public void onFinished() {
            mTaskManager.close();
            mTaskManager = null;
            mTaskManagerId = 0;
        }
    };

    /**
     * Setup the ProgressDialog according to our needs
     */
    private void updateProgress() {
        boolean wantDet = wantDeterminateProgress();

        if (mProgressDialog != null) {
            Object tag = mProgressDialog.getWindow().getDecorView().getTag();
            if ((wantDet && "indeterminate".equals(tag)) || (!wantDet && "determinate".equals(tag))) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }

        // Create dialog if necessary
        if (mProgressDialog == null) {
            mProgressDialog = new Dialog(this);
            mProgressDialog.setContentView(R.layout.progress_dialog);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.getWindow().getDecorView().setTag(wantDet ? "determinate" : "indeterminate");
        }

        LinearLayout spinnerLayout = mProgressDialog.findViewById(R.id.spinner_layout);
        LinearLayout horizontalLayout = mProgressDialog.findViewById(R.id.horizontal_layout);

        // Set style
        if (wantDet) {
            spinnerLayout.setVisibility(View.GONE);
            horizontalLayout.setVisibility(View.VISIBLE);
            ProgressBar progressBar = mProgressDialog.findViewById(R.id.progress_horizontal);
            progressBar.setMax(mProgressMax);
            progressBar.setProgress(mProgressCount);
            TextView messageView = mProgressDialog.findViewById(R.id.horizontal_message);
            messageView.setText(mProgressMessage);
        } else {
            horizontalLayout.setVisibility(View.GONE);
            spinnerLayout.setVisibility(View.VISIBLE);
            TextView messageView = mProgressDialog.findViewById(R.id.spinner_message);
            messageView.setText(mProgressMessage);
        }

        // Set message; if we are cancelling we override the message
        if (mTaskManager.isCancelling()) {
            TextView messageView;
            if (wantDet) {
                messageView = mProgressDialog.findViewById(R.id.horizontal_message);
            } else {
                messageView = mProgressDialog.findViewById(R.id.spinner_message);
            }
            messageView.setText(getString(R.string.cancelling));
        }

        // Set other attrs
        mProgressDialog.setOnKeyListener(mDialogKeyListener);
        mProgressDialog.setOnCancelListener(mCancelHandler);
        // Show it if necessary
        mProgressDialog.show();
    }

    /**
     * Cancel all tasks, and if the progress is showing, update it (it will check task manager status)
     */
    private void cancelAndUpdateProgress() {
        if (mTaskManager != null)
            mTaskManager.cancelAllTasks();
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            updateProgress();
        }
    }

    /**
     * Save the TaskManager ID for later retrieval
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mTaskManagerId != 0)
            outState.putLong("TaskManagerId", mTaskManagerId);
    }    /**
     * Handler for the user cancelling the progress dialog.
     */
    private final OnCancelListener mCancelHandler = i -> cancelAndUpdateProgress();

    /**
     * Wait for the 'Back' key and cancel all tasks on keyUp.
     */
    private final OnKeyListener mDialogKeyListener = (dialog, keyCode, event) -> {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Toasting a message here makes the app look less responsive, because
                // the final 'Cancelled...' message is delayed too much.
                //Toast.makeText(ActivityWithTasks.this, R.string.cancelling, Toast.LENGTH_LONG).show();
                cancelAndUpdateProgress();
                return true;
            }
        }
        return false;
    };




}
