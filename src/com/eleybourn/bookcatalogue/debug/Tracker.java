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
package com.eleybourn.bookcatalogue.debug;

import java.util.Date;

import org.acra.ErrorReporter;

import com.eleybourn.bookcatalogue.utils.Utils;

public class Tracker {

	public enum States {
		Enter,
		Exit,
		Running
	}
	private static class Event {
		public String activityClass;
		public String action;
		public States state;
		public Date date;
		public Event(Object a, String action, States state) {
			activityClass = a.getClass().getSimpleName();
			this.action = action;
			this.state = state;
			date = new Date();
		}
		
		public String getInfo() {
			return Utils.toSqlDateTime(date) + ": " + activityClass + " " + action + " " + state;
		}
	}
	
	private final static int K_MAX_EVENTS = 100;
	private static Event[] mEventBuffer = new Event[K_MAX_EVENTS];
	private static int mNextEventBufferPos = 0;

	public static void enterOnActivityCreated(Object a) {
		handleEvent(a,"OnActivityCreated (" + a.toString() + ")", States.Enter);				
	}
	public static void exitOnActivityCreated(Object a) {
		handleEvent(a,"OnActivityCreated (" + a.toString() + ")", States.Exit);				
	}

	public static void enterOnActivityResult(Object a, int requestCode, int resultCode) {
		handleEvent(a,"OnActivityResult[" + requestCode + "," + resultCode + "] (" + a.toString() + ")", States.Enter);				
	}
	public static void exitOnActivityResult(Object a, int requestCode, int resultCode) {
		handleEvent(a,"OnActivityResult[" + requestCode + "," + resultCode + "] (" + a.toString() + ")", States.Exit);				
	}

	public static void enterOnCreate(Object a) {
		handleEvent(a,"OnCreate (" + a.toString() + ")", States.Enter);
	}
	public static void exitOnCreate(Object a) {
		handleEvent(a,"OnCreate (" + a.toString() + ")", States.Exit);		
	}
	public static void enterOnCreateView(Object a) {
		handleEvent(a,"OnCreateView (" + a.toString() + ")", States.Enter);
	}
	public static void exitOnCreateView(Object a) {
		handleEvent(a,"OnCreateView (" + a.toString() + ")", States.Exit);
	}
	public static void enterOnDestroy(Object a) {
		handleEvent(a,"OnDestroy", States.Enter);
	}
	public static void exitOnDestroy(Object a) {
		handleEvent(a,"OnDestroy", States.Exit);		
	}
	public static void enterOnPause(Object a) {
		handleEvent(a,"OnPause (" + a.toString() + ")", States.Enter);		
	}
	public static void exitOnPause(Object a) {
		handleEvent(a,"OnPause (" + a.toString() + ")", States.Exit);				
	}
	public static void enterOnResume(Object a) {
		handleEvent(a,"OnResume (" + a.toString() + ")", States.Enter);				
	}
	public static void exitOnResume(Object a) {
		handleEvent(a,"OnResume (" + a.toString() + ")", States.Exit);						
	}
	public static void enterOnSaveInstanceState(Object a) {
		handleEvent(a,"OnSaveInstanceState", States.Enter);		
	}
	public static void exitOnSaveInstanceState(Object a) {
		handleEvent(a,"OnSaveInstanceState", States.Exit);
	}
	public static void enterOnRestoreInstanceState(Object a) {
		handleEvent(a,"OnRestoreInstanceState", States.Enter);		
	}
	public static void exitOnRestoreInstanceState(Object a) {
		handleEvent(a,"OnRestoreInstanceState", States.Exit);		
	}
	
	public static void handleEvent(Object o, String name, States type) {
		Event e = new Event(o, name, type);
		mEventBuffer[mNextEventBufferPos] = e;
		ErrorReporter.getInstance().putCustomData("History-" + mNextEventBufferPos, e.getInfo());
		mNextEventBufferPos = (mNextEventBufferPos + 1) % K_MAX_EVENTS;
	}
	
	public static String getEventsInfo() {
		StringBuilder s = new StringBuilder("Recent Events:\n");
		int pos = mNextEventBufferPos;
		for(int i = 0; i < K_MAX_EVENTS; i++) {
			int index = (pos + i) % K_MAX_EVENTS;
			Event e = mEventBuffer[index];
			if (e != null) {
				s.append(e.getInfo());
				s.append("\n");
			}
		}
		return s.toString();
	}
}
