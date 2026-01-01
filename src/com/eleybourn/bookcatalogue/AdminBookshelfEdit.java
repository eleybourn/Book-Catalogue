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

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.data.Bookshelf;
import com.eleybourn.bookcatalogue.data.BookshelfViewModel;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.material.appbar.MaterialToolbar;

public class AdminBookshelfEdit extends BookCatalogueActivity {

	private EditText mBookshelfText;
    private Long mRowId;
    private BookshelfViewModel mViewModel;


    @Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);

            // Initialize ViewModel
            mViewModel = new ViewModelProvider(this).get(BookshelfViewModel.class);

            setContentView(R.layout.admin_bookshelf_edit);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            setSupportActionBar(topAppBar);
            topAppBar.setTitle(R.string.title_edit_bookshelf);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

			mBookshelfText = findViewById(R.id.field_bookshelf);
            Button mConfirmButton = findViewById(R.id.button_confirm);
            Button mCancelButton = findViewById(R.id.button_cancel);


            // Get ID from Intent
			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROW_ID) : null;
			if (mRowId == null) {
				Bundle extras = getIntent().getExtras();
				mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROW_ID) : null;
			}

            // Populate Fields
            if (mRowId != null && mRowId > 0) {
                mConfirmButton.setText(R.string.confirm_save_bs);

                // Fetch data asynchronously using the ViewModel
                // You will need to add 'getBookshelfById(long id)' to your ViewModel/DAO
                mViewModel.getBookshelfById(mRowId).observe(this, bookshelf -> {
                    if (bookshelf != null) {
                        mBookshelfText.setText(bookshelf.name);
                    }
                });
            } else {
                mConfirmButton.setText(R.string.confirm_add_bs);
            }

			mConfirmButton.setOnClickListener(view -> {
                saveState();
                setResult(RESULT_OK);
                finish();
            });
			
			mCancelButton.setOnClickListener(view -> {
                setResult(RESULT_OK);
                finish();
            });
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		try {
			outState.putLong(CatalogueDBAdapter.KEY_ROW_ID, mRowId);
		} catch (Exception e) {
			//do nothing
		}
	}

	private void saveState() {
		String name = mBookshelfText.getText().toString().trim();
        // Prevent saving empty names
        if (name.isEmpty()) return;

        Bookshelf bookshelf = new Bookshelf();
        bookshelf.name = name;

        if (mRowId != null && mRowId > 0) {
            // Update existing
            bookshelf.id = mRowId;
            mViewModel.update(bookshelf);
        } else {
            // Create new
            mViewModel.insert(bookshelf);
        }

	}
}
