package com.capstoneecho.echo_back.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

// 인가 실패(403)를 ControllerAdvice 로 전파한다. Spring Security 의 AccessDeniedException 은 필터 체인에서
// 터져 DispatcherServlet 까지 못 가므로, 공용 HandlerExceptionResolver 에 위임해 GlobalExceptionHandler 가
// 다른 실패와 동일한 로깅 + ApiResponse 봉투로 처리하게 한다.
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public JwtAccessDeniedHandler(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
