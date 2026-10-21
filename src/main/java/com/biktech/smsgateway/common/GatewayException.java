package com.biktech.smsgateway.common;

import lombok.Getter;

@Getter
public class GatewayException extends RuntimeException {
    private final ErrorCode errorCode;

    public GatewayException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
