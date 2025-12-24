/*
 * @copyright 2013 Evan Leybourn
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
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter.AnthologyTitleExistsException;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

public class BookEditAnthology extends BookEditFragmentAbstract {
	
	private EditText mTitleText;
	private AutoCompleteTextView mAuthorText;
	private String bookAuthor;
	private String bookTitle;
	private Button mAdd;
	private CheckBox mSame;
	private Integer mEditPosition = null;
	int anthology_num = CatalogueDBAdapter.ANTHOLOGY_NO;
	private ArrayList<AnthologyTitle> mList;
	
	private static final int DELETE_ID = Menu.FIRST;
	private static final int POPULATE = Menu.FIRST + 1;
	

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.list_anthology, container, false);
	}

	/**
	 * Display the edit fields page
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		loadPage();
	}
	
	/**
	 * Display the main manage anthology page. This has three parts. 
	 * 1. Setup the "Same Author" checkbox
	 * 2. Setup the "Add Title" fields
	 * 3. Populate the "Title List" - @see fillAnthology();
	 */
	public void loadPage() {
		
		BookData book = mEditManager.getBookData();
		bookAuthor = book.getString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
		bookTitle = book.getString(CatalogueDBAdapter.KEY_TITLE);
		// Setup the same author field
		anthology_num = book.getInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK);			

		mSame = (CheckBox) getView().findViewById(R.id.same_author);
		if ((anthology_num & CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS) != 0) {
			mSame.setChecked(false);
		} else {
			mSame.setChecked(true);
		}
		
		mSame.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				saveState(mEditManager.getBookData());
				loadPage();
			}
		});
		
		ArrayAdapter<String> author_adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_dropdown_item_1line, mDbHelper.getAllAuthors());
		mAuthorText = (AutoCompleteTextView) getView().findViewById(R.id.add_author);
		mAuthorText.setAdapter(author_adapter);
		if (mSame.isChecked()) {
			mAuthorText.setVisibility(View.GONE);
		} else {
			mAuthorText.setVisibility(View.VISIBLE);			
		}
		mTitleText = (EditText) getView().findViewById(R.id.add_title);

		mAdd = (Button) getView().findViewById(R.id.row_add);
		mAdd.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				try {
					String title = mTitleText.getText().toString();
					String author = mAuthorText.getText().toString();
					if (mSame.isChecked()) {
						author = bookAuthor; 
					}
					AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter)BookEditAnthology.this.getListView().getAdapter());
					if (mEditPosition == null) {
						AnthologyTitle anthology = new AnthologyTitle(new Author(author), title);
						adapter.add(anthology);
					} else {
						AnthologyTitle anthology = adapter.getItem(mEditPosition);
						anthology.setAuthor(new Author(author));
						anthology.setTitle(title);
						mEditPosition = null;
						mAdd.setText(R.string.anthology_add);
					}
					mTitleText.setText("");
					mAuthorText.setText("");
					//fillAnthology(currentPosition);
					mEditManager.setDirty(true);
				} catch(AnthologyTitleExistsException e) {
					Toast.makeText(getActivity(), R.string.the_title_already_exists, Toast.LENGTH_LONG).show();
				}
			}
		});
		
		fillAnthology();
	}
	
	public void fillAnthology(int scroll_to_id) {
		fillAnthology();
		gotoTitle(scroll_to_id);
	}
	
	/**
	 * Populate the bookEditAnthology view
	 */
	public void fillAnthology() {

		// Get all of the rows from the database and create the item list
		mList = mEditManager.getBookData().getAnthologyTitles(); // mDbHelper.getBookAnthologyTitleList(mEditManager.getBookData().getRowId());
		// Now create a simple cursor adapter and set it to display
		AnthologyTitleListAdapter books = new AnthologyTitleListAdapter(getActivity(), R.layout.row_anthology, mList);
		final ListView list = getListView();
		list.setAdapter(books);
		registerForContextMenu(list);
		list.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				mEditPosition = position;
				AnthologyTitle anthology = mList.get(position);
				mTitleText.setText(anthology.getTitle());
				mAuthorText.setText(anthology.getAuthor().getDisplayName());
				mAdd.setText(R.string.anthology_save);
			}});
		
	}
	
	private ListView getListView() {
		return (ListView) getView().findViewById(R.id.list);
	}

	public class AnthologyTitleListAdapter extends SimpleListAdapter<AnthologyTitle> {
		boolean series = false;
		
		/**
		 * 
		 * Pass the parameters directly to the overridden function
		 * 
		 * @param context
		 * @param rowViewId
		 * @param items
		 */
		public AnthologyTitleListAdapter(Context context, int rowViewId, ArrayList<AnthologyTitle> items) {
			super(context, rowViewId, items);
		}

		@Override
		protected void onSetupView(AnthologyTitle anthology, int position, View target) {
			TextView author = (TextView)target.findViewById(R.id.row_author);
			author.setText(anthology.getAuthor().getDisplayName());
			TextView title = (TextView)target.findViewById(R.id.row_title);
			title.setText(anthology.getTitle());
		}
		
		@Override
		protected void onRowClick(AnthologyTitle anthology, int position, View v) {
			mEditPosition = position;
			mTitleText.setText(anthology.getTitle());
			mAuthorText.setText(anthology.getAuthor().getDisplayName());
			mAdd.setText(R.string.anthology_save);
		};
		
		@Override
		protected void onListChanged() {
			mEditManager.setDirty(true);
		};
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
		String basepath = "https://en.wikipedia.org";
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
				Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
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
					Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				}
			}
			if (success == false) {
				Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
				return;
			}
		} catch (MalformedURLException e) {
			Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		} catch (ParserConfigurationException e) {
			Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		} catch (SAXException e) {
			Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		} catch (Exception e) {
			Toast.makeText(getActivity(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
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
		
		AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).setMessage(anthology_title).create();
		alertDialog.setTitle(R.string.anthology_confirm);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, this.getResources().getString(R.string.button_ok), new DialogInterface.OnClickListener() {
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
					AnthologyTitle anthology = new AnthologyTitle(new Author(anthology_author), anthology_title);
					mList.add(anthology);
				}
				AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter)BookEditAnthology.this.getListView().getAdapter());
				adapter.notifyDataSetChanged();
				return;
			}
		}); 
		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, this.getResources().getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				//do nothing
				return;
			}
		}); 
		alertDialog.show();

	}
	
	/**
	 * Run each time the menu button is pressed. This will setup the options menu
	 */
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		MenuItem populate = menu.add(0, POPULATE, 0, R.string.populate_anthology_titles);
		populate.setIcon(android.R.drawable.ic_menu_add);
		super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * This will be called when a menu item is selected. A large switch statement to
	 * call the appropriate functions (or other activities) 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case POPULATE:
			searchWikipedia();
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, DELETE_ID, 0, R.string.menu_delete_anthology);
	}
	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		switch(item.getItemId()) {
			case DELETE_ID:
				AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
				AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter)BookEditAnthology.this.getListView().getAdapter());
				adapter.remove(adapter.getItem((int)info.id));
				mEditManager.setDirty(true);
				return true;
		}
		return super.onContextItemSelected(item);
	}

	private void saveState(BookData book) {
		if (mSame.isChecked()) {
			anthology_num = CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY;
		} else {
			anthology_num = CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS ^ CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY;
		}
		book.setAnthologyTitles(mList);
		book.putInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK, anthology_num);
	}
	
	@Override 
	public void onPause() {
		super.onPause();
		saveState(mEditManager.getBookData());
	}

	@Override
	protected void onLoadBookDetails(BookData book) {
		if (!false)
			mFields.setAll(book);
	}

	@Override
	protected void onSaveBookDetails(BookData book) {
		super.onSaveBookDetails(book);
		saveState(book);		
	}
}
