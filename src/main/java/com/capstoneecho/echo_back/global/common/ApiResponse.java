package com.capstoneecho.echo_back.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;

// 모든 REST 응답이 공유하는 envelope. success 플래그로 성공/실패를 구분하고,
// 성공 시 data, 실패 시 error 만 직렬화된다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode code, String message) {
        String resolved = (message == null || message.isBlank())
                ? code.getDefaultMessage()
                : message;
        return new ApiResponse<>(false, null, new ApiError(code.name(), resolved));
    }

    public static <T> ApiResponse<T> failure(ErrorCode code) {
        return failure(code, null);
    }

    public record ApiError(String code, String message) {}
}
