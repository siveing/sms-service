package com.biktech.smsgateway.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Every error the API answers with; the name is the code callers see. */
@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are invalid."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Send a valid key: X-Api-Key for an app, X-Device-Key for a phone."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "This key may not use this endpoint."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "This message does not exist."),
    NUMBER_INVALID(HttpStatus.BAD_REQUEST, "Use a number such as 012345678 or +85512345678."),
    NAME_TAKEN(HttpStatus.CONFLICT, "That name is already used."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Try again later.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
