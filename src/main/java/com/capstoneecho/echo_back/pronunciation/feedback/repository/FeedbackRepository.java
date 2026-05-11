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
