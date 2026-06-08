package com.capstoneecho.echo_back.challenge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 어드민이 등록한 "오늘의 챌린지" 문장. 한 번에 active=true 인 행은 ChallengeService 가 도메인 차원에서
// 단 하나만 유지하도록 보장한다. DB 레벨로도 active_marker generated column + unique index 로 race 시
// 한쪽이 DataIntegrityViolation 으로 거절되도록 보강돼 있다 (V17).
// 활성화 / 비활성화 시점을 따로 남겨 랭킹 윈도와 배지 발급 시점 계산에 쓴다.
@Entity
@Table(name = "daily_challenges", indexes = {
        @Index(name = "ix_daily_challenges_active", columnList = "active"),
        @Index(name = "ix_daily_challenges_created", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_text", nullable = false, length = 500)
    private String targetText;

    @Column(name = "korean_translation", length = 500)
    private String koreanTranslation;

    @Column(name = "active", nullable = false)
    private boolean active;

    // active=1 인 행만 'X' 값을 갖는 generated column. unique 인덱스가 같이 걸려 있어
    // 동시에 두 챌린지가 활성화되면 DB 가 한쪽을 DataIntegrityViolationException 으로 거절한다.
    // INSERT/UPDATE 시 Hibernate 가 값을 쓰지 않도록 insertable/updatable=false 로 둔다.
    @Column(name = "active_marker", insertable = false, updatable = false, length = 1)
    private String activeMarker;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    // 챌린지 생성 시점에 LlmCanonicalGenerator 가 만든 단어별 ARPABET 시퀀스 JSON.
    // 모든 사용자가 동일한 정답으로 채점되어 랭킹 공정성을 유지한다 — 챌린지 엔티티에 한 번 저장하고
    // 모든 시도가 이 캐시를 공유한다. NULL 이면 채점 시점에 즉석 생성을 시도하고, 그래도 실패하면
    // BusinessException 으로 거절한다 (silent fallback 금지).
    // 포맷: [{"word":"...","phonemes":[...]}].
    @Column(name = "canonical_cached_json", columnDefinition = "LONGTEXT")
    private String canonicalCachedJson;

    private DailyChallenge(String targetText, String koreanTranslation) {
        this.targetText = targetText;
        this.koreanTranslation = koreanTranslation;
        this.active = false;
    }

    public static DailyChallenge create(String targetText, String koreanTranslation) {
        if (targetText == null || targetText.isBlank()) {
            throw new IllegalArgumentException("targetText is required");
        }
        String trimmedKr = (koreanTranslation == null || koreanTranslation.isBlank())
                ? null : koreanTranslation.trim();
        return new DailyChallenge(targetText.trim(), trimmedKr);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // 챌린지를 활성 상태로 전환한다. 이미 활성이면 활성 시각은 갱신하지 않아 랭킹 윈도가 흔들리지 않게 한다.
    public void activate() {
        if (this.active) {
            return;
        }
        this.active = true;
        this.activatedAt = Instant.now();
        this.deactivatedAt = null;
    }

    // 챌린지를 비활성 상태로 전환한다. 이미 비활성이면 종료 시각은 갱신하지 않는다 (이중 종료 방지).
    public void deactivate() {
        if (!this.active) {
            return;
        }
        this.active = false;
        this.deactivatedAt = Instant.now();
    }

    // 어드민이 LlmCanonicalGenerator 로 만든 canonicalWords JSON 을 영속화한다. 빈 입력은 컬럼을
    // NULL 로 유지해 채점 단계에서 명시적 에러로 끊는다.
    public void applyCanonical(String canonicalJson) {
        this.canonicalCachedJson = (canonicalJson == null || canonicalJson.isBlank())
                ? null : canonicalJson;
    }
}
