package com.eleybourn.bookcatalogue.utils;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.eleybourn.bookcatalogue.BookDetails;
import com.eleybourn.bookcatalogue.BookEditAnthology;
import com.eleybourn.bookcatalogue.BookEditFields;
import com.eleybourn.bookcatalogue.BookEditLoaned;
import com.eleybourn.bookcatalogue.BookEditNotes;

public class BookEditPagerAdapter extends FragmentStateAdapter {
    private static final int NUM_TABS = 4;
    public boolean READ_ONLY = false;
    public boolean BLANK_BOOK = true;
    public boolean ANTHOLOGY_TAB = false;

    public BookEditPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Return a new fragment instance for the given position
        switch (position) {
            case 1:
                return new BookEditNotes();
            case 2:
                return new BookEditLoaned();
            case 3:
                return new BookEditAnthology();
            default:
                if (READ_ONLY) {
                    return new BookDetails();
                } else {
                    return new BookEditFields();
                }
        }
    }

    @Override
    public int getItemCount() {
        // Return the number of tabs
        if (READ_ONLY) {
            return 1;
        }
        if (BLANK_BOOK) {
            return NUM_TABS - 3;
        }
        if (!ANTHOLOGY_TAB) {
            return NUM_TABS - 1;
        }
        return NUM_TABS;    }
}
