package com.biktech.smsgateway.feature.caller;

/** An app that sends SMS through the API, or an Android phone that texts them. */
public enum CallerKind {
    CLIENT("sgc_"),
    PHONE("sgp_");

    private final String keyPrefix;

    CallerKind(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
