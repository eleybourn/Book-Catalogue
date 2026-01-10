package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.data.AnthologyTitle;
import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.data.Bookshelf;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

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
    private static String mEmail;
    private static boolean mOptIn;
    private static String mApiToken;
    private static BookCataloguePreferences mPrefs;
    private final Context mContext;
    private final String mRequest;
    private int mSuccessCount = 0;
    private int mErrorCount = 0;
    private SimpleTaskContext mTaskContext;
    private ApiListener mListener;

    public BookCatalogueAPI(String request, Context context, ApiListener listener) {
        this.mContext = context;
        this.mRequest = request;
        this.mListener = listener;
        mPrefs = new BookCataloguePreferences();
        mEmail = mPrefs.getAccountEmail();
        mOptIn = mPrefs.getAccountOptIn();
        mApiToken = mPrefs.getAccountApiToken();

        mSyncQueue.enqueue(this);
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

    private static float getFloat(Cursor c, String columnName) {
        int index = c.getColumnIndex(columnName);
        if (index > -1 && !c.isNull(index)) {
            return c.getFloat(index);
        }
        return 0.0f;
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
                        mSuccessCount++;
                    } catch (Exception e) {
                        // Log the specific error for this book, but continue the loop
                        System.err.println("Error syncing book index " + count + ": " + e.getMessage());
                        mErrorCount++;
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
        if (mListener != null) {
            // We need a handler to jump to main thread,
            // but SimpleTaskQueue doesn't expose one easily inside 'run'.
            // In standard Android, we can use the Main Looper:
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    mListener.onApiProgress(REQUEST_FULL_BACKUP, current, total)
            );
        }
    }

    /**
     * Executes the API Sync Logic in background
     */
    public boolean backupBook(int bookId, Cursor bookCursor,
                              ArrayList<Author> authors, ArrayList<Bookshelf> bookshelves,
                              ArrayList<Series> series, ArrayList<AnthologyTitle> anthology,
                              File thumbnailFile) throws Exception {
        String title = "";
        if (mApiToken.isEmpty()) {
            // It might be better to try a login() call here, but for now, we'll error out.
            throw new Exception("No API Token set for runFullBackup");
        }
        ArrayList<String> fields = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        fields.add("book_id");
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
            return true;
        }
        throw new Exception("Upload failed for book ID " + bookId + " (" + title + "): ");
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
                if (mListener != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            mListener.onApiComplete(REQUEST_COUNT, bookCount)
                    );
                }
            } else {
                // Handle cases where the JSON is null or doesn't have the "count" key
                throw new Exception("Invalid response from /books/count endpoint. JSON: " + json);
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            if (mListener != null) {
                final String errorMessage = e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        mListener.onApiError(REQUEST_COUNT, errorMessage)
                );
            }
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
                if (mListener != null) {
                    // Use a handler to post the result back to the UI thread
                    new Handler(Looper.getMainLooper()).post(() ->
                            mListener.onApiComplete(REQUEST_LAST_BACKUP, lastBackup)
                    );
                }
            } else {
                // Handle cases where the JSON is null or doesn't have the "last_backup" key
                throw new Exception("Invalid response from /books/last_backup endpoint. JSON: " + json);
            }
        } catch (Exception e) {
            Log.e("BookCatalogueAPI", "API failed", e);
            if (mListener != null) {
                final String errorMessage = e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        mListener.onApiError(REQUEST_LAST_BACKUP, errorMessage)
                );
            }
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
            // TODO: DELETE Log.d
            Log.d("BookCatalogueAPI", "URL: " + urlString);

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
            Log.d("BookCatalogueAPI", "responseCode: " + responseCode);
            String response;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                response = readStream(conn.getInputStream());
                if (response.isEmpty()) return null; // Handle empty success response
                return new JSONObject(response);
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

        if (mApiToken.isEmpty()) {
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
