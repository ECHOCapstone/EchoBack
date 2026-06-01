package com.capstoneecho.echo_back.global.security.oauth2;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.Provider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// OAuth2 인증 성공 후 우리 도메인에 사용자가 없을 때, 회원가입 폼 단계까지만 신원을 짧게 보장하는 토큰.
// 정식 access JWT 와 같은 HS256 secret 을 쓰되 subject 를 "pending_oauth_signup" 으로 고정해 구분한다.
// 만료는 5분 — 사용자가 가입 폼을 채우는 동안만 유효하면 충분하고 도용 노출도 최소화된다.
@Service
public class PendingOAuthTokenService {

    private static final String SUBJECT = "pending_oauth_signup";
    private static final long PENDING_EXPIRATION_MS = 5 * 60 * 1000L;
    private static final String CLAIM_PROVIDER = "provider";
    private static final String CLAIM_PROVIDER_UID = "providerUid";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NICKNAME_HINT = "nicknameHint";

    private final SecretKey signingKey;

    public PendingOAuthTokenService(AppProperties properties) {
        Objects.requireNonNull(properties, "AppProperties");
        Objects.requireNonNull(properties.jwt(), "app.jwt configuration is required");
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issue(Provider provider, String providerUid, String email, String nicknameHint) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerUid, "providerUid");
        Objects.requireNonNull(email, "email");
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SUBJECT)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(PENDING_EXPIRATION_MS)))
                .claim(CLAIM_PROVIDER, provider.name())
                .claim(CLAIM_PROVIDER_UID, providerUid)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NICKNAME_HINT, nicknameHint == null ? "" : nicknameHint)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public PendingOAuthClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!SUBJECT.equals(claims.getSubject())) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            String providerName = asString(claims.get(CLAIM_PROVIDER));
            String providerUid = asString(claims.get(CLAIM_PROVIDER_UID));
            String email = asString(claims.get(CLAIM_EMAIL));
            String nicknameHint = asString(claims.get(CLAIM_NICKNAME_HINT));
            if (providerName == null || providerUid == null || email == null) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            Provider provider;
            try {
                provider = Provider.valueOf(providerName);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return new PendingOAuthClaims(provider, providerUid, email, nicknameHint);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public long expirationSec() {
        return PENDING_EXPIRATION_MS / 1000L;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public record PendingOAuthClaims(
            Provider provider,
            String providerUid,
            String email,
            String nicknameHint
    ) {}
}
