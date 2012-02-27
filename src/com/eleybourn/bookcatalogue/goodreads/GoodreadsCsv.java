/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.goodreads;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.http.HttpResponse;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.goodreads.api.TrivialParser;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.*;

/**
 * RELEASE GoodreadsCsv is a WORK IN PROGRESS. Probably not needed.
 * 
 * @author Philip Warner
 */
public class GoodreadsCsv {

	private CatalogueDBAdapter mDb = null;
	private long mLastId = -1;

	private static final GoodreadsFieldPublishedDateHandler mPubDateHandler = new GoodreadsFieldPublishedDateHandler();
	private static final GoodreadsFieldIgnoreHandler mIgnoreHandler = new GoodreadsFieldIgnoreHandler();
	//private static final GoodreadsFieldOriginalDateHandler mOrigDateHandler = new GoodreadsFieldOriginalDateHandler();
	private static final GoodreadsFieldBookshelvesHandler mBookshelvesHandler = new GoodreadsFieldBookshelvesHandler();

	private static final GoodreadsCsvFields mFieldMap = new GoodreadsCsvFields()
		.add("Title", CatalogueDBAdapter.KEY_TITLE)
		// TODO: Work out how to handle authors in GR CSV
		//.add("Author", CatalogueDBAdapter.KEY_AUTHOR_NAME)
		.add("Author", mIgnoreHandler)
		.add("ISBN", CatalogueDBAdapter.KEY_ISBN)
		.add("Average Rating", mIgnoreHandler)
		.add("Publisher", CatalogueDBAdapter.KEY_PUBLISHER)
		.add("Binding", CatalogueDBAdapter.KEY_FORMAT)
		.add("Year Published", mPubDateHandler)
		// RELEASE: Handle ORIGINAL PUB DATE
		//.add("Original Publicaltion Year", mOrigDateHandler)
		.add("Original Publicaltion Year", mIgnoreHandler)
		.add("Date Read", CatalogueDBAdapter.KEY_READ_END)
		.add("Date Added", CatalogueDBAdapter.KEY_DATE_ADDED)
		.add("Bookshelves", mBookshelvesHandler)
		.add("My Review", CatalogueDBAdapter.KEY_NOTES)
		;
	
	private static abstract class GoodreadsFieldHandler {
		public abstract void outputCsv(CatalogueDBAdapter db, BooksRowView book, StringBuilder csv);
		public abstract boolean canImport();
		public abstract boolean canExport();
	}

	private static class GoodreadsFieldDirectCopy extends GoodreadsFieldHandler {
		private final String mDestination;
		GoodreadsFieldDirectCopy(String destination) {
			mDestination = destination;
		}
		public void outputCsv(CatalogueDBAdapter db, BooksRowView book, StringBuilder csv) {
			csv.append(book.getString(mDestination));
		}
		@Override
		public boolean canImport() {
			return true;
		}
		@Override
		public boolean canExport() {
			return true;
		}
	}

	private static class GoodreadsFieldIgnoreHandler extends GoodreadsFieldHandler {
		public void outputCsv(CatalogueDBAdapter db, BooksRowView book, StringBuilder csv) {
			throw new RuntimeException("Can not output IGNORED field");
		}

		@Override
		public boolean canImport() {
			return false;
		}

		@Override
		public boolean canExport() {
			return false;
		}
	}

	private static class GoodreadsFieldPublishedDateHandler extends GoodreadsFieldHandler {
		public void outputCsv(CatalogueDBAdapter db, BooksRowView book, StringBuilder csv) {
			csv.append(book.getDatePublished());
		}

		@Override
		public boolean canImport() {
			return true;
		}

		@Override
		public boolean canExport() {
			return true;
		}
	}

	//private static class GoodreadsFieldOriginalDateHandler extends GoodreadsFieldHandler {
	//}

	private static class GoodreadsFieldBookshelvesHandler extends GoodreadsFieldHandler {
		public void outputCsv(CatalogueDBAdapter db, BooksRowView book, StringBuilder csv) {
			Cursor shelves = db.getAllBookBookshelvesForGoodreadsCursor(book.getId());
			try {
				int	shelfCol = shelves.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF);

				while (shelves.moveToNext()) {
					String shelfName = shelves.getString(shelfCol);
					csv.append("'" + shelfName + "' ");
				}
			} finally {
				shelves.close();
			}
		}

		@Override
		public boolean canImport() {
			return true;
		}

