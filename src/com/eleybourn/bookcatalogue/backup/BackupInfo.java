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
package com.eleybourn.bookcatalogue.backup;

import java.util.Date;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to encapsulate the INFO block from an archive
 * 
 * @author pjw
 */
public class BackupInfo {
	/** Bundle retrieved from the archive for this instance */
	private Bundle mBundle;

	/** Standard INFO item */
	public static final String INFO_ARCHVERSION = "ArchVersion";
	/** Standard INFO item */
	public static final String INFO_CREATEDATE = "CreateDate";
	/** Standard INFO item */
	public static final String INFO_NUMBOOKS = "NumBooks";
	/** Standard INFO item */
	public static final String INFO_APPPACKAGE = "AppPackage";
	/** Standard INFO item */
	public static final String INFO_APPVERSIONNAME = "AppVersionName";
	/** Standard INFO item */
	public static final String INFO_APPVERSIONCODE = "AppVersionCode";
	/** Standard INFO item */
	public static final String INFO_SDK = "SDK";
	/** Standard INFO item */
	public static final String INFO_COMPATARCHIVER = "CompatArchiver";

	/** Standard INFO item */
	public static final String INFO_HAS_BOOKS = "HasBooks";
	/** Standard INFO item */
	public static final String INFO_HAS_COVERS = "HasCovers";
	/** Standard INFO item */
	public static final String INFO_HAS_DATABASE = "HasDatabase";
	/** Standard INFO item */
	public static final String INFO_HAS_SETTINGS = "HasSettings";
	/** Standard INFO item */
	public static final String INFO_HAS_BOOKLIST_STYLES = "HasBooklistStyles";
	

	/**
	 * Static method to create an INFO block based on the current environment.
	 * 
	 * @param container		The container being used (we want the version)
	 * @param db			Database
	 * @param context		Context (for package-related info)
	 * 
	 * @return				a new BackupInfo object
	 */
	public static BackupInfo createInfo(BackupContainer container, CatalogueDBAdapter db, Context context) {
		Bundle info = new Bundle();
	
		info.putInt(INFO_ARCHVERSION, container.getVersion());
		info.putInt(INFO_COMPATARCHIVER, 1);
		info.putString(INFO_CREATEDATE, Utils.toSqlDateTime(new Date()));
		info.putInt(INFO_NUMBOOKS, (int)db.getBookCount());
	    try {
	    	// Get app info
	        PackageManager manager = context.getPackageManager(); 
			PackageInfo appInfo = manager.getPackageInfo( context.getPackageName(), 0);
			info.putString(INFO_APPPACKAGE, appInfo.packageName);
			info.putString(INFO_APPVERSIONNAME, appInfo.versionName);
			info.putInt(INFO_APPVERSIONCODE, appInfo.versionCode);
		} catch (Exception e1) {
			// Not much we can do inside error logger...
		}
		info.putInt(INFO_SDK, Build.VERSION.SDK_INT);
		return new BackupInfo(info);
	}

	public BackupInfo(Bundle b) {
		mBundle = b;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public Bundle getBundle() {
		return mBundle;
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public int getArchVersion() {
		return mBundle.getInt(INFO_ARCHVERSION);
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public int getCompatArchiver() {
		return mBundle.getInt(INFO_COMPATARCHIVER);
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public Date getCreateDate() {
		return Utils.parseDate(mBundle.getString(INFO_CREATEDATE));
	}
	
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public int getNumBooks() {
		return mBundle.getInt(INFO_NUMBOOKS);		
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public String getAppPackage() {
		return mBundle.getString(INFO_APPPACKAGE);		
	}
	
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public String getAppVersionName() {
		return mBundle.getString(INFO_APPVERSIONNAME);		
	}
	
	/**
	 * Accessor
	 * 
	 * @return
	 */
	public int getAppVersionCode() {
		return mBundle.getInt(INFO_APPVERSIONCODE);		
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public int getSdk() {
		return mBundle.getInt(INFO_APPVERSIONCODE);		
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean hasBooks() {
		return mBundle.getBoolean(INFO_HAS_BOOKS);
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean hasCovers() {
		return mBundle.getBoolean(INFO_HAS_COVERS);
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean hasDatabase() {
		return mBundle.getBoolean(INFO_HAS_DATABASE);
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean hasPreferences() {
		return mBundle.getBoolean(INFO_HAS_SETTINGS);
	}

	/**
	 * Accessor
	 * 
	 * @return
	 */
	public boolean hasBooklistStyles() {
		return mBundle.getBoolean(INFO_HAS_BOOKLIST_STYLES);
	}
	
	public int getBookCount() {
		return mBundle.getInt(INFO_NUMBOOKS);
	}

}
