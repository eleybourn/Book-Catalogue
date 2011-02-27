package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;

/**
 * Class to used as a bace class for any Activity that wants to run one or more threads that
 * use a ProgressDialog. Even a good choice for Activities that run a ProgressDialog because
 * PDs don't play well with orientation changes.
 * 
 * NOTE: If an activity needs to use onRetainNonConfigurationInstance() it should not use
 * this base class, but it should create, use and save a TaskManager object as this class
 * does. 
 * 
 * Part of three components that make this easier:
 *  - TaskManager -- handles the management of multiple threads sharing a progressDialog
 *  - ActivityWithTasks -- uses a TaskManager (and communicates with it) to handle progress
 *    messages for threads. Deals with orientation changes in cooperation with TaskManager.
 *  - ManagedTask -- Background task that is managed by TaskManager and uses TaskManager to 
 *    do all display activities.
 *    
 * @author Grunthos
 */
abstract public class ActivityWithTasks extends Activity {
	protected TaskManager mTaskManager = new TaskManager(this);

	/**
	 * Ensure the TaskManager is restored.
	 */
	@Override
	protected void onRestoreInstanceState(Bundle inState) {
		mTaskManager = (TaskManager) getLastNonConfigurationInstance();
		if (mTaskManager != null)
			mTaskManager.reconnect(this);
		super.onRestoreInstanceState(inState);
	}

	/**
	 * Ensure the TaskManager is saved.
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		TaskManager t = mTaskManager;
		if (mTaskManager != null) {
			mTaskManager.disconnect();
			mTaskManager = null;
		}
		return t;
	}

	abstract TaskHandler getTaskHandler(ManagedTask t);
}
