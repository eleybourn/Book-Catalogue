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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import android.database.Cursor;
import android.os.ParcelFileDescriptor;

/*
 * A book catalogue application that integrates with Google Books.
 */
/* THIS DOES NOT WORK YET */
public class BookBackupAgent { //TODO extends BackupAgent 
	static final int AGENT_VERSION = 1;
	static final String APP_DATA_KEY = "alldata";
	public int mCount = 0;
	public CatalogueDBAdapter mDbHelper;
	
	/** For convenience, we set up the File object for the app's data on creation */
	//TODO @Override
	public void onCreate() {
		mDbHelper = null; //TODO new CatalogueDBAdapter(this);
	}
	
	/* (non-Javadoc)
	 * @see android.app.backup.BackupAgent#onBackup(android.os.ParcelFileDescriptor, android.app.backup.BackupDataOutput, android.os.ParcelFileDescriptor)
	 */
	/*
	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
		try {
			// Get the oldState input stream
			FileInputStream instream = new FileInputStream(oldState.getFileDescriptor());
			DataInputStream in = new DataInputStream(instream);
			
			int dbCount = mDbHelper.countBooks("All Books");
			try {
				// Get the last modified timestamp from the state file and data file
				int stateCount = in.readInt();
				
				if (stateCount != dbCount) {
					// The database has changed, so do a backup
					// Or the time on the device changed, so be safe and do a backup
				} else {
					// Don't back up because the file hasn't changed
					return;
				}
			} catch (IOException e) {
				// Unable to read state file... be safe and do a backup
			}	
			
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
				CatalogueDBAdapter.KEY_AUDIOBOOK+ "\t" + 		//21
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
					row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUDIOBOOK)) + "\t";
					row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED)) + "\t";
					row += books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO)) + "\t";
					row += anthology_titles + "\t";
					row += "\n";
					export += row;
				}
				while (books.moveToNext()); 
			} 
			
			// write to the SDCard 
			try {
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(AdministrationFunctions.filePath), AdministrationFunctions.UTF8), AdministrationFunctions.BUFFER_SIZE);
				out.write(export);
				out.close();
				//Toast.makeText(this, R.string.export_complete, Toast.LENGTH_LONG).show();
			} catch (IOException e) {
				//Log.e("Book Catalogue", "Could not write to the SDCard");		
				//Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
			}
			
			// Create buffer stream and data output stream for our data
			ByteArrayOutputStream bufStream = new ByteArrayOutputStream();
			DataOutputStream outWriter = new DataOutputStream(bufStream);
			outWriter.writeChars(export);
			// Send the data to the Backup Manager via the BackupDataOutput
			byte[] buffer = bufStream.toByteArray();
			int len = buffer.length;
			data.writeEntityHeader(APP_DATA_KEY, len);
			data.writeEntityData(buffer, len);
			
			//save newstate to oldstate
			FileOutputStream outstream = new FileOutputStream(newState.getFileDescriptor());
			DataOutputStream out = new DataOutputStream(outstream);
			out.writeInt(dbCount);
		} catch (Exception e) {
			//I can't test this, so I don't know if it will work
		}
	}
	*/
	
