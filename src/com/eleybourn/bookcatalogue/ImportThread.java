package com.eleybourn.bookcatalogue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to handle import in a separate thread.
 *
 * @author Philip Warner
 */
public class ImportThread extends ManagedTask {
	public static String UTF8 = "utf8";
	public static int BUFFER_SIZE = 8192;

	private final File mFile;
	private String mFileSpec;
	private boolean mFileIsForeign;
	private final String mSharedStoragePath;
	private CatalogueDBAdapter mDbHelper;
	
	public static class ImportException extends RuntimeException {
		private static final long serialVersionUID = 1660687786319003483L;

		ImportException(String s) {
			super(s);
		}
	};

	//private int mImportUpdated;
	//private int mImportCreated;

	public ImportThread(TaskManager manager, String fileSpec) throws IOException {
		super(manager);
		mFile = new File(fileSpec);
		// Changed getCanonicalPath to getAbsolutePath based on this bug in Android 2.1:
		//     http://code.google.com/p/android/issues/detail?id=4961
		mFileSpec = mFile.getAbsolutePath();
		mSharedStoragePath = StorageUtils.getSharedStorage().getAbsolutePath();

		mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
		mDbHelper.open();

		mFileIsForeign = !(mFileSpec.startsWith(mSharedStoragePath));
		//getMessageSwitch().addListener(getSenderId(), taskHandler, false);
		//Debug.startMethodTracing();
	}

	@Override
	protected void onThreadFinish() {
		cleanup();
	}

