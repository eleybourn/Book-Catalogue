package com.eleybourn.bookcatalogue;

/*
 * Copyright (C) 2010 The SendMeLogs for Android project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

/**
 * A portion of this class is based on android-log-collector
 * (http://code.google.com/p/android-log-collector/)
 * 
 * @author lintonye
 */
public class LogCollector {
	public final static String LINE_SEPARATOR = System.getProperty("line.separator");//$NON-NLS-1$
	private static final String LOG_TAG = LogCollector.class.getSimpleName();
	private Context mContext;
	private String mPackageName;
	private Pattern mPattern;
	private SharedPreferences mPrefs;
	private ArrayList<String> mLastLogs;
	
	public LogCollector(Context context) {
		mContext = context;
		mPackageName = context.getPackageName();
		String pattern = String.format("(.*)E\\/AndroidRuntime\\(\\s*\\d+\\)\\:\\s*at\\s%s.*", 
			mPackageName.replace(".", "\\."));
		mPattern = Pattern.compile(pattern);
		mPrefs = mContext.getSharedPreferences("LogCollector", Context.MODE_PRIVATE);
		mLastLogs = new ArrayList<String>();
	}

	@SuppressWarnings("unchecked")
	private void collectLog(List<String> outLines, String format, String buffer, String[] filterSpecs){
		/*Usage: logcat [options] [filterspecs]
	        options include:
	          -s              Set default filter to silent.
	                          Like specifying filterspec '*:s'
	          -f <filename>   Log to file. Default to stdout
	          -r [<kbytes>]   Rotate log every kbytes. (16 if unspecified). Requires -f
	          -n <count>      Sets max number of rotated logs to <count>, default 4
	          -v <format>     Sets the log print format, where <format> is one of:

	                          brief process tag thread raw time threadtime long

	          -c              clear (flush) the entire log and exit
	          -d              dump the log and then exit (don't block)
	          -g              get the size of the log's ring buffer and exit
	          -b <buffer>     request alternate ring buffer
	                          ('main' (default), 'radio', 'events')
	          -B              output the log in binary
	        filterspecs are a series of
	          <tag>[:priority]

	        where <tag> is a log component tag (or * for all) and priority is:
	          V    Verbose
	          D    Debug
	          I    Info
	          W    Warn
	          E    Error
	          F    Fatal
	          S    Silent (supress all output)

	        '*' means '*:d' and <tag> by itself means <tag>:v

	        If not specified on the commandline, filterspec is set from ANDROID_LOG_TAGS.
	        If no filterspec is found, filter defaults to '*:I'

	        If not specified with -v, format is set from ANDROID_PRINTF_LOG
	        or defaults to "brief"*/

		outLines.clear();
		
		ArrayList<String> params = new ArrayList<String>();

		if (format == null){
			format = "time";
		}

		params.add("-v");
		params.add(format);

		if (buffer != null){
			params.add("-b");
			params.add(buffer);
		}

		if (filterSpecs != null){
			for (String filterSpec : filterSpecs){
				params.add(filterSpec);
			}
		}

		final StringBuilder log = new StringBuilder();
		try{
			ArrayList<String> commandLine = new ArrayList<String>();
			commandLine.add("logcat");//$NON-NLS-1$
			commandLine.add("-d");//$NON-NLS-1$
			commandLine.addAll(params);

			Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;
			while ((line = bufferedReader.readLine()) != null){ 
				outLines.add(line);
			}
		} 
		catch (IOException e){
			Log.e(LOG_TAG, String.format("collectAndSendLog failed - format:%s, buffer:%s, filterSpecs:%s", 
					format, buffer, filterSpecs), e);
		} 

	}

	public boolean hasForceCloseHappened() {
		String[] filterSpecs = {"*:E"};  //{"AndroidRuntime:E"}; // for some reason, AndroidRuntime:E lists all logs
		ArrayList<String> lines = new ArrayList<String>();
		collectLog(lines, "time", null, filterSpecs);
		if (lines.size()>0) {
			boolean forceClosedSinceLastCheck = false;
			for (String line:lines) {
				final Matcher matcher = mPattern.matcher(line);
				boolean isMyStackTrace = matcher.matches();
				SharedPreferences prefs = mPrefs;
				if (isMyStackTrace) {
					String timestamp = matcher.group(1); 
					boolean appeared = prefs.getBoolean(timestamp , false);
//					Log.d(LOG_TAG, lineKey);
					if (!appeared) {
//						Log.d(LOG_TAG, "!appeared");
						forceClosedSinceLastCheck = true;
						prefs.edit().putBoolean(timestamp, true).commit();
					}
				}
			}
			return forceClosedSinceLastCheck;
		} else
			return false;
	}

	private String collectPhoneInfo() {
		return String.format(
				"Carrier:%s\nModel:%s\nFirmware:%s\n",
				Build.BRAND, 
//				Build.DEVICE, 
//				Build.BOARD, 
//				Build.DISPLAY, 
				Build.MODEL, 
//				Build.PRODUCT, 
				Build.VERSION.RELEASE);
	}

	public boolean collect() {
		ArrayList<String> lines = mLastLogs;
		collectLog(lines, null, null, null);
		return lines.size()>0;
	}

	public void sendLog(String email, String subject, String preface) {
		ArrayList<String> lines = mLastLogs;
		if (lines.size()>0) {
			Uri emailUri = Uri.parse("mailto:"+email);
			StringBuilder sb = new StringBuilder(preface).append(LINE_SEPARATOR);
			String phoneInfo = collectPhoneInfo();
			sb.append(LINE_SEPARATOR).append(phoneInfo);
			for (String line: lines)
				sb.append(LINE_SEPARATOR).append(line);
			String content = sb.toString();
			Intent intent = new Intent(Intent.ACTION_SENDTO, emailUri);
//			intent.putExtra(Intent.EXTRA_EMAIL, new String[]{mEmail});
			intent.putExtra(Intent.EXTRA_SUBJECT, subject);
			intent.putExtra(Intent.EXTRA_TEXT, content);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(intent);
		}
	}

}