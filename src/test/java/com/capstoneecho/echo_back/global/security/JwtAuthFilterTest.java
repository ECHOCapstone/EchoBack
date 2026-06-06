package com.capstoneecho.echo_back.global.security;

import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET =
            "test-jwt-secret-please-replace-and-make-this-very-long-1234567890";

    private JwtAuthFilter filter;
    private JwtAuthEntryPoint entryPoint;
    private JwtProvider validProvider;

    @BeforeEach
    void setUp() {
        validProvider = providerWithExpiration(3_600_000L);
        // 활성 사용자 mock — 필터의 isDeleted 검사가 항상 통과하도록 valid User 를 돌려준다.
        UserRepository userRepository = mock(UserRepository.class);
        User activeUser = User.signup(
                "u", "u@test.com",
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV12345",
                "U");
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(activeUser));
        filter = new JwtAuthFilter(validProvider, userRepository);
        entryPoint = new JwtAuthEntryPoint(JsonMapper.builder().build());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("토큰 없음 → 401 UNAUTHORIZED")
    void missingTokenReturns401Unauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthFilter.ERROR_ATTRIBUTE)).isNull();

        entryPoint.commence(request, response,
                new InsufficientAuthenticationException("auth required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        String body = response.getContentAsString();
        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("\"error\":{\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("만료 토큰 → 401 INVALID_TOKEN")
    void expiredTokenReturns401InvalidToken() throws Exception {
        JwtProvider expiredProvider = providerWithExpiration(-1_000L);
        String token = expiredProvider.issue(7L,
                Map.of("username", "bob", "email", "bob@example.com"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthFilter.ERROR_ATTRIBUTE))
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        entryPoint.commence(request, response,
                new InsufficientAuthenticationException("invalid token"));

        assertThat(response.getStatus()).isEqualTo(401);
        String body = response.getContentAsString();
        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("\"error\":{\"code\":\"INVALID_TOKEN\"");
    }

    @Test
    @DisplayName("위조 토큰 → 401 INVALID_TOKEN")
    void tamperedTokenReturns401InvalidToken() throws Exception {
        String token = validProvider.issue(99L,
                Map.of("username", "carol", "email", "carol@example.com"));
        int lastDot = token.lastIndexOf('.');
        String header = token.substring(0, lastDot);
        String signature = token.substring(lastDot + 1);
        char first = signature.charAt(0);
        char swap = (first == 'a') ? 'b' : 'a';
        String tampered = header + "." + swap + signature.substring(1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tampered);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthFilter.ERROR_ATTRIBUTE))
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        entryPoint.commence(request, response,
                new InsufficientAuthenticationException("invalid token"));

        assertThat(response.getStatus()).isEqualTo(401);
        String body = response.getContentAsString();
        assertThat(body).contains("\"error\":{\"code\":\"INVALID_TOKEN\"");
    }

    @Test
    @DisplayName("유효 토큰 → SecurityContext 에 JwtPrincipal 채워짐")
    void validTokenPopulatesSecurityContext() throws Exception {
        String token = validProvider.issue(42L,
                Map.of("username", "alice", "email", "alice@example.com"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull()
                .satisfies(auth -> {
                    assertThat(auth.isAuthenticated()).isTrue();
                    assertThat(auth.getPrincipal()).isNotNull();
                });
        assertThat(request.getAttribute(JwtAuthFilter.ERROR_ATTRIBUTE)).isNull();
    }

    private JwtProvider providerWithExpiration(long expirationMs) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(SECRET, expirationMs),
                null, null, null, null, null, null, null, null, null, null, null, null);
        return new JwtProvider(props);
    }
}
