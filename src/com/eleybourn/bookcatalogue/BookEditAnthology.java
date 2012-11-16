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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter.AnthologyTitleExistsException;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

public class BookEditAnthology extends ListActivity {
	
	private EditText mTitleText;
	private AutoCompleteTextView mAuthorText;
	private String bookAuthor;
	private String bookTitle;
	private Button mAdd;
	private CheckBox mSame;
	private Long mRowId;
	private Long mEditId = null;
	private int currentPosition = 0;
	private int maxPosition = 0;
	private CatalogueDBAdapter mDbHelper;
	int anthology_num = CatalogueDBAdapter.ANTHOLOGY_NO;
	
	private static final int DELETE_ID = Menu.FIRST;
	private static final int POPULATE = Menu.FIRST + 1;
	
	/* Side-step a bug in HONEYCOMB. It seems that startManagingCursor() in honeycomb causes
	 * child-list cursors for ExpanadableList objects to be closed prematurely. So we seem to have
	 * to roll our own...see http://osdir.com/ml/Android-Developers/2011-03/msg02605.html.
	 */
	private ArrayList<Cursor> mManagedCursors = new ArrayList<Cursor>();
	@Override    
	public void startManagingCursor(Cursor c)
	{     
		synchronized(mManagedCursors) {
			if (!mManagedCursors.contains(c))
				mManagedCursors.add(c);     
		}    
	}

	@Override    
	public void stopManagingCursor(Cursor c)
	{
		synchronized(mManagedCursors) {
			try {
				mManagedCursors.remove(c);				
			} catch (Exception e) {
				// Don;t really care if it's called more than once.
			}
		}
	}

	private void destroyManagedCursors() 
	{
		synchronized(mManagedCursors) {
			for (Cursor c : mManagedCursors) {
				try {
					c.close();
				} catch (Exception e) {
					// Don;t really care if it's called more than once or fails.
				}
			}     
			mManagedCursors.clear();
		}
	}

	protected void getRowId() {
		/* Get any information from the extras bundle */
		Bundle extras = getIntent().getExtras();
		mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
	}
	
