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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 챌린지 종료 시점에 1~3등 사용자에게 발급된 배지 기록.
// (challenge_id, user_id) 유니크 제약으로 한 챌린지/사용자 조합은 단 한 번 발급된다.
// 프로필 화면이 사용자 배지 목록 노출에 사용한다.
@Entity
@Table(name = "daily_challenge_rewards",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dcr_challenge_user",
                columnNames = {"challenge_id", "user_id"}),
        indexes = @Index(name = "ix_dcr_user_awarded", columnList = "user_id, awarded_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyChallengeReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private DailyChallenge challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "rank_at_close", nullable = false)
    private int rankAtClose;

    @Column(name = "best_score", nullable = false)
    private double bestScore;

    @Column(name = "badge_id", nullable = false, length = 50)
    private String badgeId;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private Instant awardedAt;

    private DailyChallengeReward(
            DailyChallenge challenge, User user, int rankAtClose, double bestScore, String badgeId) {
        this.challenge = challenge;
        this.user = user;
        this.rankAtClose = rankAtClose;
        this.bestScore = bestScore;
        this.badgeId = badgeId;
    }

    public static DailyChallengeReward create(
            DailyChallenge challenge, User user, int rankAtClose, double bestScore, String badgeId) {
        if (challenge == null) {
            throw new IllegalArgumentException("challenge is required");
        }
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (rankAtClose < 1) {
            throw new IllegalArgumentException("rankAtClose must be >= 1");
        }
        if (badgeId == null || badgeId.isBlank()) {
            throw new IllegalArgumentException("badgeId is required");
        }
        return new DailyChallengeReward(challenge, user, rankAtClose, bestScore, badgeId.trim());
    }

    @PrePersist
    void onCreate() {
        this.awardedAt = Instant.now();
    }
}
