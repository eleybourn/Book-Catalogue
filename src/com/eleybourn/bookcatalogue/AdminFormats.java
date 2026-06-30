package com.eleybourn.bookcatalogue;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.data.FormatAdapter;
import com.eleybourn.bookcatalogue.data.FormatCount;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

/**
 * Activity to manage unique formats in the database.
 */
public class AdminFormats extends BookCatalogueActivity {
    private static final int EDIT_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;
    private static final int MERGE_ID = Menu.FIRST + 2;

    private CatalogueDBAdapter mDbHelper;
    private FormatAdapter mAdapter;
    private String mSelectedFormat;

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_formats);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        topAppBar.setTitle(R.string.title_manage_formats);
        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        mDbHelper = new CatalogueDBAdapter(this);
        mDbHelper.open();

        RecyclerView recyclerView = findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mAdapter = new FormatAdapter(
                this::editFormat,
                (format, view, menu) -> {
                    mSelectedFormat = format;
                    menu.add(0, EDIT_ID, 0, R.string.menu_edit_format);
                    menu.add(0, MERGE_ID, 0, R.string.menu_merge_formats);
                    menu.add(0, DELETE_ID, 0, R.string.button_delete);
                }
        );
        recyclerView.setAdapter(mAdapter);

        fillData();
    }

    /**
     * Fills the list with unique formats from the database.
     */
    private void fillData() {
        ArrayList<FormatCount> formats = mDbHelper.getFormatsWithCount();
        mAdapter.setFormats(formats);

        View emptyView = findViewById(R.id.empty);
        if (emptyView != null) {
            emptyView.setVisibility(formats.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Shows a dialogue to rename a format.
     */
    private void editFormat(final String format) {
        final EditText input = new EditText(this);
        input.setText(format);
        new AlertDialog.Builder(this)
                .setTitle(R.string.label_edit_format_name)
                .setView(input)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    String newFormat = input.getText().toString().trim();
                    if (!newFormat.isEmpty() && !newFormat.equals(format)) {
                        mDbHelper.globalReplaceFormat(format, newFormat);
                        fillData();
                        Toast.makeText(AdminFormats.this, R.string.button_save, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /**
     * Shows a confirmation dialogue to delete a format.
     */
    private void deleteFormat(final String format) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.button_delete)
                .setMessage(getString(R.string.really_delete_format, format))
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    mDbHelper.globalReplaceFormat(format, "");
                    fillData();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /**
     * Shows a dialogue to select formats to merge into the selected one.
     */
    private void mergeFormats(final String targetFormat) {
        final ArrayList<String> allFormats = mDbHelper.getFormats(false);
        allFormats.remove(targetFormat);

        if (allFormats.isEmpty()) {
            Toast.makeText(this, R.string.alert_unexpected_error, Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] formatArray = allFormats.toArray(new String[0]);
        final boolean[] checkedItems = new boolean[formatArray.length];
        final ArrayList<String> selectedSources = new ArrayList<>();

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_merge_formats)
                .setMultiChoiceItems(formatArray, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedSources.add(formatArray[which]);
                    } else {
                        selectedSources.remove(formatArray[which]);
                    }
                })
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    if (!selectedSources.isEmpty()) {
                        confirmMerge(targetFormat, selectedSources);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /**
     * Shows a confirmation dialogue before merging.
     */
    private void confirmMerge(final String targetFormat, final ArrayList<String> sources) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_merge_formats)
                .setMessage(getString(R.string.confirm_merge_formats, targetFormat))
                .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                    for (String source : sources) {
                        mDbHelper.globalReplaceFormat(source, targetFormat);
                    }
                    fillData();
                    Toast.makeText(AdminFormats.this, R.string.button_save, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @Override
    public boolean onContextItemSelected(@NonNull android.view.MenuItem item) {
        if (mSelectedFormat == null) return super.onContextItemSelected(item);

        int itemId = item.getItemId();
        if (itemId == EDIT_ID) {
            editFormat(mSelectedFormat);
            return true;
        } else if (itemId == DELETE_ID) {
            deleteFormat(mSelectedFormat);
            return true;
        } else if (itemId == MERGE_ID) {
            mergeFormats(mSelectedFormat);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDbHelper != null) {
            mDbHelper.close();
        }
    }
}
