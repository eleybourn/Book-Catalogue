package com.eleybourn.bookcatalogue;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class BookCatalogueAPI implements SimpleTask {

    private static final SimpleTaskQueue mSyncQueue = new SimpleTaskQueue("BookCatalogueSyncQueue");
    private static final String BASE_URL = "https://book-catalogue.com/api";
    public static String REQUEST_LOGIN = "login";
    public static String REQUEST_COUNT = "count";
    public static String REQUEST_LAST_BACKUP = "last_backup";
    public static String REQUEST_FULL_BACKUP = "full_backup";
    public static String REQUEST_FULL_RESTORE = "restore";
    private static String mEmail;
    private static boolean mOptIn;
    private static String mApiToken;
    private static BookCataloguePreferences mPrefs;
    // Add a static field to hold the currently active listener.
    private static ApiListener sActiveListener;
    private final String mRequest;
    private SimpleTaskContext mTaskContext;
    private boolean retry = true;

    public BookCatalogueAPI(String request, ApiListener listener) {
        this.mRequest = request;
        sActiveListener = listener;
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

    private static String getDate(Cursor c, String columnName) {
        int index = c.getColumnIndex(columnName);
        if (index > -1 && !c.isNull(index)) {
            // Get the date as a long (Unix timestamp in milliseconds)
            long timestamp = c.getLong(index);

            // Avoid returning a date for timestamp 0, which often means "not set"
            if (timestamp == 0) {
                return "";
            }

            // Define the desired output format
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

            // Create a Date object and format it
            Date date = new Date(timestamp);
            return sdf.format(date);
        }
        return "";
    }

    private static String getString(Cursor c, String columnName) {
        int index = c.getColumnIndex(columnName);
        if (index > -1 && !c.isNull(index)) {
            return c.getString(index);
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
        if (value == null) value = ""; // Ensure we don't send nulls
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n");
        writer.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        writer.append(value).append("\r\n").flush();
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
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
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

            JSONObject json = connection("/login", fields, values);
            if (json.has("api_token")) {
                mApiToken = json.getString("api_token");
                mPrefs.setAccountApiToken(mApiToken);
                notifyComplete("Login successful");
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "Login failed", e);
        }
    }

    public void runFullBackup() {
        CatalogueDBAdapter db = mTaskContext.getDb();
        // Query all books.
        // Note: Using the raw cursor from the adapter provided by SimpleTaskContext
        try (Cursor bookCursor = db.fetchAllBooks()) {
            if (bookCursor != null && bookCursor.moveToFirst()) {
                int total = bookCursor.getCount();
                int count = 0;

                // Get column indices
                int idIndex = bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROW_ID);
                int uuidIndex = bookCursor.getColumnIndexOrThrow(DatabaseDefinitions.DOM_BOOK_UUID.name);

                do {
                    // Check if queue is killing us
                    if (mTaskContext.isTerminating()) break;

                    try {
                        int bookId = bookCursor.getInt(idIndex);
                        String uuid = bookCursor.getString(uuidIndex);
                        File thumbFile = CatalogueDBAdapter.fetchThumbnailByUuid(uuid);
                        ArrayList<Author> authors = db.getBookAuthorList(bookId);
                        ArrayList<Bookshelf> bookshelves = db.getBookBookshelfList(bookId);
                        ArrayList<Series> series = db.getBookSeriesList(bookId);
                        ArrayList<AnthologyTitle> anthology = db.getBookAnthologyTitleList(bookId);

                        // Upload
                        backupBook(bookId, bookCursor, authors, bookshelves, series, anthology, thumbFile);
                    } catch (Exception e) {
                        // Log the specific error for this book, but continue the loop
                        System.err.println("Error syncing book index " + count + ": " + e.getMessage());
                    }
                    count++;

                    notifyProgress(count, total);

                } while (bookCursor.moveToNext());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Helper to notify progress safely on UI thread
     */
    private void notifyProgress(int current, int total) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (sActiveListener != null) {
                sActiveListener.onApiProgress(mRequest, current, total);
            }
        });
    }

    private void notifyComplete(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (sActiveListener != null) {
                sActiveListener.onApiComplete(mRequest, message);
            }
        });
    }

    private void notifyError(String error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (sActiveListener != null) {
                sActiveListener.onApiError(mRequest, error);
            }
        });
    }

    /**
     * Executes the API Sync Logic in background
     */
    public void backupBook(int bookId, Cursor bookCursor,
                           ArrayList<Author> authors, ArrayList<Bookshelf> bookshelves,
                           ArrayList<Series> series, ArrayList<AnthologyTitle> anthology,
                           File thumbnailFile) throws Exception {
        String title;
        if (mApiToken.isEmpty()) {
            // It might be better to try a login() call here, but for now, we'll error out.
            throw new Exception("No API Token set for runFullBackup");
        }
        ArrayList<String> fields = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        fields.add("bcid");
        values.add(String.valueOf(bookId));
        // authors[]:<comma separated string - ID, family, given>
        if (authors != null) {
            for (Author author : authors) {
                fields.add("authors[]");
                values.add(author.id + ", " + author.familyName + ", " + author.givenNames);
            }
        }
        fields.add("title");
        title = getString(bookCursor, CatalogueDBAdapter.KEY_TITLE);
        values.add(title);
        fields.add("isbn");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_ISBN));
        fields.add("publisher");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_PUBLISHER));
        fields.add("date_published");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_DATE_PUBLISHED));
        fields.add("rating");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_RATING));
        // bookshelves[]:<comma separated string - bookCatalogue bookshelf ID, bookshelf name>
        // bookshelves[]:<repeat as many times as needed>
        if (bookshelves != null) {
            for (Bookshelf bookshelf : bookshelves) {
                fields.add("bookshelves[]");
                values.add(bookshelf.id + ", " + bookshelf.name);
            }
        }
        fields.add("read");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_READ));
        // series[]:<comma separated string - bookCatalogue series ID, series number, series title>
        // series[]:<repeat as many times as needed>
        if (series != null) {
            for (Series seriesEntry : series) {
                fields.add("series[]");
                values.add(seriesEntry.id + ", " + seriesEntry.num + ", " + seriesEntry.name);
            }
        }
        fields.add("pages");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_PAGES));
        fields.add("notes");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_NOTES));
        fields.add("list_price");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LIST_PRICE));
        fields.add("location");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LOCATION));
        fields.add("read_start");
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_READ_START));
        fields.add("read_end");
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_READ_END));
        fields.add("format");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_FORMAT));
        fields.add("signed");
        values.add(getInt(bookCursor, CatalogueDBAdapter.KEY_SIGNED) > 0 ? "1" : "0");
        fields.add("loaned_to");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LOANED_TO));
        fields.add("anthology");
        values.add(getInt(bookCursor, CatalogueDBAdapter.KEY_ANTHOLOGY_MASK) > 0 ? "1" : "0");
        // anthologies[]:<comma separated string - bookCatalogue anthology ID, anthology title>
        // anthologies[]:<repeat as many times as needed>
        // anthology_authors[]:<comma separated string - bookCatalogue author ID, family_name, given_names>
        // anthology_authors[]:<repeat as many times as needed - must have the same number of records as anthologies[]>
        if (anthology != null) {
            for (AnthologyTitle anthologyTitle : anthology) {
                fields.add("anthologies[]");
                values.add(anthologyTitle.getId() + ", " + anthologyTitle.getTitle());
                fields.add("anthology_authors[]");
                values.add(anthologyTitle.getAuthor().id + ", " + anthologyTitle.getAuthor().familyName + ", " + anthologyTitle.getAuthor().givenNames);
            }
        }
        fields.add("description");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_DESCRIPTION));
        fields.add("genre");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_GENRE));
        fields.add("language");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_LANGUAGE));
        fields.add("date_added");
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_DATE_ADDED));
        fields.add("last_update_date");
        values.add(getDate(bookCursor, CatalogueDBAdapter.KEY_LAST_UPDATE_DATE));
        fields.add("book_uuid");
        values.add(getString(bookCursor, CatalogueDBAdapter.KEY_BOOK_UUID));

        JSONObject json = connection("/book", fields, values, thumbnailFile);
        if (json != null && json.has("id")) {
            return;
        }
        throw new Exception("Upload failed for book ID " + bookId + " (" + title + "): ");
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

    public void runFullRestore() {
        if (mApiToken.isEmpty()) {
            notifyError("No API Token set for runFullRestore");
            return;
        }
        CatalogueDBAdapter db = null;
        DbSync.Synchronizer.SyncLock txLock = null;

        try {
            notifyProgress(0, 1); // Indicate that the process has started
            JSONArray books = connection("/books");
            if (books == null) {
                throw new Exception("Failed to fetch books from the server.");
            }

            db = mTaskContext.getDb();
            db.open(); // Ensure DB is open

            int total = books.length();
            Log.d("BookCatalogueAPI", "Importing " + total + " books");
            final int NOTIFY_INTERVAL = 10; // Update UI thread every 50 books
            final int BATCH_SIZE = 50;      // Commit transaction every 100 books
            txLock = db.startTransaction(true);

            for (int i = 0; i < total; i++) {
                if (mTaskContext.isTerminating()) break;

                if (i > 0 && i % BATCH_SIZE == 0) {
                    Log.d("BookCatalogueAPI", "Committing batch at book " + i);
                    db.setTransactionSuccessful();
                    db.endTransaction(txLock);
                    txLock = db.startTransaction(true);
                }

                JSONObject bookJson = books.getJSONObject(i);
                BookData values = new BookData();

                long bcid = bookJson.getLong("bcid");
                String uuid = getStringOrEmpty(bookJson, "book_uuid");
                String title = getStringOrEmpty(bookJson, "title");
                // Only log every Nth book to reduce logcat spam
                if (i % NOTIFY_INTERVAL == 0) {
                    Log.d("BookCatalogueAPI", "Importing book " + (i + 1) + "/" + total + ": " + title);
                }

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

                String backup_filename = getStringOrEmpty(bookJson, "thumbnail");
                if (!backup_filename.isEmpty()) {
                    String filename = Utils.saveThumbnailFromUrl(backup_filename, "");
                    if (!filename.isEmpty()) {
                        values.putString(CatalogueDBAdapter.KEY_THUMBNAIL, filename);
                    }
                }

                try {
                    boolean doUpdate = true;
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
                    notifyProgress(i + 1, total);
                }
            }
            // Commit the final batch
            Log.d("BookCatalogueAPI", "Committing final batch.");
            db.setTransactionSuccessful();
            notifyComplete("Restore completed successfully.");
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "Restore failed", e);
            notifyError("Restore failed: " + e.getMessage());
        } finally {
            // --- ROBUST FINALLY BLOCK ---
            // This ensures the transaction is always closed, even on error.
            if (db != null && txLock != null) {
                Log.d("BookCatalogueAPI", "Executing finally block: Ending transaction.");
                db.endTransaction(txLock);
            }
        }
    }

    public void getTotalBooks() {
        try {
            if (mApiToken.isEmpty()) {
                // It might be better to try a login() call here, but for now, we'll error out.
                throw new Exception("No API Token set for getTotalBooks");
            }
            ArrayList<String> fields = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();

            JSONObject json = connection("/books/count", fields, values);
            if (json != null && json.has("count")) {
                // Retrieve the count from the JSON response.
                String bookCount = json.getString("count");
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

    public void getLastBackup() {
        try {
            if (mApiToken.isEmpty()) {
                // It might be better to try a login() call here, but for now, we'll error out.
                throw new Exception("No API Token set for getTotalBooks");
            }
            ArrayList<String> fields = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();

            JSONObject json = connection("/books/last_backup", fields, values);
            if (json != null && json.has("last_backup")) {
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

    /**
     * Overloaded connection method for GET requests that return a JSONArray.
     */
    private JSONArray connection(String urlEndPoint) throws Exception {
        String urlString = BASE_URL + urlEndPoint;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setReadTimeout(15000); // 15 seconds
        conn.setConnectTimeout(15000); // 15 seconds
        conn.setRequestProperty("Authorization", "Bearer " + mApiToken);

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String response = readStream(conn.getInputStream());
            return new JSONArray(response);
        } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && retry) {
            login();
            retry = false;
            return connection(urlEndPoint);
        } else {
            String error = readStream(conn.getErrorStream());
            throw new IOException("Server returned non-OK status: " + responseCode + " " + error);
        }
    }

    public JSONObject connection(String urlEndPoint, ArrayList<String> fields, ArrayList<String> values) {
        File thumbnailFile = null;
        return connection(urlEndPoint, fields, values, thumbnailFile);
    }

    public JSONObject connection(String urlEndPoint, ArrayList<String> fields, ArrayList<String> values, File thumbnailFile) {
        String method;
        switch (urlEndPoint) {
            case "/login":
            case "/book":
                method = "POST";
                break;
            case "/books":
            case "/books/last_backup":
            case "/books/count":
            default:
                method = "GET";
                break;
        }
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
                try (OutputStream os = conn.getOutputStream();
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

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
                return new JSONObject(response);
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && retry) {
                login();
                retry = false;
                return connection(urlEndPoint, fields, values, thumbnailFile);
            } else {
                // If it's an error, read the error stream.
                response = readStream(conn.getErrorStream());
                Log.e("BookCatalogueAPI", "Error Response: " + response);
                // Propagate the error so the calling method knows it failed.
                throw new Exception("Server returned error code " + responseCode + ": " + response);
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "Connection Error", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    @Override
    public void run(SimpleTaskContext taskContext) throws Exception {
        this.mTaskContext = taskContext;

        if (mRequest.equals(REQUEST_LOGIN) || mApiToken.isEmpty()) {
            login();
            // After login, re-check if the token was successfully retrieved.
            if (mApiToken.isEmpty()) {
                throw new Exception("Login required, but failed to get API token.");
            }
        }

        if (mRequest.equals(REQUEST_COUNT)) {
            getTotalBooks();
        }

        if (mRequest.equals(REQUEST_LAST_BACKUP)) {
            getLastBackup();
        }

        if (mRequest.equals(REQUEST_FULL_BACKUP)) {
            runFullBackup();
        }

        if (mRequest.equals(REQUEST_FULL_RESTORE)) {
            runFullRestore();
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

    // Callback interface to update the UI (MainMenu)
    public interface ApiListener {
        void onApiProgress(String request, int current, int total);

        void onApiComplete(String request, String message);

        void onApiError(String request, String error);
    }

}
