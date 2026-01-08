package com.eleybourn.bookcatalogue;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.data.AnthologyTitle;
import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.data.Bookshelf;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

import java.io.File;
import java.util.ArrayList;

/**
 * A self-contained task to perform Google Cloud Backup.
 * Implements SimpleTask to work with SimpleTaskQueue.
 */
public class BookCatalogueSyncTask implements SimpleTask {

    // Callback interface to update the UI (MainMenu)
    public interface SyncListener {
        void onSyncProgress(int current, int total);
        void onSyncComplete(String message);
        void onSyncError(String error);
    }

    private final String mEmail;
    private final boolean mOptIn;
    private final SyncListener mListener;
    private int mSuccessCount = 0;
    private int mErrorCount = 0;

    public BookCatalogueSyncTask(String email, boolean optIn, SyncListener listener) {
        this.mEmail = email;
        this.mOptIn = optIn;
        this.mListener = listener;
    }

    @Override
    public void run(SimpleTaskContext taskContext) throws Exception {
        // 1. Authenticate with the API
        // Note: We do this inside the background thread
        String token = BookCatalogueSync.login(mEmail, mOptIn);

        CatalogueDBAdapter db = taskContext.getDb();

        // Query all books. 
        // Note: Using the raw cursor from the adapter provided by SimpleTaskContext
        try (Cursor bookCursor = db.fetchAllBooks()) {

            if (bookCursor != null && bookCursor.moveToFirst()) {
                int total = bookCursor.getCount();
                int count = 0;

                // Get column indices
                int idIndex = bookCursor.getColumnIndexOrThrow("_id");
                // Check if cover column exists, otherwise handle gracefully
                int coverIndex = bookCursor.getColumnIndex("cover_url");

                do {
                    // Check if queue is killing us
                    if (taskContext.isTerminating()) break;

                    try {
                        int bookId = bookCursor.getInt(idIndex);

                        String coverPath = (coverIndex > -1) ? bookCursor.getString(coverIndex) : null;

                        // Prepare File
                        File thumbFile = null;
                        if (coverPath != null && !coverPath.trim().isEmpty()) {
                            File f = new File(coverPath);
                            if (f.exists()) thumbFile = f;
                        }
                        ArrayList<Author> authors = db.getBookAuthorList(bookId);
                        ArrayList<Bookshelf> bookshelves = db.getBookBookshelfList(bookId);
                        ArrayList<Series> series = db.getBookSeriesList(bookId);
                        ArrayList<AnthologyTitle> anthology = db.getBookAnthologyTitleList(bookId);

                        // Upload
                        BookCatalogueSync.uploadBook(token, bookId, bookCursor, authors, bookshelves, series, anthology, thumbFile);
                        mSuccessCount++;
                    } catch (Exception e) {
                        // Log the specific error for this book, but continue the loop
                        System.err.println("Error syncing book index " + count + ": " + e.getMessage());
                        mErrorCount++;
                    }
                    count++;

                    // Optional: Update progress every 5 items to reduce UI spam
                    if (mListener != null && count % 5 == 0) {
                        // We can't call UI methods directly here, but we can log
                        // If you need real-time progress, you'd usually post a Runnable to a Handler here
                        // For now, we rely on onFinish, or you can add a progress callback that uses a Handler.
                        notifyProgress(count, total);
                    }

                } while (bookCursor.moveToNext());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onFinish(Exception e) {
        // This runs on the UI Thread automatically via SimpleTaskQueue
        if (mListener == null) return;

        if (e != null) {
            mListener.onSyncError(e.getMessage());
        } else {
            // Report partial successes if errors occurred during loop
            if (mErrorCount > 0) {
                mListener.onSyncComplete("Finished with " + mSuccessCount + " success(es) and " + mErrorCount + " error(s).");
            } else {
                mListener.onSyncComplete("Cloud backup finished successfully.");
            }
        }
    }

    /**
     * Helper to notify progress safely on UI thread
     */
    private void notifyProgress(int current, int total) {
        if (mListener != null) {
            // We need a handler to jump to main thread, 
            // but SimpleTaskQueue doesn't expose one easily inside 'run'.
            // In standard Android, we can use the Main Looper:
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    mListener.onSyncProgress(current, total)
            );
        }
    }
}
