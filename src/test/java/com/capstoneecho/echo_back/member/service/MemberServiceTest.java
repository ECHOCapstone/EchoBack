package com.capstoneecho.echo_back.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.dto.NicknameUpdateRequest;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberServiceTest {

    private static final String VALID_BCRYPT =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private MemberService memberService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("findMe 는 인증된 사용자의 프로필을 반환한다")
    void findMeReturnsProfile() {
        User saved = userRepository.save(
                User.signup("alice", "alice@example.com", VALID_BCRYPT, "Alice"));

        UserResponse response = memberService.findMe(saved.getId());

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.nickname()).isEqualTo("Alice");
        assertThat(response.streak()).isZero();
        assertThat(response.exp()).isZero();
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("findMe 는 존재하지 않는 id 에 대해 USER_NOT_FOUND 예외를 던진다")
    void findMeUnknownIdThrowsUserNotFound() {
        assertThatThrownBy(() -> memberService.findMe(999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("updateNickname 은 닉네임을 갱신하고 갱신된 프로필을 반환한다")
    void updateNicknameUpdatesAndReturns() {
        User saved = userRepository.save(
                User.signup("bob", "bob@example.com", VALID_BCRYPT, "Bob"));

        UserResponse response = memberService.updateNickname(
                saved.getId(), new NicknameUpdateRequest("Bobby"));

        assertThat(response.nickname()).isEqualTo("Bobby");
        assertThat(userRepository.findById(saved.getId()))
                .get()
                .extracting(User::getNickname)
                .isEqualTo("Bobby");
    }

    @Test
    @DisplayName("updateNickname 은 blank 닉네임을 VALIDATION_FAILED 로 거부한다")
    void updateNicknameRejectsBlank() {
        User saved = userRepository.save(
                User.signup("carol", "carol@example.com", VALID_BCRYPT, "Carol"));

        assertThatThrownBy(() -> memberService.updateNickname(
                saved.getId(), new NicknameUpdateRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("updateNickname 은 30자 초과 닉네임을 VALIDATION_FAILED 로 거부한다")
    void updateNicknameRejectsTooLong() {
        User saved = userRepository.save(
                User.signup("dan", "dan@example.com", VALID_BCRYPT, "Dan"));

        assertThatThrownBy(() -> memberService.updateNickname(
                saved.getId(), new NicknameUpdateRequest("a".repeat(31))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("updateNickname 은 존재하지 않는 사용자에 대해 USER_NOT_FOUND 를 던진다")
    void updateNicknameUnknownUserThrowsUserNotFound() {
        assertThatThrownBy(() -> memberService.updateNickname(
                999_999L, new NicknameUpdateRequest("Whoever")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("신규 가입자는 온보딩 미완료이며, completeOnboarding 후 완료로 바뀐다")
    void completeOnboardingMarksCompleted() {
        User saved = userRepository.save(
                User.signup("erin", "erin@example.com", VALID_BCRYPT, "Erin"));
        assertThat(memberService.findMe(saved.getId()).onboardingCompleted()).isFalse();

        UserResponse response = memberService.completeOnboarding(saved.getId());

        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(userRepository.findById(saved.getId()))
                .get()
                .extracting(User::isOnboardingCompleted)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("completeOnboarding 은 멱등하다 — 다시 호출해도 완료 상태를 유지한다")
    void completeOnboardingIsIdempotent() {
        User saved = userRepository.save(
                User.signup("frank", "frank@example.com", VALID_BCRYPT, "Frank"));

        memberService.completeOnboarding(saved.getId());
        UserResponse second = memberService.completeOnboarding(saved.getId());

        assertThat(second.onboardingCompleted()).isTrue();
    }
}
