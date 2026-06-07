package com.capstoneecho.echo_back.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    private static final String FRONTEND_URL = "http://localhost:3000/oauth/callback";
    private static final long EXPIRATION_MS = 3_600_000L;

    @Mock
    private JwtProvider jwtProvider;

    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("test-secret-secret-secret-secret-secret", EXPIRATION_MS),
                null, null, null, null, null, null, null,
                null,
                null,
                null,
                new AppProperties.OAuth2(FRONTEND_URL, "http://localhost:3000/login", "http://localhost:3000/signup"),
                null, null, null, null);
        handler = new OAuth2LoginSuccessHandler(jwtProvider, props);
    }

    private static OAuth2AuthenticationToken token(Long userId, String username, String email) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("sub", "google-sub-1");
        attrs.put(OAuth2Attributes.INTERNAL_USER_ID, userId);
        attrs.put(OAuth2Attributes.INTERNAL_USERNAME, username);
        attrs.put(OAuth2Attributes.INTERNAL_EMAIL, email);
        DefaultOAuth2User principal = new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attrs,
                "sub"
        );
        return new OAuth2AuthenticationToken(
                principal,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                "google"
        );
    }

    @Test
    @DisplayName("성공 시 JWT 를 fragment 로 실어 frontendRedirectUri 로 302 redirect 한다")
    void issuesJwtAndRedirectsToFrontendWithFragment() throws Exception {
        when(jwtProvider.issue(eq(42L), any())).thenReturn("fake-jwt-value");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token(42L, "alice@gmail.com", "alice@gmail.com"));

        assertThat(response.getStatus()).isEqualTo(302);
        String location = response.getRedirectedUrl();
        assertThat(location).startsWith(FRONTEND_URL + "#token=fake-jwt-value");
        assertThat(location).contains("&expiresIn=" + (EXPIRATION_MS / 1000L));
    }

    @Test
    @DisplayName("internal userId attribute 가 없으면 IllegalStateException 을 던진다")
    void rejectsTokenMissingInternalUserId() {
        Map<String, Object> attrs = Map.of("sub", "google-sub");
        DefaultOAuth2User principal = new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attrs,
                "sub"
        );
        OAuth2AuthenticationToken bad = new OAuth2AuthenticationToken(
                principal,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                "google"
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() ->
                handler.onAuthenticationSuccess(request, response, bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId");
    }
}
