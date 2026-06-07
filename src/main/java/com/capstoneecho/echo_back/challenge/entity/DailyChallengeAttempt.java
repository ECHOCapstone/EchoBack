package com.capstoneecho.echo_back.challenge.entity;

import com.capstoneecho.echo_back.member.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자가 활성 챌린지에 발음 도전한 결과 1건. best 점수가 챌린지 랭킹의 단일 출처이며, 같은 사용자가
// 여러 번 시도해도 랭킹은 최고점 한 줄만 본다. 시도 추이(차트)와 하루 N회 제한 카운트도 본 테이블 기반.
@Entity
@Table(name = "daily_challenge_attempts", indexes = {
        @Index(name = "ix_dca_challenge_user_score", columnList = "challenge_id, user_id, score"),
        @Index(name = "ix_dca_user_created", columnList = "user_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyChallengeAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private DailyChallenge challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "audio_path", nullable = false, length = 500)
    private String audioPath;

    @Column(name = "perceived", columnDefinition = "TEXT")
    private String perceived;

    @Column(name = "canonical", columnDefinition = "TEXT")
    private String canonical;

    @Column(name = "errors_json", columnDefinition = "TEXT")
    private String errorsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private DailyChallengeAttempt(
            DailyChallenge challenge,
            User user,
            double score,
            String audioPath,
            String perceived,
            String canonical,
            String errorsJson) {
        this.challenge = challenge;
        this.user = user;
        this.score = score;
        this.audioPath = audioPath;
        this.perceived = perceived;
        this.canonical = canonical;
        this.errorsJson = errorsJson;
    }

    public static DailyChallengeAttempt create(
            DailyChallenge challenge,
            User user,
            double score,
            String audioPath,
            String perceived,
            String canonical,
            String errorsJson) {
        if (challenge == null) {
            throw new IllegalArgumentException("challenge is required");
        }
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (audioPath == null || audioPath.isBlank()) {
            throw new IllegalArgumentException("audioPath is required");
        }
        return new DailyChallengeAttempt(
                challenge, user, score, audioPath,
                blankToNull(perceived), blankToNull(canonical), blankToNull(errorsJson));
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
