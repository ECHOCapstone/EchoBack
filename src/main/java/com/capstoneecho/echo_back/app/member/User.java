package com.capstoneecho.echo_back.app.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// 인증 + 학습 통계 캐시를 보유하는 사용자. password 는 BCrypt 해시 형태로만 저장한다.
// streak/exp 는 학습 기록 변경 시 갱신되며, 학습 메인 화면 헤더에서 즉시 노출된다.
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String username;

    @Column(length = 100, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(length = 30, nullable = false)
    private String nickname;

    @Column(nullable = false)
    private int streak;

    @Column(nullable = false)
    private int exp;

    // 마지막으로 학습 완료가 기록된 시각. streak 정책 계산의 기준점이 된다.
    // 한 번도 학습한 적 없는 사용자는 null.
    @Column(name = "last_study_at")
    private Instant lastStudyAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User create(String username, String email, String passwordHash, String nickname) {
        var user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.streak = 0;
        user.exp = 0;
        return user;
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // 빈 입력은 무시하고, 30자를 넘으면 잘라서 저장한다.
    public void changeNickname(String nickname) {
        if (nickname == null) return;
        var trimmed = nickname.trim();
        if (trimmed.isEmpty()) return;
        this.nickname = trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }

    // 한 챕터 학습을 끝냈을 때의 streak 갱신 + EXP 가산.
    //   - 같은 날 다시 완료하면 streak 은 그대로 (중복 가산 방지)
    //   - 어제 완료 + 오늘 완료면 streak +1
    //   - 이틀 이상 비었거나 첫 학습이면 streak 을 1 로 리셋
    // 자정 경계는 호출자가 넘겨준 zone 을 기준으로 본다.
    public void recordCompletion(int expReward, ZoneId zone) {
        var today = LocalDate.now(zone);
        var lastDate = lastStudyAt != null ? lastStudyAt.atZone(zone).toLocalDate() : null;
        if (lastDate == null || lastDate.isBefore(today.minusDays(1))) {
            this.streak = 1;
        } else if (lastDate.isEqual(today.minusDays(1))) {
            this.streak += 1;
        }
        this.lastStudyAt = Instant.now();
        this.exp += expReward;
    }
}
