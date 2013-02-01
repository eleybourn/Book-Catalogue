package com.eleybourn.bookcatalogue;

import java.io.Serializable;

import com.eleybourn.bookcatalogue.utils.Utils;

public class AnthologyTitle implements Serializable {
	private Author mAuthor;
	private String mTitle;

	public AnthologyTitle(Author author, String title) {
		mAuthor = author;
		mTitle = title.trim();
	}
	
	public String getTitle() {
		return mTitle;
	}
	public void setTitle(String title) {
		mTitle = title;
	}
	
	public Author getAuthor() {
		return mAuthor;
	}
	public void setAuthor(Author author) {
		mAuthor = author;
	}

}