	/**
	 * Display the edit fields page
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();
		
		mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
		if (mRowId == null) {
			getRowId();
		}
		loadPage();
	}
	
	/**
	 * Display the main manage anthology page. This has three parts. 
	 * 1. Setup the "Same Author" checkbox
	 * 2. Setup the "Add Title" fields
	 * 3. Populate the "Title List" - @see fillAnthology();
	 */
	public void loadPage() {
		setContentView(R.layout.list_anthology);
		
		Cursor book = mDbHelper.fetchBookById(mRowId);
		try {
			if (book != null) {
				book.moveToFirst();
			}

			bookAuthor = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED));
			bookTitle = book.getString(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
			// Setup the same author field
			anthology_num = book.getInt(book.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
			
		} finally {
			if (book != null)
				book.close();			
		}

		mSame = (CheckBox) findViewById(R.id.same_author);
		if (anthology_num == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS) {
			mSame.setChecked(false);
		} else {
			mSame.setChecked(true);
		}
		
		mSame.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				saveState();
				loadPage();
			}
		});
		
		ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mDbHelper.getAllAuthors());
		mAuthorText = (AutoCompleteTextView) findViewById(R.id.add_author);
		mAuthorText.setAdapter(author_adapter);
		if (mSame.isChecked()) {
			mAuthorText.setVisibility(View.GONE);
		}
		mTitleText = (EditText) findViewById(R.id.add_title);
		
		mAdd = (Button) findViewById(R.id.row_add);
		mAdd.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				try {
					String title = mTitleText.getText().toString();
					String author = mAuthorText.getText().toString(); 
					if (mEditId == null) {
						if (mSame.isChecked()) {
							author = bookAuthor; 
						}
						mDbHelper.createAnthologyTitle(mRowId, author, title, false);
					} else {
						mDbHelper.updateAnthologyTitle(mEditId, mRowId, author, title);
						mEditId = null;
						mAdd.setText(R.string.anthology_add);
					}
					mTitleText.setText("");
					mAuthorText.setText("");
					fillAnthology(currentPosition);
					currentPosition = maxPosition;
				} catch(AnthologyTitleExistsException e) {
					Toast.makeText(BookEditAnthology.this, R.string.the_title_already_exists, Toast.LENGTH_LONG).show();
				}
			}
		});
		
		fillAnthology();
		
		// Setup the background
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);
	}
	
	public void fillAnthology(int scroll_to_id) {
		fillAnthology();
		gotoTitle(scroll_to_id);
	}
	
	/**
	 * Populate the bookEditAnthology view
	 */
	public void fillAnthology() {
		int layout = R.layout.row_anthology;
		
		// Get all of the rows from the database and create the item list
		Cursor BooksCursor = mDbHelper.fetchAnthologyTitlesByBook(mRowId);
		maxPosition = BooksCursor.getCount();
		currentPosition = maxPosition;
		startManagingCursor(BooksCursor);
		String[] from = null;
		int[] to = null;
		// Create an array to specify the fields we want to display in the list
		from = new String[]{CatalogueDBAdapter.KEY_ROWID, CatalogueDBAdapter.KEY_POSITION, CatalogueDBAdapter.KEY_AUTHOR_NAME, CatalogueDBAdapter.KEY_TITLE};
		// and an array of the fields we want to bind those fields to (in this case just text1)
		to = new int[]{R.id.row_row_id, R.id.row_position, R.id.row_author, R.id.row_title};
		// Now create a simple cursor adapter and set it to display
		SimpleCursorAdapter books = new AnthologyTitleListAdapter(this, layout, BooksCursor, from, to);
		setListAdapter(books);
		
		registerForContextMenu(getListView());
	}
	
	/**
	 * The adapter for the Titles List
	 * 
	 * @author evan
	 */
	public class AnthologyTitleListAdapter extends SimpleCursorAdapter {
		boolean series = false;
		
		/**
		 * 
		 * Pass the parameters directly to the overridden function
		 * 
		 * @param context
		 * @param layout
		 * @param cursor
		 * @param from
		 * @param to
		 */
		public AnthologyTitleListAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to) {
			super(context, layout, cursor, from, to);
		}
		
		/**
		 * Override the setTextView function. This helps us set the appropriate opening and
		 * closing brackets for author names.
		 */
		@Override
		public void setViewText(TextView v, String text) {
			if (v.getId() == R.id.row_author) {
				if (mSame.isChecked()) {
					v.setVisibility(View.GONE);
				} else {
					text = " (" + text + ")";
				}
			} else if (v.getId() == R.id.row_position) {
				text = text + ". ";
			} else if (v.getId() == R.id.row_row_id) {
				final long this_text = Long.parseLong(text); 
				ImageView up = (ImageView) ((ViewGroup) v.getParent()).findViewById(R.id.row_up);
				up.setOnClickListener(new View.OnClickListener() {
					public void onClick(View view) {
						int position = mDbHelper.updateAnthologyTitlePosition(this_text, true);
						fillAnthology(position-2);
					}
				});
				ImageView down = (ImageView) ((ViewGroup) v.getParent()).findViewById(R.id.row_down);
				down.setOnClickListener(new View.OnClickListener() {
					public void onClick(View view) {
						int position = mDbHelper.updateAnthologyTitlePosition(this_text, false);
						fillAnthology(position);
					}
				});
				text = "";
			}
			v.setText(text);
		}
		
	}
	
	/**
	 * Scroll to the current group
	 */
	public void gotoTitle(int id) {
		try {
			ListView view = this.getListView();
			view.setSelection(id);
		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}
	
	public void searchWikipedia() {
		String basepath = "http://en.wikipedia.org";
		String pathAuthor = bookAuthor.replace(" ", "+");
		pathAuthor = pathAuthor.replace(",", "");
		// Strip everything past the , from the title
		String pathTitle = bookTitle;
		int comma = bookTitle.indexOf(",");
		if (comma > 0) {
			pathTitle = pathTitle.substring(0, comma);
		}
		pathTitle = pathTitle.replace(" ", "+");
		String path = basepath + "/w/index.php?title=Special:Search&search=%22" + pathTitle + "%22+" + pathAuthor + "";
		boolean success = false;
		URL url;
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		SearchWikipediaHandler handler = new SearchWikipediaHandler();
		SearchWikipediaEntryHandler entryHandler = new SearchWikipediaEntryHandler();

		try {
			url = new URL(path);
			parser = factory.newSAXParser();
			try {
				parser.parse(Utils.getInputStream(url), handler);
			} catch (RuntimeException e) {
				Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				Logger.logError(e);
				return;
			}
			String[] links = handler.getLinks();
			for (int i = 0; i < links.length; i++) {
				if (links[i].equals("") || success == true) {
					break;
				}
				url = new URL(basepath + links[i]);
				parser = factory.newSAXParser();
				try {
					parser.parse(Utils.getInputStream(url), entryHandler);
					ArrayList<String> titles = entryHandler.getList();
					/* Display the confirm dialog */
					if (titles.size() > 0) {
						success = true;
						showAnthologyConfirm(titles);
					}
				} catch (RuntimeException e) {
					Logger.logError(e);
					Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				}
			}
			if (success == false) {
				Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				return;
			}
		} catch (MalformedURLException e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		} catch (ParserConfigurationException e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		} catch (SAXException e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		} catch (Exception e) {
			Toast.makeText(this, R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		}
		fillAnthology();
		return;
	}
	
	private void showAnthologyConfirm(final ArrayList<String> titles) {
		String anthology_title = "";
		for (int j=0; j < titles.size(); j++) {
			anthology_title += "* " + titles.get(j) + "\n";
		}
		
		AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(anthology_title).create();
		alertDialog.setTitle(R.string.anthology_confirm);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton(this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				for (int j=0; j < titles.size(); j++) {
					String anthology_title = titles.get(j);
					anthology_title = anthology_title + ", ";
					String anthology_author = bookAuthor;
					// Does the string look like "Hindsight by Jack Williamson"
					int pos = anthology_title.indexOf(" by ");
					if (pos > 0) {
						anthology_author = anthology_title.substring(pos+4);
						anthology_title = anthology_title.substring(0, pos);
					}
					// Trim extraneous punctionaction and whitespace from the titles and authors
					anthology_author = anthology_author.trim().replace("\n", " ").replaceAll("[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$", "").trim();
					anthology_title = anthology_title.trim().replace("\n", " ").replaceAll("[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$", "").trim();
					mDbHelper.createAnthologyTitle(mRowId, anthology_author, anthology_title, true);
				}
				fillAnthology();
				return;
			}
		}); 
		alertDialog.setButton2(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				//do nothing
				return;
			}
		}); 
		alertDialog.show();

	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		Cursor anthology = mDbHelper.fetchAnthologyTitleById(id);
		String title;
		String author;
		try {
			anthology.moveToFirst();
			title = anthology.getString(anthology.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE)); 
			author = anthology.getString(anthology.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_NAME));
			anthology.close();			
		} finally {
			if (anthology != null)
				anthology.close();
		}

		currentPosition = position;
		mEditId = id;
		mTitleText.setText(title);
		mAuthorText.setText(author);
		mAdd.setText(R.string.anthology_save);
	}
	
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		MenuItem populate = menu.add(0, POPULATE, 0, R.string.populate_anthology_titles);
		populate.setIcon(android.R.drawable.ic_menu_add);
		return super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case POPULATE:
			searchWikipedia();
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, DELETE_ID, 0, R.string.menu_delete_anthology);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case DELETE_ID:
				AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
				mDbHelper.deleteAnthologyTitle(info.id);
				fillAnthology();
				return true;
		}
		return super.onContextItemSelected(item);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mRowId != null) {
			outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		} else {
			//there is nothing todo
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}
	
	/*
	@Override
	protected void onResume() {
		super.onResume();
		fillAnthology();
	}
	*/

	private void saveState() {
		Integer anthology = CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS;
		if (mSame.isChecked()) {
			anthology = CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR;
		}
		Bundle values = new Bundle();
		values.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		values.putInt(CatalogueDBAdapter.KEY_ANTHOLOGY, anthology);

		if (mRowId == null || mRowId == 0) {
			//This should never happen
			//long id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num);
			Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_LONG).show();
			finish();
		} else {
			mDbHelper.updateBook(mRowId, values, true);
		}
		return;
	}
	
	@Override
	protected void onDestroy() {
		destroyManagedCursors();
		super.onDestroy();
		mDbHelper.close();
	}

}
