package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.bcservices.BcBooksApi;

/**
 * Class to handle backup to web in a separate thread.
 */
public class BackupToWebThread extends ManagedTask {

    /**
     * Constructor.
     *
     * @param manager Associated task manager
     */
    public BackupToWebThread(TaskManager manager) {
        super(manager);
    }

    @Override
    protected void onThreadFinish() { cleanup(); }

    @Override
    protected void onRun() {
        // Login at the start of each session
        BcBooksApi.login();
        int numFailedBackup = BcBooksApi.retrieveBooksForPosting();
        if(numFailedBackup > 0) {
            String toastMessage = BookCatalogueApp.getResourceString(R.string.books_failed_backup, numFailedBackup);
            mManager.doToast(toastMessage);
        }
    }

    //TODO - cleanup method if needed
    private void cleanup() {
    }
}

