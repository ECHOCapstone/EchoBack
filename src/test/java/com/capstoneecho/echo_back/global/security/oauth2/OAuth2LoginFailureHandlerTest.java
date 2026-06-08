package com.capstoneecho.echo_back.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.global.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2LoginFailureHandlerTest {

    private static final String ERROR_URI = "http://localhost:3000/login";

    private OAuth2LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("test-secret-secret-secret-secret-secret", 3_600_000L),
                null, null, null, null, null, null, null,
                null,
                null,
                new AppProperties.OAuth2(
                        "http://localhost:3000/oauth/callback",
                        ERROR_URI,
                        "http://localhost:3000/signup"),
                null, null, null, null, null, null);
        PendingOAuthTokenService pendingTokenService = new PendingOAuthTokenService(props);
        handler = new OAuth2LoginFailureHandler(props, pendingTokenService);
    }

    @Test
    @DisplayName("OAuth2AuthenticationException 의 errorCode 를 oauthError 쿼리로 실어 redirect 한다")
    void redirectsWithOauthErrorCode() throws Exception {
        OAuth2AuthenticationException ex = new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", "user canceled", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, ex);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo(ERROR_URI + "?oauthError=access_denied");
    }

    @Test
    @DisplayName("invalid_email 같은 다른 errorCode 도 그대로 전달한다")
    void redirectsForInvalidEmailErrorCode() throws Exception {
        OAuth2AuthenticationException ex = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_email"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, ex);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(ERROR_URI + "?oauthError=invalid_email");
    }

    @Test
    @DisplayName("OAuth2AuthenticationException 이 아닌 경우 기본 코드 oauth2_failure 를 사용한다")
    void fallsBackToDefaultCodeForNonOAuth2Exception() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("nope"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo(ERROR_URI + "?oauthError=oauth2_failure");
    }
}
