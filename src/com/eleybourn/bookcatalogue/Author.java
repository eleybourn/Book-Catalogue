package com.eleybourn.bookcatalogue;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class to hold author data. Used in lists and import/export.
 * 
 * @author Grunthos
 */
public class Author implements Parcelable, Utils.ItemWithIdFixup {

	public String 	familyName;
	public String 	givenNames;
	public long		id;

	/**
	 * Constructor that will attempt to parse a single string into an author name.
	 * 
	 * @param name
	 */
	Author(String name) {
		id = 0;
		fromString(name);
	}

	/**
	 * Constructor without ID.
	 * 
	 * @param family	Family name
	 * @param given		Given names
	 */
	Author(String family, String given) {
		this(0L, family, given);
	}

	/**
	 * Constructor
	 *
	 * @param id		ID of author in DB (0 if not in DB)
	 * @param family	Family name
	 * @param given		Given names
	 */
	Author(long id, String family, String given) {
		this.id = id;
		familyName = family.trim();
		givenNames = given.trim();
	}

	/**
	 * Return the 'human readable' version of the name (eg. 'Iassc Asimov').
	 * 
	 * @return	formatted name
	 */
	public String getDisplayName() {
		if (givenNames != null && givenNames.length() > 0)
			return givenNames + " " + familyName;
		else
			return familyName;
	}

	/**
	 * Return the name in a sortable form (eg. 'Asimov, Iassc')
	 * 
	 * @return	formatted name
	 */
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
	 * Support for creation via Parcelable. This is primarily useful for passing
	 * ArrayList<Author> in Bundles to activities.
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

    /**
     * Constructor using a Parcel.
     * 
     * @param in
     */
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

	@Override
	public long fixupId(CatalogueDBAdapter db) {
		this.id = db.lookupAuthorId(this);
		return this.id;
	}
}
