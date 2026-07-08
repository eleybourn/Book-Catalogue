package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.data.AnthologyTitle;
import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.data.Bookshelf;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.database.DbSync;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

public class BookCatalogueAPI implements SimpleTask {
    public static final String REQUEST_LOGIN = "login";
    public static final String REQUEST_INFO_COUNT = "count";
    public static final String REQUEST_INFO_LAST = "last_backup";
    public static final String REQUEST_BACKUP_ALL = "full_backup";
    public static final String REQUEST_BACKUP_BOOK = "backup_book";
    public static final String REQUEST_RESTORE_ALL = "restore";
    public static final String REQUEST_DELETE_BOOK = "delete_book";
    public static final String REQUEST_DELETE_ALL = "delete_all";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_GET = "GET";
    public static final String METHOD_DEL = "DELETE";
    private static final SimpleTaskQueue mSyncQueue = new SimpleTaskQueue("BookCatalogueSyncQueue");
    private static final String BASE_URL = "https://book-catalogue.com/api";
    public static String REQUEST_GET_BOOKS = "get_books";
    public static String REQUEST_GET_BOOK = "get_book";
    public static volatile boolean isBackupRunning = false;
    public static volatile boolean isRestoreRunning = false;
    private final String mEmail;
    private final boolean mOptIn;
    private String mApiToken;
    private final BookCataloguePreferences mPrefs;
    // Add a static field to hold the currently active listener.
    private static ApiListener sActiveListener;
    private final Context mContext;
    private final String mRequest;
    private SimpleTaskContext mTaskContext;
    private boolean retry = true;
    private long mBookId;
    private final ApiListener mInstanceListener;


    public BookCatalogueAPI(Context context, String request, long book_id, ApiListener listener) {
        mContext = context;
        mBookId = book_id;
        this.mRequest = request;
        mInstanceListener = listener;
        if (listener != null) {
            sActiveListener = listener;
        }
        mPrefs = new BookCataloguePreferences();
        mEmail = mPrefs.getAccountEmail();
        mOptIn = mPrefs.getAccountOptIn();
        mApiToken = mPrefs.getAccountApiToken();

        mSyncQueue.enqueue(this);
    }

    public BookCatalogueAPI(Context context, String request, ApiListener listener) {
        mContext = context;
        this.mRequest = request;
        mInstanceListener = listener;
        if (listener != null) {
            sActiveListener = listener;
        }
        mPrefs = new BookCataloguePreferences();
        mEmail = mPrefs.getAccountEmail();
        mOptIn = mPrefs.getAccountOptIn();
        mApiToken = mPrefs.getAccountApiToken();

        mSyncQueue.enqueue(this);
    }

    // Add a static method to update the listener.
    public static void setActiveListener(ApiListener listener) {
        sActiveListener = listener;
    }

    /**
     * Helper method to automatically sync a book to the cloud if the user is subscribed and sync is enabled.
     */
    public static void syncBook(Context context, long bookId) {
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        if (prefs.isSubscribed() && prefs.isOnlineSyncEnabled() && !prefs.getAccountApiToken().isEmpty()) {
            new BookCatalogueAPI(context, REQUEST_BACKUP_BOOK, bookId, null);
        }
    }

    /**
     * Helper method to automatically delete a book from the cloud if the user is subscribed and sync is enabled.
     */
    public static void syncDeleteBook(Context context, long bookId) {
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        if (prefs.isSubscribed() && prefs.isOnlineSyncEnabled() && !prefs.getAccountApiToken().isEmpty()) {
            new BookCatalogueAPI(context, REQUEST_DELETE_BOOK, bookId, null);
        }
    }