	/**
	 * This program reads a text file line by line and print to the console. It uses
	 * FileOutputStream to read the file.
	 */
	private ArrayList<String> readFile(String fileSpec) {
		ArrayList<String> importedString = new ArrayList<String>();

		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileSpec), UTF8),BUFFER_SIZE);
			String line = "";
			while ((line = in.readLine()) != null) {
				importedString.add(line);
			}
			in.close();
		} catch (FileNotFoundException e) {
			doToast(BookCatalogueApp.getResourceString(R.string.import_failed));
			Logger.logError(e);
		} catch (IOException e) {
			doToast(BookCatalogueApp.getResourceString(R.string.import_failed));
			Logger.logError(e);
		}
		return importedString;
	}
	
	
	@Override
	protected void onRun() {
		// Initialize
		ArrayList<String> export = readFile(mFileSpec);
		
		if (export == null || export.size() == 0)
			return;

		mManager.setMax(this, export.size() - 1);

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

		int row = 1; // Start after headings.
		boolean inTx = false;
		int txRowCount = 0;

		long lastUpdate = 0;
		/* Iterate through each imported row */
		SyncLock txLock = null;
		try {
			while (row < export.size() && !isCancelled()) {
				if (inTx && txRowCount > 10) {
					mDbHelper.setTransactionSuccessful();
					mDbHelper.endTransaction(txLock);
					inTx = false;
				}
				if (!inTx) {
					txLock = mDbHelper.startTransaction(true);
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

					if (authorDetails == null || authorDetails.length() == 0) {
						String s = BookCatalogueApp.getResourceString(R.string.column_is_blank);
						throw new ImportException(String.format(s, CatalogueDBAdapter.KEY_AUTHOR_DETAILS, row));
					}

					// Now build the array for authors
					ArrayList<Author> aa = Utils.getAuthorUtils().decodeList(authorDetails, '|', false);
					Utils.pruneList(mDbHelper, aa);
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
					Utils.pruneList(mDbHelper, sa);
					values.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, sa);				
				}
				
				
				// Make sure we have bookself_text if we imported bookshelf
				if (values.containsKey(CatalogueDBAdapter.KEY_BOOKSHELF) && !values.containsKey("bookshelf_text")) {
					values.setBookshelfList(values.getString(CatalogueDBAdapter.KEY_BOOKSHELF));
				}

				try {
					if (!hasUuid && !hasNumericId) {
						// Always import empty IDs...even if they are duplicates.
						Long id = mDbHelper.createBook(values);
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
							Long l = mDbHelper.getBookIdFromUuid(uuidVal);
							if (l != 0) {
								exists = true;
								idLong = l;
							} else {
								exists = false;
								// We have a UUID, but book does not exist. We will create a book.
								// Make sure the ID (if present) is not already used.
								if (hasNumericId && mDbHelper.checkBookExists(idLong))
									idLong = 0L;
							}

						} else {
							exists = mDbHelper.checkBookExists(idLong);							
						}

						if (exists) {
							mDbHelper.updateBook(idLong, values, false);								
							//mImportUpdated++;
						} else {
							newId = mDbHelper.createBook(idLong, values);
							//mImportCreated++;
							values.putString(CatalogueDBAdapter.KEY_ROWID, newId.toString());							
							idLong = newId;
						}
						// When importing a file that has an ID or UUID, try to import a cover.
						if (hasUuid) {
							// Only copy UUID files if they are foreign...since they already exists, otherwise.
							if (mFileIsForeign)
								copyCoverImageIfMissing(uuidVal);							
						} else {
							if (idFromFile != 0) {
								// This will be a rename or a copy
								if (mFileIsForeign)
									copyCoverImageIfMissing(idFromFile, idLong);
								else
									renameCoverImageIfMissing(idFromFile, idLong);																
							}
						}
					}
				} catch (Exception e) {
					Logger.logError(e, "Import at row " + row);
				}

				if (values.containsKey(CatalogueDBAdapter.KEY_LOANED_TO) && !values.get(CatalogueDBAdapter.KEY_LOANED_TO).equals("")) {
					int id = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ROWID));
					mDbHelper.deleteLoan(id);
					mDbHelper.createLoan(values);
				}

				if (values.containsKey(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK)) {
					int anthology;
					try {
						anthology = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK));
					} catch (Exception e) {
						anthology = 0;
					}
					if (anthology == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS || anthology == CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY) {
						int id = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ROWID));
						// We have anthology details, delete the current details.
						mDbHelper.deleteAnthologyTitles(id);
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
									mDbHelper.createAnthologyTitle(id, anth_author, anth_title, true);
								}
								oldi = i + 1;
								i = anthology_titles.indexOf("|", oldi);
							}
						} catch (NullPointerException e) {
							//do nothing. There are no anthology titles
						}
					}
				}

				long now = System.currentTimeMillis();
				if ( (now - lastUpdate) > 200 && !isCancelled()) {
					doProgress(title, row);
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
				mDbHelper.setTransactionSuccessful();
				mDbHelper.endTransaction(txLock);
			}
			mDbHelper.purgeAuthors();
			mDbHelper.purgeSeries();
		}
		try {
			mDbHelper.analyzeDb();
		} catch (Exception e) {
			// Do nothing. Not a critical step.
			Logger.logError(e);
		}
		if (isCancelled()) {
			doToast(getString(R.string.cancelled));
		} else {
			doToast(getString(R.string.import_complete));
		}
	}

	private File findExternalCover(String name) {
		// Find the original, if present.
		File orig = new File(mFile.getParent() + "/" + name + ".jpg");
		if (!orig.exists()) {
			orig = new File(mFile.getParent() + "/" + name + ".png");
		}

		// Nothing to copy?
		if (!orig.exists())
			return null;
		else
			return orig;
		
	}

	/**
	 * Find the current cover file (or new file) based on the passed source and UUID.
	 * 
	 * @param orig		Original file to be copied/renamed if no existing file.
	 * @param newUuid	UUID of file
	 * 
	 * @return			Existing file (if length > 0), or new file object
	 */
	private File getNewCoverFile(File orig, String newUuid) {
		File newFile;
		// Check for ANY current image; delete empty ones and retry
		newFile = CatalogueDBAdapter.fetchThumbnailByUuid(newUuid);
		while (newFile.exists()) {
			if (newFile.length() > 0)
				return newFile;
			else
				newFile.delete();
			newFile = CatalogueDBAdapter.fetchThumbnailByUuid(newUuid);
		}
		
		// Get the new path based on the input file type.
		if (orig.getAbsolutePath().toLowerCase().endsWith(".png")) 
			newFile = new File(mSharedStoragePath + "/" + newUuid + ".png");
		else
			newFile = new File(mSharedStoragePath + "/" + newUuid + ".jpg");

		return newFile;
	}
	/**
	 * Copy a specified source file into the default cover location for a new file.
	 * DO NO OVERWRITE EXISTING FILES.
	 * 
	 * @param orig
	 * @param newUuid
	 * @throws IOException
	 */
	private void copyFileToCoverImageIfMissing(File orig, String newUuid) throws IOException {
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		// Check for ANY current image
		File newFile = getNewCoverFile(orig, newUuid);
		if (newFile.exists())
			return;

		// Copy it.
		InputStream in = null;
		OutputStream out = null;
		try {
			// Open in & out
			in = new FileInputStream(orig);
			out = new FileOutputStream(newFile);
			// Get a buffer
			byte[] buffer = new byte[8192];
			int nRead = 0;
			// Copy
			while( (nRead = in.read(buffer)) > 0){
			    out.write(buffer, 0, nRead);
			}
			// Close both. We close them here so exceptions are signalled
			in.close();
			in = null;
			out.close();
			out = null;
		} finally {
			// If not already closed, close.
			try {
				if (in != null)
					in.close();
			} catch (Exception e) {};
			try {
				if (out != null)
					out.close();
			} catch (Exception e) {};
		}
	}

	/**
	 * Rename/move a specified source file into the default cover location for a new file.
	 * DO NO OVERWRITE EXISTING FILES.
	 * 
	 * @param orig
	 * @param newUuid
	 * @throws IOException
	 */
	private void renameFileToCoverImageIfMissing(File orig, String newUuid) throws IOException {
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		// Check for ANY current image
		File newFile = getNewCoverFile(orig, newUuid);
		if (newFile.exists())
			return;

		orig.renameTo(newFile);
	}

	/**
	 * Copy the ID-based cover from its current location to the correct location in shared 
	 * storage, if it exists.
	 * 
	 * @param externalId		The file ID in external media
	 * @param newId				The new file ID
	 * @throws IOException 
	 */
	private void renameCoverImageIfMissing(long externalId, long newId) throws IOException {
		File orig = findExternalCover(Long.toString(externalId));
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		String newUuid = mDbHelper.getBookUuid(newId);

		renameFileToCoverImageIfMissing(orig, newUuid);
	}

	/**
	 * Copy the ID-based cover from its current location to the correct location in shared 
	 * storage, if it exists.
	 * 
	 * @param externalId		The file ID in external media
	 * @param newId				The new file ID
	 * @throws IOException 
	 */
	private void copyCoverImageIfMissing(long externalId, long newId) throws IOException {
		File orig = findExternalCover(Long.toString(externalId));
		// Nothing to copy?
		if (orig == null || !orig.exists() || orig.length() == 0)
			return;

		String newUuid = mDbHelper.getBookUuid(newId);

		copyFileToCoverImageIfMissing(orig, newUuid);
	}

	/**
	 * Copy the UUID-based cover from its current location to the correct location in shared 
	 * storage, if it exists.
	 * 
	 * @param uuid
	 * @throws IOException 
	 */
	private void copyCoverImageIfMissing(String uuid) throws IOException {
		File orig = findExternalCover(uuid);
		// Nothing to copy?
		if (orig == null || !orig.exists())
			return;

		copyFileToCoverImageIfMissing(orig, uuid);
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

	/**
	 * Cleanup any DB connection etc after main task has run.
	 */
	private void cleanup() {
		if (mDbHelper != null) {
			mDbHelper.close();
			mDbHelper = null;
		}		
	}

	@Override
	protected void finalize() throws Throwable {
		cleanup();
		super.finalize();
	}
}
