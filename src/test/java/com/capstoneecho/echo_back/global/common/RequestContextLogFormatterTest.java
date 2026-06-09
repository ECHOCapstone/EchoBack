package com.capstoneecho.echo_back.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RequestContextLogFormatterTest {

    @Mock
    private HttpServletRequest request;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("clientIp: X-Forwarded-For 다중 홉이면 첫 홉만 사용")
    void clientIpUsesFirstForwardedHop() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");

        assertThat(RequestContextLogFormatter.clientIp(request)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("clientIp: 헤더가 없으면 getRemoteAddr 로 폴백")
    void clientIpFallsBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.0.2.5");

        assertThat(RequestContextLogFormatter.clientIp(request)).isEqualTo("192.0.2.5");
    }

    @Test
    @DisplayName("clientIp: 헤더가 공백이면 getRemoteAddr 로 폴백")
    void clientIpFallsBackWhenHeaderBlank() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("192.0.2.5");

        assertThat(RequestContextLogFormatter.clientIp(request)).isEqualTo("192.0.2.5");
    }

    @Test
    @DisplayName("safeQuery: null/blank 는 대시, 긴 값은 256자로 절단")
    void safeQueryNormalizesAndTruncates() {
        assertThat(RequestContextLogFormatter.safeQuery(null)).isEqualTo("-");
        assertThat(RequestContextLogFormatter.safeQuery("  ")).isEqualTo("-");
        assertThat(RequestContextLogFormatter.safeQuery("a=1")).isEqualTo("a=1");
        assertThat(RequestContextLogFormatter.safeQuery("x".repeat(300))).hasSize(256);
    }

    @Test
    @DisplayName("orDash: null 은 대시, 값은 문자열")
    void orDashConvertsNull() {
        assertThat(RequestContextLogFormatter.orDash(null)).isEqualTo("-");
        assertThat(RequestContextLogFormatter.orDash(42L)).isEqualTo("42");
    }

    @Test
    @DisplayName("currentUserId: 인증 컨텍스트가 비면 null")
    void currentUserIdNullWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThat(RequestContextLogFormatter.currentUserId()).isNull();
    }

    @Test
    @DisplayName("currentUserId: JwtPrincipal 이면 userId 반환")
    void currentUserIdFromJwtPrincipal() {
        JwtPrincipal principal = new JwtPrincipal(7L, "user7", "user7@test.com", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));

        assertThat(RequestContextLogFormatter.currentUserId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("currentUserId: JwtPrincipal 이 아닌 주체면 null")
    void currentUserIdNullForNonJwtPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null));

        assertThat(RequestContextLogFormatter.currentUserId()).isNull();
    }
}
