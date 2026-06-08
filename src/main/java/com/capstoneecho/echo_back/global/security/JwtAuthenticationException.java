package com.capstoneecho.echo_back.global.security;

import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.security.core.AuthenticationException;

// 필터단 인증 실패를 ControllerAdvice 까지 ErrorCode 객체로 실어 나르는 인증 예외.
// JwtAuthEntryPoint 가 ERROR_ATTRIBUTE 로 해석한 ErrorCode 를 담아 HandlerExceptionResolver 에 위임하면,
// GlobalExceptionHandler 가 attribute(문자열 키)를 다시 읽지 않고 getErrorCode() 로 타입 안전하게 받는다.
public class JwtAuthenticationException extends AuthenticationException {

    // ErrorCode 는 직렬화 대상이 아니므로 transient 로 둔다(AuthenticationException 은 Serializable).
    private final transient ErrorCode errorCode;

    public JwtAuthenticationException(ErrorCode errorCode) {
        // getMessage() 가 한국어 기본 메시지가 되어 로그 msg 와 응답 봉투 error.message 가 일치한다.
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
