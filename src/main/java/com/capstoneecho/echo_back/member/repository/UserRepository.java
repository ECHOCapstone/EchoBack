package com.capstoneecho.echo_back.member.repository;

import com.capstoneecho.echo_back.member.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    // OAuth2 가입 흐름이 이메일을 키로 사용자를 찾을 수 있어야 한다.
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
