package com.eleybourn.bookcatalogue;

import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class BookCatalogueSync {

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

        try (OutputStream os = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

            addFormField(writer, boundary, "email", email);
            addFormField(writer, boundary, "opt-in", optIn ? "1" : "0");
            writer.append("--").append(boundary).append("--").append("\r\n");
            writer.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(response);
            // Assuming the JSON structure is { "api_token": "..." } or { "user": { "api_token": "..." } }
            // Adjust based on exact server response. This assumes root level or user object:
            if (json.has("api_token")) {
                return json.getString("api_token");
            } else if (json.has("user")) {
                return json.getJSONObject("user").getString("api_token");
            }
        }
        throw new Exception("Login failed: " + responseCode);
    }

    /**
     * Uploads a book record.
     * Note: You will need to map your internal Book object to these parameters.
     */
    public static void uploadBook(String apiToken, int bookId, String title, List<String> authors, File thumbnailFile) throws Exception {
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

            // Mandatory Fields
            addFormField(writer, boundary, "book_id", String.valueOf(bookId));
            addFormField(writer, boundary, "title", title);

            // Example of Array Fields (Repeat the field name for every item)
            // authors[]:<comma seperated string - ID, family, given>
            if (authors != null) {
                for (String authorString : authors) {
                    addFormField(writer, boundary, "authors[]", authorString);
                }
            }

            // ... Add other fields (isbn, publisher, etc) here using addFormField ...

            // File Upload
            if (thumbnailFile != null && thumbnailFile.exists()) {
                addFormField(writer, boundary, "book_uuid", UUID.randomUUID().toString()); // Mandatory if uploading thumb
                addFilePart(writer, os, boundary, "thumbnail", thumbnailFile);
            }

            writer.append("--").append(boundary).append("--").append("\r\n");
            writer.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 201) {
            String error = readStream(conn.getErrorStream());
            throw new Exception("Upload failed (" + responseCode + "): " + error);
        }
    }

    private static void addFormField(PrintWriter writer, String boundary, String name, String value) {
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n");
        writer.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        writer.append(value).append("\r\n");
        writer.flush();
    }

    private static void addFilePart(PrintWriter writer, OutputStream os, String boundary, String fieldName, File uploadFile) throws IOException {
        String fileName = uploadFile.getName();
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(fileName).append("\"\r\n");
        writer.append("Content-Type: application/octet-stream\r\n\r\n");
        writer.flush();

        try (FileInputStream inputStream = new FileInputStream(uploadFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
        os.flush();
        writer.append("\r\n");
        writer.flush();
    }

    private static String readStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        return result.toString();
    }
}
