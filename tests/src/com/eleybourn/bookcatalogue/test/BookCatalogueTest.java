/**
 * 
 */
package com.eleybourn.bookcatalogue.test;

import java.io.File;
import java.util.ArrayList;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.Utils;
import com.jayway.android.robotium.solo.Solo;

@SuppressWarnings("unchecked")
/**
 * @author evan
 *
 */
public class BookCatalogueTest extends ActivityInstrumentationTestCase2 {
	
	private static final String TARGET_PACKAGE_ID = "com.eleybourn.bookcatalogue";
	private static final String LAUNCHER_ACTIVITY_FULL_CLASSNAME = "com.eleybourn.bookcatalogue.BookCatalogue";
	private static Class<?> launcherActivityClass;	
	
	static{
		try {	
			launcherActivityClass = Class.forName(LAUNCHER_ACTIVITY_FULL_CLASSNAME);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	//@SuppressWarnings("unchecked")	
	public BookCatalogueTest() throws ClassNotFoundException {
		super(TARGET_PACKAGE_ID, launcherActivityClass);
	}
	
	private Solo t;
	
	@Override
	protected void setUp() throws Exception {
		t =  new Solo(getInstrumentation(), getActivity());
	}
	
	public void test000Reset() {
        File sp = new File("/data/data/com.eleybourn.bookcatalogue/shared_prefs/bookCatalogue.xml");
        sp.delete();
        File db = new File("/data/data/com.eleybourn.bookcatalogue/databases/book_catalogue");
        db.delete();
    }
	
	public void test101AddByISBNNumbers() {
		t.clickOnMenuItem("Add Book...");
		t.clickOnText("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		
		//setup fields
		EditText isbn = t.getEditText(0); //ISBN
		
		//test all the buttons 
		t.clickOnText("1");
		t.clickOnText("2");
		t.clickOnText("3");
		t.clickOnText("4");
		t.clickOnText("5");
		t.clickOnText("6");
		t.clickOnText("7");
		t.clickOnText("8");
		t.clickOnText("9");
		t.clickOnText("X");
		t.clickOnText("0");
		t.sleep(1000);
		//TODO: FIX	solo.clickOnText("1");
		//			solo.clickOnImageButton(0);
		assertEquals("The ISBN string was different than expected", "123456789X0", isbn.getText().toString());
	}
	
	public void test102AddByISBN(){
		t.clickOnMenuItem("Add Book...");
		t.clickOnText("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		
		//setup fields
		EditText isbn = t.getEditText(0); //ISBN
		Log.e("BC", "" + t.getCurrentButtons().size());
		Log.e("BC", "" + t.getCurrentImageButtons().size());
		
		//Search for an ISBN
		t.clearEditText(isbn);
		t.enterText(isbn, "0586057242");
		t.clickOnButton("Search");
		t.sleep(10000); //wait 10 seconds for the page to search and load
		
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		assertTrue("Expected populated title", t.searchEditText("The Complete Robot"));
		assertTrue("Expected populated ISBN", t.searchEditText("9780586057247"));
		t.clickOnButton("Add Book");
		
		t.sleep(1000);
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		t.clickOnButton("Save Book");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		t.goBack();
		
		assertTrue("Expected populated title", t.searchText("Complete Robot, The"));
		assertTrue("Expected populated author", t.searchText("Asimov, Isaac"));
	}
	
	public void test103AddByISBNLandscape() {
		t.clickOnMenuItem("Add Book...");
		t.clickOnText("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		t.setActivityOrientation(Solo.LANDSCAPE); // Change orientation of activity
		
		//setup fields
		EditText isbn = t.getEditText(0); //ISBN
		
		//test all the buttons 
		t.clickOnText("1");
		t.clickOnText("2");
		t.clickOnText("3");
		t.clickOnText("4");
		t.clickOnText("5");
		t.clickOnText("6");
		t.clickOnText("7");
		t.clickOnText("8");
		t.clickOnText("9");
		t.clickOnText("X");
		t.clickOnText("0");
		t.setActivityOrientation(Solo.PORTRAIT); // Change orientation of activity
		t.sleep(1000);
		//TODO: FIX	solo.clickOnText("1");
		//			solo.clickOnImageButton(0);
		assertEquals("The ISBN string was different than expected", "123456789X0", isbn.getText().toString());
		assertTrue("Expected search button", t.searchButton("Search"));
	}
	
	public void test104Anthology() {
		if (t.searchText("Complete Robot") == false) {
			t.clickOnText("Asimov");
		}
		t.sleep(100);
		t.clickOnText("Complete Robot");
		
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		assertTrue("Expected book title", t.searchText("Complete Robot, The"));
		t.searchText("Is this book an Anthology?");
		if (t.isCheckBoxChecked(0) == false) {
			t.clickOnCheckBox(0);
			assertTrue("Expected anthology tab to appear", t.searchText("Anthology Titles"));
		}
		t.clickOnCheckBox(0);
		assertFalse("Expected anthology tab to disappear", t.searchText("Anthology Titles"));
		t.clickOnCheckBox(0);
		assertTrue("Expected anthology tab to appear", t.searchText("Anthology Titles"));
		
		t.clickOnText("Anthology Titles");
		
		//multiple authors
		t.clickOnText("All stories in this anthology are written by the same author");
		t.sleep(100);
		
		EditText author = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.add_author);
		EditText title = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.add_title);
		t.sleep(100);
		
		assertEquals("Author", author.getHint().toString());
		assertEquals("Title", title.getHint().toString());
		
		//manually add title
		t.enterText(author, "Test Author");
		t.enterText(title, "Test Title");
		t.clickOnButton("Add");
		
		//same author
		int gone = 8;
		int visible = 0;
		t.clickOnText("All stories in this anthology are written by the same author");
		author = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.add_author);
		t.sleep(100);
		assertEquals(gone, author.getVisibility());
		assertFalse(t.searchText("Author, Test", true));
		t.clickOnText("All stories in this anthology are written by the same author");
		t.sleep(100);
		author = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.add_author);
		assertEquals(visible, author.getVisibility());
		assertTrue(t.searchText("Author, Test", true));
		t.clickOnText("All stories in this anthology are written by the same author");
		t.sleep(100);
		author = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.add_author);
		assertEquals(gone, author.getVisibility());
		assertFalse(t.searchText("Author, Test", true));
		
		//edit list
		title = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.add_title);
		t.clickInList(0);
		t.clearEditText(title);
		t.enterText(title, "A");
		t.clickOnButton("Save");
		assertTrue(t.searchText("A"));
		t.sleep(1000);
		
