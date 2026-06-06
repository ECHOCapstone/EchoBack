package com.capstoneecho.echo_back.global.jwt;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET =
            "test-jwt-secret-please-replace-and-make-this-very-long-1234567890";

    private JwtProvider providerWithExpiration(long expirationMs) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(SECRET, expirationMs),
                null, null, null, null, null, null, null, null, null, null, null, null);
        return new JwtProvider(props);
    }

    @Test
    @DisplayName("issue → parse roundtrip yields the same userId/username/email")
    void issueAndParseRoundTrip() {
        JwtProvider provider = providerWithExpiration(3_600_000L);

        String token = provider.issue(
                42L,
                Map.of("username", "alice", "email", "alice@example.com")
        );

        JwtPrincipal principal = provider.parse(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("issue accepts null additionalClaims (still parsable)")
    void issueWithNullAdditionalClaimsIsParsable() {
        JwtProvider provider = providerWithExpiration(3_600_000L);

        String token = provider.issue(7L, null);

        JwtPrincipal principal = provider.parse(token);
        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.username()).isNull();
        assertThat(principal.email()).isNull();
    }

    @Test
    @DisplayName("expired token throws BusinessException(INVALID_TOKEN)")
    void expiredTokenThrowsInvalidToken() {
        JwtProvider provider = providerWithExpiration(-1_000L);

        String token = provider.issue(
                7L,
                Map.of("username", "bob", "email", "bob@example.com")
        );

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("tampered signature throws BusinessException(INVALID_TOKEN)")
    void tamperedSignatureThrowsInvalidToken() {
        JwtProvider provider = providerWithExpiration(3_600_000L);

        String token = provider.issue(
                99L,
                Map.of("username", "carol", "email", "carol@example.com")
        );

        int lastDot = token.lastIndexOf('.');
        String header = token.substring(0, lastDot);
        String signature = token.substring(lastDot + 1);
        char first = signature.charAt(0);
        char swap = (first == 'a') ? 'b' : 'a';
        String tampered = header + "." + swap + signature.substring(1);

        assertThatThrownBy(() -> provider.parse(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("malformed token (wrong segment count) throws BusinessException(INVALID_TOKEN)")
    void malformedTokenThrowsInvalidToken() {
        JwtProvider provider = providerWithExpiration(3_600_000L);

        assertThatThrownBy(() -> provider.parse("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        assertThatThrownBy(() -> provider.parse(""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
