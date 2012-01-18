package com.eleybourn.bookcatalogue.goodreads;

import java.lang.ref.WeakReference;

import android.widget.ImageView;

import com.eleybourn.bookcatalogue.BcQueueManager;
import com.eleybourn.bookcatalogue.Logger;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Utils;

import net.philipwarner.taskqueue.QueueManager;
import net.philipwarner.taskqueue.Listeners.OnTaskChangeListener;
import net.philipwarner.taskqueue.Listeners.TaskActions;
import net.philipwarner.taskqueue.Task;

/**
 * Class to store the 'work' data returned via a goodreads search. It also creates a background task
 * to find images and monitors related tasks for completion.
 * 
 * @author Grunthos
 *
 */
public class GoodreadsWork implements OnTaskChangeListener {
	public String title;
	public String imageUrl;
	public String smallImageUrl;
	public Long bookId;
	public Long workId;
	public Long pubDay;
	public Long pubMonth;
	public Long pubYear;
	public Double rating;
	public Long authorId;
	public String authorName;
	public byte[] image = null;
	public long imageTaskId = 0;
	//private static Integer mIdCounter = 0;
	//private int mId = 0;
	private WeakReference<ImageView> mImageView = null;

	public GoodreadsWork() {
		super();
		//mId = ++mIdCounter;
	}

	/**
	 * Listener called when a task has changed state
	 */
	@Override
	public void onTaskChange(Task task, TaskActions action) {
		if (action == TaskActions.completed) {
			boolean isMine = false;
			// Any code that looks at or requests image info needs to sync on this object
			synchronized(this) {
				// Comparison MUST be inside sync() because otherwise imageTaskId may not have been set
				isMine = (task.getId() == imageTaskId);
				if (isMine) {
					image = ((GetImageTask)task).getBytes();
					//System.out.println("Work(" + mId + "):" + ((GetImageTask)task).getDescription());
					// Stop monitoring
					QueueManager.getQueueManager().unregisterTaskListener(this);
					ImageView v = mImageView.get();
					if (v != null && ((Long)v.getTag(R.id.TAG_TASK_ID)) == task.getId())
						v.setImageBitmap( Utils.getBitmapFromBytes(image) );
					// Clear to say "no task running"
					imageTaskId = 0;
				}
			}
		}
	}

	/**
	 * If the cover image has been retrieved, put it in the passed view. Otherwise, request
	 * its retrieval and store a reference to the view for use when the image becomes
	 * available.
	 * 
	 * @param v		ImageView to display cover image
	 */
	public void fillImageView(ImageView v) {
		synchronized(this) {
			if (this.image == null) {
				v.setImageBitmap(null);
				// Let the work know where it is going to be displayed
				mImageView = new WeakReference<ImageView>(v);
				if (imageTaskId == 0) {
					// No task running, so request the image
					this.requestImage();
				}
				// Save the task ID in the tag for verification
				v.setTag(R.id.TAG_TASK_ID, (Long)this.imageTaskId);
			} else {
				// We already have an image, so just expand it.
				v.setImageBitmap( Utils.getBitmapFromBytes(this.image) );					
			}
		}
	}

	/**
	 * Start a background task to get the best image available and start monitoring task
	 * completions.
	 * 
	 * MUST BE CALLED FROM SYNC(THIS) CODE.
	 */
	private void requestImage() {
		// Find best (biggest) image.
		String url = null;
		if (!imageUrl.equals(""))
			url = imageUrl;
		else
			url = smallImageUrl;

		// Start monitoring
		QueueManager.getQueueManager().registerTaskListener(this);

		// Queue the task
		try {
			GetImageTask task = new GetImageTask(url);
			imageTaskId = QueueManager.getQueueManager().enqueueTask(task, BcQueueManager.QUEUE_SMALL_JOBS);
		} catch (Exception e) {
			Logger.logError(e, "Failed to create task to get image from goodreads");
		}
		
	}
}