		@Override
		public boolean canExport() {
			return true;
		}
	}

	private static class GoodreadsCsvField {
		public String grName;
		public GoodreadsFieldHandler handler;
		GoodreadsCsvField(String grName, String bcFieldName) {
			this.grName = grName;
			this.handler = new GoodreadsFieldDirectCopy(bcFieldName);
		}
		GoodreadsCsvField(String grName, GoodreadsFieldHandler handler) {
			this.grName = grName;
			this.handler = handler;
		}
	}

	private static class GoodreadsCsvFields implements Iterable<GoodreadsCsvField> {
		private ArrayList<GoodreadsCsvField> mList = new ArrayList<GoodreadsCsvField>();

		public GoodreadsCsvFields add(String grName, String bcName) {
			GoodreadsCsvField f = new GoodreadsCsvField(grName, bcName);
			mList.add(f);
			return this;
		}
		public GoodreadsCsvFields add(String grName, GoodreadsFieldHandler handler) {
			GoodreadsCsvField f = new GoodreadsCsvField(grName, handler);
			mList.add(f);
			return this;
		}
		@Override
		public Iterator<GoodreadsCsvField> iterator() {
			return mList.iterator();
		}
	}
	
	/**
	 * Send all books to goodreads in batches of 100 via the CSV interface
	 * @throws IOException 
	 * @throws BookNotFoundException 
	 * @throws NotAuthorizedException 
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthMessageSignerException 
	 */
	public void sendAllToGoodreads() throws IOException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException {
		CatalogueDBAdapter db = getDb();
		long lastId = mLastId;
		BooksCursor books = null;
		StringBuilder csvBuilder = new StringBuilder();

		try {
			// reset the CSV data
			csvBuilder.setLength(0);
			
			// Make the headers
			boolean first = true;
			for(GoodreadsCsvField f: mFieldMap) {
				if (f.handler.canExport()) {
					if (first) 
						first = false;
					else 
						csvBuilder.append(",");
					csvBuilder.append(f.grName);
				}
			}
			csvBuilder.append("\n");

			while (true) {
				String sql = "Select " + TBL_BOOKS.dot("*") + " from " + TBL_BOOKS.ref() + 
						" Where " + TBL_BOOKS.dot(DOM_ID) + " > " + lastId +
						" Order By " + TBL_BOOKS.dot(DOM_ID) + " asc Limit 5";
				
				books = db.fetchBooks(sql, CatalogueDBAdapter.EMPTY_STRING_ARRAY);
				BooksRowView book = books.getRowView();

				while (books.moveToNext()) {
					lastId = book.getId();
					first = true;
					for(GoodreadsCsvField f: mFieldMap) {
						if (f.handler.canExport()) {
							if (first) 
								first = false;
							else 
								csvBuilder.append(",");
							f.handler.outputCsv(db, book, csvBuilder);
						}
					}
					csvBuilder.append("\n");
				}
				books.close();
				sendCsv(csvBuilder.toString());
				mLastId = lastId;
			}
		} finally {
			if (books != null)
				books.close();
		}
	}

	private CatalogueDBAdapter getDb() {
		if (mDb == null) {
			mDb = new CatalogueDBAdapter(BookCatalogueApp.context);
			mDb.open();
		}
		return mDb;
	}

	public void sendCsv(final String csvData) throws IOException, OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, NotAuthorizedException, BookNotFoundException {

		GoodreadsManager grMgr = new GoodreadsManager();
	
		File file = File.createTempFile("BC_GR", ".csv");
		try {
			FileWriter writer = new FileWriter(file);
			writer.write(csvData);
			writer.close();

			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost("http://www.goodreads.com/review/import");

			MultipartEntity entity = new MultipartEntity();

//			List<NameValuePair> parameters = new ArrayList<NameValuePair>();
//	        parameters.add(new BasicNameValuePair("import[file]", "test.csv"));
//	        parameters.add(new BasicNameValuePair("commit", "import books"));
//
//	        UrlEncodedFormEntity args = new UrlEncodedFormEntity(parameters);
//	        entity.addPart(args);

	        entity.addPart("file.csv", new FileBody(file));

			post.setEntity(entity);

			TrivialParser parser = new TrivialParser();
			grMgr.execute(post, parser, true);
			String res = parser.getHtml();
			System.out.println(res);
			
		} finally {
			file.delete();
		}
		
	}
}
