package com.eleybourn.bookcatalogue.bcservices;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.BookEditFields;
import com.eleybourn.bookcatalogue.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Class for API calls to Book Catalogue web
 */
public class BcBooksApi {
    // URL strings
    private static final String LOGIN_URL = "https://book-catalogue.com/api/login";
    private static final String POST_URL = "https://book-catalogue.com/api/books";
    private static final String GET_URL = "https://book-catalogue.com/api/books/";
    private static final String DELETE = "https://book-catalogue.com/api/books/";

    // JSON fields for book details
    private static final String ISBN = "isbn";
    private static final String TITLE = "title";
    private static final String DATE_PUBLISHED = "date_published";
    private static final String PAGES = "pages";
    private static final String FORMAT = "format";
    private static final String DESCRIPTION = "description";
    private static final String LANGUAGE = "language";
    private static final String GENRE = "genre";
    private static final String PUBLISHER = "publisher";
    private static final String THUMBNAIL = "thumbnail";
    private static final String BOOK_ID = "book_id";
    private static final String BOOK_UUID = "book_uuid";
    private static final String TO_BC_AUTHORS = "authors[]";
    private static final String AUTHORS = "authors";
    private static final String RATING = "rating";
    private static final String READ = "read";
    private static final String SERIES = "series";
    private static final String TO_BC_SERIES = "series[]";
    private static final String NOTES = "notes";
    private static final String BOOKSHELVES = "bookshelves";
    private static final String TO_BC_BOOKSHELVES = "bookshelves[]";
    private static final String LIST_PRICE = "list_price";
    private static final String LOCATION = "location";
    private static final String READ_START = "read_start";
    private static final String READ_END = "read_end";
    private static final String SIGNED = "signed";
    private static final String LOANS = "loans";
    private static final String LOANED_TO = "loaned_to";
    private static final String GR_ID = "goodreads_book_id";
    private static final String GR_LAST_SYNC_DATE = "last_goodreads_sync_date";
    private static final String DATE_ADDED = "date_added";
    private static final String ANTHOLOGY = "anthology";
    private static final String ANTHOLOGIES = "anthologies[]";
    private static final String ANTHOLOGY_AUTHORS = "anthology_authors[]";
    private static final String URL_PATH = "url_path";
    private static final String UNKNOWN = BookCatalogueApp.getResourceString(R.string.unknown);

    // TODO - store these elsewhere
    private static String apiToken;
    private static String emailToken;

    private static int numFailedBackup;

    private static CatalogueDBAdapter mDbHelper;


