package com.eleybourn.bookcatalogue.data;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookshelfViewModel extends AndroidViewModel {
    private final BookshelfDAO mDao;
    private final LiveData<List<Bookshelf>> mAllBookshelves;

    // CHANGED: Removed "com.google.firebase.firestore.util." prefix
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public BookshelfViewModel(@NonNull Application application) {
        super(application);
        CatalogueDatabase db = CatalogueDatabase.getDatabase(application);
        mDao = db.bookshelfDAO();
        mAllBookshelves = mDao.getAllBookshelves();
    }

    public LiveData<List<Bookshelf>> getAllBookshelves() {
        return mAllBookshelves;
    }

    public LiveData<Bookshelf> getBookshelfById(long id) {    return mDao.getBookshelfById(id);
    }

    // Also ensure you have the insert/update methods:
    public void insert(Bookshelf bookshelf) {
        mExecutor.execute(() -> mDao.insert(bookshelf));
    }

    public void update(Bookshelf bookshelf) {
        mExecutor.execute(() -> mDao.update(bookshelf));
    }

    public void deleteBookshelf(long id) {
        mExecutor.execute(() -> mDao.deleteById(id));
    }
}
