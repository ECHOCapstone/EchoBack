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

        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.profile()).isNotNull();
        assertThat(response.profile().username()).isEqualTo("alice");
        assertThat(response.profile().email()).isEqualTo("alice@example.com");
        assertThat(response.profile().nickname()).isEqualTo("Alice");

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

        assertThat(response.token()).isNotBlank();
        assertThat(response.profile().username()).isEqualTo("dan");
    }

    @Test
    @DisplayName("login 은 email 로 인증에 성공하면 JWT 를 반환한다")
    void loginByEmailSucceeds() {
        authService.signup(
                new SignupRequest("eve", "eve@example.com", "Password!1", "Eve"));

        AuthTokenResponse response = authService.login(
                new LoginRequest("eve@example.com", "Password!1"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.profile().email()).isEqualTo("eve@example.com");
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

    @Test
    @DisplayName("loginWithGoogleDemo 는 신규 데모 이메일이면 사용자를 한 번만 생성하고 JWT 를 발급한다")
    void loginWithGoogleDemoCreatesUserOnce() {
        AuthTokenResponse first = authService.loginWithGoogleDemo();
        AuthTokenResponse second = authService.loginWithGoogleDemo();

        assertThat(first.token()).isNotBlank();
        assertThat(second.token()).isNotBlank();
        assertThat(first.profile().email()).isEqualTo(AuthService.DEMO_GOOGLE_EMAIL);
        assertThat(second.profile().email()).isEqualTo(AuthService.DEMO_GOOGLE_EMAIL);
        assertThat(first.profile().id()).isEqualTo(second.profile().id());

        long demoRowCount = userRepository.findAll().stream()
                .filter(u -> AuthService.DEMO_GOOGLE_EMAIL.equals(u.getEmail()))
                .count();
        assertThat(demoRowCount).isEqualTo(1L);

        User saved = userRepository.findByEmail(AuthService.DEMO_GOOGLE_EMAIL).orElseThrow();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getNickname()).isEqualTo(AuthService.DEMO_GOOGLE_NICKNAME);
    }

    @Test
    @DisplayName("loginWithGoogleDemo 는 동일 이메일의 기존 표준 가입자를 머지하고 동일 row 로 JWT 를 발급한다")
    void loginWithGoogleDemoMergesExistingEmail() {
        AuthTokenResponse standard = authService.signup(
                new SignupRequest("existing", AuthService.DEMO_GOOGLE_EMAIL, "Password!1", "Existing"));
        Long existingId = standard.profile().id();

        AuthTokenResponse demo = authService.loginWithGoogleDemo();

        assertThat(demo.token()).isNotBlank();
        assertThat(demo.profile().id()).isEqualTo(existingId);
        assertThat(demo.profile().email()).isEqualTo(AuthService.DEMO_GOOGLE_EMAIL);

        long demoRowCount = userRepository.findAll().stream()
                .filter(u -> AuthService.DEMO_GOOGLE_EMAIL.equals(u.getEmail()))
                .count();
        assertThat(demoRowCount).isEqualTo(1L);

        User merged = userRepository.findById(existingId).orElseThrow();
        assertThat(merged.getUsername()).isEqualTo("existing");
        assertThat(merged.getPasswordHash()).isNotNull();
    }
}
