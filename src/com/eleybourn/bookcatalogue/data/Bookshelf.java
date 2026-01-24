package com.eleybourn.bookcatalogue.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;

@Entity(tableName = CatalogueDBAdapter.DB_TB_BOOKSHELF, indices = {@Index(value = {CatalogueDBAdapter.KEY_BOOKSHELF}, name = "bookshelf_bookshelf")})
public class Bookshelf {
    @PrimaryKey
    @ColumnInfo(name = CatalogueDBAdapter.KEY_ROW_ID)
    public Long id;

    @NonNull
    @ColumnInfo(name = CatalogueDBAdapter.KEY_BOOKSHELF)
    public String name;

    public Bookshelf() {
        name = "";
    }
}
