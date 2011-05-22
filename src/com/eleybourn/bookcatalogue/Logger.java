/*
* @copyright 2011 Evan Leybourn
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.Build;
import android.util.Log;

public class Logger {
	
	public static void logError(Exception e) {
		logError(e, "");
	}
	/**
	 * Write the exception stacktrace to the error log file 
	 * @param e The exception to log
	 */
	public static void logError(Exception e, String msg) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();
		String now = dateFormat.format(date);
		
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		
		String error = "An Exception Occured @ " + now + "\n" + 
			"In Phone " + Build.MODEL + " (" + Build.VERSION.SDK_INT + ") \n" + 
			msg + "\n" + 
			sw.toString();
		//Log.e("BookCatalogue", error);
		
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Utils.ERRORLOG_FILE), "utf8"), 8192);
			out.write(error);
			out.close();
		} catch (Exception e1) {
			// do nothing - we can't log an error in the error logger. (and we don't want to FC the app)
		}
	}

	/**
	 * Clear the error log each time the app is started; preserve previous if non-empty
	 */
	public static void clearLog() {
		try {
			try { 
				File orig = new File(Utils.ERRORLOG_FILE);
				File backup = new File(Utils.ERRORLOG_FILE + ".bak");
				if (orig.exists() && orig.length() > 0)
					orig.renameTo(backup);
			} catch (Exception e) {
				// Ignore backup failure...
			}
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Utils.ERRORLOG_FILE), "utf8"), 8192);
			out.write("");
			out.close();
		} catch (Exception e1) {
			// do nothing - we can't log an error in the error logger. (and we don't want to FC the app)
		}
	}
	
}
