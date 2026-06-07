package com.capstoneecho.echo_back.challenge.repository;

import com.capstoneecho.echo_back.challenge.entity.DailyChallengeAttempt;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyChallengeAttemptRepository extends JpaRepository<DailyChallengeAttempt, Long> {

    // 사용자가 특정 챌린지에 시도한 모든 결과 (시간 순). 본인 시도 추이 노출용.
    List<DailyChallengeAttempt> findByChallenge_IdAndUser_IdOrderByCreatedAtAsc(
            Long challengeId, Long userId);

    // 사용자가 오늘 만든 시도 수. 하루 N회 제한 카운트의 SSOT.
    long countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, Instant startInclusive, Instant endExclusive);

    // 챌린지 랭킹 — 사용자별 best 점수와 시도 횟수를 JPQL 집계로 한 번에 가져온다. 모든 attempts 를
    // 메모리로 끌어와 자바에서 집계하는 비용을 피한다.
    @Query("""
            SELECT a.user.id AS userId,
                   a.user.nickname AS nickname,
                   MAX(a.score) AS bestScore,
                   COUNT(a) AS attempts
              FROM DailyChallengeAttempt a
             WHERE a.challenge.id = :challengeId
             GROUP BY a.user.id, a.user.nickname
             ORDER BY MAX(a.score) DESC, COUNT(a) DESC, a.user.id ASC
            """)
    List<UserBestProjection> findUserBestsByChallengeId(@Param("challengeId") Long challengeId);

    // 챌린지 + 사용자 best 점수 1건 — 챌린지 페이지에서 본인 best 노출용.
    @Query("""
            SELECT MAX(a.score) FROM DailyChallengeAttempt a
             WHERE a.challenge.id = :challengeId AND a.user.id = :userId
            """)
    Double findUserBestScore(
            @Param("challengeId") Long challengeId,
            @Param("userId") Long userId);

    interface UserBestProjection {
        Long getUserId();
        String getNickname();
        Double getBestScore();
        Long getAttempts();
    }
}
