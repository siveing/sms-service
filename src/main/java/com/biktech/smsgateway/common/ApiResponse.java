package com.biktech.smsgateway.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Every API answer: {success, data} or {success, error: {code, message}}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, Error error) {

    public record Error(String code, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new Error(code.name(), message));
    }

    /** The same envelope written by hand, for the security layer that answers before any controller. */
    public static String failJson(ErrorCode code) {
        return "{\"success\":false,\"error\":{\"code\":\"" + code.name() + "\",\"message\":\"" + code.getMessage() + "\"}}";
    }
}
