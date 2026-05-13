package com.capstoneecho.echo_back.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final int STREAK_CAP = 7;
    private static final int NICKNAME_MAX = 30;
    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "streak", nullable = false)
    private int streak;

    @Column(name = "exp", nullable = false)
    private int exp;

    @Column(name = "last_study_at")
    private Instant lastStudyAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private User(String username, String email, String passwordHash, String nickname) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.streak = 0;
        this.exp = 0;
        this.lastStudyAt = null;
        this.createdAt = Instant.now();
    }

    public static User signup(String username, String email, String passwordHashBCrypt, String nickname) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        if (passwordHashBCrypt == null || !BCRYPT_PATTERN.matcher(passwordHashBCrypt).matches()) {
            throw new IllegalArgumentException("password must be a BCrypt hash");
        }
        return new User(username, email, passwordHashBCrypt, truncateNickname(nickname));
    }

    public static User fromOAuth2(String email, String nickname) {
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        return new User(email, email, null, truncateNickname(nickname));
    }

    public void updateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        this.nickname = truncateNickname(nickname);
    }

    public void mergeOAuth2Login() {
        // 동일 email 의 표준 회원에 OAuth2 로그인을 연결하는 훅. 현재는 별도 provider 트랙킹이 없어 no-op.
    }

    public void recordCompletion(Instant now, int expReward, ZoneId statsZone) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (statsZone == null) {
            throw new IllegalArgumentException("statsZone is required");
        }
        if (expReward < 0) {
            throw new IllegalArgumentException("expReward must be >= 0");
        }

        LocalDate today = now.atZone(statsZone).toLocalDate();
        if (this.lastStudyAt == null) {
            this.streak = 1;
        } else {
            LocalDate lastDay = this.lastStudyAt.atZone(statsZone).toLocalDate();
            if (lastDay.equals(today)) {
                // 같은 KST 일자 — streak 유지
            } else if (lastDay.plusDays(1).equals(today)) {
                this.streak = Math.min(STREAK_CAP, this.streak + 1);
            } else {
                this.streak = 1;
            }
        }
        this.exp += expReward;
        this.lastStudyAt = now;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String truncateNickname(String s) {
        return s.length() > NICKNAME_MAX ? s.substring(0, NICKNAME_MAX) : s;
    }
}
