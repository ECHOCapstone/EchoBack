package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

// OAuth2 인증 성공 후 우리 JWT 를 발급하고, 프론트엔드 콜백 URL 의 fragment 로 실어 redirect 한다.
// 형식: <frontendRedirectUri>#token=<JWT>&expiresIn=<sec>
//
// fragment 를 쓰는 이유는 (a) 브라우저가 fragment 를 서버로 전송하지 않아 Referer / 액세스 로그
// 누출이 없고, (b) 기존 username/password 로그인이 응답 body 로 돌려주는 동일한 JWT 형식을 그대로 재사용해
// 프론트 후처리 로직을 통일할 수 있기 때문이다.
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final long jwtExpirationMs;
    private final String frontendRedirectUri;

    public OAuth2LoginSuccessHandler(JwtProvider jwtProvider, AppProperties appProperties) {
        this.jwtProvider = jwtProvider;
        this.jwtExpirationMs = appProperties.jwt().expirationMs();
        AppProperties.OAuth2 oauth2 = appProperties.oauth2();
        if (oauth2 == null || oauth2.frontendRedirectUri() == null
                || oauth2.frontendRedirectUri().isBlank()) {
            throw new IllegalStateException("app.oauth2.frontend-redirect-uri must be configured");
        }
        this.frontendRedirectUri = oauth2.frontendRedirectUri();
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        Object userIdAttr = principal.getAttribute(OAuth2Attributes.INTERNAL_USER_ID);
        if (!(userIdAttr instanceof Number)) {
            throw new IllegalStateException(
                    "OAuth2User 에 내부 userId attribute 가 없습니다. CustomOAuth2UserService 가 적용되지 않은 상태입니다.");
        }
        Long userId = ((Number) userIdAttr).longValue();
        String email = stringAttribute(principal, OAuth2Attributes.INTERNAL_EMAIL);
        String username = stringAttribute(principal, OAuth2Attributes.INTERNAL_USERNAME);
        String role = stringAttribute(principal, OAuth2Attributes.INTERNAL_ROLE);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("username", username);
        claims.put("email", email);
        claims.put("role", role);
        String token = jwtProvider.issue(userId, claims);
        long expiresInSec = Math.max(0L, jwtExpirationMs / 1000L);

        // 설정값의 path/query 만 떼어 와 incoming request 의 scheme+host 와 합성한다.
        // 그래야 Cloudflare Tunnel / 리버스 프록시 뒤에서도 사용자가 접속한 도메인으로 콜백된다.
        // base 에 이미 query 가 있을 수 있으므로 UriComponentsBuilder 로 안전하게 합성한다.
        String base = FrontendUrlResolver.resolve(request, frontendRedirectUri);
        String fragment = "token=" + encode(token) + "&expiresIn=" + expiresInSec;
        String targetUrl = UriComponentsBuilder.fromUriString(base)
                .fragment(fragment)
                .build(true)
                .toUriString();

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private static String stringAttribute(OAuth2User principal, String key) {
        Object value = principal.getAttribute(key);
        return value == null ? null : value.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
