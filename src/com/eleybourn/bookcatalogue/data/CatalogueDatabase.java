package com.eleybourn.bookcatalogue.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 1. Annotate the class to be a Room Database
// Declare all your entities here (Bookshelf.class, etc.)
// Increment 'version' if you change the schema later
@Database(entities = {Bookshelf.class}, version = 1, exportSchema = false)
public abstract class CatalogueDatabase extends RoomDatabase {

    // 2. Expose DAOs
    // This abstract method returns the DAO so the ViewModel can use it
    public abstract BookshelfDAO bookshelfDAO();

    // 3. Singleton Pattern
    // Prevent having multiple instances of the database opening at the same time.
    private static volatile CatalogueDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;

    // Optional: Shared executor for database write operations
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static CatalogueDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (CatalogueDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    CatalogueDatabase.class, "book_catalogue_database")
                            // .addMigrations(...) // Add migrations here later if needed
                            // .fallbackToDestructiveMigration() // Use this only during dev to wipe DB on schema change
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
