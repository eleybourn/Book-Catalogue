package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import com.eleybourn.bookcatalogue.TaskWithProgress.TaskHandler;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

abstract public class ActivityWithTasks extends Activity {
	protected TaskManager mTaskManager = new TaskManager(this);

	protected void onRestoreInstanceState(Bundle inState) {
		mTaskManager = (TaskManager) getLastNonConfigurationInstance();
		if (mTaskManager != null)
			mTaskManager.reconnect(this);
		super.onRestoreInstanceState(inState);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		TaskManager t = mTaskManager;
		if (mTaskManager != null) {
			mTaskManager.disconnect();
			mTaskManager = null;
		}
		return t;
	}

	abstract TaskHandler getTaskHandler(TaskWithProgress t);
}
