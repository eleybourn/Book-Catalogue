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

//import android.R;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class Administration extends Activity {

    private static final int ACTIVITY_BOOKSHELF=1;
    private CatalogueDBAdapter mDbHelper;
    
    private int importUpdated = 0;
    private int importCreated = 0;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			setContentView(R.layout.administration);
			setupAdmin();
    	} catch (Exception e) {
    		//Log.e("Book Catalogue", "Unknown Exception - BC onCreate - " + e.getMessage() );
    	}
    }
    
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
    	final Administration pthis = this;
    	imports.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
	        	// Verify - this can be a dangerous operation
	        	AlertDialog alertDialog = new AlertDialog.Builder(pthis).setMessage(R.string.import_alert).create();
	            alertDialog.setTitle(R.string.import_data);
	            alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
	            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
	            	public void onClick(DialogInterface dialog, int which) {
	            		importData();
						Toast.makeText(pthis, importUpdated + " Updated, " + importCreated + " Created", Toast.LENGTH_LONG).show();
	            		return;
	            	}
	            }); 
	            alertDialog.setButton2("Cancel", new DialogInterface.OnClickListener() {
	            	public void onClick(DialogInterface dialog, int which) {
	            		//do nothing
	            		return;
	            	}
	            }); 
	            alertDialog.show();
	            return;
			}
    	});

    	/* Version Number */
    	TextView release = (TextView) findViewById(R.id.version);
    	PackageManager manager = this.getPackageManager();
        PackageInfo info;
		try {
			info = manager.getPackageInfo(this.getPackageName(), 0);
	        //String packageName = info.packageName;
	        //int versionCode = info.versionCode;
	        String versionName = info.versionName;
	    	release.setText(versionName);
		} catch (NameNotFoundException e) {
			//do nothing
		}
		
		/*
		WebView webview = (WebView) findViewById(R.id.donate_web);
		String summary = "<form stye=\"background:#2d2d2d;\" action=\"https://www.paypal.com/cgi-bin/webscr\" method=\"post\">" + 
						"<input type=\"hidden\" name=\"cmd\" value=\"_s-xclick\">" + 
						"<input type=\"hidden\" name=\"encrypted\" value=\"-----BEGIN PKCS7-----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-----END PKCS7-----\"" +
						"\"> " +
						"<input type=\"image\" src=\"https://www.paypal.com/en_AU/i/btn/btn_donateCC_LG.gif\" border=\"0\" name=\"submit\" alt=\"PayPal - The safer, easier way to pay online.\">" +
						"<img alt=\"\" border=\"0\" src=\"https://www.paypal.com/en_AU/i/scr/pixel.gif\" width=\"1\" height=\"1\">" + 
						"</form>" +
						"";
		webview.loadData(summary, "text/html", "utf-8");
		*/
		
		ImageView donate = (ImageView) findViewById(R.id.donate_url);
		donate.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=94US4N9MQGDMY&lc=AU&currency_code=AUD&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted"));
				startActivity(loadweb); 
				return;
			}
    	});
    	
    
    }
    
    /*
     * Load the BookEdit Activity
     * 
     * return void
     */
    private void manageBookselves() {
        Intent i = new Intent(this, Bookshelf.class);
        startActivityForResult(i, ACTIVITY_BOOKSHELF);
    }
	
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
    		"\n";
        if (books.moveToFirst()) {
            do { 
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FAMILY_NAME)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GIVEN_NAMES)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow("bookshelf_id")) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM)) + "\t";
            	export += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES)) + "\t";
            	export += "\n";
            } 
            while (books.moveToNext()); 
        } 
        
        /* write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/export.tab"));
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
	public ArrayList<String> readFile(String filename) {

		ArrayList<String> importedString = new ArrayList<String>();
		File file = new File(filename);
		FileInputStream fis = null;
		BufferedInputStream bis = null;
		DataInputStream dis = null;

		try {
			fis = new FileInputStream(file);
			// Here BufferedInputStream is added for fast reading.
			bis = new BufferedInputStream(fis);
			dis = new DataInputStream(bis);

			// dis.available() returns 0 if the file does not have more lines.
			while (dis.available() != 0) {
				// this statement reads the line from the file and print it to the console.
				importedString.add(dis.readLine());
			}
			// dispose all the resources after using them.
			fis.close();
			bis.close();
			dis.close();
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
    	ArrayList<String> export = readFile(Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/export.tab");
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

    		String author = family + ", " + given;
    		
    		if (id == 0) {
    			// Book is new. It does not exist in the current database
            	if (!isbn.equals("")) {
            		Cursor book = mDbHelper.fetchBookByISBN(isbn);
            		int rows = book.getCount();
            		if (rows != 0) {
                		// Its a new entry, but the ISBN exists
            			book.moveToFirst();
            			mDbHelper.updateBook(book.getLong(0), author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
            			importUpdated++;
            			continue;
            		}
            	} 
                mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
                importCreated++;
    			continue;
    			
    		} else {
    			// Book exists and should be updated if it has changed
    			mDbHelper.updateBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
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