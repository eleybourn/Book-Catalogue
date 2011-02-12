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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import com.eleybourn.bookcatalogue.UpdateThumbnailsThread.BookInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class AdministrationFunctions extends Activity {
	private static final int ACTIVITY_BOOKSHELF=1;
	private static final int ACTIVITY_FIELD_VISIBILITY=2;
	private CatalogueDBAdapter mDbHelper;
	private int importUpdated = 0;
	private int importCreated = 0;
	public static String filePath = Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION;
	public static String fileName = filePath + "/export.csv";
	public static String UTF8 = "utf8";
	public static int BUFFER_SIZE = 8192;
	private ProgressDialog pd = null;
	private int num = 0;
	private boolean finish_after = false;
	UpdateThumbnailsThread mUpdateThumbnailsThread = null;

	public static final String DOAUTO = "do_auto";
	
	final UpdateThumbnailsThread.LookupHandler mLookupHandler = new UpdateThumbnailsThread.LookupHandler() {
		@Override
		public void onFinish(LinkedList<BookInfo> queue) {
			if (finish_after == true) {
				finish();
			}
		}

		@Override
		public void onFound(LinkedList<BookInfo> queue) {
			Iterator<BookInfo> i = queue.iterator();
			while (i.hasNext()) {
				BookInfo bi = i.next();
				Log.i("BookCatalogue", "Updating book " + bi.id);
				mDbHelper.updateBook(bi.id, bi.bookData);
			}
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
				//do nothing
			}
			setupAdmin();
		} catch (Exception e) {
			//Log.e("Book Catalogue", "Unknown Exception - BC onCreate - " + e.getMessage() );
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
	
//	private class UpdateThumbnailsThread extends Thread {
//		public boolean overwrite = false;
//		public Cursor books = null;
//		private Handler mHandler;
//		
//		/**
//		 * @param handler
//		 */
//		public UpdateThumbnailsThread(Handler handler) {
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
//		@Override
//		public void run() {
//			Looper.prepare();
//			
//			/* Test write to the SDCard */
//			try {
//				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath + "/.nomedia"), UTF8), BUFFER_SIZE);
//				out.write("");
//				out.close();
//			} catch (IOException e) {
//				sendMessage(0, "Thumbnail Download Failed - Could not write to SDCard");
//				return;
//			}
//			
//			startManagingCursor(books);
//			int num = 0;
//			//try {
//				while (books.moveToNext()) {
//
//					// Copy the fields from the cursor
//					ContentValues values = new ContentValues();
//					for(int i = 0; i < books.getColumnCount(); i++) {
//						values.put(books.getColumnName(i), books.getString(i));
//					}
//					
//					num++;
//					// delete any tmp thumbnails //
//					try {
//						File delthumb = CatalogueDBAdapter.fetchThumbnail(0);
//						delthumb.delete();
//					} catch (Exception e) {
//						// do nothing - this is the expected behaviour 
//					}
//
//					Long id = values.getAsLong(CatalogueDBAdapter.KEY_ROWID);
//					String isbn = values.getAsString(CatalogueDBAdapter.KEY_ISBN);
//					String author = values.getAsString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
//					String title = values.getAsString(CatalogueDBAdapter.KEY_TITLE);
//					String genre = values.getAsString(CatalogueDBAdapter.KEY_GENRE);
//					String description = values.getAsString(CatalogueDBAdapter.KEY_DESCRIPTION);
//
//					ContentValues bookData = new ContentValues();
//
//					File thumb = CatalogueDBAdapter.fetchThumbnail(id);
//
//					if (isbn.equals("") && author.equals("") && title.equals("")) {
//						// Must have an ISBN to be able to search
//						sendMessage(num, "Skip - " + title);
//						//TODO: searchGoogle(AUTHOR)
//					} else if (overwrite == true || !thumb.exists() || genre.equals("") || description.equals("")) {
//						sendMessage(num, title);
//						BookISBNSearch bis = new BookISBNSearch();
//						//String[] book = {0=author, 1=title, 2=isbn, 3=publisher, 4=date_published, 5=rating,  6=bookshelf, 
//						//	7=read, 8=series, 9=pages, 10=series_num, 11=list_price, 12=anthology, 13=location, 14=read_start, 
//						//	15=read_end, 16=audiobook, 17=signed, 18=description, 19=genre};
//
//						try {
//							bis.searchGoogle(isbn, author, title, bookData);							
//						} catch (Exception e) {
//							
//						}
//
//						try {
//							bis.searchAmazon(isbn, author, title, bookData);
//						} catch (Exception e) {
//							
//						}
//
//						// LibraryThing
//						try {
//							if (bookData.containsKey(CatalogueDBAdapter.KEY_ISBN)) {
//								String bdIsbn = bookData.getAsString(CatalogueDBAdapter.KEY_ISBN);
//								if (bdIsbn.length() > 0) {
//									LibraryThingManager ltm = new LibraryThingManager(bookData);
//									ltm.searchByIsbn(bdIsbn);
//								}
//							}
//						} catch (Exception e) {
//							
//						}
//						Utils.cleanupThumbnails(bookData);
//
//						File tmpthumb = CatalogueDBAdapter.fetchThumbnail(0);
//						/* Copy tmpthumb over realthumb */
//						if (overwrite == true || !thumb.exists()) {
//							try {
//								tmpthumb.renameTo(thumb);
//							} catch (Exception e) {
//								//do nothing
//							}
//						}
//
//						if (description.equals("") && bookData.containsKey(CatalogueDBAdapter.KEY_DESCRIPTION)) {
//							values.put(CatalogueDBAdapter.KEY_DESCRIPTION, bookData.getAsString(CatalogueDBAdapter.KEY_DESCRIPTION));
//						}
//						if (genre.equals("") && bookData.containsKey(CatalogueDBAdapter.KEY_GENRE)) {
//							values.put(CatalogueDBAdapter.KEY_GENRE, bookData.getAsString(CatalogueDBAdapter.KEY_GENRE));
//						}
//						mDbHelper.updateBook(id, values);
//						
//					} else {
//						sendMessage(num, "Skip - " + title);
//					}
//				}
//			//} catch (Exception e) {
//				// do nothing
//			//}
//			sendMessage(0, num + " Books Searched");
//		}
//		
//	}	
//	
	/**
	 * Update all (non-existent) thumbnails
	 * 
	 * There is a current limitation that restricts the search to only books with an ISBN
	 */
	private void updateThumbnails(boolean overwrite) {

		Cursor books = mDbHelper.fetchAllBooks("b." + CatalogueDBAdapter.KEY_ROWID, "All Books", "", "", "", "", "");
		mUpdateThumbnailsThread = new UpdateThumbnailsThread(this, overwrite, books, mLookupHandler);
		mUpdateThumbnailsThread.start();
		//Toast.makeText(AdministrationFunctions.this, R.string.download_thumbs, Toast.LENGTH_LONG).show();
	}
	
	private class ExportThread extends Thread {
		public Cursor books = null;
		private Handler mHandler;
		
		/**
		 * @param handler
		 */
		public ExportThread(Handler handler) {
			mHandler = handler;
		}
		
		private void sendMessage(int num, String title) {
			/* Send message to the handler */
			Message msg = mHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putInt("total", num);
			b.putString("title", title);
			msg.setData(b);
			mHandler.sendMessage(msg);
			return;
		}

		@Override
		public void run() {
			Looper.prepare();
			int num = 0;
			
			/* Test write to the SDCard */
			try {
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath + "/.nomedia"), UTF8), BUFFER_SIZE);
				out.write("");
				out.close();
			} catch (IOException e) {
				sendMessage(0, "Export Failed - Could not write to SDCard");
				return;
			}
			
			String export = 
				'"' + CatalogueDBAdapter.KEY_ROWID + "\"," + 			//0
				'"' + CatalogueDBAdapter.KEY_FAMILY_NAME + "\"," + 		//1
				'"' + CatalogueDBAdapter.KEY_GIVEN_NAMES + "\"," + 		//2
				'"' + CatalogueDBAdapter.KEY_AUTHOR_ID + "\"," + 		//3
				'"' + CatalogueDBAdapter.KEY_TITLE + "\"," + 			//4
				'"' + CatalogueDBAdapter.KEY_ISBN + "\"," + 			//5
				'"' + CatalogueDBAdapter.KEY_PUBLISHER + "\"," + 		//6
				'"' + CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\"," + 	//7
				'"' + CatalogueDBAdapter.KEY_RATING + "\"," + 			//8
				'"' + "bookshelf_id\"," + 								//9
				'"' + CatalogueDBAdapter.KEY_BOOKSHELF + "\"," +		//10
				'"' + CatalogueDBAdapter.KEY_READ + "\"," +				//11
				'"' + CatalogueDBAdapter.KEY_SERIES_NAME + "\"," +		//12
				'"' + CatalogueDBAdapter.KEY_SERIES_NUM + "\"," +		//13
				'"' + CatalogueDBAdapter.KEY_PAGES + "\"," + 			//14
				'"' + CatalogueDBAdapter.KEY_NOTES + "\"," + 			//15
				'"' + CatalogueDBAdapter.KEY_LIST_PRICE + "\"," + 		//16
				'"' + CatalogueDBAdapter.KEY_ANTHOLOGY+ "\"," + 		//17
				'"' + CatalogueDBAdapter.KEY_LOCATION+ "\"," + 			//18
				'"' + CatalogueDBAdapter.KEY_READ_START+ "\"," + 		//19
				'"' + CatalogueDBAdapter.KEY_READ_END+ "\"," + 			//20
				'"' + CatalogueDBAdapter.KEY_FORMAT+ "\"," + 			//21
				'"' + CatalogueDBAdapter.KEY_SIGNED+ "\"," + 			//22
				'"' + CatalogueDBAdapter.KEY_LOANED_TO+ "\"," +			//23 
				'"' + "anthology_titles" + "\"," +						//24 
				'"' + CatalogueDBAdapter.KEY_DESCRIPTION+ "\"," + 		//25
				'"' + CatalogueDBAdapter.KEY_GENRE+ "\"," + 			//26
				"\n";
			if (books.moveToFirst()) {
				do { 
					num++;
					long id = books.getLong(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
					String dateString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1])+1;
						int dd = Integer.parseInt(date[2]);
						dateString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String dateReadStartString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1])+1;
						int dd = Integer.parseInt(date[2]);
						dateReadStartString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String dateReadEndString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1])+1;
						int dd = Integer.parseInt(date[2]);
						dateReadEndString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String anthology = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
					String anthology_titles = "";
					if (anthology.equals(CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS + "") || anthology.equals(CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR + "")) {
						Cursor titles = mDbHelper.fetchAnthologyTitlesByBook(id);
						if (titles.moveToFirst()) {
							do { 
								String anth_title = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
								String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_ID));
								anthology_titles += anth_title + " * " + anth_author + "|";
							} while (titles.moveToNext()); 
						}
					}
					String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
					//Display the selected bookshelves
					Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(id);
					String bookshelves_id_text = "";
					String bookshelves_name_text = "";
					while (bookshelves.moveToNext()) {
						bookshelves_id_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_ROWID)) + BookEditFields.BOOKSHELF_SEPERATOR;
						bookshelves_name_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BookEditFields.BOOKSHELF_SEPERATOR;
					}
					bookshelves.close();
					
					String row = "";
					row += "\"" + formatCell(id) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FAMILY_NAME))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GIVEN_NAMES))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_ID))) + "\",";
					row += "\"" + formatCell(title) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER))) + "\",";
					row += "\"" + formatCell(dateString) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING))) + "\",";
					row += "\"" + formatCell(bookshelves_id_text) + "\",";
					row += "\"" + formatCell(bookshelves_name_text) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NAME))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE))) + "\",";
					row += "\"" + formatCell(anthology) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION))) + "\",";
					row += "\"" + formatCell(dateReadStartString) + "\",";
					row += "\"" + formatCell(dateReadEndString) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO))+"") + "\",";
					row += "\"" + formatCell(anthology_titles) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DESCRIPTION))) + "\",";
					row += "\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GENRE))) + "\",";
					row += "\n";
					export += row;
					sendMessage(num, title);
				}
				while (books.moveToNext()); 
			} 
			
			/* write to the SDCard */
			try {
				backupExport();
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), UTF8), BUFFER_SIZE);
				out.write(export);
				out.close();
				sendMessage(0, "Export Complete");
				//Toast.makeText(AdministrationFunctions.this, R.string.export_complete, Toast.LENGTH_LONG).show();
			} catch (IOException e) {
				//Log.e("Book Catalogue", "Could not write to the SDCard");		
				//Toast.makeText(AdministrationFunctions.this, R.string.export_failed, Toast.LENGTH_LONG).show();
				sendMessage(0, "Export Failed - Could not write to SDCard");
			}
		}
		
		private void backupExport() {
			File export = new File(fileName);
			File backup = new File(fileName + ".bak");
			export.renameTo(backup);
		}
		
		/**
		 * Double quote all "'s and remove all newlines
		 * 
		 * @param cell The cell the format
		 * @return The formatted cell
		 */
		private String formatCell(String cell) {
			try {
				if (cell.equals("null")) {
					return "";
				}
				return cell.replaceAll("\"", "\"\"").replaceAll("\n", "").replaceAll("\r", "");
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

	/**
	 * Export all data to a CSV file
	 * 
	 * return void
	 */
	public void exportData() {
		Cursor books = mDbHelper.exportBooks();
		pd = new ProgressDialog(AdministrationFunctions.this);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setMessage("Exporting...");
		pd.setCancelable(false);
		pd.setMax(books.getCount());
		pd.show();
		
		ExportThread thread = new ExportThread(mProgressHandler);
		thread.books = books;
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
		} catch (IOException e) {
			Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
		}
		return importedString;
	}
	
	private class ImportThread extends Thread {
		public ArrayList<String> export = null;
		private Handler mHandler;
		
		/**
		 * @param handler
		 */
		public ImportThread(Handler handler) {
			mHandler = handler;
		}
		
		private void sendMessage(int num, String title) {
			/* Send message to the handler */
			Message msg = mHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putInt("total", num);
			b.putString("title", title);
			msg.setData(b);
			mHandler.sendMessage(msg);
			return;
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
		private void requireColumn(ContentValues values, String name) {
			if (!values.containsKey(name))
				throw new RuntimeException("File must contain column named " + name);
		}

		private void requireNonblank(ContentValues values, String name, int row) {
			if (values.getAsString(name).length() == 0)
				throw new RuntimeException("Column " + name + " is blank at line " + row);
		}

		@Override
		public void run() {
			Looper.prepare();

			// Container for values.
			ContentValues values = new ContentValues();

			String[] names = returnRow(export.get(0));
			for(int i = 0; i < names.length; i++) {
				names[i] = names[i].toLowerCase();
				values.put(names[i], "");
			}

			// Make sure required fields are present.
			requireColumn(values, CatalogueDBAdapter.KEY_ROWID);
			requireColumn(values, CatalogueDBAdapter.KEY_FAMILY_NAME);

			if (!values.containsKey(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED)) {
				values.put(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, "");
			}

			int row = 1;
			int num = 0;

			/* Iterate through each imported row */
			while (row < export.size()) {
				num++;
				String[] imported = returnRow(export.get(row));
				row++;

				for(int i = 0; i < names.length; i++) {
					values.put(names[i], imported[i]);
				}

				// Validate ID
				String idVal = values.getAsString(CatalogueDBAdapter.KEY_ROWID.toLowerCase());
				if (idVal == "") {
					idVal = "0";
					values.put(CatalogueDBAdapter.KEY_ROWID, idVal);
				}

				requireNonblank(values, CatalogueDBAdapter.KEY_FAMILY_NAME, row);
				requireNonblank(values, CatalogueDBAdapter.KEY_TITLE, row);

				String family = values.getAsString(CatalogueDBAdapter.KEY_FAMILY_NAME);
				String given = "";

				if (values.containsKey(CatalogueDBAdapter.KEY_GIVEN_NAMES))
					given = values.getAsString(CatalogueDBAdapter.KEY_GIVEN_NAMES);
				String title = values.getAsString(CatalogueDBAdapter.KEY_TITLE);

				values.put(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED, family + ", " + given);

				// Make sure we have bookself_text if we imported bookshelf
				if (values.containsKey(CatalogueDBAdapter.KEY_BOOKSHELF) && !values.containsKey("bookshelf_text")) {
					values.put("bookshelf_text", values.getAsString(CatalogueDBAdapter.KEY_BOOKSHELF));
				}

				try {
					if (idVal.equals("0")) {
						// ID is unknown, may be new. Check if it exists in the current database.
						Cursor book = null;
						int rows = 0;
						// If the ISBN is specified, use it as a definitive lookup.
						String isbn = values.getAsString(CatalogueDBAdapter.KEY_ISBN);
						if (isbn != "") {
							book = mDbHelper.fetchBookByISBN(isbn);
							rows = book.getCount();
						} else {
							if (rows == 0) {
								book = mDbHelper.fetchByAuthorAndTitle(family, given, title);
								rows = book.getCount();
							}
						}
						if (rows != 0) {
							book.moveToFirst();
							// Its a new entry, but the ISBN exists
							Integer id = book.getInt(0);
							values.put(CatalogueDBAdapter.KEY_ROWID, book.getString(0));
							mDbHelper.updateBook(id,values);
							importUpdated++;
						} else {
							Long id = mDbHelper.createBook(values);
							idVal = id.toString();
							values.put(CatalogueDBAdapter.KEY_ROWID, idVal);
							importCreated++;
						}
					} else {
						Long id = Long.parseLong(idVal);
						Cursor book = mDbHelper.fetchBookById(id);
						int rows = book.getCount();
						if (rows == 0) {
							id = mDbHelper.createBook(values);
							importCreated++;
							idVal = id.toString();
							values.put(CatalogueDBAdapter.KEY_ROWID, idVal);
						} else {
							// Book exists and should be updated if it has changed
							mDbHelper.updateBook(id, values);
							importUpdated++;
						}
					}
				} catch (Exception e) {
					//Log.e("BC", "Import Book (Single) Error");
					// do nothing
				}

				if (!values.get(CatalogueDBAdapter.KEY_LOANED_TO).equals("")) {
					mDbHelper.createLoan(values);
				}

				if (values.containsKey(CatalogueDBAdapter.KEY_ANTHOLOGY)) {
					int anthology = Integer.parseInt(values.getAsString(CatalogueDBAdapter.KEY_ANTHOLOGY));
					int id = Integer.parseInt(values.getAsString(CatalogueDBAdapter.KEY_ROWID));
					if (anthology == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS || anthology == CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR) {
						int oldi = 0;
						String anthology_titles = values.getAsString("anthology_titles");
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

				sendMessage(num, title);
			}
			sendMessage(0, "Import Complete");
		}
	}	

	
	/**
	 * Import all data from the CSV file
	 * 
	 * return void
	 */
	private void importData() {
		importUpdated = 0;
		importCreated = 0;
		ArrayList<String> export = readFile();
		
		pd = new ProgressDialog(AdministrationFunctions.this);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setMessage("Importing...");
		pd.setCancelable(false);
		pd.setMax(export.size() - 1);
		pd.show();

		ImportThread thread = new ImportThread(mProgressHandler);
		thread.export = export;
		thread.start();
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
	protected void onRestoreInstanceState(Bundle inState) {
		// Get the AsyncTask
		mUpdateThumbnailsThread = (UpdateThumbnailsThread) getLastNonConfigurationInstance();

		if (mUpdateThumbnailsThread != null && !mUpdateThumbnailsThread.isFinished()) {
			// If we had a task, create the progross dialog and reset the pointers.
			mUpdateThumbnailsThread.reconnect(this, mLookupHandler);
		}
		super.onRestoreInstanceState(inState);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		// Save the AsyncTask and remove the local refs.
		UpdateThumbnailsThread t = mUpdateThumbnailsThread;
		if (mUpdateThumbnailsThread != null) {
			mUpdateThumbnailsThread.disconnect();
		}
		return t;
	}

}
