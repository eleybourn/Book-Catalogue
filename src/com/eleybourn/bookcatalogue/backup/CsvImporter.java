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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.ImportThread.ImportException;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Implementation of Importer that reads a CSV file.
 * 
 * @author pjw
 */
public class CsvImporter {
	private static String UTF8 = "utf8";
	private static int BUFFER_SIZE = 32768;

	public boolean importBooks(InputStream exportStream, Importer.CoverFinder coverFinder, Importer.OnImporterListener listener, int importFlags) throws IOException {
		ArrayList<String> importedString = new ArrayList<String>();

		BufferedReader in = new BufferedReader(new InputStreamReader(exportStream, UTF8),BUFFER_SIZE);
		String line = "";
		while ((line = in.readLine()) != null) {
			importedString.add(line);
		}

		return importBooks(importedString, coverFinder, listener, importFlags);
	}

	private boolean importBooks(ArrayList<String> export, Importer.CoverFinder coverFinder, Importer.OnImporterListener listener, int importFlags) {

		if (export == null || export.size() == 0)
			return true;

		Integer nCreated = 0;
		Integer nUpdated = 0;

		listener.setMax(export.size() - 1);

		// Container for values.
		BookData values = new BookData();

		String[] names = returnRow(export.get(0), true);

		// Store the names so we can check what is present
		for(int i = 0; i < names.length; i++) {
			names[i] = names[i].toLowerCase();
			values.putString(names[i], "");
		}

		// See if we can deduce the kind of escaping to use based on column names.
		// Version 1->3.3 export with family_name and author_id. Version 3.4+ do not; latest versions
		// make an attempt at escaping characters etc to preserve formatting.
		boolean fullEscaping;
		if (values.containsKey(CatalogueDBAdapter.KEY_AUTHOR_ID) && values.containsKey(CatalogueDBAdapter.KEY_FAMILY_NAME)) {
			// Old export, or one using old formats
			fullEscaping = false;
		} else {
			// More recent data format
			fullEscaping = true;
		}

		// Make sure required fields are present.
		// ENHANCE: Rationalize import to allow updates using 1 or 2 columns. For now we require complete data.
		// ENHANCE: Do a search if mandatory columns missing (eg. allow 'import' of a list of ISBNs).
		// ENHANCE: Only make some columns mandatory if the ID is not in import, or not in DB (ie. if not an update)
		// ENHANCE: Export/Import should use GUIDs for book IDs, and put GUIDs on Image file names.
		requireColumnOr(values, CatalogueDBAdapter.KEY_ROWID, DatabaseDefinitions.DOM_BOOK_UUID.name);
		requireColumnOr(values, CatalogueDBAdapter.KEY_FAMILY_NAME,
								CatalogueDBAdapter.KEY_AUTHOR_FORMATTED,
								CatalogueDBAdapter.KEY_AUTHOR_NAME,
								CatalogueDBAdapter.KEY_AUTHOR_DETAILS);

		boolean updateOnlyIfNewer;
		if ( (importFlags & Importer.IMPORT_NEW_OR_UPDATED) != 0) {
			if (!values.containsKey(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name)) {
				throw new RuntimeException("Imported data does not contain " + DatabaseDefinitions.DOM_LAST_UPDATE_DATE);
			}
			updateOnlyIfNewer = true;
		} else {
			updateOnlyIfNewer = false;
		}

		
		CatalogueDBAdapter db;
		db = new CatalogueDBAdapter(BookCatalogueApp.context);
		db.open();

		int row = 1; // Start after headings.
		boolean inTx = false;
		int txRowCount = 0;

		long lastUpdate = 0;
		/* Iterate through each imported row */
		SyncLock txLock = null;
		try {
			while (row < export.size() && !listener.isCancelled()) {
				if (inTx && txRowCount > 10) {
					db.setTransactionSuccessful();
					db.endTransaction(txLock);
					inTx = false;
				}
				if (!inTx) {
					txLock = db.startTransaction(true);
					inTx = true;
					txRowCount = 0;
				}
				txRowCount++;

				// Get row
				String[] imported = returnRow(export.get(row), fullEscaping);

				values.clear();
				for(int i = 0; i < names.length; i++) {
					values.putString(names[i], imported[i]);
				}

				boolean hasNumericId;
				// Validate ID
				String idStr = values.getString(CatalogueDBAdapter.KEY_ROWID.toLowerCase());
				Long idLong;
				if (idStr == null || idStr == "") {
					hasNumericId = false;
					idLong = 0L;
				} else {
					try {
						idLong = Long.parseLong(idStr);
						hasNumericId = true;
					} catch (Exception e) {
						hasNumericId = false;
						idLong = 0L;
					}
				}
				if (!hasNumericId) {
					values.putString(CatalogueDBAdapter.KEY_ROWID, "0");					
				}

				// Get the UUID, and remove from collection if null/blank
				boolean hasUuid;
				final String uuidColumnName = DatabaseDefinitions.DOM_BOOK_UUID.name.toLowerCase();
				String uuidVal = values.getString(uuidColumnName);
				if (uuidVal != null && !uuidVal.equals("")) {
					hasUuid = true;
				} else {
					// Remove any blank UUID column, just in case
					if (values.containsKey(uuidColumnName))
						values.remove(uuidColumnName);
					hasUuid = false;
				}

				requireNonblank(values, row, CatalogueDBAdapter.KEY_TITLE);
				String title = values.getString(CatalogueDBAdapter.KEY_TITLE);

				// Keep author handling stuff local
				{
					// Get the list of authors from whatever source is available.
					String authorDetails;
					authorDetails = values.getString(CatalogueDBAdapter.KEY_AUTHOR_DETAILS);
					if (authorDetails == null || authorDetails.length() == 0) {
						// Need to build it from other fields.
						if (values.containsKey(CatalogueDBAdapter.KEY_FAMILY_NAME)) {
							// Build from family/given
							authorDetails = values.getString(CatalogueDBAdapter.KEY_FAMILY_NAME);
							String given = "";
							if (values.containsKey(CatalogueDBAdapter.KEY_GIVEN_NAMES))
								given = values.getString(CatalogueDBAdapter.KEY_GIVEN_NAMES);
							if (given != null && given.length() > 0)
								authorDetails += ", " + given;
						} else if (values.containsKey(CatalogueDBAdapter.KEY_AUTHOR_NAME)) {
							authorDetails = values.getString(CatalogueDBAdapter.KEY_AUTHOR_NAME);
						} else if (values.containsKey(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED)) {
							authorDetails = values.getString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);					
						}
					}

					// A pre-existing bug sometimes results in blank author-details due to bad underlying data
					// (it seems a 'book' record gets written without an 'author' record; should not happen)
					// so we allow blank author_details and full in a regionalized version of "Author, Unknown"
					if (authorDetails == null || authorDetails.length() == 0) {
						authorDetails = BookCatalogueApp.getResourceString(R.string.author) + ", " + BookCatalogueApp.getResourceString(R.string.unknown);
						//String s = BookCatalogueApp.getResourceString(R.string.column_is_blank);
						//throw new ImportException(String.format(s, CatalogueDBAdapter.KEY_AUTHOR_DETAILS, row));
					}

					// Now build the array for authors
					ArrayList<Author> aa = Utils.getAuthorUtils().decodeList(authorDetails, '|', false);
					Utils.pruneList(db, aa);
					values.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, aa);
				}

				// Keep series handling local
				{
					String seriesDetails;
					seriesDetails = values.getString(CatalogueDBAdapter.KEY_SERIES_DETAILS);
					if (seriesDetails == null || seriesDetails.length() == 0) {
						// Try to build from SERIES_NAME and SERIES_NUM. It may all be blank
						if (values.containsKey(CatalogueDBAdapter.KEY_SERIES_NAME)) {
							seriesDetails = values.getString(CatalogueDBAdapter.KEY_SERIES_NAME);
							if (seriesDetails != null && seriesDetails.length() != 0) {
								String seriesNum = values.getString(CatalogueDBAdapter.KEY_SERIES_NUM);
								if (seriesNum == null)
									seriesNum = "";
								seriesDetails += "(" + seriesNum + ")";
							} else {
								seriesDetails = null;
							}
						}
					}
					// Handle the series
					ArrayList<Series> sa = Utils.getSeriesUtils().decodeList(seriesDetails, '|', false);
					Utils.pruneSeriesList(sa);
					Utils.pruneList(db, sa);
					values.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, sa);				
				}
				
				
				// Make sure we have bookself_text if we imported bookshelf
				if (values.containsKey(CatalogueDBAdapter.KEY_BOOKSHELF) && !values.containsKey("bookshelf_text")) {
					values.setBookshelfList(values.getString(CatalogueDBAdapter.KEY_BOOKSHELF));
				}

