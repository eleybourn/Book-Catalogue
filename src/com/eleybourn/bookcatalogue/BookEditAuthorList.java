/*
 * @copyright 2011 Philip Warner
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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

/**
 * Activity to edit a list of authors provided in an ArrayList<Author> and
 * return an updated list.
 *
 * @author Philip Warner
 */
public class BookEditAuthorList extends BookEditObjectList<Author> {

    /**
     * Constructor; pass the superclass the main and row based layouts to use.
     */
    public BookEditAuthorList() {
        super(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, R.layout.edit_author_list, R.layout.row_edit_author_list);
    }

    @Override
    protected void onSetupView(View target, Author object) {
        if (object != null) {
            TextView at = target.findViewById(R.id.row_author);
            if (at != null) {
                at.setText(object.getDisplayName());
            }
            at = target.findViewById(R.id.row_author_sort);
            if (at != null) {
                at.setText(object.getSortName());
            }
        }
    }

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AutoCompleteTextView author_text_field = findViewById(R.id.field_author);
        try {
            if (Objects.requireNonNull(getIntent().getStringExtra("is_anthology")).equals("1")) {
                author_text_field.setHint(R.string.label_author_or_editor);
            }
        } catch (Exception ignored) {}

        try {
            // Setup autocomplete for author name
            ArrayAdapter<String> author_adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, mDbHelper.getAllAuthors());
            author_text_field.setAdapter(author_adapter);
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Do the work of the onClickListener for the 'Add' button.
     */
    protected void onAdd(View v) {
        // Get the text
        AutoCompleteTextView t = BookEditAuthorList.this.findViewById(R.id.field_author);
        String s = t.getText().toString().trim();
        if (!s.isEmpty()) {
            // Get an author and try to find in DB.
            Author a = new Author(t.getText().toString());
            a.id = mDbHelper.lookupAuthorId(a);

            //
            boolean foundMatch = false;
            for (int i = 0; i < mList.size() && !foundMatch; i++) {
                if (a.id != 0L) {
                    if (mList.get(i).id == a.id)
                        foundMatch = true;
                } else {
                    if (a.getDisplayName().equals(mList.get(i).getDisplayName()))
                        foundMatch = true;
                }
            }
            if (foundMatch) {
                Toast.makeText(BookEditAuthorList.this, getResources().getString(R.string.author_already_in_list), Toast.LENGTH_LONG).show();
                return;
            }

            mList.add(a);
            mAdapter.notifyDataSetChanged();
            t.setText("");
        } else {
            Toast.makeText(BookEditAuthorList.this, getResources().getString(R.string.author_is_blank), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onRowClick(View target, int position, final Author object) {
        editAuthor(object);
    }

    private void editAuthor(final Author author) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_edit_author);
        dialog.setTitle(R.string.edit_author_details);
        EditText familyView = dialog.findViewById(R.id.field_family_name);
        EditText givenView = dialog.findViewById(R.id.field_given_names);
        familyView.setText(author.familyName);
        givenView.setText(author.givenNames);

        Button saveButton = dialog.findViewById(R.id.button_confirm);
        saveButton.setOnClickListener(v -> {
            String newFamily = familyView.getText().toString().trim();
            if (newFamily.isEmpty()) {
                Toast.makeText(BookEditAuthorList.this, R.string.author_is_blank, Toast.LENGTH_LONG).show();
                return;
            }
            String newGiven = givenView.getText().toString();
            Author newAuthor = new Author(newFamily, newGiven);
            dialog.dismiss();
            confirmEditAuthor(author, newAuthor);
        });
        Button cancelButton = dialog.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void confirmEditAuthor(final Author oldAuthor, final Author newAuthor) {
        // First, deal with a some special cases...

        // Case: Unchanged.
        if (newAuthor.familyName.compareTo(oldAuthor.familyName) == 0
                && newAuthor.givenNames.compareTo(oldAuthor.givenNames) == 0) {
            // No change; nothing to do
            return;
        }

        // Get the new author ID
        oldAuthor.id = mDbHelper.lookupAuthorId(oldAuthor);
        newAuthor.id = mDbHelper.lookupAuthorId(newAuthor);

        // See if the old author is used in any other books.
        long nRefs = mDbHelper.getAuthorBookCount(oldAuthor) + mDbHelper.getAuthorAnthologyCount(oldAuthor);
        boolean oldHasOthers = nRefs > (mRowId == 0 ? 0 : 1);

        // Case: author is the same, or is only used in this book
        if (newAuthor.id == oldAuthor.id || !oldHasOthers) {
            // Just update with the most recent spelling and format
            oldAuthor.copyFrom(newAuthor);
            Utils.pruneList(mDbHelper, mList);
            mDbHelper.sendAuthor(oldAuthor);
            mAdapter.notifyDataSetChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author is used in more than one place.
        String format = getResources().getString(R.string.changed_author_how_apply);
        String allBooks = getResources().getString(R.string.option_all_books);
        String thisBook = getResources().getString(R.string.this_book);
        String message = String.format(format, oldAuthor.getSortName(), newAuthor.getSortName(), allBooks);
        new MaterialAlertDialogBuilder(this).setMessage(message)
                .setTitle(getResources().getString(R.string.scope_of_change))
                .setIcon(R.drawable.ic_menu_info)
                .setPositiveButton(thisBook, (dialog, which) -> {
                    oldAuthor.copyFrom(newAuthor);
                    Utils.pruneList(mDbHelper, mList);
                    mAdapter.notifyDataSetChanged();
                    dialog.dismiss();
                })
                .setNegativeButton(allBooks, (dialog, which) -> {
                    mDbHelper.globalReplaceAuthor(oldAuthor, newAuthor);
                    oldAuthor.copyFrom(newAuthor);
                    Utils.pruneList(mDbHelper, mList);
                    mAdapter.notifyDataSetChanged();
                    dialog.dismiss();
                })
                .create().show();
    }

    @Override
    protected boolean onSave(Intent intent) {
        final AutoCompleteTextView t = BookEditAuthorList.this.findViewById(R.id.field_author);
        Resources res = this.getResources();
        String s = t.getText().toString().trim();
        if (!s.isEmpty()) {
            final AlertDialog alertDialog = new MaterialAlertDialogBuilder(this).setMessage(res.getText(R.string.unsaved_edits)).create();

            alertDialog.setTitle(res.getText(R.string.unsaved_edits_title));
            alertDialog.setIcon(R.drawable.ic_menu_info);
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, res.getText(R.string.option_yes), (dialog, which) -> {
                t.setText("");
                findViewById(R.id.button_confirm).performClick();
            });

            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, res.getText(R.string.option_no), (dialog, which) -> {
                //do nothing
            });

            alertDialog.show();
            return false;
        } else {
            return true;
        }
    }
}
