package com.capstoneecho.echo_back.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
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

    // DB 컬럼 길이의 단일 출처. 입력이 길면 자르기 (truncate) 로 통일한다.
    private static final int USERNAME_MAX = 50;
    private static final int EMAIL_MAX = 100;
    private static final int NICKNAME_MAX = 30;

    // Spring Security BCryptPasswordEncoder 결과 ($2a / $2b / $2y prefix, 53 chars suffix).
    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = USERNAME_MAX)
    private String username;

    @Column(name = "email", nullable = false, length = EMAIL_MAX)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = NICKNAME_MAX)
    private String nickname;

    @Column(name = "streak", nullable = false)
    private int streak;

    @Column(name = "exp", nullable = false)
    private int exp;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "last_study_at")
    private Instant lastStudyAt;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    // 동의한 약관 버전. AgreementRecord 가 한 묶음으로 기록되며, 추후 본문이 바뀌면 재동의 요구 판정의 기준이 된다.
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "agreed_terms_at")
    private Instant agreedTermsAt;

    @Column(name = "agreed_privacy_at")
    private Instant agreedPrivacyAt;

    @Column(name = "agreed_age_over14_at")
    private Instant agreedAgeOver14At;

    // 마케팅 동의는 선택. NULL 이면 미동의 상태.
    @Column(name = "agreed_marketing_at")
    private Instant agreedMarketingAt;

    // 회원 탈퇴 시각. NULL 이면 활성 회원. 인증 단계에서 not-null 인 사용자는 거절된다.
    @Column(name = "deleted_at")
    private Instant deletedAt;

    private User(String username, String email, String passwordHash, String nickname) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.streak = 0;
        this.exp = 0;
        this.role = Role.USER;
        this.lastStudyAt = null;
        this.createdAt = Instant.now();
    }

    // 가입 시점에 모은 동의 사실을 한 번에 적용한다. 필수 3종 시각이 빠지면 가입 흐름의 사전 검증이 누락된 것.
    public void applyAgreements(AgreementRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("agreement record is required");
        }
        if (record.termsAt() == null || record.privacyAt() == null || record.ageOver14At() == null) {
            throw new IllegalArgumentException("required agreement timestamps must not be null");
        }
        this.termsVersion = record.version();
        this.agreedTermsAt = record.termsAt();
        this.agreedPrivacyAt = record.privacyAt();
        this.agreedAgeOver14At = record.ageOver14At();
        this.agreedMarketingAt = record.marketingAt();
    }

    // 동의 한 묶음. 필수 3종은 not-null, 마케팅은 nullable.
    public record AgreementRecord(
            String version,
            Instant termsAt,
            Instant privacyAt,
            Instant ageOver14At,
            Instant marketingAt
    ) {}

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    // 부트스트랩/운영 도구가 특정 계정을 관리자로 승격할 때 사용한다.
    public void promoteToAdmin() {
        this.role = Role.ADMIN;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // 회원 탈퇴 처리. 즉시 hard delete 하지 않고 시각을 기록해 30일 grace period 동안 보존한다.
    // 별도 cleanup 작업이 grace period 가 지난 사용자를 hard delete 한다.
    public void markDeleted(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        this.deletedAt = now;
    }

    // 탈퇴 후 grace period 안에 같은 인증 정보로 다시 로그인한 경우 계정을 살린다.
    // 학습 기록은 grace period 동안 보존되므로 그대로 복구된다.
    public void restore() {
        this.deletedAt = null;
    }

    // 새 BCrypt 해시로 비밀번호를 교체한다. OAuth 전용 계정 (해시 null) 은 호출자가 사전 검사해야 한다.
    public void replacePasswordHash(String newPasswordHashBCrypt) {
        if (newPasswordHashBCrypt == null
                || !BCRYPT_PATTERN.matcher(newPasswordHashBCrypt).matches()) {
            throw new IllegalArgumentException("password must be a BCrypt hash");
        }
        this.passwordHash = newPasswordHashBCrypt;
    }

    // 표준 회원가입 경로. passwordHash 는 반드시 BCrypt 결과여야 한다 (생패스워드 저장 방지 검증).
    public static User signup(String username, String email, String passwordHashBCrypt, String nickname) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        if (passwordHashBCrypt == null || !BCRYPT_PATTERN.matcher(passwordHashBCrypt).matches()) {
            throw new IllegalArgumentException("password must be a BCrypt hash");
        }
        return new User(username, email, passwordHashBCrypt, truncate(nickname, NICKNAME_MAX));
    }

    // OAuth2 경로. 비밀번호 해시는 null 로 남겨 BCrypt 검증을 우회한다 (소셜 로그인 전용).
    // username 은 표준 로그인에 쓰지 않으므로 email 을 재사용하되 컬럼 길이에 맞춰 자른다.
    public static User fromOAuth2(String email, String nickname) {
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        return new User(
                truncate(email, USERNAME_MAX), email, null, truncate(nickname, NICKNAME_MAX));
    }

    // OAuth2 가입 폼 완료 — 사용자에게는 닉네임만 받고, 통합 회원 풀의 unique 제약을 충족할
    // 내부 username 은 (provider, providerUid) 로부터 결정적으로 생성한다.
    // 이메일은 provider 가 검증한 값을 그대로 신뢰하고, 비밀번호는 없다 (소셜 로그인 전용).
    public static User fromOAuth2Signup(Provider provider, String providerUid, String email, String nickname) {
        requireNonBlank(providerUid, "providerUid");
        requireNonBlank(email, "email");
        requireNonBlank(nickname, "nickname");
        return new User(
                generateOAuthUsername(provider, providerUid),
                email,
                null,
                truncate(nickname, NICKNAME_MAX));
    }

    // OAuth 가입자의 내부 username — 외부에 노출되지 않으며 통합 회원 풀의 unique 제약을 충족시키기
    // 위한 식별자다. 형식 "{provider 소문자}_{providerUid}" 로 결정적이며, provider + sub 가 OAuth 측에서
    // 유일성을 보장하므로 자체 충돌 위험이 없다. USERNAME_MAX 를 넘으면 컬럼 길이에 맞춰 자른다.
    private static String generateOAuthUsername(Provider provider, String providerUid) {
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        String raw = provider.name().toLowerCase(Locale.ROOT) + "_" + providerUid;
        return truncate(raw, USERNAME_MAX);
    }

    public void updateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        this.nickname = truncate(nickname, NICKNAME_MAX);
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

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
