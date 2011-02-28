package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import android.database.Cursor;
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
		mDbHelper = new CatalogueDBAdapter(manager.getContext());
		mDbHelper.open();
		
		manager.setMax(this, mExport.size());
	}

	@Override
	protected boolean onFinish() {
		ImportHandler h = (ImportHandler)getTaskHandler();
		if (h != null) {
			h.onFinish();
			return true;
		} else {
			return false;
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

		String[] names = returnRow(mExport.get(0));

		// Store the names so we can check what is present
		for(int i = 0; i < names.length; i++) {
			names[i] = names[i].toLowerCase();
			values.putString(names[i], "");
		}

		// Make sure required fields are present.
		// TODO: Rationalize import to allow updates using 1 or 2 columns. For now we require complete data.
		// TODO: Do a search if mandatory columns missing (eg. allow 'import' of a list of ISBNs).
		// TODO: Only make some columns mandator if the ID is not in import or not in DB 
		requireColumn(values, CatalogueDBAdapter.KEY_ROWID);
		requireColumnOr(values, CatalogueDBAdapter.KEY_FAMILY_NAME,
								CatalogueDBAdapter.KEY_AUTHOR_FORMATTED,
								CatalogueDBAdapter.KEY_AUTHOR_NAME,
								CatalogueDBAdapter.KEY_AUTHOR_DETAILS);

		int row = 1; // Start after headings.
		boolean inTx = false;
		int txRowCount = 0;

		/* Iterate through each imported row */
		try {
			while (row < mExport.size() && !isCancelled()) {
				if (inTx && txRowCount > 10) {
					mDbHelper.setTransactionSuccessful();
					mDbHelper.endTransaction();
				}
				if (!inTx) {
					mDbHelper.startTransaction();
					inTx = true;
					txRowCount = 0;
				}
				// Get row
				String[] imported = returnRow(mExport.get(row));

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
						Long id = Long.parseLong(idVal);
						Cursor book = mDbHelper.fetchBookById(id);
						int rows = book.getCount();
						book.close();
						if (rows == 0) {
							id = mDbHelper.createBook(values);
							mImportCreated++;
							idVal = id.toString();
							values.putString(CatalogueDBAdapter.KEY_ROWID, idVal);
						} else {
							// Book exists and should be updated if it has changed
							mDbHelper.updateBook(id, values);
							mImportUpdated++;
						}
					}
				} catch (Exception e) {
					BookCatalogue.logError(e);
				}

				if (!values.get(CatalogueDBAdapter.KEY_LOANED_TO).equals("")) {
					mDbHelper.createLoan(values);
				}

				if (values.containsKey(CatalogueDBAdapter.KEY_ANTHOLOGY)) {
					int anthology = Integer.parseInt(values.getString(CatalogueDBAdapter.KEY_ANTHOLOGY));
					int id = Integer.parseInt(Utils.getAsString(values, CatalogueDBAdapter.KEY_ROWID));
					if (anthology == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS || anthology == CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR) {
						int oldi = 0;
						String anthology_titles = values.getString("anthology_titles");
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
					}
				}

				doProgress(title, row);

				// Increment row count
				row++;
			}			
		} finally {
			if (inTx) {
				mDbHelper.setTransactionSuccessful();
				mDbHelper.endTransaction();
			}
		}
		doToast("Import Complete");
	}

	//
	// This CSV parser is not a complete parser, but it will parse files exported by older 
	// versions. At some stage in the future it would be good to allow full CSV export 
	// and import to allow for escape('\') chars so that cr/lf can be preserved.
	// 
	private String[] returnRow(String row) {
		// Need to handle double quotes etc
		char sep = ',';				// CSV seperator
		char quoteChar = '"';		// CSV quote char
		int pos = 0;				// Current position
		boolean inQuote = false;	// In a quoted string
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

			if (inQuote)
			{
				if (c == quoteChar) {
					if (next == quoteChar)
					{
						// Double-quote: Advance one more and append a single quote
						pos++;
						next = (pos < endPos) ? row.charAt(pos+1) : '\0';
						bld.append(c);
					} else {
						// Leave the quote
						inQuote = false;
					}
				} else {
					// Append anything else that appears in quotes
					bld.append(c);
				}
			} else {
				if (bld.length() == 0 && (c == ' ' || c == '\t') ) {
					// Skip leading white space
				} else if (c == quoteChar) {
					if (bld.length() > 0) {
						// Fields with quotes MUST be quoted...
						throw new IllegalArgumentException();
					} else {
						inQuote = true;
					}
				} else if (c == sep) {
					// Add this field and reset it.
					fields.add(bld.toString());
					bld = new StringBuilder();
				} else {
					// Just append the char
					bld.append(c);
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
	
}
