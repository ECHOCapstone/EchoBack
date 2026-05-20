package com.capstoneecho.echo_back.global.jwt;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class JwtProvider {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {};
    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secret;
    private final long expirationMs;

    public JwtProvider(AppProperties properties) {
        Objects.requireNonNull(properties, "AppProperties");
        Objects.requireNonNull(properties.jwt(), "app.jwt configuration is required");
        String configuredSecret = properties.jwt().secret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        this.expirationMs = properties.jwt().expirationMs();
    }

    public String issue(Long userId, Map<String, Object> additionalClaims) {
        Objects.requireNonNull(userId, "userId");
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(userId));
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusMillis(expirationMs).getEpochSecond());
        if (additionalClaims != null) {
            for (Map.Entry<String, Object> entry : additionalClaims.entrySet()) {
                claims.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        String headerSegment = URL_ENCODER.encodeToString(
                HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payloadSegment;
        try {
            payloadSegment = URL_ENCODER.encodeToString(MAPPER.writeValueAsBytes(claims));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JWT claims", ex);
        }
        String signingInput = headerSegment + "." + payloadSegment;
        String signatureSegment = URL_ENCODER.encodeToString(
                hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signatureSegment;
    }

    public JwtPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        byte[] expectedSignature = hmacSha256(
                (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));

        byte[] providedSignature;
        Map<String, Object> claims;
        try {
            providedSignature = URL_DECODER.decode(parts[2]);
            byte[] payloadBytes = URL_DECODER.decode(parts[1]);
            claims = MAPPER.readValue(payloadBytes, CLAIMS_TYPE);
        } catch (IllegalArgumentException | JacksonException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Object expClaim = claims.get("exp");
        if (!(expClaim instanceof Number expNumber)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (Instant.now().getEpochSecond() >= expNumber.longValue()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Object subClaim = claims.get("sub");
        if (subClaim == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        long userId;
        try {
            userId = Long.parseLong(subClaim.toString());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return new JwtPrincipal(
                userId,
                asString(claims.get("username")),
                asString(claims.get("email"))
        );
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private byte[] hmacSha256(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
