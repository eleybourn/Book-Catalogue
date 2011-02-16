package com.eleybourn.bookcatalogue;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;

public class Author implements Parcelable {

	public String 	familyName;
	public String 	givenNames;
	public long		id;

	Author(String name) {
		id = 0;
		fromString(name);
	}

	Author(long id, String family, String given) {
		this.id = id;
		familyName = family.trim();
		givenNames = given.trim();
	}

	Author(String family, String given) {
		this(0L, family, given);
	}

	public String getDisplayName() {
		if (givenNames != null && givenNames.length() > 0)
			return givenNames + " " + familyName;
		else
			return familyName;
	}

	public String getSortName() {
		if (givenNames != null && givenNames.length() > 0)
			return familyName + ", " + givenNames;
		else
			return familyName;
	}

	// Support for encoding to a text file
	@Override
	public String toString() {
		// Always use givenNames even if blanks because we need to KNOW they are blank. There
		// is a slim chance that family name may contain spaces (eg. 'Anonymous Anarchists').
		return Utils.encodeListItem(familyName, ',') + ", " + Utils.encodeListItem(givenNames, ',');
	}

	//@Override
	private void fromString(String s) {
		ArrayList<String> sa = Utils.decodeList(s, ',');
		if (sa != null && sa.size() > 0) {
			if (sa.size() < 2) {
				// We have a name with no comma. Parse it the usual way.
				String[] names = CatalogueDBAdapter.processAuthorName(s);
				familyName = names[0];
				givenNames = names[1];
			} else {
				familyName = sa.get(0).trim();
				givenNames = sa.get(1).trim();			
			}
			
		}
	}

	/**
	 * Support for creation via Parcelable
	 */
    public static final Parcelable.Creator<Author> CREATOR
            = new Parcelable.Creator<Author>() {
        public Author createFromParcel(Parcel in) {
            return new Author(in);
        }

        public Author[] newArray(int size) {
            return new Author[size];
        }
    };
    
    private Author(Parcel in) {
    	familyName = in.readString();
    	givenNames = in.readString();
    	id = in.readLong();
    }

    @Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(familyName);
		dest.writeString(givenNames);
		dest.writeLong(id);
	}
}