    public static void login() {
        String urlString = LOGIN_URL;
        List<ParamsPair> loginBody = new ArrayList<>();
        //TODO - get email address that is stored
        loginBody.add(new ParamsPair("email", "some.email@gmail.com"));
        BcService.waitUntilRequestAllowed();
        JSONObject response = BcService.makeServiceCall(urlString, Methods.Post, loginBody).get(0);
        try {
            // TODO - don't store in static memory - write to somewhere else.
            apiToken = response.getString("api_token");
            emailToken = response.getString("email_token");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // TODO - update once tokens are stored elsewhere
    public static String getApiToken() {
        return apiToken;
    }

    /**
     * Iterates over all books to be backed up to web service.
     * @return Number of books that could not be backed up.
     */
    public static int retrieveBooksForPosting() {
        mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
        mDbHelper.open();
        BooksCursor books = null;
        numFailedBackup = 0;
        try {
            books = mDbHelper.fetchAllBooks("", "", "", "", "", "", "");
            if (books.moveToFirst()) {
                do {
                    postBook(books);
                } while (books.moveToNext());
            }
        } catch (Exception e) {
            Logger.logError(e);
        } finally {
            if (books != null)
                books.close();
        }
        mDbHelper.close();
        return numFailedBackup;
    }

// TODO - occasionally will create a new entry in web service despite an identical entry already existing.
    public static void postBook(BooksCursor booksCursor) {
        String urlString = POST_URL;
        List<ParamsPair> bookInfo = new ArrayList<>();
        // Try to get the mandatory fields
        // If unsuccessful, update count of number of books unable to be uploaded and return
        BooksRowView rowView = booksCursor.getRowView();
        long id;
        String uuid;
        try {
            id = rowView.getId();
            bookInfo.add(new ParamsPair(BOOK_ID, String.valueOf(id)));
            uuid = rowView.getBookUuid();
            bookInfo.add(new ParamsPair(BOOK_UUID, uuid));
            String title = rowView.getTitle();
            // Sanity check: ensure title is non-blank. This has not happened yet, but we
            // know it does for author, so completeness suggests making sure all 'required'
            // fields are non-blank.
            if (title == null || title.trim().equals("")) {
                title = UNKNOWN;
            }
            bookInfo.add(new ParamsPair(TITLE, title));
        } catch (RuntimeException re) {
            Logger.logError(re);
            numFailedBackup++;
            return;
        }

        // Now try to get all other fields
        try {
            ArrayList<Author> authors = mDbHelper.getBookAuthorList(id);
            for(Author author : authors) {
                String familyName = author.familyName;
                String givenNames = author.givenNames;
                //Ensure author is not blank - this can happen, probably due to constraint failures.
                if(familyName == null && givenNames == null) {
                    familyName = "AUTHOR";
                    givenNames = "UNKNOWN";
                } else if(familyName == null) { //For mononymous authors, set name as family so it is shown before the comma.
                    familyName = givenNames;
                    givenNames = "";
                } else if(givenNames == null) {
                    givenNames = "";
                }
                bookInfo.add(new ParamsPair(TO_BC_AUTHORS, String.format("%d, %s, %s", author.id, familyName, givenNames)));
            }
        } catch (RuntimeException re) {}

        try {
            String isbn = rowView.getIsbn();
            if(isbn != null || !isbn.trim().equals("")) {
                bookInfo.add(new ParamsPair(ISBN, isbn));
            }
        } catch (RuntimeException re) {}

        try {
            String publisher = rowView.getPublisher();
            if(publisher != null || !publisher.trim().equals("")) {
                bookInfo.add(new ParamsPair(PUBLISHER, publisher));
            }
        } catch (RuntimeException re) {}

        try {
            String datePublished = rowView.getDatePublished();
            if(datePublished != null || !datePublished.trim().equals("")) {
                bookInfo.add(new ParamsPair(DATE_PUBLISHED, datePublished));
            }
        } catch (RuntimeException re) {}

        try {
            bookInfo.add(new ParamsPair(RATING, String.valueOf(rowView.getRating())));
        } catch (RuntimeException re) {}

        Cursor shelves = mDbHelper.fetchAllBookshelvesByBook(id);
        try {
            shelves.moveToFirst();
            do {
                int shelfId = shelves.getInt(0);
                String shelfName = shelves.getString(1);
                bookInfo.add(new ParamsPair(TO_BC_BOOKSHELVES, String.format("%d, %s", shelfId, shelfName)));
            } while (shelves.moveToNext());
        } catch (RuntimeException re) {
        } finally {
            shelves.close();
        }

        try {
            bookInfo.add(new ParamsPair(READ, String.valueOf(rowView.getRead())));
        } catch (RuntimeException re) {}

        try {
            ArrayList<Series> series = mDbHelper.getBookSeriesList(id);
            for(Series s : series) {
                bookInfo.add(new ParamsPair(TO_BC_SERIES, String.format("%d, %s, %s", s.id, s.num, s.name)));
            }
        } catch (RuntimeException re) {}

        try {
            String pages = booksCursor.getString(booksCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES));
            if(pages != null || !pages.trim().equals("")) {
                bookInfo.add(new ParamsPair(PAGES, pages));
            }
        } catch (RuntimeException re) {}

        try {
            String notes = rowView.getNotes();
            if(notes != null || !notes.trim().equals("")) {
                bookInfo.add(new ParamsPair(NOTES, notes));
            }
        } catch (RuntimeException re) {}

        try {
            String listPrice = (booksCursor.getString(booksCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE)));
            if(listPrice != null || !listPrice.trim().equals("")) {
                bookInfo.add(new ParamsPair(LIST_PRICE, listPrice));
            }
        } catch (RuntimeException re) {}

        try {
            String location = rowView.getLocation();
            if(location != null || !location.trim().equals("")) {
                bookInfo.add(new ParamsPair(LOCATION, location));
            }
        } catch (RuntimeException re) {}

        try {
            String readStart = booksCursor.getString(booksCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START));
            if(readStart != null || !readStart.trim().equals("")) {
                bookInfo.add(new ParamsPair(READ_START, readStart));
            }
        } catch (RuntimeException re) {}

