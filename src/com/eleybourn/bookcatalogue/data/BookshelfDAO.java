package com.eleybourn.bookcatalogue.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;

import java.util.List;

@Dao
public interface BookshelfDAO {
    // LiveData automatically updates the UI when data changes
    @Query("SELECT * FROM " + CatalogueDBAdapter.DB_TB_BOOKSHELF + " ORDER BY bookshelf ASC")
    LiveData<List<Bookshelf>> getAllBookshelves();

    @Query("SELECT * FROM " + CatalogueDBAdapter.DB_TB_BOOKSHELF + " WHERE " + CatalogueDBAdapter.KEY_ROW_ID + " = :id LIMIT 1")
    LiveData<Bookshelf> getBookshelfById(long id);

    @Insert
    void insert(Bookshelf bookshelf);

    @Delete
    void delete(Bookshelf bookshelf);

    @Query("DELETE FROM " + CatalogueDBAdapter.DB_TB_BOOKSHELF + " WHERE " + CatalogueDBAdapter.KEY_ROW_ID + " = :id")
    void deleteById(long id);

    @Update
    void update(Bookshelf bookshelf);

}
