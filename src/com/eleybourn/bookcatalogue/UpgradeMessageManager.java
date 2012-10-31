package com.eleybourn.bookcatalogue;

import java.util.ArrayList;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

import com.eleybourn.bookcatalogue.BookCatalogueApp.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Class to manage the message that is displayed when the application is upgraded.
 * 
 * The app version is stored in preferences and when there are messages to display,
 * the getUpgradeMessage() method returns a non-empty string. When the message has
 * been acknowledged by the user, the startup activity should call setMessageAcknowledged()
 * to store the current app version in preferences and so prevent re-display of the
 * messages.
 * 
 * ENHANCE: Put these strings in strings.xml to allow translations....
 * 
 * @author pjw
 */
public class UpgradeMessageManager {
	private final static String PREF_LAST_MESSAGE = "UpgradeMessages.LastMessage";

	/** List of version-specific messages */
	private static final UpgradeMessages mMessages = new UpgradeMessages() 
	.add(118, R.string.new_in_42);
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
