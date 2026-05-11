package com.capstoneecho.echo_back.learning.session.repository;

import com.capstoneecho.echo_back.learning.session.entity.Session;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByIdAndUser_Id(Long id, Long userId);

    List<Session> findAllByUser_IdOrderByUpdatedAtDesc(Long userId);

    int deleteByIdAndUser_Id(Long id, Long userId);
}
