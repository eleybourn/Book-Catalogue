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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import android.app.Activity;
import android.database.Cursor;

/*
 * A book catalogue application that integrates with Google Books.
 */
/* THIS DOES NOT WORK YET */
public class BookBackup extends Activity {
	public int mCount = 0;
	public CatalogueDBAdapter mDbHelper;
	
	public void onCreate() {
		mDbHelper = new CatalogueDBAdapter(this);
	}
	
	/* (non-Javadoc)
	 * @see android.app.backup.BackupAgent#onBackup(android.os.ParcelFileDescriptor, android.app.backup.BackupDataOutput, android.os.ParcelFileDescriptor)
	 */
	public void backup() {
		// Write structured data
		Cursor books = mDbHelper.exportBooks();
		
		// TODO: Fix this
		// This code is duplicated here and in exportData in AdministrationFunctions
		// I can't just call exportData as it is encapsulated in a progress bar 
		String export = 
			CatalogueDBAdapter.KEY_ROWID + "\t" + 			//0
			CatalogueDBAdapter.KEY_FAMILY_NAME + "\t" + 	//1
			CatalogueDBAdapter.KEY_GIVEN_NAMES + "\t" + 	//2
			CatalogueDBAdapter.KEY_AUTHOR + "\t" + 			//3
			CatalogueDBAdapter.KEY_TITLE + "\t" + 			//4
			CatalogueDBAdapter.KEY_ISBN + "\t" + 			//5
			CatalogueDBAdapter.KEY_PUBLISHER + "\t" + 		//6
			CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\t" + 	//7
			CatalogueDBAdapter.KEY_RATING + "\t" + 			//8
			"bookshelf_id\t" + 								//9
			CatalogueDBAdapter.KEY_BOOKSHELF + "\t" +		//10
			CatalogueDBAdapter.KEY_READ + "\t" +			//11
			CatalogueDBAdapter.KEY_SERIES + "\t" + 			//12
			CatalogueDBAdapter.KEY_SERIES_NUM + "\t" +		//13
			CatalogueDBAdapter.KEY_PAGES + "\t" + 			//14
			CatalogueDBAdapter.KEY_NOTES + "\t" + 			//15
			CatalogueDBAdapter.KEY_LIST_PRICE + "\t" + 		//16
			CatalogueDBAdapter.KEY_ANTHOLOGY+ "\t" + 		//17
			CatalogueDBAdapter.KEY_LOCATION+ "\t" + 		//18
			CatalogueDBAdapter.KEY_READ_START+ "\t" + 		//19
			CatalogueDBAdapter.KEY_READ_END+ "\t" + 		//20
			CatalogueDBAdapter.KEY_FORMAT+ "\t" +	 		//21
			CatalogueDBAdapter.KEY_SIGNED+ "\t" + 			//22
			CatalogueDBAdapter.KEY_LOANED_TO+ "\t" +		//23 
			"anthology_titles\t" +							//24 
			"\n";
		if (books.moveToFirst()) {
			do { 
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
							String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR));
							anthology_titles += anth_title + " * " + anth_author + "|";
						} while (titles.moveToNext()); 
					}
				}
				String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
				
				String row = "";
				row += id + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FAMILY_NAME)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GIVEN_NAMES)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR)) + "\t";
				row += title + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER)) + "\t";
				row += dateString + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow("bookshelf_id")) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE)) + "\t";
				row += anthology + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION)) + "\t";
				row += dateReadStartString + "\t";
				row += dateReadEndString + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED)) + "\t";
				row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO)) + "\t";
				row += anthology_titles + "\t";
				row += "\n";
				export += row;
			}
			while (books.moveToNext()); 
		} 
		
		/* write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(AdministrationFunctions.filePath), AdministrationFunctions.UTF8), AdministrationFunctions.BUFFER_SIZE);
			out.write(export);
			out.close();
			//Toast.makeText(this, R.string.export_complete, Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			//Log.e("Book Catalogue", "Could not write to the SDCard");		
			//Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
		}
		
	}
	
	public void restore() {
		//use restore from administrationfunction 
	}
	
}