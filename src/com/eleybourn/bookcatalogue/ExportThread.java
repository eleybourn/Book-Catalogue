package com.eleybourn.bookcatalogue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.backup.CsvExporter;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.Exporter.ExportListener;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to handle export in a separate thread.
 * 
 * @author Philip Warner
 */
public class ExportThread extends ManagedTask {
	// Changed the paths to non-static variable because if this code is called 
	// while a phone sync is in progress, they will not be set correctly
	private String mFilePath = StorageUtils.getSharedStorage().getAbsolutePath();
	private String mExportFileName = mFilePath + "/export.csv";
	private String mTempFileName = mFilePath + "/export.tmp";
	private static String UTF8 = "utf8";
	private static int BUFFER_SIZE = 8192;
	private CatalogueDBAdapter mDbHelper;

	public ExportThread(TaskManager ctx) {
		super(ctx);
		mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
		mDbHelper.open();
	}

	@Override
	protected void onThreadFinish() {
		cleanup();
	}
	
	private Exporter.ExportListener mOnExportListener = new Exporter.ExportListener() {

		@Override
		public void onProgress(String message, int position) {
			if (position > 0) {
				mManager.doProgress(ExportThread.this, message, position);
			} else {
				mManager.doProgress(message);
			}
		}

		@Override
		public boolean isCancelled() {
			return ExportThread.this.isCancelled();
		}

		@Override
		public void setMax(int max) {
			mManager.setMax(ExportThread.this, max);
		}
	};

