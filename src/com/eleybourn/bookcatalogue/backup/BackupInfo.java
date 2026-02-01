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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.Date;

/**
 * Class to encapsulate the INFO block from an archive
 *
 * @author pjw
 */
public class BackupInfo {
    /**
     * Standard INFO item
     */
    public static final String INFO_ARCH_VERSION = "ArchVersion";
    /**
     * Standard INFO item
     */
    public static final String INFO_CREATE_DATE = "CreateDate";
    /**
     * Standard INFO item
     */
    public static final String INFO_NUM_BOOKS = "NumBooks";
    /**
     * Standard INFO item
     */
    public static final String INFO_NUM_COVERS = "NumCovers";
    /**
     * Standard INFO item
     */
    public static final String INFO_APP_PACKAGE = "AppPackage";
    /**
     * Standard INFO item
     */
    public static final String INFO_APP_VERSION_NAME = "AppVersionName";
    /**
     * Standard INFO item
     */
    public static final String INFO_APP_VERSION_CODE = "AppVersionCode";
    /**
     * Standard INFO item
     */
    public static final String INFO_SDK = "SDK";
    /**
     * Standard INFO item
     */
    public static final String INFO_COMPAT_ARCHIVER = "CompatArchiver";
    /**
     * Bundle retrieved from the archive for this instance
     */
    private final Bundle mBundle;


    public BackupInfo(Bundle b) {
        mBundle = b;
    }

    /**
     * Static method to create an INFO block based on the current environment.
     *
     * @param container The container being used (we want the version)
     * @param context   Context (for package-related info)
     * @return                a new BackupInfo object
     */
    public static BackupInfo createInfo(BackupContainer container, Context context, int bookCount, int coverCount) {
        Bundle info = new Bundle();

        info.putInt(INFO_ARCH_VERSION, container.getVersion());
        info.putInt(INFO_COMPAT_ARCHIVER, 1);
        info.putString(INFO_CREATE_DATE, Utils.toSqlDateTime(new Date()));
        info.putInt(INFO_NUM_BOOKS, bookCount);
        info.putInt(INFO_NUM_COVERS, coverCount);
        try {
            // Get app info
            PackageManager manager = context.getPackageManager();
            PackageInfo appInfo = manager.getPackageInfo(context.getPackageName(), 0);
            info.putString(INFO_APP_PACKAGE, appInfo.packageName);
            info.putString(INFO_APP_VERSION_NAME, appInfo.versionName);
            info.putInt(INFO_APP_VERSION_CODE, appInfo.versionCode);
        } catch (Exception e1) {
            // Not much we can do inside error logger...
        }
        info.putInt(INFO_SDK, Build.VERSION.SDK_INT);
        return new BackupInfo(info);
    }

    /**
     * Accessor
     */
    public Bundle getBundle() {
        return mBundle;
    }

    /**
     * Accessor
     */
    public Date getCreateDate() {
        return Utils.parseDate(mBundle.getString(INFO_CREATE_DATE));
    }

    /**
     * Accessor
     */
    public int getAppVersionCode() {
        return mBundle.getInt(INFO_APP_VERSION_CODE);
    }

    /**
     * Accessor
     */
    public boolean hasCoverCount() {
        return mBundle.containsKey(INFO_NUM_COVERS);
    }

    /**
     * Accessor
     */
    public int getBookCount() {
        return mBundle.getInt(INFO_NUM_BOOKS);
    }

    /**
     * Accessor
     */
    public int getCoverCount() {
        return mBundle.getInt(INFO_NUM_COVERS);
    }

}
