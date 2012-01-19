package com.eleybourn.bookcatalogue.goodreads;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;

import net.philipwarner.taskqueue.QueueManager;
import android.content.Context;
import android.content.Intent;

/**
 * Simple class to run in background and verify goodreads credentials then
 * display a notification based on the result.
 * 
 * This task is run as the last part of the goodreads auth process.
 * 
 * Runs in background because it can take several seconds.
 * 
 * @author Grunthos
 */
public class GoodreadsAuthorizationResultCheck extends GenericTask {
	private static final long serialVersionUID = -5502292652351148420L;

	public GoodreadsAuthorizationResultCheck() {
		super(BookCatalogueApp.getResourceString(R.string.goodreads_auth_check));
	}

	@Override
	public boolean run(QueueManager manager, Context c) {
		GoodreadsManager grMgr = new GoodreadsManager();
		Intent i = new Intent(c, GoodreadsRegister.class);
		i.setAction("android.intent.action.MAIN");
		i.addCategory(Intent.CATEGORY_LAUNCHER);
	    try {
		    grMgr.handleAuthentication();		    	
		    if (grMgr.hasValidCredentials())
				manager.showNotification(R.id.NOTIFICATION, 
										c.getString(R.string.authorized), 
										c.getString(R.string.goodreads_auth_successful), i);
			else
				manager.showNotification(R.id.NOTIFICATION, 
										c.getString(R.string.not_authorized), 
										c.getString(R.string.goodreads_auth_failed), i);
	    } catch (NotAuthorizedException e) {
			QueueManager.getQueueManager().showNotification(R.id.NOTIFICATION, 
										c.getString(R.string.not_authorized), 
										c.getString(R.string.goodreads_auth_failed), i);
	    } catch (Exception e) {
			QueueManager.getQueueManager().showNotification(R.id.NOTIFICATION, 
										c.getString(R.string.not_authorized), 
										c.getString(R.string.goodreads_auth_error) + " " + c.getString(R.string.if_the_problem_persists), i);		    	
	    }

		return true;
	}

}
