package com.eleybourn.bookcatalogue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedList;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;

/**
 * Class to update all thumbnails (and some other data) in a background thread.
 *
 * @author Grunthos
 */
public class UpdateThumbnailsThread extends ManagedTask {
	private boolean mOverwrite = false;
	private Cursor mBooks = null;
	private LinkedList<BookInfo> mBookQueue = new LinkedList<BookInfo>();

	public static String filePath = Utils.EXTERNAL_FILE_PATH;
	public static String fileName = filePath + "/export.csv";
	public static String UTF8 = "utf8";
	public static int BUFFER_SIZE = 8192;
	private String mFinalMessage;

	// DB connection
	protected CatalogueDBAdapter mDbHelper;

	public class BookInfo {
		long			id;
		Bundle			bookData = null;
		BookInfo(long id, Bundle values) {
			this.id = id;
			bookData = values;
		}
	}

	public interface LookupHandler extends ManagedTask.TaskHandler {
		void onFinish(LinkedList<BookInfo> queue);
		void onProgress(LinkedList<BookInfo> queue);
	}

	/**
	 * Constructor.
	 * 
	 * @param ctx				Context to use for constructing progressdialog
	 * @param overwrite			Whether to overwrite details
	 * @param books				Cursor to scan
	 * @param lookupHandler		Interface object to handle events in this thread.
	 * 
	 */
	public UpdateThumbnailsThread(TaskManager manager, boolean overwrite, Cursor books, LookupHandler lookupHandler) {
		super(manager, lookupHandler);
		mOverwrite = overwrite;
		mBooks = books;
		manager.setMax(this, books.getCount());
	}

	private void sendBook() {
		/* Send message to the handler */
		Message msg = obtainMessage();
		Bundle b = new Bundle();
		b.putBoolean("sendBook", true);
		msg.setData(b);
		sendMessage(msg);
		return;
	}

