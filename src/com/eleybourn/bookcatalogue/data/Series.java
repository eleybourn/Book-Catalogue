/*
 * @copyright 2011 Philip Warner
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

package com.eleybourn.bookcatalogue.data;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to hold book-related series data. Used in lists and import/export.
 * 
 * @author Philip Warner
 */
public class Series implements Serializable, Utils.ItemWithIdFixup {
	private static final long serialVersionUID = 1L;
	public long		id;
	public String 	name;
	public String	num;

    public Series(String name) {
        Pattern mPattern = Pattern.compile("^(.*)\\s*\\((.*)\\)\\s*$");
        java.util.regex.Matcher m = mPattern.matcher(name);
		if (m.find()) {
			this.name = Objects.requireNonNull(m.group(1)).trim();
			this.num = cleanupSeriesPosition(m.group(2));
		} else {
			this.name = name.trim();
			this.num = "";
		}
		this.id = 0L;
	}

    public Series(long id, String name) {
		this(id, name, "");
	}

	public Series(String name, String num) {
		this(0L, name, num);
	}

    public Series(long id, String name, String num) {
		this.id = id;
		this.name = name.trim();
		this.num = cleanupSeriesPosition(num);
	}

	public String getDisplayName() {
		if (num != null && !num.isEmpty())
			return name + " (" + num + ")";
		else
			return name;
	}

	public String getSortName() {
		return getDisplayName();
	}

	@NonNull
    public String toString() {
		return getDisplayName();
	}

    /**
     * Replace local details from another series
     * 
     * @param source	Author to copy
     */
    public void copyFrom(Series source) {
		name = source.name;
		num = source.num;
		id = source.id;    	
    }

    @Override
	public long fixupId(CatalogueDBAdapter db) {
		this.id = db.lookupSeriesId(this);
		return this.id;
	}

	@Override
	public long getId() {
		return id;
	}

	/**
	 * Each position in a series ('Elric(1)', 'Elric(2)' etc) will have the same
	 * ID, so they are not unique by ID.
	 */
	@Override
	public boolean isUniqueById() {
		return false;
	}


	/**
	 * Data class giving resulting series info after parsing a series name
	 * 
	 * @author Philip Warner
	 */
	public static class SeriesDetails {
		public String name;
		public String position = null;
		public int startChar;
	}

	/** Pattern used to recognize series numbers embedded in names */
	private static Pattern mSeriesPat = null;
	private static final String mSeriesNumberPrefixes = "(#|number|num|num.|no|no.|nr|nr.|book|bk|bk.|volume|vol|vol.|tome|part|pt.|)";

	/**
	 * Try to extract a series from a book title.
	 * 
	 * @param 	title	Book title to parse
	 */
	public static SeriesDetails findSeries(String title) {
		SeriesDetails details = null;
		int last = title.lastIndexOf("(");
		if (last >= 1) { // We want a title that does not START with a bracket!
			int close = title.lastIndexOf(")");
			if (close > -1 && last < close) {
				details = new SeriesDetails();
				details.name = title.substring((last+1), close);
				details.startChar = last;
				if (mSeriesPat == null) {
					// NOTE: Changes to this pattern should be mirrored in cleanupSeriesPosition().
					String seriesExp = "(.*?)(,|\\s)\\s*" + mSeriesNumberPrefixes + "\\s*([0-9\\.\\-]+|[ivxlcm\\.\\-]+)\\s*$";
					// Compile and get a reference to a Pattern object. <br>  
					mSeriesPat = Pattern.compile(seriesExp, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
				}
				Matcher matcher = mSeriesPat.matcher(details.name);
				if (matcher.find()) {
					details.name = matcher.group(1);
					details.position = matcher.group(4);
				}
			}
		}
		return details;
	}

	/** Pattern used to remove extraneous text from series positions */
	private static Pattern mSeriesPosCleanupPat = null;
	private static Pattern mSeriesIntegerPat = null;

	/**
	 * Try to cleanup a series position number by removing superfluous text.
	 * 
	 * @param 	position	Position name to cleanup
	 */
	public static String cleanupSeriesPosition(String position) {
		if (position == null)
			return "";
		position = position.trim();

		if (mSeriesPosCleanupPat == null) {
			// NOTE: Changes to this pattern should be mirrored in findSeries().
			String seriesExp = "^\\s*" + mSeriesNumberPrefixes + "\\s*([0-9\\.\\-]+|[ivxlcm\\.\\-]+)\\s*$";
			// Compile and get a reference to a Pattern object. <br>
			mSeriesPosCleanupPat = Pattern.compile(seriesExp, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
		}
		if (mSeriesIntegerPat == null) {
			String numericExp = "^[0-9]+$";
			mSeriesIntegerPat = Pattern.compile(numericExp);
		}

		Matcher matcher = mSeriesPosCleanupPat.matcher(position);

		if (matcher.find()) {
			// Try to remove leading zeros.
			String pos = matcher.group(2);
            assert pos != null;
            Matcher intMatch = mSeriesIntegerPat.matcher(pos);
			if (intMatch.find()) {
				return Long.parseLong(pos) + "";
			} else {
				return pos;
			}
		} else {
			return position;
		}
	}

}
