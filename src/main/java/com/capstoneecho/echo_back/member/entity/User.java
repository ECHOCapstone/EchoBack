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

// 인증 + 학습 통계 캐시를 보유하는 사용자.
// 일반 가입은 BCrypt 해시 형태의 password 를 강제하고, OAuth2 가입 사용자는 password 가 비어 있다.
// streak 와 exp 는 학습 완료를 기록할 때 갱신되며, 학습 메인 화면 헤더에서 즉시 노출된다.
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

    // 연속 학습일의 상한. 운영 정책상 한 사용자가 무한 streak 으로 누적되는 것을 막는다.
    private static final int STREAK_CAP = 7;
    // 닉네임 최대 길이. column length 와 일치한다.
    private static final int NICKNAME_MAX = 30;
    // BCrypt 해시 형식 검증. signup 진입 직전에 PasswordEncoder.encode 결과를 받아 적용한다는 전제.
    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    // OAuth2 가입의 경우 비어 있을 수 있어 nullable.
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "streak", nullable = false)
    private int streak;

    @Column(name = "exp", nullable = false)
    private int exp;

    // 마지막으로 학습 완료가 기록된 시각. streak 정책 계산의 기준점이 된다. 한 번도 학습한 적 없는 사용자는 null.
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

    // 일반 회원 가입. 호출자가 PasswordEncoder.encode 로 만든 BCrypt 해시를 그대로 넘긴다.
    public static User signup(String username, String email, String passwordHashBCrypt, String nickname) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        if (passwordHashBCrypt == null || !BCRYPT_PATTERN.matcher(passwordHashBCrypt).matches()) {
            throw new IllegalArgumentException("password must be a BCrypt hash");
        }
        return new User(username, email, passwordHashBCrypt, truncateNickname(nickname));
    }

    // OAuth2 가입. password 가 없고 username 은 email 과 동일하게 둔다.
    public static User fromOAuth2(String email, String nickname) {
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        return new User(email, email, null, truncateNickname(nickname));
    }

    // 빈 입력은 무시한다. 길이를 넘으면 자른다.
    public void updateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        this.nickname = truncateNickname(nickname);
    }

    // 동일 email 의 표준 회원에 OAuth2 로그인을 연결하는 훅.
    // provider 식별자를 별도 컬럼으로 트래킹하지 않는 현 모델에서는 no-op.
    public void mergeOAuth2Login() {
    }

    // 한 챕터 학습을 끝냈을 때의 streak 갱신 + EXP 가산.
    //   - 같은 날 다시 완료하면 streak 은 그대로 (중복 가산 방지)
    //   - 어제 완료 + 오늘 완료면 streak +1 (단, STREAK_CAP 초과 금지)
    //   - 이틀 이상 비었거나 첫 학습이면 streak 을 1 로 리셋
    // 자정 경계는 호출자가 넘겨준 statsZone 기준이며, now 인자로 시간 의존성을 주입해 테스트 가능하다.
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
                // 같은 날 — streak 유지.
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
