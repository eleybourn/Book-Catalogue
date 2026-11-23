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

    @androidx.room.Ignore
    public String uuid = "";

    public Bookshelf() {
        name = "";
    }

    public String getUuid() {
        if (uuid == null || uuid.isEmpty()) {
            if (name != null && !name.isEmpty()) {
                uuid = java.util.UUID.nameUUIDFromBytes(name.trim().toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            } else {
                uuid = java.util.UUID.randomUUID().toString();
            }
        }
        return uuid;
    }
}
