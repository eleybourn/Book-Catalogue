package com.eleybourn.bookcatalogue;

import java.io.File;
import java.util.ArrayList;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


/**
 * Activity to show search results in the list. 
 * Used if user want to show search results in the list (only for results of searching by author and title).
 * 
 * @author novacej
 */
public class SearchResultList extends ListActivity{
	    
    private static final int CREATE_BOOK = 1;
    private static final int size = 100;
    
	private ImageView t_image;
	private TextView t_title;
	private TextView t_author;
	private TextView t_publisher;    
    
    private ArrayList<Book> m_bookList;
   
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_books);  
        // change label of screen    
        TextView t_label = (TextView) findViewById(R.id.bookshelf_label);
        t_label.setText(getResources().getString(R.string.search_title));
        // try to get a list of books from bundle
        Bundle extras = getIntent().getExtras();
        m_bookList = new ArrayList<Book>();
        m_bookList = (ArrayList<Book>) ((Bundle)extras.getParcelable("bookData")).getSerializable(CatalogueDBAdapter.KEY_BOOKLIST);       
        // if there are some books, display a list
        if(m_bookList != null){
        	setListAdapter(new BookListAdapter(SearchResultList.this, R.layout.row_books, m_bookList));
        }else{
        	// should not happen
			setResult(RESULT_CANCELED);
			finish();
        }        
    }  
    
    /**
     * Overridden method. If user click on some book, will be displayed its detail.
     * 
     */     
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {    	  	
    	Bundle bookData = new Bundle();    	
    	fillUpBundle(bookData, m_bookList.get(position));    	
		createBook(bookData);
    }               

    /**
     * Our implementation of ListAdapter.
     * 
     */    
    private class BookListAdapter extends ArrayAdapter<Book> {

    	// list of books
        private ArrayList<Book> items;       

        /**
         * Constructor with parameters.
         * 
         * @param context
         * @param textViewResourceId
         * @param items		List of books
         */  
        public BookListAdapter(Context context, int textViewResourceId, ArrayList<Book> items) {
                super(context, textViewResourceId, items);
                this.items = items;
        }

        /**
         * Overridden method. For all books will be displayed thumbnail, author, title and publisher.
         * 
         * @param position
         * @param convertView
         * @param parent
         */         
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
               View v = convertView;
                if (v == null) {
                    LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.row_books, null);
                }
                // current book
                Book b = items.get(position);
                // if current book is not null, display its parameters
                if (b != null) {
                		t_image = (ImageView) v.findViewById(R.id.row_image_view);
                        t_title = (TextView) v.findViewById(R.id.row_title);
                        t_author = (TextView) v.findViewById(R.id.row_author);
                        t_publisher = (TextView) v.findViewById(R.id.row_publisher);                         
                        String tmp = "";                                                
                        if(b.getTHUMBNAIL() == null || b.getTHUMBNAIL().length() == 0){
                        	t_image.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_menu_help));
                        }else{                                              	
	                        CatalogueDBAdapter.fetchThumbnailIntoListImageView(b.getTHUMBNAIL(), t_image, size, size, true);
                        }
	                    t_image.setVisibility(View.VISIBLE);                                                                        
                    	if(b.getAUTHOR_ARRAY().size() == 0){
                    		tmp = getResources().getString(R.string.unknown);
                    	}else{
                    		tmp = b.getAUTHOR_ARRAY().get(0).getDisplayName();
                    		if(b.getAUTHOR_ARRAY().size() > 1)
                    			tmp += " " + getResources().getString(R.string.and_others);
                    	}
                    	t_author.setText(tmp);                    	
                    	t_title.setText(b.getTITLE().equals("") ? getResources().getString(R.string.unknown) : b.getTITLE());
                    	t_publisher.setText(b.getPUBLISHER().equals("") ? getResources().getString(R.string.unknown) : b.getPUBLISHER());                         
                }
                return v;        	        
        }    	
    }
    
	/**
	 * Fill up given bundle with given book.
	 *
	 *@param b		Bundle
	 *@param book	Book
	 */     
    private void fillUpBundle(Bundle b, Book book){ 
    	// put all parameters of given book to bundle
    	b.putString(CatalogueDBAdapter.KEY_TITLE, book.getTITLE());
    	b.putString(CatalogueDBAdapter.KEY_PUBLISHER, book.getPUBLISHER());
    	b.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, book.getDATE_PUBLISHED());
    	b.putString(CatalogueDBAdapter.KEY_SERIES_NAME, book.getSERIES_NAME());
    	b.putString(CatalogueDBAdapter.KEY_ISBN, book.getISBN());
    	b.putString(CatalogueDBAdapter.KEY_PAGES, book.getPAGES());
    	b.putString(CatalogueDBAdapter.KEY_DESCRIPTION, book.getDESCRIPTION());
    	b.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, book.getAUTHOR_ARRAY());
    	b.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, book.getSERIES_ARRAY()); 
    	// delete old temp thumbnail
    	Utils.deleteTempThumbnail();
    	// if given book has thumbnail, make temp thumbnail
    	if(book.getTHUMBNAIL() != null && book.getTHUMBNAIL().length() > 0){
    		Utils.makeTempThumbnail(book.getTHUMBNAIL());
    		b.putBoolean(CatalogueDBAdapter.KEY_THUMBNAIL, true);
    	}    	
    }  
    
	/**
	 * Load the BookEdit Activity
	 *
	 *@param bookData
	 */    
	private void createBook(Bundle bookData) {		
		Intent i = new Intent(this, BookEdit.class);				
		i.putExtra("bookData", bookData);
		startActivityForResult(i, CREATE_BOOK);		
	}    
    
    /**
     * Overridden method. If new book was created, all thumbnails will be deleted and this activity will finish.
     * 
     * @param requestCode
     * @param resultCode
     * @param intent
     */  	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);		
		if(requestCode == CREATE_BOOK){
			if(resultCode == RESULT_OK){				
				for(Book b : m_bookList){
					File f = new File(b.getTHUMBNAIL());
					f.delete();
				}				
				setResult(RESULT_OK);
				finish();								
			}				
		}
	}
	
    /**
     * Overridden method. If back button was used, delete all thumbnails.
     * 
     * @param keyCode
     * @param event
     */  	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			for(Book b : m_bookList){
				File f = new File(b.getTHUMBNAIL());
				f.delete();
			}
	    }
	    return super.onKeyDown(keyCode, event);
	}	   
}

