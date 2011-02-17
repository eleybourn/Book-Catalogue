package com.eleybourn.bookcatalogue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.os.Message;

/**
 * Class to handle export in a separate thread.
 * 
 * @author Grunthos
 */
public class ExportThread extends TaskWithProgress {
	private static String mFilePath = Environment.getExternalStorageDirectory() + "/" + BookCatalogue.LOCATION;
	private static String mFileName = mFilePath + "/export.csv";
	private static String UTF8 = "utf8";
	private static int BUFFER_SIZE = 8192;
	private CatalogueDBAdapter mDbHelper;

	public interface ExportHandler extends TaskWithProgress.TaskHandler {
		void onFinish();
	}

	public ExportThread(Context ctx, ExportHandler taskHandler) {
		super(ctx, taskHandler);

		mDbHelper = new CatalogueDBAdapter(getContext());
		mDbHelper.open();
		mBooks = mDbHelper.exportBooks();

		setMax(mBooks.getCount());
	}

	public Cursor mBooks = null;

	@Override
	protected void onFinish() {
		ExportHandler h = (ExportHandler)getTaskHandler();
		if (h != null)
			h.onFinish();
	}

	@Override
	protected void onMessage(Message msg) {
		// Nothing to do. we don't sent any
	}

	@Override
	protected void onRun() {
		int num = 0;

		if (!Utils.sdCardWritable()) {
			doToast("Export Failed - Could not write to SDCard");
			return;			
		}
		
		String export = 
			'"' + CatalogueDBAdapter.KEY_ROWID + "\"," + 			//0
			'"' + CatalogueDBAdapter.KEY_AUTHOR_DETAILS + "\"," + 	//2
			'"' + CatalogueDBAdapter.KEY_TITLE + "\"," + 			//4
			'"' + CatalogueDBAdapter.KEY_ISBN + "\"," + 			//5
			'"' + CatalogueDBAdapter.KEY_PUBLISHER + "\"," + 		//6
			'"' + CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\"," + 	//7
			'"' + CatalogueDBAdapter.KEY_RATING + "\"," + 			//8
			'"' + "bookshelf_id\"," + 								//9
			'"' + CatalogueDBAdapter.KEY_BOOKSHELF + "\"," +		//10
			'"' + CatalogueDBAdapter.KEY_READ + "\"," +				//11
			'"' + CatalogueDBAdapter.KEY_SERIES_DETAILS + "\"," +	//12
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

		if (mBooks.moveToFirst()) {
			do { 
				num++;
				long id = mBooks.getLong(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
				String dateString = "";
				try {
					String[] date = mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)).split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])+1;
					int dd = Integer.parseInt(date[2]);
					dateString = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					//do nothing
				}
				String dateReadStartString = "";
				try {
					String[] date = mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START)).split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])+1;
					int dd = Integer.parseInt(date[2]);
					dateReadStartString = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					//do nothing
				}
				String dateReadEndString = "";
				try {
					String[] date = mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END)).split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])+1;
					int dd = Integer.parseInt(date[2]);
					dateReadEndString = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					//do nothing
				}
				String anthology = mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
				String anthology_titles = "";
				if (anthology.equals(CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS + "") || anthology.equals(CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR + "")) {
					Cursor titles = mDbHelper.fetchAnthologyTitlesByBook(id);
					if (titles.moveToFirst()) {
						do { 
							String anth_title = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
							String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_NAME));
							anthology_titles += anth_title + " * " + anth_author + "|";
						} while (titles.moveToNext()); 
					}
				}
				String title = mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
				//Display the selected bookshelves
				Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(id);
				String bookshelves_id_text = "";
				String bookshelves_name_text = "";
				while (bookshelves.moveToNext()) {
					bookshelves_id_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_ROWID)) + BookEditFields.BOOKSHELF_SEPERATOR;
					bookshelves_name_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BookEditFields.BOOKSHELF_SEPERATOR;
				}
				bookshelves.close();

				String authorDetails = Utils.getAuthorUtils().encodeList( mDbHelper.getBookAuthorList(id), '|' );
				String seriesDetails = Utils.getSeriesUtils().encodeList( mDbHelper.getBookSeriesList(id), '|' );

				String row = "";
				row += "\"" + formatCell(id) + "\",";
				row += "\"" + formatCell(authorDetails) + "\",";
				row += "\"" + formatCell(title) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN))) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER))) + "\",";
				row += "\"" + formatCell(dateString) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING))) + "\",";
				row += "\"" + formatCell(bookshelves_id_text) + "\",";
				row += "\"" + formatCell(bookshelves_name_text) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ))) + "\",";
				row += "\"" + formatCell(seriesDetails) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES))) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES))) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE))) + "\",";
				row += "\"" + formatCell(anthology) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION))) + "\",";
				row += "\"" + formatCell(dateReadStartString) + "\",";
				row += "\"" + formatCell(dateReadEndString) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT))) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO))+"") + "\",";
				row += "\"" + formatCell(anthology_titles) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DESCRIPTION))) + "\",";
				row += "\"" + formatCell(mBooks.getString(mBooks.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GENRE))) + "\",";
				row += "\n";
				export += row;
				doProgress(title, num);
			}
			while (mBooks.moveToNext() && !isCancelled()); 
		} 
		
		/* write to the SDCard */
		try {
			backupExport();
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mFileName), UTF8), BUFFER_SIZE);
			out.write(export);
			out.close();
			doToast("Export Complete");
			//Toast.makeText(AdministrationFunctions.this, R.string.export_complete, Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			//Log.e("Book Catalogue", "Could not write to the SDCard");		
			//Toast.makeText(AdministrationFunctions.this, R.string.export_failed, Toast.LENGTH_LONG).show();
			doToast("Export Failed - Could not write to SDCard");
		}
	}
	
	/**
	 * Backup the current file
	 */
	private void backupExport() {
		File export = new File(mFileName);
		File backup = new File(mFileName + ".bak");
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
