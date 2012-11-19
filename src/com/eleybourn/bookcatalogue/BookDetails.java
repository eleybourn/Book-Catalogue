package com.eleybourn.bookcatalogue;

import java.io.File;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Activity for representing only read-only details of the book.
 * @author n.silin
 */
public class BookDetails extends Activity {

	private CatalogueDBAdapter mDbHelper;

	private long mRowId = -1;
	
	
	// Target size of a thumbnail in edit dialog and zoom dialog (bbox dim)
	private static final int MAX_EDIT_THUMBNAIL_SIZE = 256;
	private Integer mThumbEditSize;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Tracker.enterOnCreate(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.book_details);

		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);

		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();

		if (getIntent().hasExtra(CatalogueDBAdapter.KEY_ROWID)) {
			mRowId = getIntent().getLongExtra(CatalogueDBAdapter.KEY_ROWID, -1);
		}

		// See how big the display is and use that to set bitmap sizes
		android.util.DisplayMetrics mMetrics = new android.util.DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
		// Minimum of MAX_EDIT_THUMBNAIL_SIZE and 1/3rd of largest screen dimension
		mThumbEditSize = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(mMetrics.widthPixels, mMetrics.heightPixels) / 3);

		initViews();
		Tracker.exitOnCreate(this);
	}

	private void initViews() {
		initThumbnail();
	}

	private void initThumbnail() {
		ImageView iv = (ImageView) findViewById(R.id.row_img);
		Utils.fetchFileIntoImageView(getCoverFile(), iv, mThumbEditSize, mThumbEditSize, true);
	}

	/**
	 * Get the File object for the cover of the book we are editing. If the boo
	 * is new, return the standard temp file.
	 */
	private File getCoverFile() {
		if (mRowId < 0) {
			return CatalogueDBAdapter.getTempThumbnail();
		} else {
			return CatalogueDBAdapter.fetchThumbnailByUuid(mDbHelper.getBookUuid(mRowId));
		}
	}
}
