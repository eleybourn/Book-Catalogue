/*
 * @copyright 2010 Evan Leybourn
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

package com.eleybourn.bookcatalogue;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;
import com.eleybourn.bookcatalogue.UpdateThumbnailsThread.BookInfo;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class AdministrationFunctions extends ActivityWithTasks {
	private static final int ACTIVITY_BOOKSHELF=1;
	private static final int ACTIVITY_FIELD_VISIBILITY=2;
	private CatalogueDBAdapter mDbHelper;
	//private int importUpdated = 0;
	//private int importCreated = 0;
	public static String fileName = Utils.EXTERNAL_FILE_PATH + "/export.csv";
	public static String UTF8 = "utf8";
	public static int BUFFER_SIZE = 8192;
	private ProgressDialog pd = null;
	private int num = 0;
	private boolean finish_after = false;

	public static final String DOAUTO = "do_auto";

	final UpdateThumbnailsThread.LookupHandler mThumbnailsHandler = new UpdateThumbnailsThread.LookupHandler() {
		@Override
		public void onFinish(LinkedList<BookInfo> queue) {
			if (finish_after == true) {
				finish();
			}
		}

		@Override
		public void onProgress(LinkedList<BookInfo> queue) {
			Iterator<BookInfo> i = queue.iterator();
			while (i.hasNext()) {
				BookInfo bi = i.next();
				mDbHelper.updateBook(bi.id, bi.bookData);
			}
		}

//		@Override
//		public String getString(int id) {
//			return getResources().getString(id);
//		}
	};

	final ExportThread.ExportHandler mExportHandler = new ExportThread.ExportHandler() {
//		@Override
//		public String getString(int id) {
//			return getResources().getString(id);
//		}

		@Override
		public void onFinish() {
		}
	};

	final ImportThread.ImportHandler mImportHandler = new ImportThread.ImportHandler() {
//		@Override
//		public String getString(int id) {
//			return getResources().getString(id);
//		}

		@Override
		public void onFinish() {
		}
	};

	final Handler mProgressHandler = new Handler() {
		public void handleMessage(Message msg) {
			int total = msg.getData().getInt("total");
			String title = msg.getData().getString("title");
			if (total == 0) {
				pd.dismiss();
				if (finish_after == true) {
					finish();
				}
				Toast.makeText(AdministrationFunctions.this, title, Toast.LENGTH_LONG).show();
				//progressThread.setState(UpdateThumbnailsThread.STATE_DONE);
			} else {
				num += 1;
				pd.incrementProgressBy(1);
				if (title.length() > 21) {
					title = title.substring(0, 20) + "...";
				}
				pd.setMessage(title);
			}
		}
	};

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			setContentView(R.layout.administration_functions);
			Bundle extras = getIntent().getExtras();
			try {
				if (extras.getString(DOAUTO).equals("export")) {
					finish_after = true;
					exportData();
				} else if (extras.getString(DOAUTO).equals("update_fields")) {
					finish_after = true;
					updateThumbnails(false);
				}
			} catch (NullPointerException e) {
				Logger.logError(e);
			}
			setupAdmin();
		} catch (Exception e) {
			Logger.logError(e);
		}
	}
	
	/**
	 * This function builds the Administration page in 4 sections. 
	 * 1. The button to goto the manage bookshelves activity
	 * 2. The button to export the database
	 * 3. The button to import the exported file into the database
	 * 4. The application version and link details
	 * 5. The link to paypal for donation
	 */
	public void setupAdmin() {
		/* Bookshelf Link */
		TextView bookshelf = (TextView) findViewById(R.id.bookshelf_label);
		bookshelf.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				manageBookselves();
				return;
			}
		});
		
		/* Manage Fields Link */
		TextView fields = (TextView) findViewById(R.id.fields_label);
		fields.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				manageFields();
				return;
			}
		});
		
		/* Export Link */
		TextView export = (TextView) findViewById(R.id.export_label);
		export.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				exportData();
				return;
			}
		});
		
		/* Import Link */
		TextView imports = (TextView) findViewById(R.id.import_label);
		/* Hack to pass this into the class */
		final AdministrationFunctions pthis = this;
		imports.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Verify - this can be a dangerous operation
				AlertDialog alertDialog = new AlertDialog.Builder(pthis).setMessage(R.string.import_alert).create();
				alertDialog.setTitle(R.string.import_data);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(pthis.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						importData();
						//Toast.makeText(pthis, importUpdated + " Existing, " + importCreated + " Created", Toast.LENGTH_LONG).show();
						return;
					}
				}); 
				alertDialog.setButton2(pthis.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
						return;
					}
				}); 
				alertDialog.show();
				return;
			}
		});

		// Debug ONLY!
		/* Backup Link */
		TextView backup = (TextView) findViewById(R.id.backup_label);
		backup.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mDbHelper.backupDbFile();
				Toast.makeText(AdministrationFunctions.this, R.string.backup_success, Toast.LENGTH_LONG).show();
				return;
			}
		});

		/* Export Link */
		TextView thumb = (TextView) findViewById(R.id.thumb_label);
		thumb.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Verify - this can be a dangerous operation
				AlertDialog alertDialog = new AlertDialog.Builder(pthis).setMessage(R.string.overwrite_thumbnail).create();
				alertDialog.setTitle(R.string.update_thumbnails);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(pthis.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						updateThumbnails(true);
						return;
					}
				}); 
				alertDialog.setButton2(pthis.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
						return;
					}
				}); 
				alertDialog.setButton3(pthis.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						updateThumbnails(false);
						return;
					}
				}); 
				alertDialog.show();
				return;
			}
		});
	}
	
	/**
	 * Load the Bookshelf Activity
	 */
	private void manageBookselves() {
		Intent i = new Intent(this, Bookshelf.class);
		startActivityForResult(i, ACTIVITY_BOOKSHELF);
	}
	
	/**
	 * Load the Manage Field Visibility Activity
	 */
	private void manageFields() {
		Intent i = new Intent(this, FieldVisibility.class);
		startActivityForResult(i, ACTIVITY_FIELD_VISIBILITY);
	}
	
	/**
	 * Update all (non-existent) thumbnails
	 * 
	 * There is a current limitation that restricts the search to only books with an ISBN
	 */
	private void updateThumbnails(boolean overwrite) {

		Cursor books = mDbHelper.fetchAllBooks("b." + CatalogueDBAdapter.KEY_ROWID, "All Books", "", "", "", "", "");
		UpdateThumbnailsThread thread = new UpdateThumbnailsThread(mTaskManager, overwrite, books, mThumbnailsHandler);
		thread.start();
	}


	/**
	 * Export all data to a CSV file
	 * 
	 * return void
	 */
	public void exportData() {
		ExportThread thread = new ExportThread(mTaskManager, mExportHandler);
		thread.start();		
	}

	/**
	 * This program reads a text file line by line and print to the console. It uses
	 * FileOutputStream to read the file.
	 * 
	 */
	public ArrayList<String> readFile() {
		ArrayList<String> importedString = new ArrayList<String>();
		
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), UTF8),BUFFER_SIZE);
			String line = "";
			while ((line = in.readLine()) != null) {
				importedString.add(line);
			}
			in.close();
		} catch (FileNotFoundException e) {
			Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		} catch (IOException e) {
			Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		}
		return importedString;
	}
	
