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
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.data.Bookshelf;
import com.eleybourn.bookcatalogue.data.BookshelfAdapter;
import com.eleybourn.bookcatalogue.data.BookshelfViewModel;
import com.google.android.material.appbar.MaterialToolbar;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class AdminBookshelf extends BookCatalogueActivity {
    private static final int INSERT_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;

    private BookshelfViewModel mViewModel;
    private BookshelfAdapter mAdapter;
    private Bookshelf mSelectedBookshelfForContext; // To hold selection for delete

    // Define Result Launchers
    private ActivityResultLauncher<Intent> editBookshelfLauncher;
    private ActivityResultLauncher<Intent> createBookshelfLauncher;

    @Override
    protected RequiredPermission[] getRequiredPermissions() {
        return new RequiredPermission[0];
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_bookshelves);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        topAppBar.setTitle(R.string.title_manage_bookshelves);
        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Initialize Launchers
        setupResultLaunchers();

        // Setup RecyclerView
        RecyclerView recyclerView = findViewById(R.id.list); // Or specific ID in your XML
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Initialize Adapter with BOTH Click and LongClick listeners
        mAdapter = new BookshelfAdapter(
                // Short Click (Edit)
                bookshelf -> {
                    Intent i = new Intent(AdminBookshelf.this, AdminBookshelfEdit.class);
                    i.putExtra(CatalogueDBAdapter.KEY_ROW_ID, bookshelf.id);
                    editBookshelfLauncher.launch(i);
                },
                // Long Click (Prepare for Delete)
                (bookshelf, view, menu) -> {
                    mSelectedBookshelfForContext = bookshelf;
                    menu.add(0, DELETE_ID, 0, R.string.menu_delete_bs);
                }
        );

        recyclerView.setAdapter(mAdapter);

        // Initialize ViewModel
        mViewModel = new ViewModelProvider(this).get(BookshelfViewModel.class);

        // Observe Data (Replaces fillBookshelves/CursorAdapter)
        mViewModel.getAllBookshelves().observe(this, bookshelves -> {
            // Update the cached copy of the words in the adapter.
            mAdapter.submitList(bookshelves);

            View emptyView = findViewById(R.id.empty);
            if (emptyView != null) {
                emptyView.setVisibility(bookshelves.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(bookshelves.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

    }

    private void setupResultLaunchers() {
        // Launcher for Editing
        editBookshelfLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // ViewModel observes LiveData, so data updates automatically.
                    // You can show a success message here if needed.
                });

        // Launcher for Creating
        createBookshelfLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Data updates automatically via LiveData
                });
    }


    /**
     * Fix background
     */
    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, INSERT_ID, 0, R.string.menu_insert_bs)
                .setIcon(R.drawable.ic_menu_new)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == INSERT_ID) {
            createBookshelf();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == DELETE_ID) {
            if (mSelectedBookshelfForContext != null) {
                if (mSelectedBookshelfForContext.id == 1) {
                    Toast.makeText(this, R.string.delete_1st_bs, Toast.LENGTH_LONG).show();
                } else {
                    mViewModel.deleteBookshelf(mSelectedBookshelfForContext.id);
                }
            }
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void createBookshelf() {
        Intent i = new Intent(this, AdminBookshelfEdit.class);
        createBookshelfLauncher.launch(i);
    }
}
