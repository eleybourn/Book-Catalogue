package com.eleybourn.bookcatalogue.goodreads;

import net.philipwarner.taskqueue.QueueManager;

import com.eleybourn.bookcatalogue.BcQueueManager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Trivial Activity to handle the callback URI; while using a broadcast receiver would be nicer, 
 * it does not seem to be possible to get them to work from web browser callbacks. So, we just
 * do the necessary processing here and exit.
 * 
 * TODO: Ideally this activity should bring the app to the foreground.
 * 
 * @author Grunthos
 */
public class GoodreadsAuthorizationActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// TODO: wrap this in try/catch and make the activity display a message if the 
		// authorization fails for some reason.

		// Get the payload and make sure it is what we expect
		Intent i = this.getIntent();
		android.net.Uri uri = i.getData();
		if (uri != null) {// && uri.toString().startsWith("BookCatalogue")) {  
			// GoodReads does not set the verifier...but we may as well check for it.
			// The verifier was added in API version 1.0A, and GoodReads seems to 
			// implement 1.0.
		    String verifier = uri.getQueryParameter("oauth_verifier");  
		    if (verifier == null)
		    	verifier = "";
		    // Handle the auth response by passing it off to a background task to check.
		    GoodreadsAuthorizationResultCheck task = new GoodreadsAuthorizationResultCheck();
		    QueueManager.getQueueManager().enqueueTask(task, BcQueueManager.QUEUE_SMALL_JOBS, 0);
		}
		this.finish();
	} 

}
