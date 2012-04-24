/*
 * @copyright 2011 Philip Warner
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

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashSet;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;
import com.eleybourn.bookcatalogue.SearchThread.SearchTaskHandler;
import com.eleybourn.bookcatalogue.TaskManager.OnTaskEndedListener;

/**
 * Class to co-ordinate multiple SearchThread objects using an existing TaskManager.
 * 
 * It uses the task manager it is passed and listens to OnTaskEndedListener messages;
 * it maintain its own internal list of tasks and as tasks it knows about end, it
 * processes the data. Once all tasks are complete, it sends a message to its
 * creator via its SearchHandler.
 * 
 * @author Philip Warner
 */
public class SearchManager implements OnTaskEndedListener {
	
	/** Flag indicating a search source to use */
	public static final int SEARCH_GOOGLE = 1;
	/** Flag indicating a search source to use */
	public static final int SEARCH_AMAZON = 2;
	/** Flag indicating a search source to use */
	public static final int SEARCH_LIBRARY_THING = 4;
	/** Flag indicating a search source to use */
	public static final int SEARCH_GOODREADS = 8;
	/** Mask including all search sources */
	public static final int SEARCH_ALL = SEARCH_GOOGLE | SEARCH_AMAZON | SEARCH_LIBRARY_THING | SEARCH_GOODREADS;
	
	// ENHANCE: Allow user to change the default search data priority
	public static final int[] mSearchPriority = new int[] {SEARCH_GOOGLE, SEARCH_AMAZON, SEARCH_GOODREADS, SEARCH_LIBRARY_THING};

	/** Flags applicable to *current* search */
	int mSearchFlags;

	// TaskManager for threads; may have other threads tham the ones this object creates.
	TaskManager mTaskManager;
	// Accumulated book data
	private Bundle mBookData = null;
	// Flag indicating searches will be non-concurrent until an ISBN is found
	private boolean mWaitingForIsbn = false;
	// Flag indicating a task was cancelled.
	private boolean mCancelledFlg = false;
	// Flag to indicate search finished
	private boolean mFinished = false;
	// Original author for search
	private String mAuthor;
	// Original title for search
	private String mTitle;
	// Original ISBN for search
	private String mIsbn;
	// Whether of not to fetch thumbnails
	private boolean mFetchThumbnail;

	/** Output from search threads */
	private Hashtable<Integer,Bundle> mSearchResults = new Hashtable<Integer,Bundle>();

	// Handler for search results
	private SearchResultHandler mSearchHandler = null;

	// List of threads created by *this* object.
	private ArrayList<ManagedTask> mRunningTasks = new ArrayList<ManagedTask>();
	
	// A flag to identify if we are searching by name of book
	private boolean mShowResultsInList = false;

	/**
	 * Task handler for thread management; caller MUST implement this to get
	 * search results.
	 * 
	 * @author Philip Warner
	 */
	public interface SearchResultHandler extends ManagedTask.TaskHandler {
		void onSearchFinished(Bundle bookData, boolean cancelled);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param taskManager	TaskManager to use
	 * @param taskHandler	SearchHandler to send results
	 */
	SearchManager(TaskManager taskManager, SearchResultHandler taskHandler) {
		mTaskManager = taskManager;
		mSearchHandler = taskHandler;
	}

	/**
	 * Disconnect from handler so that underlying activity can rebuild.
	 */
	public void disconnect() {
		mSearchHandler = null;
	}

	/**
	 * Reconnect and (possibly)send the completion message.
	 * @param handler
	 */
	public void reconnect(SearchResultHandler handler) {
		mSearchHandler = handler;
		if (mFinished)
			mSearchHandler.onSearchFinished(mBookData, mCancelledFlg);
	}
	
	public TaskHandler getTaskHandler(ManagedTask t) {
		if (t instanceof SearchThread) {
			return mGenericSearchHandler;
		} else {
			return null;
		}
	}

	/**
	 * When a task has ended, see if we are finished (no more tasks running).
	 * If so, finish.
	 */
	@Override
	public void onTaskEnded(TaskManager manager, ManagedTask task) {
		int size;
		synchronized(mRunningTasks) {
			mRunningTasks.remove(task);
			size = mRunningTasks.size();
		}
		if (size == 0) {
			finish();
			mTaskManager.removeOnTaskEndedListener(this);
		}
	}

	/**
	 * Utility routine to start a task
	 * 
	 * @param thread	Task to start
	 */
	private void startOne(SearchThread thread) {
		synchronized(mRunningTasks) {
			mRunningTasks.add(thread);
		}
		thread.start();
	}

