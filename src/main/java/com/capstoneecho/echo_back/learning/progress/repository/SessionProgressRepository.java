package com.capstoneecho.echo_back.learning.progress.repository;

import com.capstoneecho.echo_back.learning.progress.entity.SessionProgress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionProgressRepository extends JpaRepository<SessionProgress, Long> {

    Optional<SessionProgress> findByUser_IdAndSession_Id(Long userId, Long sessionId);

    void deleteByUser_IdAndSession_Id(Long userId, Long sessionId);
}
