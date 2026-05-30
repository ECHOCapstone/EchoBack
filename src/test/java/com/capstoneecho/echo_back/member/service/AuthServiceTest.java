package com.capstoneecho.echo_back.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("signup 은 JWT 를 발급하고 비밀번호를 BCrypt 해시로만 저장한다")
    void signupIssuesJwtAndPersistsBCryptHash() {
        AuthTokenResponse response = authService.signup(
                new SignupRequest("alice", "alice@example.com", "Password!1", "Alice"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSec()).isPositive();
        assertThat(response.user()).isNotNull();
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        assertThat(response.user().nickname()).isEqualTo("Alice");

        User saved = userRepository.findByUsername("alice").orElseThrow();
        assertThat(saved.getPasswordHash())
                .isNotEqualTo("Password!1")
                .startsWith("$2");
        assertThat(passwordEncoder.matches("Password!1", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("signup 은 username 이 중복이면 USERNAME_DUPLICATED 를 던진다")
    void signupDuplicateUsernameThrowsUsernameDuplicated() {
        authService.signup(
                new SignupRequest("bob", "bob@example.com", "Password!1", "Bob"));

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("bob", "bob2@example.com", "Password!1", "Bob2")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.USERNAME_DUPLICATED);
    }

    @Test
    @DisplayName("signup 은 email 이 중복이면 EMAIL_DUPLICATED 를 던진다")
    void signupDuplicateEmailThrowsEmailDuplicated() {
        authService.signup(
                new SignupRequest("carol", "shared@example.com", "Password!1", "Carol"));

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("carol2", "shared@example.com", "Password!1", "Carol2")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("login 은 username 으로 인증에 성공하면 JWT 를 반환한다")
    void loginByUsernameSucceeds() {
        authService.signup(
                new SignupRequest("dan", "dan@example.com", "Password!1", "Dan"));

        AuthTokenResponse response = authService.login(
                new LoginRequest("dan", "Password!1"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("dan");
    }

    @Test
    @DisplayName("login 은 email 로 로그인 시도하면 LOGIN_FAILED (spec: username only)")
    void loginByEmailFailsBecauseSpecIsUsernameOnly() {
        authService.signup(
                new SignupRequest("eve", "eve@example.com", "Password!1", "Eve"));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("eve@example.com", "Password!1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("login 은 잘못된 비밀번호에 대해 LOGIN_FAILED 를 던진다")
    void loginWithWrongPasswordThrowsLoginFailed() {
        authService.signup(
                new SignupRequest("frank", "frank@example.com", "Password!1", "Frank"));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("frank", "WrongPassword!9")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("login 은 존재하지 않는 사용자에 대해 LOGIN_FAILED 를 던진다")
    void loginUnknownUserThrowsLoginFailed() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("ghost", "Password!1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("login 은 OAuth2-only 사용자(passwordHash null) 표준 로그인 시 LOGIN_FAILED 를 던진다")
    void loginOAuth2OnlyUserThrowsLoginFailed() {
        userRepository.save(User.fromOAuth2("oauth@example.com", "OauthUser"));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("oauth@example.com", "Password!1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("checkUsername 은 사용 가능 여부를 반환한다")
    void checkUsernameReturnsAvailability() {
        authService.signup(
                new SignupRequest("hank", "hank@example.com", "Password!1", "Hank"));

        AvailabilityResponse taken = authService.checkUsername("hank");
        AvailabilityResponse free = authService.checkUsername("newuser");

        assertThat(taken.available()).isFalse();
        assertThat(free.available()).isTrue();
    }

    @Test
    @DisplayName("checkEmail 은 사용 가능 여부를 반환한다")
    void checkEmailReturnsAvailability() {
        authService.signup(
                new SignupRequest("ivy", "ivy@example.com", "Password!1", "Ivy"));

        AvailabilityResponse taken = authService.checkEmail("ivy@example.com");
        AvailabilityResponse free = authService.checkEmail("nobody@example.com");

        assertThat(taken.available()).isFalse();
        assertThat(free.available()).isTrue();
    }
}
