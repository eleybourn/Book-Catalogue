package com.eleybourn.bookcatalogue.debug;

import java.util.Date;

import org.acra.ErrorReporter;

import com.eleybourn.bookcatalogue.Utils;

import android.app.Activity;

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

	public static void enterOnCreate(Object a) {
		handleEvent(a,"OnCreate", States.Enter);
	}
	public static void exitOnCreate(Object a) {
		handleEvent(a,"OnCreate", States.Exit);		
	}
	public static void enterOnDestroy(Object a) {
		handleEvent(a,"OnDestroy", States.Enter);
	}
	public static void exitOnDestroy(Object a) {
		handleEvent(a,"OnDestroy", States.Exit);		
	}
	public static void enterOnPause(Object a) {
		handleEvent(a,"OnPause", States.Enter);		
	}
	public static void exitOnPause(Object a) {
		handleEvent(a,"OnPause", States.Exit);				
	}
	public static void enterOnResume(Object a) {
		handleEvent(a,"OnResume", States.Enter);				
	}
	public static void exitOnResume(Object a) {
		handleEvent(a,"OnResume", States.Exit);						
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
