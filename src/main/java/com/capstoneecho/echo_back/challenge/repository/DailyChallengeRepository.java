package com.capstoneecho.echo_back.challenge.repository;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, Long> {

    // 현재 활성 챌린지. 도메인 서비스가 단일 active 를 보장하므로 한 줄만 돌아오지만, 안전망으로 List 도 제공.
    Optional<DailyChallenge> findFirstByActiveTrueOrderByActivatedAtDesc();

    List<DailyChallenge> findAllByActiveTrue();

    // 히스토리 페이지: 활성 여부와 무관하게 최신순.
    Page<DailyChallenge> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
