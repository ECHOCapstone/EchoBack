package com.capstoneecho.echo_back.app.jwt;

import com.capstoneecho.echo_back.app.AppProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

// HS256 기반 JWT 발급/검증 단일 지점. 비밀키와 만료 시간은 AppProperties.Jwt 에서 주입받는다.
@Component
public class JwtProvider {

    private static final String CLAIM_USER_ID = "uid";

    private final SecretKey key;
    private final long expireMs;

    public JwtProvider(AppProperties properties) {
        var jwt = properties.jwt();
        this.key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.expireMs = jwt.expireMs();
    }

    public String issue(Long userId, String username) {
        var now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    public Optional<JwtPrincipal> verify(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            var userId = ((Number) claims.get(CLAIM_USER_ID)).longValue();
            return Optional.of(new JwtPrincipal(userId, claims.getSubject()));
        } catch (JwtException | IllegalArgumentException | ClassCastException e) {
            return Optional.empty();
        }
    }

    public long expiresInSec() {
        return expireMs / 1000;
    }
}
