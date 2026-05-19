package com.capstoneecho.echo_back.app.common;

// 도메인 규칙으로 거절해야 하는 상황을 감싸는 예외. 던지기만 하면 GlobalExceptionHandler 가
// ErrorCode 의 HTTP 상태 + 메시지로 응답을 만든다.
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
