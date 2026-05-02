package com.capstoneecho.echo_back.app.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Session> findByIdAndUserId(Long id, Long userId);
}
