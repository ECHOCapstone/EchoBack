package com.capstoneecho.echo_back.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AppProperties propsWith(String bootstrapUsername, AppProperties.Admin.SeedAdmin seed) {
        return new AppProperties(
                null, null, null, null, null, null, null, null,
                null,
                null, null,
                new AppProperties.Admin(bootstrapUsername, seed), null, null, null);
    }

    private AdminBootstrap bootstrap(String bootstrapUsername) {
        return new AdminBootstrap(userRepository, passwordEncoder, propsWith(bootstrapUsername, null));
    }

    private AdminBootstrap bootstrapWithSeed(AppProperties.Admin.SeedAdmin seed) {
        return new AdminBootstrap(userRepository, passwordEncoder, propsWith(null, seed));
    }

    private User userNamed(String username) {
        return User.signup(username, username + "@test.com",
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV12345", "Name");
    }

    // ----- 부팅 승격 경로 -----

    @Test
    @DisplayName("부팅 시 bootstrap username 계정을 관리자로 승격한다")
    void promotesConfiguredUserAtBoot() {
        User user = userNamed("root");
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(user));

        bootstrap("root").promoteConfiguredAtBoot();

        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("bootstrap username 미설정이면 조회조차 하지 않는다")
    void skipsWhenUnset() {
        bootstrap("").promoteConfiguredAtBoot();
        bootstrap(null).promoteConfiguredAtBoot();
    }

    @Test
    @DisplayName("해당 계정이 없으면 예외 없이 건너뛴다")
    void skipsWhenUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        bootstrap("ghost").promoteConfiguredAtBoot();
    }

    // ----- 로그인 승격 경로 -----

    @Test
    @DisplayName("로그인 시 username 이 일치하면 승격하고 true 를 반환한다")
    void promotesMatchingUserOnLogin() {
        User user = userNamed("root");

        boolean promoted = bootstrap("root").promoteIfBootstrap(user);

        assertThat(promoted).isTrue();
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("username 이 다르면 승격하지 않는다")
    void skipsNonMatchingUser() {
        User user = userNamed("someone");

        boolean promoted = bootstrap("root").promoteIfBootstrap(user);

        assertThat(promoted).isFalse();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("이미 관리자면 그대로 두고 false 를 반환한다 (멱등)")
    void idempotentWhenAlreadyAdmin() {
        User user = userNamed("root");
        user.promoteToAdmin();

        boolean promoted = bootstrap("root").promoteIfBootstrap(user);

        assertThat(promoted).isFalse();
        assertThat(user.isAdmin()).isTrue();
    }

    // ----- 시드 관리자 생성 경로 -----

    @Test
    @DisplayName("시드 설정이 비어 있으면 어떤 호출도 하지 않는다")
    void skipsSeedWhenUnconfigured() {
        bootstrapWithSeed(null).createSeedAdminIfMissing();
        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("시드 필드 중 하나라도 비면 건너뛴다")
    void skipsSeedWhenPartiallyConfigured() {
        AppProperties.Admin.SeedAdmin partial =
                new AppProperties.Admin.SeedAdmin("admin", "admin@echo.local", "", "관리자");
        bootstrapWithSeed(partial).createSeedAdminIfMissing();
        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("시드 username 의 계정이 없으면 BCrypt 해시 비밀번호와 함께 ROLE_ADMIN 으로 생성한다")
    void createsSeedAdminWhenAbsent() {
        AppProperties.Admin.SeedAdmin seed =
                new AppProperties.Admin.SeedAdmin("admin", "admin@echo.local", "secret123", "관리자");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123"))
                .thenReturn("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV12345");

        bootstrapWithSeed(seed).createSeedAdminIfMissing();

        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("같은 username 의 계정이 이미 있고 일반 회원이면 ROLE_ADMIN 으로 승격만 한다")
    void promotesExistingUserWithSeedUsername() {
        AppProperties.Admin.SeedAdmin seed =
                new AppProperties.Admin.SeedAdmin("admin", "admin@echo.local", "secret123", "관리자");
        User existing = userNamed("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        bootstrapWithSeed(seed).createSeedAdminIfMissing();

        assertThat(existing.isAdmin()).isTrue();
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 관리자면 아무 변경도 하지 않는다 (멱등)")
    void idempotentSeedAdmin() {
        AppProperties.Admin.SeedAdmin seed =
                new AppProperties.Admin.SeedAdmin("admin", "admin@echo.local", "secret123", "관리자");
        User existing = userNamed("admin");
        existing.promoteToAdmin();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        bootstrapWithSeed(seed).createSeedAdminIfMissing();

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("시드 설정과 부트스트랩 설정이 같이 있으면 둘 다 동작한다 (서로 간섭 X)")
    void seedAndBootstrapCoexist() {
        AppProperties.Admin.SeedAdmin seed =
                new AppProperties.Admin.SeedAdmin("admin", "admin@echo.local", "secret123", "관리자");
        AppProperties props = new AppProperties(
                null, null, null, null, null, null, null, null,
                null,
                null, null,
                new AppProperties.Admin("root", seed), null, null, null);
        when(userRepository.findByUsername(eq("admin"))).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123"))
                .thenReturn("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV12345");
        User root = userNamed("root");
        when(userRepository.findByUsername(eq("root"))).thenReturn(Optional.of(root));

        AdminBootstrap target = new AdminBootstrap(userRepository, passwordEncoder, props);
        target.createSeedAdminIfMissing();
        target.promoteConfiguredAtBoot();

        verify(userRepository).save(any(User.class));
        assertThat(root.isAdmin()).isTrue();
    }
}
