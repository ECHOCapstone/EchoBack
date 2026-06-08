package com.capstoneecho.echo_back.external.llm.canonical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// (user_id, target_type, target_id) 별로 첫 시도 시 LLM 이 만든 canonical 을 고정 저장한다.
// 이후 같은 학습자의 동일 발화 단위 시도에서 LLM 호출 시 입력으로 다시 주입해 그대로 echo 하게 만든다 —
// 학습자가 "정답이 시도마다 바뀌는" UX 를 겪지 않도록 보장한다.
@Entity
@Table(
        name = "user_canonical_locks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ucl_user_target",
                columnNames = {"user_id", "target_type", "target_id"}),
        indexes = @Index(name = "ix_ucl_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCanonicalLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private CanonicalTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    // LLM 응답의 canonicalWords 를 그대로 직렬화한 JSON. 포맷: [{"word":"...","phonemes":[...]}].
    @Column(name = "canonical_json", nullable = false, columnDefinition = "LONGTEXT")
    private String canonicalJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private UserCanonicalLock(
            Long userId, CanonicalTargetType targetType, Long targetId, String canonicalJson) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.canonicalJson = canonicalJson;
    }

    public static UserCanonicalLock create(
            Long userId, CanonicalTargetType targetType, Long targetId, String canonicalJson) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        if (canonicalJson == null || canonicalJson.isBlank()) {
            throw new IllegalArgumentException("canonicalJson is required");
        }
        return new UserCanonicalLock(userId, targetType, targetId, canonicalJson);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