	/**
	 * Start an Amazon search
	 */
	private boolean startAmazon() {
		if (!mCancelledFlg) {
			if(mShowResultsInList)
				startOne( new SearchAmazonThread(mTaskManager, mGenericSearchHandler, Utils.appendListFlag(mAuthor), mTitle, mIsbn, mFetchThumbnail) );		
			else
				startOne( new SearchAmazonThread(mTaskManager, mGenericSearchHandler, mAuthor, mTitle, mIsbn, mFetchThumbnail) );
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Start a Google search
	 */
	private boolean startGoogle() {
		if (!mCancelledFlg) {
			if(mShowResultsInList)
				startOne( new SearchGoogleThread(mTaskManager, mGenericSearchHandler, Utils.appendListFlag(mAuthor), mTitle, mIsbn, mFetchThumbnail) );
			else
				startOne( new SearchGoogleThread(mTaskManager, mGenericSearchHandler, mAuthor, mTitle, mIsbn, mFetchThumbnail) );		
			return true;
		} else {
			return false;			
		}
	}
	/**
	 * Start an Amazon search
	 */
	private boolean startLibraryThing(){
		if (!mCancelledFlg && mIsbn != null && mIsbn.trim().length() > 0) {
			startOne( new SearchLibraryThingThread(mTaskManager, mGenericSearchHandler, mAuthor, mTitle, mIsbn, mFetchThumbnail));		
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Start an Amazon search
	 */
	private boolean startGoodreads(){
		if (!mCancelledFlg && mIsbn != null && mIsbn.trim().length() > 0) {
			startOne( new SearchGoodreadsThread(mTaskManager, mGenericSearchHandler, mAuthor, mTitle, mIsbn, mFetchThumbnail));		
			return true;
		} else {
			return false;			
		}
	}

	/**
	 * Start a search
	 * 
	 * @param author	Author to search for
	 * @param title		Title to search for
	 * @param isbn		ISBN to search for
	 */
	public void search(String author, String title, String isbn, boolean fetchThumbnail, int searchFlags) {
		if ( (searchFlags & SEARCH_ALL) == 0)
			throw new RuntimeException("Must specify at least one source to use");

		// Save the flags
		mSearchFlags = searchFlags;
		if (!Utils.USE_LT) {
			mSearchFlags &= ~SEARCH_LIBRARY_THING;
		}

		// Save the input and initialize
		mBookData = new Bundle();
		mSearchResults = new Hashtable<Integer,Bundle>();

		mWaitingForIsbn = false;
		mCancelledFlg = false;
		mFinished = false;

		if(Utils.getListFlag(author)){
			mShowResultsInList = true;
			author = Utils.removeListFlag(author);
		}
		
		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;
		mFetchThumbnail = fetchThumbnail;			
		
		if (mTaskManager.runningInUiThread()) {
			doSearch();
		} else {
			mTaskManager.postToUiThread(new Runnable() {
				@Override
				public void run() {
					doSearch();
				}});
		}
	}
	private void doSearch() {
		// List for task ends
		mTaskManager.addOnTaskEndedListener(this);
		
		// We really want to ensure we get the same book from each, so if isbn is not present, do
		// these in series.

		boolean tasksStarted = false;
		try {
			if (mIsbn != null && mIsbn.length() > 0) {
				mWaitingForIsbn = false;
				tasksStarted = this.startSearches(mSearchFlags);
			} else {
				// Run one at a time, startNext() defined the order.
				mWaitingForIsbn = true;
				tasksStarted = startNext();
			}			
		} finally {
			if (!tasksStarted) {
				finish();
				mTaskManager.removeOnTaskEndedListener(this);
			}
		}

	}

	/**
	 * Utility routine to append text data from one Bundle to another
	 * 
	 * @param key		Key of data
	 * @param source	Source Bundle
	 * @param dest		Destination Bundle
	 */
	private void appendData(String key, Bundle source, Bundle dest) {
		String res = dest.getString(key) + "|" + source.getString(key);
		dest.putString(key, res);
	}

	/**
	 * Copy data from passed Bundle to current accumulated data. Does some careful
	 * processing of the data.
	 * 
	 * @param bookData	Source
	 */
	private void accumulateData(int searchId) {
		// See if we got data from this source
		if (!mSearchResults.containsKey(searchId))
			return;
		Bundle bookData = mSearchResults.get(searchId);

		// See if we REALLY got data from this source
		if (bookData == null)
			return;

		for (String k : bookData.keySet()) {
			// If its not there, copy it.
			if (!mBookData.containsKey(k) || mBookData.getString(k) == null || mBookData.getString(k).trim().length() == 0) 
				mBookData.putString(k, bookData.get(k).toString());
			else {
				// Copy, append or update data as appropriate.
				if (k.equals(CatalogueDBAdapter.KEY_AUTHOR_DETAILS)) {
					appendData(k, bookData, mBookData);
				} else if (k.equals(CatalogueDBAdapter.KEY_SERIES_DETAILS)) {
					appendData(k, bookData, mBookData);					
				} else if (k.equals(CatalogueDBAdapter.KEY_DATE_PUBLISHED)) {
					// Grab a different date if we can parse it.
					Date newDate = Utils.parseDate(bookData.getString(k));
					if (newDate != null) {
						String curr = mBookData.getString(k);
						if (Utils.parseDate(curr) == null) {
							mBookData.putString(k, Utils.toSqlDateOnly(newDate));
						}
					}
				} else if (k.equals("__thumbnail")) {
					appendData(k, bookData, mBookData);					
				}
			}
		}
	}	
	
	/**
	 * Copy data from passed Bundle to current accumulated data.
	 * 
	 * @param searchId	Identifier of source
	 */	
	private void accumulateLists(int searchId) {		
		// See if we got data from this source
		if (!mSearchResults.containsKey(searchId))
			return;
		Bundle bookData = mSearchResults.get(searchId);

		// See if we REALLY got data from this source
		if (bookData == null)
			return;		
		
		// Tries to get lists from Bundles
		ArrayList<Book> l1 = mBookData.containsKey(CatalogueDBAdapter.KEY_BOOKLIST) ? (ArrayList<Book>) mBookData.getSerializable(CatalogueDBAdapter.KEY_BOOKLIST) : null;
		ArrayList<Book> l2 = bookData.containsKey(CatalogueDBAdapter.KEY_BOOKLIST) ? (ArrayList<Book>) bookData.getSerializable(CatalogueDBAdapter.KEY_BOOKLIST) : null;
		
		// Put combined lists back to Bundle
		mBookData.putSerializable(CatalogueDBAdapter.KEY_BOOKLIST, combineArrayLists(l1, l2));
		
	}
	
	/**
	 * Combine two lists of books.
	 * 
	 * @param l1	First list
	 * @param l2	Second list
	 */		
	private ArrayList<Book> combineArrayLists(ArrayList<Book> l1, ArrayList<Book> l2){
		
		// If one of given lists is empty, return the second one
		if(l1 == null || l1.size() == 0){
			return l2;
		}
		if(l2 == null || l2.size() == 0){
			return l1;
		}
		
		// Lists are combined through a map, so the result has regular order and duplicates are removed.
		LinkedHashSet<Book> tmp = new LinkedHashSet<Book>();		
		int size = Math.max(l1.size(), l2.size());
		for(int i = 0; i < size; i++){			
			if(i < l1.size()){
				if(!tmp.add(l1.get(i)))
					Utils.deleteThumbnail(l1.get(i).getTHUMBNAIL());
			}
			if(i < l2.size()){							
				if(!tmp.add(l2.get(i)))
					Utils.deleteThumbnail(l2.get(i).getTHUMBNAIL());
			}						
		}		
		return new ArrayList<Book>(tmp);
	}
	
	/**
	 * Proper list of books form Bundle.
	 */	
	private void properBookList(){		
		ArrayList<Book> list = (ArrayList<Book>) mBookData.getSerializable(CatalogueDBAdapter.KEY_BOOKLIST);		
		for(Book book : list){
			try {
				// Decode the collected author names and convert to an ArrayList
				book.setAUTHOR_ARRAY(Utils.getAuthorUtils().decodeList(book.getAUTHOR(), '|', false));
				// Decode the collected series names and convert to an ArrayList
				try {
					book.setSERIES_ARRAY(Utils.getSeriesUtils().decodeList(book.getSERIES_NAME(), '|', false));
				} catch (Exception e) {
					Logger.logError(e);
				}			
				book.setTITLE(Utils.properCase(book.getTITLE()));
				book.setPUBLISHER(Utils.properCase(book.getPUBLISHER()));
				book.setDATE_PUBLISHED(Utils.properCase(book.getDATE_PUBLISHED()));
				book.setSERIES_NAME(Utils.properCase(book.getSERIES_NAME()));	
			} catch (Exception e) {}
		}				
	}	

	/**
	 * Combine all the data and create a book or display an error.
	 */
	private void finish() {
		if(!mShowResultsInList){
			// Merge the data we have. We do this in a fixed order rather than as the threads finish.
			for(int i: mSearchPriority)
				accumulateData(i);
			
			// If there are thumbnails present, pick the biggest, delete others and rename.
			Utils.cleanupThumbnails(mBookData);
			
			// If book is not found, just return to dialog.
			String authors = null;
			String title = null;
			try {
				authors = mBookData.getString(CatalogueDBAdapter.KEY_AUTHOR_DETAILS);
			} catch (Exception e) {}
			try {
				title = mBookData.getString(CatalogueDBAdapter.KEY_TITLE);
			} catch (Exception e) {}
			if (authors == null || authors.length() == 0 || title == null || title.length() == 0) {				
				bookNotFound();
			} else {
				Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_TITLE);
				Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_PUBLISHER);
				Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_DATE_PUBLISHED);
				Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_SERIES_NAME);
				
				// Decode the collected author names and convert to an ArrayList
				ArrayList<Author> aa = Utils.getAuthorUtils().decodeList(authors, '|', false);
				mBookData.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, aa);
				
				// Decode the collected series names and convert to an ArrayList
				try {
					String series = mBookData.getString(CatalogueDBAdapter.KEY_SERIES_DETAILS);
					ArrayList<Series> sa = Utils.getSeriesUtils().decodeList(series, '|', false);
					mBookData.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, sa);
				} catch (Exception e) {
					Logger.logError(e);
				}
			}
		}else{
			for(int i: mSearchPriority)
				accumulateLists(i);									
			
			ArrayList<Book> books = mBookData.containsKey(CatalogueDBAdapter.KEY_BOOKLIST) ? (ArrayList<Book>) mBookData.getSerializable(CatalogueDBAdapter.KEY_BOOKLIST) : null;
			
			if(books == null || books.size() == 0){
				mBookData.remove(CatalogueDBAdapter.KEY_BOOKLIST);
				bookNotFound();
			}else{			
				properBookList();
			}			
		}
		if (mSearchHandler != null)
			mSearchHandler.onSearchFinished(mBookData, mCancelledFlg);		
		mFinished = true;
	}
		
	/**
	 * Called when book was not found.
	 */	
	private void bookNotFound(){
		mTaskManager.doToast(mTaskManager.getString(R.string.book_not_found));
		mBookData.putString(CatalogueDBAdapter.KEY_ISBN, mIsbn);
		mBookData.putString(CatalogueDBAdapter.KEY_TITLE, mTitle);
		ArrayList<Author> aa = Utils.getAuthorUtils().decodeList(mAuthor, '|', false);
		mBookData.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, aa);
		//add series to stop crashing
		ArrayList<Series> sa = Utils.getSeriesUtils().decodeList("", '|', false);
		mBookData.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, sa);
		if (mSearchHandler != null) {
			mSearchHandler.onSearchFinished(mBookData, mCancelledFlg);
		}		
	}	

	/**
	 * When running in single-stream mode, start the next thread that has no data.
	 * Google is reputedly most likely to succeed. Amazon is fastest, and LT REQUIRES an ISBN.
	 */
	private boolean startNext() {
		// Loop though in 'search-priority' order
		for (int source: mSearchPriority) {
			// If this search includes the source, check it
			if ( (mSearchFlags & source) != 0) {
				// If the source has not been search, search it
				if (!mSearchResults.containsKey(source)) {
					return startOneSearch(source);
				}
			}
		}
		return false;
	}

	/**
	 * Start all searches listed in passed parameter that have not been run yet.
	 * 
	 * @param sources
	 */
	private boolean startSearches(int sources) {
		// Scan searches in priority order
		boolean started = false;
		for(int source: mSearchPriority) {
			// If requested search contains this source...
			if ((sources & source) != 0)
				// If we have not run this search...
				if (!mSearchResults.containsKey(source)) {
					// Run it now
					if (startOneSearch(source))
						started = true;
				}
		}
		return started;
	}
	
	/**
	 * Start specific search listed in passed parameter.
	 * 
	 * @param sources
	 */
	private boolean startOneSearch(int source) {
		switch(source) {
		case SEARCH_GOOGLE:
			return startGoogle();
		case SEARCH_AMAZON:
			return startAmazon();
		case SEARCH_LIBRARY_THING:
			return startLibraryThing();
		case SEARCH_GOODREADS:
			return startGoodreads();
		default:
			throw new RuntimeException("Unexpected search source: " + source);				
		}
	}

	/**
	 * Handle SearchThread completion; just save the results and if waiting for ISBN, start 
	 * next/rest depending on whether we now have an ISBN.
	 */
	private SearchTaskHandler mGenericSearchHandler = new SearchTaskHandler() {
		@Override
		public void onSearchThreadFinish(SearchThread t, Bundle bookData, boolean cancelled) {
			mCancelledFlg = cancelled;
			mSearchResults.put(t.getSearchId(), bookData);
			if (cancelled) {
				mWaitingForIsbn = false;
			} else {
				if (mWaitingForIsbn) {
					if (Utils.isNonBlankString(bookData, CatalogueDBAdapter.KEY_ISBN)) {
						mWaitingForIsbn = false;
						// Start the other two...even if they have run before
						mIsbn = bookData.getString(CatalogueDBAdapter.KEY_ISBN);
						startSearches(mSearchFlags);
					} else {
						// Start next one that has not run. 
						startNext();
					}
				}				
			}
		}
	};

}