	/* (non-Javadoc)
	 * @see android.app.backup.BackupAgent#onRestore(android.app.backup.BackupDataInput, int, android.os.ParcelFileDescriptor)
	 */
	//TODO @Override
	/* TODO
	public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
		try {
			data.readNextHeader();
			String key = data.getKey();
			int dataSize = data.getDataSize();
			ArrayList<String> export = new ArrayList<String>();
			if (key == APP_DATA_KEY) {
				byte[] dataBuf = new byte[dataSize];
				data.readEntityData(dataBuf, 0, dataSize);
				ByteArrayInputStream baStream = new ByteArrayInputStream(dataBuf);
				DataInputStream in = new DataInputStream(baStream);
				try {
					String line = in.readLine();
					while (line != null) {
						export.add(line);
						line = in.readLine();
					}
				} catch (IOException e) {
					//end of buffer
				}
			}
			
			//TODO: As before
			// Iterate through each imported row 
			int row = 1;
			while (row < export.size()) {
				String[] imported = export.get(row).split("\t");
				for (int i=0; i<imported.length; i++) {
					imported[i] = imported[i].trim();
					if (imported[i].indexOf("\"") == 0) {
						imported[i] = imported[i].substring(1);
					}
					int length = imported[i].length()-1;
					if (length > -1 && imported[i].lastIndexOf("\"") == length) {
						imported[i] = imported[i].substring(0, length);
					}
					imported[i] = imported[i].trim();
				}
				// This import line is too short
				row++;
				
				// Setup aliases for each cell
				Long id = null;
				try {
					id = Long.parseLong(imported[0]);
				} catch(Exception e) {
					id = Long.parseLong("0");
				}
				
				String family = "";
				try {
					family = imported[1]; 
				} catch (Exception e) {
					// family is a compulsory field
					continue;
				}
				String given = "";
				try {
					given = imported[2]; 
				} catch (Exception e) {
					given = "";
				}
				//String author_id = imported[3];
				
				String title = "";
				try {
					title = imported[4]; 
				} catch (Exception e) {
					//title is a compulsory field
					continue;
				}
				
				String isbn = "";
				try {
					isbn = imported[5];
				} catch (Exception e) {
					isbn = "";
				}
				
				String publisher = "";
				try {
					publisher = imported[6]; 
				} catch (Exception e) {
					publisher = "";
				}
				
				String date_published = "";
				try {
					date_published = imported[7];
					String[] date = date_published.split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])-1;
					int dd = Integer.parseInt(date[2]);
					date_published = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					date_published = "";
				}
				
				float rating = 0;
				try {
					rating = Float.valueOf(imported[8]); 
				} catch (Exception e) {
					rating = 0;
				}
				
				//String bookshelf_id = imported[9]; 
				
				String bookshelf = "";
				try {
					bookshelf = imported[10];
				} catch (Exception e) {
					bookshelf = "";
				}
				
				boolean read = false;
				try {
					read = (imported[11].equals("0")? false:true); 
				} catch (Exception e) {
					read = false;
				}
				
				String series = "";
				try {
					series = imported[12]; 
				} catch (Exception e) {
					series = "";
				}
				
				String series_num = "";
				try {
					series_num = imported[13];
				} catch (Exception e) {
					series_num = "";
				}
				
				int pages = 0;
				try {
					pages = Integer.parseInt(imported[14]); 
				} catch (Exception e) {
					pages = 0;
				}
				
				String notes = "";
				try {
					notes = imported[15];
				} catch (Exception e) {
					notes = "";
				}
				
				String list_price = "";
				try {
					list_price = imported[16];
				} catch (ArrayIndexOutOfBoundsException e) {
					list_price = "";
				}
				
				int anthology = CatalogueDBAdapter.ANTHOLOGY_NO;
				try {
					anthology = Integer.parseInt(imported[17]); 
				} catch (Exception e) {
					anthology = 0;
				}
				
				String location = "";
				try {
					location = imported[18]; 
				} catch (Exception e) {
					location = "";
				}
				
				String read_start = "";
				try {
					read_start = imported[19];
					String[] date = date_published.split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])-1;
					int dd = Integer.parseInt(date[2]);
					read_start = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					read_start = "";
				}
				
				String read_end = "";
				try {
					read_end = imported[20];
					String[] date = date_published.split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])-1;
					int dd = Integer.parseInt(date[2]);
					read_end = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					read_end = "";
				}
				
				boolean audiobook = false;
				try {
					audiobook = (imported[21].equals("0")? false:true); 
				} catch (Exception e) {
					audiobook = false;
				}
				
				boolean signed = false;
				try {
					signed = (imported[22].equals("0")? false:true); 
				} catch (Exception e) {
					signed = false;
				}
				
				String loan = "";
				try {
					loan = imported[23]; 
				} catch (Exception e) {
					loan = ""; 
				}
				
				String anthology_titles = "";
				try {
					anthology_titles = imported[24]; 
				} catch (Exception e) {
					anthology_titles = ""; 
				}
				
				String author = family + ", " + given;
				try {
					if (id == 0) {
						// Book is new. It does not exist in the current database
						Cursor book = mDbHelper.fetchBookByISBNOrCombo(isbn, family, given, title);
						int rows = book.getCount();
						if (rows != 0) {
							// Its a new entry, but the ISBN exists
							id = book.getLong(0);
							book.moveToFirst();
							mDbHelper.updateBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
						} else {
							id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
						}
					} else {
						Cursor book = mDbHelper.fetchBookById(id);
						int rows = book.getCount();
						if (rows == 0) {
							mDbHelper.createBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
						} else {
							// Book exists and should be updated if it has changed
							mDbHelper.updateBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
						}
					}
				} catch (Exception e) {
					// do nothing
				}
				
				if (!loan.equals("")) {
					mDbHelper.createLoan(id, loan);
				}
				
				if (anthology == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS || anthology == CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR) {
					String[] extracted_titles = anthology_titles.split("|");
					for (int j=0; j<extracted_titles.length; j++) {
						String[] author_title = extracted_titles[j].split(" * ");
						if (author_title.length > 2) {
							mDbHelper.createAnthologyTitle(id, author_title[1], author_title[0]);
						}
					}
				}
			}
			
			//write the state file
			int dbCount = mDbHelper.countBooks("All Books");
			FileOutputStream outstream = new FileOutputStream(newState.getFileDescriptor());
			DataOutputStream out = new DataOutputStream(outstream);
			out.writeInt(dbCount);
		} catch (Exception e) {
			// I can't test this so I don't know if it will work
		}
	}
	*/
	
}