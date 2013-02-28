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

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;

/**
 * Class to manage the message that is displayed when the application is upgraded.
 * 
 * The app version is stored in preferences and when there are messages to display,
 * the getUpgradeMessage() method returns a non-empty string. When the message has
 * been acknowledged by the user, the startup activity should call setMessageAcknowledged()
 * to store the current app version in preferences and so prevent re-display of the
 * messages.
 * 
 * @author pjw
 */
public class UpgradeMessageManager {
	private final static String PREF_LAST_MESSAGE = "UpgradeMessages.LastMessage";

	// New messages go here in order of increasing version ID.
	/** List of version-specific messages */
	private static final UpgradeMessages mMessages = new UpgradeMessages() 
	
	.add(124, R.string.new_in_42)
	.add(125, R.string.new_in_421)
	.add(126, R.string.new_in_422)
	.add(128, R.string.new_in_423)
	.add(134, R.string.new_in_424)
	.add(142, R.string.new_in_500)
	.add(145, R.string.new_in_502)
	.add(146, R.string.new_in_503)
	.add(147, R.string.new_in_504)
	;

	
	//* Internal: prep for fragments by separating message delivery from activities
	//* Internal: one database connection for all activities and threads
	;

	/**
	 * Class to store one version-specific message
	 * 
	 * @author pjw
	 */
	private static class UpgradeMessage {
		int version;
		int messageId;
		UpgradeMessage(int version, int messageId) {
			this.version = version;
			this.messageId = messageId;
		}
		public String getMessage() {
			return BookCatalogueApp.getResourceString(messageId);
		}
	}

	/**
	 * Class to manage a list of class-specific messages.
	 * 
	 * @author pjw
	 */
	private static class UpgradeMessages extends ArrayList<UpgradeMessage> {
		private static final long serialVersionUID = -1646609828897186899L;

		public UpgradeMessages add(int version, int messageId) {
			this.add(new UpgradeMessage(version, messageId));
			return this;
		}
	}

	/** The message generated for this instance; will be set first time it is generated */
	private static String mMessage = null;

	/**
	 * Get the upgrade message for the running app instance; caches the result for later use.
	 * 
	 * @return	Upgrade message (or blank string)
	 */
	public static String getUpgradeMessage() {
		// If cached version exists, return it
		if (mMessage != null)
			return mMessage;

		// Builder for message
		StringBuilder message = new StringBuilder();

		// See if we have a saved version id. If not, it's either a new install, or 
		// an older install.
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

		long lastVersion = prefs.getInt(PREF_LAST_MESSAGE, 0);
		if (lastVersion == 0) {
			// It's either a new install, or an install using old database-based message system

			// Up until version 98, messages were handled via the CatalogueDBAdapter object, so create one
			// and see if there is a message.
			CatalogueDBAdapter tmpDb = new CatalogueDBAdapter(BookCatalogueApp.context);
			try {
				// On new installs, there is no upgrade message
				if (tmpDb.isNewInstall()) {
					mMessage = "";
					setMessageAcknowledged();
					return mMessage;					
				}
				// It's not a new install, so we use the 'old' message format and set the version to the
				// last installed version that used the old method.
				lastVersion = 98;
				if (!CatalogueDBAdapter.message.equals(""))
					message.append("<p>" + CatalogueDBAdapter.message + "</p>");
			} finally {
				tmpDb.close();				
			}
		}

		boolean first = true;
		for(UpgradeMessage m: mMessages) {
			if (m.version > lastVersion) {
				if (!first)
					message.append("\n");
				message.append(m.getMessage());
				first = false;
			}
		}

		mMessage = message.toString().replace("\n", "<br/>");
		return mMessage;
	}
	
	public static void setMessageAcknowledged() {
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		try {
			Context c = BookCatalogueApp.context;
			int currVersion = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
			prefs.setInt(PREF_LAST_MESSAGE, currVersion);
		} catch (NameNotFoundException e) {
			Logger.logError(e, "Failed to get package version code");
		}		
	}
}
