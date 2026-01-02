/*
 * @copyright 2011 Philip Warner
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

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.AdminUpdateFromInternet.FieldUsages.Usages;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.LinkedHashMap;
import java.util.Objects;

public class AdminUpdateFromInternet extends ActivityWithTasks {

    private final FieldUsages mFieldUsages = new FieldUsages();
    private long mUpdateSenderId = 0;
    final ManagedTask.TaskListener mThumbnailsHandler = t -> {
        mUpdateSenderId = 0;
        finish();
    };
    private SharedPreferences mPrefs = null;

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return mInternetPermissions;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.admin_update_from_internet);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            setSupportActionBar(topAppBar);
            topAppBar.setTitle(R.string.app_name);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

            mPrefs = getSharedPreferences("bookCatalogue", android.content.Context.MODE_PRIVATE);
            setupFields();
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param field    Field name to use in FieldUsages
     * @param visField Field name to check for visibility. If null, use field.
     * @param stringId ID of field label string
     * @param usage    Usage to apply.
     */
    private void addIfVisible(String field, String visField, int stringId, Usages usage, boolean canAppend) {
        if (visField == null || visField.trim().isEmpty())
            visField = field;
        if (mPrefs.getBoolean(AdminFieldVisibility.prefix + visField, true))
            mFieldUsages.put(new FieldUsage(field, stringId, usage, canAppend));
    }

    /**
     * This function builds the manage field visibility by adding onClick events
     * to each field checkbox
     */
    public void setupFields() {
        addIfVisible(CatalogueDBAdapter.KEY_AUTHOR_ARRAY, CatalogueDBAdapter.KEY_AUTHOR_ID, R.string.label_author, Usages.ADD_EXTRA, true);
        addIfVisible(CatalogueDBAdapter.KEY_TITLE, null, R.string.label_title, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_ISBN, null, R.string.label_isbn, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_THUMBNAIL, null, R.string.label_thumbnail, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_SERIES_ARRAY, CatalogueDBAdapter.KEY_SERIES_NAME, R.string.label_series, Usages.ADD_EXTRA, true);
        addIfVisible(CatalogueDBAdapter.KEY_PUBLISHER, null, R.string.label_publisher, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_DATE_PUBLISHED, null, R.string.label_date_published, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_PAGES, null, R.string.label_pages, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_LIST_PRICE, null, R.string.label_list_price, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_FORMAT, null, R.string.label_format, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_DESCRIPTION, null, R.string.label_description, Usages.COPY_IF_BLANK, false);
        addIfVisible(CatalogueDBAdapter.KEY_GENRE, null, R.string.label_genre, Usages.COPY_IF_BLANK, false);
        addIfVisible(DatabaseDefinitions.DOM_LANGUAGE.name, null, R.string.label_language, Usages.COPY_IF_BLANK, false);

        // Display the list of fields
        LinearLayout parent = findViewById(R.id.manage_fields_scrollview);
        for (FieldUsage usage : mFieldUsages.values()) {
            //Create the LinearLayout to hold each row
            LinearLayout ll = new LinearLayout(this);
            ll.setPadding(5, 0, 0, 0);

            //Create the checkbox
            CheckBox cb = new CheckBox(this);
            cb.setChecked(usage.selected);
            ViewTagger.setTag(cb, usage);
            cb.setId(R.id.fieldCheckbox);
            //add override capability
            cb.setOnClickListener(v -> {
                CheckBox this_checkbox = (CheckBox) v;
                if (!this_checkbox.isChecked() && this_checkbox.getText().toString().contains(getResources().getString(R.string.option_usage_copy_if_blank))) {
                    FieldUsage usage1 = (FieldUsage) ViewTagger.getTag(this_checkbox);
                    assert usage1 != null;
                    String extra;
                    String text = getResources().getString(usage1.stringId);
                    if (usage1.canAppend) {
                        extra = getResources().getString(R.string.option_usage_add_extra);
                        this_checkbox.setText(text + " (" + extra + ")");
                        this_checkbox.setChecked(true); //reset to checked
                        usage1.usage = Usages.ADD_EXTRA;
                    } else {
                        extra = getResources().getString(R.string.option_usage_overwrite);
                        this_checkbox.setText(text + " (" + extra + ")");
                        this_checkbox.setChecked(true); //reset to checked
                        usage1.usage = Usages.OVERWRITE;
                    }
                    ViewTagger.setTag(this_checkbox, usage1);
                } else if (this_checkbox.getText().toString().contains(getResources().getString(R.string.option_usage_add_extra))) {
                    FieldUsage usage1 = (FieldUsage) ViewTagger.getTag(this_checkbox);
                    String extra = getResources().getString(R.string.option_usage_overwrite);
                    assert usage1 != null;
                    String text = getResources().getString(usage1.stringId);
                    this_checkbox.setText(text + " (" + extra + ")");
                    this_checkbox.setChecked(true); //reset to checked
                    usage1.usage = Usages.OVERWRITE;
                    ViewTagger.setTag(this_checkbox, usage1);
                } else if (this_checkbox.getText().toString().contains(getResources().getString(R.string.option_usage_overwrite))) {
                    FieldUsage usage1 = (FieldUsage) ViewTagger.getTag(this_checkbox);
                    String extra = getResources().getString(R.string.option_usage_copy_if_blank);
                    assert usage1 != null;
                    String text = getResources().getString(usage1.stringId);
                    this_checkbox.setText(text + " (" + extra + ")");
                    usage1.usage = Usages.COPY_IF_BLANK;
                    ViewTagger.setTag(this_checkbox, usage1);
                }
            });
            cb.setTextAppearance(android.R.style.TextAppearance_Large);
            String text = getResources().getString(usage.stringId);
            String extra;
            switch (usage.usage) {
                case ADD_EXTRA:
                    extra = getResources().getString(R.string.option_usage_add_extra);
                    break;
                case COPY_IF_BLANK:
                    extra = getResources().getString(R.string.option_usage_copy_if_blank);
                    break;
                case OVERWRITE:
                    extra = getResources().getString(R.string.option_usage_overwrite);
                    break;
                default:
                    throw new RuntimeException("Unknown Usage");
            }
            cb.setText(text + " (" + extra + ")");
            //tv.setPadding(0, 5, 0, 0);
            ll.addView(cb);

            //Add the LinearLayout to the parent
            parent.addView(ll);
        }

        Button confirmBtn = findViewById(R.id.button_confirm);
        confirmBtn.setOnClickListener(v -> {
            // Get the selections the user made
            if (readUserSelections() == 0) {
                Toast.makeText(AdminUpdateFromInternet.this, R.string.alert_select_min_1_field, Toast.LENGTH_LONG).show();
                return;
            }

            // If they have selected thumbnails, check if they want to download ALL.
            boolean thumbnail_check = false;
            try {
                thumbnail_check = Objects.requireNonNull(mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL)).selected;
            } catch (NullPointerException e) {
                Logger.logError(e);
            }

            if (thumbnail_check) {
                // Verify - this can be a dangerous operation
                new AlertDialog.Builder(AdminUpdateFromInternet.this)
                        .setTitle(R.string.label_update_fields)
                        .setMessage(R.string.description_overwrite_thumbnail)
                        .setIcon(R.drawable.ic_menu_info)
                        // Positive Button (formerly setButton) -> Yes
                        .setPositiveButton(R.string.option_yes, (dialog, which) -> {
                            Objects.requireNonNull(mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL)).usage = Usages.OVERWRITE;
                            startUpdate();
                        })
                        // Neutral Button (formerly setButton3) -> No
                        .setNeutralButton(R.string.option_no, (dialog, which) -> {
                            Objects.requireNonNull(mFieldUsages.get(CatalogueDBAdapter.KEY_THUMBNAIL)).usage = Usages.COPY_IF_BLANK;
                            startUpdate();
                        })
                        // Negative Button (formerly setButton2) -> Cancel
                        .setNegativeButton(R.string.button_cancel, (dialog, which) -> {
                            // do nothing
                        })
                        .show();
            } else {
                startUpdate();
            }
        });
    }

    private int readUserSelections() {
        LinearLayout parent = findViewById(R.id.manage_fields_scrollview);
        int nChildren = parent.getChildCount();
        int nSelected = 0;
        for (int i = 0; i < nChildren; i++) {
            View v = parent.getChildAt(i);
            CheckBox cb = v.findViewById(R.id.fieldCheckbox);
            if (cb != null) {
                FieldUsage usage = (FieldUsage) ViewTagger.getTag(cb);
                assert usage != null;
                usage.selected = cb.isChecked();
                if (usage.selected)
                    nSelected++;
            }
        }
        return nSelected;
    }

    private void startUpdate() {
        UpdateThumbnailsThread t = new UpdateThumbnailsThread(getTaskManager(), mFieldUsages, mThumbnailsHandler);
        mUpdateSenderId = t.getSenderId();
        UpdateThumbnailsThread.getMessageSwitch().addListener(mUpdateSenderId, mThumbnailsHandler, false);
        t.start();
    }

    @Override
    protected void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();
        if (mUpdateSenderId != 0)
            UpdateThumbnailsThread.getMessageSwitch().removeListener(mUpdateSenderId, mThumbnailsHandler);
        Tracker.exitOnPause(this);
    }

    @Override
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mUpdateSenderId != 0)
            UpdateThumbnailsThread.getMessageSwitch().addListener(mUpdateSenderId, mThumbnailsHandler, true);
        Tracker.exitOnResume(this);
    }

    /**
     * Class to manage a collection of fields and the rules for importing them.
     * Inherits from LinkedHashMap to guarantee iteration order.
     *
     * @author Philip Warner
     */
    static public class FieldUsages extends LinkedHashMap<String, FieldUsage> {
        private static final long serialVersionUID = 1L;

        public FieldUsage put(FieldUsage usage) {
            this.put(usage.fieldName, usage);
            return usage;
        }

        public enum Usages {COPY_IF_BLANK, ADD_EXTRA, OVERWRITE}

    }

    public static class FieldUsage {
        final String fieldName;
        final int stringId;
        final boolean canAppend;
        Usages usage;
        boolean selected;

        FieldUsage(String name, int id, Usages usage, boolean canAppend) {
            this.fieldName = name;
            this.stringId = id;
            this.usage = usage;
            this.selected = true;
            this.canAppend = canAppend;
        }
    }

}
