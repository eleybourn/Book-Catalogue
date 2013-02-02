/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue;

import java.io.Serializable;

/**
 * Class to represent a single title within an anthology
 * 
 * @author pjw
 */
public class AnthologyTitle implements Serializable {
	private static final long serialVersionUID = -8715364898312204329L;
	private Author mAuthor;
	private String mTitle;

	/**
	 * Constructor
	 * 
	 * @param author	Author of title
	 * @param title		Title
	 */
	public AnthologyTitle(Author author, String title) {
		mAuthor = author;
		mTitle = title.trim();
	}
	
	/** Accessor */
	public String getTitle() {
		return mTitle;
	}
	/** Accessor */
	public void setTitle(String title) {
		mTitle = title;
	}
	
	/** Accessor */
	public Author getAuthor() {
		return mAuthor;
	}
	/** Accessor */
	public void setAuthor(Author author) {
		mAuthor = author;
	}

}
