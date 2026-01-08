package com.eleybourn.bookcatalogue;

import android.database.Cursor;

import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import com.eleybourn.bookcatalogue.data.AnthologyTitle;
import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.data.Bookshelf;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;

public class BookCatalogueSync {

    private static final SimpleTaskQueue mSyncQueue = new SimpleTaskQueue("BookCatalogueSyncQueue");
    private static final String BASE_URL = "https://book-catalogue.com/api";
    private static final String TAG = "BookSync";

    /**
     * Performs the Login API call.
     *
     * @param email The email from Google OAuth
     * @param optIn 0 or 1
     * @return The API Token string
     * @throws Exception if login fails
     */
    public static String login(String email, boolean optIn) throws Exception {
        String urlString = BASE_URL + "/login";
        String boundary = UUID.randomUUID().toString();

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Accept", "application/json");

        // Save to Preferences
        BookCataloguePreferences prefs = new BookCataloguePreferences();
        prefs.setAccountOptIn(optIn);
        prefs.setAccountEmail(email);

        try (OutputStream os = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

            addFormField(writer, boundary, "email", email);
            addFormField(writer, boundary, "opt-in", optIn ? "1" : "0");
            writer.append("--").append(boundary).append("--").append("\r\n").flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String response = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(response);
            String api_token = "";
            if (json.has("api_token")) {
                api_token = json.getString("api_token");
            } else if (json.has("user")) {
                api_token = json.getJSONObject("user").getString("api_token");
            }
            prefs.setAccountApiToken(api_token);
            return api_token;
        }
        throw new Exception("Login failed: " + responseCode);
    }