	@Override
	protected void onRun() {
		if (!StorageUtils.sdCardWritable()) {
			mManager.doToast("Export Failed - Could not write to SDCard");
			return;			
		}
		try {
			FileOutputStream out = new FileOutputStream(mTempFileName);
			CsvExporter exporter = new CsvExporter();
			exporter.export(out, mOnExportListener, Exporter.EXPORT_ALL);
			if (out != null && out.getChannel().isOpen()) {
				out.close();
			}
			renameFiles();
		} catch (IOException e) {
			Logger.logError(e);
			mManager.doToast(getString(R.string.export_failed_sdcard));
		}

//		mManager.doProgress(getString(R.string.export_starting_ellipsis));
//		boolean displayingStartupMessage = true;
//
//		StringBuilder export = new StringBuilder(
//			'"' + CatalogueDBAdapter.KEY_ROWID + "\"," + 			//0
//			'"' + CatalogueDBAdapter.KEY_AUTHOR_DETAILS + "\"," + 	//2
//			'"' + CatalogueDBAdapter.KEY_TITLE + "\"," + 			//4
//			'"' + CatalogueDBAdapter.KEY_ISBN + "\"," + 			//5
//			'"' + CatalogueDBAdapter.KEY_PUBLISHER + "\"," + 		//6
//			'"' + CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\"," + 	//7
//			'"' + CatalogueDBAdapter.KEY_RATING + "\"," + 			//8
//			'"' + "bookshelf_id\"," + 								//9
//			'"' + CatalogueDBAdapter.KEY_BOOKSHELF + "\"," +		//10
//			'"' + CatalogueDBAdapter.KEY_READ + "\"," +				//11
//			'"' + CatalogueDBAdapter.KEY_SERIES_DETAILS + "\"," +	//12
//			'"' + CatalogueDBAdapter.KEY_PAGES + "\"," + 			//14
//			'"' + CatalogueDBAdapter.KEY_NOTES + "\"," + 			//15
//			'"' + CatalogueDBAdapter.KEY_LIST_PRICE + "\"," + 		//16
//			'"' + CatalogueDBAdapter.KEY_ANTHOLOGY_MASK+ "\"," + 		//17
//			'"' + CatalogueDBAdapter.KEY_LOCATION+ "\"," + 			//18
//			'"' + CatalogueDBAdapter.KEY_READ_START+ "\"," + 		//19
//			'"' + CatalogueDBAdapter.KEY_READ_END+ "\"," + 			//20
//			'"' + CatalogueDBAdapter.KEY_FORMAT+ "\"," + 			//21
//			'"' + CatalogueDBAdapter.KEY_SIGNED+ "\"," + 			//22
//			'"' + CatalogueDBAdapter.KEY_LOANED_TO+ "\"," +			//23 
//			'"' + "anthology_titles" + "\"," +						//24 
//			'"' + CatalogueDBAdapter.KEY_DESCRIPTION+ "\"," + 		//25
//			'"' + CatalogueDBAdapter.KEY_GENRE+ "\"," + 			//26
//			'"' + CatalogueDBAdapter.KEY_DATE_ADDED+ "\"," + 		//27
//			'"' + DatabaseDefinitions.DOM_GOODREADS_BOOK_ID + "\"," + 		//28
//			'"' + DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE + "\"," + 		//29
//			'"' + DatabaseDefinitions.DOM_LAST_UPDATE_DATE + "\"," + 		//30
//			'"' + DatabaseDefinitions.DOM_BOOK_UUID + "\"," + 		//31
//			"\n");
//		
//		long lastUpdate = 0;
//		
//		StringBuilder row = new StringBuilder();
//		
//		BooksCursor books = mDbHelper.exportBooks();
//		BooksRowView rv = books.getRowView();
//
//		try {
//			final int totalBooks = books.getCount();
//
//			if (!isCancelled()) {
//	
//				mManager.setMax(this, totalBooks);
//
//				/* write to the SDCard */
//				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mTempFileName), UTF8), BUFFER_SIZE);
//				out.write(export.toString());
//				if (books.moveToFirst()) {
//					do { 
//						num++;
//						long id = books.getLong(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateString = "";
//						try {
//							dateString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED));
//						} catch (Exception e) {
//							//do nothing
//						}
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateReadStartString = "";
//						try {
//							dateReadStartString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START));
//						} catch (Exception e) {
//							Logger.logError(e);
//							//do nothing
//						}
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateReadEndString = "";
//						try {
//							dateReadEndString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END));
//						} catch (Exception e) {
//							Logger.logError(e);
//							//do nothing
//						}
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateAddedString = "";
//						try {
//							dateAddedString = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_ADDED));
//						} catch (Exception e) {
//							//do nothing
//						}
//
//						String anthology = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK));
//						String anthology_titles = "";
//						if (anthology.equals(CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS + "") || anthology.equals(CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY + "")) {
//							Cursor titles = mDbHelper.fetchAnthologyTitlesByBook(id);
//							try {
//								if (titles.moveToFirst()) {
//									do { 
//										String anth_title = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
//										String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_NAME));
//										anthology_titles += anth_title + " * " + anth_author + "|";
//									} while (titles.moveToNext()); 
//								}
//							} finally {
//								if (titles != null)
//									titles.close();
//							}
//						}
//						String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
//						//Display the selected bookshelves
//						Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(id);
//						String bookshelves_id_text = "";
//						String bookshelves_name_text = "";
//						while (bookshelves.moveToNext()) {
//							bookshelves_id_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_ROWID)) + BookEditFields.BOOKSHELF_SEPERATOR;
//							bookshelves_name_text += Utils.encodeListItem(bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)),BookEditFields.BOOKSHELF_SEPERATOR) + BookEditFields.BOOKSHELF_SEPERATOR;
//						}
//						bookshelves.close();
//
//						String authorDetails = Utils.getAuthorUtils().encodeList( mDbHelper.getBookAuthorList(id), '|' );
//						String seriesDetails = Utils.getSeriesUtils().encodeList( mDbHelper.getBookSeriesList(id), '|' );
//
//						row.setLength(0);
//						row.append("\"" + formatCell(id) + "\",");
//						row.append("\"" + formatCell(authorDetails) + "\",");
//						row.append( "\"" + formatCell(title) + "\"," );
//						row.append("\"" + formatCell(rv.getIsbn()) + "\",");
//						row.append("\"" + formatCell(rv.getPublisher()) + "\",");
//						row.append("\"" + formatCell(dateString) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING))) + "\",");
//						row.append("\"" + formatCell(bookshelves_id_text) + "\",");
//						row.append("\"" + formatCell(bookshelves_name_text) + "\",");
//						row.append("\"" + formatCell(rv.getRead()) + "\",");
//						row.append("\"" + formatCell(seriesDetails) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES))) + "\",");
//						row.append("\"" + formatCell(rv.getNotes()) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE))) + "\",");
//						row.append("\"" + formatCell(anthology) + "\",");
//						row.append("\"" + formatCell(rv.getLocation()) + "\",");
//						row.append("\"" + formatCell(dateReadStartString) + "\",");
//						row.append("\"" + formatCell(dateReadEndString) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT))) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO))+"") + "\",");
//						row.append("\"" + formatCell(anthology_titles) + "\",");
//						row.append("\"" + formatCell(rv.getDescription()) + "\",");
//						row.append("\"" + formatCell(rv.getGenre()) + "\",");
//						row.append("\"" + formatCell(dateAddedString) + "\",");
//						row.append("\"" + formatCell(rv.getGoodreadsBookId()) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE.name))) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name))) + "\",");
//						row.append("\"" + formatCell(rv.getBookUuid()) + "\",");
//						row.append("\n");
//						out.write(row.toString());
//						//export.append(row);
//						
//						long now = System.currentTimeMillis();
//						if ( (now - lastUpdate) > 200) {
//							if (displayingStartupMessage) {
//								mManager.doProgress("");
//								displayingStartupMessage = false;
//							}
//							doProgress(title, num);
//							lastUpdate = now;
//						}
//					}
//					while (books.moveToNext() && !isCancelled()); 
//				} 
//				
//				out.close();
//				//Toast.makeText(AdministrationFunctions.this, R.string.export_complete, Toast.LENGTH_LONG).show();
//				renameFiles();
//			}
//			
//		} catch (IOException e) {
//			Logger.logError(e);
//			mManager.doToast(getString(R.string.export_failed_sdcard));
//		} finally {
//			if (displayingStartupMessage) {
//				mManager.doProgress("");
//				displayingStartupMessage = false;
//			}
//			if (!isCancelled()) {
//				mManager.doToast( getString(R.string.export_complete) );
//			} else {
//				mManager.doToast( getString(R.string.cancelled) );				
//			}
//			if (books != null)
//				books.close();
//		}
	}
	
