package com.capstoneecho.echo_back.global.jwt;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

// JWT 발급 / 파싱 단일 출처. JJWT (HS256) 위에서 동작하며 알고리즘 confusion 공격은 JJWT 가 막아준다.
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtProvider(AppProperties properties) {
        Objects.requireNonNull(properties, "AppProperties");
        Objects.requireNonNull(properties.jwt(), "app.jwt configuration is required");
        String configuredSecret = properties.jwt().secret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        byte[] keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256 (current length: "
                            + keyBytes.length + ")");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = properties.jwt().expirationMs();
    }

    // subject = userId 문자열, 추가 클레임은 username / email 등을 그대로 실어 보낸다.
    public String issue(Long userId, Map<String, Object> additionalClaims) {
        Objects.requireNonNull(userId, "userId");
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);

        Map<String, Object> claims = new LinkedHashMap<>();
        if (additionalClaims != null) {
            claims.putAll(additionalClaims);
        }

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    // 잘못된 토큰 / 만료 / 서명 불일치 등 모든 실패는 INVALID_TOKEN 으로 일관 변환한다.
    public JwtPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (subject == null) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            long userId;
            try {
                userId = Long.parseLong(subject);
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return new JwtPrincipal(
                    userId,
                    asString(claims.get("username")),
                    asString(claims.get("email"))
            );
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
