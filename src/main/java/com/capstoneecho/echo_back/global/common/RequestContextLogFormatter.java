package com.capstoneecho.echo_back.global.common;

import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// HTTP 요청 실패 로그에 들어갈 요청 컨텍스트(사용자/IP/쿼리)를 안전하게 추출하는 헬퍼.
// GlobalExceptionHandler 전용 보조 유틸이므로 빈으로 등록하지 않고 static 메서드로 둔다
// (슬라이스 테스트 와이어링 부담 없음 + 순수 단위 테스트 용이).
final class RequestContextLogFormatter {

    // 쿼리스트링이 비정상적으로 길어도 로그 한 줄을 오염시키지 않도록 절단한다.
    private static final int MAX_QUERY_LENGTH = 256;
    private static final String DASH = "-";

    private RequestContextLogFormatter() {
    }

    // 인증된 사용자 ID. 미인증이거나 JwtPrincipal 이 아닌 주체면 null 을 돌려준다.
    static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return (auth.getPrincipal() instanceof JwtPrincipal principal) ? principal.userId() : null;
    }

    // 프록시 뒤에서도 실제 클라이언트를 식별하도록 X-Forwarded-For 첫 홉을 우선 사용하고,
    // 없으면 소켓 원격 주소로 폴백한다.
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String firstHop = (comma > 0) ? forwarded.substring(0, comma) : forwarded;
            return firstHop.trim();
        }
        return request.getRemoteAddr();
    }

    // null/blank 쿼리는 "-", 길면 MAX_QUERY_LENGTH 로 절단한다.
    static String safeQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return DASH;
        }
        return (raw.length() > MAX_QUERY_LENGTH) ? raw.substring(0, MAX_QUERY_LENGTH) : raw;
    }

    // null 값을 로그 친화적인 "-" 로 치환한다.
    static String orDash(Object value) {
        return (value == null) ? DASH : value.toString();
    }
}
