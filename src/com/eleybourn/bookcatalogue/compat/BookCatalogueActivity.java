package com.eleybourn.bookcatalogue.compat;

import android.Manifest.permission;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.MenuItem;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Class introduced to reduce the future pain when we remove sherlock (once we no longer 
 * support Android 2.x), and potentially to make it easier to support two versions.
 * 
 * This activity inherits from SherlockFragmentActivity which is just a subclass of
 * the compatibility library FragmentActivity which should be fairly compatible with
 * Activity in API 11+.
 * 
 * @author pjw
 */
public abstract class BookCatalogueActivity extends AppCompatActivity {
    /** Last locale used so; cached so we can check if it has genuinely changed */
    private Locale mLastLocale = BookCatalogueApp.getPreferredLocale();
	private static final int PERMISSIONS_RESULT = 666;

	protected abstract RequiredPermission[] getRequiredPermissions();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
        	// Show home, use logo (bigger) and show title
        	bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
        	// Don't display the 'back' decoration if we are at the top
			boolean atTop = this.isTaskRoot() || getIntent().getBooleanExtra("willBeTaskRoot", false);
    		bar.setDisplayHomeAsUpEnabled(!atTop);
    		//bar.setHomeAsUpIndicator(R.drawable.ic_launcher4);
			if (atTop)
	   			bar.setLogo(R.drawable.ic_launcher4);
			else
				bar.setLogo(0);
        }
		checkPermissions();
    }

    public static class RequiredPermission {
		String permission;
		int reason;
		public RequiredPermission(String permission, int reason) {
			this.permission = permission;
			this.reason = reason;
		}
	}

	protected static RequiredPermission[] mMinimumPermissions = new RequiredPermission[] {
			new RequiredPermission(permission.READ_EXTERNAL_STORAGE, R.string.perm_read_ext),
			new RequiredPermission(permission.WRITE_EXTERNAL_STORAGE, R.string.perm_write_ext),
	};

	protected static RequiredPermission[] mInternetPermissions = new RequiredPermission[] {
			new RequiredPermission(permission.INTERNET, R.string.perm_internet),
			new RequiredPermission(permission.ACCESS_NETWORK_STATE, R.string.perm_network_state),
			new RequiredPermission(permission.ACCESS_WIFI_STATE, R.string.perm_network_state),
	};

	private void checkPermissions() {
		RequiredPermission[] permissions = getRequiredPermissions();

		// List of permissions that we need, but don't have
		ArrayList<RequiredPermission> failed = new ArrayList<>();

		// Check the minimum basic permissions
		for(RequiredPermission req: mMinimumPermissions) {
			if (ContextCompat.checkSelfPermission(this, req.permission) != PackageManager.PERMISSION_GRANTED) {
				failed.add(req);
			}
		}

		// Check the activity permissions
		for(RequiredPermission req: permissions) {
			if (ContextCompat.checkSelfPermission(this, req.permission) != PackageManager.PERMISSION_GRANTED) {
				failed.add(req);
			}
		}

		// If we have them all, exit
		if (failed.size() == 0)
			return;

    	// Now build a message and the list to request
		PackageManager pm = getPackageManager();
		StringBuilder message = new StringBuilder();
		final String[] list = new String[failed.size()];
		int pos = 0;

		for(RequiredPermission req: failed) {
			list[pos++] = req.permission;
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, req.permission)) {
				PermissionInfo info = null;
				try {
					info = pm.getPermissionInfo(req.permission, 0);
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}
				String name = info == null ? req.permission : info.loadLabel(pm).toString();
				message.append("<p>");
				message.append("<b>");
				message.append(name);
				message.append(":</b>");
				message.append("</p>");
				message.append("<p>");

				message.append(getString(req.reason));
				message.append("</p>");
			}
		}

		if (message.length() > 0) {
			// Show reasons, rinse and repeat.
			new AlertDialog.Builder(this)
					.setTitle(R.string.perm_required)
					.setMessage(Html.fromHtml(getString(R.string.perm_intro) + message.toString()))
					.setPositiveButton(R.string.ok, new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							ActivityCompat.requestPermissions(BookCatalogueActivity.this, list, PERMISSIONS_RESULT);
						}
					})
					.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialogInterface) {
							finish();
						}
					})
					.create()
					.show();
		} else {
			// Ask for them
			ActivityCompat.requestPermissions(this, list, PERMISSIONS_RESULT);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {

			case PERMISSIONS_RESULT: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED
						&& grantResults[1] == PackageManager.PERMISSION_GRANTED)
				{
					// permission was granted, yay! Do the
					// contacts-related task you need to do.
				} else {
					// permission denied, boo! Disable the
					// functionality that depends on this permission.
					this.finish();
				}
				return;
			}

			// other 'case' lines to check for other
			// permissions this app might request.
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		// Default handler for home icon
        case android.R.id.home:
        	finish();
            return true;

        default:
            return super.onOptionsItemSelected(item);	
		}
		
	}

    @Override
    /**
     * When resuming, check if locale has changed and reload activity if so.
     */
    protected void onResume() {
        reloadIfLocaleChanged();
        super.onResume();
    }

    /**
     * Reload this activity if locale has changed.
     */
    public void reloadIfLocaleChanged() {
        Locale old = mLastLocale;
        Locale curr = BookCatalogueApp.getPreferredLocale();
        if ((curr != null && !curr.equals(old)) || (curr == null && old != null)) {
            mLastLocale = curr;
            BookCatalogueApp.applyPreferredLocaleIfNecessary(this.getResources());
            Intent intent = getIntent();
            System.out.println("Restarting " + this.getClass().getSimpleName());
            finish();
            startActivity(intent);
        }
    }

}
