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
package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.eleybourn.bookcatalogue.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

/**
 * Utilities related to building an AlertDialog that is just a list of clickable options.
 *
 * @author Philip Warner
 */
public class AlertDialogUtils {
    /**
     * Utility routine to display an array of ContextDialogItems in an alert.
     *
     * @param title Title of Alert
     * @param items Items to display
     */
    public static void showContextDialogue(Context context, String title, ArrayList<AlertDialogItem> items) {
        if (!items.isEmpty()) {
            final AlertDialogItem[] itemArray = new AlertDialogItem[items.size()];
            items.toArray(itemArray);

            ArrayAdapter<AlertDialogItem> adapter = new ArrayAdapter<>(context, R.layout.string_list_item, R.id.field_name, items) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = view.findViewById(R.id.field_name);
                    AlertDialogItem item = getItem(position);
                    if (item != null) {
                        text.setText(item.name);
                        if (item.iconRes != 0) {
                            text.setCompoundDrawablesWithIntrinsicBounds(item.iconRes, 0, 0, 0);
                            int colorOnSurface = Utils.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface);
                            TextViewCompat.setCompoundDrawableTintList(text, ColorStateList.valueOf(colorOnSurface));
                        } else {
                            text.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                        }
                    }
                    // Hide the radio button and divider if they're there
                    View selector = view.findViewById(R.id.selector);
                    if (selector != null) selector.setVisibility(View.GONE);
                    View divider = view.findViewById(R.id.divider);
                    if (divider != null) divider.setVisibility(View.GONE);
                    return view;
                }
            };

            new MaterialAlertDialogBuilder(context)
                    .setTitle(title)
                    .setAdapter(adapter, (dialog, which) -> items.get(which).handler.run())
                    .create().show();
        }
    }

    /**
     * Class to make building a 'context menu' from an AlertDialog a little easier.
     * Used in Event.buildDialogItems and related Activities.
     *
     * @author Philip Warner
     *
     */
    public static class AlertDialogItem implements CharSequence {
        public final String name;
        public final Runnable handler;
        public final int iconRes;

        public AlertDialogItem(String name, Runnable handler) {
            this(name, 0, handler);
        }

        public AlertDialogItem(String name, int iconRes, Runnable handler) {
            this.name = name;
            this.iconRes = iconRes;
            this.handler = handler;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }

        @Override
        public char charAt(int index) {
            return name.charAt(index);
        }

        @Override
        public int length() {
            return name.length();
        }

        @NonNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return name.subSequence(start, end);
        }
    }

}
