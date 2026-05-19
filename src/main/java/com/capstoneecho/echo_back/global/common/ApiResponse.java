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

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message));
    }

    public static <T> ApiResponse<T> fail(ErrorCode code) {
        return fail(code, code.defaultMessage());
    }

    public record ApiError(String code, String message) {}
}
