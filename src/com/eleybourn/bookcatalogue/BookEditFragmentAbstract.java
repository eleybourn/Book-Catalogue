/*
 * @copyright 2013 Philip Warner
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
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;

import java.util.ArrayList;

/**
 * Based class for all fragments that appear in the BookEdit activity
 *
 * @author pjw
 */
public abstract class BookEditFragmentAbstract extends Fragment implements DataEditor {
    protected Fields mFields;
    /**
     * A link to the BookEditManager for this fragment (the activity)
     */
    protected BookEditManager mEditManager;
    /**
     * Database instance
     */
    protected CatalogueDBAdapter mDbHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Activity a = null;
        if (context instanceof Activity) {
            a = (Activity) context;
        }

        if (!(a instanceof BookEditManager)) {
            assert a != null;
            throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement BookEditManager");
        }

        mEditManager = (BookEditManager) a;
        mDbHelper = new CatalogueDBAdapter(a);
        mDbHelper.open();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFields = new Fields(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // This is now done in onPause() since the view may have been deleted when this is called
        onSaveBookDetails(mEditManager.getBookData());
    }

    /**
     * Called to load data from the BookData object when needed.
     *
     * @param book BookData to load from
     */
    abstract protected void onLoadBookDetails(BookData book);

    /**
     * Default implementation of code to save existing data to the BookData object
     */
    protected void onSaveBookDetails(BookData book) {
        mFields.getAll(book);
    }

    @Override
    public void onResume() {
        //double t0 = System.currentTimeMillis();

        super.onResume();

        // Load the data and preserve the isDirty() setting
        mFields.setAfterFieldChangeListener(null);
        final boolean wasDirty = mEditManager.isDirty();
        BookData book = mEditManager.getBookData();
        onLoadBookDetails(book);
        mEditManager.setDirty(wasDirty);

        // Set the listener to monitor edits
        mFields.setAfterFieldChangeListener((field, newValue) -> mEditManager.setDirty(true));
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mDbHelper.close();
    }

    @Override
    public void saveAllEdits(DataManager data) {
        mFields.getAll(mEditManager.getBookData());
    }

    /**
     * This is 'final' because we want inheritors to implement onLoadBookDetails()
     */
    @Override
    public final void reloadData(DataManager data) {
        final boolean wasDirty = mEditManager.isDirty();
        onLoadBookDetails(mEditManager.getBookData());
        mEditManager.setDirty(wasDirty);
    }

    /**
     * Show or Hide text field if it has not any useful data.
     * Don't show a field if it is already hidden (assumed by user preference)
     *
     * @param hideIfEmpty   TODO
     * @param resId         layout resource id of the field
     * @param relatedFields list of fields whose visibility will also be set based on the first field
     */
    protected void showHideField(boolean hideIfEmpty, int resId, int... relatedFields) {
        // Get the base view
        assert getView() != null;
        final View v = getView().findViewById(resId);
        int visibility;
        if (v != null) {
            visibility = v.getVisibility();
            if (hideIfEmpty) {
                if (v.getVisibility() != View.GONE) {
                    // Determine if we should hide it
                    if (v instanceof ImageView) {
                        visibility = v.getVisibility();
                    } else {
                        final String value = mFields.getField(resId).getValue().toString();
                        final boolean isExist = !value.isEmpty();
                        visibility = isExist ? View.VISIBLE : View.GONE;
                        v.setVisibility(visibility);
                    }
                }
            }
            // Set the related views
            for (int i : relatedFields) {
                View rv = getView().findViewById(i);
                if (rv != null)
                    rv.setVisibility(visibility);
            }
        }
    }

    /**
     * Hides unused fields if they have not any useful data. Checks all text fields
     * except of author, series and loaned.
     */
    protected void showHideFields(boolean hideIfEmpty) {
        mFields.resetVisibility();

        // Check publishing information; in reality only one of these fields will exist
        showHideField(hideIfEmpty, R.id.field_publisher, R.id.label_publishing, R.id.row_publisher);
        showHideField(hideIfEmpty, R.id.field_publisher, R.id.label_publishing, R.id.row_publisher);

        showHideField(hideIfEmpty, R.id.button_date_published, R.id.row_date_published);
        showHideField(hideIfEmpty, R.id.row_img, R.id.image_wrapper);

        // Check format information
        showHideField(hideIfEmpty, R.id.field_pages, R.id.row_pages);
        showHideField(hideIfEmpty, R.id.field_format, R.id.row_format);

        // Check genre
        showHideField(hideIfEmpty, R.id.field_genre, R.id.label_genre, R.id.row_genre);

        // Check language
        showHideField(hideIfEmpty, R.id.field_language, R.id.label_language, R.id.row_language);

        // Check ISBN
        showHideField(hideIfEmpty, R.id.field_isbn, R.id.row_isbn);

        // Check ISBN
        showHideField(hideIfEmpty, R.id.field_series, R.id.row_series, R.id.label_series);

        // Check list price
        showHideField(hideIfEmpty, R.id.field_list_price, R.id.row_list_price);

        // Check description
        showHideField(hideIfEmpty, R.id.field_description, R.id.heading_description, R.id.description_divider);

        // **** MY COMMENTS SECTION ****
        // Check notes
        showHideField(hideIfEmpty, R.id.field_notes, R.id.label_notes, R.id.row_notes);

        // Check date start reading
        showHideField(hideIfEmpty, R.id.field_read_start, R.id.row_read_start);

        // Check date end reading
        showHideField(hideIfEmpty, R.id.field_read_end, R.id.row_read_end);

        // Check location
        showHideField(hideIfEmpty, R.id.field_location, R.id.row_location, R.id.row_location);

        // Check signed flag
        showHideField(hideIfEmpty, R.id.field_signed, R.id.row_signed);

    }

    /**
     * Interface that any containing activity must implement.
     *
     * @author pjw
     */
    public interface BookEditManager {
        //public Fields getFields();
        void setShowAnthology(boolean showAnthology);

        boolean isDirty();

        void setDirty(boolean isDirty);

        BookData getBookData();

        void setRowId(Long id);

        ArrayList<String> getFormats();

        ArrayList<String> getGenres();

        ArrayList<String> getLanguages();

        ArrayList<String> getPublishers();
    }
}