//	private class ImportThread extends Thread {
//		//public ArrayList<String> export = null;
//		private Handler mHandler;
//		
//		/**
//		 * @param handler
//		 */
//		public ImportThread(Handler handler) {
//			mHandler = handler;
//		}
//		
//		private void sendMessage(int num, String title) {
//			/* Send message to the handler */
//			Message msg = mHandler.obtainMessage();
//			Bundle b = new Bundle();
//			b.putInt("total", num);
//			b.putString("title", title);
//			msg.setData(b);
//			mHandler.sendMessage(msg);
//			return;
//		}
//
//		//
//		// This CSV parser is not a complete parser, but it will parse files exported by older 
//		// versions. At some stage in the future it would be good to allow full CSV export 
//		// and import to allow for escape('\') chars so that cr/lf can be preserved.
//		// 
//		private String[] returnRow(String row) {
//			// Need to handle double quotes etc
//			char sep = ',';				// CSV seperator
//			char quoteChar = '"';		// CSV quote char
//			int pos = 0;				// Current position
//			boolean inQuote = false;	// In a quoted string
//			char c;						// 'Current' char
//			char next					// 'Next' char 
//					= (row.length() > 0) ? row.charAt(0) : '\0';
//			int endPos					// Last position in row 
//					= row.length() - 1;
//			ArrayList<String> fields	// Array of fields found in row
//					= new ArrayList<String>();
//
//			StringBuilder bld			// Temp. storage for current field
//					= new StringBuilder();
//
//			while (next != '\0')
//			{
//				// Get current and next char
//				c = next;
//				next = (pos < endPos) ? row.charAt(pos+1) : '\0';
//
//				if (inQuote)
//				{
//					if (c == quoteChar) {
//						if (next == quoteChar)
//						{
//							// Double-quote: Advance one more and append a single quote
//							pos++;
//							next = (pos < endPos) ? row.charAt(pos+1) : '\0';
//							bld.append(c);
//						} else {
//							// Leave the quote
//							inQuote = false;
//						}
//					} else {
//						// Append anything else that appears in quotes
//						bld.append(c);
//					}
//				} else {
//					if (bld.length() == 0 && (c == ' ' || c == '\t') ) {
//						// Skip leading white space
//					} else if (c == quoteChar) {
//						if (bld.length() > 0) {
//							// Fields with quotes MUST be quoted...
//							throw new IllegalArgumentException();
//						} else {
//							inQuote = true;
//						}
//					} else if (c == sep) {
//						// Add this field and reset it.
//						fields.add(bld.toString());
//						bld = new StringBuilder();
//					} else {
//						// Just append the char
//						bld.append(c);
//					}
//				}
//				pos++;
//			};
//
//			// Add the remaining chunk
//			fields.add(bld.toString());
//
//			// Return the result as a String[].
//			String[] imported = new String[fields.size()];
//			fields.toArray(imported);
//
//			return imported;
//		}
//
//
//		// Require a column
//		private void requireColumn(ContentValues values, String name) {
//			if (!values.containsKey(name))
//				throw new RuntimeException("File must contain column named " + name);
//		}
//
//		private void requireNonblank(ContentValues values, String name, int row) {
//			if (values.getAsString(name).length() == 0)
//				throw new RuntimeException("Column " + name + " is blank at line " + row);
//		}
//
//		@Override
//		public void run() {
//			Looper.prepare();
//
//			// Container for values.
//			ContentValues values = new ContentValues();
//
//			String[] names = returnRow(export.get(0));
//			for(int i = 0; i < names.length; i++) {
//				names[i] = names[i].toLowerCase();
//				values.put(names[i], "");
//			}
//
//			// Make sure required fields are present.
//			requireColumn(values, CatalogueDBAdapter.KEY_ROWID);
//			requireColumn(values, CatalogueDBAdapter.KEY_FAMILY_NAME);
//
//			if (!values.containsKey(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED)) {
//				values.put(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, "");
//			}
//
//			int row = 1;
//			int num = 0;
//
//			/* Iterate through each imported row */
//			while (row < export.size()) {
//				num++;
//				String[] imported = returnRow(export.get(row));
//				row++;
//
//				for(int i = 0; i < names.length; i++) {
//					values.put(names[i], imported[i]);
//				}
//
//				// Validate ID
//				String idVal = values.getAsString(CatalogueDBAdapter.KEY_ROWID.toLowerCase());
//				if (idVal == "") {
//					idVal = "0";
//					values.put(CatalogueDBAdapter.KEY_ROWID, idVal);
//				}
//
//				requireNonblank(values, CatalogueDBAdapter.KEY_FAMILY_NAME, row);
//				requireNonblank(values, CatalogueDBAdapter.KEY_TITLE, row);
//
//				String family = values.getAsString(CatalogueDBAdapter.KEY_FAMILY_NAME);
//				String given = "";
//
//				if (values.containsKey(CatalogueDBAdapter.KEY_GIVEN_NAMES))
//					given = values.getAsString(CatalogueDBAdapter.KEY_GIVEN_NAMES);
//				String title = values.getAsString(CatalogueDBAdapter.KEY_TITLE);
//
//				values.put(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, family + ", " + given);
//
//				// Make sure we have bookself_text if we imported bookshelf
//				if (values.containsKey(CatalogueDBAdapter.KEY_BOOKSHELF) && !values.containsKey("bookshelf_text")) {
//					values.put("bookshelf_text", values.getAsString(CatalogueDBAdapter.KEY_BOOKSHELF));
//				}
//
//				try {
//					if (idVal.equals("0")) {
//						// ID is unknown, may be new. Check if it exists in the current database.
//						Cursor book = null;
//						int rows = 0;
//						// If the ISBN is specified, use it as a definitive lookup.
//						String isbn = values.getAsString(CatalogueDBAdapter.KEY_ISBN);
//						if (isbn != "") {
//							book = mDbHelper.fetchBookByISBN(isbn);
//							rows = book.getCount();
//						} else {
//							if (rows == 0) {
//								book = mDbHelper.fetchByAuthorAndTitle(family, given, title);
//								rows = book.getCount();
//							}
//						}
//						if (rows != 0) {
//							book.moveToFirst();
//							// Its a new entry, but the ISBN exists
//							Integer id = book.getInt(0);
//							values.put(CatalogueDBAdapter.KEY_ROWID, book.getString(0));
//							mDbHelper.updateBook(id,values);
//							importUpdated++;
//						} else {
//							Long id = mDbHelper.createBook(values);
//							idVal = id.toString();
//							values.put(CatalogueDBAdapter.KEY_ROWID, idVal);
//							importCreated++;
//						}
//					} else {
//						Long id = Long.parseLong(idVal);
//						Cursor book = mDbHelper.fetchBookById(id);
//						int rows = book.getCount();
//						if (rows == 0) {
//							id = mDbHelper.createBook(values);
//							importCreated++;
//							idVal = id.toString();
//							values.put(CatalogueDBAdapter.KEY_ROWID, idVal);
//						} else {
//							// Book exists and should be updated if it has changed
//							mDbHelper.updateBook(id, values);
//							importUpdated++;
//						}
//					}
//				} catch (Exception e) {
//					Logger.logError(e);
//				}
//
//				if (!values.get(CatalogueDBAdapter.KEY_LOANED_TO).equals("")) {
//					mDbHelper.createLoan(values);
//				}
//
//				if (values.containsKey(CatalogueDBAdapter.KEY_ANTHOLOGY)) {
//					int anthology = Integer.parseInt(values.getAsString(CatalogueDBAdapter.KEY_ANTHOLOGY));
//					int id = Integer.parseInt(values.getAsString(CatalogueDBAdapter.KEY_ROWID));
//					if (anthology == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS || anthology == CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR) {
//						int oldi = 0;
//						String anthology_titles = values.getAsString("anthology_titles");
//						int i = anthology_titles.indexOf("|", oldi);
//						while (i > -1) {
//							String extracted_title = anthology_titles.substring(oldi, i).trim();
//							
//							int j = extracted_title.indexOf("*");
//							if (j > -1) {
//								String anth_title = extracted_title.substring(0, j).trim();
//								String anth_author = extracted_title.substring((j+1)).trim();
//								mDbHelper.createAnthologyTitle(id, anth_author, anth_title);
//							}
//							oldi = i + 1;
//							i = anthology_titles.indexOf("|", oldi);
//						}
//					}
//				}
//
//				sendMessage(num, title);
//			}
//			sendMessage(0, "Import Complete");
//		}
//	}	

	
	/**
	 * Import all data from the CSV file
	 * 
	 * return void
	 */
	private void importData() {
		ArrayList<String> export = readFile();
		ImportThread thread = new ImportThread(mTaskManager, mImportHandler, export);
		thread.start();		
//		importUpdated = 0;
//		importCreated = 0;
//		ArrayList<String> export = readFile();
//		
//		pd = new ProgressDialog(AdministrationFunctions.this);
//		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//		pd.setMessage("Importing...");
//		pd.setCancelable(false);
//		pd.setMax(export.size() - 1);
//		pd.show();
//
//		ImportThread thread = new ImportThread(mProgressHandler);
//		thread.export = export;
//		thread.start();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ACTIVITY_BOOKSHELF:
		case ACTIVITY_FIELD_VISIBILITY:
			//do nothing (yet)
			break;
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	} 

	@Override
	protected void onPause() {
		super.onPause();
	} 
	@Override
	protected void onResume() {
		super.onResume();
	} 

	@Override
	TaskHandler getTaskHandler(ManagedTask t) {
		// If we had a task, create the progress dialog and reset the pointers.
		if (t instanceof UpdateThumbnailsThread) {
			return mThumbnailsHandler;
		} else if (t instanceof ExportThread) {
			return mExportHandler;
		} else if (t instanceof ImportThread) {
			return mImportHandler;
		} else {
			return null;
		}
	}

}
