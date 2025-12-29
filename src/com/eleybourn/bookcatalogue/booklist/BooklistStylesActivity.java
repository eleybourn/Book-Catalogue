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

package com.eleybourn.bookcatalogue.booklist;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.EditObjectList;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Activity to edit the list of styles and enable/disable their presence in the
 * styles menu.
 *
 * @author Philip Warner
 */
public class BooklistStylesActivity extends EditObjectList<BooklistStyle> {
    /** Database connection */
    private CatalogueDBAdapter mDb ;
    /** The row being edited. Set when an individual style is edited */
    private int mEditedRow;

    // Modern Activity Result Launcher
    private ActivityResultLauncher<Intent> mStyleEditorLauncher;

    /**
     * Constructor
     */
    public BooklistStylesActivity() {
        super(null, R.layout.admin_styles, R.layout.row_edit_booklist_styles);
    }

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            // Superclass will call getList() which needs DB, so create DB before calling superclass.
            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            // Initialize the launcher BEFORE calling super.onCreate or potentially triggering lifecycle events
            mStyleEditorLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // Only handle the result if the result code was OK (or handle cancelled if needed)
                        // The legacy logic for UniqueId.ACTIVITY_BOOKLIST_STYLE didn't check RESULT_OK explicitly,
                        // but handleStyleResult checks for null data.
                        handleStyleResult(result.getData());
                    }
            );

            super.onCreate(savedInstanceState);

            // We want context menus to be available
            registerForContextMenu(findViewById(R.id.list));

            this.setTitle(R.string.preferred_styles);

            if (savedInstanceState == null)
                HintManager.displayHint(this, R.string.hint_booklist_styles_editor, null, null);


        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Fix background
     */
    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @Override
    protected ArrayList<BooklistStyle> getList() {
        ArrayList<BooklistStyle> styles = new ArrayList<>();
        // get the preferred styles first
        for(BooklistStyle s: BooklistStyles.getAllStyles(mDb)) {
            styles.add(s);
        }
        return styles;
    }

    /**
     * Required, not used
     */
    @Override
    protected void onAdd(View v) {
    }

    /**
     * Holder pattern object for list items
     *
     * @author Philip Warner
     */
    private static class Holder {
        BooklistStyle style;
        TextView name;
        TextView groups;
        TextView kind;
        ImageView preferred;
    }

    @Override
    protected void onSetupView(View target, BooklistStyle style) {
        Holder h;
        h = ViewTagger.getTag(target, R.id.TAG_HOLDER);
        if (h == null) {
            // No holder found, create one

            h = new Holder();
            h.name = target.findViewById(R.id.field_name);
            h.groups = target.findViewById(R.id.field_groups);
            h.kind = target.findViewById(R.id.field_kind);
            h.preferred = target.findViewById(R.id.row_preferred);
            // Tag relevant views
            ViewTagger.setTag(target, R.id.TAG_HOLDER, h);
            ViewTagger.setTag(h.preferred, R.id.TAG_HOLDER, h);

            // Make it flash
            //target.setBackgroundResource(android.R.drawable.list_selector_background);

            // Handle clicks on the tick/cross
            h.preferred.setOnClickListener(v -> {
                Holder h1 = ViewTagger.getTag(v, R.id.TAG_HOLDER);
                assert h1 != null;
                boolean newPref = !h1.style.isPreferred();
                h1.style.setPreferred(newPref);
                if (newPref) {
                    h1.preferred.setImageResource(R.drawable.btn_check_clipped);
                } else {
                    h1.preferred.setImageResource(R.drawable.btn_uncheck_clipped);
                }
                onListChanged();
            });
        }

        // Set the volatile fields in the holder
        h.style = style;
        h.name.setText(style.getDisplayName());
        h.groups.setText(style.getGroupListDisplayNames());

        if (style.isUserDefined())
            h.kind.setText(R.string.user_defined);
        else
            h.kind.setText(R.string.builtin);

        if (style.isPreferred()) {
            h.preferred.setImageResource(R.drawable.btn_check_clipped);
        } else {
            h.preferred.setImageResource(R.drawable.btn_uncheck_clipped);
        }
    }

    /**
     * Class used for an item in the pseudo-context menu.
     * Context menus don't seem to work for EditObject subclasses, perhaps because we consume click events.
     *
     * @author Philip Warner
     */
    private class ContextItem implements CharSequence {
        /** String for this item */
        private final String mString;
        /** ID of this item */
        private final int mId;
        /**
         * Constructor
         *
         * @param stringId	ID of String for this item
         * @param id		ID of this item
         */
        ContextItem(int stringId, int id) {
            mString = getString(stringId);
            mId = id;
        }

        /** Return the associated string */
        @NonNull
        public String toString() {
            return mString;
        }
        /** Get the ID */
        public int getId() {
            return mId;
        }
        /** Use the string object to provide the CharSequence implementation */
        @Override
        public char charAt(int index) {
            return mString.charAt(index);
        }
        /** Use the string object to provide the CharSequence implementation */
        @Override
        public int length() {
            return mString.length();
        }
        /** Use the string object to provide the CharSequence implementation */
        @NonNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return mString.subSequence(start, end);
        }
    }

    /**
     * Use the RowClick ti present a pseudo context menu.
     */
    @Override
    protected void onRowClick(View target, final int position, final BooklistStyle style) {
        // Build the array of menu items based on the style we are editing
        final ArrayList<ContextItem> items = new ArrayList<>();
        if (style.isUserDefined()) {
            items.add(new ContextItem(R.string.delete_style, R.id.MENU_DELETE_STYLE));
            items.add(new ContextItem(R.string.edit_style, R.id.MENU_EDIT_STYLE));
        }
        items.add(new ContextItem(R.string.clone_style, R.id.MENU_CLONE_STYLE));

        // Turn the list into an array
        CharSequence[] csa = new CharSequence[items.size()];
        for(int i = 0 ; i < items.size(); i++)
            csa[i] = items.get(i);

        // Show the dialog
        final AlertDialog dialog = new AlertDialog.Builder(getLayoutInflater().getContext()).setItems(csa, (dialog1, which) -> {
            switch(items.get(which).getId()) {
                case R.id.MENU_DELETE_STYLE:
                    style.deleteFromDb(mDb);
                    // Refresh the list
                    setList(getList());
                    dialog1.dismiss();
                    return;
                case R.id.MENU_EDIT_STYLE:
                    editStyle(position, style, false);
                    dialog1.dismiss();
                    return;
                case R.id.MENU_CLONE_STYLE:
                    editStyle(position, style, true);
                    dialog1.dismiss();
            }
        }).create();

        dialog.show();
    }

    /**
     * Edit the passed style, saving its details locally. Optionally for a clone.
     *
     * @param position		Position in list
     * @param style			Actual style
     * @param alwaysClone	Force a clone, even if its already user-defined
     */
    private void editStyle(int position, BooklistStyle style, boolean alwaysClone) {
        Intent i = new Intent(this, BooklistStylePropertiesActivity.class);
        // Save the current row
        mEditedRow = position;

        if (!style.isUserDefined() || alwaysClone) {
            try {
                style = style.getClone();
                style.setRowId(0);
                style.setName(style.getDisplayName());
            } catch (Exception e) {
                Logger.logError(e);
                Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_LONG).show();
                return;
            }
        }
        i.putExtra(BooklistStylePropertiesActivity.KEY_STYLE, style);
        // Replace deprecated startActivityForResult with the launcher
        mStyleEditorLauncher.launch(i);
    }

    @Override
    protected void onListChanged() {
        // Save the order whenever the list is modified.
        BooklistStyles.SaveMenuOrder(mList);
    }

    /**
     * Called after a style has been edited.
     *
     * @param data		Data passed from the Result API
     */
    private void handleStyleResult(Intent data) {
        // Make sure we have a style. If not, the user must have cancelled.
        if (data == null || !data.hasExtra(BooklistStylePropertiesActivity.KEY_STYLE))
            return;

        try {
            BooklistStyle result = (BooklistStyle) data.getSerializableExtra(BooklistStylePropertiesActivity.KEY_STYLE);
            if (result == null) {
                // Style was deleted. Refresh.
                setList(getList());
            } else if (mEditedRow < 0) {
                // Was added. So put at top and mark as preferred
                result.setPreferred(true);
                mList.add(0, result);
                BooklistStyles.SaveMenuOrder(mList);
            } else {
                BooklistStyle origStyle = mList.get(mEditedRow);
                if (origStyle.getRowId() != result.getRowId()) {
                    if (!origStyle.isUserDefined()) {
                        // Working on a clone of a builtin style
                        if (origStyle.isPreferred()) {
                            // Replace the original row with the new one
                            mList.set(mEditedRow, result);
                            // And demote the original
                            origStyle.setPreferred(false);
                            mList.add(origStyle);
                        } else {
                            // Try to put it directly after original
                            mList.add(mEditedRow, result);
                        }
                    } else {
                        // A clone of an original. Put it directly after the original
                        mList.add(mEditedRow, result);
                    }
                    if (result.isPreferred())
                        BooklistStyles.SaveMenuOrder(mList);
                } else {
                    mList.set(mEditedRow, result);
                }
            }
            setList(mList);
        } catch (Exception e) {
            Logger.logError(e);
            // Do our best to recover
            setList(getList());
        }
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDb != null)
            mDb.close();
    }
}
