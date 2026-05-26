package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.global.config.AppProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

// OAuth2 인증 실패 시 프론트엔드 로그인 페이지로 redirect 한다.
// 형식: <frontendErrorUri>?oauthError=<errorCode>
//
// 실패 원인은 사용자 동의 거부 (access_denied), email scope 누락 (invalid_email),
// sub 누락 (invalid_user_info), Google 측 에러 등 다양하다. errorCode 만 표면화하고
// 상세 메시지는 서버 로그에서만 확인하도록 한다 (민감 정보 누출 방지).
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final String frontendErrorUri;

    public OAuth2LoginFailureHandler(AppProperties appProperties) {
        AppProperties.OAuth2 oauth2 = appProperties.oauth2();
        if (oauth2 == null || oauth2.frontendErrorUri() == null
                || oauth2.frontendErrorUri().isBlank()) {
            throw new IllegalStateException("app.oauth2.frontend-error-uri must be configured");
        }
        this.frontendErrorUri = oauth2.frontendErrorUri();
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String errorCode = "oauth2_failure";
        if (exception instanceof OAuth2AuthenticationException oae
                && oae.getError() != null
                && oae.getError().getErrorCode() != null) {
            errorCode = oae.getError().getErrorCode();
        }
        String targetUrl = frontendErrorUri
                + "?oauthError=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
