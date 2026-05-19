package com.capstoneecho.echo_back.global.common;

// 모든 REST 응답이 공유하는 envelope. 성공 시 data 가 채워지고 error 는 null,
// 실패 시 그 반대다. 두 필드 모두 항상 직렬화되어 클라이언트는 일관된 키를 본다.
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
