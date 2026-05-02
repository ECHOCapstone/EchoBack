package com.capstoneecho.echo_back.app.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    public void addExp(int amount) {
        this.exp += amount;
    }

    public void increaseStreak() {
        this.streak += 1;
    }

    public void resetStreak() {
        this.streak = 0;
    }
}