		//setup move
		title = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.add_title);
		t.enterText(title, "B");
		t.clickOnButton("Add");
		t.enterText(title, "C");
		t.clickOnButton("Add");
		t.scrollUpList(0);
		t.sleep(1000);
		
		//TODO: Fix this. Image number does not work???
		
		ArrayList<View> v2 = t.getCurrentViews();
		for (int i = 0; i<v2.size(); i++) {
			View view = v2.get(i);
			Log.e("BC", i + "	" + view.toString() + "	" + view.getId() + "	" + view.getWidth() + "	" + view.isClickable() + "	");
		}
		
		//move title down
		t.clickOnImage(4);
		t.sleep(1000);
		ArrayList<TextView> v = t.getCurrentTextViews(null);
		boolean found = false;
		for (int i=0; i<v.size(); i++) {
			if (v.get(i).getText().toString().trim().equals("B")) {
				found = false;
			}
			if (v.get(i).getText().toString().trim().equals("A")) {
				found = true;
			}
			Log.e("BC", "A" + found + " " + i + " " + v.get(i).getText().toString() + ".");
		}
		assertTrue(found);
		t.scrollUpList(0);
		
		//move title up
		t.clickOnImage(7);
		t.sleep(1000);
		v = t.getCurrentTextViews(null);
		found = false;
		for (int i=0; i<v.size(); i++) {
			if (v.get(i).getText().toString().trim().equals("A")) {
				found = false;
			}
			if (v.get(i).getText().toString().trim().equals("B")) {
				found = true;
			}
			Log.e("BC", "B" + found + " " + i + " " + v.get(i).getText().toString() + ".");
		}
		assertTrue(found);
		
		//delete
		t.clickLongInList(0);
		t.clickOnText("Delete Title from Anthology");
		assertFalse(t.searchText("Foobar"));
		
		
		//search & cancel
		t.clickOnMenuItem("Automatically Populate Titles");
		t.sleep(3000);
		t.clickOnButton("Cancel");
		assertFalse(t.searchText("Sally"));
		t.sleep(1000);
		
		//search & do
		t.clickOnMenuItem("Automatically Populate Titles");
		t.sleep(3000);
		t.clickOnButton("OK");
		assertTrue(t.searchText("Sally"));
	}
	
	public void test105AddByName(){
		t.clickOnMenuItem("Add Book...");
		t.clickOnText("Add by Name");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		
		//setup fields
		EditText author = t.getEditText(0);
		EditText title = t.getEditText(1); 
		
		//Search for an ISBN
		t.clearEditText(author);
		t.clearEditText(title);
		t.enterText(author, "Terry Pratchett");
		t.enterText(title, "Nation");
		t.clickOnButton("Search");
		t.sleep(10000); //wait 10 seconds for the page to search and load
		
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		assertTrue("Expected populated title", t.searchEditText("Nation"));
		assertTrue("Expected populated author", t.searchText("Terry Pratchett"));
		assertTrue("Expected populated ISBN", t.searchEditText("9780552557795"));
		t.clickOnButton("Add Book");
		
		t.sleep(1000);
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		t.clickOnButton("Save Book");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		t.goBack();
		
		assertTrue("Expected populated title", t.searchText("Nation"));
		assertTrue("Expected populated author", t.searchText("Pratchett, Terry"));
	}
	
	public void test106AddBook(){
		t.clickOnMenuItem("Add Book...");
		t.clickOnText("Add Book", 2);
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		
		//add author
		t.clickOnText("Set Authors");
		t.assertCurrentActivity("Expected EditAuthorList activity", "EditAuthorList");
		
		EditText author = t.getEditText(0);
		t.enterText(author, "XXX");
		t.clickOnText("Add");
		assertFalse("The author textbox is not blank", t.searchEditText("XXX"));
		assertTrue("XXX was not added", t.searchText("XXX"));
		
		//add and delete author
		t.enterText(author, "Isaac Asimov");
		t.clickOnText("Add");
		assertFalse("The author textbox is not blank", t.searchEditText("Isaac Asimov"));
		assertTrue("Isaac Asimov was not added", t.searchText("Isaac Asimov"));
		t.clickOnImage(0);
		assertFalse("XXX was not deleted", t.searchText("XXX"));
		t.clickOnText("Save");
		
		//setup fields
		t.enterText(0, "I Robot");
		assertTrue("Title was not entered", t.searchText("I Robot"));
		t.enterText(2, "Publisher");
		t.clickOnText("Date Published");
		t.clickOnText("Set");
		assertFalse("Date not set", t.searchText("Not Set"));
		
		//TODO: BROKEN
		//t.scrollUp();
		//t.sleep(1000);
		//t.drag(100, 100, 1000, 600, 6);
		//t.sleep(1000);
		//t.clickOnText("Select Bookshelves", 0, true);
		//t.sleep(1000);
		//t.clickOnCheckBox(0);
		//t.clickOnCheckBox(1);
		//t.clickOnText("OK");
		
		t.clickOnText("Add Book", 3);
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		t.clickOnButton("Save Book");
		t.assertCurrentActivity("Expected BookCatalogue activity", "BookCatalogue");
		
		assertTrue("Expected populated author", t.searchText("Asimov, Isaac"));
		if (t.searchText("I Robot") == false) {
			//it may be closed
			t.clickOnText("Asimov, Isaac");
			assertTrue("Expected populated title", t.searchText("I Robot"));
		}
		
	}
	
	public void test107EditNotes(){
		if (t.searchText("I Robot") == false) {
			//it may be closed
			t.clickOnText("Asimov, Isaac");
			assertTrue("Expected populated title", t.searchText("I Robot"));
		}
		t.clickLongOnText("I Robot");
		t.clickOnText("Your Comments");
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		
		//save book
		t.clickOnText("Have you read this book?");
		
		t.enterText(0, "My Notes");
		assertTrue("Notes were not entered", t.searchText("My Notes", true));
		
		//check that persistance works
		t.clickOnText("Edit Book");
		t.assertCurrentActivity("Expected BookEditFields activity", "BookEditFields");
		//assertFalse("Notes still visible", t.searchText("My Notes", true));
		t.clickOnText("Your Comments");
		t.assertCurrentActivity("Expected BookEditNotes activity", "BookEditNotes");
		assertTrue("Notes not visible", t.searchText("My Notes", true));
		
		t.clickOnText("Has this book been signed");
		t.scrollDown();
		t.clickOnText("Date started");
		t.clickOnText("Set");
		t.clickOnText("Date finished");
		t.clickOnText("Set");
		//EditText location = t.getEditText("Location of the book");
		//t.enterText(location, "My Location");
		//assertTrue("Location not entered", t.searchText("My Location"));
		t.clickOnText("Save Book");
		t.assertCurrentActivity("Expected BookCatalogue activity", "BookCatalogue");
	}
	
	
	
	public void test201AdminHelp() {
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		assertTrue("Expected help link", t.searchText("https://github.com/eleybourn/Book-Catalogue/wiki/Help"));
	}
	
	public void test202AdminBookshelves() {
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Administration Functions");
		t.assertCurrentActivity("Expected AdministrationFunctions activity", "AdministrationFunctions");
		t.clickOnText("Manage Bookshelves");
		t.assertCurrentActivity("Expected Bookshelf activity", "Bookshelf");
		
		//add 3x bookshelves
		t.clickOnMenuItem("Create Bookshelf");
		t.assertCurrentActivity("Expected BookshelfEdit activity", "BookshelfEdit");
		EditText bookshelf = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.bookshelf);
		t.clearEditText(bookshelf);
		t.enterText(bookshelf, "A1");
		t.clickOnButton("Add Bookshelf");
		t.assertCurrentActivity("Expected Bookshelf activity", "Bookshelf");
		
		t.clickOnMenuItem("Create Bookshelf");
		bookshelf = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.bookshelf);
		t.clearEditText(bookshelf);
		t.enterText(bookshelf, "A2");
		t.clickOnButton("Add Bookshelf");
		
		t.clickOnMenuItem("Create Bookshelf");
		bookshelf = (EditText) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.bookshelf);
		t.clearEditText(bookshelf);
		t.enterText(bookshelf, "A3");
		t.clickOnButton("Add Bookshelf");
		
		//Edit bookshelf
		t.clickOnText("A2");
		t.assertCurrentActivity("Expected BookshelfEdit activity", "BookshelfEdit");
		t.clickOnEditText(0); //A1
		t.goBack();
		t.enterText(0, "x2");
		t.clickOnButton("Save Bookshelf");
		t.assertCurrentActivity("Expected Bookshelf activity", "Bookshelf");
		
		//Delete bookshelf
		t.clickLongInList(0);
		t.clickOnText("Delete Bookshelf");
		assertFalse(t.searchText("A1"));
		
	}
	
	/*public void test203AdminBackupDatabase() {
		//weak test
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Administration Functions");
		t.assertCurrentActivity("Expected AdministrationFunctions activity", "AdministrationFunctions");
		t.clickOnText("Backup Database");
		t.sleep(1000);
		String filename = com.eleybourn.bookcatalogue.Utils.EXTERNAL_FILE_PATH + "/bookCatalogueDbExport.db";
		Log.e("BC", filename);
		File file = new File(filename);
		assertTrue("Backup file does not exist", file.exists());
	}*/
	
	public void test204AdminFieldInvisibility() {
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Administration Functions");
		t.assertCurrentActivity("Expected AdministrationFunctions activity", "AdministrationFunctions");
		t.clickOnText("Manage Field Visibility");
		t.assertCurrentActivity("Expected FieldVisibility activity", "FieldVisibility");
		
		//set all as true (reset)
		ArrayList<CheckBox> fields = t.getCurrentCheckBoxes();
		for (int i = 0; i<fields.size(); i++) {
			CheckBox field = fields.get(i);
			if (!field.isChecked()) {
				t.clickOnCheckBox(i);
			}
		}
		
		//set known fields (will need to be updated for each new field)
		t.scrollUp();
		t.scrollUp();
		t.scrollUp();
		t.scrollUp();
		t.clickOnText("Cover Thumbnail");
		t.clickOnText("ISBN");
		t.clickOnText("Series");
		t.clickOnText("#");
		t.clickOnText("Publisher");
		t.clickOnText("Date Published");
		t.clickOnText("Pages");
		t.clickOnText("List Price");
		t.clickOnText("Have you read this book?");
		t.clickOnText("Rating");
		t.clickOnText("Notes");
		t.clickOnText("Anthology");
		t.clickOnText("Location of the book");
		t.clickOnText("Date started reading");
		t.clickOnText("Date finished reading");
		t.clickOnText("Format");
		t.clickOnText("Has this book been signed");
		t.clickOnText("Description");
		t.clickOnText("Genre");
		
		//goto book
		t.goBack();
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.goBack();
		t.assertCurrentActivity("Expected BookCatalogue activity", "BookCatalogue");
		t.clickOnText("Complete Robot");
		
		//check
		assertTrue("Author is not visible", t.searchText("Isaac Asimov", true));
		assertTrue("Title is not visible", t.searchText("Complete Robot", true));
		ImageView thumb = (ImageView) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.row_img);
		boolean thumb_visi = false; 
		if (thumb == null) {
			assertFalse(thumb_visi);
		} else {
			if (thumb.getVisibility() == 0) {
				thumb_visi = true;
			}
			assertFalse(thumb_visi);
		}
		
		assertFalse("ISBN is visible", t.searchText("9780586057247", true)); 
		assertFalse("Series is visible", t.searchText("Robot Series", true));
		assertFalse("Series # is visible", t.searchText("0.3", true));
		assertFalse("Publisher is visible", t.searchText("Harpercollins", true));
		assertFalse("Published Date is visible", t.searchText("Date Published", true));
		assertTrue("Bookshelf is not visible", t.searchText("Default, ", true));
		assertFalse("Pages is visible", t.searchText("688", true));
		assertFalse("List Price is visible", t.searchText("List Price", true));
		assertFalse("Format is visible", t.searchText("Paperback", true));
		assertFalse("Genre is visible", t.searchText("Science Fiction", true));
		assertFalse("Description is visible", t.searchText("stunning visions", true));
		assertFalse("Anthology is visible", t.searchText("Is this book an Anthology", true));
		
		t.clickOnText("Your Comments");
		assertFalse("Read is visible", t.searchText("Have you read this book?", true));
		assertFalse("Rating is visible", t.searchText("Rating", true));
		assertFalse("Notes is visible", t.searchText("Notes", true));
		assertFalse("Signedis visible", t.searchText("Has this book been signed", true));
		assertFalse("Date started is visible", t.searchText("Date started reading", true));
		assertFalse("Date finished is visible", t.searchText("Date finished reading", true));
		assertFalse("Location is visible", t.searchText("Location of the book", true));
	}
	
	public void test205AdminFieldVisibility() {
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Administration Functions");
		t.assertCurrentActivity("Expected AdministrationFunctions activity", "AdministrationFunctions");
		t.clickOnText("Manage Field Visibility");
		t.assertCurrentActivity("Expected FieldVisibility activity", "FieldVisibility");
		
		//set all as true (reset)
		//ArrayList<CheckBox> fields = t.getCurrentCheckBoxes();
		//for (int i = 0; i<fields.size(); i++) {
		//	CheckBox field = fields.get(i);
		//	if (!field.isChecked()) {
		//		t.clickOnCheckBox(i);
		//	}
		//}
		
		//set known fields (will need to be updated for each new field)
		t.scrollUp();
		t.scrollUp();
		t.scrollUp();
		t.scrollUp();
		t.clickOnText("Cover Thumbnail");
		t.clickOnText("ISBN");
		t.clickOnText("Series");
		t.clickOnText("#");
		t.clickOnText("Publisher");
		t.clickOnText("Date Published");
		t.clickOnText("Pages");
		t.clickOnText("List Price");
		t.clickOnText("Have you read this book?");
		t.clickOnText("Rating");
		t.clickOnText("Notes");
		t.clickOnText("Anthology");
		t.clickOnText("Location of the book");
		t.clickOnText("Date started reading");
		t.clickOnText("Date finished reading");
		t.clickOnText("Format");
		t.clickOnText("Has this book been signed");
		t.clickOnText("Description");
		t.clickOnText("Genre");
		
		//goto book
		t.goBack();
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.goBack();
		t.assertCurrentActivity("Expected BookCatalogue activity", "BookCatalogue");
		t.clickOnText("Complete Robot");
		
		//check
		assertTrue("Author is not visible", t.searchText("Isaac Asimov", true));
		assertTrue("Title is not visible", t.searchText("Complete Robot", true));
		ImageView thumb = (ImageView) t.getCurrentActivity().findViewById(com.eleybourn.bookcatalogue.R.id.row_img);
		boolean thumb_visi = false; 
		if (thumb == null) {
			assertFalse(thumb_visi);
		} else {
			if (thumb.getVisibility() == 0) {
				thumb_visi = true;
			}
			assertFalse(thumb_visi);
		}
		
		assertTrue("ISBN is not visible", t.searchText("9780586057247", true)); 
		assertTrue("Series is not visible", t.searchText("Robot Series", true));
		assertTrue("Series # is not visible", t.searchText("0.3", true));
		assertTrue("Publisher is not visible", t.searchText("Harpercollins", true));
		assertTrue("Published Date is not visible", t.searchText("Date Published", true));
		assertTrue("Bookshelf is not visible", t.searchText("Default, ", true));
		assertTrue("Pages is not visible", t.searchText("688", true));
		//assertTrue("List Price is not visible", t.searchText("List Price", true));
		assertTrue("Format is not visible", t.searchText("Paperback", true));
		assertTrue("Genre is not visible", t.searchText("Science Fiction", true));
		assertTrue("Description is not visible", t.searchText("stunning visions", true));
		assertTrue("Anthology is not visible", t.searchText("Anthology?", true));
		
		t.clickOnText("Your Comments");
		assertTrue("Read is not visible", t.searchText("Have you read this book?", true));
		assertTrue("Rating is not visible", t.searchText("Rating", true));
		//assertTrue("Notes is not visible", t.searchText("Notes", true));
		assertTrue("Signedis not visible", t.searchText("Has this book been signed", true));
		assertTrue("Date started is not visible", t.searchText("Date started reading", true));
		assertTrue("Date finished is not visible", t.searchText("Date finished reading", true));
		//assertTrue("Location is not visible", t.searchText("Location of the book", true));
	}
	
	/*public void test206AdminExport() {
		t.clickOnText("Nation");
		//check
		assertTrue("Author is not visible", t.searchText("Terry Pratchett", true));
		t.clickOnText("Nation");
		EditText title = t.getEditText(0);
		t.clearEditText(title);
		t.enterText(title, "'$5,\",\"\",\"'");
		t.clickOnText("Save Book");
		
		//delete export file
		String mFileName = Utils.EXTERNAL_FILE_PATH + "/export.csv";
		File export = new File(mFileName);
		export.delete();
		export = new File(mFileName);
		assertFalse("File was not deleted", export.exists());
		
		//export
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Administration Functions");
		t.assertCurrentActivity("Expected AdministrationFunctions activity", "AdministrationFunctions");
		t.clickOnText("Export Books");
		t.sleep(10000);
		export = new File(mFileName);
		assertTrue("File does not exist", export.exists());
	}*/
	
	public void test207AdminImport() {
		//delete book
		t.clickLongOnText("5");
		t.clickOnText("Delete Book");
		t.clickOnText("OK");
		assertFalse("Book was not deleted", t.searchText("5", true));
		
		//rename book
		t.clickOnText("Complete Robot, The");
		EditText title = t.getEditText(0);
		t.clearEditText(title);
		t.enterText(title, "Foobar");
		t.clickOnText("Save Book");
		assertTrue("Book was not renamed", t.searchText("Foobar", true));
		
		//import
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Administration Functions");
		t.assertCurrentActivity("Expected AdministrationFunctions activity", "AdministrationFunctions");
		t.clickOnText("Import Books");
		t.clickOnText("OK");
		t.sleep(10000);
		t.goBack();
		
		assertTrue("Book was not restored", t.searchText("Complete Robot, The", true));
		assertTrue("Book was not restored", t.searchText("5", true));
	}
	
	public void test208AdminAutoUpdate(){
		t.clickOnMenuItem("Add Book...");
		t.clickOnText("Add Book");
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		
		//add author
		t.clickOnText("Set Authors");
		EditText author = t.getEditText(0);
		t.enterText(author, "Fred Bloggs");
		t.clickOnText("Add");
		t.clickOnText("Save");
		
		//setup fields
		t.enterText(0, "Demonstorm");
		assertTrue("Title was not entered", t.searchText("Demonstorm"));
		t.enterText(1, "9780575073333");
		t.clickOnText("Add Book", 3);
		t.clickOnButton("Save Book");
		
		assertTrue("Expected populated author", t.searchText("Bloggs, Fred"));
		
		//automatically update
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Administration Functions");
		t.assertCurrentActivity("Expected AdministrationFunctions activity", "AdministrationFunctions");
		t.clickOnText("Automatically Update Fields");
		//leave everything default
		t.clickOnButton("Update");
		t.sleep(30000);
		t.goBack();
		assertTrue("Expected populated author", t.searchText("Barclay, James"));
	}
	
	public void test209AdminLinks() {
		t.clickOnMenuItem("Help & Admin");
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("Donate");
		t.assertCurrentActivity("Expected AdministrationDonate activity", "AdministrationDonate");
		
		assertTrue("Expected wishlist link", t.searchText("Amazon Wishlist", 2));
		
		//t.clickOnImage(0); //donate
		//t.sleep(3000);
		//t.goBack(); //expected to have gone somewhere. I don't know the WEB Activity name, so not checking
		//t.assertCurrentActivity("Expected AdministrationDonate activity", "AdministrationDonate"); // This will fail if we don't return from the webpage
		
		t.clickOnText("About this App");
		t.assertCurrentActivity("Expected AdministrationAbout activity", "AdministrationAbout");
		
		assertTrue("Expected wiki link", t.searchText("https://wiki.github.com/eleybourn/Book-Catalogue"));
		//t.clickOnText("https://wiki.github.com/eleybourn/Book-Catalogue");
		//t.sleep(3000);
		//t.goBack(); //expected to have gone somewhere. I don't know the WEB Activity name, so not checking
		//t.assertCurrentActivity("Expected AdministrationAbout activity", "AdministrationAbout"); // This will fail if we don't return from the webpage
		
		assertTrue("Expected source link", t.searchText("https://github.com/eleybourn/Book-Catalogue"));
		//t.clickOnText("https://github.com/eleybourn/Book-Catalogue");
		//t.sleep(3000);
		//t.goBack(); //expected to have gone somewhere. I don't know the WEB Activity name, so not checking
		//t.assertCurrentActivity("Expected AdministrationAbout activity", "AdministrationAbout"); // This will fail if we don't return from the webpage
		
		assertTrue("Expected email link", t.searchText("eleybourn@gmail.com"));
		//t.clickOnText("eleybourn@gmail.com");
		//t.sleep(3000);
		//t.goBack(); //expected to have gone somewhere. I don't know the EMAIL Activity name, so not checking
		//t.assertCurrentActivity("Expected AdministrationAbout activity", "AdministrationAbout"); // This will fail if we don't return from the webpage
	}

	
	@Override	
	public void tearDown() throws Exception {	
		try {
			t.finalize();
		} catch (Throwable 	e) {
			e.printStackTrace();
		}
		getActivity().finish();
		super.tearDown();
	}

}
