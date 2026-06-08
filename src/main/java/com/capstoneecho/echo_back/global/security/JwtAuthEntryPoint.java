package com.capstoneecho.echo_back.global.security;

import com.capstoneecho.echo_back.global.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

// 인증 실패(401)를 ControllerAdvice 로 전파한다. 필터가 ERROR_ATTRIBUTE 로 남긴 ErrorCode 를 해석해
// JwtAuthenticationException 으로 감싼 뒤 공용 HandlerExceptionResolver 에 위임하면,
// GlobalExceptionHandler 가 다른 실패와 동일한 로깅 + ApiResponse 봉투로 처리한다.
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public JwtAuthEntryPoint(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) {
        ErrorCode code = resolveCode(request);
        // RFC 6750 § 3 — Bearer 인증 실패 시 challenge 헤더를 함께 내려보내 표준 클라이언트가 인증 흐름을
        // 식별할 수 있게 한다. resolver 가 봉투/상태를 기록한 뒤에도 이미 세팅된 헤더는 보존된다.
        response.setHeader("WWW-Authenticate", "Bearer realm=\"echo\", error=\"invalid_token\"");
        resolver.resolveException(request, response, null, new JwtAuthenticationException(code));
    }

    private static ErrorCode resolveCode(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthFilter.ERROR_ATTRIBUTE);
        if (attribute instanceof ErrorCode code) {
            return code;
        }
        return ErrorCode.UNAUTHORIZED;
    }
}
