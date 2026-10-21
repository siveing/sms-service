package com.biktech.smsgateway.feature.message;

/** How a message reaches people: a real SMS from a phone, or a post in a Telegram group for testing. */
public enum DeliveryChannel {
    SMS("Real SMS"),
    TELEGRAM("Telegram");

    private final String label;

    DeliveryChannel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
