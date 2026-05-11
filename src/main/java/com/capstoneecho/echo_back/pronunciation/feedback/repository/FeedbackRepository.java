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

    List<PronunciationFeedback> findAllByUser_IdOrderByCompletedAtDesc(Long userId);

    List<PronunciationFeedback> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndCompletedTrue(Long userId);

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
