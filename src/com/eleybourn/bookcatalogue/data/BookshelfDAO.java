package com.eleybourn.bookcatalogue.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookshelfDAO {
    // LiveData automatically updates the UI when data changes
    @Query("SELECT * FROM bookshelves ORDER BY bookshelf ASC")
    LiveData<List<Bookshelf>> getAllBookshelves();

    @Query("SELECT * FROM bookshelves WHERE _id = :id LIMIT 1")
    LiveData<Bookshelf> getBookshelfById(long id);

    @Insert
    void insert(Bookshelf bookshelf);

    @Delete
    void delete(Bookshelf bookshelf);

    @Query("DELETE FROM bookshelves WHERE _id = :id")
    void deleteById(long id);

    @Update
    void update(Bookshelf bookshelf);

}
