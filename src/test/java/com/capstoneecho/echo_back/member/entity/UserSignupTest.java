package com.capstoneecho.echo_back.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSignupTest {

    private static final String VALID_BCRYPT =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("signup 은 BCrypt 형식이 아닌 password 를 거부한다")
    void signupRequiresBCryptHash() {
        assertThatThrownBy(() ->
                User.signup("alice", "alice@example.com", "plaintext-password", "Alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BCrypt");

        assertThatThrownBy(() ->
                User.signup("alice", "alice@example.com", null, "Alice"))
                .isInstanceOf(IllegalArgumentException.class);

        User valid = User.signup("alice", "alice@example.com", VALID_BCRYPT, "Alice");
        assertThat(valid.getPasswordHash()).isEqualTo(VALID_BCRYPT);
        assertThat(valid.getUsername()).isEqualTo("alice");
        assertThat(valid.getEmail()).isEqualTo("alice@example.com");
        assertThat(valid.getNickname()).isEqualTo("Alice");
        assertThat(valid.getStreak()).isZero();
        assertThat(valid.getExp()).isZero();
        assertThat(valid.getLastStudyAt()).isNull();
    }

    @Test
    @DisplayName("fromOAuth2 는 passwordHash 가 null 인 사용자를 만든다")
    void fromOAuth2SetsNullPasswordHash() {
        User u = User.fromOAuth2("bob@example.com", "Bob");

        assertThat(u.getPasswordHash()).isNull();
        assertThat(u.getEmail()).isEqualTo("bob@example.com");
        assertThat(u.getNickname()).isEqualTo("Bob");
        assertThat(u.getStreak()).isZero();
        assertThat(u.getExp()).isZero();
    }

    @Test
    @DisplayName("signup 은 빈 입력값을 거부한다")
    void signupRejectsBlankInputs() {
        assertThatThrownBy(() -> User.signup("", "a@b.com", VALID_BCRYPT, "A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.signup("a", "", VALID_BCRYPT, "A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.signup("a", "a@b.com", VALID_BCRYPT, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateNickname 은 30자 초과를 절단하고 빈 입력은 무시")
    void updateNicknameRules() {
        User u = User.fromOAuth2("c@example.com", "Carol");

        u.updateNickname("a".repeat(40));
        assertThat(u.getNickname()).hasSize(30);

        u.updateNickname("");
        assertThat(u.getNickname()).hasSize(30);

        u.updateNickname("Dave");
        assertThat(u.getNickname()).isEqualTo("Dave");
    }

    @Test
    @DisplayName("recordCompletion: 첫 학습은 streak=1, 같은 KST 일자는 유지, 다음날은 +1, 7에서 cap, 끊기면 1로 리셋")
    void recordCompletionStreakLogic() {
        User u = User.signup("d", "d@example.com", VALID_BCRYPT, "Dan");

        Instant day1 = Instant.parse("2026-01-01T10:00:00Z");
        u.recordCompletion(day1, 100, 7, KST);
        assertThat(u.getStreak()).isEqualTo(1);
        assertThat(u.getExp()).isEqualTo(100);
        assertThat(u.getLastStudyAt()).isEqualTo(day1);

        Instant day1Again = day1.plusSeconds(3600);
        u.recordCompletion(day1Again, 50, 7, KST);
        assertThat(u.getStreak()).isEqualTo(1);
        assertThat(u.getExp()).isEqualTo(150);

        Instant day2 = day1.plusSeconds(86400);
        u.recordCompletion(day2, 10, 7, KST);
        assertThat(u.getStreak()).isEqualTo(2);
        assertThat(u.getExp()).isEqualTo(160);

        for (int i = 3; i < 20; i++) {
            u.recordCompletion(day1.plusSeconds(86400L * (i - 1)), 0, 7, KST);
        }
        assertThat(u.getStreak()).isEqualTo(7);

        Instant farFuture = Instant.parse("2026-04-01T10:00:00Z");
        u.recordCompletion(farFuture, 0, 7, KST);
        assertThat(u.getStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordCompletion 은 null/음수 expReward 를 거부한다")
    void recordCompletionRejectsInvalidArgs() {
        User u = User.fromOAuth2("e@example.com", "Eve");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> u.recordCompletion(null, 10, 7, KST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> u.recordCompletion(now, 10, 7, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> u.recordCompletion(now, -1, 7, KST))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
