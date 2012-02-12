package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;

import android.os.Bundle;
import android.os.Message;

/**
 * Class to handle import in a separate thread.
 *
 * @author Grunthos
 */
public class ImportThread extends ManagedTask {
	public ArrayList<String> mExport = null;
	private CatalogueDBAdapter mDbHelper;
	
	public class ImportException extends RuntimeException {
		private static final long serialVersionUID = 1660687786319003483L;

		ImportException(String s) {
			super(s);
		}
	};

	private int mImportUpdated;
	private int mImportCreated;
	
	public interface ImportHandler extends ManagedTask.TaskHandler {
		void onFinish();
	}

	public ImportThread(TaskManager manager, TaskHandler taskHandler, ArrayList<String> export) {
		super(manager, taskHandler);
		mExport = export;
		mDbHelper = new CatalogueDBAdapter(manager.getAppContext());
		mDbHelper.open();
		manager.setMax(this, mExport.size());
		//Debug.startMethodTracing();
	}

	@Override
	protected boolean onFinish() {
		try {
			//Debug.stopMethodTracing();
			ImportHandler h = (ImportHandler)getTaskHandler();
			if (h != null) {
				h.onFinish();
				return true;
			} else {
				return false;
			}			
		} finally {
			cleanup();
		}
	}

	@Override
	protected void onMessage(Message msg) {
		// Nothing to do. we don't sent any
	}

	@Override
	protected void onRun() {
		// Container for values.
		Bundle values = new Bundle();

		String[] names = returnRow(mExport.get(0), true);

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
		requireColumn(values, CatalogueDBAdapter.KEY_ROWID);
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
			while (row < mExport.size() && !isCancelled()) {
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
				String[] imported = returnRow(mExport.get(row), fullEscaping);

				values.clear();
				for(int i = 0; i < names.length; i++) {
					values.putString(names[i], imported[i]);
				}

				// Validate ID
				String idVal = values.getString(CatalogueDBAdapter.KEY_ROWID.toLowerCase());
				if (idVal == "") {
					idVal = "0";
					values.putString(CatalogueDBAdapter.KEY_ROWID, idVal);
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
						String s = mManager.getString(R.string.column_is_blank);
						throw new ImportException(String.format(s, CatalogueDBAdapter.KEY_AUTHOR_DETAILS, row));
					}

					// Now build the array for authors
					ArrayList<Author> aa = Utils.getAuthorUtils().decodeList(authorDetails, '|', false);
					Utils.pruneList(mDbHelper, aa);
					values.putParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, aa);
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
					Utils.pruneList(mDbHelper, sa);
					values.putParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY, sa);				
				}
				
				
				// Make sure we have bookself_text if we imported bookshelf
				if (values.containsKey(CatalogueDBAdapter.KEY_BOOKSHELF) && !values.containsKey("bookshelf_text")) {
					values.putString("bookshelf_text", values.getString(CatalogueDBAdapter.KEY_BOOKSHELF));
				}

				try {
					if (idVal.equals("0")) {
						// Always import empty IDs...even if the are duplicates.
						Long id = mDbHelper.createBook(values);
						idVal = id.toString();
						values.putString(CatalogueDBAdapter.KEY_ROWID, idVal);
						mImportCreated++;
					} else {
						Long id;
						try {
							id = Long.parseLong(idVal);
						} catch (Exception e) {
							id = 0L;
						}
						if (id == 0 || !mDbHelper.checkBookExists(id)) {
							id = mDbHelper.createBook(id, values);
							mImportCreated++;
							idVal = id.toString();
							values.putString(CatalogueDBAdapter.KEY_ROWID, idVal);
						} else {
							// Book exists and should be updated if it has changed
							mDbHelper.updateBook(id, values, false);
							mImportUpdated++;
						}
					}
				} catch (Exception e) {
					Logger.logError(e, "Import at row " + row);
				}

				if (!values.get(CatalogueDBAdapter.KEY_LOANED_TO).equals("")) {
					mDbHelper.createLoan(values);
				}

				if (values.containsKey(CatalogueDBAdapter.KEY_ANTHOLOGY)) {
					int anthology;
					try {
						anthology = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ANTHOLOGY));
					} catch (Exception e) {
						anthology = 0;
					}
					int id = Integer.parseInt(Utils.getAsString(values, CatalogueDBAdapter.KEY_ROWID));
					if (anthology == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS || anthology == CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR) {
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
									mDbHelper.createAnthologyTitle(id, anth_author, anth_title);
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
				if ( (now - lastUpdate) > 200) {
					doProgress(title, row);
					lastUpdate = now;
				}

				// Increment row count
				row++;
			}			
		} finally {
			if (inTx) {
				mDbHelper.setTransactionSuccessful();
				mDbHelper.endTransaction(txLock);
			}
			mDbHelper.purgeAuthors();
			mDbHelper.purgeSeries();
		}
		doToast("Import Complete");
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
	private void requireColumn(Bundle values, String name) {
		if (values.containsKey(name))
			return;

		String s = mManager.getString(R.string.file_must_contain_column);
		throw new ImportException(String.format(s,name));
	}

	// Require a column
	private void requireColumnOr(Bundle values, String... names) {
		for(int i = 0; i < names.length; i++)
			if (values.containsKey(names[i]))
				return;
		
		String s = mManager.getString(R.string.file_must_contain_any_column);
		throw new ImportException(String.format(s, Utils.join(names, ",")));
	}

	private void requireNonblank(Bundle values, int row, String name) {
		if (values.getString(name).length() != 0)
			return;
		String s = mManager.getString(R.string.column_is_blank);
		throw new ImportException(String.format(s, name, row));
	}

	private void requireAnyNonblank(Bundle values, int row, String... names) {
		for(int i = 0; i < names.length; i++)
			if (values.containsKey(names[i]) && values.getString(names[i]).length() != 0)
				return;

		String s = mManager.getString(R.string.columns_are_blank);
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
