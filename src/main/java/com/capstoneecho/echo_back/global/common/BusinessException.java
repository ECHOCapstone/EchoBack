package com.capstoneecho.echo_back.global.common;

// 도메인 규칙으로 거절해야 하는 상황을 감싸는 예외.
// 던지기만 하면 GlobalExceptionHandler 가 ErrorCode 의 HTTP 상태 + 메시지로 응답을 만든다.
// 호출자가 메시지를 명시하지 않거나 빈 문자열을 넘기면 ErrorCode 의 기본 메시지로 채운다.
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super((message == null || message.isBlank()) ? code.getDefaultMessage() : message);
        this.code = code;
    }

    public BusinessException(ErrorCode code) {
        this(code, null);
    }

    public ErrorCode getCode() {
        return code;
    }
}
