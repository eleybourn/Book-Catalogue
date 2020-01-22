/*
 * @copyright 2020 Philip Warner
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

package com.eleybourn.bookcatalogue.bcservices;

import android.net.Uri;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.SearchThread.BookSearchResults;
import com.eleybourn.bookcatalogue.SearchThread.DataSource;
import com.eleybourn.bookcatalogue.bcservices.BcService.Methods;
import com.eleybourn.bookcatalogue.utils.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Handle all aspects of searching (and ultimately synchronizing with) the Book Catalogue web services for searching.
 * <p>
 * The basic URLs are:
 * <p>
 * ISBN: https://homestead.test/api/search/isbn/<isbn>
 * Author/Title: https://homestead.test/api/search/details/<author>/<title>
 * Covers via url_path in search results.
 *
 * @author Philip Warner
 */
public class BcSearchManager {

	/**
	 * This searches the Book-Catalogue web service site based on a specific isbn or author/title
	 *
	 * @param isbn The ISBN to search for
	 * @param author Name of author
	 * @param title Title of book
	 */
	public static void searchBcService(String isbn, String author, String title, ArrayList<BookSearchResults> results, boolean fetchThumbnail) {

		// URI encode details
		author = Uri.encode(author);
		title = Uri.encode(title);

		BcSearchManager handler = new BcSearchManager();

		// Prefer ISBN search
		if (!isbn.equals("")) {
			handler.searchByIsbn(isbn, fetchThumbnail, results);
		} else {
			handler.searchByAuthorTitle(author, title, fetchThumbnail, results);
		}

		//// Sanity-check (reinstate if search service changes substantially)
		//for(BookSearchResults result: results) {
		//	if (result.source == DataSource.Google) {
		//		ArrayList<String> keys = new ArrayList<>(result.data.keySet());
		//		Collections.sort(keys);
		//		for(String k: keys) {
		//			System.out.println("SANITY:GOOGLE-BCD    " + k + " => " + result.data.get(k).toString());
		//		}
		//	}
		//}
	}

	/** URL for search by ISBN */
	private static final String SEARCH_ISBN_URL = "https://book-catalogue.com/api/search/isbn/%1$s";
	/** URL for search by AUTHOR or TITLE */
	private static final String SEARCH_AUTHOR_TITLE_URL = "https://book-catalogue.com/api/search/details/%1$s/%2$s";

	// Keys in JSON
	private static final String RESULTS = "Results";
	private static final String SOURCE = "source";
	private static final String STATUS = "status";
	private static final String STATUS_OK = "OK";
	private static final String DATA = "data";
	private static final String SRC_BCDB = "BCDB";
	private static final String SRC_GOOGLE = "Google";
	private static final String SRC_GOOGLE_OLD = "GoogleOld";
	private static final String SRC_AMAZON = "Amazon";
	private static final String SRC_OPENLIB = "OpenLibrary";

	private BcSearchManager() {
	}

	/**
	 * Search Book Catalogue API for an ISBN using the Web API.
	 *
	 * @param isbn     ISBN to lookup
	 * @param results  Collection to save results in
	 *                 <p>
	 *                 A typical response looks like:
	 */
	private void searchByIsbn(String isbn, boolean fetchThumbnail, ArrayList<BookSearchResults> results) {

		// Base path for an ISBN search
		String path = String.format(SEARCH_ISBN_URL, isbn);

		if (isbn.equals(""))
			throw new IllegalArgumentException();

		search(path, fetchThumbnail, results);
	}

	/**
	 * Search Book Catalogue API for an ISBN using the Web API.
	 *
	 * @param author    Author of book to lookup
	 * @param title     Title of book to lookup
	 * @param results   Collection to save results in
	 *                 <p>
	 *                 A typical response looks like:
	 */
	private void searchByAuthorTitle(
			String author,
			String title,
			boolean fetchThumbnail,
			ArrayList<BookSearchResults> results) {

		// Base path for an ISBN search
		String path = String.format(SEARCH_AUTHOR_TITLE_URL, author, title);

		if (author.equals("") && title.equals(""))
			throw new IllegalArgumentException();

		search(path, fetchThumbnail, results);
	}

	/**
	 * Use the passed complete search URL to gather results.
	 *
	 * @param url				URL for search.
	 * @param fetchThumbnail	True if thumbnails are wanted.
	 * @param resultList		List of {@link BookSearchResults} objects
	 */
	private void search(String url, boolean fetchThumbnail, ArrayList<BookSearchResults> resultList) {
		JSONObject apiResult;
		try {
			// Make sure we limit requests (no more than 1 request/second/client).
			BcService.waitUntilRequestAllowed();
			// Get the JSON result
			apiResult = BcService.makeServiceCall(url, Methods.Get, new HashMap<>());
		} catch (Exception e) {
			Logger.logError(e, "Service call failed");
			return;
		}

		if (apiResult == null) {
			Logger.logError(new RuntimeException("API result is null"));
			return;
		}

		try {
			// Check overall status
			apiResult = apiResult.getJSONObject(RESULTS);
			if (apiResult.optString(STATUS, "").equals(STATUS_OK)) {
				JSONArray results = apiResult.getJSONArray(DATA);
				// Process array of results
				for (int i = 0; i < results.length(); i++) {
					// Get one result
					JSONObject resultWrapper = results.getJSONObject(i);
					// If result status OK, process data
					if (resultWrapper.optString(STATUS, "").equals(STATUS_OK)) {
						boolean skip = false;
						String source = resultWrapper.getString(SOURCE);
						DataSource src;
						if (source.equalsIgnoreCase(SRC_AMAZON)) {
							src = DataSource.Amazon;
						} else if (source.equalsIgnoreCase(SRC_BCDB)) {
							src = DataSource.BCDB;
						} else if (source.equalsIgnoreCase(SRC_GOOGLE)) {
							src = DataSource.Google;
						} else if (source.equalsIgnoreCase(SRC_GOOGLE_OLD)) {
							src = DataSource.Google;
							skip = true;
						} else if (source.equalsIgnoreCase(SRC_OPENLIB)) {
							src = DataSource.OpenLibrary;
						} else {
							src = DataSource.Other;
						}

						if (!skip) {
							BookSearchResults srchRes = new BookSearchResults(src, new Bundle());
							resultList.add(srchRes);
							//Bundle bookData = srchRes.data;
							//if (BuildConfig.DEBUG) {
							//	bookData.putString("_DBGSRC_", "BC");
							//}

							JSONObject result = resultWrapper.getJSONObject(DATA);
							BcService.jsonResultToBookData(result, srchRes, fetchThumbnail);
						}
					}
				}
			}
		} catch (JSONException e) {
			Logger.logError(e, "Unexpected JSON Structure: " + apiResult.toString());
		}
	}

	public boolean isAvailable() {
		return true;
	}
}
