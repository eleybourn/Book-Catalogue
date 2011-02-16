package com.eleybourn.bookcatalogue;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;

public class Series implements Parcelable {
	public long		id;
	public String 	name;
	public String	num;

	Series(String name) {
		//java.util.regex.Pattern p = java.util.regex.Pattern.compile("^(.*)\\s*\\((.*)\\)$");
//		java.util.regex.Matcher m = p.matcher(seriesSpec);
//		if (m.find()) {
//			seriesName = m.group(1);
//			bookSeries.put(KEY_SERIES_NUM, m.group(2));
//		} else {
//			seriesName = seriesSpec;
//		}

		this(0L, name, "");
	}

	Series(long id, String name) {
		this(id, name, "");
	}

	Series(String name, String num) {
		this(0L, name, num);
	}

	Series(long id, String name, String num) {
		this.id = id;
		this.name = name;
		this.num = num;
	}

	public String getDisplayName() {
		if (num != null && num.length() > 0)
			return name + " (" + num + ")";
		else
			return name;
	}

	public String getSortName() {
		return getDisplayName();
	}

	public String toString() {
		return getDisplayName();
	}

	/**
	 * Support for creation via Parcelable
	 */
    public static final Parcelable.Creator<Series> CREATOR
            = new Parcelable.Creator<Series>() {
        public Series createFromParcel(Parcel in) {
            return new Series(in);
        }

        public Series[] newArray(int size) {
            return new Series[size];
        }
    };
    
    private Series(Parcel in) {
    	name = in.readString();
    	num = in.readString();
    	id = in.readLong();
    }

    @Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(name);
		dest.writeString(num);
		dest.writeLong(id);
	}
}
