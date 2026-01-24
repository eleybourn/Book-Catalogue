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

import android.app.Activity;
import android.content.Intent;
import android.database.SQLException;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.BookshelfDialogFragment.OnBookshelfCheckChangeListener;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerFragment.OnPartialDatePickerListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment;
import com.eleybourn.bookcatalogue.dialogs.TextFieldEditorFragment.OnTextFieldEditorListener;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.SafeSpannedTextView;

import java.util.ArrayList;
import java.util.Objects;


public class BookEditFields extends BookAbstract
        implements OnPartialDatePickerListener, OnTextFieldEditorListener, OnBookshelfCheckChangeListener {

    // Launcher for Editing Authors
    private final ActivityResultLauncher<Intent> mEditAuthorsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int resultCode = result.getResultCode();
                Intent data = result.getData();

                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY)) {
                    mEditManager.getBookData().setAuthorList(Utils.getAuthorsFromBundle(Objects.requireNonNull(data.getExtras())));
                    mEditManager.setDirty(true);
                } else {
                    // Even though the dialog was terminated, some authors MAY have been updated/added.
                    mEditManager.getBookData().refreshAuthorList(mDbHelper);
                }

                // Refresh the display
                boolean oldDirty = mEditManager.isDirty();
                populateAuthorListField();
                mEditManager.setDirty(oldDirty);
            }
    );

    // Launcher for Editing Series
    private final ActivityResultLauncher<Intent> mEditSeriesLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int resultCode = result.getResultCode();
                Intent data = result.getData();

                if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY)) {
                    mEditManager.getBookData().setSeriesList(Utils.getSeriesFromBundle(Objects.requireNonNull(data.getExtras())));
                    mEditManager.setDirty(true);
                } else {
                    mEditManager.getBookData().refreshSeriesList(mDbHelper);
                }

                // Refresh the display
                boolean oldDirty = mEditManager.isDirty();
                populateSeriesListField();
                mEditManager.setDirty(oldDirty);
            }
    );

    /**
     * Display the edit fields page
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.book_edit_details, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        Tracker.enterOnActivityCreated(this);
        double t0 = System.currentTimeMillis();
        double t1 = 0;
        double t2 = 0;

        try {
            t1 = System.currentTimeMillis();
            super.onViewCreated(view, savedInstanceState);
            t2 = System.currentTimeMillis();

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
            }

            //Set click listener on Author field
            assert getView() != null;
            View v = getView().findViewById(R.id.field_author); //Reusable view for setting listeners
            v.setOnClickListener(v1 -> {
                Intent i = new Intent(getActivity(), BookEditAuthorList.class);
                i.putExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, mEditManager.getBookData().getAuthorList());
                i.putExtra(CatalogueDBAdapter.KEY_ROW_ID, mEditManager.getBookData().getRowId());
                i.putExtra("label_title", CatalogueDBAdapter.KEY_TITLE);
                i.putExtra("field_title", mFields.getField(R.id.field_title).getValue().toString());
                mEditAuthorsLauncher.launch(i);
            });

            //Set click listener on Series field
            v = getView().findViewById(R.id.field_series);
            v.setOnClickListener(v2 -> {
                Intent i = new Intent(getActivity(), BookEditSeriesList.class);
                i.putExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY, mEditManager.getBookData().getSeriesList());
                i.putExtra(CatalogueDBAdapter.KEY_ROW_ID, mEditManager.getBookData().getRowId());
                i.putExtra("label_title", CatalogueDBAdapter.KEY_TITLE);
                i.putExtra("field_title", mFields.getField(R.id.field_title).getValue().toString());
                mEditSeriesLauncher.launch(i);
            });
            assert this.getActivity() != null;
            ArrayAdapter<String> publisher_adapter = new ArrayAdapter<>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getPublishers());
            mFields.setAdapter(R.id.field_publisher, publisher_adapter);
            ArrayAdapter<String> genre_adapter = new ArrayAdapter<>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getGenres());
            mFields.setAdapter(R.id.field_genre, genre_adapter);
            ArrayAdapter<String> language_adapter = new ArrayAdapter<>(this.getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getLanguages());
            mFields.setAdapter(R.id.field_language, language_adapter);

            mFields.setListener(R.id.button_date_published, view1 -> showDatePublishedDialog());

            final Field formatField = mFields.getField(R.id.field_format);
            // Get the formats to use in the AutoComplete stuff
            AutoCompleteTextView formatText = (AutoCompleteTextView) formatField.getView();
            formatText.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, mEditManager.getFormats()));
            // Get the drop-down button for the formats list and setup dialog
            ImageView formatButton = getView().findViewById(R.id.format_dropdown_button);
            formatButton.setOnClickListener(v3 -> StandardDialogs.selectStringDialog(getActivity().getLayoutInflater(), getString(R.string.label_format),
                    mDbHelper.getFormats(), formatField.getValue().toString(),
                    item -> formatField.setValue(item.toString())));

            Field bookshelfButtonFe = mFields.getField(R.id.field_bookshelf);
            bookshelfButtonFe.getView().setOnClickListener(v4 -> {
                BookshelfDialogFragment frag = BookshelfDialogFragment.newInstance(
                        R.id.field_bookshelf,
                        mEditManager.getBookData().getRowId(),
                        mEditManager.getBookData().getBookshelfText(),
                        mEditManager.getBookData().getBookshelfList()
                );

                frag.show(getParentFragmentManager(), "bookshelves_dialog");
            });

            // Build the label for the book description if this is first time, otherwise will be built later
            if (savedInstanceState == null)
                buildDescription();

            // Setup the Save/Add/Anthology UI elements
            setupUi();

            try {
                Utils.fixFocusSettings(getView());
                getView().findViewById(R.id.field_author).requestFocus();
            } catch (Exception e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes when the
                // 'next' key is pressed and some views have been hidden.
                Logger.logError(e);
            }

            if (savedInstanceState != null) {
                mEditManager.setDirty(savedInstanceState.getBoolean("Dirty"));
            } else {
                mEditManager.setDirty(false);
            }

        } catch (IndexOutOfBoundsException | SQLException e) {
            Logger.logError(e);
        } finally {
            Tracker.exitOnActivityCreated(this);
        }

        double tn = System.currentTimeMillis();
    }

    private void showDescriptionDialog() {
        Object o = mFields.getField(R.id.field_description).getValue();
        String description = (o == null ? null : o.toString());
        TextFieldEditorFragment dlg = TextFieldEditorFragment.newInstance(R.id.field_description, R.string.label_description_edit, description);
        dlg.show(getParentFragmentManager(), null);
    }

    private void showDatePublishedDialog() {
        PartialDatePickerFragment frag = PartialDatePickerFragment.newInstance();
        Utils.prepareDateDialogFragment(frag, mFields.getField(R.id.button_date_published).getValue());
        frag.setTitle(R.string.label_date_published);
        frag.setDialogId(R.id.button_date_published); // Set to the destination field ID
        frag.show(getParentFragmentManager(), null);
    }

    /**
     * This function will populate the forms elements in three different ways
     * 1. If a valid rowId exists it will populate the fields from the database
     * 2. If fields have been passed from another activity (e.g. ISBNSearch) it will populate the fields from the bundle
     * 3. It will leave the fields blank for new books.
     */
    // TODO: remove this suppression once TIRAMISU is the standard. i.e. minSdkVersion = 33
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private void populateFields() {
        double t0 = System.currentTimeMillis();
        assert getActivity() != null;
        Bundle extras = getActivity().getIntent().getExtras();
        final BookData book = mEditManager.getBookData();

        populateFieldsFromBook(book);
        if (book.getRowId() == 0) { // New book
            if (extras != null) {
                // From the ISBN Search (add)
                try {
                    if (extras.containsKey("book")) {
                        throw new RuntimeException("[book] array passed in Intent");
                    } else {
                        Bundle values;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            values = extras.getParcelable("bookData", Bundle.class);
                        } else {
                            // Use the deprecated method for older APIs
                            values = extras.getParcelable("bookData");
                        }
                        if (values == null)
                            throw new RuntimeException("[bookData] data not present");
                        for (Field f : mFields) {
                            if (!f.column.isEmpty() && values.containsKey(f.column)) {
                                try {
                                    f.setValue(Objects.requireNonNull(values.get(f.column)).toString());
                                } catch (Exception e) {
                                    String msg = "Populate field " + f.column + " failed: " + e.getMessage();
                                    Logger.logError(e, msg);
                                }
                            }
                        }

                    }
                } catch (NullPointerException e) {
                    Logger.logError(e);
                }
            }
            initDefaultShelf();
            setCoverImage();
        }

        populateAuthorListField();
        populateSeriesListField();

        // Restore default visibility and hide unused/unwanted and empty fields
        showHideFields(false);
    }

    /**
     * Use the currently selected bookshelf as default
     */
    private void initDefaultShelf() {
        final BookData book = mEditManager.getBookData();
        final String list = book.getBookshelfList();
        if (list == null || list.isEmpty()) {
            String currShelf = BookCatalogueApp.getAppPreferences().getString(Library.PREF_BOOKSHELF, "");
            if (currShelf.isEmpty()) {
                currShelf = mDbHelper.getBookshelfName(1);
            }
            String encoded_shelf = Utils.encodeListItem(currShelf, BOOKSHELF_SEPARATOR);
            Field fe = mFields.getField(R.id.field_bookshelf);
            fe.setValue(currShelf);
            book.setBookshelfList(encoded_shelf);
        }
    }

    private void setupUi() {
        Log.d("BC", "setupUI");
        assert getView() != null;
        final CheckBox cb = getView().findViewById(R.id.field_anthology);
        cb.setOnClickListener(view -> mEditManager.setShowAnthology(cb.isChecked()));
    }

    /**
     * Setup the 'description' header field to have a clickable link.
     */
    private void buildDescription() {
        assert getView() != null;
        SafeSpannedTextView descriptionButton = getView().findViewById(R.id.field_description);
        descriptionButton.setOnClickListener(v -> BookEditFields.this.showDescriptionDialog());

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);

        super.onSaveInstanceState(outState);

        Tracker.exitOnSaveInstanceState(this);
    }

    @Override
    protected void populateAuthorListField() {
        ArrayList<Author> list = mEditManager.getBookData().getAuthorList();
        if (!list.isEmpty() && Utils.pruneList(mDbHelper, list)) {
            mEditManager.setDirty(true);
            mEditManager.getBookData().setAuthorList(list);
        }
        super.populateAuthorListField();
    }

    @Override
    protected void onLoadBookDetails(BookData book) {
        mFields.setAll(book);
        populateFields();
    }

    /**
     * The callback received when the user "sets" the date in the dialog.
     * Build a full or partial date in SQL format
     */
    @Override
    public void onPartialDatePickerSet(int dialogId, PartialDatePickerFragment dialog, Integer year, Integer month, Integer day) {
        String value = Utils.buildPartialDate(year, month, day);
        mFields.getField(dialogId).setValue(value);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the date in the dialog.
     * Dismiss it.
     */
    @Override
    public void onPartialDatePickerCancel(int dialogId, PartialDatePickerFragment dialog) {
        dialog.dismiss();
    }

    /**
     * The callback received when the user "sets" the text editor in the text editor dialog.
     * Set the appropriate field
     */
    @Override
    public void onTextFieldEditorSave(int dialogId, TextFieldEditorFragment dialog, String newText) {
        mFields.getField(dialogId).setValue(newText);
        dialog.dismiss();
    }

    /**
     * The callback received when the user "cancels" the text editor dialog.
     * Dismiss it.
     */
    @Override
    public void onTextFieldEditorCancel(int dialogId, TextFieldEditorFragment dialog) {
        dialog.dismiss();
    }

    @Override
    public void onBookshelfCheckChanged(int dialogId,
                                        BookshelfDialogFragment dialog, boolean checked, String shelf, String textList, String encodedList) {

        Field fe = mFields.getField(R.id.field_bookshelf);
        fe.setValue(textList);
        mEditManager.getBookData().setBookshelfList(encodedList);
    }

}