				try {
					boolean doUpdate;
					if (!hasUuid && !hasNumericId) {
						doUpdate = true;
						// Always import empty IDs...even if they are duplicates.
						Long id = db.createBook(values, CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
						values.putString(CatalogueDBAdapter.KEY_ROWID, id.toString());
						// Would be nice to import a cover, but with no ID/UUID thats not possible
						//mImportCreated++;
					} else {
						boolean exists;
						// Save the original ID from the file for use in checing for images
						Long idFromFile = idLong;
						// newId will get the ID allocated if a book is created
						Long newId = 0L;

						// Let the UUID trump the ID; we may be importing someone else's list with bogus IDs
						if (hasUuid) {
							Long l = db.getBookIdFromUuid(uuidVal);
							if (l != 0) {
								exists = true;
								idLong = l;
							} else {
								exists = false;
								// We have a UUID, but book does not exist. We will create a book.
								// Make sure the ID (if present) is not already used.
								if (hasNumericId && db.checkBookExists(idLong))
									idLong = 0L;
							}
						} else {
							exists = db.checkBookExists(idLong);							
						}

						if (exists) {
							if (!updateOnlyIfNewer) {
								doUpdate = true;
							} else {
								Date bookDate;
								Date importDate;
								String bookDateStr = db.getBookUpdateDate(idLong);
								if (bookDateStr == null || bookDateStr.equals("")) {
									bookDate = null; // Local record has never been updated
								} else {
									try {
										bookDate = Utils.parseDate(bookDateStr);
									} catch (Exception e) {
										bookDate = null; // Treat as if never updated
									}
								}
								String importDateStr = values.getString(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name);
								if (importDateStr == null || importDateStr.equals("")) {
									importDate = null; // Imported record has never been updated
								} else {
									try {
										importDate = Utils.parseDate(importDateStr);
									} catch (Exception e) {
										importDate = null; // Treat as if never updated
									}
								}
								if (importDate == null) {
									doUpdate = false;
								} else if (bookDate == null) {
									doUpdate = true;
								} else {
									doUpdate = importDate.compareTo(bookDate) > 0;
								}
							}
							if (doUpdate) {
								db.updateBook(idLong, values, CatalogueDBAdapter.BOOK_UPDATE_SKIP_PURGE_REFERENCES|CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);								
								nUpdated++;
							}
							//mImportUpdated++;
						} else {
							doUpdate = true;
							newId = db.createBook(idLong, values, CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
							nCreated++;
							//mImportCreated++;
							values.putString(CatalogueDBAdapter.KEY_ROWID, newId.toString());							
							idLong = newId;
						}

						// When importing a file that has an ID or UUID, try to import a cover.
						if (coverFinder != null) {
							coverFinder.copyOrRenameCoverFile(uuidVal, idFromFile, idLong);
						}
						// Save the real ID to the collection (will/may be used later)
						values.putString(CatalogueDBAdapter.KEY_ROWID, idLong.toString());
					}
					
					if (doUpdate) {
						if (values.containsKey(CatalogueDBAdapter.KEY_LOANED_TO) && !values.get(CatalogueDBAdapter.KEY_LOANED_TO).equals("")) {
							int id = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ROWID));
							db.deleteLoan(id, false);
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
								int id = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ROWID));
								// We have anthology details, delete the current details.
								db.deleteAnthologyTitles(id, false);
								int oldi = 0;
								String anthology_titles = values.getString("anthology_titles");
								try {
									int i = anthology_titles.indexOf("|", oldi);
									while (i > -1) {
										String extracted_title = anthology_titles.substring(oldi, i).trim();
										
										int j = extracted_title.indexOf("*");
										if (j > -1) {
											String anth_title = extracted_title.substring(0, j).trim();
											String anth_author = extracted_title.substring((j+1)).trim();
											db.createAnthologyTitle(id, anth_author, anth_title, true, false);
										}
										oldi = i + 1;
										i = anthology_titles.indexOf("|", oldi);
									}
								} catch (NullPointerException e) {
									//do nothing. There are no anthology titles
								}
							}
						}						
					}

				} catch (Exception e) {
					Logger.logError(e, "Import at row " + row);
				}

				long now = System.currentTimeMillis();
				if ( (now - lastUpdate) > 200 && !listener.isCancelled()) {
					listener.onProgress(title + "\n(" + BookCatalogueApp.getResourceString(R.string.n_created_m_updated, nCreated, nUpdated) + ")", row);
					lastUpdate = now;
				}

				// Increment row count
				row++;
			}	
		} catch (Exception e) {
			Logger.logError(e);
			throw new RuntimeException(e);
		} finally {
			if (inTx) {
				db.setTransactionSuccessful();
				db.endTransaction(txLock);
			}
			try {
				db.purgeAuthors();
				db.purgeSeries();
				db.analyzeDb();
			} catch (Exception e) {
				// Do nothing. Not a critical step.
				Logger.logError(e);
			}
			try {
				db.close();
			} catch (Exception e) {
				// Do nothing. Not a critical step.
				Logger.logError(e);
			}
		}
		
		return true;

