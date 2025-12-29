/*
 * Copyright (C) 2011-2013 Nick Eley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter.AnthologyTitleExistsException;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

import java.net.URL;
import java.util.ArrayList;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class BookEditAnthology extends BookEditFragmentAbstract {

    private static final int DELETE_ID = Menu.FIRST;
    // Modern: Use resource IDs for menus if possible, but keeping logic consistent
    private static final int POPULATE_ID = Menu.FIRST + 1;

    int anthology_num = CatalogueDBAdapter.ANTHOLOGY_NO;
    private EditText mTitleText;
    private AutoCompleteTextView mAuthorText;
    private String bookAuthor;
    private String bookTitle;
    private Button mAdd;
    private CheckBox mSame;
    private Integer mEditPosition = null;
    private ArrayList<AnthologyTitle> mList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.book_edit_anthology, container, false);
    }

    /**
     * Display the edit fields page
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupMenu();
        loadPage();
    }

    private void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                // Not used in this fragment, but required by interface
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                // Clear and add item dynamically as per original logic
                menu.clear();
                MenuItem populate = menu.add(0, POPULATE_ID, 0, R.string.populate_anthology_titles);
                populate.setIcon(android.R.drawable.ic_menu_add);
                populate.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == POPULATE_ID) {
                    searchWikipedia();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    /**
     * Display the main manage anthology page. This has three parts.
     * 1. Setup the "Same Author" checkbox
     * 2. Setup the "Add Title" fields
     * 3. Populate the "Title List" - @see fillAnthology();
     */
    public void loadPage() {

        BookData book = mEditManager.getBookData();
        bookAuthor = book.getString(CatalogueDBAdapter.KEY_AUTHOR_FORMATTED);
        bookTitle = book.getString(CatalogueDBAdapter.KEY_TITLE);
        // Setup the same author field
        anthology_num = book.getInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK);

        assert getView() != null;
        mSame = getView().findViewById(R.id.field_same_author);
        mSame.setChecked((anthology_num & CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS) == 0);

        mSame.setOnClickListener(view -> {
            saveState(mEditManager.getBookData());
            loadPage();
        });

        ArrayAdapter<String> author_adapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_dropdown_item_1line, mDbHelper.getAllAuthors());
        mAuthorText = getView().findViewById(R.id.field_add_author);
        mAuthorText.setAdapter(author_adapter);
        if (mSame.isChecked()) {
            mAuthorText.setVisibility(View.GONE);
        } else {
            mAuthorText.setVisibility(View.VISIBLE);
        }
        mTitleText = getView().findViewById(R.id.field_add_title);

        mAdd = getView().findViewById(R.id.row_add);
        mAdd.setOnClickListener(view -> {
            try {
                String title = mTitleText.getText().toString();
                String author = mAuthorText.getText().toString();
                if (mSame.isChecked()) {
                    author = bookAuthor;
                }
                AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) BookEditAnthology.this.getListView().getAdapter());
                if (mEditPosition == null) {
                    AnthologyTitle anthology = new AnthologyTitle(new Author(author), title);
                    adapter.add(anthology);
                } else {
                    AnthologyTitle anthology = adapter.getItem(mEditPosition);
                    assert anthology != null;
                    anthology.setAuthor(new Author(author));
                    anthology.setTitle(title);
                    mEditPosition = null;
                    mAdd.setText(R.string.button_anthology_add);
                }
                mTitleText.setText("");
                mAuthorText.setText("");
                //fillAnthology(currentPosition);
                mEditManager.setDirty(true);
            } catch (AnthologyTitleExistsException e) {
                Toast.makeText(getActivity(), R.string.the_title_already_exists, Toast.LENGTH_LONG).show();
            }
        });

        fillAnthology();
    }

    public void fillAnthology(int scroll_to_id) {
        fillAnthology();
        gotoTitle(scroll_to_id);
    }

    /**
     * Populate the bookEditAnthology view
     */
    public void fillAnthology() {
        if (mEditManager == null || mEditManager.getBookData() == null) return;

        // Get all of the rows from the database and create the item list
        mList = mEditManager.getBookData().getAnthologyTitles();
        // Now create a simple cursor adapter and set it to display
        AnthologyTitleListAdapter books = new AnthologyTitleListAdapter(requireActivity(), R.layout.row_anthology, mList);
        final ListView list = getListView();
        list.setAdapter(books);
        registerForContextMenu(list);
        list.setOnItemClickListener((parent, view, position, id) -> {
            mEditPosition = position;
            AnthologyTitle anthology = mList.get(position);
            mTitleText.setText(anthology.getTitle());
            mAuthorText.setText(anthology.getAuthor().getDisplayName());
            mAdd.setText(R.string.anthology_save);
        });
    }

    private ListView getListView() {
        assert getView() != null;
        return getView().findViewById(R.id.list);
    }

    /**
     * Scroll to the current group
     */
    public void gotoTitle(int id) {
        try {
            ListView view = this.getListView();
            view.setSelection(id);
        } catch (Exception e) {
            Logger.logError(e);
        }
    }
    public void searchWikipedia() {
        // Modernization: Use a standard Thread for a single-shot background task
        // This avoids the overhead and lint warnings of creating/closing an ExecutorService for one run.
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            // No 'finally' shutdown needed; the thread dies automatically when this block finishes.
            try {
                String basepath = "https://en.wikipedia.org";
                String path = getString(basepath); // Using your helper method

                boolean success = false;
                ArrayList<String> foundTitles = new ArrayList<>();
                URL url;

                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser parser;
                SearchWikipediaHandler handler = new SearchWikipediaHandler();
                SearchWikipediaEntryHandler entryHandler = new SearchWikipediaEntryHandler();

                try {
                    url = new URL(path);
                    parser = factory.newSAXParser();
                    try {
                        parser.parse(Utils.getInputStream(url), handler);
                    } catch (RuntimeException e) {
                        mainHandler.post(() -> Toast.makeText(getContext(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show());
                        Logger.logError(e);
                        return;
                    }
                    String[] links = handler.getLinks();
                    for (String link : links) {
                        if (link.isEmpty() || success) {
                            break;
                        }
                        url = new URL(basepath + link);
                        parser = factory.newSAXParser();
                        try {
                            parser.parse(Utils.getInputStream(url), entryHandler);
                            ArrayList<String> titles = entryHandler.getList();
                            /* Prepare result */
                            if (!titles.isEmpty()) {
                                success = true;
                                foundTitles.addAll(titles);
                            }
                        } catch (RuntimeException e) {
                            Logger.logError(e);
                            // We continue loop even if one fails
                        }
                    }
                } catch (Exception e) {
                    Logger.logError(e);
                }

                final boolean finalSuccess = success;
                mainHandler.post(() -> {
                    if (finalSuccess) {
                        showAnthologyConfirm(foundTitles);
                    } else {
                        Toast.makeText(getContext(), R.string.automatic_population_failed, Toast.LENGTH_LONG).show();
                    }
                    fillAnthology();
                });
            } catch (Exception e) {
                Logger.logError(e);
            }
        }).start();
    }

    @NonNull
    private String getString(String basepath) {
        String pathAuthor = bookAuthor.replace(" ", "+");
        pathAuthor = pathAuthor.replace(",", "");

        // Strip everything past the , from the title
        String pathTitle = bookTitle;
        int comma = bookTitle.indexOf(",");
        if (comma > 0) {
            pathTitle = pathTitle.substring(0, comma);
        }
        pathTitle = pathTitle.replace(" ", "+");
        return basepath + "/w/index.php?title=Special:Search&search=%22" + pathTitle + "%22+" + pathAuthor;
    }

    private void showAnthologyConfirm(final ArrayList<String> titles) {
        StringBuilder anthology_title = new StringBuilder();
        for (int j = 0; j < titles.size(); j++) {
            anthology_title.append("* ").append(titles.get(j)).append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(anthology_title.toString());
        builder.setTitle(R.string.anthology_confirm);
        builder.setIcon(android.R.drawable.ic_menu_info_details);

        // Modernization: Use explicit positive/negative buttons instead of setButton
        builder.setPositiveButton(R.string.button_ok, (dialog, which) -> {
            for (int j = 0; j < titles.size(); j++) {
                String anthology_title1 = titles.get(j);
                anthology_title1 = anthology_title1 + ", ";
                String anthology_author = bookAuthor;
                // Does the string look like "Hindsight by Jack Williamson"
                int pos = anthology_title1.indexOf(" by ");
                if (pos > 0) {
                    anthology_author = anthology_title1.substring(pos + 4);
                    anthology_title1 = anthology_title1.substring(0, pos);
                }
                // Trim extraneous punctuation and whitespace from the titles and authors
                anthology_author = anthology_author.trim().replace("\n", " ").replaceAll("[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$", "").trim();
                anthology_title1 = anthology_title1.trim().replace("\n", " ").replaceAll("[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$", "").trim();
                AnthologyTitle anthology = new AnthologyTitle(new Author(anthology_author), anthology_title1);
                mList.add(anthology);
            }
            AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) BookEditAnthology.this.getListView().getAdapter());
            adapter.notifyDataSetChanged();
        });

        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> {
            //do nothing
        });

        builder.create().show();
    }

    // Removed onPrepareOptionsMenu and onOptionsItemSelected
    // as they are replaced by MenuProvider in onViewCreated

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, DELETE_ID, 0, R.string.menu_delete_anthology);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == DELETE_ID) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
            AnthologyTitleListAdapter adapter = ((AnthologyTitleListAdapter) BookEditAnthology.this.getListView().getAdapter());
            assert info != null;
            adapter.remove(adapter.getItem((int) info.id));
            mEditManager.setDirty(true);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void saveState(BookData book) {
        if (mSame.isChecked()) {
            anthology_num = CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY;
        } else {
            anthology_num = CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS ^ CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY;
        }
        book.setAnthologyTitles(mList);
        book.putInt(CatalogueDBAdapter.KEY_ANTHOLOGY_MASK, anthology_num);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mEditManager != null && mEditManager.getBookData() != null) {
            saveState(mEditManager.getBookData());
        }
    }

    @Override
    protected void onLoadBookDetails(BookData book) {
        mFields.setAll(book);
    }

    @Override
    protected void onSaveBookDetails(BookData book) {
        super.onSaveBookDetails(book);
        saveState(book);
    }

    public class AnthologyTitleListAdapter extends SimpleListAdapter<AnthologyTitle> {
        // Pass the parameters directly to the overridden function
        public AnthologyTitleListAdapter(Context context, int rowViewId, ArrayList<AnthologyTitle> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onSetupView(AnthologyTitle anthology, int position, View target) {
            TextView author = target.findViewById(R.id.row_author);
            author.setText(anthology.getAuthor().getDisplayName());
            TextView title = target.findViewById(R.id.row_title);
            title.setText(anthology.getTitle());
        }

        @Override
        protected void onRowClick(AnthologyTitle anthology, int position, View v) {
            mEditPosition = position;
            mTitleText.setText(anthology.getTitle());
            mAuthorText.setText(anthology.getAuthor().getDisplayName());
            mAdd.setText(R.string.anthology_save);
        }

        @Override
        protected void onListChanged() {
            if (mEditManager != null) {
                mEditManager.setDirty(true);
            }
        }
    }
}
