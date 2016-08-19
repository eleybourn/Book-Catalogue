/*
 * @copyright 2016 Guillaume Smaha
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

package com.eleybourn.bookcatalogue.searchorder;

import java.util.ArrayList;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.EditObjectList;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Activity to edit the search order and to enable or disable a provider
 *
 * @author Guillaume Smaha
 */
public class SearchOrdersActivity extends EditObjectList<SearchProvider> {

    /**
     * Constructor
     */
    public SearchOrdersActivity() {
        super(null, R.layout.search_orders_edit_list, R.layout.search_orders_edit_row);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            // We want context menus to be available
            registerForContextMenu(getListView());

            super.onCreate(savedInstanceState);

            this.setTitle(R.string.preferred_search_order);

            if (savedInstanceState == null)
                HintManager.displayHint(this, R.string.hint_search_order_editor, null);

            Utils.initBackground(R.drawable.bc_background_gradient_dim, this, R.id.list_wrapper, false);

        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Fix background
     */
    @Override
    public void onResume() {
        super.onResume();
        Utils.initBackground(R.drawable.bc_background_gradient_dim, this, R.id.list_wrapper, false);
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @Override
    protected ArrayList<SearchProvider> getList() {
        ArrayList<SearchProvider> providers = new ArrayList<SearchProvider>();
        // get the preferred styles first
        for (SearchProvider p : SearchOrders.getAllSearchProviders()) {
            providers.add(p);
        }
        return providers;
    }

    /**
     * Required, not used
     */
    @Override
    protected void onAdd(View v) {
    }

    /**
     * Check if all providers are disabled
     *
     * @author Guillaume Smaha
     */
    private boolean isAllDisabled() {
        boolean enable = false;
        for (SearchProvider p : mList) {
            enable |= p.isEnabled();
        }
        return !enable;
    }

    /**
     * Check if all providers are disabled
     *
     * @author Guillaume Smaha
     */
    private ArrayList<Integer> getSearchPossibilityDisabled() {

        int[] listResult = new int[SearchOrders.SEARCH_ALL_POSSIBILITY_COUNT_BITS];
        int i, searchPossibility;
        for (SearchProvider p : mList) {
            if(p.isEnabled()) {
                i = 0;
                searchPossibility = 1;
                while (i < SearchOrders.SEARCH_ALL_POSSIBILITY_COUNT_BITS) {
                    if (p.hasSearchPossibility(searchPossibility)) {
                        listResult[i]++;
                    }
                    searchPossibility <<= 1;
                    i++;
                }
            }
        }

        ArrayList<Integer> searchPossibilityDisabled = new ArrayList<Integer>();
        for(i = 0 ; i < SearchOrders.SEARCH_ALL_POSSIBILITY_COUNT_BITS ; i ++) {
            if(listResult[i] == 0) {
                searchPossibilityDisabled.add((int)(Math.pow(2.0, i)));
            }
        }

        return searchPossibilityDisabled;
    }

    ;

    /**
     * Holder pattern object for list items
     *
     * @author Guillaume Smaha
     */
    private class Holder {
        SearchProvider provider;
        TextView name;
        CheckBox enabled;
    }

    /**
     * Setup the view
     */
    @Override
    protected void onSetupView(View target, SearchProvider provider) {
        Holder h;
        h = (Holder) ViewTagger.getTag(target, R.id.TAG_HOLDER);
        if (h == null) {
            // No holder found, create one

            h = new Holder();
            h.name = (TextView) target.findViewById(R.id.name);
            h.enabled = (CheckBox) target.findViewById(R.id.enabled);
            // Tag relevant views
            ViewTagger.setTag(target, R.id.TAG_HOLDER, h);
            ViewTagger.setTag(h.enabled, R.id.TAG_HOLDER, h);


            // Handle clicks on the tick/cross
            h.enabled.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Holder h = (Holder) ViewTagger.getTag(v, R.id.TAG_HOLDER);
                    boolean newPref = !h.provider.isEnabled();
                    h.provider.setState(newPref);
                    if (isAllDisabled()) {
                        h.provider.setState(true);
                        h.enabled.setChecked(true);
                        Toast.makeText(v.getContext(), getString(R.string.search_order_cant_disable_all), Toast.LENGTH_LONG).show();
                    }
                    else {
                        ArrayList<Integer> listDisabled = getSearchPossibilityDisabled();
                        if(listDisabled.size() > 0) {
                            String message = "";
                            if(listDisabled.contains(SearchOrders.SEARCH_BY_ISBN)) {
                                message += getString(R.string.isbn);
                            }
                            if(listDisabled.contains(SearchOrders.SEARCH_BY_ASIN)) {
                                if(message.length() > 0) {
                                    message += ", ";
                                }
                                message += getString(R.string.asin);
                            }
                            if(listDisabled.contains(SearchOrders.SEARCH_BY_TITLE_AUTHOR)) {
                                if(message.length() > 0) {
                                    message += ", ";
                                }
                                message += getString(R.string.title_author);
                            }
                            Toast.makeText(v.getContext(), getString(R.string.search_possibility_incomplete_message) + " " + message, Toast.LENGTH_LONG).show();
                        }
                    }

                    onListChanged();
                }
            });
        }

        // Set the volatile fields in the holder
        h.provider = provider;
        h.name.setText(provider.getDisplayName());
        h.enabled.setChecked(provider.isEnabled());
    }

    /**
     * Required, not used
     */
    @Override
    protected void onRowClick(View target, final int position, final SearchProvider provider) {
    }

    /**
     * Saved the search order after edit
     */
    @Override
    protected void onListChanged() {
        // Save the order whenever the list is modified.
        SearchOrders.SaveMenuOrder(mList);
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
