package com.capstoneecho.echo_back.pronunciation.feedback.repository;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<PronunciationFeedback, Long> {

    Optional<PronunciationFeedback> findByIdAndUser_Id(Long id, Long userId);

    List<PronunciationFeedback> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndCompletedTrue(Long userId);

    // 사용자가 "완주" 한 트랙 수.
    //   완주 = 트랙에 속한 모든 챕터(Script.chapterOrder IS NOT NULL) 에 대해
    //          사용자의 PronunciationFeedback 이 completed=true.
    // 챕터가 0 개인 트랙은 완주 대상에서 제외한다 (EXISTS 절이 그 역할).
    @Query("""
            SELECT COUNT(t) FROM Track t
             WHERE EXISTS (
                   SELECT s FROM Script s
                    WHERE s.track = t AND s.chapterOrder IS NOT NULL
                 )
               AND NOT EXISTS (
                   SELECT s FROM Script s
                    WHERE s.track = t AND s.chapterOrder IS NOT NULL
                      AND NOT EXISTS (
                          SELECT f FROM PronunciationFeedback f
                           WHERE f.script = s
                             AND f.user.id = :userId
                             AND f.completed = true
                        )
                 )
            """)
    long countTracksFullyCompletedByUser(@Param("userId") Long userId);

    @Query("""
            SELECT f.completedAt FROM PronunciationFeedback f
             WHERE f.user.id = :userId
               AND f.completed = true
               AND f.completedAt >= :start
               AND f.completedAt < :end
            """)
    List<Instant> findCompletedAtInRange(
            @Param("userId") Long userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT f FROM PronunciationFeedback f
              JOIN FETCH f.user
              LEFT JOIN FETCH f.script
             WHERE f.completed = true
               AND f.completedAt >= :start
               AND f.completedAt < :end
            """)
    List<PronunciationFeedback> findCompletedInRange(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT f.weakPhoneme FROM PronunciationFeedback f
             WHERE f.user.id = :userId
               AND f.completed = true
               AND f.completedAt >= :start
               AND f.completedAt < :end
               AND f.weakPhoneme IS NOT NULL
            """)
    List<String> findWeakPhonemesInRange(
            @Param("userId") Long userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Modifying
    @Query("""
            UPDATE PronunciationFeedback f
               SET f.completed   = true,
                   f.completedAt = :now
             WHERE f.id = :id
               AND f.user.id = :userId
               AND f.completed = false
            """)
    int markCompletedAtomically(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("now") Instant now);
}