        try {
            String readEnd = rowView.getReadEnd();
            if(readEnd != null || !readEnd.trim().equals("")) {
                bookInfo.add(new ParamsPair(READ_END, readEnd));
            }
        } catch (RuntimeException re) {}

        try {
            String format = booksCursor.getString(booksCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT));
            if(format != null || !format.trim().equals("")) {
                bookInfo.add(new ParamsPair(FORMAT, format));
            }
        } catch (RuntimeException re) {}

        try {
            bookInfo.add(new ParamsPair(SIGNED, String.valueOf(rowView.getSigned())));
        } catch (RuntimeException re) {}

        // TODO - this will need altering if loaned_to is changed to only take loanee's name.
        try {
            String loaned = mDbHelper.fetchLoanByBook(id);
            long loanId = mDbHelper.fetchLoanIdByBook(id);
            if(loaned != null || !loaned.trim().equals("")) {
                bookInfo.add(new ParamsPair(LOANED_TO, String.format("%d, %s", loanId, loaned)));
            }
        } catch (RuntimeException re) {}

        try {
            int anthology = booksCursor.getInt(booksCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK));
            if (anthology != 0) {
                Cursor titles = mDbHelper.fetchAnthologyTitlesByBook(id);
                try {
                    if (titles.moveToFirst()) {
                        do {
                            String anthId = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
                            String anthTitle = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
                            String anthAuthorId = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_ID));
                            String anthAuthor = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_NAME));
                            bookInfo.add(new ParamsPair(ANTHOLOGY, String.valueOf(anthology)));
                            if(anthId != null || !anthId.trim().equals("")) {
                                if(anthTitle == null || anthTitle.trim().equals("")) {
                                    anthTitle = UNKNOWN;
                                }
                                bookInfo.add(new ParamsPair(ANTHOLOGIES, String.format("%s, %s", anthId, anthTitle)));
                            }
                            if(anthAuthorId != null || !anthAuthorId.trim().equals("")) {
                                if(anthAuthor == null || anthAuthor.trim().equals("")) {
                                    anthAuthor = UNKNOWN;
                                }
                                bookInfo.add(new ParamsPair(ANTHOLOGY_AUTHORS, String.format("%s, %s", anthAuthorId, anthAuthor)));
                            }
                        } while (titles.moveToNext());
                    }
                } finally {
                    if (titles != null)
                        titles.close();
                }
            }
        } catch (RuntimeException re) {}

        // TODO - description gets cut off at 256 characters, need to address this (maybe due to server side limitation?)
        try {
            String description = rowView.getDescription();
            if(description != null || !description.trim().equals("")) {
                bookInfo.add(new ParamsPair(DESCRIPTION, description));
            }
        } catch (RuntimeException re) {}

        try {
            String genre = rowView.getGenre();
            if(genre != null || !genre.trim().equals("")) {
                bookInfo.add(new ParamsPair(GENRE, genre));
            }
        } catch (RuntimeException re) {}

        try {
            String language = rowView.getLanguage();
            if(language != null || !language.trim().equals("")) {
                bookInfo.add(new ParamsPair(LANGUAGE, language));
            }
        } catch (RuntimeException re) {}

        try {
            String dateAdded = booksCursor.getString(booksCursor.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_ADDED));
            if(dateAdded != null || !dateAdded.trim().equals("")) {
                bookInfo.add(new ParamsPair(DATE_ADDED, dateAdded));
            }
        } catch (RuntimeException re) {}

        try {
            long grId = rowView.getGoodreadsBookId();
            if(grId > 0) {
                bookInfo.add(new ParamsPair(GR_ID, String.valueOf(grId)));
            }
        } catch (RuntimeException re) {}

        try {
            String lastGrSync = booksCursor.getString(booksCursor.getColumnIndexOrThrow(DOM_LAST_GOODREADS_SYNC_DATE.name));
            if(lastGrSync != null && !lastGrSync.trim().equals("0000-00-00")) {
                bookInfo.add(new ParamsPair(GR_LAST_SYNC_DATE, lastGrSync));
            }
        } catch (RuntimeException re) {}

        // Get the thumbnail file file path
        File coverFile = CatalogueDBAdapter.fetchThumbnailByUuid(uuid);
        try {
            String coverPath = coverFile.getCanonicalPath();
            bookInfo.add(new ParamsPair(THUMBNAIL, coverPath));
        } catch (IOException e) {}
        BcService.waitUntilRequestAllowed();
        JSONObject bookRecord = BcService.makeServiceCall(urlString, Methods.Post, bookInfo).get(0);
    }

    /**
     * Retrieves all books stored in user's web service account.
     */
    public static void getAllBooks() {
        String urlString = GET_URL;
        BcService.waitUntilRequestAllowed();
        List<JSONObject> booksList = BcService.makeServiceCall(urlString, Methods.Get, new ArrayList<>());
        mDbHelper = new CatalogueDBAdapter(BookCatalogueApp.context);
        mDbHelper.open();
        for(JSONObject bookJson : booksList) {
            long id = 0L;
            ArrayList<String> isbnArray =  new ArrayList<>();
            try {
                id = bookJson.getLong("bcid");
                String isbn = bookJson.getString(ISBN);
                isbnArray.add(isbn);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // See if this book exists in our database
            BooksCursor c = mDbHelper.fetchBookById(id);
            try {
                boolean found = c.moveToFirst();
                if (!found) {
                    // Not found by Book Catalogue id, try ISBN
                    c.close();
                    c = null;

                    c = mDbHelper.fetchBooksByIsbns(isbnArray);
                    found = c.moveToFirst();
                }
                if (found) {
                    // If found, update books
                    BooksRowView rv = c.getRowView();
                    do {
                        updateBook(rv, bookJson);
                    } while (c.moveToNext());
                } else {
                    // Create the book
                    createBook(bookJson);
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
        mDbHelper.close();
    }

    /**
     * Update the book using the JSON data
     *
     * @param rv
     * @param json
     */
    private static void updateBook(BooksRowView rv, JSONObject json) {
        // We build a new book bundle each time since it will build on the existing
        // data for the given book, not just replace it.
        BookData book = buildBundle(rv, json);
        mDbHelper.updateBook(rv.getId(), book, CatalogueDBAdapter.BOOK_UPDATE_SKIP_PURGE_REFERENCES|CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
        book.putString(CatalogueDBAdapter.KEY_ROWID, String.valueOf(rv.getId()));
        if (book.getString(CatalogueDBAdapter.KEY_LOANED_TO) != null) {
            mDbHelper.deleteLoan(rv.getId(), false);
            mDbHelper.createLoan(book, false);
        }
    }

    /**
     * Create a new book
     *
     * @param json
     */
    private static void createBook(JSONObject json) {
        BookData book = buildBundle(null, json);
        long id = mDbHelper.createBook(book, CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
        book.putString(CatalogueDBAdapter.KEY_ROWID, String.valueOf(id));
        if (book.getString(CatalogueDBAdapter.KEY_LOANED_TO) != null) {
            mDbHelper.createLoan(book, false);
        }
        if (book.getBoolean(CatalogueDBAdapter.KEY_THUMBNAIL)) {
            String uuid = mDbHelper.getBookUuid(id);
            File thumb = CatalogueDBAdapter.getTempThumbnail();
            File real = CatalogueDBAdapter.fetchThumbnailByUuid(uuid);
            thumb.renameTo(real);
        }
    }

    /**
     * Build a book bundle based on the JSON data pulled from web service. Some data is just copied
     * while other data is processed (eg. dates) and other are combined (authors & series).
     *
     * @param rv
     * @param json
     * @return
     */
    private static BookData buildBundle(BooksRowView rv, JSONObject json) {
        BookData book = new BookData();

        addStringIfNonBlank(json, TITLE, book, TITLE);
        addStringIfNonBlank(json, DESCRIPTION, book, DESCRIPTION);
        addStringIfNonBlank(json, FORMAT, book, FORMAT);
        addStringIfNonBlank(json, NOTES, book, NOTES);
        addStringIfNonBlank(json, GENRE, book, GENRE);
        addStringIfNonBlank(json, LANGUAGE, book, LANGUAGE);
        addStringIfNonBlank(json, ISBN, book, ISBN);
        addLongIfPresent(json, PAGES, book, PAGES);
        addStringIfNonBlank(json, PUBLISHER, book, PUBLISHER);
        addDateIfValid(json, DATE_PUBLISHED, book, DATE_PUBLISHED);
        addLongIfPresent(json, GR_ID, book, GR_ID);
        addStringIfNonBlank(json, LIST_PRICE, book, LIST_PRICE);
        addStringIfNonBlank(json, LOCATION, book, LOCATION);
        addDateIfValid(json, DATE_ADDED, book, DATE_ADDED);
        addDateIfValid(json, GR_LAST_SYNC_DATE, book, GR_LAST_SYNC_DATE);

        Double rating = addDoubleIfPresent(json, RATING, book, RATING);
        addDateIfValid(json, READ_START, book, READ_START);
        String readEnd = addDateIfValid(json, READ_END, book, READ_END);
        // If it has a rating or a 'read_end' date, assume it's read. If these are missing then
        // DO NOT overwrite existing data since it *may* be read even without these fields.
        if ((rating != null && rating > 0) || (readEnd != null && readEnd.length() > 0)) {
            book.putBoolean(CatalogueDBAdapter.KEY_READ, true);
        }

        // Process any loans
        try {
            JSONArray loanArray = json.getJSONArray(LOANS);
            if (loanArray.length() > 0) {
                JSONObject loan = loanArray.getJSONObject(0);
                book.putString(CatalogueDBAdapter.KEY_LOANED_TO, loan.getString(LOANED_TO));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Process if signed
        try {
            boolean signed = json.getInt(SIGNED) == 1;
            book.putBoolean(CatalogueDBAdapter.KEY_SIGNED, signed);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Process any thumbnails
        String thumbnailUrl;
        try {
            thumbnailUrl = "https://book-catalogue.com/" + json.getString(URL_PATH);
            String filename = Utils.saveThumbnailFromUrl(thumbnailUrl, "_BC");
            if (filename.length() > 0)
                book.appendOrAdd( "__thumbnail", filename);
            book.cleanupThumbnails();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Process any authors
        ArrayList<Author> authors;
        if (rv == null) {
            authors = new ArrayList<>();
            try {
                JSONArray bcAuthors = json.getJSONArray(AUTHORS);
                for (int i = 0; i < bcAuthors.length(); i++) {
                    JSONObject author = bcAuthors.getJSONObject(i);
                    authors.add(new Author(author.getLong("id"), author.getString("family_name"), author.getString("given_names")));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            authors = mDbHelper.getBookAuthorList(rv.getId());
        }
        book.putSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, authors);

        // Process any series
        ArrayList<Series> allSeries;
        if (rv == null) {
            allSeries = new ArrayList<>();
            try {
                JSONArray bcSeries = json.getJSONArray(SERIES);
                for (int i = 0; i < bcSeries.length(); i++) {
                    JSONObject series = bcSeries.getJSONObject(i);
                    allSeries.add(new Series(series.getString("series_name"), series.getString("series_num")));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            allSeries = mDbHelper.getBookSeriesList(rv.getId());
        }
        Utils.pruneSeriesList(allSeries);
        book.putSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY, allSeries);

        // Process any bookshelves
        String bookshelves = null;
        try {
            JSONArray bcShelves = json.getJSONArray(BOOKSHELVES);
            for (int i = 0; i < bcShelves.length(); i++) {
                JSONObject shelf = bcShelves.getJSONObject(i);
                String shelfName = shelf.getString("bookshelf");
                if (shelfName != null && !shelfName.equals("")) {
                    shelfName = Utils.encodeListItem(shelfName, BookEditFields.BOOKSHELF_SEPERATOR);
                    if (bookshelves == null) {
                        bookshelves = shelfName;
                    } else {
                        bookshelves += BookEditFields.BOOKSHELF_SEPERATOR + shelfName;
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (bookshelves != null && bookshelves.length() > 0) {
            book.setBookshelfList(bookshelves);
        }

        // Process if part of an anthology
        // TODO - process anthology

        Date now = new Date();
        book.putString(DOM_LAST_UPDATE_DATE.name, Utils.toSqlDateTime(now));

        return book;
    }

    /**
     * Utility to copy a non-blank and valid date string to the book bundle; will
     * attempt to translate as appropriate and will not add the date if it cannot
     * be parsed.
     *
     * @param source
     * @param sourceField
     * @param dest
     * @param destField
     *
     * @Return reformatted sql date, or null if not able to parse
     */
    private static String addDateIfValid(JSONObject source, String sourceField, BookData dest, String destField) {
        if (!source.has(sourceField))
            return null;

        String val = null;
        try {
            val = source.getString(sourceField);
        } catch (JSONException e) {}
        if (val == null || val.equals("null") || val.equals(""))
            return null;

        if (val.length() >= 10) {
            Date d = Utils.parseDate(val);
            if (d == null)
                return null;

            val = Utils.toSqlDateTime(d);
        }
        dest.putString(destField, val);
        return val;
    }

    /**
     * Utility to copy a non-blank string to the book bundle.
     *  @param source
     * @param sourceField
     * @param dest
     * @param destField
     */
    private static void addStringIfNonBlank(JSONObject source, String sourceField, BookData dest, String destField) {
        if (source.has(sourceField)) {
            String val = null;
            try {
                val = source.getString(sourceField);
            } catch (JSONException e) {}
            if (val != null && !val.equals("") && !val.equals("null")) {
                dest.putString(destField, val);
            }
        }
    }

    /**
     * Utility to copy a Long value to the book bundle.
     *
     * @param source
     * @param sourceField
     * @param dest
     * @param destField
     */
    private static void addLongIfPresent(JSONObject source, String sourceField, BookData dest, String destField) {
        if (source.has(sourceField)) {
            long val = 0L;
            try {
                val = source.getLong(sourceField);
            } catch (JSONException e) {}
            dest.putLong(destField, val);
        }
    }

    /**
     * Utility to copy a Double value to the book bundle.
     *
     * @param source
     * @param sourceField
     * @param dest
     * @param destField
     */
    private static Double addDoubleIfPresent(JSONObject source, String sourceField, BookData dest, String destField) {
        if (source.has(sourceField)) {
            double val = 0;
            try {
                val = source.getDouble(sourceField);
            } catch (JSONException e) {}
            dest.putDouble(destField, val);
            return val;
        } else {
            return null;
        }
    }

    /**
     * API to delete a book stored in the user's web service account.
     * @param bcId
     */
    // TODO - currently not called from anywhere.
    public static void delete(int bcId) {
        String urlString = DELETE;
        urlString += String.valueOf(bcId);
        BcService.waitUntilRequestAllowed();
        JSONObject deletionConfirmation = BcService.makeServiceCall(urlString, Methods.Delete, new ArrayList<>()).get(0);
    }
}
