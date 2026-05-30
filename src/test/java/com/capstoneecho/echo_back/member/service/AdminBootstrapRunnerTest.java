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

class AdminBootstrapRunnerTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private AppProperties propsWithAdmin(String bootstrapUsername) {
        return new AppProperties(
                null, null, null, null, null, null, null, null, null, null,
                new AppProperties.Admin(bootstrapUsername));
    }

    @Test
    @DisplayName("bootstrap username 계정을 관리자로 승격한다")
    void promotesConfiguredUser() {
        User user = User.signup("root", "root@test.com",
                "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV12345", "Root");
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(user));

        new AdminBootstrapRunner(userRepository, propsWithAdmin("root")).run(null);

        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("bootstrap username 미설정이면 조회조차 하지 않는다")
    void skipsWhenUnset() {
        // findByUsername 스텁을 두지 않았으므로 호출되면 NPE 로 드러난다.
        new AdminBootstrapRunner(userRepository, propsWithAdmin("")).run(null);
        new AdminBootstrapRunner(userRepository, propsWithAdmin(null)).run(null);
    }

    @Test
    @DisplayName("해당 계정이 없으면 예외 없이 건너뛴다")
    void skipsWhenUserMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        new AdminBootstrapRunner(userRepository, propsWithAdmin("ghost")).run(null);
    }
}
