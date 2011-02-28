package com.eleybourn.bookcatalogue;


public class SearchLibraryThingThread extends SearchThread {

	public SearchLibraryThingThread(TaskManager manager,
			TaskHandler taskHandler, String author, String title, String isbn) {
		super(manager, taskHandler, author, title, isbn);
	}

	@Override
	protected void onRun() {
		//
		//	LibraryThing
		//
		//	We always contact LibraryThing because it is a good source of Series data and thumbnails. But it 
		//	does require an ISBN AND a developer key.
		//
		if (mBookData.containsKey(CatalogueDBAdapter.KEY_ISBN)) {
			String isbn = mBookData.getString(CatalogueDBAdapter.KEY_ISBN);
			if (isbn.length() > 0) {
				this.doProgress(getString(R.string.searching_library_thing), 0);
				LibraryThingManager ltm = new LibraryThingManager(mBookData);
				try {
					ltm.searchByIsbn(isbn);
					// Look for series name and clear KEY_TITLE
					checkForSeriesName();
				} catch (Exception e) {
					Logger.logError(e);
					showException(R.string.searching_library_thing, e);
				}
			}
		}
	}

}
