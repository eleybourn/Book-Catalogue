/*
 * @copyright 2012 Philip Warner
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

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to make building menus simpler. Implements some default menu items and allows
 * selecting which need to be added as well as the addition of custom items.
 * 
 * @author Philip Warner
 */
public class MenuHandler {
	private static final int MNU_ADD_BOOK = Menu.FIRST+1;
	private static final int MNU_ITM_ADD_BOOK_MANUAL = Menu.FIRST+2;
	private static final int MNU_ITM_ADD_BOOK_BARCODE = Menu.FIRST+3;
	private static final int MNU_ITM_ADD_BOOK_ISBN = Menu.FIRST+4;
	private static final int MNU_ITM_ADD_BOOK_NAMES = Menu.FIRST+5;
	private static final int MNU_ITM_HELP = Menu.FIRST+7;
	private static final int MNU_ITM_ADMIN = Menu.FIRST+8;
	private static final int MNU_ITM_SEARCH = Menu.FIRST+9;
	private static final int MNU_ITM_ABOUT = Menu.FIRST+10;
	private static final int MNU_ITM_DONATE = Menu.FIRST+11;
	private static final int MNU_ITM_BOOKSHELVES = Menu.FIRST+12;
	
	public static final int FIRST = Menu.FIRST+13;

	private int mSort = 0;

	public void init(Menu menu) {
		mSort = 0;
		menu.clear();
	}
	
	/**
	 * Add menu and submenu for book creation.
	 * 
	 * @param menu	Root menu
	 */
	public void addCreateBookItems(Menu menu) {
		SubMenu addMenu = menu.addSubMenu(0, MNU_ADD_BOOK, mSort++, BookCatalogueApp.getResourceString(R.string.menu_insert) + "...");
		addMenu.setIcon(android.R.drawable.ic_menu_add);
		{
			if (Utils.USE_BARCODE) {
				MenuItem insertBC = addMenu.add(0, MNU_ITM_ADD_BOOK_BARCODE, mSort++, R.string.scan_barcode_isbn);
				insertBC.setIcon(R.drawable.ic_menu_insert_barcode);			
			}
			MenuItem insertISBN = addMenu.add(0, MNU_ITM_ADD_BOOK_ISBN, mSort++, R.string.enter_isbn);
			insertISBN.setIcon(android.R.drawable.ic_menu_zoom);
			
			MenuItem insertName = addMenu.add(0, MNU_ITM_ADD_BOOK_NAMES, mSort++, R.string.search_internet);
			insertName.setIcon(android.R.drawable.ic_menu_zoom);

			MenuItem insertBook = addMenu.add(0, MNU_ITM_ADD_BOOK_MANUAL, mSort++, R.string.add_manually);
			insertBook.setIcon(android.R.drawable.ic_menu_add);
		}
	}

	/**
	 * Add a custom menu item.
	 * 
	 * @param menu		Root menu
	 * @param id		Menu item ID
	 * @param stringId	String ID to display
	 * @param icon		Icon for menu item
	 * 
	 * @return			The new item
	 */
	public MenuItem addItem( Menu menu, int id, int stringId, int icon ) {
		MenuItem item = menu.add(0, id, mSort++, stringId);
		item.setIcon(icon);		
		return item;
	}

	/**
	 * Add the default 'help & admin' menu item
	 * 
	 * @param menu	root menu
	 */
	public void addCreateHelpAndAdminItems(Menu menu) {
		{
			String title = BookCatalogueApp.getResourceString(R.string.menu_bookshelf);
			MenuItem item = menu.add(0, MNU_ITM_BOOKSHELVES, mSort++, title);
			item.setIcon(R.drawable.ic_menu_bookshelves);
		}
		{
			String helpTitle = BookCatalogueApp.getResourceString(R.string.help);
			MenuItem help = menu.add(0, MNU_ITM_HELP, mSort++, helpTitle);
			help.setIcon(android.R.drawable.ic_menu_help);
		}
		{
			String adminTitle = BookCatalogueApp.getResourceString(R.string.menu_administration);
			MenuItem admin = menu.add(0, MNU_ITM_ADMIN, mSort++, adminTitle);
			admin.setIcon(android.R.drawable.ic_menu_manage);
		}
		{
			String aboutTitle = BookCatalogueApp.getResourceString(R.string.about_label);
			MenuItem admin = menu.add(0, MNU_ITM_ABOUT, mSort++, aboutTitle);
			admin.setIcon(R.drawable.ic_menu_info_details);
		}
		{
			String aboutTitle = BookCatalogueApp.getResourceString(R.string.donate_label);
			MenuItem admin = menu.add(0, MNU_ITM_DONATE, mSort++, aboutTitle);
			admin.setIcon(R.drawable.ic_menu_donate);
		}
	}
	
