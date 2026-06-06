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

// OAuth2 인증 후처리 분기:
//   1) PendingSignupException → 신규 사용자. 5분짜리 pending JWT 를 발급해 가입 폼으로 안내한다.
//      형식: <frontendSignupUri>#pendingToken=<JWT>&email=<email>&nicknameHint=<name>&provider=<provider>
//   2) 그 외 실패 (사용자 동의 거부 / email scope 누락 / sub 누락 등) → 로그인 페이지로 안내한다.
//      형식: <frontendErrorUri>?oauthError=<errorCode>
//
// pending 토큰은 fragment 로 실어 브라우저가 서버로 다시 보내지 않게 한다 (Referer / 액세스 로그 누출 방지).
// 일반 실패는 노출 대상이 짧은 errorCode 만이라 쿼리로도 안전하며 기존 동작을 유지한다.
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final String frontendErrorUri;
    private final String frontendSignupUri;
    private final PendingOAuthTokenService pendingTokenService;

    public OAuth2LoginFailureHandler(
            AppProperties appProperties,
            PendingOAuthTokenService pendingTokenService
    ) {
        AppProperties.OAuth2 oauth2 = appProperties.oauth2();
        if (oauth2 == null || oauth2.frontendErrorUri() == null
                || oauth2.frontendErrorUri().isBlank()) {
            throw new IllegalStateException("app.oauth2.frontend-error-uri must be configured");
        }
        if (oauth2.frontendSignupUri() == null || oauth2.frontendSignupUri().isBlank()) {
            throw new IllegalStateException("app.oauth2.frontend-signup-uri must be configured");
        }
        this.frontendErrorUri = oauth2.frontendErrorUri();
        this.frontendSignupUri = oauth2.frontendSignupUri();
        this.pendingTokenService = pendingTokenService;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        if (exception instanceof PendingSignupException pending) {
            String pendingToken = pendingTokenService.issue(
                    pending.provider(),
                    pending.providerUid(),
                    pending.email(),
                    pending.nicknameHint());
            // 설정값의 host 가 localhost 박힌 경우에도 외부 도메인 (Cloudflare Tunnel 등) 으로 콜백되도록
                    // incoming request 의 scheme + host 와 합성한다.
            String base = FrontendUrlResolver.resolve(request, frontendSignupUri);
            String targetUrl = base
                    + "#pendingToken=" + encode(pendingToken)
                    + "&email=" + encode(pending.email())
                    + "&nicknameHint=" + encode(pending.nicknameHint() == null ? "" : pending.nicknameHint())
                    + "&provider=" + encode(pending.provider().name().toLowerCase());
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            return;
        }

        String errorCode = "oauth2_failure";
        if (exception instanceof OAuth2AuthenticationException oae
                && oae.getError() != null
                && oae.getError().getErrorCode() != null) {
            errorCode = oae.getError().getErrorCode();
        }
        String base = FrontendUrlResolver.resolve(request, frontendErrorUri);
        String targetUrl = base + "?oauthError=" + encode(errorCode);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
