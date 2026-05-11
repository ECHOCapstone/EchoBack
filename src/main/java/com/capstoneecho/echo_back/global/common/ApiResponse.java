package com.capstoneecho.echo_back.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String errorMessage
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String errorMessage) {
        String message = (errorMessage == null || errorMessage.isBlank())
                ? errorCode.getDefaultMessage()
                : errorMessage;
        return new ApiResponse<>(false, null, errorCode.name(), message);
    }
}
