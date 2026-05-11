package com.capstoneecho.echo_back.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.member.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class UserRepositoryDataJpaTest {

    private static final String VALID_BCRYPT =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void seed() {
        repository.save(User.signup("alice", "alice@example.com", VALID_BCRYPT, "Alice"));
        repository.save(User.fromOAuth2("bob@example.com", "Bob"));
    }

    @Test
    @DisplayName("existsByUsername 는 존재하는 username 에 대해 true")
    void existsByUsernameReturnsTrueWhenPresent() {
        assertThat(repository.existsByUsername("alice")).isTrue();
        assertThat(repository.existsByUsername("nobody")).isFalse();
    }

    @Test
    @DisplayName("existsByEmail 는 존재하는 email 에 대해 true")
    void existsByEmailReturnsTrueWhenPresent() {
        assertThat(repository.existsByEmail("alice@example.com")).isTrue();
        assertThat(repository.existsByEmail("bob@example.com")).isTrue();
        assertThat(repository.existsByEmail("none@example.com")).isFalse();
    }

    @Test
    @DisplayName("findByUsername 는 매칭되는 user 를 반환")
    void findByUsernameReturnsUser() {
        assertThat(repository.findByUsername("alice"))
                .isPresent()
                .get()
                .extracting(User::getNickname)
                .isEqualTo("Alice");
    }

    @Test
    @DisplayName("findByEmail 는 매칭되는 user 를 반환하며 OAuth2 user 는 passwordHash 가 null")
    void findByEmailReturnsUser() {
        assertThat(repository.findByEmail("bob@example.com"))
                .isPresent()
                .get()
                .extracting(User::getPasswordHash)
                .isNull();
    }
}
