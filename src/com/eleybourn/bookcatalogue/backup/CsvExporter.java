/*
* @copyright 2013 Evan Leybourn
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
package com.eleybourn.bookcatalogue.backup;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookEditFields;
import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Implementation of Exporter that creates a CSV file.
 * 
 * @author pjw
 */
public class CsvExporter implements Exporter {
	private String mLastError;

	private static String UTF8 = "utf8";
	private static int BUFFER_SIZE = 32768;

	public String getLastError() {
		return mLastError;
	}

	public boolean export(OutputStream outputStream, Exporter.ExportListener listener) throws IOException {
		int num = 0;
		if (!StorageUtils.sdCardWritable()) {
			mLastError = "Export Failed - Could not write to SDCard";
			return false;			
		}
		listener.onProgress(BookCatalogueApp.getResourceString(R.string.export_starting_ellipsis), 0);
		boolean displayingStartupMessage = true;

		StringBuilder export = new StringBuilder(
			'"' + CatalogueDBAdapter.KEY_ROWID + "\"," + 			//0
			'"' + CatalogueDBAdapter.KEY_AUTHOR_DETAILS + "\"," + 	//2
			'"' + CatalogueDBAdapter.KEY_TITLE + "\"," + 			//4
			'"' + CatalogueDBAdapter.KEY_ISBN + "\"," + 			//5
			'"' + CatalogueDBAdapter.KEY_PUBLISHER + "\"," + 		//6
			'"' + CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\"," + 	//7
			'"' + CatalogueDBAdapter.KEY_RATING + "\"," + 			//8
			'"' + "bookshelf_id\"," + 								//9
			'"' + CatalogueDBAdapter.KEY_BOOKSHELF + "\"," +		//10
			'"' + CatalogueDBAdapter.KEY_READ + "\"," +				//11
			'"' + CatalogueDBAdapter.KEY_SERIES_DETAILS + "\"," +	//12
			'"' + CatalogueDBAdapter.KEY_PAGES + "\"," + 			//14
			'"' + CatalogueDBAdapter.KEY_NOTES + "\"," + 			//15
			'"' + CatalogueDBAdapter.KEY_LIST_PRICE + "\"," + 		//16
			'"' + CatalogueDBAdapter.KEY_ANTHOLOGY_MASK+ "\"," + 		//17
			'"' + CatalogueDBAdapter.KEY_LOCATION+ "\"," + 			//18
			'"' + CatalogueDBAdapter.KEY_READ_START+ "\"," + 		//19
			'"' + CatalogueDBAdapter.KEY_READ_END+ "\"," + 			//20
			'"' + CatalogueDBAdapter.KEY_FORMAT+ "\"," + 			//21
			'"' + CatalogueDBAdapter.KEY_SIGNED+ "\"," + 			//22
			'"' + CatalogueDBAdapter.KEY_LOANED_TO+ "\"," +			//23 
			'"' + "anthology_titles" + "\"," +						//24 
			'"' + CatalogueDBAdapter.KEY_DESCRIPTION+ "\"," + 		//25
			'"' + CatalogueDBAdapter.KEY_GENRE+ "\"," + 			//26
			'"' + DatabaseDefinitions.DOM_LANGUAGE+ "\"," + 			//+1
			'"' + CatalogueDBAdapter.KEY_DATE_ADDED+ "\"," + 		//27
			'"' + DatabaseDefinitions.DOM_GOODREADS_BOOK_ID + "\"," + 		//28
			'"' + DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE + "\"," + 		//29
			'"' + DatabaseDefinitions.DOM_LAST_UPDATE_DATE + "\"," + 		//30
			'"' + DatabaseDefinitions.DOM_BOOK_UUID + "\"," + 		//31
			"\n");
		
		long lastUpdate = 0;
		
		StringBuilder row = new StringBuilder();

		CatalogueDBAdapter db;
		db = new CatalogueDBAdapter(BookCatalogueApp.context);
		db.open();		

		BooksCursor books = db.exportBooks();
		BooksRowView rv = books.getRowView();

		try {
			final int totalBooks = books.getCount();

			if (!listener.isCancelled()) {
	
				listener.setMax(totalBooks);

				/* write to the SDCard */
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE);
				out.write(export.toString());
				if (books.moveToFirst()) {
					do { 
						num++;
						long id = books.getLong(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateString = "";
						try {
							dateString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
						} catch (Exception e) {
							//do nothing
						}
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateReadStartString = "";
						try {
							dateReadStartString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START));
						} catch (Exception e) {
							Logger.logError(e);
							//do nothing
						}
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateReadEndString = "";
						try {
							dateReadEndString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END));
						} catch (Exception e) {
							Logger.logError(e);
							//do nothing
						}
						// Just get the string from the database and save it. It should be in standard SQL form already.
						String dateAddedString = "";
						try {
							dateAddedString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_ADDED));
						} catch (Exception e) {
							//do nothing
						}

						String anthology = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK));
						String anthology_titles = "";
						if (anthology.equals(CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS + "") || anthology.equals(CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY + "")) {
							Cursor titles = db.fetchAnthologyTitlesByBook(id);
							try {
								if (titles.moveToFirst()) {
									do { 
										String anth_title = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
										String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_NAME));
										anthology_titles += anth_title + " * " + anth_author + "|";
									} while (titles.moveToNext()); 
								}
							} finally {
								if (titles != null)
									titles.close();
							}
						}
						String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
						//Display the selected bookshelves
						Cursor bookshelves = db.fetchAllBookshelvesByBook(id);
						String bookshelves_id_text = "";
						String bookshelves_name_text = "";
						while (bookshelves.moveToNext()) {
							bookshelves_id_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_ROWID)) + BookEditFields.BOOKSHELF_SEPERATOR;
							bookshelves_name_text += Utils.encodeListItem(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)),BookEditFields.BOOKSHELF_SEPERATOR) + BookEditFields.BOOKSHELF_SEPERATOR;
						}
						bookshelves.close();

						String authorDetails = Utils.getAuthorUtils().encodeList( db.getBookAuthorList(id), '|' );
						String seriesDetails = Utils.getSeriesUtils().encodeList( db.getBookSeriesList(id), '|' );

						row.setLength(0);
						row.append("\"" + formatCell(id) + "\",");
						row.append("\"" + formatCell(authorDetails) + "\",");
						row.append( "\"" + formatCell(title) + "\"," );
						row.append("\"" + formatCell(rv.getIsbn()) + "\",");
						row.append("\"" + formatCell(rv.getPublisher()) + "\",");
						row.append("\"" + formatCell(dateString) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING))) + "\",");
						row.append("\"" + formatCell(bookshelves_id_text) + "\",");
						row.append("\"" + formatCell(bookshelves_name_text) + "\",");
						row.append("\"" + formatCell(rv.getRead()) + "\",");
						row.append("\"" + formatCell(seriesDetails) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES))) + "\",");
						row.append("\"" + formatCell(rv.getNotes()) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE))) + "\",");
						row.append("\"" + formatCell(anthology) + "\",");
						row.append("\"" + formatCell(rv.getLocation()) + "\",");
						row.append("\"" + formatCell(dateReadStartString) + "\",");
						row.append("\"" + formatCell(dateReadEndString) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT))) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO))+"") + "\",");
						row.append("\"" + formatCell(anthology_titles) + "\",");
						row.append("\"" + formatCell(rv.getDescription()) + "\",");
						row.append("\"" + formatCell(rv.getGenre()) + "\",");
						row.append("\"" + formatCell(rv.getLanguage()) + "\",");
						row.append("\"" + formatCell(dateAddedString) + "\",");
						row.append("\"" + formatCell(rv.getGoodreadsBookId()) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE.name))) + "\",");
						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name))) + "\",");
						row.append("\"" + formatCell(rv.getBookUuid()) + "\",");
						row.append("\n");
						out.write(row.toString());
						//export.append(row);
						
						long now = System.currentTimeMillis();
						if ( (now - lastUpdate) > 200) {
							if (displayingStartupMessage) {
								listener.onProgress("",0);
								displayingStartupMessage = false;
							}
							listener.onProgress(title, num);
							lastUpdate = now;
						}
					}
					while (books.moveToNext() && !listener.isCancelled()); 
				} 
				
				out.close();
			}
	
		} finally {
			System.out.println("Books Exported: " + num);
			if (displayingStartupMessage) 
				try {
					listener.onProgress("",0);
					displayingStartupMessage = false;
				} catch (Exception e) {
					
				}
			if (books != null)
				try { books.close(); } catch (Exception e) {};
			if (db != null)
				db.close();
		}
		return true;
	}

	/**
	 * Double quote all "'s and remove all newlines
	 * 
	 * @param cell The cell the format
	 * @return The formatted cell
	 */
	private String formatCell(String cell) {
		try {
			if (cell.equals("null") || cell.trim().length() == 0) {
				return "";
			}
			StringBuilder bld = new StringBuilder();
			int endPos = cell.length() - 1;
			int pos = 0;
			while (pos <= endPos) {
				char c = cell.charAt(pos);
				switch(c) {
				case '\r':
					bld.append("\\r");
					break;
				case '\n':
					bld.append("\\n");
					break;
				case '\t':
					bld.append("\\t");
					break;
				case '"':
					bld.append("\"\"");
					break;
				case '\\':
					bld.append("\\\\");
					break;
				default:
					bld.append(c);
				}
				pos++;

			}
			return bld.toString();
		} catch (NullPointerException e) {
			return "";
		}
	}
	
	/**
	 * @see formatCell(String cell)
	 * @param cell The cell the format
	 * @return The formatted cell
	 */
	private String formatCell(long cell) {
		String newcell = cell + "";
		return formatCell(newcell);
	}

}
