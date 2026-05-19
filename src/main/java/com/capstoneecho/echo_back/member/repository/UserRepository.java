package com.capstoneecho.echo_back.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import com.capstoneecho.echo_back.member.entity.User;
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
