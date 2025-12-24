package com.eleybourn.bookcatalogue.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;

@Entity(tableName = "bookshelves") // Replace with your actual table name from DBAdapter
public class Bookshelf {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id") // Mapping internal ID to standard Android row ID
    public long id;

    @ColumnInfo(name = CatalogueDBAdapter.KEY_BOOKSHELF) // Replace with CatalogueDBAdapter.KEY_BOOKSHELF
    public String name;

    // Add other columns if your existing table has them
}
