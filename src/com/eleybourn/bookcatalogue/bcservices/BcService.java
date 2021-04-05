package com.eleybourn.bookcatalogue.bcservices;

import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.SearchThread.BookSearchResults;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class BcService {

	// JSON fields for book details.
	private static final String ISBN = "isbn";
	private static final String TITLE = "title";
	private static final String DATE_PUBLISHED = "date_published";
	private static final String PAGES = "pages";
	private static final String FORMAT = "format";
	private static final String DESCRIPTION = "description";
	private static final String LANGUAGE = "language";
	private static final String GENRE = "genre";
	private static final String AUTHORS = "authors";
	private static final String FULL_NAME = "full_name";
	private static final String AUTHOR_POSITION = "author_position";
	private static final String PUBLISHER = "publisher";
	private static final String COVER_URL = "default_thumbnail";
	private static final String THUMBNAILS = "thumbnails";
	private static final String SERIES = "series";
	private static final String SERIES_NAME = "series_name";
	private static final String SERIES_NUM = "series_num";
	private static final String SERIES_POSITION = "series_position";
	private static final String CRLF = "\r\n";
	/** Sync object */
	private static final Object mApiSync = new Object();
	/** Last time API was called; limit to 1 per second */
	private static long mLastApiRequestTime = 0L;

	/**
	 * Make a call to a Book Catalogue service; assumes the caller has waited for appropriate delay
	 *
	 * @param url		Service URL
	 * @param method	Method
	 * @param params	List of params
	 * @return			JSON result
	 */
	@SuppressWarnings("WeakerAccess")
	public static List<JSONObject> makeServiceCall(
			String url, Methods method,
			List<ParamsPair> params) {
		// Open the URL and return a stream
		InputStream is;
		try {
			is = openUrl(url, method, params);
		} catch (Exception e) {
			Logger.logError(e, "Failed to open URL: " + url);
			return null;
		}
		// Read the entire stream
		String response = null;
		try {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(is, StandardCharsets.UTF_8),8192);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			is.close();
			response = sb.toString();
		} catch (Exception e) {
			Logger.logError(e, "Exception while reading response");
		}
		// Convert result string to JSON
		List<JSONObject> jsonList = new ArrayList<>();
		// Check if it is a JSONArray
		if(response.charAt(0) == '[') {
			try {
				JSONArray jsonArray = new JSONArray(response);
				for(int i = 0; i < jsonArray.length(); i++) {
					JSONObject json = jsonArray.getJSONObject(i);
					jsonList.add(json);
				}
			} catch (JSONException e) {
				Logger.logError(e);
				throw new RuntimeException("Unable to parse API result", e);
			}
		} else {
			try {
				JSONObject json = new JSONObject(response);
				jsonList.add(json);
			} catch (JSONException e) {
				Logger.logError(e);
				throw new RuntimeException("Unable to parse API result", e);
			}

				//}
			//if (!json.get(API_STATUS).equals(API_OK)) {
			//	throw new RuntimeException("API call failed: " + json.get(API_REASON));
			//}
		}
		return jsonList;
	}

	/**
	 * Use mLastRequestTime to determine how long until the next request is allowed; and
	 * update mLastRequestTime this needs to be synchronized across threads.
	 * <p>
	 * Note that as a result of this approach mLastRequestTime may in fact be
	 * in the future; callers to this routine effectively allocate time slots.
	 * <p>
	 * This method will sleep() until it can make a request; if ten threads call this
	 * simultaneously, one will return immediately, one will return 1 second later, another
	 * two seconds etc.
	 */
	@SuppressWarnings("WeakerAccess")
	public static void waitUntilRequestAllowed() {
		long now = System.currentTimeMillis();
		long wait;
		synchronized (mApiSync) {
			wait = 1000 - (now - mLastApiRequestTime);
			//
			// mLastRequestTime must be updated while synchronized. As soon as this
			// block is left, another block may perform another update.
			//
			if (wait < 0)
				wait = 0;
			mLastApiRequestTime = now + wait;
		}
		if (wait > 0) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException ignored) {
			}
		}
	}

	private static final String UTF8 = "UTF-8";
	private static final int API_TIMEOUT = 30000;

	private static InputStream openUrl(
			String urlString, Methods method,
			List<ParamsPair> params) throws IOException
	{
		URL url = new URL(urlString);
		HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
		// Tell the URLConnection to use a SocketFactory from our SSLContext
		//urlConnection.setRequestMethod(method == Methods.Post ? "POST" : "GET");
		String requestMethod;
		switch(method) {
			case Post:
				requestMethod = "POST";
				break;
			case Delete:
				requestMethod = "DELETE";
				break;
			default:
				requestMethod = "GET";
				break;
		}
		urlConnection.setRequestMethod(requestMethod);
		urlConnection.setRequestProperty("Accept-Language", UTF8);
		// Set a timeout
		urlConnection.setConnectTimeout(API_TIMEOUT);
		urlConnection.setRequestProperty("Authorization", "Bearer " + BcBooksApi.getApiToken());

		if (method == Methods.Post) {
			urlConnection.setUseCaches(false);
			urlConnection.setDoOutput(true);
			urlConnection.setDoInput(true);
			urlConnection.setReadTimeout(API_TIMEOUT);
			// Set body type to form-data, and format body accordingly
			String boundary = "***" + System.currentTimeMillis() + "***";
			urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			OutputStream outputStream = urlConnection.getOutputStream();
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "utf-8");
			if (params != null) {
				Iterator<ParamsPair> it = params.iterator();
				while(it.hasNext()) {
					ParamsPair pair = it.next();
					String key = pair.getKey();
					outputStreamWriter.append("--" + boundary + CRLF);
					if (key.equals("thumbnail")) {
						addFilePart(key, pair.getValue(), outputStream, outputStreamWriter);
					} else {
						addFormField(key, pair.getValue(), outputStreamWriter);
					}
				}
			}
			outputStreamWriter.append("--" + boundary + "--").append(CRLF);
			outputStreamWriter.close();
		}
		int responseCode = urlConnection.getResponseCode();
		if (responseCode >= 300) {
			throw new RuntimeException("Unexpected response from the server: " + responseCode);
		}
		return urlConnection.getInputStream();
	}

	/**
	 * Inspired by https://stackoverflow.com/questions/34276466/simple-httpurlconnection-post-file-multipart-form-data-from-android-to-google-bl
	 * Adds a form field to the request
	 *
	 * @param name  field name
	 * @param value field value
	 */
	public static void addFormField(String name, String value, OutputStreamWriter writer) throws IOException {
		writer.append("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF);
		writer.append(CRLF);
		writer.append(value);
		writer.append(CRLF);
		writer.flush();
	}

	/**
	 * Adds a upload file section to the request
	 *
	 * @param fieldName  name attribute in <input type="file" name="..." />
	 * @param filePath a path to a File to be uploaded
	 * @throws IOException
	 */
	public static void addFilePart(String fieldName, String filePath, OutputStream outputStream, OutputStreamWriter writer)
			throws IOException {
		File thumbnail = new File(filePath);
		writer.append(
				"Content-Disposition: form-data; name=\"" + fieldName
						+ "\"; filename=\"" + thumbnail.getName() + "\"")
				.append(CRLF);
		writer.append(
				"Content-Type: image/jpeg")
				.append(CRLF);
		writer.append("Content-Transfer-Encoding: binary").append(CRLF);
		writer.append(CRLF);
		writer.flush();

		FileInputStream inputStream = new FileInputStream(thumbnail);
		byte[] buffer = new byte[4096];
		int bytesRead = -1;
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, bytesRead);
		}
		outputStream.flush();
		inputStream.close();
		writer.append(CRLF);
		writer.flush();
	}

	/**
	 * Convert the standard API book data to a Bundle suitable for use in search results.
	 *
	 * @param result			The JSON data for the book
	 * @param bookResults		{@link BookSearchResults} to contain data
	 * @param fetchThumbnail	True if thumbnail should be fetched.
	 *
	 * @throws JSONException	From JSON library
	 */
	@SuppressWarnings("WeakerAccess")
	public static void jsonResultToBookData(JSONObject result, BookSearchResults bookResults, boolean fetchThumbnail)
			throws JSONException
	{
		Bundle bookData = bookResults.data;
		Iterator<String> keysIter = result.keys();
		while (keysIter.hasNext()) {
			String key = keysIter.next();
			String value = result.getString(key);
			if(value == null) {
				continue;
			}
			switch (key) {
				case ISBN:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_ISBN, value);
					break;
				case TITLE:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_TITLE, value);
					break;
				case PUBLISHER:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_PUBLISHER, value);
					break;
				case DATE_PUBLISHED:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_DATE_PUBLISHED, value);
					break;
				case PAGES:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_PAGES, value);
					break;
				case FORMAT:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_FORMAT, value);
					break;
				case DESCRIPTION:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_DESCRIPTION, value);
					break;
				case LANGUAGE:
					addIfNotPresent(bookData, DatabaseDefinitions.DOM_LANGUAGE.name, value);
					break;
				case GENRE:
					addIfNotPresent(bookData, CatalogueDBAdapter.KEY_GENRE, value);
					break;
				case AUTHORS: {
					JSONArray authorsJson = new JSONArray(value);
					String[] authors = new String[authorsJson.length()];
					for (int aIdx = 0; aIdx < authorsJson.length(); aIdx++) {
						JSONObject authorJson = authorsJson.getJSONObject(aIdx);
						String name = authorJson.getString(FULL_NAME);
						int pos = authorJson.getInt(AUTHOR_POSITION);
						authors[pos] = name;
					}
					for (String name : authors) {
						Utils.appendOrAdd(bookData, CatalogueDBAdapter.KEY_AUTHOR_DETAILS, name);
					}
					break;
				}
				case COVER_URL: {
					if (fetchThumbnail) {
						String sfx = "_BC_" + bookResults.source;
						fetchThumbnail = !getCoverImage(bookData, value, sfx);
					}
					break;
				}
				case SERIES: {
					JSONArray listJson = new JSONArray(value);
					SeriesInfo[] list = new SeriesInfo[listJson.length()];
					for (int aIdx = 0; aIdx < listJson.length(); aIdx++) {
						JSONObject json = listJson.getJSONObject(aIdx);
						SeriesInfo info = new SeriesInfo(json);
						list[info.positionInList] = info;
					}
					for (SeriesInfo info : list) {
						Utils.appendOrAdd(bookData,
										  CatalogueDBAdapter.KEY_SERIES_DETAILS,
										  info.toString());
					}
				}
				case THUMBNAILS:
					break;
				default:
					Logger.logError(new RuntimeException(
							"Unexpected JSON key in result: " + key + ", value " + value));
			}
		}

	}

	/**
	 * Data class for series data from JSON search results
	 */
	public static class SeriesInfo {
		String name;
		int numberInSeries;
		int positionInList;

		SeriesInfo(JSONObject json) throws JSONException {
			name = json.getString(SERIES_NAME);
			numberInSeries = json.getInt(SERIES_NUM);
			positionInList = json.getInt(SERIES_POSITION);
		}

		@NonNull
		public String toString() {
			return name + " (" + numberInSeries + ")";
		}
	}

	/**
	 * Add the current characters to the book collection if not already present.
	 *
	 * @param key Key for data to add
	 */
	private static void addIfNotPresent(Bundle bookData, String key, String value) {
		String s = bookData.getString(key);
		if (s == null || s.length() == 0) {
			bookData.putString(key, value);
		}
	}

	/**
	 * Get the cover image using the ISBN
	 *
	 * @param bookData Bundle to store info
	 * @param url      Image URL
	 */
	private static boolean getCoverImage(Bundle bookData, String url, String sfx) {
		//String url = getCoverImageUrl(isbn, size);
		// Make sure we follow LibraryThing ToS (no more than 1 request/second).
		waitUntilRequestAllowed();

		// Save it with an _BC suffix
		String filename = Utils.saveThumbnailFromUrl(url, sfx);
		if (filename.length() > 0 && bookData != null) {
			Utils.appendOrAdd(bookData, "__thumbnail", filename);
			return true;
		} else {
			return false;
		}
	}
}
