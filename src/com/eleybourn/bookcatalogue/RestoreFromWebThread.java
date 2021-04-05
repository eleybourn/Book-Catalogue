package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.bcservices.BcBooksApi;

/**
 * Class to handle backup to web in a separate thread.
 */
public class RestoreFromWebThread extends ManagedTask {

    /**
     * Constructor.
     *
     * @param manager Associated task manager
     */
    public RestoreFromWebThread(TaskManager manager) {
        super(manager);
    }

    @Override
    protected void onThreadFinish() { cleanup(); }

    @Override
    protected void onRun() {
        // Login at the start of each session
        BcBooksApi.login();
        BcBooksApi.getAllBooks();
    }

    // TODO - cleanup method if needed
    private void cleanup() {

    }
}
