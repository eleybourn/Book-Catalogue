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

import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.google.android.material.appbar.MaterialToolbar;

public class AdminBookshelfEdit extends BookCatalogueActivity {

	private EditText mBookshelfText;
	private Button mConfirmButton;
    private Long mRowId;
	private CatalogueDBAdapter mDbHelper;

	@Override
	protected RequiredPermission[] getRequiredPermissions() {
		return new RequiredPermission[0];
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			
			setContentView(R.layout.admin_bookshelf_edit);
            MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
            setSupportActionBar(topAppBar);
            topAppBar.setTitle(R.string.title_edit_bs);
            topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

			mBookshelfText = findViewById(R.id.field_bookshelf);
			mConfirmButton = findViewById(R.id.button_confirm);
            Button mCancelButton = findViewById(R.id.button_cancel);
			
			mRowId = savedInstanceState != null ? savedInstanceState.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			if (mRowId == null) {
				Bundle extras = getIntent().getExtras();
				mRowId = extras != null ? extras.getLong(CatalogueDBAdapter.KEY_ROWID) : null;
			}
			populateFields();
			
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
	
	private void populateFields() {
		if (mRowId != null && mRowId > 0) {
			mBookshelfText.setText(mDbHelper.getBookshelfName(mRowId));
			mConfirmButton.setText(R.string.confirm_save_bs);
		} else {
			mConfirmButton.setText(R.string.confirm_add_bs);
		}
	}
	
	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		try {
			outState.putLong(CatalogueDBAdapter.KEY_ROWID, mRowId);
		} catch (Exception e) {
			//do nothing
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		populateFields();
	}
	
	private void saveState() {
		String bookshelf = mBookshelfText.getText().toString().trim();
		if (mRowId == null || mRowId == 0) {
			long id = mDbHelper.createBookshelf(bookshelf);
			if (id > 0) {
				mRowId = id;
			}
		} else {
			mDbHelper.updateBookshelf(mRowId, bookshelf);
		}
	}
	
	@Override
	protected void onDestroy(){
		if (mDbHelper != null) {
			mDbHelper.close();
			mDbHelper = null;
		}
		super.onDestroy();
	}
}
