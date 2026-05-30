package com.capstoneecho.echo_back.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private AppProperties propsWithAdmin(String bootstrapUsername) {
        return new AppProperties(
                null, null, null, null, null, null, null, null, null, null,
                new AppProperties.Admin(bootstrapUsername));
    }

    private AdminBootstrap bootstrap(String bootstrapUsername) {
        return new AdminBootstrap(userRepository, propsWithAdmin(bootstrapUsername));
    }

    private User userNamed(String username) {
        return User.signup(username, username + "@test.com",
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV12345", "Name");
    }

    // ----- 부팅 경로 -----

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
        // findByUsername 스텁을 두지 않았으므로 호출되면 NPE 로 드러난다.
        bootstrap("").promoteConfiguredAtBoot();
        bootstrap(null).promoteConfiguredAtBoot();
    }

    @Test
    @DisplayName("해당 계정이 없으면 예외 없이 건너뛴다")
    void skipsWhenUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        bootstrap("ghost").promoteConfiguredAtBoot();
    }

    // ----- 로그인 경로 -----

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
}
