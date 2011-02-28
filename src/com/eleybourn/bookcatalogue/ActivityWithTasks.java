package com.eleybourn.bookcatalogue;

import java.util.Hashtable;

import android.app.Activity;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.ManagedTask.TaskHandler;

/**
 * Class to used as a base class for any Activity that wants to run one or more threads that
 * use a ProgressDialog. Even a good choice for Activities that run a ProgressDialog because
 * PDs don't play well with orientation changes.
 * 
 * NOTE: If an activity needs to use onRetainNonConfigurationInstance() it should use the
 * overridable method onRetainNonConfigurationInstance() to save object references then use
 * getLastNonConfigurationInstance(String key) to retrieve the values.
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

	private class NonConfigInstance {
		TaskManager taskManager = mTaskManager;
		Hashtable<String,Object> extra = new Hashtable<String,Object>();
	}
	/**
	 * Ensure the TaskManager is restored.
	 */
	@Override
	protected void onRestoreInstanceState(Bundle inState) {
		NonConfigInstance info = (NonConfigInstance) getLastNonConfigurationInstance();
		if (info != null && info.taskManager != null) {
			mTaskManager = info.taskManager;
			mTaskManager.reconnect(this);				
		}
		super.onRestoreInstanceState(inState);
	}

	/**
	 * Ensure the TaskManager is saved and call a onRetainNonConfigurationInstance(store)
	 * to allow subclasses to save data as well.
	 * 
	 * Marked as final to ensure this does not get overwritten.
	 */
	@Override
	final public Object onRetainNonConfigurationInstance() {
		NonConfigInstance info = new NonConfigInstance();
		if (mTaskManager != null) {
			mTaskManager.disconnect();
			mTaskManager = null;
		}
		onRetainNonConfigurationInstance(info.extra);
		return info;
	}

	/**
	 * Method to get a value from the store that was saved in onRetainNonConfigurationInstance().
	 * 
	 * @param key	Key to lookup
	 * 
	 * @return		Object from key in hashtable
	 */
	Object getLastNonConfigurationInstance(String key) {
		NonConfigInstance info = (NonConfigInstance) getLastNonConfigurationInstance();
		if (info != null && info.extra.containsKey(key)) {
			return info.extra.get(key);
		} else {
			return null;
		}
	}

	/**
	 * Passed a task find the appropriate handler to use. Called on reconnect().
	 * 
	 * @param t		Task reference to lookup
	 * 
	 * @return		Appropriate handler for the task
	 */
	abstract TaskHandler getTaskHandler(ManagedTask t);
	
	/**
	 * Method to save names instance state objects.
	 * 
	 * @param store
	 */
	void onRetainNonConfigurationInstance(Hashtable<String,Object> store) {};
}
