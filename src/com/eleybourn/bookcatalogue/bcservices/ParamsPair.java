package com.eleybourn.bookcatalogue.bcservices;

/**
 *  Class to store key-value pairs for POST API
 */
public class ParamsPair {
    private String key;
    private String value;

    ParamsPair(String k, String v) {
        this.key = k;
        this.value = v;
    }

    public String getKey() {
        return this.key;
    }

    public String getValue() {
        return this.value;
    }
}
