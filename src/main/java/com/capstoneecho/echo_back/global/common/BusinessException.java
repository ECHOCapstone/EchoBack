package com.capstoneecho.echo_back.global.common;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super((message == null || message.isBlank()) ? errorCode.getDefaultMessage() : message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
