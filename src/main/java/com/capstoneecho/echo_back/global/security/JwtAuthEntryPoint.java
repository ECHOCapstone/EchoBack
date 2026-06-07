package com.capstoneecho.echo_back.global.security;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        ErrorCode code = resolveCode(request);
        ApiResponse<Void> body = ApiResponse.failure(code, code.getDefaultMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // RFC 6750 § 3 — Bearer 인증 실패 시 challenge 헤더를 함께 내려보내 표준 클라이언트가 인증 흐름을
        // 식별할 수 있게 한다. error 코드는 RFC 가 정한 invalid_token 으로 고정.
        response.setHeader("WWW-Authenticate", "Bearer realm=\"echo\", error=\"invalid_token\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static ErrorCode resolveCode(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthFilter.ERROR_ATTRIBUTE);
        if (attribute instanceof ErrorCode code) {
            return code;
        }
        return ErrorCode.UNAUTHORIZED;
    }
}
