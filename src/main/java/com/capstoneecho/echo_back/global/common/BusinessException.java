package com.capstoneecho.echo_back.global.common;

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
