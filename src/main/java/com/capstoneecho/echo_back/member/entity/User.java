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

    // 닉네임 컬럼 길이 (DB) 와 일치한다. 잘림 정책은 자르기 (truncate) 로 통일.
    private static final int NICKNAME_MAX = 30;

    // Spring Security BCryptPasswordEncoder 결과 ($2a / $2b / $2y prefix, 53 chars suffix).
    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

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

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
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

    // 표준 회원가입 경로. passwordHash 는 반드시 BCrypt 결과여야 한다 (생패스워드 저장 방지 검증).
    public static User signup(String username, String email, String passwordHashBCrypt, String nickname) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        if (passwordHashBCrypt == null || !BCRYPT_PATTERN.matcher(passwordHashBCrypt).matches()) {
            throw new IllegalArgumentException("password must be a BCrypt hash");
        }
        return new User(username, email, passwordHashBCrypt, truncateNickname(nickname));
    }

    // OAuth2 경로. 비밀번호 해시는 null 로 남겨 BCrypt 검증을 우회한다 (소셜 로그인 전용).
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

    // 학습 완료 보상. 같은 KST 일자면 streak 유지, 어제면 +1 (상한 적용), 그 외엔 1 로 재시작.
    public void recordCompletion(Instant now, int expReward, int streakCap, ZoneId statsZone) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (statsZone == null) {
            throw new IllegalArgumentException("statsZone is required");
        }
        if (expReward < 0) {
            throw new IllegalArgumentException("expReward must be >= 0");
        }
        if (streakCap < 1) {
            throw new IllegalArgumentException("streakCap must be >= 1");
        }

        LocalDate today = now.atZone(statsZone).toLocalDate();
        if (this.lastStudyAt == null) {
            this.streak = 1;
        } else {
            LocalDate lastDay = this.lastStudyAt.atZone(statsZone).toLocalDate();
            if (lastDay.equals(today)) {
                // 같은 KST 일자: streak 유지.
            } else if (lastDay.plusDays(1).equals(today)) {
                this.streak = Math.min(streakCap, this.streak + 1);
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
