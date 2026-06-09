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
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET =
            "test-jwt-secret-please-replace-and-make-this-very-long-1234567890";

    private JwtAuthFilter filter;
    private JwtAuthEntryPoint entryPoint;
    private HandlerExceptionResolver resolver;
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
        // 엔트리포인트는 봉투를 직접 쓰지 않고 HandlerExceptionResolver(=ControllerAdvice 경로)로 위임한다.
        // 여기서는 위임이 올바른 ErrorCode 를 실은 JwtAuthenticationException 으로 일어나는지만 검증하고,
        // 실제 401 봉투/상태는 SecurityConfigIntegrationTest(full chain)에서 확인한다.
        resolver = mock(HandlerExceptionResolver.class);
        entryPoint = new JwtAuthEntryPoint(resolver);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("토큰 없음 → 필터는 ERROR_ATTRIBUTE 미설정, 엔트리포인트는 UNAUTHORIZED 위임")
    void missingTokenResolvesUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthFilter.ERROR_ATTRIBUTE)).isNull();

        entryPoint.commence(request, response,
                new InsufficientAuthenticationException("auth required"));

        assertDelegatedWithCode(request, response, ErrorCode.UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer realm=\"echo\", error=\"invalid_token\"");
    }

    @Test
    @DisplayName("만료 토큰 → 필터는 INVALID_TOKEN 설정, 엔트리포인트는 INVALID_TOKEN 위임")
    void expiredTokenResolvesInvalidToken() throws Exception {
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

        assertDelegatedWithCode(request, response, ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("위조 토큰 → 필터는 INVALID_TOKEN 설정, 엔트리포인트는 INVALID_TOKEN 위임")
    void tamperedTokenResolvesInvalidToken() throws Exception {
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

        assertDelegatedWithCode(request, response, ErrorCode.INVALID_TOKEN);
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

    // 엔트리포인트가 resolver(=ControllerAdvice 경로)로 위임할 때, 기대한 ErrorCode 를 실은
    // JwtAuthenticationException 이 전달됐는지 검증한다.
    private void assertDelegatedWithCode(
            MockHttpServletRequest request, MockHttpServletResponse response, ErrorCode expected) {
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(resolver).resolveException(eq(request), eq(response), isNull(), captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(JwtAuthenticationException.class)
                .extracting(ex -> ((JwtAuthenticationException) ex).getErrorCode())
                .isEqualTo(expected);
    }

    private JwtProvider providerWithExpiration(long expirationMs) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(SECRET, expirationMs),
                null, null, null, null, null, null, null,
                null,
                null, null, null, null, null, null, null, null);
        return new JwtProvider(props);
    }
}
