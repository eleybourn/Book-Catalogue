package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.Fields.FieldFormatter;
import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.Date;

/**
 * Class for representing read-only book details.
 *
 * @author n.silin
 */
public class BookDetails extends BookAbstract {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Tracker.enterOnCreateView(this);
        final View rootView = inflater.inflate(R.layout.book_details, container, false);
        Tracker.exitOnCreateView(this);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /* In superclass onCreate method we initialize fields, background,
         * display metrics and other. So see superclass onCreate method. */

        // Set additional (non book details) fields before their populating
        addFields();

        /*
         * We have to override this value to initialize book thumb with right size.
         * You have to see in book_details.xml to get dividing coefficient
         */
        mThumbEditSize = Math.min(mMetrics.widthPixels, mMetrics.heightPixels) / 3;

        if (savedInstanceState == null) {
            HintManager.displayHint(getActivity(), R.string.hint_view_only_help, null, null);
        }

        // Just format a binary value as yes/no/blank
        mFields.getField(R.id.field_signed).formatter = new BinaryYesNoEmptyFormatter();
    }

    @Override
    /* The only difference from super class method is initializing of additional
     * fields needed for read-only mode (user notes, loaned, etc.) */
    protected void populateFieldsFromBook(BookData book) {
        try {
            //BookData book = mEditManager.getBookData();
            populateBookDetailsFields(book);
            // Set maximum aspect ratio width : height = 1 : 2
            setBookThumbnail(book.getRowId(), mThumbEditSize, mThumbEditSize * 2);

            // Additional fields for read-only mode which are not initialized automatically
            showReadStatus(book);
            // XXX: Use the data!
            showLoanedInfo(book.getRowId());
            showSignedStatus(book);
            formatFormatSection(book);
            formatPublishingSection(book);

            // Restore default visibility and hide unused/unwanted and empty fields
            showHideFields(true);

            // Hide the fields that we never use...
            assert getView() != null;
            getView().findViewById(R.id.field_anthology).setVisibility(View.GONE);

        } catch (Exception e) {
            Logger.logError(e);
        }

        // Populate bookshelves and hide the field if bookshelves are not set.
        if (!populateBookshelvesField(mFields, book)) {
            assert getView() != null;
            getView().findViewById(R.id.lbl_bookshelves).setVisibility(View.GONE);
            //getView().findViewById(R.id.bookshelf_text).setVisibility(View.GONE);
        }

    }

    @Override
    /* Override populating author field. Hide the field if author not set or
     * shows author (or authors through ',') with 'by' at the beginning. */
    protected void populateAuthorListField() {
        ArrayList<Author> authors = mEditManager.getBookData().getAuthorList();
        int authorsCount = authors.size();
        if (authorsCount == 0) {
            // Hide author field if it is not set
            assert getView() != null;
            getView().findViewById(R.id.field_author).setVisibility(View.GONE);
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(getResources().getString(R.string.book_details_readonly_by));
            builder.append(" ");
            for (int i = 0; i < authorsCount; i++) {
                builder.append(authors.get(i).getDisplayName());
                if (i != authorsCount - 1) {
                    builder.append(", ");
                }
            }
            mFields.getField(R.id.field_author).setValue(builder.toString());
        }
    }

    @Override
    protected void populateSeriesListField() {
        ArrayList<Series> series = mEditManager.getBookData().getSeriesList();

        int size;
        try {
            size = series.size();
        } catch (NullPointerException e) {
            size = 0;
        }
        assert getView() != null;
        if (size == 0 || !mFields.getField(R.id.field_series).visible) {
            // Hide 'Series' label and data
            getView().findViewById(R.id.label_series).setVisibility(View.GONE);
            getView().findViewById(R.id.field_series).setVisibility(View.GONE);
        } else {
            // Show 'Series' label and data
            getView().findViewById(R.id.label_series).setVisibility(View.VISIBLE);
            getView().findViewById(R.id.field_series).setVisibility(View.VISIBLE);

            String newText = null;
            Utils.pruneSeriesList(series);
            Utils.pruneList(mDbHelper, series);
            int seriesCount = series.size();
            if (seriesCount > 0) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < seriesCount; i++) {
                    builder.append("    ").append(series.get(i).getDisplayName());
                    if (i != seriesCount - 1) {
                        builder.append("<br/>");
                    }
                }
                newText = builder.toString();
            }
            mFields.getField(R.id.field_series)
                    .setShowHtml(true)
                    .setValue(newText);
        }
    }

    /**
     * Add other fields of book to details fields. We need this method to automatically
     * populate some fields during populating. See populateFieldsFromDb(Long).
     * Note that it should be performed before populating.
     */
    private void addFields() {
        // From 'My comments' tab
        mFields.add(R.id.field_rating, CatalogueDBAdapter.KEY_RATING, null);
        mFields.add(R.id.field_notes, CatalogueDBAdapter.KEY_NOTES, null)
                .setShowHtml(true);
        mFields.add(R.id.field_read_start, CatalogueDBAdapter.KEY_READ_START, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.field_read_end, CatalogueDBAdapter.KEY_READ_END, null, new Fields.DateFieldFormatter());
        mFields.add(R.id.field_location, CatalogueDBAdapter.KEY_LOCATION, null);
        // Make sure the label is hidden when the ISBN is
        mFields.add(R.id.label_isbn, "", CatalogueDBAdapter.KEY_ISBN, null);
        mFields.add(R.id.field_publisher, "", CatalogueDBAdapter.KEY_PUBLISHER, null);
    }

    /**
     * Formats 'format' section of the book depending on values
     * of 'pages' and 'format' fields.
     */
    private void formatFormatSection(BookData book) {
        Field field = mFields.getField(R.id.field_pages);
        String value = book.getString(CatalogueDBAdapter.KEY_PAGES);
        boolean isExist = value != null && !value.isEmpty();
        if (isExist) { //If 'pages' field is set format it
            field.setValue(getString(R.string.book_details_readonly_pages, value));
        }
        // Format 'format' field
        field = mFields.getField(R.id.field_format);
        value = book.getString(CatalogueDBAdapter.KEY_FORMAT);
        if (isExist && value != null && !value.isEmpty()) {
            /* Surround 'format' field with braces if 'pages' field is set
             * and 'format' field is not empty */
            field.setValue(getString(R.string.brackets, value));
        }
    }

    /**
     * Formats 'Publishing' section of the book depending on values
     * of 'publisher' and 'date published' fields.
     */
    private void formatPublishingSection(BookData book) {
        String date = book.getString(CatalogueDBAdapter.KEY_DATE_PUBLISHED);
        boolean hasDate = date != null && !date.isEmpty();
        String pub = book.getString(CatalogueDBAdapter.KEY_PUBLISHER);
        boolean hasPub = pub != null && !pub.isEmpty();
        String value;

        if (hasDate) {
            try {
                Date d = Utils.parseDate(date, true);
                date = Utils.toPrettyDate(d);
            } catch (Exception e) {
                // Ignore; just use what we have
            }
        }

        if (hasPub) {
            if (hasDate) {
                value = pub + "; " + date;
            } else {
                value = pub;
            }
        } else {
            if (hasDate) {
                value = date;
            } else {
                value = "";
            }
        }
        mFields.getField(R.id.field_publisher).setValue(value);
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * If book is not loaned field is invisible.
     *
     * @param rowId Database row _id of the loaned book
     */
    private void showLoanedInfo(Long rowId) {
        String personLoanedTo = mDbHelper.fetchLoanByBook(rowId);
        assert getView() != null;
        TextView textView = getView().findViewById(R.id.field_loaned_to);
        if (personLoanedTo != null) {
            textView.setVisibility(View.VISIBLE);
            String resultText = getString(R.string.book_details_readonly_loaned_to, personLoanedTo);
            textView.setText(resultText);
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    /**
     * Sets read status of the book if needed. Shows green tick if book is read.
     *
     * @param book Cursor containing information of the book from database
     */
    private void showReadStatus(BookData book) {
        assert getView() != null;
        ImageView image = getView().findViewById(R.id.field_read);
        if (AdminFieldVisibility.isVisible(CatalogueDBAdapter.KEY_READ)) {
            if (book.isRead()) {
                image.setVisibility(View.VISIBLE);
                image.setImageResource(R.drawable.ic_menu_checkmark_checked);
            } else {
                image.setVisibility(View.GONE);
            }
        } else {
            image.setVisibility(View.GONE);
        }
    }

    /**
     * Show signed status of the book. Set text 'yes' if signed. Otherwise it is 'No'.
     *
     * @param book Cursor containing information of the book from database
     */
    private void showSignedStatus(BookData book) {
        if (book.isSigned()) {
            assert getView() != null;
            ((TextView) getView().findViewById(R.id.field_signed)).setText(getResources().getString(R.string.option_yes));
        }
    }

    /**
     * Updates all fields of book from database.
     */
    private void updateFields(BookData book) {
        populateFieldsFromBook(book);
        // Populate author and series fields
        populateAuthorListField();
        populateSeriesListField();
    }

    @Override
    protected void onLoadBookDetails(BookData book) {
        mFields.setAll(book);
        updateFields(book);
    }

    @Override
    protected void onSaveBookDetails(BookData book) {
        // Override to Do nothing because we modify the fields to make them look pretty.
    }

    public void onResume() {
        // If we are read-only, returning here from somewhere else and have an
        // ID...reload!
        BookData book = mEditManager.getBookData();
        if (book.getRowId() != 0) {
            book.reload();
        }
        super.onResume();
    }

    /**
     * Formatter for date fields. On failure just return the raw string.
     *
     * @author Philip Warner
     *
     */
    static private class BinaryYesNoEmptyFormatter implements FieldFormatter {

        /**
         * Display as a human-friendly date
         */
        public String format(Field f, String source) {
            try {
                boolean val = Utils.stringToBoolean(source, false);
                return BookCatalogueApp.getRes().getString(val ? R.string.option_yes : R.string.option_no);
            } catch (Exception e) {
                return source;
            }
        }

        /**
         * Extract as an SQL date.
         */
        public String extract(Field f, String source) {
            try {
                return Utils.stringToBoolean(source, false) ? "1" : "0";
            } catch (Exception e) {
                return source;
            }
        }
    }

}
