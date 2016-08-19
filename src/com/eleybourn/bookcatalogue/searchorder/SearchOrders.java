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
import java.util.HashMap;
import java.util.Iterator;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.R;

/**
 * Collection of search provider and determine the search order
 *
 * @author Guillaume Smaha
 */
public class SearchOrders implements Iterable<SearchProvider> {

    // Flag indicating a search source to use
    public static final int SEARCH_GOOGLE = 1;
    // Flag indicating a search source to use
    public static final int SEARCH_AMAZON = 2;
    // Flag indicating a search source to use
    public static final int SEARCH_LIBRARY_THING = 4;
    // Flag indicating a search source to use
    public static final int SEARCH_GOODREADS = 8;

    // Mask including all search sources
    public static final int SEARCH_ALL = SEARCH_GOOGLE | SEARCH_AMAZON | SEARCH_LIBRARY_THING | SEARCH_GOODREADS;

    /**
     * Max bits used by the value of SEARCH_ALL
     * (adding 0.00000000001 is paranoia to avoid rounding/accuracy issues)
     */
    public static final int SEARCH_ALL_COUNT_BITS = (int) (Math.floor(Math.log(SEARCH_ALL) / Math.log(2) + 0.00000000001) + 1);

    // Flag indicating the search possibility for the provider
    public static final int SEARCH_BY_ISBN = 1;
    // Flag indicating the search possibility for the provider
    public static final int SEARCH_BY_ASIN = 2;
    // Flag indicating the search possibility for the provider
    public static final int SEARCH_BY_TITLE_AUTHOR = 4;

    // Mask including all search possibility for the provider
    public static final int SEARCH_ALL_POSSIBILITY = SEARCH_BY_ISBN | SEARCH_BY_ASIN | SEARCH_BY_TITLE_AUTHOR;
    /**
     * Max bits used by the value of SEARCH_ALL
     * (adding 0.00000000001 is paranoia to avoid rounding/accuracy issues)
     */
    public static final int SEARCH_ALL_POSSIBILITY_COUNT_BITS = (int) (Math.floor(Math.log(SEARCH_ALL_POSSIBILITY) / Math.log(2) + 0.00000000001) + 1);


    // Define each search provider
    public static final HashMap<Integer, SearchProvider> SEARCH_ALL_PROVIDERS = new HashMap<Integer, SearchProvider>();
    static {
        SEARCH_ALL_PROVIDERS.put(SEARCH_GOOGLE, new SearchProvider(SEARCH_GOOGLE, R.string.google_books, SEARCH_BY_ISBN | SEARCH_BY_TITLE_AUTHOR | SEARCH_BY_ASIN));
        SEARCH_ALL_PROVIDERS.put(SEARCH_AMAZON, new SearchProvider(SEARCH_AMAZON, R.string.amazon, SEARCH_BY_ISBN | SEARCH_BY_TITLE_AUTHOR | SEARCH_BY_ASIN));
        SEARCH_ALL_PROVIDERS.put(SEARCH_LIBRARY_THING, new SearchProvider(SEARCH_LIBRARY_THING, R.string.library_thing, SEARCH_BY_ISBN));
        SEARCH_ALL_PROVIDERS.put(SEARCH_GOODREADS, new SearchProvider(SEARCH_GOODREADS, R.string.goodreads_label, SEARCH_BY_ISBN | SEARCH_BY_TITLE_AUTHOR));
    }

    // Default search order used by default
    public static final int[] mDefaultSearchOrder = new int[]{SEARCH_AMAZON, SEARCH_GOODREADS, SEARCH_GOOGLE, SEARCH_LIBRARY_THING};

    // Preferences names for search order
    public static final String PREF_APP_SEARCH_ORDER = "App.SearchOrder";
    // Preferences names used for search order enabled
    public static final String PREF_APP_SEARCH_ORDER_ENABLED = "App.SearchOrderEnabled";


    // Internal storage for the search providers with their state
    private ArrayList<SearchProvider> mListOrdered = new ArrayList<SearchProvider>();

    // Internal storage with the value of the providers enabled (bits enabled)
    private Integer mProvidersEnabled;

    /**
     * Constructor
     */
    public SearchOrders() {
        loadPreferredSearchOrder();
    }

