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

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/**
 * Utilities related to building an AlertDialog that is just a list of clickable options.
 * 
 * @author Philip Warner
 */
public class AlertDialogUtils {
	/**
	 * Class to make building a 'context menu' from an AlertDialog a little easier.
	 * Used in Event.buildDialogItems and related Activities.
	 * 
	 * @author Philip Warner
	 *
	 */
	public static class AlertDialogItem implements CharSequence {
		public String name;
		public Runnable handler;
		public AlertDialogItem(String name, Runnable handler ) {
			this.name = name;
			this.handler = handler;
		}
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
		@Override
		public CharSequence subSequence(int start, int end) {
			return name.subSequence(start, end);
		}
	}

	/**
	 * Utility routine to display an array of ContextDialogItems in an alert.
	 * 
	 * @param title		Title of Alert
	 * @param items		Items to display
	 */
	public static void showContextDialogue(Context context, String title, ArrayList<AlertDialogItem> items) {
		if (!items.isEmpty()) {
			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setTitle(title);

			final AlertDialogItem[] itemArray = new AlertDialogItem[items.size()];
			items.toArray(itemArray);
	
			builder.setItems(itemArray, (dialog, item) -> itemArray[item].handler.run());
			AlertDialog alert = builder.create();	
			alert.show();
		}		
	}

}
