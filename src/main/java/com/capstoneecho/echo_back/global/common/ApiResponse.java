package com.capstoneecho.echo_back.global.common;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error
) {

    public record ApiError(String code, String message) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode code, String message) {
        String resolved = (message == null || message.isBlank())
                ? code.getDefaultMessage()
                : message;
        return new ApiResponse<>(false, null, new ApiError(code.name(), resolved));
    }
}
