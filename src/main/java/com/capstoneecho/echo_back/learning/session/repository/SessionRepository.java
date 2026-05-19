package com.capstoneecho.echo_back.learning.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.capstoneecho.echo_back.learning.session.entity.Session;
public interface SessionRepository extends JpaRepository<Session, Long> {

    // 즐겨찾기를 먼저 노출하고 그 안에서는 최근 갱신 순으로 정렬한다.
    List<Session> findByUserIdOrderByFavoriteDescUpdatedAtDesc(Long userId);

    Optional<Session> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
