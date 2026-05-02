package com.capstoneecho.echo_back.app.common;

// 도메인 흐름 상 발생하는 예외의 단일 진입점. 컨트롤러/서비스 어디서든 던지면
// GlobalExceptionHandler가 ErrorCode 기반으로 응답을 정형화한다.
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