	@Override
	public void onRun() {
		// We need this because we use bookISBNSearch which seems to want to create handlers...
		//Looper.prepare();

		mDbHelper = new CatalogueDBAdapter(mManager.getContext());
		mDbHelper.open();
		int counter = 0;

		/* Test write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath + "/.nomedia"), UTF8), BUFFER_SIZE);
			out.write("");
			out.close();
		} catch (IOException e) {
			Logger.logError(e);
			mFinalMessage = getString(R.string.thumbnail_failed_sdcard);
			return;
		}
		
		try {
			while (mBooks.moveToNext() && !isCancelled()) {

				// Copy the fields from the cursor
				Bundle origData = new Bundle();
				for(int i = 0; i < mBooks.getColumnCount(); i++) {
					origData.putString(mBooks.getColumnName(i), mBooks.getString(i));
				}

				counter++;
				// delete any tmp thumbnails //
				try {
					File delthumb = CatalogueDBAdapter.fetchThumbnail(0);
					delthumb.delete();
				} catch (Exception e) {
					// do nothing - this is the expected behaviour 
				}

				Long id = Utils.getAsLong(origData, CatalogueDBAdapter.KEY_ROWID);
				String isbn = origData.getString(CatalogueDBAdapter.KEY_ISBN);
				String author = origData.getString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
				String title = origData.getString(CatalogueDBAdapter.KEY_TITLE);
				String genre = origData.getString(CatalogueDBAdapter.KEY_GENRE);
				String description = origData.getString(CatalogueDBAdapter.KEY_DESCRIPTION);

				Bundle newData = new Bundle();

				File thumb = CatalogueDBAdapter.fetchThumbnail(id);

				if (isbn.equals("") && author.equals("") && title.equals("")) {
					// Must have an ISBN to be able to search
					mManager.doProgress(this, String.format(getString(R.string.skip_title), title), counter);
					//TODO: searchGoogle(AUTHOR)
				} else if (true) { // if (mOverwrite == true || !thumb.exists() || genre.equals("") || description.equals("")) {

					mManager.doProgress(this, title, counter);

					//String[] book = {0=author, 1=title, 2=isbn, 3=publisher, 4=date_published, 5=rating,  6=bookshelf, 
					//	7=read, 8=series, 9=pages, 10=series_num, 11=list_price, 12=anthology, 13=location, 14=read_start, 
					//	15=read_end, 16=audiobook, 17=signed, 18=description, 19=genre};

					if (!isCancelled()) {
						try {
							GoogleBooksManager.searchGoogle(isbn, author, title, newData);							
						} catch (Exception e) {
							Logger.logError(e);
						}
					}

					if (!isCancelled()) {
						try {
							AmazonManager.searchAmazon(isbn, author, title, newData);
						} catch (Exception e) {
							Logger.logError(e);
						}
					}

					if (!isCancelled()) {
						// LibraryThing
						try {
							if (newData.containsKey(CatalogueDBAdapter.KEY_ISBN)) {
								String bdIsbn = newData.getString(CatalogueDBAdapter.KEY_ISBN);
								if (bdIsbn.length() > 0) {
									LibraryThingManager ltm = new LibraryThingManager(newData);
									ltm.searchByIsbn(bdIsbn);
								}
							}
						} catch (Exception e) {
							Logger.logError(e);
						}
					}
					
					Utils.cleanupThumbnails(newData);

					if (!isCancelled()) {
						File tmpthumb = CatalogueDBAdapter.fetchThumbnail(0);
						/* Copy tmpthumb over realthumb */
						if (mOverwrite == true || !thumb.exists()) {
							try {
								tmpthumb.renameTo(thumb);
							} catch (Exception e) {
								//do nothing
							}
						}

						if (description.equals("") && newData.containsKey(CatalogueDBAdapter.KEY_DESCRIPTION)) {
							origData.putString(CatalogueDBAdapter.KEY_DESCRIPTION, newData.getString(CatalogueDBAdapter.KEY_DESCRIPTION));
						}
						if (genre.equals("") && newData.containsKey(CatalogueDBAdapter.KEY_GENRE)) {
							origData.putString(CatalogueDBAdapter.KEY_GENRE, newData.getString(CatalogueDBAdapter.KEY_GENRE));
						}

						if (newData.containsKey(CatalogueDBAdapter.KEY_AUTHOR_DETAILS)) {
							ArrayList<Author> authors = mDbHelper.getBookAuthorList(id);
							ArrayList<Author> newAuthors = Utils.getAuthorUtils().decodeList(newData.getString(CatalogueDBAdapter.KEY_AUTHOR_DETAILS), '|', false);
							authors.addAll(newAuthors);
							origData.putParcelableArrayList(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, authors);
						}
						if (newData.containsKey(CatalogueDBAdapter.KEY_SERIES_DETAILS)) {
							ArrayList<Series> series = mDbHelper.getBookSeriesList(id);
							ArrayList<Series> newSeries = Utils.getSeriesUtils().decodeList(newData.getString(CatalogueDBAdapter.KEY_SERIES_DETAILS), '|', false);
							series.addAll(newSeries);
							origData.putParcelableArrayList(CatalogueDBAdapter.KEY_SERIES_ARRAY, series);
						}
						
						// Queue the book we found
						synchronized(mBookQueue) {
							mBookQueue.add(new BookInfo(id, origData));
						}						
					}

					sendBook();

//				} else {
//					doProgress("Skip - " + title, mProgressCount);
				}
			}
		} catch (Exception e) {
			Logger.logError(e);
		} finally {
			if (mBooks != null && !mBooks.isClosed())
				mBooks.close();
		}
		mFinalMessage = String.format(getString(R.string.num_books_searched), "" + counter);
		if (isCancelled()) 
			mFinalMessage = String.format(getString(R.string.cancelled_info), mFinalMessage);
	}

	@Override
	protected boolean onFinish() {
		mManager.doToast(mFinalMessage);
		if (getTaskHandler() != null) {
			((LookupHandler)getTaskHandler()).onFinish(mBookQueue);	
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onMessage(Message msg) {
		if (msg.getData().containsKey("sendBook")) {
			if (getTaskHandler() != null && mBookQueue.size() > 0) {
				LinkedList<BookInfo> queue;
				synchronized(mBookQueue) {
					queue = mBookQueue;
					mBookQueue = new LinkedList<BookInfo>();
				}
				((LookupHandler)getTaskHandler()).onProgress(queue);
			}		
		}
	}
}
