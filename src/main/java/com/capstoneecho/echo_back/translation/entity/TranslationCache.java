package com.capstoneecho.echo_back.translation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 원문 (영어) → 번역문 (한국어) 캐시 한 건. PK 는 원문의 SHA-256 hex(64) — 본문 길이와 무관하게 일정.
// 같은 원문이 다시 들어오면 외부 LLM 호출 없이 이 행을 조회해 즉시 응답한다.
@Entity
@Table(name = "translation_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationCache {

    @Id
    @Column(name = "text_hash", length = 64, nullable = false, updatable = false)
    private String textHash;

    @Column(name = "source_text", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String sourceText;

    @Column(name = "target_text", columnDefinition = "TEXT", nullable = false)
    private String targetText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private TranslationCache(String textHash, String sourceText, String targetText) {
        this.textHash = textHash;
        this.sourceText = sourceText;
        this.targetText = targetText;
    }

    // 원문 + 번역문을 받아 새 캐시 엔트리를 만든다. textHash 는 원문에서 일관 계산한다.
    public static TranslationCache of(String sourceText, String targetText) {
        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalArgumentException("sourceText is required");
        }
        if (targetText == null) {
            throw new IllegalArgumentException("targetText must not be null");
        }
        return new TranslationCache(hashOf(sourceText), sourceText, targetText);
    }

    // 외부에서 캐시 조회용 hash 를 구할 수 있게 노출한다. Service 가 입력 원문으로 캐시 키를 만들 때 호출한다.
    public static String hashOf(String sourceText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(sourceText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 은 모든 표준 JVM 에 포함돼 실제로 throw 되지 않는다. 안전망으로 재투척.
            throw new IllegalStateException("SHA-256 알고리즘이 JVM 에 없습니다", ex);
        }
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
