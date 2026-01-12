/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.google.android.material.appbar.MaterialToolbar;

/**
 *
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to
 * manage bookshelves.
 *
 * @author Evan Leybourn
 */
public class MainHelp extends BookCatalogueActivity {
    public Resources res;
    private CatalogueDBAdapter mDbHelper;

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Needed for sending com.eleybourn.bookcatalogue.debug info...
            mDbHelper = new CatalogueDBAdapter(this);
            mDbHelper.open();

            setContentView(R.layout.main_help);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            topAppBar.setTitle(R.string.app_name);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

            res = getResources();

            TextView webInstructions = findViewById(R.id.helpInstructions);
            webInstructions.setOnClickListener(v -> {
                Intent loadWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.url_help)));
                startActivity(loadWeb);
            });

            TextView webpage = findViewById(R.id.helpPage);
            webpage.setOnClickListener(v -> {
                Intent loadWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.url_help)));
                startActivity(loadWeb);
            });

            Button sendInfo = findViewById(R.id.sendInfo);
            sendInfo.setOnClickListener(v -> StorageUtils.sendDebugInfo(MainHelp.this, mDbHelper));

            setupCleanupButton();

        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    private void setupCleanupButton() {
        try {
            Button cleanupBtn = findViewById(R.id.cleanupButton);
            TextView cleanupTxt = findViewById(R.id.cleanupText);

            cleanupBtn.setOnClickListener(v -> {
                StorageUtils.cleanupFiles();
                setupCleanupButton();
            });


            float space = StorageUtils.cleanupFilesTotalSize();
            if (space == 0) {
                cleanupBtn.setVisibility(View.GONE);
                cleanupTxt.setVisibility(View.GONE);
            } else {
                cleanupBtn.setVisibility(View.VISIBLE);
                cleanupTxt.setVisibility(View.VISIBLE);
                String fmt = getString(R.string.para_cleanup_files);
                String sizeStr = Utils.formatFileSize(space);
                cleanupTxt.setText(String.format(fmt, sizeStr));

            }
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCleanupButton();
    }

    /**
     * Called when activity destroyed
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mDbHelper.close();
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

}