/**
 * 
 */
package com.eleybourn.bookcatalogue.test;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.widget.EditText;

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
	
	public void test000LibraryThing() {
		//reset message for LT
		t.clickOnMenuItem("Help & Admin", true);
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("LibraryThing");
		t.clickOnEditText(0); //Dev Key
		t.goBack();
		t.clearEditText(t.getEditText(0));
		t.clickOnText("Save");
		t.clickOnText("Reset");
		t.goBack();
		
		//Disable the message
		t.clickOnMenuItem("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		t.clickOnText("Disable Message"); // This will fail if it does not exist
		t.goBack();
		
		//Check it has been disabled
		t.clickOnMenuItem("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		assertFalse("Did not expect cancel button", t.searchButton("Cancel"));
		t.goBack();
		
		//reset again
		t.clickOnMenuItem("Help & Admin", true);
		t.assertCurrentActivity("Expected Administration activity", "Administration");
		t.clickOnText("LibraryThing");
		t.clickOnEditText(0); //Dev Key
		t.goBack();
		t.clearEditText(t.getEditText(0));
		t.clickOnText("Save");
		t.clickOnText("Reset");
		t.goBack();
		
		//Test Cancel Button
		t.clickOnMenuItem("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		t.clickOnText("Cancel"); // This will fail if it does not exist
		t.goBack();
		
		//Test Adding Dev Key
		t.clickOnMenuItem("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		t.clickOnText("More Info"); // This will fail if it does not exist
		t.assertCurrentActivity("Expected AdministrationLibraryThing activity", "AdministrationLibraryThing");
		t.clickOnEditText(0); //Dev Key
		t.goBack();
		t.enterText(0, "118983f247fc3fe43dcafcd042655440");
		t.sleep(1000);
		t.clickOnText("Save");
		t.clickOnText("Reset");
		t.goBack();
		t.goBack();
		
		//Check it has been disabled (the key has been added)
		t.clickOnMenuItem("Add by ISBN");
		t.assertCurrentActivity("Expected BookISBNSearch activity", "BookISBNSearch");
		assertFalse("Did not expect cancel button", t.searchButton("Cancel"));
		t.goBack();
	}
	
	public void test001AddByISBNNumbers() {
		t.clickOnMenuItem("Add by ISBN");
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
		//TODO: FIX solo.clickOnText("1");
		//solo.clickOnImageButton(0);
		assertEquals("The ISBN string was different than expected", "123456789X0", isbn.getText().toString());
	}
	
	public void test002AddByISBN(){
		t.clickOnMenuItem("Add by ISBN");
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
	
	public void test003AddByISBNLandscape() {
		t.clickOnMenuItem("Add by ISBN");
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
		t.sleep(1000);
		//TODO: FIX solo.clickOnText("1");
		//solo.clickOnImageButton(0);
		assertEquals("The ISBN string was different than expected", "123456789X0", isbn.getText().toString());
		assertTrue("Expected search button", t.searchButton("Search"));
	}
	
	public void test004Anthology() {
		t.clickOnText("Complete Robot, The");
		
		t.assertCurrentActivity("Expected BookEdit activity", "BookEdit");
		assertTrue("Expected book title", t.searchText("Complete Robot, The"));
		t.searchText("Is this book an Anthology?");
		t.clickOnCheckBox(0);
		assertTrue("Expected anthology tab to appear", t.searchText("Anthology Titles"));
		t.clickOnCheckBox(0);
		assertFalse("Expected anthology tab to disappear", t.searchText("Anthology Titles"));
		t.clickOnCheckBox(0);
		assertTrue("Expected anthology tab to appear", t.searchText("Anthology Titles"));
		
		t.clickOnText("Anthology Titles");
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