	/**
	 * Backup the current file
	 */
	private void renameFiles() {
		File temp = new File(mTempFileName);
		File export = new File(mExportFileName);
		if (isCancelled()) {
			if (temp.exists())
				temp.delete();
		} else {
			String fmt = mFilePath + "/export.%s.csv";
			File fLast = new File(String.format(fmt, 5));
			if (fLast.exists())
				fLast.delete();
			for(int i = 4; i > 0; i--) {
				File fCurr = new File(String.format(fmt, i));
				if (fCurr.exists())
					fCurr.renameTo(fLast);
				fLast = fCurr;
			}
			if (export.exists())
				export.renameTo(fLast);
			if (temp.exists())
				temp.renameTo(export);
		}
	}
	
	/**
	 * Double quote all "'s and remove all newlines
	 * 
	 * @param cell The cell the format
	 * @return The formatted cell
	 */
	private String formatCell(String cell) {
		try {
			if (cell.equals("null") || cell.trim().length() == 0) {
				return "";
			}
			StringBuilder bld = new StringBuilder();
			int endPos = cell.length() - 1;
			int pos = 0;
			while (pos <= endPos) {
				char c = cell.charAt(pos);
				switch(c) {
				case '\r':
					bld.append("\\r");
					break;
				case '\n':
					bld.append("\\n");
					break;
				case '\t':
					bld.append("\\t");
					break;
				case '"':
					bld.append("\"\"");
					break;
				case '\\':
					bld.append("\\\\");
					break;
				default:
					bld.append(c);
				}
				pos++;

			}
			return bld.toString();
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
