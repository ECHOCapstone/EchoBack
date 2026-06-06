package com.capstoneecho.echo_back.global.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

// OAuth 흐름의 frontend redirect 대상 URL 을 합성한다.
//
// application.yaml 에 박힌 frontend-redirect-uri / frontend-error-uri / frontend-signup-uri 는
// 보통 절대 URL (예: http://localhost:3000/oauth/callback) 형태인데, Cloudflare Tunnel 같은
// 외부 도메인 뒤에서 OAuth 가 돌아갈 때 그 호스트 부분이 localhost 면 콜백이 localhost 로 튄다.
//
// 해결: 들어오는 요청의 scheme + host (server.forward-headers-strategy=framework 가 X-Forwarded-* 헤더를
// 반영해 자동 갱신해 줌) 를 우선 사용하고, 설정값에서는 path + query 만 떼어 합성한다.
// 설정값이 path-only ("/oauth/callback") 면 그대로 path 로 사용한다.
public final class FrontendUrlResolver {

    private FrontendUrlResolver() {}

    public static String resolve(HttpServletRequest request, String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(configured);
            // path-only ("/oauth/callback") 면 그대로 합성.
            // 절대 URL 이면 path/query 만 떼어 와 incoming request 의 scheme+host 와 합친다.
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery();
            return buildBaseUrl(request) + path + (query == null ? "" : "?" + query);
        } catch (URISyntaxException ex) {
            // 파싱 실패 시 안전하게 설정값 그대로 사용.
            return configured;
        }
    }

    private static String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        if (!defaultPort) {
            sb.append(":").append(port);
        }
        return sb.toString();
    }
}
