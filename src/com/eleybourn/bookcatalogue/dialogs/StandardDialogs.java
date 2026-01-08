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

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.data.Author;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.data.Series;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

public class StandardDialogs {

	/**
	 * Show a dialog asking if unsaved edits should be ignored. Finish if so.
	 */
	public static void showConfirmUnsavedEditsDialog(final Activity a, final Runnable r){
		AlertDialog.Builder dialog = new Builder(a);

		dialog.setTitle(R.string.details_have_changed);
		dialog.setMessage(R.string.you_have_unsaved_changes);
		
		dialog.setPositiveButton(R.string.exit, (dialog1, which) -> {
            dialog1.dismiss();
            if (r != null) {
                r.run();
            } else {
                a.finish();
            }
        });

		dialog.setNegativeButton(R.string.continue_editing, (dialog2, which) -> dialog2.dismiss());
		dialog.setCancelable(false);
		dialog.create().show();
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
        new AlertDialog.Builder(context)
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

		ArrayList<Author> authorList = dbHelper.getBookAuthorList(id);

		String title;
        try (Cursor cur = dbHelper.fetchBookById(id)) {
            if (cur == null || !cur.moveToFirst())
                return R.string.unable_to_find_book;

            title = cur.getString(cur.getColumnIndex(CatalogueDBAdapter.KEY_TITLE));
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
        new AlertDialog.Builder(context)
                .setMessage(message)
                .setTitle(R.string.menu_delete_book)
                .setIcon(R.drawable.ic_menu_info)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    dbHelper.deleteBook(id);
                    onDeleted.run();
                })
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.dismiss())
                .show();
		return 0;

	}

	/**
	 * Interface for item that displays in a custom dialog list
	 */
	public interface SimpleDialogItem {
		View getView(LayoutInflater inflater);
		RadioButton getSelector(View v);
	}
	/**
	 * Interface to listen for item selection in a custom dialog list
	 */
	public interface SimpleDialogOnClickListener {
		void onClick(SimpleDialogItem item);
	}

	/**
	 * Select a custom item from a list, and call handler when/if item is selected.
	 */
	public static void selectItemDialog(LayoutInflater inflater, String message, ArrayList<SimpleDialogItem> items, SimpleDialogItem selectedItem, final SimpleDialogOnClickListener handler) {
		// Get the view and the radio group
		final View root = inflater.inflate(R.layout.select_list_dialog, null);
		TextView msg = root.findViewById(R.id.message);

		// Build the base dialog
		final AlertDialog.Builder builder = new AlertDialog.Builder(inflater.getContext()).setView(root);
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
            if (! (v instanceof RadioButton) ) {
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
		for(SimpleDialogItem item: items) {
			View v = item.getView(inflater);
			v.setBackgroundResource(android.R.drawable.list_selector_background);
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
	 * Wrapper class to present a list of files for selection
	 */
	public static void selectFileDialog(LayoutInflater inflater, String title, ArrayList<File> files, final SimpleDialogOnClickListener handler) {
		ArrayList<SimpleDialogItem> items = new ArrayList<>();
		for(File file: files) {
			items.add(new SimpleDialogFileItem(file));
		}
		selectItemDialog(inflater, title, items, null, handler);
	}

	/**
	 * Wrapper class to present a list of arbitrary objects for selection; it uses
	 * the toString() method to display a simple list.
	 */
	public static <T> void selectStringDialog(LayoutInflater inflater, String title, ArrayList<T> objects, String current, final SimpleDialogOnClickListener handler) {
		ArrayList<SimpleDialogItem> items = new ArrayList<>();
		SimpleDialogItem selectedItem = null;
		for(T o: objects) {
			SimpleDialogObjectItem item = new SimpleDialogObjectItem(o);
			if (current != null && o.toString().equalsIgnoreCase(current))
				selectedItem = item;
			items.add(item);
		}
		selectItemDialog(inflater, title, items, selectedItem, handler);
	}

	/**
	 * Simple item to manage a File object in a list of items.
	 */
	public static class SimpleDialogFileItem implements SimpleDialogItem {
		private final File mFile; 
		
		public SimpleDialogFileItem(File file) {
			mFile = file;
		}
		
		public File getFile() {
			return mFile;
		}

		public RadioButton getSelector(View v) {
			return null;
		}

		/**
		 * Get a View to display the file
		 */
		public View getView(LayoutInflater inflater) {
			// Create the view
			View v = inflater.inflate(R.layout.file_list_item, null);
			// Set the file name
			TextView name = v.findViewById(R.id.field_name);
			name.setText(mFile.getName());
			// Set the path
			TextView location = v.findViewById(R.id.field_path);
			location.setText(mFile.getParent());
			// Set the size
			TextView size = v.findViewById(R.id.field_size);
			size.setText(Utils.formatFileSize(mFile.length()));
			// Set the last modified date
			TextView update = v.findViewById(R.id.field_updated);
			update.setText(Utils.toPrettyDateTime(new Date(mFile.lastModified())));
			// Return it
			return v;
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
		public View getView(LayoutInflater inflater) {
			// Create the view
			View v = inflater.inflate(R.layout.string_list_item, null);
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
		public View getView(LayoutInflater inflater) {
			View v = super.getView(inflater);
			TextView name = v.findViewById(R.id.field_name);
			name.setCompoundDrawablesWithIntrinsicBounds(mDrawableId, 0, 0, 0);
			getSelector(v).setVisibility(View.GONE);
			return v;
		}
	}

	
}
