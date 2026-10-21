package com.biktech.smsgateway.feature.message;

/** Where an SMS stands: waiting, held by a phone, or final. */
public enum SmsStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    EXPIRED
}
