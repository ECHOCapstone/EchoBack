package com.capstoneecho.echo_back.challenge.repository;

import com.capstoneecho.echo_back.challenge.entity.DailyChallengeReward;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyChallengeRewardRepository extends JpaRepository<DailyChallengeReward, Long> {

    // 사용자가 받은 모든 챌린지 배지 (최신 발급순). 프로필 화면이 사용한다.
    List<DailyChallengeReward> findByUser_IdOrderByAwardedAtDesc(Long userId);

    // 챌린지의 등수별 배지 목록 (등수 ASC). 챌린지 상세 화면에서 1~3등 미니뷰 노출용.
    List<DailyChallengeReward> findByChallenge_IdOrderByRankAtCloseAsc(Long challengeId);

    // 같은 챌린지에 이미 배지가 발급된 경우 재발급을 막기 위한 존재 검사.
    boolean existsByChallenge_Id(Long challengeId);
}
