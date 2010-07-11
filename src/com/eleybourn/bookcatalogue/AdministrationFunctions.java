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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
	private CatalogueDBAdapter mDbHelper;
	private int importUpdated = 0;
	private int importCreated = 0;
	private String filePath = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/export.csv";
	private String UTF8 = "utf8";
	private int BUFFER_SIZE = 8192;
	private ProgressDialog pd = null;
	private int num = 0;
	
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
			setupAdmin();
		} catch (Exception e) {
			//Log.e("Book Catalogue", "Unknown Exception - BC onCreate - " + e.getMessage() );
		}
	}
	
	/**
	 * This function builds the Admin page in 4 sections. 
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
						Toast.makeText(pthis, importUpdated + " Updated, " + importCreated + " Created", Toast.LENGTH_LONG).show();
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
	
	private class UpdateThumbnailsThread extends Thread {
		public boolean overwrite = false;
		public Cursor books = null;
		private Handler mHandler;
		
		/**
		 * @param handler
		 */
		public UpdateThumbnailsThread(Handler handler) {
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
			String tmpThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/tmp.jpg";
			String realThumbFilename = "";
			startManagingCursor(books);
			int num = 0;
			while (books.moveToNext()) {
				int id = books.getInt(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
				String isbn = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN));
				String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
				
				num++;
				// delete any tmp thumbnails //
				try {
					File delthumb = new File(tmpThumbFilename);
					delthumb.delete();
				} catch (Exception e) {
					// do nothing - this is the expected behaviour 
				}
				
				realThumbFilename = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/" + id + ".jpg";
				File thumb = new File(realThumbFilename);
				if ((overwrite == true || !thumb.exists()) && !isbn.equals("")) {
					sendMessage(num, title);
					BookISBNSearch bis = new BookISBNSearch();
					bis.searchAmazon(isbn);
					File tmpthumb = new File(tmpThumbFilename);
					/* If amazon fails, try google books */
					if (!tmpthumb.exists()) {
						bis.searchGoogle(isbn);
						tmpthumb = new File(tmpThumbFilename);
					}
					
					/* Copy tmpthumb over realthumb */
					try {
						tmpthumb.renameTo(thumb);
					} catch (Exception e) {
						//do nothing
					}
					
				} else {
					sendMessage(num, "Skip - " + title);
				}
			}
			sendMessage(0, "Complete");
		}
		
	}	
	
	/**
	 * Update all (non-existent) thumbnails
	 * 
	 * There is a current limitation that restricts the search to only books with an ISBN
	 */
	private void updateThumbnails(boolean overwrite) {
		Cursor books = mDbHelper.fetchAllBooks("b." + CatalogueDBAdapter.KEY_ROWID, "All Books");
		
		pd = new ProgressDialog(AdministrationFunctions.this);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setMessage("Updating...");
		pd.setCancelable(false);
		pd.setMax(books.getCount());
		pd.show();
		
		//pd = ProgressDialog.show(this, getResources().getString(R.string.update_thumbnails), "Updating ...", false);
		//pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		//pd.setMax(100);
		
		UpdateThumbnailsThread thread = new UpdateThumbnailsThread(handler);
		thread.overwrite = overwrite;
		thread.books = books;
		thread.start();
		//Toast.makeText(AdministrationFunctions.this, R.string.download_thumbs, Toast.LENGTH_LONG).show();
	}
	
	final Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			int total = msg.getData().getInt("total");
			String title = msg.getData().getString("title");
			if (total == 0) {
				//Toast.makeText(AdministrationFunctions.this, num + " Thumbnail Updated", Toast.LENGTH_LONG).show();
				pd.dismiss();
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

	
	/*
	 * Export all data to a CSV file
	 * 
	 * return void
	 */
	private void exportData() {
		Cursor books = mDbHelper.exportBooks();
		String export = 
			CatalogueDBAdapter.KEY_ROWID + "\t" + 
			CatalogueDBAdapter.KEY_FAMILY_NAME + "\t" + 
			CatalogueDBAdapter.KEY_GIVEN_NAMES + "\t" + 
			CatalogueDBAdapter.KEY_AUTHOR + "\t" + 
			CatalogueDBAdapter.KEY_TITLE + "\t" + 
			CatalogueDBAdapter.KEY_ISBN + "\t" + 
			CatalogueDBAdapter.KEY_PUBLISHER + "\t" + 
			CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\t" + 
			CatalogueDBAdapter.KEY_RATING + "\t" + 
			"bookshelf_id\t" + 
			CatalogueDBAdapter.KEY_BOOKSHELF + "\t" +
			CatalogueDBAdapter.KEY_READ + "\t" +
			CatalogueDBAdapter.KEY_SERIES + "\t" + 
			CatalogueDBAdapter.KEY_SERIES_NUM + "\t" +
			CatalogueDBAdapter.KEY_PAGES + "\t" + 
			CatalogueDBAdapter.KEY_NOTES + "\t" + 
			CatalogueDBAdapter.KEY_LIST_PRICE + "\t" + 
			"\n";
		if (books.moveToFirst()) {
			do { 
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
				
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FAMILY_NAME)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GIVEN_NAMES)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER)) + "\t";
				export += dateString + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow("bookshelf_id")) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES)) + "\t";
				export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE)) + "\t";
				export += "\n";
			}
			while (books.moveToNext()); 
		} 
		
		/* write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), UTF8), BUFFER_SIZE);
			out.write(export);
			out.close();
			Toast.makeText(this, R.string.export_complete, Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			//Log.e("Book Catalogue", "Could not write to the SDCard");		
			Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * This program reads a text file line by line and print to the console. It uses
	 * FileOutputStream to read the file.
	 * 
	 */
	public ArrayList<String> readFile() {
		ArrayList<String> importedString = new ArrayList<String>();
		
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), UTF8),BUFFER_SIZE);
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
	
	/*
	 * Export all data to a CSV file
	 * 
	 * return void
	 */
	private void importData() {
		importUpdated = 0;
		importCreated = 0;
		ArrayList<String> export = readFile();
		int row = 1;
		
		/* Iterate through each imported row */
		while (row < export.size()) {
			String[] imported = export.get(row).split("\t");
			row++;
			/* Setup aliases for each cell*/
			Long id = null;
			try {
				id = Long.parseLong(imported[0]);
			} catch(Exception e) {
				id = Long.parseLong("0");
			}
			String family = imported[1]; 
			String given = imported[2]; 
			//String author_id = imported[3]; 
			String title = imported[4]; 
			String isbn = imported[5];
			String publisher = imported[6]; 
			String date_published = imported[7];
			try {
				String[] date = date_published.split("-");
				int yyyy = Integer.parseInt(date[0]);
				int mm = Integer.parseInt(date[1])-1;
				int dd = Integer.parseInt(date[2]);
				date_published = yyyy + "-" + mm + "-" + dd;
			} catch (Exception e) {
				//do nothing
			}
			
			float rating = 0;
			try {
				rating = Float.valueOf(imported[8]); 
			} catch (Exception e) {
				rating = 0;
			}
			//String bookshelf_id = imported[9]; 
			String bookshelf = imported[10];
			Boolean read;
			if (imported[11].equals("0")) {
				read = false;
			} else {
				read = true;
			}
			String series = imported[12]; 
			String series_num = imported[13];
			int pages = 0;
			try {
				pages = Integer.parseInt(imported[14]); 
			} catch (Exception e) {
				pages = 0;
			}
			//occasionally the notes will not import correctly (as it used to be the last field)
			String notes = "";
			try {
				notes = imported[15];
			} catch (ArrayIndexOutOfBoundsException e) {
				// do nothing
			}
			//occasionally the list_price will not import correctly (as it is the last field)
			String list_price = "";
			try {
				list_price = imported[16];
			} catch (ArrayIndexOutOfBoundsException e) {
				// do nothing
			}
			
			String author = family + ", " + given;
			if (id == 0) {
				// Book is new. It does not exist in the current database
				if (!isbn.equals("")) {
					Cursor book = mDbHelper.fetchBookByISBN(isbn);
					int rows = book.getCount();
					if (rows != 0) {
						// Its a new entry, but the ISBN exists
						book.moveToFirst();
						mDbHelper.updateBook(book.getLong(0), author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price);
						importUpdated++;
						continue;
					}
				} 
				mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price);
				importCreated++;
				continue;
				
			} else {
				// Book exists and should be updated if it has changed
				mDbHelper.updateBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price);
				importUpdated++;
				continue;
			}
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ACTIVITY_BOOKSHELF:
			//do nothing (yet)
			break;
		}
	}

}