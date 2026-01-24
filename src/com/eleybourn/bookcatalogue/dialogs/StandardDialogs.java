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

package com.eleybourn.bookcatalogue.dialogs;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.eleybourn.bookcatalogue.BookCatalogueAPI;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class StandardDialogs {

    private static StaticApiListener mApiListener;

    /**
     * Show a dialog asking if unsaved edits should be ignored. Finish if so.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void showConfirmUnsavedEditsDialog(final Activity a, final Runnable r) {
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.details_have_changed)
                .setMessage(R.string.you_have_unsaved_changes)
                .setCancelable(false)
                .setPositiveButton(R.string.exit, (dialog1, which) -> {
                    dialog1.dismiss();
                    File thumb = CatalogueDBAdapter.getTempThumbnail();
                    thumb.delete();
                    if (r != null) {
                        r.run();
                    } else {
                        a.finish();
                    }
                })
                .setNegativeButton(R.string.button_continue_editing, (dialog2, which) -> dialog2.dismiss())
                .create().show();
    }

    public static void deleteSeriesAlert(Context context, final CatalogueDBAdapter dbHelper, final Series series, final Runnable onDeleted) {

        // When we get here, we know the names are genuinely different and the old series is used in more than one place.
        String message = "Delete series";
        try {
            message = String.format(context.getResources().getString(R.string.really_delete_series), series.name);
        } catch (NullPointerException e) {
            Logger.logError(e);
        }
        // Modern Builder approach
        new MaterialAlertDialogBuilder(context)
                .setMessage(message)
                .setTitle(R.string.delete_series)
                .setIcon(R.drawable.ic_menu_info)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    dbHelper.deleteSeries(series);
                    onDeleted.run();
                })
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static int deleteBookAlert(Context context, final CatalogueDBAdapter dbHelper, final long id, final Runnable onDeleted) {
        mApiListener = new StaticApiListener(context);
        ArrayList<Author> authorList = dbHelper.getBookAuthorList(id);

        String title;
        try (Cursor cur = dbHelper.fetchBookById(id)) {
            if (cur == null || !cur.moveToFirst())
                return R.string.unable_to_find_book;
            int titleId = cur.getColumnIndex(CatalogueDBAdapter.KEY_TITLE);
            title = cur.getString(titleId);
            if (title == null || title.isEmpty())
                title = "<Unknown>";

        }

        // Format the list of authors nicely
        StringBuilder authors;
        if (authorList.isEmpty())
            authors = new StringBuilder("<Unknown>");
        else {
            authors = new StringBuilder(authorList.get(0).getDisplayName());
            for (int i = 1; i < authorList.size() - 1; i++) {
                authors.append(", ").append(authorList.get(i).getDisplayName());
            }
            if (authorList.size() > 1)
                authors.append(" ").append(context.getResources().getString(R.string.list_and)).append(" ").append(authorList.get(authorList.size() - 1).getDisplayName());
        }

        // Get the title		
        String format = context.getResources().getString(R.string.really_delete_book);

        String message = String.format(format, title, authors);
        // Modern Builder approach
        new MaterialAlertDialogBuilder(context)
                .setMessage(message)
                .setTitle(R.string.menu_delete_book)
                .setIcon(R.drawable.ic_menu_info)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    dbHelper.deleteBook(id);
                    new BookCatalogueAPI(context, BookCatalogueAPI.REQUEST_DELETE_BOOK, id, mApiListener);
                    onDeleted.run();
                })
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.dismiss())
                .show();
        return 0;

    }

    /**
     * Select a custom item from a list, and call handler when/if item is selected.
     */
    public static void selectItemDialog(LayoutInflater inflater, String message, ArrayList<SimpleDialogItem> items, SimpleDialogItem selectedItem, final SimpleDialogOnClickListener handler) {
        // Get the view and the radio group
        final View root = inflater.inflate(R.layout.dialog_select_list, null);
        TextView msg = root.findViewById(R.id.message);

        // Build the base dialog
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(inflater.getContext()).setView(root);
        if (message != null && !message.isEmpty()) {
            msg.setText(message);
        } else {
            msg.setVisibility(View.GONE);
        }

        final AlertDialog dialog = builder.create();

        // Create the listener for each item
        OnClickListener listener = v -> {
            SimpleDialogItem item = ViewTagger.getTag(v, R.id.TAG_DIALOG_ITEM);
            // For a consistent UI, make sure the selector is checked as well. NOT mandatory from
            // a functional point of view, just consistent
            if (!(v instanceof RadioButton)) {
                assert item != null;
                RadioButton btn = item.getSelector(v);
                if (btn != null) {
                    btn.setChecked(true);
                    btn.invalidate();
                }
            }

            dialog.dismiss();
            handler.onClick(item);
        };

        // Add the items to the dialog
        LinearLayout list = root.findViewById(R.id.list);
        for (SimpleDialogItem item : items) {
            View v = item.getView(inflater, list);
            v.setBackgroundResource(Utils.backgroundFlash(v.getContext()));
            ViewTagger.setTag(v, R.id.TAG_DIALOG_ITEM, item);
            list.addView(v);
            v.setOnClickListener(listener);
            RadioButton btn = item.getSelector(v);
            if (btn != null) {
                ViewTagger.setTag(btn, R.id.TAG_DIALOG_ITEM, item);
                btn.setChecked(item == selectedItem);
                btn.setOnClickListener(listener);
            }
        }
        dialog.show();
    }

    /**
     * Wrapper class to present a list of arbitrary objects for selection; it uses
     * the toString() method to display a simple list.
     */
    public static <T> void selectStringDialog(LayoutInflater inflater, String title, ArrayList<T> objects, String current, final SimpleDialogOnClickListener handler) {
        ArrayList<SimpleDialogItem> items = new ArrayList<>();
        SimpleDialogItem selectedItem = null;
        for (T o : objects) {
            SimpleDialogObjectItem item = new SimpleDialogObjectItem(o);
            if (current != null && o.toString().equalsIgnoreCase(current))
                selectedItem = item;
            items.add(item);
        }
        selectItemDialog(inflater, title, items, selectedItem, handler);
    }

    /**
     * Interface for item that displays in a custom dialog list
     */
    public interface SimpleDialogItem {
        View getView(LayoutInflater inflater, ViewGroup parent);

        RadioButton getSelector(View v);
    }

    /**
     * Interface to listen for item selection in a custom dialog list
     */
    public interface SimpleDialogOnClickListener {
        void onClick(SimpleDialogItem item);
    }

    // Define the listener as a static inner class
    private static class StaticApiListener implements BookCatalogueAPI.ApiListener {
        private final WeakReference<Context> contextReference;

        StaticApiListener(Context context) {
            // Use a WeakReference to avoid memory leaks
            this.contextReference = new WeakReference<>(context);
        }

        @Override
        public void onApiProgress(String request, int current, int total) {
            onApiProgress(request, current, total, "");
        }

        @Override
        public void onApiProgress(String request, int current, int total, String message) {
        }

        @Override
        public void onApiComplete(String request, String message) {
        }

        @Override
        public void onApiError(String request, String error) {
            Context context = contextReference.get();
            if (context == null) {
                return;
            }
            Log.e("StandardDialogs", "API Error for " + request + ": " + error);
        }
    }

    /**
     * Item to manage an Object in a list of items.
     */
    public static class SimpleDialogObjectItem implements SimpleDialogItem {
        private final Object mObject;

        public SimpleDialogObjectItem(Object object) {
            mObject = object;
        }

        public Object getObject() {
            return mObject;
        }

        /**
         * Get a View to display the object
         */
        public View getView(LayoutInflater inflater, ViewGroup parent) {
            // Create the view
            View v = inflater.inflate(R.layout.string_list_item, parent, false);
            // Set the name
            TextView name = v.findViewById(R.id.field_name);
            name.setText(mObject.toString());
            // Return it
            return v;
        }

        public RadioButton getSelector(View v) {
            return v.findViewById(R.id.selector);
        }

        /**
         * Get the underlying object as a string
         */
        @NonNull
        @Override
        public String toString() {
            return mObject.toString();
        }
    }

    public static class SimpleDialogMenuItem extends SimpleDialogObjectItem {
        final int mItemId;
        final int mDrawableId;

        public SimpleDialogMenuItem(Object object, int itemId, int icon) {
            super(object);
            mItemId = itemId;
            mDrawableId = icon;
        }

        public int getItemId() {
            return mItemId;
        }

        @Override
        public View getView(LayoutInflater inflater, ViewGroup parent) {
            View v = super.getView(inflater, parent);
            TextView name = v.findViewById(R.id.field_name);
            name.setCompoundDrawablesWithIntrinsicBounds(mDrawableId, 0, 0, 0);
            getSelector(v).setVisibility(View.GONE);
            return v;
        }
    }

}