	/**
	 * Add the default 'search' menu item
	 * 
	 * @param menu	root menu
	 */
	public void addSearchItem(Menu menu) {
		MenuItem search = menu.add(0, MNU_ITM_SEARCH, mSort++, R.string.menu_search);
		search.setIcon(android.R.drawable.ic_menu_search);
	}

	/**
	 * Handle the default menu items
	 * 
	 * @param a				Calling activity
	 * @param featureId		
	 * @param item			The item selected
	 * 
	 * @return		True, if handled
	 */
	public boolean onMenuItemSelected(Activity a, int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case MNU_ITM_ADD_BOOK_MANUAL:
			createBook(a);
			return true;
		case MNU_ITM_ADD_BOOK_ISBN:
			createBookISBN(a, "isbn");
			return true;
		case MNU_ITM_ADD_BOOK_BARCODE:
			createBookScan(a);
			return true;
		case MNU_ITM_ADD_BOOK_NAMES:
			createBookISBN(a,"name");
			return true;
		case MNU_ITM_HELP:
			helpPage(a);
			return true;
		case MNU_ITM_ADMIN:
			adminPage(a);
			return true;
		case MNU_ITM_DONATE:
			donatePage(a);
			return true;
		case MNU_ITM_ABOUT:
			aboutPage(a);
			return true;
		case MNU_ITM_BOOKSHELVES:
			bookshelvesPage(a);
			return true;
		case MNU_ITM_SEARCH:
			a.onSearchRequested();
			return true;
		}
		
		return false;
	}

	/**
	 * Load the BookEdit Activity
	 */
	private void createBook(Activity a) {
		Intent i = new Intent(a, BookEdit.class);
		a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY);
	}

	/**
	 * Load the Search by ISBN Activity
	 */
	private void createBookISBN(Activity a, String by) {
		Intent i = new Intent(a, BookISBNSearch.class);
		i.putExtra(BookISBNSearch.BY, by);
		a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_ISBN);
	}
	
	/**
	 * Load the Search by ISBN Activity to begin scanning.
	 */
	private void createBookScan(Activity a) {
		Intent i = new Intent(a, BookISBNSearch.class);
		i.putExtra(BookISBNSearch.BY, "scan");
		a.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_SCAN);
	}

	/**
	 * Load the Admin Activity
	 */
	private void adminPage(Activity a) {
		Intent i = new Intent(BookCatalogueApp.context, AdministrationFunctions.class);
		i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
		a.startActivityForResult(i, UniqueId.ACTIVITY_ADMIN);
	}
	
	/**
	 * Load the Bookshelves Activity
	 */
	private void bookshelvesPage(Activity a) {
		Intent i = new Intent(BookCatalogueApp.context, Bookshelf.class);
		i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
		a.startActivityForResult(i, UniqueId.ACTIVITY_BOOKSHELF);
	}
	
	/**
	 * Load the About Activity
	 */
	private void aboutPage(Activity a) {
		Intent i = new Intent(BookCatalogueApp.context, AdministrationAbout.class);
		i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
		a.startActivityForResult(i, UniqueId.ACTIVITY_ABOUT);
	}
	
	/**
	 * Load the Donate Activity
	 */
	private void donatePage(Activity a) {
		Intent i = new Intent(BookCatalogueApp.context, AdministrationDonate.class);
		i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
		a.startActivityForResult(i, UniqueId.ACTIVITY_DONATE);
	}
	
	/**
	 * Load the Main Menu Activity
	 */
	private void helpPage(Activity a) {
		Intent i = new Intent(BookCatalogueApp.context, Help.class);
		i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
		a.startActivityForResult(i, UniqueId.ACTIVITY_HELP);
	}

	/**
	 * Load the EditBook activity based on the provided id. Also open to the provided tab
	 * 
	 * @param id The id of the book to edit
	 * @param tab Which tab to open first
	 */
	public static void editBook(Activity a, long id, int tab) {
		Intent i = new Intent(a, BookEdit.class);
		i.putExtra(CatalogueDBAdapter.KEY_ROWID, id);
		i.putExtra(BookEdit.TAB, tab);
		a.startActivityForResult(i, UniqueId.ACTIVITY_EDIT_BOOK);
		return;
	}
}
