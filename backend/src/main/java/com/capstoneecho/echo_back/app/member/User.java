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

    // 한 학습 단위(=챕터) 를 완료했을 때 호출되는 단일 진입점.
    // streak 은 마지막 학습 일자(lastStudyAt) 와 오늘을 비교해 결정된다.
    //   - 같은 날 다시 완료: streak 그대로 (중복 가산 방지)
    //   - 어제 완료 + 오늘 완료: streak += 1
    //   - 그 외 (이틀 이상 공백 또는 첫 학습): streak = 1 로 새 시작
    public void recordCompletion(int expReward) {
        var zone = ZoneId.systemDefault();
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