    /**
     * Helper method to automatically run a full backup in the background when the app starts.
     */
    public static void backgroundFullBackup(Context context) {
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        if (prefs.isSubscribed() && prefs.isOnlineSyncEnabled() && !prefs.getAccountApiToken().isEmpty()) {
            final Context appContext = context.getApplicationContext();
            Toast.makeText(appContext, "Checking online backup status...", Toast.LENGTH_SHORT).show();
            new BookCatalogueAPI(appContext, REQUEST_BACKUP_ALL, new ApiListener() {
                @Override
                public void onApiProgress(String request, int current, int total, String message) {}

                @Override
                public void onApiProgress(String request, int current, int total) {}

                @Override
                public void onApiComplete(String request, String message) {
                    if (REQUEST_BACKUP_ALL.equals(request)) {
                        Toast.makeText(appContext, "Backup check complete", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onApiError(String request, String error) {
                }
            });
        }
    }

    private static String getDate(Cursor c, String columnName) {
        int index = c.getColumnIndex(columnName);
        if (index > -1 && !c.isNull(index)) {
            // Get the date as a string since it is stored as a formatted string in the DB
            String dateStr = c.getString(index).trim();

            // Avoid returning a date for empty or "0" which often means "not set"
            if (dateStr == null || dateStr.isEmpty() || dateStr.equals("0")) {
                return "";
            }

            // Special handling for 4-digit year only: convert to YYYY-01-01
            if (dateStr.length() == 4) {
                try {
                    int year = Integer.parseInt(dateStr);
                    if (year >= 1000 && year <= 2100) {
                        return dateStr + "-01-01";
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // Special handling for 7-character year-month (YYYY-MM): convert to YYYY-MM-01
            if (dateStr.length() == 7 && dateStr.charAt(4) == '-') {
                try {
                    int year = Integer.parseInt(dateStr.substring(0, 4));
                    int month = Integer.parseInt(dateStr.substring(5, 7));
                    if (year >= 1000 && year <= 2100 && month >= 1 && month <= 12) {
                        return dateStr + "-01";
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // Try to parse using project utilities to normalize to ISO format (yyyy-MM-dd)
            Date d = Utils.parseDate(dateStr);
            if (d != null) {
                if (dateStr.contains(":") || dateStr.length() > 10) {
                    return Utils.toSqlDateTime(d);
                } else {
                    return Utils.toSqlDateOnly(d);
                }
            }

            // Fallback for cases where it might be a millisecond timestamp stored as a string or number
            try {
                long timestamp = Long.parseLong(dateStr);
                if (timestamp > 0) {
                    return Utils.toSqlDateTime(new Date(timestamp));
                }
            } catch (NumberFormatException e) {
                // Ignore
            }

            return dateStr;
        }
        return "";
    }

    private static String getString(Cursor c, String columnName) {
        int index = c.getColumnIndex(columnName);
        if (index > -1 && !c.isNull(index)) {
            return c.getString(index).trim();
        }
        return "";
    }

    private static int getInt(Cursor c, String columnName) {
        int index = c.getColumnIndex(columnName);
        if (index > -1 && !c.isNull(index)) {
            return c.getInt(index);
        }
        return 0;
    }

    private static void addFormField(PrintWriter writer, String boundary, String name, String value) {
        String finalValue = (value == null) ? "" : value;
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n");
        writer.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        writer.append(finalValue).append("\r\n").flush();
    }

    @SuppressWarnings("SameParameterValue")
    private static void addFilePart(PrintWriter writer, OutputStream os, String boundary, String fieldName, File uploadFile) throws IOException {
        String fileName = uploadFile.getName();
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(fileName).append("\"\r\n");
        writer.append("Content-Type: application/octet-stream\r\n\r\n");

        // CRITICAL FIX: Flush the writer before writing raw bytes to the underlying stream
        writer.flush();

        try (FileInputStream inputStream = new FileInputStream(uploadFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }

        // Prepare for the next part (character data)
        writer.append("\r\n");
        writer.flush();
    }

    private static String readStream(InputStream in) throws IOException {
        if (in == null) return "No error stream";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    /**
     * Performs the Login API call.
     */
    public void login() {
        try {
            if (mEmail.isEmpty()) {
                throw new Exception("No email address set");
            }
            ArrayList<String> fields = new ArrayList<>();
            fields.add("email");
            fields.add("opt-in");
            ArrayList<String> values = new ArrayList<>();
            values.add(mEmail);
            values.add(mOptIn ? "1" : "0");

            String response = connection("/login", METHOD_POST, fields, values);
            if (response == null) {
                throw new Exception("Failed to connect to login endpoint");
            }
            JSONObject json = new JSONObject(response);
            if (json.has("api_token")) {
                mApiToken = json.getString("api_token");
                mPrefs.setAccountApiToken(mApiToken);
                if (json.has("subscription_expiry")) {
                    mPrefs.setSubscriptionExpiry(json.getString("subscription_expiry"));
                }
                // If they are logging in and are subscribed, enable sync by default.
                if (mPrefs.isSubscribed()) {
                    mPrefs.setOnlineSyncEnabled(true);
                }
                notifyComplete("Login successful");
            } else {
                throw new Exception("Login response missing API token");
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "Login failed", e);
            notifyError("Login failed: " + e.getMessage());
        }
    }

    public void runBackupBook() {
        CatalogueDBAdapter db = new CatalogueDBAdapter(mContext);
        try {
            db.open();
            try (Cursor bookCursor = db.fetchBookById(mBookId)) {
                // Optimization for single book: Fetch server state for this book specifically
                JSONObject serverBook = getBook(false);
                HashMap<Long, JSONObject> serverMap = null;
                if (serverBook != null && serverBook.has("bcid")) {
                    serverMap = new HashMap<>();
                    serverMap.put(serverBook.optLong("bcid"), serverBook);
                }
                runBackup(bookCursor, db, serverMap);
                notifyComplete("Backup completed successfully.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            db.close();
        }
    }

    public void runFullBackup() {
        CatalogueDBAdapter db = new CatalogueDBAdapter(mContext);
        try {
            db.open();
            // 1. Fetch current cloud state to identify orphaned records
            notifyProgress(0, 1, "Reviewing online backup...");
            JSONArray serverBooks = getAllBooks(false);
            HashMap<Long, JSONObject> serverMap = new HashMap<>();

            if (serverBooks != null) {
                int serverTotal = serverBooks.length();
                for (int i = 0; i < serverTotal; i++) {
                    // Check if queue is killing us
                    if (mTaskContext.isTerminating()) break;

                    JSONObject serverBook = serverBooks.optJSONObject(i);
                    if (serverBook != null) {
                        long bcid = serverBook.optLong("bcid", -1);
                        if (bcid != -1) {
                            if (!db.checkBookExists(bcid)) {
                                // Book exists in cloud but not locally, delete it
                                notifyProgress(i + 1, serverTotal, "Removing orphaned records...");
                                connection("/book/" + bcid, METHOD_DEL);
                            } else {
                                // Keep track of it for the next phase (delta backup)
                                serverMap.put(bcid, serverBook);
                            }
                        }
                    }
                }
            }

            // 2. Perform the backup of local books (only those that are different)
            try (Cursor bookCursor = db.fetchAllBooks()) {
                runBatchBackup(bookCursor, db, serverMap);
                notifyComplete("Backup completed successfully.");
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "Full backup failed", e);
            notifyError("Backup failed: " + e.getMessage());
        } finally {
            db.close();
        }
    }

    private void populateBookFields(ArrayList<String> fields, ArrayList<String> values, String prefix, String suffix,
                                    int bookId, Cursor bookCursor, ArrayList<Author> authors,
                                    ArrayList<Bookshelf> bookshelves, ArrayList<Series> series,
                                    ArrayList<AnthologyTitle> anthology, String thumbnailRequest) {
        fields.add(prefix + "bcid" + suffix);
        values.add(String.valueOf(bookId));

        if (authors != null) {
            for (Author author : authors) {
                fields.add(prefix + "authors" + suffix + "[]");
                values.add(author.id + ", " + author.familyName + ", " + author.givenNames);
            }
        }

        fields.add(prefix + "title" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_TITLE));

        fields.add(prefix + "isbn" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_ISBN));

        fields.add(prefix + "publisher" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_PUBLISHER));

        fields.add(prefix + "date_published" + suffix);
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_DATE_PUBLISHED));

        fields.add(prefix + "rating" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_RATING));

        if (bookshelves != null) {
            for (Bookshelf bookshelf : bookshelves) {
                fields.add(prefix + "bookshelves" + suffix + "[]");
                values.add(bookshelf.id + ", " + bookshelf.name);
            }
        }

        fields.add(prefix + "read" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_READ));

        if (series != null) {
            for (Series seriesEntry : series) {
                fields.add(prefix + "series" + suffix + "[]");
                values.add(seriesEntry.id + ", " + seriesEntry.num + ", " + seriesEntry.name);
            }
        }

        fields.add(prefix + "pages" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_PAGES));

        fields.add(prefix + "notes" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_NOTES));

        fields.add(prefix + "list_price" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LIST_PRICE));

        fields.add(prefix + "location" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LOCATION));

        fields.add(prefix + "read_start" + suffix);
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_READ_START));

        fields.add(prefix + "read_end" + suffix);
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_READ_END));

        fields.add(prefix + "format" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_FORMAT));

        fields.add(prefix + "signed" + suffix);
        values.add(getInt(bookCursor, CatalogueDBAdapter.KEY_SIGNED) > 0 ? "1" : "0");

        fields.add(prefix + "loaned_to" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LOANED_TO));

        fields.add(prefix + "anthology" + suffix);
        values.add(getInt(bookCursor, CatalogueDBAdapter.KEY_ANTHOLOGY_MASK) > 0 ? "1" : "0");

        if (anthology != null) {
            for (AnthologyTitle anthologyTitle : anthology) {
                fields.add(prefix + "anthologies" + suffix + "[]");
                values.add(anthologyTitle.getId() + ", " + anthologyTitle.getTitle());
                fields.add(prefix + "anthology_authors" + suffix + "[]");
                values.add(anthologyTitle.getAuthor().id + ", " + anthologyTitle.getAuthor().familyName + ", " + anthologyTitle.getAuthor().givenNames);
            }
        }

        fields.add(prefix + "description" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_DESCRIPTION));

        fields.add(prefix + "genre" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_GENRE));

        fields.add(prefix + "language" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LANGUAGE));

        fields.add(prefix + "date_added" + suffix);
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_DATE_ADDED));

        fields.add(prefix + "last_update_date" + suffix);
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_LAST_UPDATE_DATE));

        fields.add(prefix + "book_uuid" + suffix);
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_BOOK_UUID));

        if (thumbnailRequest != null && !thumbnailRequest.isEmpty()) {
            fields.add(prefix + "thumbnail_request" + suffix);
            values.add(thumbnailRequest);
        }
    }

    private void runBackup(Cursor bookCursor, CatalogueDBAdapter db, HashMap<Long, JSONObject> serverMap) {
        if (bookCursor != null && bookCursor.moveToFirst()) {
            int total = bookCursor.getCount();
            int count = 0;

            // Get column indices
            int idIndex = bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROW_ID);
            int uuidIndex = bookCursor.getColumnIndexOrThrow(DatabaseDefinitions.DOM_BOOK_UUID.name);

            do {
                // Check if queue is killing us
                if (mTaskContext.isTerminating()) break;

                count++;
                notifyProgress(count, total);

                try {
                    int bookId = bookCursor.getInt(idIndex);
                    String uuid = bookCursor.getString(uuidIndex);
                    File thumbFile = CatalogueDBAdapter.fetchThumbnailByUuid(uuid);
                    ArrayList<Author> authors = db.getBookAuthorList(bookId);
                    ArrayList<Bookshelf> bookshelves = db.getBookBookshelfList(bookId);
                    ArrayList<Series> series = db.getBookSeriesList(bookId);
                    ArrayList<AnthologyTitle> anthology = db.getBookAnthologyTitleList(bookId);

                    // Check if we need to back up this book
                    boolean needsBackup = true;
                    if (serverMap != null && serverMap.containsKey((long) bookId)) {
                        JSONObject serverBook = serverMap.get((long) bookId);
                        if (serverBook != null && !isBookDifferent(bookCursor, authors, bookshelves, series, anthology, thumbFile, serverBook, true)) {
                            needsBackup = false;
                        }
                    }

                    if (needsBackup) {
                        // Upload using the single-book endpoint with thumbnail
                        backupBook(bookId, bookCursor, authors, bookshelves, series, anthology, thumbFile);
                    }
                } catch (Exception e) {
                    // Log the specific error for this book, but continue the loop
                    Log.e("BookCatalogueAPI", "Error syncing book at index " + (count - 1), e);
                }

            } while (bookCursor.moveToNext());
        }
    }

    private void runBatchBackup(Cursor bookCursor, CatalogueDBAdapter db, HashMap<Long, JSONObject> serverMap) {
        if (bookCursor != null && bookCursor.moveToFirst()) {
            int total = bookCursor.getCount();
            int count = 0;

            ArrayList<String> batchFields = new ArrayList<>();
            ArrayList<String> batchValues = new ArrayList<>();
            ArrayList<ThumbnailTask> thumbnailTasks = new ArrayList<>();
            int batchCount = 0;

            // Get column indices
            int idIndex = bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROW_ID);
            int uuidIndex = bookCursor.getColumnIndexOrThrow(DatabaseDefinitions.DOM_BOOK_UUID.name);

            do {
                // Check if queue is killing us
                if (mTaskContext.isTerminating()) break;

                count++;
                notifyProgress(count, total, "books backed up");

                try {
                    int bookId = bookCursor.getInt(idIndex);
                    String uuid = bookCursor.getString(uuidIndex);
                    File thumbFile = CatalogueDBAdapter.fetchThumbnailByUuid(uuid);
                    ArrayList<Author> authors = db.getBookAuthorList(bookId);
                    ArrayList<Bookshelf> bookshelves = db.getBookBookshelfList(bookId);
                    ArrayList<Series> series = db.getBookSeriesList(bookId);
                    ArrayList<AnthologyTitle> anthology = db.getBookAnthologyTitleList(bookId);

                    JSONObject serverBook = (serverMap != null) ? serverMap.get((long) bookId) : null;

                    // Check if we need to back up this book record (ignoring thumbnail)
                    boolean needsBackup = true;
                    //Log.d("BC", "Book " + bookId + " title: " + getString(bookCursor, CatalogueDBAdapter.KEY_TITLE));
                    if (serverBook != null && !isBookDifferent(bookCursor, authors, bookshelves, series, anthology, thumbFile, serverBook, false)) {
                        needsBackup = false;
                    }

                    if (needsBackup) {
                        // Add to batch
                        populateBookFields(batchFields, batchValues, "books[" + batchCount + "][", "]", bookId, bookCursor, authors, bookshelves, series, anthology, "SKIP");
                        batchCount++;
                    }

                    // Check if thumbnail changed independently
                    boolean thumbChanged = true;
                    if (serverBook != null) {
                        String localMd5 = (thumbFile != null && thumbFile.exists()) ? Utils.calculateMD5(thumbFile) : "";
                        String serverMd5 = getStringOrEmpty(serverBook, "thumbnail_md5");
                        if (localMd5.equals(serverMd5)) {
                            thumbChanged = false;
                        }
                    }

                    if (thumbChanged) {
                        // Queue thumbnail work
                        thumbnailTasks.add(new ThumbnailTask(bookId, (thumbFile != null && thumbFile.exists()) ? thumbFile : null));
                    }

                    if (batchCount >= 30) {
                        try {
                            sendBatch(batchFields, batchValues);
                        } finally {
                            batchFields.clear();
                            batchValues.clear();
                            batchCount = 0;
                        }
                    }
                } catch (Exception e) {
                    // Log the specific error for this book/batch, but continue the loop
                    Log.e("BookCatalogueAPI", "Error syncing book batch starting near index " + (count - 1), e);
                    // Ensure the batch is cleared so we don't keep failing on the same bad record
                    batchFields.clear();
                    batchValues.clear();
                    batchCount = 0;
                }

            } while (bookCursor.moveToNext());

            // Final batch
            if (batchCount > 0) {
                try {
                    sendBatch(batchFields, batchValues);
                } catch (Exception e) {
                    Log.e("BookCatalogueAPI", "Final batch failed", e);
                }
            }

            // Sync thumbnails
            int skippedThumbs = total - thumbnailTasks.size();
            if (thumbnailTasks.isEmpty()) {
                notifyProgress(total, total, "thumbnails backed up");
            }
            for (int i = 0; i < thumbnailTasks.size(); i++) {
                if (mTaskContext.isTerminating()) break;
                ThumbnailTask task = thumbnailTasks.get(i);
                notifyProgress(skippedThumbs + i + 1, total, "thumbnails backed up");
                try {
                    if (task.file != null) {
                        backupThumbnail(task.id, task.file);
                    } else {
                        deleteThumbnail(task.id);
                    }
                } catch (Exception e) {
                    Log.e("BookCatalogueAPI", "Thumbnail sync failed for book " + task.id, e);
                }
            }
        }
    }

    /**
     * Compare a local book record with a server record to determine if an update is needed.
     */
    private boolean isBookDifferent(Cursor local, ArrayList<Author> authors, ArrayList<Bookshelf> bookshelves, ArrayList<Series> series, ArrayList<AnthologyTitle> anthology, File thumbFile, JSONObject server, boolean checkThumbnail) {
        long bookId = getInt(local, CatalogueDBAdapter.KEY_ROW_ID);
        try {
            // Check basic fields
            if (checkDiff(bookId, "title", getString(local, CatalogueDBAdapter.KEY_TITLE), getStringOrEmpty(server, "title"))) return true;
            if (checkDiff(bookId, "isbn", getString(local, CatalogueDBAdapter.KEY_ISBN), getStringOrEmpty(server, "isbn"))) return true;
            if (checkDiff(bookId, "publisher", getString(local, CatalogueDBAdapter.KEY_PUBLISHER), getStringOrEmpty(server, "publisher"))) return true;
            if (checkDiff(bookId, "date_published", getDate(local, CatalogueDBAdapter.KEY_DATE_PUBLISHED), normalizeServerDate(getStringOrEmpty(server, "date_published")), true)) return true;
            if (checkDiff(bookId, "rating", getString(local, CatalogueDBAdapter.KEY_RATING), getStringOrEmpty(server, "rating"))) return true;
            if (checkDiff(bookId, "read", String.valueOf(getInt(local, CatalogueDBAdapter.KEY_READ)), String.valueOf(server.optInt("read", 0)))) return true;
            if (checkDiff(bookId, "pages", getString(local, CatalogueDBAdapter.KEY_PAGES), getStringOrEmpty(server, "pages"))) return true;
            if (checkDiff(bookId, "notes", getString(local, CatalogueDBAdapter.KEY_NOTES), getStringOrEmpty(server, "notes"))) return true;
            if (checkDiff(bookId, "list_price", getString(local, CatalogueDBAdapter.KEY_LIST_PRICE), getStringOrEmpty(server, "list_price"))) return true;
            if (checkDiff(bookId, "location", getString(local, CatalogueDBAdapter.KEY_LOCATION), getStringOrEmpty(server, "location"))) return true;
            if (checkDiff(bookId, "read_start", getDate(local, CatalogueDBAdapter.KEY_READ_START), normalizeServerDate(getStringOrEmpty(server, "read_start")), true)) return true;
            if (checkDiff(bookId, "read_end", getDate(local, CatalogueDBAdapter.KEY_READ_END), normalizeServerDate(getStringOrEmpty(server, "read_end")), true)) return true;
            if (checkDiff(bookId, "format", getString(local, CatalogueDBAdapter.KEY_FORMAT), getStringOrEmpty(server, "format"))) return true;
            if (checkDiff(bookId, "signed", String.valueOf(getInt(local, CatalogueDBAdapter.KEY_SIGNED) > 0 ? 1 : 0), String.valueOf(server.optInt("signed", 0)))) return true;
            if (checkDiff(bookId, "loaned_to", getString(local, CatalogueDBAdapter.KEY_LOANED_TO), getStringOrEmpty(server, "loaned_to"))) return true;
            if (checkDiff(bookId, "anthology", String.valueOf(getInt(local, CatalogueDBAdapter.KEY_ANTHOLOGY_MASK) > 0 ? 1 : 0), String.valueOf(server.optInt("anthology", 0)))) return true;
            if (checkDiff(bookId, "description", getString(local, CatalogueDBAdapter.KEY_DESCRIPTION), getStringOrEmpty(server, "description"))) return true;
            if (checkDiff(bookId, "genre", getString(local, CatalogueDBAdapter.KEY_GENRE), getStringOrEmpty(server, "genre"))) return true;
            if (checkDiff(bookId, "language", getString(local, CatalogueDBAdapter.KEY_LANGUAGE), getStringOrEmpty(server, "language"))) return true;
            if (checkDiff(bookId, "date_added", getDate(local, CatalogueDBAdapter.KEY_DATE_ADDED), normalizeServerDate(getStringOrEmpty(server, "date_added")), true)) return true;
            if (checkDiff(bookId, "book_uuid", getString(local, CatalogueDBAdapter.KEY_BOOK_UUID), getStringOrEmpty(server, "book_uuid"))) return true;

            // Check relations - simple counts and string checks for speed
            JSONArray sAuthors = server.optJSONArray("authors");
            if (checkDiff(bookId, "authors_count", String.valueOf(authors == null ? 0 : authors.size()), String.valueOf(sAuthors == null ? 0 : sAuthors.length()))) return true;
            
            JSONArray sBookshelves = server.optJSONArray("bookshelves");
            if (checkDiff(bookId, "bookshelves_count", String.valueOf(bookshelves == null ? 0 : bookshelves.size()), String.valueOf(sBookshelves == null ? 0 : sBookshelves.length()))) return true;
            
            JSONArray sSeries = server.optJSONArray("series");
            if (checkDiff(bookId, "series_count", String.valueOf(series == null ? 0 : series.size()), String.valueOf(sSeries == null ? 0 : sSeries.length()))) return true;

            // Check Anthology Titles
            JSONArray sAnthology = server.optJSONArray("anthologies");
            if (checkDiff(bookId, "anthologies_count", String.valueOf(anthology == null ? 0 : anthology.size()), String.valueOf(sAnthology == null ? 0 : sAnthology.length()))) return true;

            // Check Thumbnail MD5
            if (checkThumbnail) {
                String localMd5 = (thumbFile != null && thumbFile.exists()) ? Utils.calculateMD5(thumbFile) : "";
                String serverMd5 = getStringOrEmpty(server, "thumbnail_md5");
                if (checkDiff(bookId, "thumbnail_md5", localMd5, serverMd5)) return true;
            }

        } catch (Exception e) {
            Log.e("BC", "Error comparing book " + bookId, e);
            return true; // If anything fails, assume different to be safe
        }
        return false;
    }

    private boolean checkDiff(long bookId, String fieldName, String localValue, String serverValue) {
        return checkDiff(bookId, fieldName, localValue, serverValue, false);
    }

    private boolean checkDiff(long bookId, String fieldName, String localValue, String serverValue, boolean isDate) {
        String l = localValue.replace('\u00A0', ' ').trim();
        String s = serverValue.replace('\u00A0', ' ').trim();

        // If it's a date and one side is just a date (10 chars), truncate the other side for comparison
        if (isDate && !l.isEmpty() && !s.isEmpty()) {
            if (l.length() > 10 && s.length() == 10) {
                if (l.contains("-") && l.indexOf("-") == 4) {
                    l = l.substring(0, 10);
                }
            } else if (s.length() > 10 && l.length() == 10) {
                if (s.contains("-") && s.indexOf("-") == 4) {
                    s = s.substring(0, 10);
                }
            }
        }

        if (!l.equals(s)) {
            // If it's a date field and strings don't match, try a semantic date comparison
            if (isDate && !localValue.isEmpty() && !serverValue.isEmpty()) {
                Date dl = Utils.parseDate(localValue);
                Date ds = Utils.parseDate(serverValue);
                if (dl != null && ds != null) {
                    if (Utils.toSqlDateOnly(dl).equals(Utils.toSqlDateOnly(ds))) {
                        return false;
                    }
                } else {
                    // Log why semantic check failed to help debugging
                    if (dl == null) Log.d("BC", "Semantic check: failed to parse local date: " + localValue);
                    if (ds == null) Log.d("BC", "Semantic check: failed to parse server date: " + serverValue);
                }
            }
            Log.d("BC", "Diff found for book " + bookId + " on field '" + fieldValue(fieldName) + "'. Local: '" + localValue + "', Server: '" + serverValue + "'");
            return true;
        }
        return false;
    }

    private String fieldValue(String fieldName) {
        return fieldName;
    }

    private String normalizeServerDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("0")) {
            return "";
        }

        // If it's the ISO format, try to parse just the date part first if it's a T format
        String s = dateStr;
        if (s.contains("T")) {
            s = s.substring(0, s.indexOf("T"));
            Date d = Utils.parseDate(s);
            if (d != null) return Utils.toSqlDateOnly(d);
        }

        // Otherwise try normal parsing
        Date d = Utils.parseDate(dateStr);
        if (d != null) {
            // Check if it's a date-time or date-only based on the original string
            if (dateStr.contains(":") || (dateStr.length() > 10 && !dateStr.contains("T"))) {
                return Utils.toSqlDateTime(d);
            } else {
                return Utils.toSqlDateOnly(d);
            }
        }
        return dateStr;
    }

    private void notifyProgress(int current, int total, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mInstanceListener != null) {
                mInstanceListener.onApiProgress(mRequest, current, total, message);
            } else if (sActiveListener != null) {
                sActiveListener.onApiProgress(mRequest, current, total, message);
            }
        });
    }

    /**
     * Helper to notify progress safely on UI thread
     */
    private void notifyProgress(int current, int total) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mInstanceListener != null) {
                mInstanceListener.onApiProgress(mRequest, current, total);
            } else if (sActiveListener != null) {
                sActiveListener.onApiProgress(mRequest, current, total);
            }
        });
    }

    private void notifyComplete(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mInstanceListener != null) {
                mInstanceListener.onApiComplete(mRequest, message);
            } else if (sActiveListener != null) {
                sActiveListener.onApiComplete(mRequest, message);
            }
        });
    }

    private void notifyError(String error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mInstanceListener != null) {
                mInstanceListener.onApiError(mRequest, error);
            } else if (sActiveListener != null) {
                sActiveListener.onApiError(mRequest, error);
            }
        });
    }

    /**
     * Executes the API Sync Logic in background for a single book.
     */
    public void backupBook(int bookId, Cursor bookCursor, ArrayList<Author> authors, ArrayList<Bookshelf> bookshelves, ArrayList<Series> series, ArrayList<AnthologyTitle> anthology, File thumbnailFile) throws Exception {
        if (mApiToken.isEmpty()) {
            throw new Exception("No API Token set for backupBook");
        }
        ArrayList<String> fields = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();

        String thumbRequest = (thumbnailFile == null || !thumbnailFile.exists()) ? "DEL" : "";
        populateBookFields(fields, values, "", "", bookId, bookCursor, authors, bookshelves, series, anthology, thumbRequest);

        String response = connection("/book", METHOD_POST, fields, values, thumbnailFile);
        if (response == null) {
            throw new Exception("Upload failed for book ID " + bookId);
        }
        JSONObject json = new JSONObject(response);
        if (json.has("id")) {
            return;
        }
        throw new Exception("Upload failed for book ID " + bookId);
    }

    private void sendBatch(ArrayList<String> fields, ArrayList<String> values) throws Exception {
        String response = connection("/books", METHOD_POST, fields, values);
        if (response == null) {
            throw new Exception("Batch upload failed");
        }
    }

    public void backupThumbnail(long bookId, File thumbFile) throws Exception {
        String url = "/book/" + bookId + "/thumb";
        String response = connection(url, METHOD_POST, null, null, thumbFile);
        if (response == null) throw new Exception("Thumbnail upload failed");
    }

    public void deleteThumbnail(long bookId) throws Exception {
        String url = "/book/" + bookId + "/thumb";
        String response = connection(url, METHOD_DEL);
        if (response == null) throw new Exception("Thumbnail delete failed");
    }

    /**
     * Helper to safely get a string from a JSONObject, returning an empty string if the key
     * doesn't exist or is null.
     */
    private String getStringOrEmpty(JSONObject obj, String key) {
        if (obj.isNull(key)) {
            return "";
        }
        return obj.optString(key, "");
    }

    public void runDeleteBook() {
        try {
            if (mApiToken.isEmpty()) {
                throw new Exception("No API Token set for runDeleteBook");
            }
            String urlEndPoint = "/book/" + mBookId;
            String response = connection(urlEndPoint, METHOD_DEL);
            JSONObject book = new JSONObject(response);
            if (book.has("deleted_book_id")) {
                // Retrieve the count from the JSON response.
                String bcid = book.getString("deleted_book_id");
                notifyComplete("Deleted book " + bcid);
            } else {
                // Handle cases where the JSON is null or doesn't have the "count" key
                throw new Exception("Invalid response from /book/<id> endpoint. JSON: " + book);
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            final String errorMessage = e.getMessage();
            notifyError(errorMessage);
        }
    }

    public void runDeleteAll() {
        try {
            if (mApiToken.isEmpty()) {
                throw new Exception("No API Token set for runDeleteAll");
            }
            String response = connection("/books", METHOD_DEL);
            if (response == null) {
                throw new Exception("Failed to connect to delete_all endpoint");
            }
            JSONObject json = new JSONObject(response);
            if (json.has("status") && json.getString("status").equals("ok")) {
                notifyComplete("Online backup cleared");
            } else {
                throw new Exception("Invalid response from delete_all endpoint.");
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            final String errorMessage = e.getMessage();
            notifyError(errorMessage);
        }
    }

    public JSONObject getBook(boolean notifyComplete) {
        JSONObject book = null;
        try {
            if (mApiToken.isEmpty()) {
                throw new Exception("No API Token set for getAllBooks");
            }
            String urlEndPoint = "/book/" + mBookId;
            String response = connection(urlEndPoint, METHOD_GET);
            book = new JSONObject(response);
            if (book.has("bcid")) {
                // Retrieve the count from the JSON response.
                String bcid = book.getString("bcid");
                if (notifyComplete) {
                    notifyComplete(bcid);
                }
            } else {
                // Handle cases where the JSON is null or doesn't have the "count" key
                throw new Exception("Invalid response from /books/count endpoint. JSON: " + book);
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            final String errorMessage = e.getMessage();
            notifyError(errorMessage);
        }
        return book;
    }

    public JSONArray getAllBooks(boolean notifyComplete) {
        JSONArray books = null;
        try {
            if (mApiToken.isEmpty()) {
                throw new Exception("No API Token set for getAllBooks");
            }
            String response = connection("/books", METHOD_GET);
            books = new JSONArray(response);
            int total = books.length();
            if (notifyComplete) {
                notifyComplete(String.valueOf(total));
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            final String errorMessage = e.getMessage();
            notifyError(errorMessage);
        }
        return books;
    }

    public void runRestoreAll() {
        CatalogueDBAdapter db = null;
        DbSync.Synchronizer.SyncLock txLock = null;
        ArrayList<HashMap<String, String>> thumbnailTasks = new ArrayList<>();

        try {
            notifyProgress(0, 1, "Getting restore information..."); // Indicate that the process has started
            JSONArray books = getAllBooks(false);

            db = new CatalogueDBAdapter(mContext);
            db.open(); // Ensure DB is open

            int total = books.length();
            final int NOTIFY_INTERVAL = 50; // Update UI thread every 50 books
            final int BATCH_SIZE = 50;      // Commit transaction every 50 books
            txLock = db.startTransaction(true);

            for (int i = 0; i < total; i++) {
                if (mTaskContext.isTerminating()) break;

                if (i > 0 && i % BATCH_SIZE == 0) {
                    db.setTransactionSuccessful();
                    db.endTransaction(txLock);
                    txLock = db.startTransaction(true);
                }

                JSONObject bookJson = books.getJSONObject(i);
                BookData values = new BookData(mContext);

                long bcid = bookJson.getLong("bcid");
                String uuid = getStringOrEmpty(bookJson, "book_uuid");
                String title = getStringOrEmpty(bookJson, "title");

                values.putLong(CatalogueDBAdapter.KEY_ROW_ID, bcid);
                values.putString(CatalogueDBAdapter.KEY_TITLE, title);
                values.putString(CatalogueDBAdapter.KEY_ISBN, getStringOrEmpty(bookJson, "isbn"));
                values.putString(CatalogueDBAdapter.KEY_PUBLISHER, getStringOrEmpty(bookJson, "publisher"));
                values.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, getStringOrEmpty(bookJson, "date_published"));
                values.putString(CatalogueDBAdapter.KEY_RATING, getStringOrEmpty(bookJson, "rating"));
                values.putInt(CatalogueDBAdapter.KEY_READ, bookJson.optInt("read", 0));
                values.putString(CatalogueDBAdapter.KEY_PAGES, getStringOrEmpty(bookJson, "pages"));
                values.putString(CatalogueDBAdapter.KEY_NOTES, getStringOrEmpty(bookJson, "notes"));
                values.putString(CatalogueDBAdapter.KEY_LIST_PRICE, getStringOrEmpty(bookJson, "list_price"));
                values.putString(CatalogueDBAdapter.KEY_LOCATION, getStringOrEmpty(bookJson, "location"));
                values.putString(CatalogueDBAdapter.KEY_READ_START, getStringOrEmpty(bookJson, "read_start"));
                values.putString(CatalogueDBAdapter.KEY_READ_END, getStringOrEmpty(bookJson, "read_end"));
                values.putString(CatalogueDBAdapter.KEY_FORMAT, getStringOrEmpty(bookJson, "format"));
                values.putInt(CatalogueDBAdapter.KEY_SIGNED, bookJson.optInt("signed", 0));
                values.putString(CatalogueDBAdapter.KEY_DESCRIPTION, getStringOrEmpty(bookJson, "description"));
                values.putString(CatalogueDBAdapter.KEY_GENRE, getStringOrEmpty(bookJson, "genre"));
                values.putString(CatalogueDBAdapter.KEY_LANGUAGE, getStringOrEmpty(bookJson, "language"));
                values.putString(CatalogueDBAdapter.KEY_DATE_ADDED, getStringOrEmpty(bookJson, "date_added"));
                values.putString(CatalogueDBAdapter.KEY_LAST_UPDATE_DATE, getStringOrEmpty(bookJson, "last_update_date"));
                values.putString(CatalogueDBAdapter.KEY_BOOK_UUID, uuid);
                values.putString(CatalogueDBAdapter.KEY_LOANED_TO, getStringOrEmpty(bookJson, "loaned_to"));
                values.putInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK, bookJson.optInt("anthology", 0));

                JSONArray authorsJson = bookJson.optJSONArray("authors");
                if (authorsJson == null || authorsJson.length() == 0) {
                    continue;
                }
                ArrayList<Author> authors = new ArrayList<>();
                for (int author_i = 0; author_i < authorsJson.length(); author_i++) {
                    try {
                        JSONObject authorJson = authorsJson.getJSONObject(author_i);
                        Author author = new Author(authorJson.optLong("bcid", 0), getStringOrEmpty(authorJson, "family_name"), getStringOrEmpty(authorJson, "given_names"));
                        authors.add(author);
                    } catch (Exception e) {
                        Log.e("BookCatalogueAPI", "Failed to parse or insert author for book " + title, e);
                    }
                }
                values.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, authors);

                JSONArray seriesJson = bookJson.optJSONArray("series");
                if (seriesJson != null) {
                    ArrayList<Series> series = new ArrayList<>();
                    for (int series_i = 0; series_i < seriesJson.length(); series_i++) {
                        try {
                            JSONObject seriesEntryJson = seriesJson.getJSONObject(series_i);
                            Series seriesEntry = new Series(seriesEntryJson.optLong("bcid", 0), getStringOrEmpty(seriesEntryJson, "series_name"), getStringOrEmpty(seriesEntryJson, "series_num"));
                            series.add(seriesEntry);
                        } catch (Exception e) {
                            Log.e("BookCatalogueAPI", "Failed to parse or insert series for book " + title, e);
                        }
                    }
                    Utils.pruneSeriesList(series);
                    Utils.pruneList(db, series);
                    values.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, series);
                }

                JSONArray bookshelvesJson = bookJson.optJSONArray("bookshelves");
                if (bookshelvesJson != null) {
                    StringBuilder bookshelves_list = new StringBuilder();
                    for (int bookshelf_i = 0; bookshelf_i < bookshelvesJson.length(); bookshelf_i++) {
                        try {
                            JSONObject bookshelfJson = bookshelvesJson.getJSONObject(bookshelf_i);
                            String name = getStringOrEmpty(bookshelfJson, "bookshelf");
                            String encoded_name = Utils.encodeListItem(name, BookAbstract.BOOKSHELF_SEPARATOR);
                            if (bookshelves_list.length() == 0) {
                                bookshelves_list = new StringBuilder(encoded_name);
                            } else {
                                bookshelves_list.append(BookAbstract.BOOKSHELF_SEPARATOR).append(encoded_name);
                            }

                        } catch (Exception e) {
                            Log.e("BookCatalogueAPI", "Failed to parse or insert bookshelf for book " + title, e);
                        }
                    }
                    values.setBookshelfList(bookshelves_list.toString());
                }

                try {
                    boolean exists = false;
                    if (bcid > 0) {
                        exists = db.checkBookExists(bcid);
                    }
                    if (exists) {
                        db.updateBook(bcid, values, CatalogueDBAdapter.BOOK_UPDATE_SKIP_PURGE_REFERENCES | CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                    } else {
                        // Always import empty IDs...even if they are duplicates.
                        if (bcid > 0) {
                            bcid = db.createBook(bcid, values, CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                        } else {
                            bcid = db.createBook(values, CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
                        }
                    }
                    values.putString(CatalogueDBAdapter.KEY_ROW_ID, Long.toString(bcid));

                    // Defer thumbnail download
                    String backup_filename = getStringOrEmpty(bookJson, "thumbnail");
                    if (!backup_filename.isEmpty()) {
                        HashMap<String, String> task = new HashMap<>();
                        task.put("bcid", Long.toString(bcid));
                        task.put("url", backup_filename);
                        task.put("uuid", uuid);
                        String md5 = getStringOrEmpty(bookJson, "thumbnail_md5");
                        if (!md5.isEmpty()) {
                            task.put("md5", md5);
                        }
                        thumbnailTasks.add(task);
                    }

                    if (values.containsKey(CatalogueDBAdapter.KEY_LOANED_TO) && !values.get(CatalogueDBAdapter.KEY_LOANED_TO).equals("")) {
                        db.deleteLoan(bcid, false);
                        db.createLoan(values, false);
                    }

                    if (values.containsKey(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK)) {
                        int anthology;
                        try {
                            anthology = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK));
                        } catch (Exception e) {
                            anthology = 0;
                        }
                        if (anthology != 0) {
                            int id = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ROW_ID));
                            // We have anthology details, delete the current details.
                            db.deleteAnthologyTitles(id, false);
                            int oldi = 0;
                            String anthology_titles = values.getString("anthology_titles");
                            try {
                                int anthology_i = anthology_titles.indexOf("|", oldi);
                                while (anthology_i > -1) {
                                    String extracted_title = anthology_titles.substring(oldi, anthology_i).trim();

                                    int anthology_j = extracted_title.indexOf("*");
                                    if (anthology_j > -1) {
                                        String anthology_title = extracted_title.substring(0, anthology_j).trim();
                                        String anthology_author = extracted_title.substring((anthology_j + 1)).trim();
                                        db.createAnthologyTitle(id, anthology_author, anthology_title, true, false);
                                    }
                                    oldi = anthology_i + 1;
                                    anthology_i = anthology_titles.indexOf("|", oldi);
                                }
                            } catch (NullPointerException e) {
                                //do nothing. There are no anthology titles
                            }
                        }
                    }
                } catch (Exception e) {
                    Logger.logError(e, "Import at row " + i);
                }
                // Throttle UI updates to prevent ANR
                if ((i + 1) % NOTIFY_INTERVAL == 0 || (i + 1) == total) {
                    notifyProgress(i + 1, total, "books restored");
                }
            }
            // Commit the final batch
            db.setTransactionSuccessful();
            db.endTransaction(txLock);
            txLock = null; // Mark transaction as ended

            // --- Download Thumbnails ---
            int skippedThumbs = total - thumbnailTasks.size();
            if (thumbnailTasks.isEmpty()) {
                notifyProgress(total, total, "thumbnails restored");
            }
            for (int i = 0; i < thumbnailTasks.size(); i++) {
                HashMap<String, String> task = thumbnailTasks.get(i);
                try {
                    String url = task.get("url");
                    String uuid = task.get("uuid");
                    String bcid = task.get("bcid");
                    String remoteMd5 = task.get("md5");

                    File permanentFile;
                    if (uuid != null && !uuid.isEmpty()) {
                        permanentFile = CatalogueDBAdapter.fetchThumbnailByUuid(uuid);
                    } else {
                        permanentFile = CatalogueDBAdapter.fetchThumbnailByUuid(bcid);
                    }

                    // Optimization: Check if the file already exists and if MD5 matches
                    if (permanentFile.exists() && remoteMd5 != null) {
                        String localMd5 = Utils.calculateMD5(permanentFile);
                        if (remoteMd5.equalsIgnoreCase(localMd5)) {
                            // Already have it and it matches? Then, skip download
                            // Notify progress for thumbnail download as well
                            notifyProgress(skippedThumbs + i + 1, total, "thumbnails restored");
                            continue;
                        }
                    }

                    String filename = Utils.saveThumbnailFromUrl(url, "");
                    if (!filename.isEmpty()) {
                        File downloadedFile = new File(filename);

                        // Ensure parent directory exists
                        File parent = permanentFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            parent.mkdirs();
                        }

                        // Move the file to its permanent location. Try to rename first, then copy if necessary.
                        boolean moved = downloadedFile.renameTo(permanentFile);
                        if (!moved) {
                            try {
                                Utils.copyFile(downloadedFile, permanentFile);
                                //noinspection ResultOfMethodCallIgnored
                                downloadedFile.delete();
                            } catch (IOException e) {
                                Log.e("BookCatalogueAPI", "Failed to copy thumbnail for UUID/ID " + (uuid != null ? uuid : bcid), e);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("BookCatalogueAPI", "Failed to download or save thumbnail", e);
                }
                // Notify progress for thumbnail download as well
                notifyProgress(skippedThumbs + i + 1, total, "thumbnails restored");
            }
            notifyComplete("Restore completed successfully.");
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "Restore failed", e);
            notifyError("Restore failed: " + e.getMessage());
        } finally {
            // --- ROBUST FINALLY BLOCK ---
            // This ensures the transaction is always closed, even on error.
            if (db != null && txLock != null) {
                db.endTransaction(txLock);
            }
            // Close the database connection if it's open
            if (db != null) {
                db.close();
            }
        }
    }

    public void runInfoCount() {
        try {
            if (mApiToken.isEmpty()) {
                // It might be better to try a login() call here, but for now, we'll error out.
                throw new Exception("No API Token set for runInfoCount");
            }
            String response = connection("/books/count", METHOD_GET);
            if (response == null) {
                throw new Exception("Failed to connect to count endpoint");
            }
            JSONObject json = new JSONObject(response);
            if (json.has("count")) {
                // Retrieve the count from the JSON response.
                String bookCount = json.getString("count");
                if (json.has("subscription_expiry")) {
                    mPrefs.setSubscriptionExpiry(json.getString("subscription_expiry"));
                }
                notifyComplete(bookCount);
            } else {
                // Handle cases where the JSON is null or doesn't have the "count" key
                throw new Exception("Invalid response from /books/count endpoint. JSON: " + json);
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            final String errorMessage = e.getMessage();
            notifyError(errorMessage);
        }
    }

    public void runInfoLast() {
        try {
            if (mApiToken.isEmpty()) {
                // It might be better to try a login() call here, but for now, we'll error out.
                throw new Exception("No API Token set for runInfoLast");
            }
            String response = connection("/books/last_backup", METHOD_GET);
            if (response == null) {
                throw new Exception("Failed to connect to last_backup endpoint");
            }
            JSONObject json = new JSONObject(response);
            if (json.has("last_backup")) {
                // Retrieve the last_backup from the JSON response.
                String lastBackup = json.getString("last_backup");
                notifyComplete(lastBackup);
            } else {
                // Handle cases where the JSON is null or doesn't have the "last_backup" key
                throw new Exception("Invalid response from /books/last_backup endpoint. JSON: " + json);
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            final String errorMessage = e.getMessage();
            notifyError(errorMessage);
        }
    }

    public String connection(String urlEndPoint, String method) {
        File thumbnailFile = null;
        ArrayList<String> fields = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        return connection(urlEndPoint, method, fields, values, thumbnailFile);
    }

    public String connection(String urlEndPoint, String method, ArrayList<String> fields, ArrayList<String> values) {
        File thumbnailFile = null;
        return connection(urlEndPoint, method, fields, values, thumbnailFile);
    }

    public String connection(String urlEndPoint, String method, ArrayList<String> fields, ArrayList<String> values, File thumbnailFile) {
        int deadlockRetries = 3;
        while (true) {
            HttpURLConnection conn = null;
            try {
                String boundary = UUID.randomUUID().toString();
                String urlString = BASE_URL + urlEndPoint;

                conn = (HttpURLConnection) new URL(urlString).openConnection();
                conn.setRequestMethod(method);
                conn.setRequestProperty("Accept", "application/json");
                conn.setReadTimeout(15000); // 15 seconds
                conn.setConnectTimeout(15000); // 15 seconds
                if (mApiToken != null && !mApiToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + mApiToken);
                }

                if (method.equals("POST")) {
                    conn.setDoOutput(true); // Allow sending a body for POST
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    try (OutputStream os = conn.getOutputStream(); PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

                        if (fields != null && values != null) {
                            for (int i = 0; i < fields.size(); i++) {
                                addFormField(writer, boundary, fields.get(i), values.get(i));
                            }
                        }

                        // File Upload
                        if (thumbnailFile != null && thumbnailFile.exists()) {
                            addFilePart(writer, os, boundary, "thumbnail", thumbnailFile);
                        }
                        writer.append("--").append(boundary).append("--").append("\r\n").flush();
                    }
                } else {
                    // For GET requests, we don't send a body.
                    conn.setDoOutput(false);
                }

                int responseCode = conn.getResponseCode();
                String response;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    response = readStream(conn.getInputStream());
                    if (response.isEmpty()) return null; // Handle empty success response
                    return response;
                } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && retry) {
                    login();
                    retry = false;
                    continue; // Retry after login
                } else {
                    // If it's an error, read the error stream.
                    response = readStream(conn.getErrorStream());
                    Log.e("BookCatalogueAPI", "Error Response: " + response);

                    // Check for deadlock and retry
                    if (responseCode == 500 && response.contains("Deadlock found") && deadlockRetries > 0) {
                        deadlockRetries--;
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    // Propagate the error so the calling method knows it failed.
                    throw new Exception("Server returned error code " + responseCode + ": " + response);
                }
            } catch (Exception e) {
                Log.e("BookCatalogueAPI", "Connection Error", e);
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    @Override
    public void run(SimpleTaskContext taskContext) throws Exception {
        this.mTaskContext = taskContext;
        if (mEmail.isEmpty()) {
            return;
        }

        if (mRequest.equals(REQUEST_LOGIN) || mApiToken.isEmpty()) {
            try {
                login();
            } catch (Exception e) {
                return;
            }
            // After login, re-check if the token was successfully retrieved.
            if (mApiToken.isEmpty()) {
                throw new Exception("Login required, but failed to get API token.");
            }
        }

        if (mRequest.equals(REQUEST_INFO_COUNT)) {
            runInfoCount();
        }

        if (mRequest.equals(REQUEST_INFO_LAST)) {
            runInfoLast();
        }

        if (mRequest.equals(REQUEST_BACKUP_ALL)) {
            runFullBackup();
        }

        if (mRequest.equals(REQUEST_RESTORE_ALL)) {
            runRestoreAll();
        }

        if (mRequest.equals(REQUEST_BACKUP_BOOK)) {
            runBackupBook();
        }

        if (mRequest.equals(REQUEST_DELETE_BOOK)) {
            runDeleteBook();
        }

        if (mRequest.equals(REQUEST_DELETE_ALL)) {
            notifyProgress(0, 1, "Clearing online backup...");
            runDeleteAll();
        }

    }

    @Override
    public void onFinish(Exception e) {

    }

    /**
     * Cleanup!
     */
    public void onDestroy() {
        mSyncQueue.finish();
    }

    private static class ThumbnailTask {
        long id;
        File file;

        ThumbnailTask(long id, File file) {
            this.id = id;
            this.file = file;
        }
    }

    // Callback interface to update the UI (MainMenu)
    public interface ApiListener {
        void onApiProgress(String request, int current, int total, String message);

        void onApiProgress(String request, int current, int total);

        void onApiComplete(String request, String message);

        void onApiError(String request, String error);
    }

}
