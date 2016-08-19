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

import java.io.Serializable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.database.SerializationUtils;
import com.eleybourn.bookcatalogue.database.SerializationUtils.DeserializationException;

/**
 * Represents a search provider (like Amazon, Goodreads, ...) with their state.
 *
 * @author Guillaume Smaha
 */
public class SearchProvider implements Serializable {

    // Internal storage with the id of the provider
    private final Integer mId;

    // Internal storage to store the state of the provider
    private Integer mSearchPossibility = 0;

    // Internal storage to store the state of the provider
    private Integer mResourceName = 0;

    // Internal storage to store the state of the provider
    private boolean mIsEnabled = true;

    /**
     * Constructor for one search provider
     *
     * @param id
     * @param resourceName
     * @param searchPossibility
     */
    SearchProvider(Integer id, Integer resourceName, Integer searchPossibility) {
        mId = id;
        mResourceName = resourceName;
        mSearchPossibility = searchPossibility;
    }

    /**
     * Accessor. Returns the name of the search provider.
     * else return the mId value.
     *
     * @return
     */
    public String getDisplayName() {
        if (mResourceName > 0) {
            return BookCatalogueApp.getResourceString(mResourceName);
        }

        return toString();
    }

    /**
     * Construct a deep clone of this object.
     */
    public SearchProvider getClone() throws DeserializationException {
        SearchProvider newProvider = SerializationUtils.cloneObject(this);
        return newProvider;
    }

    /**
     * Method setState. Define the state of the search provider
     *
     * @return
     */
    public void setState(boolean isEnabled) {
        mIsEnabled = isEnabled;
    }

    /**
     * Method getId. Returns id
     *
     * @return
     */
    public int getId() {
        return mId;
    }

    /**
     * Method isEnabled. Returns if search provider is enabled
     *
     * @return
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * Method hasSearchPossibility. Returns if search provider has the asked search possibility
     *
     * @return
     */
    public boolean hasSearchPossibility(Integer searchPossibility) {
        return ((mSearchPossibility & searchPossibility) != 0);
    }

    /**
     * Method toString. Returns id
     *
     * @return
     */
    public String toString() {
        return mId.toString();
    }
}