    /**
     * Executes the API Sync Logic in background
     */
    public static void performCloudSync(String email, boolean optIn) {
        // Create the task
        BookCatalogueSyncTask syncTask = new BookCatalogueSyncTask(email, optIn, new BookCatalogueSyncTask.SyncListener() {
            @Override
            public void onSyncProgress(int current, int total) {
                // Optional: Show a progress bar or toast
                // Toast.makeText(MainMenu.this, "Uploaded " + current + "/" + total, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSyncComplete(String message) {
                //Toast.makeText(MainMenu.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onSyncError(String error) {
                //Toast.makeText(MainMenu.this, "Backup Failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
        mSyncQueue.enqueue(syncTask);
    }

    /**
     * Uploads a book record.
     * Note: You will need to map your internal Book object to these parameters.
     */
    public static void uploadBook(String apiToken, int bookId, Cursor bookCursor,
                                  ArrayList<Author> authors, ArrayList<Bookshelf> bookshelves,
                                  ArrayList<Series> series, ArrayList<AnthologyTitle> anthology,
                                  File thumbnailFile) throws Exception {
        String urlString = BASE_URL + "/book";
        String boundary = UUID.randomUUID().toString();

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiToken);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Accept", "application/json");

        try (OutputStream os = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

            addFormField(writer, boundary, "book_id", String.valueOf(bookId));
            // authors[]:<comma separated string - ID, family, given>
            // authors[]:<repeat as many times as needed>
            if (authors != null) {
                for (Author author : authors) {
                    addFormField(writer, boundary, "authors[]", author.id + ", " + author.familyName + ", " + author.givenNames);
                }
            }
            addFormField(writer, boundary, "title", bookCursor.getString(bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)));
            addFormField(writer, boundary, "isbn", bookCursor.getString(bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN)));
            addFormField(writer, boundary, "publisher", bookCursor.getString(bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER)));
            addFormField(writer, boundary, "date_published", bookCursor.getString(bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)));
            addFormField(writer, boundary, "rating", bookCursor.getString(bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING)));
            // bookshelves[]:<comma separated string - bookCatalogue bookshelf ID, bookshelf name>
            // bookshelves[]:<repeat as many times as needed>
            if (bookshelves != null) {
                for (Bookshelf bookshelf : bookshelves) {
                    addFormField(writer, boundary, "bookshelves[]", bookshelf.id + ", " + bookshelf.name);
                }
            }
            addFormField(writer, boundary, "read", bookCursor.getString(bookCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ)));
            // series[]:<comma separated string - bookCatalogue series ID, series number, series title>
            // series[]:<repeat as many times as needed>
            if (series != null) {
                for (Series seriesEntry : series) {
                    addFormField(writer, boundary, "series[]", seriesEntry.id + ", " + seriesEntry.num + ", " + seriesEntry.name);
                }
            }
            addFormField(writer, boundary, "pages", getString(bookCursor, CatalogueDBAdapter.KEY_PAGES));
            addFormField(writer, boundary, "notes", getString(bookCursor, CatalogueDBAdapter.KEY_NOTES));
            addFormField(writer, boundary, "list_price", getString(bookCursor, CatalogueDBAdapter.KEY_LIST_PRICE));
            addFormField(writer, boundary, "location", getString(bookCursor, CatalogueDBAdapter.KEY_LOCATION));
            addFormField(writer, boundary, "read_start", getString(bookCursor, CatalogueDBAdapter.KEY_READ_START));
            addFormField(writer, boundary, "read_end", getString(bookCursor, CatalogueDBAdapter.KEY_READ_END));
            addFormField(writer, boundary, "format", getString(bookCursor, CatalogueDBAdapter.KEY_FORMAT));
            addFormField(writer, boundary, "signed", getInt(bookCursor, CatalogueDBAdapter.KEY_SIGNED) > 0 ? "1" : "0");
            addFormField(writer, boundary, "loaned_to", getString(bookCursor, CatalogueDBAdapter.KEY_LOANED_TO));
            addFormField(writer, boundary, "anthology", getInt(bookCursor, CatalogueDBAdapter.KEY_ANTHOLOGY_MASK) > 0 ? "1" : "0");
            // anthologies[]:<comma separated string - bookCatalogue anthology ID, anthology title>
            // anthologies[]:<repeat as many times as needed>
            // anthology_authors[]:<comma separated string - bookCatalogue author ID, family_name, given_names>
            // anthology_authors[]:<repeat as many times as needed - must have the same number of records as anthologies[]>
            if (anthology != null) {
                for (AnthologyTitle anthologyTitle : anthology) {
                    addFormField(writer, boundary, "anthologies[]", anthologyTitle.getId() + ", " + anthologyTitle.getTitle());
                    addFormField(writer, boundary, "anthology_authors[]", anthologyTitle.getAuthor().id + ", " + anthologyTitle.getAuthor().familyName + ", " + anthologyTitle.getAuthor().givenNames);
                }
            }
            addFormField(writer, boundary, "description", getString(bookCursor, CatalogueDBAdapter.KEY_DESCRIPTION));
            addFormField(writer, boundary, "genre", getString(bookCursor, CatalogueDBAdapter.KEY_GENRE));
            addFormField(writer, boundary, "language", getString(bookCursor, "language"));
            addFormField(writer, boundary, "date_added", getString(bookCursor, CatalogueDBAdapter.KEY_DATE_ADDED));
            addFormField(writer, boundary, "last_update_date", getString(bookCursor, "last_update_date"));
            addFormField(writer, boundary, "book_uuid", getString(bookCursor, "book_uuid"));

            // File Upload
            if (thumbnailFile != null && thumbnailFile.exists()) {
                addFilePart(writer, os, boundary, "thumbnail", thumbnailFile);
            }

            writer.append("--").append(boundary).append("--").append("\r\n");
            writer.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
            String error = readStream(conn.getErrorStream());
            throw new Exception("Upload failed for book ID " + bookId + " (" + responseCode + "): " + error);
        }
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

    private static void addFilePart(PrintWriter writer, OutputStream os, String boundary, String fieldName, File uploadFile) throws IOException {
        String fileName = uploadFile.getName();
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(fileName).append("\"\r\n");
        writer.append("Content-Type: application/octet-stream\r\n\r\n").flush();

        try (FileInputStream inputStream = new FileInputStream(uploadFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
        writer.append("\r\n").flush();
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
     * Cleanup!
     */
    public void onDestroy() {
        mSyncQueue.finish();
    }

}