    /**
     * Get the search order and the search providers which are enabled from user preferences.
     */
    private void loadPreferredSearchOrder() {

        //Get the enable providers, if it is not defined in user preference then enable all providers (set all bits to 1)
        BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
        mProvidersEnabled = prefs.getInt(PREF_APP_SEARCH_ORDER_ENABLED, -1);
        if (mProvidersEnabled == -1) {
            mProvidersEnabled = 0;
            int i = 1;
            while (i <= SEARCH_ALL_COUNT_BITS) {
                mProvidersEnabled <<= 1;
                mProvidersEnabled++;
                i++;
            }
        }

        // List of id defined in mListOrdered.
        ArrayList<Integer> idAssigned = new ArrayList<Integer>();

        /*
        * Create the list with the providers declared in SEARCH_ALL.
        * If the user preference is not defined, we use mDefaultSearchOrder
        */
        String itemStr = prefs.getString(PREF_APP_SEARCH_ORDER, null);
        mListOrdered.clear();
        if (itemStr != null && !itemStr.equals("")) {
            //Read concatenate string and add to the list
            ArrayList<String> list = Utils.decodeList(itemStr, '|');
            for (int i = 0; i < list.size(); i++) {
                Integer id = Integer.valueOf(list.get(i));
                if (id != null && !idAssigned.contains(id) && (id & SEARCH_ALL) != 0 && SEARCH_ALL_PROVIDERS.containsKey(id)) {
                    idAssigned.add(id);
                    SEARCH_ALL_PROVIDERS.get(id).setState((id & mProvidersEnabled) != 0);
                    mListOrdered.add(SEARCH_ALL_PROVIDERS.get(id));
                }
            }
        } else if (mDefaultSearchOrder != null) {
            for (Integer id : mDefaultSearchOrder) {
                if (!idAssigned.contains(id) && (id & SEARCH_ALL) != 0 && SEARCH_ALL_PROVIDERS.containsKey(id)) {
                    idAssigned.add(id);
                    SEARCH_ALL_PROVIDERS.get(id).setState((id & mProvidersEnabled) != 0);
                    mListOrdered.add(SEARCH_ALL_PROVIDERS.get(id));
                }
            }
        }


        // Permit to complete the list at the end with missing provider from the user preference
        if (mListOrdered.size() != SEARCH_ALL_COUNT_BITS) {
            /*
            * Add any missing providers in the default order and enable it by default
            * We assume that all providers in SEARCH_ALL are defined in mDefaultSearchOrder
            */
            for (SearchProvider p : SEARCH_ALL_PROVIDERS.values()) {
                if (!idAssigned.contains(p.getId())) {
                    p.setState(false);
                    mListOrdered.add(p);
                }
            }
        }
    }

    /**
     * Return the search order defined in user preferences or use the default search order
     * Here, we assume that mListOrdered is not empty and correctly define in loadPreferredSearchOrder
     * Can be filtered by different use
     *
     * @param filter
     * @return int[]
     */
    public int[] generatePreferredSearchOrder(Integer filter) {
        int[] listResult = new int[SEARCH_ALL_COUNT_BITS];
        int iSearch = 0;

        // Browse mListOrdered and add the provider's id into the array int[]
        for (SearchProvider provider : mListOrdered) {
            if (provider.isEnabled() && (filter == null || filter != null && provider.hasSearchPossibility(filter))) {
                listResult[iSearch] = provider.getId();
                iSearch++;
            }
        }

        return listResult;
    }

    /**
     * Return the number of search providers in this collection
     *
     * @return
     */
    public int size() {
        return mListOrdered.size();
    }

    /**
     * Utility to check if this collection contains a specific provider INSTANCE.
     *
     * @param provider
     * @return
     */
    private boolean contains(SearchProvider provider) {
        return mListOrdered.contains(provider);
    }

    /**
     * Add a provider to this list
     *
     * @param provider
     */
    public void add(SearchProvider provider) {
        provider.setState((provider.getId() & mProvidersEnabled) != 0);
        mListOrdered.add(provider);
    }

    /**
     * Find a style based on the id of the search provider
     *
     * @param id
     * @return Search provider, or null
     */
    public SearchProvider findCanonical(Integer id) {
        for (SearchProvider provider : mListOrdered) {
            if (provider.getId() == id)
                return provider;
        }
        return null;
    }

    /**
     * Return the i'th provider in the list
     *
     * @param i
     * @return
     */
    public SearchProvider get(int i) {
        return mListOrdered.get(i);
    }

    /**
     * Return an iterator for the list of providers.
     *
     * @return
     */
    public Iterator<SearchProvider> iterator() {
        return mListOrdered.iterator();
    }


    /**
     * Return the search order defined in user preferences or use the default search order.
     * Can be filtered by different use
     *
     * @param filter
     * @return int[]
     */
    public static int[] getPreferredSearchOrder(Integer filter) {
        SearchOrders searchOrders = new SearchOrders();

        return searchOrders.generatePreferredSearchOrder(filter);
    }

    /**
     * Return all search providers
     *
     * @return
     */
    public static SearchOrders getAllSearchProviders() {
        SearchOrders searchOrders = new SearchOrders();

        return searchOrders;
    }

    /**
     * Save the preferred search order and the provider which are enabled
     *
     * @return
     */
    public static boolean SaveMenuOrder(ArrayList<SearchProvider> list) {
        // Concatenate id for the search order
        // Add bit for the enable providers
        String items = "";
        int providersEnabled = 0;
        for (int i = 0; i < list.size(); i++) {
            SearchProvider f = list.get(i);
            if (!items.equals(""))
                items += "|";
            items += f.toString();
            if (f.isEnabled())
                providersEnabled |= f.getId();
        }
        Editor e = BookCatalogueApp.getAppPreferences().edit();
        e.putString(PREF_APP_SEARCH_ORDER, items);
        e.putInt(PREF_APP_SEARCH_ORDER_ENABLED, providersEnabled);
        e.commit();
        return true;
    }

    /**
     * Start the activity to edit the search order
     *
     * @param a
     */
    public static void startEditActivity(Activity a) {
        Intent i = new Intent(a, SearchOrdersActivity.class);
        a.startActivityForResult(i, UniqueId.ACTIVITY_SEARCH_ORDER);
    }
}