// XXX: Make sure this is replicated
//		if (listener.isCancelled()) {
//			doToast(getString(R.string.cancelled));
//		} else {
//			doToast(getString(R.string.import_complete));
//		}
		
	}

	//
	// This CSV parser is not a complete parser, but it will parse files exported by older 
	// versions. At some stage in the future it would be good to allow full CSV export 
	// and import to allow for escape('\') chars so that cr/lf can be preserved.
	// 
	private String[] returnRow(String row, boolean fullEscaping) {
		// Need to handle double quotes etc
		int pos = 0;				// Current position
		boolean inQuote = false;	// In a quoted string
		boolean inEsc = false;		// Found an escape char
		char c;						// 'Current' char
		char next					// 'Next' char 
				= (row.length() > 0) ? row.charAt(0) : '\0';
		int endPos					// Last position in row 
				= row.length() - 1;
		ArrayList<String> fields	// Array of fields found in row
				= new ArrayList<String>();

		StringBuilder bld			// Temp. storage for current field
				= new StringBuilder();

		while (next != '\0')
		{
			// Get current and next char
			c = next;
			next = (pos < endPos) ? row.charAt(pos+1) : '\0';

			// If we are 'escaped', just append the char, handling special cases
			if (inEsc) {
				bld.append(unescape(c));
				inEsc = false;
			}
			else if (inQuote)
			{
				switch(c) {
					case QUOTE_CHAR:
						if (next == QUOTE_CHAR)
						{
							// Double-quote: Advance one more and append a single quote
							pos++;
							next = (pos < endPos) ? row.charAt(pos+1) : '\0';
							bld.append(c);
						} else {
							// Leave the quote
							inQuote = false;
						}
						break;
					case ESCAPE_CHAR:
						if (fullEscaping)
							inEsc = true;
						else
							bld.append(c);						
						break;
					default:
						bld.append(c);						
						break;
				}
			} else {
				// This is just a raw string; no escape or quote active.
				// Ignore leading space.
				if ((c == ' ' || c == '\t') && bld.length() == 0 ) {
					// Skip leading white space
				} else {
					switch(c){
						case QUOTE_CHAR:
							if (bld.length() > 0) {
								// Fields with quotes MUST be quoted...
								throw new IllegalArgumentException();
							} else {
								inQuote = true;
							}
							break;
						case ESCAPE_CHAR:
							if (fullEscaping)
								inEsc = true;
							else
								bld.append(c);						
							break;
						case SEPARATOR:
							// Add this field and reset it.
							fields.add(bld.toString());
							bld = new StringBuilder();
							break;
						default:
							// Just append the char
							bld.append(c);
							break;
					}
				}
			}
			pos++;
		};

		// Add the remaining chunk
		fields.add(bld.toString());

		// Return the result as a String[].
		String[] imported = new String[fields.size()];
		fields.toArray(imported);

		return imported;
	}

	private final static char QUOTE_CHAR = '"';
	private final static char ESCAPE_CHAR = '\\';
	private final static char SEPARATOR = ',';
	private char unescape(char c) {
		switch(c) {
		case 'r':
			return '\r';
		case 't':
			return '\t';
		case 'n':
			return '\n';
		default:
			// Handle simple escapes. We could go further and allow arbitrary numeric wchars by
			// testing for numeric sequences here but that is beyond the scope of this app. 
			return c;
		}
	}
	
	// Require a column
	@SuppressWarnings("unused")
	private void requireColumn(Bundle values, String name) {
		if (values.containsKey(name))
			return;

		String s = BookCatalogueApp.getResourceString(R.string.file_must_contain_column);
		throw new ImportException(String.format(s,name));
	}

	// Require a column
	private void requireColumnOr(BookData values, String... names) {
		for(int i = 0; i < names.length; i++)
			if (values.containsKey(names[i]))
				return;
		
		String s = BookCatalogueApp.getResourceString(R.string.file_must_contain_any_column);
		throw new ImportException(String.format(s, Utils.join(names, ",")));
	}

	private void requireNonblank(BookData values, int row, String name) {
		if (values.getString(name).length() != 0)
			return;
		String s = BookCatalogueApp.getResourceString(R.string.column_is_blank);
		throw new ImportException(String.format(s, name, row));
	}

	@SuppressWarnings("unused")
	private void requireAnyNonblank(BookData values, int row, String... names) {
		for(int i = 0; i < names.length; i++)
			if (values.containsKey(names[i]) && values.getString(names[i]).length() != 0)
				return;

		String s = BookCatalogueApp.getResourceString(R.string.columns_are_blank);
		throw new ImportException(String.format(s, Utils.join( names, ","), row));
	}

}
